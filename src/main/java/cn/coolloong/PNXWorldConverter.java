package cn.coolloong;

import cn.coolloong.convert.RegionConvertWork;
import cn.coolloong.convert.WorldConvert;
import cn.nukkit.block.Block;
import cn.nukkit.level.DimensionEnum;
import picocli.CommandLine;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

@CommandLine.Command(description = "Usage examples: \n" +
        "java -jar pnxworldconvert.jar -t D:mc\\.minecraft\\save\\new world -d OVERWORLD")
public class PNXWorldConverter implements Callable<Integer> {
    @CommandLine.Option(names = {"-t", "--target"}, paramLabel = "PATH", description = "convert target path")
    private String target;

    @CommandLine.Option(names = {"-d", "--dimension"}, paramLabel = "Dimension", description = "Valid values: ${COMPLETION-CANDIDATES}")
    private DimensionEnum dimensionEnum;

    private static final Set<RegionConvertWork> RUN_SET = new HashSet<>();
    private static final Timer TIMER = new Timer();
    public static ForkJoinPool THREAD_POOL_EXECUTOR;

    public static void main(String[] args) {
        var mainClass = new PNXWorldConverter();
        var status = new CommandLine(mainClass).execute(args);
        if (status == 1) {
            init();
            var RegionConvert = new WorldConvert(mainClass.target);
            RegionConvert.convert(mainClass.dimensionEnum);
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
        System.exit(1);
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
            THREAD_POOL_EXECUTOR = (ForkJoinPool) Executors.newWorkStealingPool();
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
            }, 500, 1000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer call() {
        if (dimensionEnum == null || target == null) {
            System.out.println("");
            return 0;
        }
        return 1;
    }
}
