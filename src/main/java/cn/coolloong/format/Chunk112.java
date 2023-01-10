package cn.coolloong.format;

import org.jglrxavpok.hephaistos.collections.ImmutableByteArray;
import org.jglrxavpok.hephaistos.collections.ImmutableIntArray;
import org.jglrxavpok.hephaistos.mcdata.Biome;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;

import java.util.Objects;

public final class Chunk112 {
    private static class Section {
        private final ImmutableByteArray Blocks;//大小为4096
        private final ImmutableByteArray Data;//大小为2048
        private final ImmutableByteArray SkyLight;//大小为2048
        private final ImmutableByteArray BlockLight;//大小为2048

        private Section(NBTCompound sectionTag) {
            this.Blocks = sectionTag.getByteArray("Blocks");
            this.Data = sectionTag.getByteArray("Data");
            this.SkyLight = sectionTag.getByteArray("SkyLight");
            this.BlockLight = sectionTag.getByteArray("BlockLight");
        }

        private Section() {
            this.Blocks = new ImmutableByteArray(4096, integer -> (byte) 0);
            this.Data = new ImmutableByteArray(2048, integer -> (byte) 0);
            this.SkyLight = new ImmutableByteArray(2048, integer -> (byte) 0);
            this.BlockLight = new ImmutableByteArray(2048, integer -> (byte) 0);
        }

        private String getBlockState(int x, int y, int z) {
            int id = Byte.toUnsignedInt(this.Blocks.get((y << 8) + (z << 4) + x));
            int block_data = getBlockData("Data", x, y, z);
            return id + ":" + block_data;
        }

        private int getBlockData(String tagSection, int x, int y, int z) {
            var data = switch (tagSection) {
                case "SkyLight" -> this.SkyLight;
                case "BlockLight" -> this.BlockLight;
                case "Data" -> this.Data;
                default -> null;
            };
            if (data == null) {
                return 0;
            } // section not present, so must be 0
            int index = ((y << 8) + (z << 4) + x) / 2;
            int b = Byte.toUnsignedInt(data.get(index));
            int returnValue;
            if ((x & 1) == 0) {//even block
                returnValue = b & 0x0f;
            } else {//odd block
                returnValue = (b & 0xf0) >>> 4;
            }
            return returnValue;
        }
    }

    private final NBTCompound levelTag;
    private final ImmutableIntArray heightMap;
    private final ImmutableByteArray Biomes;//代表16 x 16 column的生物群系 1.12.2还没有3d生物群系
    private final Section[] sections;

    public Chunk112(NBTCompound nbt) {
        levelTag = nbt.getCompound("Level");
        Biomes = levelTag.getByteArray("Biomes");
        heightMap = Objects.requireNonNull(levelTag.getIntArray("HeightMap"));
        var sectionTags = levelTag.getList("Sections");
        //System.out.println(sectionTags);
        sections = new Section[16];
        var nbt_sections = sectionTags.getValue();
        for (int i = 0; i < nbt_sections.size(); i++) {
            var section = (NBTCompound) nbt_sections.get(i);
            byte yByte = Objects.requireNonNull(section.getByte("Y"));
            sections[yByte] = new Section((NBTCompound) nbt_sections.get(i));
        }
        for (byte i = 0; i < 16; i++) {
            if (sections[i] == null) {
                sections[i] = new Section();
            }
        }
    }

    public String getBlockState(int x, int y, int z) {
        if (!(x >= 0 && x < 16 && y >= 0 && y <= 256 && z >= 0 && z < 16)) {
            return null;
        }
        int section = y >> 4;
        int internalSection = y - (section << 4);
        if (section > sections.length) {
            return "0:0";
        } else {
            return sections[section].getBlockState(x, internalSection, z);
        }
    }

    public int getBlockLight(int x, int y, int z) {
        if (!(x >= 0 && x < 16 && y >= 0 && y <= 256 && z >= 0 && z < 16)) {
            return 0;
        }
        int section = y >> 4;
        int internalSection = y - (section << 4);
        if (section > sections.length) {
            return 0;
        } else {
            return sections[section].getBlockData("BlockLight", x, internalSection, z);
        }
    }


    public int getSkyLight(int x, int y, int z) {
        if (!(x >= 0 && x < 16 && y >= 0 && y <= 256 && z >= 0 && z < 16)) {
            return 0;
        }
        int section = y >> 4;
        int internalSection = y - (section << 4);
        if (section > sections.length) {
            return 0;
        } else {
            return sections[section].getBlockData("SkyLight", x, internalSection, z);
        }
    }

    public int getHeightMap(int rx, int rz) {
        if (rx >= 16 || rx < 0 || rz >= 16 || rz < 0) {
            return -1;
        }
        return heightMap.get((rz << 4) + rx) - 1;
    }

    public long getInhabitedTime() {
        var time = this.levelTag.getLong("InhabitedTime");
        return time == null ? 0L : time;
    }

    public String getBiome(int x, int z) {
        if (x >= 16 || x < 0 || z >= 16 || z < 0) {
            return "minecraft:plains";
        }
        return Biome.Companion.numericalIDToNamespaceID(Byte.toUnsignedInt(Biomes.get((z << 4) + x)));
    }

    public org.jglrxavpok.hephaistos.nbt.NBTList<NBTCompound> getTileEntities() {
        return this.levelTag.getList("TileEntities");
    }
}
