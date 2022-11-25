package cn.coolloong;

import cn.coolloong.convert.RegionConvertWork;
import cn.coolloong.convert.WorldConvert;
import cn.nukkit.block.Block;
import cn.nukkit.level.DimensionEnum;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

public class PNXWorldConverter {
    private static final Set<RegionConvertWork> RUN_SET = new HashSet<>();
    private static final Timer TIMER = new Timer();
    public static final ForkJoinPool THREAD_POOL_EXECUTOR = (ForkJoinPool) Executors.newWorkStealingPool();

    static {
        init();
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
        }, 1000, 1000);
    }

    public static void main(String[] args) {
        var RegionConvert = new WorldConvert("D:\\Minecraft\\MultiMC\\instances\\1.7.10\\.minecraft\\saves\\新的世界");
        RegionConvert.convert(DimensionEnum.OVERWORLD);
        PNXWorldConverter.THREAD_POOL_EXECUTOR.shutdown();
        while (!PNXWorldConverter.THREAD_POOL_EXECUTOR.isTerminated()) {
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
        PNXWorldConverter.THREAD_POOL_EXECUTOR.shutdownNow();
        System.exit(status);
    }

    public static void init() {
        try {
            Block.init();
            Class.forName("cn.nukkit.level.Level");
            Class.forName("cn.coolloong.utils.DataConvert");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
