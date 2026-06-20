package com.deathmotion.mcsources.config;

public record RunOptions(int jobs, int decompilerThreads, boolean includeSnapshots, String from, String to, int limit) {

    private static final int MAX_DEFAULT_JOBS = 8;

    public static RunOptions fromSystemProperties() {
        int cores = Runtime.getRuntime().availableProcessors();
        int jobs = intProperty("mcsg.jobs", Math.clamp(cores / 4, 1, MAX_DEFAULT_JOBS));
        int threads = intProperty("mcsg.vfThreads", Math.max(2, cores / jobs));
        boolean snapshots = Boolean.parseBoolean(System.getProperty("mcsg.snapshots", "false"));
        String from = blankToNull(System.getProperty("mcsg.from"));
        String to = blankToNull(System.getProperty("mcsg.to"));
        int limit = intProperty("mcsg.limit", -1);
        return new RunOptions(jobs, threads, snapshots, from, to, limit);
    }

    private static int intProperty(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
