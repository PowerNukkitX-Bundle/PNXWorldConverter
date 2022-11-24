package cn.coolloong;

import cn.coolloong.proxy.ProxyChunk;
import cn.coolloong.proxy.ProxyRegionLoader;
import cn.coolloong.utils.DataConvert;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.format.LevelProvider;
import org.jglrxavpok.hephaistos.mca.AnvilException;
import org.jglrxavpok.hephaistos.mca.ChunkColumn;
import org.jglrxavpok.hephaistos.mca.RegionFile;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RegionConvertWork implements Runnable {
    private final File mca;
    private final LevelProvider levelProvider;
    private final DimensionEnum dimension;
    private Thread nowThread;
    private int progress = 0;
    private String timeConsume;

    public RegionConvertWork(File mca, LevelProvider levelProvider, DimensionEnum dimension) {
        this.mca = mca;
        this.levelProvider = levelProvider;
        this.dimension = dimension;
    }

    @Override
    public void run() {
        nowThread = Thread.currentThread();
        System.out.println("Starting convert  " + mca.getName());
        var time = System.currentTimeMillis();
        var mcaName = mca.getName().split("\\.");
        int regionX = Integer.parseInt(mcaName[1]);
        int regionZ = Integer.parseInt(mcaName[2]);

        ProxyRegionLoader pnxRegion;
        RegionFile region;
        try {
            region = new RegionFile(new RandomAccessFile(mca, "r"), regionX, regionZ);
            pnxRegion = new ProxyRegionLoader(levelProvider, regionX, regionZ);
        } catch (IOException | AnvilException e) {
            throw new RuntimeException(e);
        }
        try {
            end:
            for (int rx = regionX * 32; rx < regionX * 32 + 32; ++rx) {
                for (int rz = regionZ * 32; rz < regionZ * 32 + 32; ++rz) {
                    if (nowThread.isInterrupted()) throw new InterruptedException();
                    progress++;
                    //x + region *32 z + region *32
                    ChunkColumn chunkColumn = null;
                    try {
                        chunkColumn = region.getChunk(rx, rz);
                    } catch (AnvilException e) {
                        var chunk = region.getChunkData(rx, rz);
                        if (chunk != null) {
                            //todo 从NBT转换MC_1_15版本一下的区块
                            var sections = chunk.getCompound("Level").getList("Sections");
                            //代表16 x 16 column的生物群系 1.12.2还没有3d生物群系
                            var biome = chunk.getCompound("Level").getByteArray("Biomes");
                            System.out.println(biome.getSize());
                            var section = (NBTCompound) sections.get(0);
                            //一个section 16x16x16
                            //blocks 大小为4096 坐标排序方式为yzx
                            //data大小为2048
                            //SkyLight大小为2048
                            Files.writeString(Path.of("target/chunk" + rx + ";" + rz), region.getChunkData(rx, rz).toString(), StandardCharsets.UTF_8);
                        }
                    }
                    if (chunkColumn == null) {
                        var pnxChunk = ProxyChunk.getEmptyChunk(rx, rz, levelProvider, dimension, 0, false, false);
                        if (pnxChunk == null) PNXWorldConverter.close(0);//error exit
                        //noinspection ConstantConditions
                        pnxChunk.initChunk();
                        pnxRegion.saveChunk(rx & 31, rz & 31, pnxChunk.toBinary());
                        continue;
                    }
                    int miny = dimension.equals(DimensionEnum.NETHER) ? 0 : chunkColumn.getMinY();
                    int maxy = dimension.equals(DimensionEnum.NETHER) ? 128 : chunkColumn.getMaxY();
                    var pnxChunk = ProxyChunk.getEmptyChunk(rx, rz, levelProvider, dimension, chunkColumn.getInhabitedTime(), true, true);
                    if (pnxChunk == null) System.exit(0);//error exit
                    for (int x = 0; x < 16; ++x) {
                        for (int z = 0; z < 16; ++z) {
                            for (int y = miny; y < maxy; ++y) {
                                var state = chunkColumn.getBlockState(x, y, z);
                                var pnxBlock = DataConvert.convertBlockState(state);
                                pnxChunk.setBiomeId(x, y, z, DataConvert.convertBiomes(chunkColumn.getBiome(x, y, z)));
                                switch (pnxBlock.getPersistenceName()) {
                                    //特殊处理海草 海带
                                    case "minecraft:kelp", "minecraft:seagrass", "minecraft:bubble_column" -> {
                                        pnxChunk.setBlockStateAt(x, y, z, pnxBlock);
                                        pnxChunk.setBlockAtLayer(x, y, z, 1, 9);
                                    }
                                    default -> pnxChunk.setBlockStateAt(x, y, z, pnxBlock);
                                }
                                //处理所有的含水方块
                                if (state.getProperties().containsKey("waterlogged") && Boolean.parseBoolean(state.getProperties().get("waterlogged"))) {
                                    pnxChunk.setBlockAtLayer(x, y, z, 1, 9);
                                }
                            }
                        }
                    }

                    for (byte section = (byte) (chunkColumn.getMinY() >> 4); section < (byte) (chunkColumn.getMaxY() >> 4); ++section) {
                        var sec = chunkColumn.getSection(section);
                        if (sec.getEmpty()) continue;
                        for (int i = 0; i < 16; ++i) {//x
                            for (int j = 0; j < 16; ++j) {//y
                                for (int k = 0; k < 16; ++k) {//z
                                    if (sec.getBlockLights().length != 0) {
                                        pnxChunk.setBlockLight(i, section * 16 + j, k, sec.getBlockLight(i, j, k));
                                    }
                                    if (sec.getSkyLights().length != 0) {
                                        pnxChunk.setBlockSkyLight(i, section * 16 + j, k, sec.getSkyLight(i, j, k));
                                    }
                                }
                            }
                        }
                    }
//                    chunkColumn.getTileEntities().forEach((Consumer<? super NBTCompound>) nbt-> System.out.println(nbt.toSNBT()));
                    DataConvert.convertTileEntities(pnxChunk, chunkColumn, chunkColumn.getTileEntities());
                    pnxRegion.saveChunk(rx & 31, rz & 31, pnxChunk.toBinary());
                    region.forget(chunkColumn);
                }
            }
            timeConsume = (System.currentTimeMillis() - time + "ms");
        } catch (InterruptedException e) {
            System.out.println("Task ：" + mca.getName() + " interrupted!");
            try {
                region.close();
                pnxRegion.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } catch (AnvilException | IOException e) {
            e.printStackTrace();
        }
    }

    public String getProgress() {
        var str = String.valueOf(progress * 0.09765625);
        return str.substring(0, Math.min(4, str.length())) + "%";
    }

    public boolean isDone() {
        return progress == 1024;
    }

    public void setInterrupted() {
        nowThread.interrupt();
    }

    public Thread getNowThread() {
        return nowThread;
    }

    public String getName() {
        return this.mca.getName();
    }

    public String getTimeConsume() {
        return timeConsume;
    }
}
