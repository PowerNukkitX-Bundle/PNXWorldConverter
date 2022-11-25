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
    public static Thread exitThread;
    private static boolean interrupted = false;

    public static void main(String[] args) {
        var mainClass = new PNXWorldConverter();
        var status = new CommandLine(mainClass).execute(args);
        if (status == 0) {
            init();
            var RegionConvert = new WorldConvert(mainClass.target);
            RegionConvert.convert(mainClass.dimensionEnum);
            PNXWorldConverter.THREAD_POOL_EXECUTOR.shutdown();
            while (!PNXWorldConverter.THREAD_POOL_EXECUTOR.isTerminated()) {
            }
            if (!interrupted) {
                close(0);
            } else {
                close(1);
            }
        }
        System.exit(0);
    }

    public static void close(int status) {
        if (status == 0) {
            Runtime.getRuntime().removeShutdownHook(exitThread);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            RUN_SET.stream()
                    .map(run -> Long.parseLong(run.getTimeConsume().replace("ms", "")))
                    .max(Long::compareTo).ifPresent(s -> System.out.println("All completed,Sum Time Consuming: " + s + "ms"));
            System.out.println("complete!");
        } else if (status == 1) {
            System.out.println("Convert interrupt!");
        }
        TIMER.cancel();
        PNXWorldConverter.THREAD_POOL_EXECUTOR.shutdownNow();
        System.exit(status);
    }

    public static void init() {
        try {
            exitThread = new ExitHandler();
            Runtime.getRuntime().addShutdownHook(exitThread);
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
                    RUN_SET.stream().map(run -> {
                        if (!run.isDone()) return run.getName() + ": " + run.getProgress();
                        else return run.getName() + ": Done!" + "Time consuming: " + run.getTimeConsume();
                    }).reduce((a, b) -> a + '\n' + b).ifPresent(s -> System.out.println("---\n" + s));
                }
            }, 500, 1000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer call() {
        if (dimensionEnum == null || target == null) {
            return 1;
        }
        return 0;
    }

    private static class ExitHandler extends Thread {
        public ExitHandler() {
            super("CTRLC Handler");
        }

        public void run() {
            System.out.println("Detect ctrl+c to interrupt a running task.");
            PNXWorldConverter.RUN_SET.forEach(task -> {
                if (!task.isDone()) {
                    task.setInterrupted();
                }
            });
            PNXWorldConverter.interrupted = true;
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
