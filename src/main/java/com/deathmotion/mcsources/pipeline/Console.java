package com.deathmotion.mcsources.pipeline;

import java.util.Locale;

public final class Console {
    private Console() {
    }

    public static synchronized void line(String stage, String format, Object... arguments) {
        System.out.println("[" + stage + "] " + String.format(Locale.ROOT, format, arguments));
    }

    public static String since(long startNanos) {
        return seconds(System.nanoTime() - startNanos);
    }

    public static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.1fs", nanos / 1e9);
    }

    public static String describe(Throwable failure) {
        StringBuilder builder = new StringBuilder(String.valueOf(failure));
        for (Throwable cause = failure.getCause(); cause != null && cause != failure; cause = cause.getCause()) {
            builder.append(" <- ").append(cause);
        }
        return builder.toString();
    }
}
