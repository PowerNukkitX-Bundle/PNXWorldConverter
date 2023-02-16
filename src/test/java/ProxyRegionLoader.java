import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.anvil.RegionLoader;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class ProxyRegionLoader extends RegionLoader {
    public ProxyRegionLoader(LevelProvider level, int regionX, int regionZ) throws IOException {
        super(level, regionX, regionZ);
    }

    @Override
    public Chunk readChunk(int x, int z) throws IOException {
        int index = getChunkOffset(x, z);
        if (index < 0 || index >= 4096) {
            return null;
        }

        this.lastUsed = System.currentTimeMillis();

        if (!this.isChunkGenerated(index)) {
            return null;
        }

        try {
            int[] table = this.primitiveLocationTable.get(index);
            RandomAccessFile raf = this.getRandomAccessFile();
            raf.seek((long) table[0] << 12L);
            int length = raf.readInt();
            byte compression = raf.readByte();
            if (length <= 0 ) {
                return null;
            }

            if (length > (table[1] << 12)) {
                table[1] = length >> 12;
                this.primitiveLocationTable.put(index, table);
                this.writeLocationIndex(index);
            } else if (compression != COMPRESSION_ZLIB && compression != COMPRESSION_GZIP) {
                return null;
            }

            byte[] data = new byte[length - 1];
            raf.readFully(data);
            Chunk chunk = this.unserializeChunk(data);
            if (chunk != null) {
                return chunk;
            } else {
                return null;
            }
        } catch (EOFException e) {
            return null;
        }
    }

    @Override
    protected void saveChunk(int x, int z, byte[] chunkData) throws IOException {
        int length = chunkData.length + 1;
        int sectors = (int) Math.ceil((length + 4) / 4096d);
        int index = getChunkOffset(x, z);
        boolean indexChanged = false;
        int[] table = this.primitiveLocationTable.get(index);

        if (table[1] < sectors) {
            table[0] = this.lastSector + 1;
            this.primitiveLocationTable.put(index, table);
            this.lastSector += sectors;
            indexChanged = true;
        } else if (table[1] != sectors) {
            indexChanged = true;
        }

        table[1] = sectors;
        table[2] = (int) (System.currentTimeMillis() / 1000d);

        this.primitiveLocationTable.put(index, table);
        RandomAccessFile raf = this.getRandomAccessFile();
        raf.seek((long) table[0] << 12L);

        BinaryStream stream = new BinaryStream();
        stream.put(Binary.writeInt(length));
        stream.putByte(COMPRESSION_ZLIB);
        stream.put(chunkData);
        byte[] data = stream.getBuffer();
        if (data.length < sectors << 12) {
            byte[] newData = new byte[sectors << 12];
            System.arraycopy(data, 0, newData, 0, data.length);
            data = newData;
        }

        raf.write(data);

        if (indexChanged) {
            this.writeLocationIndex(index);
        }
    }
}
