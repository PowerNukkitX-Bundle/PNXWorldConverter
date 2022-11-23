package cn.coolloong.proxy;

import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.RegionLoader;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;

import java.io.IOException;
import java.io.RandomAccessFile;

public class ProxyRegionLoader extends RegionLoader {

    public ProxyRegionLoader(LevelProvider level, int regionX, int regionZ) throws IOException {
        super(level, regionX, regionZ);
    }

    public void saveChunk(int x, int z, byte[] chunkData) throws IOException {
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
