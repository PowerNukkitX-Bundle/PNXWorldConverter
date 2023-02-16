import cn.nukkit.block.Block;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.anvil.RegionLoader;

import java.util.Scanner;

public class SlimChunk {
    public final static String path = "C:/Users/wyd/Downloads/RPG/";
    public final static String output = "C:/Users/wyd/Downloads/output/";


    public static void main(String[] args) throws Exception {
        Block.init();
        Scanner scanner = new Scanner(System.in);
        System.out.println("输入region坐标X:");
        int x = scanner.nextInt();
        System.out.println("输入region坐标Z:");
        int z = scanner.nextInt();
        Anvil format = new Anvil(null, path);
        Anvil target = new Anvil(null, output);
        RegionLoader regionLoader = new ProxyRegionLoader(format, x, z);
        RegionLoader targetRegionLoader = new ProxyRegionLoader(target, x, z);
        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                Chunk chunk = regionLoader.readChunk(i, j);
                if (chunk != null) {
                    targetRegionLoader.writeChunk(chunk);
                }
            }
        }
        targetRegionLoader.close();
        target.saveChunks();
        target.close();
        System.exit(0);
    }
}
