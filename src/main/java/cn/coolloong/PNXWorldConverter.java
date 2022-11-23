package cn.coolloong;

import cn.nukkit.block.Block;
import cn.nukkit.level.DimensionEnum;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class PNXWorldConverter {
    private static final Set<RegionConvertWork> RUN_SET = new HashSet<>();
    private static final Timer TIMER = new Timer();

    public static void main(String[] args) {
        try {
            Block.init();
            Class.forName("cn.nukkit.level.Level");
            var RegionConvert = new WorldConvert("D:\\Minecraft\\java mc\\.minecraft\\versions\\1.16.5\\saves\\test");
            RegionConvert.convert(DimensionEnum.NETHER);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        TIMER.schedule(new TimerTask() {
            @Override
            public void run() {
                for (var task : WorldConvert.tasks) {
                    if (task.getNowThread() != null) {
                        RUN_SET.add(task);
                    }
                }
                var log = "---\n" + RUN_SET.stream().map(run -> {
                    if (!run.isDone()) return run.getName() + ": " + run.getProgress();
                    else return run.getName() + ": Done!" + "Time consuming: " + run.getTimeConsume();
                }).reduce((a, b) -> a + '\n' + b).get();
                System.out.println(log);
            }
        }, 10, 500);
        WorldConvert.THREAD_POOL_EXECUTOR.shutdown();
        while (!WorldConvert.THREAD_POOL_EXECUTOR.isTerminated()) {
        }
        try {
            Thread.sleep(1000);
            TIMER.cancel();
            WorldConvert.THREAD_POOL_EXECUTOR.shutdownNow();
            System.out.println("All completed,Sum Time Consuming: " + RUN_SET.stream()
                    .map(run -> Long.parseLong(run.getTimeConsume().replace("ms", "")))
                    .max(Long::compareTo).get() + "ms");
            System.exit(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
