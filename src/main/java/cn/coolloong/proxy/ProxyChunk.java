package cn.coolloong.proxy;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.anvil.RegionLoader;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.BlockUpdateEntry;
import cn.nukkit.utils.Zlib;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class ProxyChunk extends Chunk {
    public final ArrayList<CompoundTag> tiles = new ArrayList<>();

    public ProxyChunk(LevelProvider level) {
        super(level);
    }

    public ProxyChunk(LevelProvider level, DimensionData dimensionData) {
        super(level, dimensionData);
    }

    public ProxyChunk(Class<? extends LevelProvider> providerClass) {
        super(providerClass);
    }

    public ProxyChunk(Class<? extends LevelProvider> providerClass, DimensionData dimensionData) {
        super(providerClass, dimensionData);
    }

    public ProxyChunk(Class<? extends LevelProvider> providerClass, CompoundTag nbt) {
        super(providerClass, nbt);
    }

    public ProxyChunk(Class<? extends LevelProvider> providerClass, CompoundTag nbt, DimensionData dimensionData) {
        super(providerClass, nbt, dimensionData);
    }

    public ProxyChunk(LevelProvider level, CompoundTag nbt) {
        super(level, nbt);
    }

    public ProxyChunk(LevelProvider level, CompoundTag nbt, DimensionData dimensionData) {
        super(level, nbt, dimensionData);
    }

    public static ProxyChunk getEmptyChunk(int chunkX, int chunkZ, LevelProvider provider, DimensionEnum dimensionEnum, long inhabitedTime, boolean terrainGenerated, boolean terrainPopulated) {
        try {
            ProxyChunk chunk = new ProxyChunk(Anvil.class, null, dimensionEnum.getDimensionData());
            chunk.setPosition(chunkX, chunkZ);
            chunk.heightMap = new byte[256];
            chunk.inhabitedTime = inhabitedTime;
            chunk.terrainGenerated = terrainGenerated;
            chunk.terrainPopulated = terrainPopulated;
            return chunk;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public byte[] toBinary() {
        CompoundTag nbt = this.getNBT().copy();
        nbt.remove("BiomeColors");

        nbt.putInt("xPos", this.getX());
        nbt.putInt("zPos", this.getZ());

        ListTag<CompoundTag> sectionList = new ListTag<>("Sections");
        for (cn.nukkit.level.format.ChunkSection section : this.getSections()) {
            sectionList.add(section.toNBT());
        }
        nbt.putList(sectionList);

        nbt.putByteArray("Biomes", this.getBiomeIdArray());
        int[] heightInts = new int[256];
        byte[] heightBytes = this.getHeightMapArray();
        for (int i = 0; i < heightInts.length; i++) {
            heightInts[i] = heightBytes[i] & 0xFF;
        }
        nbt.putIntArray("HeightMap", heightInts);

        ArrayList<CompoundTag> entities = new ArrayList<>();
        for (Entity entity : this.getEntities().values()) {
            if (!(entity instanceof Player) && !entity.closed) {
                entity.saveNBT();
                entities.add(entity.namedTag);
            }
        }
        ListTag<CompoundTag> entityListTag = new ListTag<>("Entities");
        entityListTag.setAll(entities);
        nbt.putList(entityListTag);

        ListTag<CompoundTag> tileListTag = new ListTag<>("TileEntities");
        tileListTag.setAll(tiles);
        nbt.putList(tileListTag);

        Set<BlockUpdateEntry> entries = null;
        if (this.provider != null) {
            Level level = provider.getLevel();
            if (level != null) {
                entries = level.getPendingBlockUpdates(this);
            }
        }

        if (entries != null) {
            ListTag<CompoundTag> tileTickTag = new ListTag<>("TileTicks");
            long totalTime = this.provider.getLevel().getCurrentTick();

            for (BlockUpdateEntry entry : entries) {
                CompoundTag entryNBT = new CompoundTag()
                        .putString("i", entry.block.getSaveId())
                        .putInt("x", entry.pos.getFloorX())
                        .putInt("y", entry.pos.getFloorY())
                        .putInt("z", entry.pos.getFloorZ())
                        .putInt("t", (int) (entry.delay - totalTime))
                        .putInt("p", entry.priority);
                tileTickTag.add(entryNBT);
            }

            nbt.putList(tileTickTag);
        }

        BinaryStream extraData = new BinaryStream();
        Map<Integer, Integer> extraDataArray = this.getBlockExtraDataArray();
        extraData.putInt(extraDataArray.size());
        for (Integer key : extraDataArray.keySet()) {
            extraData.putInt(key);
            extraData.putShort(extraDataArray.get(key));
        }

        nbt.putByteArray("ExtraData", extraData.getBuffer());

        CompoundTag chunk = new CompoundTag("");
        chunk.putCompound("Level", nbt);
        try {
            return Zlib.deflate(NBTIO.write(chunk, ByteOrder.BIG_ENDIAN), RegionLoader.COMPRESSION_LEVEL);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
