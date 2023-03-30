package cn.coolloong.convert;

import cn.coolloong.PNXWorldConverter;
import cn.coolloong.SupportVersion;
import cn.coolloong.format.Chunk112;
import cn.coolloong.proxy.ProxyChunk;
import cn.coolloong.proxy.ProxyRegionLoader;
import cn.coolloong.utils.DataConvert;
import cn.coolloong.utils.Logger;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.format.LevelProvider;
import org.jglrxavpok.hephaistos.mca.AnvilException;
import org.jglrxavpok.hephaistos.mca.RegionFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * support 1.12 region
 */
public class OldRegionConvertWork extends RegionConvertWork {


    public OldRegionConvertWork(File mca, LevelProvider levelProvider, DimensionEnum dimension, SupportVersion version) {
        super(mca, levelProvider, dimension, version);
    }

    @Override
    public void run() {
        var time = System.currentTimeMillis();
        var mcaName = mca.getName().split("\\.");
        int regionX = Integer.parseInt(mcaName[1]);
        int regionZ = Integer.parseInt(mcaName[2]);

        int miny = 0;
        int maxy = switch (dimension) {
            case OVERWORLD, END -> 256;
            case NETHER -> 128;
        };

        ProxyRegionLoader pnxRegion;
        RegionFile region;
        try {
            region = new RegionFile(new RandomAccessFile(mca, "r"), regionX, regionZ);
            pnxRegion = new ProxyRegionLoader(levelProvider, regionX, regionZ);
        } catch (IOException | AnvilException e) {
            this.progress = -1;
            Logger.warn("An error occurred while reading r." + regionX + "." + regionZ + ".mca!");
            return;
        }
        try {
            for (int rx = regionX * 32; rx < regionX * 32 + 32; ++rx) {
                for (int rz = regionZ * 32; rz < regionZ * 32 + 32; ++rz) {
                    if (nowThread.isInterrupted()) throw new InterruptedException();
                    progress++;
                    //x + region *32 z + region *32
                    var chunkNBT = region.getChunkData(rx, rz);
                    if (chunkNBT == null) {
                        var pnxChunk = ProxyChunk.getEmptyChunk(rx, rz, levelProvider, dimension, 0, false, false);
                        if (pnxChunk == null) PNXWorldConverter.close(1);//error exit
                        //noinspection ConstantConditions
                        pnxChunk.initChunk();
                        pnxRegion.saveChunk(rx & 31, rz & 31, pnxChunk.toBinary());
                        continue;
                    }

                    //debug
//                    if(chunkNBT.getCompound("Level").getList("TileEntities").isNotEmpty()){
//                        Files.writeString(Path.of("target/mca" + rx + ";" + rz+".json"), chunkNBT.toSNBT(), StandardCharsets.UTF_8);
//                    }
                    var chunk112 = new Chunk112(chunkNBT);
                    var pnxChunk = ProxyChunk.getEmptyChunk(rx, rz, levelProvider, dimension, chunk112.getInhabitedTime(), true, true);
                    if (pnxChunk == null) System.exit(0);//error exit
                    for (int x = 0; x < 16; ++x) {
                        for (int z = 0; z < 16; ++z) {
                            pnxChunk.setBiomeId(x, z, DataConvert.convertBiomes(chunk112.getBiome(x, z)));
                            for (int y = miny; y < maxy; ++y) {
                                var state = DataConvert.convertLegacyId(chunk112.getBlockState(x, y, z));
                                var pnxBlock = DataConvert.convertBlockState(state);
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

                                pnxChunk.setBlockLight(x, y, z, chunk112.getBlockLight(x, y, z));
                                pnxChunk.setBlockSkyLight(x, y, z, chunk112.getSkyLight(x, y, z));
                            }
                        }
                    }
//                    chunkColumn.getTileEntities().forEach((Consumer<? super NBTCompound>) nbt-> System.out.println(nbt.toSNBT()));
                    DataConvert.convertTileEntities(pnxChunk,
                            (x, y, z) -> DataConvert.convertLegacyId(chunk112.getBlockState(x, y, z)),
                            chunk112.getTileEntities(), version);
                    pnxRegion.saveChunk(rx & 31, rz & 31, pnxChunk.toBinary());
                }
            }
            timeConsume = (System.currentTimeMillis() - time + "ms");
        } catch (InterruptedException e) {
            Logger.warn("Task ：" + mca.getName() + " interrupted!");
            try {
                region.close();
                pnxRegion.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } catch (Exception e) {
            this.progress = -1;
            e.printStackTrace();
        }
    }
}
