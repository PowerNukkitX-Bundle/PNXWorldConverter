package cn.coolloong;

import cn.coolloong.convert.RegionConvertWork;
import cn.coolloong.convert.WorldConvert;
import cn.coolloong.utils.Logger;
import cn.nukkit.block.Block;
import cn.nukkit.level.DimensionEnum;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

@CommandLine.Command(name = "", description = "Usage examples: \n" +
        "java -jar pnxworldconvert.jar -t D:mc\\.minecraft\\save\\new world -d OVERWORLD")
public class PNXWorldConverter implements Callable<Integer> {
    @CommandLine.Option(names = {"-t", "--target"}, paramLabel = "PATH", description = "The path of convert target world")
    private String target;

    @CommandLine.Option(names = {"-d", "--dimension"}, paramLabel = "Dimension", description = "Valid values: ${COMPLETION-CANDIDATES}")
    private DimensionEnum dimensionEnum;

    private static final Set<RegionConvertWork> RUN_SET = new HashSet<>();
    private static final Set<RegionConvertWork> COMPLETE_SET = new HashSet<>();
    private static final Set<RegionConvertWork> ERROR_SET = new HashSet<>();
    private static final Timer TIMER = new Timer();
    public static ForkJoinPool THREAD_POOL_EXECUTOR;
    public static Thread exitThread;
    private static boolean interrupted = false;

    public static void main(String[] args) {
        var mainClass = new PNXWorldConverter();
        if (args.length == 0) {
            Logger.info(new CommandLine(mainClass).getUsageMessage());
            System.exit(0);
        }
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
            Logger.info("End,Summary:\n");
            Logger.info("Task Number: " + WorldConvert.TASKS.size());
            Logger.info("Success Task Number: " + COMPLETE_SET.size());
            Logger.info("Error Task Number: " + ERROR_SET.size());
            RUN_SET.stream()
                    .map(run -> Long.parseLong(run.getTimeConsume().replace("ms", "")))
                    .max(Long::compareTo).ifPresent(s -> Logger.info("Sum Time Consuming: " + s + "ms"));
            TIMER.cancel();
        } else if (status == 1) {
            Logger.warn("Convert interrupt!");
        }
        PNXWorldConverter.THREAD_POOL_EXECUTOR.shutdownNow();
        System.exit(status);
    }

    public static void init() {
        try {
            exitThread = new ExitHandler();
            Runtime.getRuntime().addShutdownHook(exitThread);
            Block.init();
            AnsiConsole.systemInstall();
            Class.forName("cn.nukkit.level.Level");
            Class.forName("cn.coolloong.utils.DataConvert");
            THREAD_POOL_EXECUTOR = (ForkJoinPool) Executors.newWorkStealingPool();
            TIMER.schedule(new TimerTask() {
                @Override
                public void run() {
                    for (var task : WorldConvert.TASKS) {
                        switch (task.getStatus()) {
                            case 0 -> {
                                ERROR_SET.add(task);
                                RUN_SET.remove(task);
                            }
                            case 1, 2 -> RUN_SET.add(task);
                            case 3 -> {
                                RUN_SET.remove(task);
                                COMPLETE_SET.add(task);
                            }
                        }
                    }
                    var s = RUN_SET.stream().filter(t -> t.getStatus() > 1).map(run -> run.getName() + ": " + run.getProgress()).reduce(Ansi.ansi().fgRgb(0, 255, 255).a("—".repeat(50)).reset().toString(), (a, b) -> a + "\n" + "  |  " + b);
                    System.out.println(s);
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
            super("CTRL-C-Handler");
        }

        public void run() {
            TIMER.cancel();
            PNXWorldConverter.RUN_SET.forEach(t -> {
                if (t.getStatus() != 3) t.setInterrupted();
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
