package cn.coolloong.utils;

import cn.coolloong.PNXWorldConverter;
import cn.coolloong.SupportVersion;
import cn.coolloong.convert.OldRegionConvertWork;
import cn.coolloong.convert.RegionConvertWork;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.format.LevelProvider;
import org.jglrxavpok.hephaistos.mca.AnvilException;
import org.jglrxavpok.hephaistos.mca.RegionFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class ConvertWorkFactory {
    private static SupportVersion tmpVersion;

    public static RegionConvertWork make(File mca, LevelProvider levelProvider, DimensionEnum dimension) throws IOException {
        var mcaName = mca.getName().split("\\.");
        int regionX = Integer.parseInt(mcaName[1]);
        int regionZ = Integer.parseInt(mcaName[2]);
        RegionFile region = null;

        if (tmpVersion == null) {
            try {
                region = new RegionFile(new RandomAccessFile(mca, "r"), regionX, regionZ);
                end:
                for (int rx = regionX * 32; rx < regionX * 32 + 32; ++rx) {
                    for (int rz = regionZ * 32; rz < regionZ * 32 + 32; ++rz) {
                        var chunkData = region.getChunkData(rx, rz);
                        if (chunkData != null) {
                            //noinspection ConstantConditions
                            if (chunkData.contains("DataVersion")) {
                                tmpVersion = SupportVersion.selectVersion(chunkData.getInt("DataVersion"));
                            } else tmpVersion = SupportVersion.MC_OLD;
                            break end;
                        }
                    }
                }
            } catch (AnvilException | IOException e) {
                System.out.println("read chunk version information error !!!");
                region.close();
                PNXWorldConverter.close(0);
            }
        }

        var result = switch (tmpVersion) {
            case MC_OLD -> new OldRegionConvertWork(mca, levelProvider, dimension, tmpVersion);
            case MC_NEW -> new RegionConvertWork(mca, levelProvider, dimension, tmpVersion);
        };
        if (result == null) {
            System.out.println("error,unknown region format!!!");
            region.close();
            PNXWorldConverter.close(0);
        }
        return result;
    }
}
