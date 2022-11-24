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
            var RegionConvert = new WorldConvert("D:\\Minecraft\\MultiMC\\instances\\1.15.2\\.minecraft\\saves\\新的世界");
            RegionConvert.convert(DimensionEnum.OVERWORLD);
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
        }, 10, 1000);
        WorldConvert.THREAD_POOL_EXECUTOR.shutdown();
        while (!WorldConvert.THREAD_POOL_EXECUTOR.isTerminated()) {
        }
        try {
            Thread.sleep(1000);
            System.out.println("All completed,Sum Time Consuming: " + RUN_SET.stream()
                    .map(run -> Long.parseLong(run.getTimeConsume().replace("ms", "")))
                    .max(Long::compareTo).get() + "ms");
            close(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void close(int status) {
        TIMER.cancel();
        WorldConvert.THREAD_POOL_EXECUTOR.shutdownNow();
        System.exit(status);
    }
}
