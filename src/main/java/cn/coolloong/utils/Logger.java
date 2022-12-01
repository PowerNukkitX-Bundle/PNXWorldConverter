package cn.coolloong.utils;

import org.fusesource.jansi.Ansi;

public final class Logger {

    public static void error(String text) {
        System.out.println(Ansi.ansi().fgRed().a(text).reset());
    }

    public static void info(String text) {
        System.out.println(Ansi.ansi().fgRgb(0, 204, 102).a(text).reset());
    }

    public static void warn(String text) {
        System.out.println(Ansi.ansi().fgRgb(255, 51, 51).a(text).reset());
    }
}
