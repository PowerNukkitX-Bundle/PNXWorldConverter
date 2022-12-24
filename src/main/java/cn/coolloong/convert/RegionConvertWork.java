package cn.coolloong.convert;

import cn.coolloong.PNXWorldConverter;
import cn.coolloong.SupportVersion;
import cn.coolloong.proxy.ProxyChunk;
import cn.coolloong.proxy.ProxyRegionLoader;
import cn.coolloong.utils.DataConvert;
import cn.coolloong.utils.Logger;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.format.LevelProvider;
import org.jglrxavpok.hephaistos.mca.AnvilException;
import org.jglrxavpok.hephaistos.mca.ChunkColumn;
import org.jglrxavpok.hephaistos.mca.RegionFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;

/**
 * support 1.15-1.19 region
 */
public class RegionConvertWork implements Runnable {
    protected final File mca;
    protected final LevelProvider levelProvider;
    protected final DimensionEnum dimension;
    protected final Thread nowThread;
    protected final SupportVersion version;
    protected int progress = 0;
    protected String timeConsume = "0ms";

    public RegionConvertWork(File mca, LevelProvider levelProvider, DimensionEnum dimension, SupportVersion version) {
        this.nowThread = Thread.currentThread();
        this.mca = mca;
        this.levelProvider = levelProvider;
        this.dimension = dimension;
        this.version = version;
        Logger.info("Starting convert  " + mca.getName());
    }

    @Override
    public void run() {
        var time = System.currentTimeMillis();
        var mcaName = mca.getName().split("\\.");
        int regionX = Integer.parseInt(mcaName[1]);
        int regionZ = Integer.parseInt(mcaName[2]);
        ProxyRegionLoader pnxRegion;
        RegionFile region;
        try {
            region = new RegionFile(new RandomAccessFile(mca, "rw"), regionX, regionZ);
            pnxRegion = new ProxyRegionLoader(levelProvider, regionX, regionZ);
        } catch (IOException | AnvilException e) {
            this.progress = -1;
            Logger.warn("An error occurred while reading r." + regionX + "." + regionZ + ".mca!");
            return;
        }
        try {
            end:
            for (int rx = regionX * 32; rx < regionX * 32 + 32; ++rx) {
                for (int rz = regionZ * 32; rz < regionZ * 32 + 32; ++rz) {
                    if (nowThread.isInterrupted()) throw new InterruptedException();
                    progress++;
                    ChunkColumn chunkColumn = null;
                    try {
                        chunkColumn = region.getChunk(rx, rz);
                    } catch (IllegalArgumentException ignored) {
                    } catch (AnvilException e) {
                        if (e.getMessage().contains("Missing field named 'xPos'") || e.getMessage().contains("Missing field named 'zPos'")) {
                            var data = region.getChunkData(rx, rz).toMutableCompound();
                            data.setInt("xPos", region.getChunkData(rx, rz).getCompound("Level").getInt("xPos"));
                            data.setInt("zPos", region.getChunkData(rx, rz).getCompound("Level").getInt("zPos"));
                            data.setString("Status", "spawn");
                            chunkColumn = new ChunkColumn(data.toCompound());
                        } else continue;
                    }
                    if (chunkColumn == null) {
                        var pnxChunk = ProxyChunk.getEmptyChunk(rx, rz, levelProvider, dimension, 0, false, false);
                        if (pnxChunk == null) PNXWorldConverter.close(1);//error exit
                        //noinspection ConstantConditions
                        pnxChunk.initChunk();
                        pnxRegion.saveChunk(rx & 31, rz & 31, pnxChunk.toBinary());
                        continue;
                    }

                    //debug
//                    if(region.getChunkData(rx,rz)!=null){
//                        Files.writeString(Path.of("target/mca" + rx + ";" + rz + ".json"), region.getChunkData(rx, rz).toSNBT(), StandardCharsets.UTF_8);
//                    }

                    int miny = Math.max(-64, dimension.equals(DimensionEnum.NETHER) ? 0 : chunkColumn.getMinY());
                    int maxy = Math.min(319, dimension.equals(DimensionEnum.NETHER) ? 128 : chunkColumn.getMaxY());
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
                    DataConvert.convertTileEntities(pnxChunk, chunkColumn::getBlockState, chunkColumn.getTileEntities(), version);
                    pnxRegion.saveChunk(rx & 31, rz & 31, pnxChunk.toBinary());
                    try {
                        region.forget(chunkColumn);
                    } catch (IllegalArgumentException ignore) {
                    }
                }
            }
            timeConsume = (System.currentTimeMillis() - time + "ms");
        } catch (InterruptedException e) {
            Logger.warn("Task ：" + mca.getName() + " interrupted!");
            try {
                region.close();
                pnxRegion.close();
                File delete = null;
                if (dimension.equals(DimensionEnum.OVERWORLD)) {
                    delete = new File("output/world/region/" + mca.getName());
                } else if (dimension.equals(DimensionEnum.NETHER)) {
                    delete = new File("output/nether/region/" + mca.getName());
                } else if (dimension.equals(DimensionEnum.END)) {
                    delete = new File("output/the_end/region/" + mca.getName());
                }
                if (delete.exists()) Files.delete(delete.toPath());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } catch (AnvilException | IOException e) {
            this.progress = -1;
            e.printStackTrace();
        }
    }

    public String getProgress() {
        var str = String.valueOf(progress * 0.09765625);
        return str.substring(0, Math.min(4, str.length())) + "%";
    }

    public int getStatus() {
        return switch (progress) {
            case -1 -> 0;
            case 0 -> 1;
            case 1024 -> 3;
            default -> 2;
        };
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
