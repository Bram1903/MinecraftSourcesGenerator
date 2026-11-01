package com.deathmotion.mcsources.config;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;

public record RunOptions(int jobs, int decompilerThreads, boolean threadsPinned, String decompilerHeap,
                         boolean includeSnapshots, String from, String to, int limit) {

    public static final String DEFAULT_DECOMPILER_HEAP = "4g";

    private static final int MAX_DEFAULT_JOBS = 8;
    private static final long GIB = 1L << 30;
    private static final long RESERVED_MEMORY = 4 * GIB;
    private static final long PER_JOB_OVERHEAD = 3 * GIB / 2;

    public static RunOptions fromSystemProperties() {
        int cores = Runtime.getRuntime().availableProcessors();
        String heap = GeneratorProperties.text("vfHeap", DEFAULT_DECOMPILER_HEAP);
        int jobs = Math.max(1, GeneratorProperties.integer("jobs", defaultJobs(cores, heap)));
        boolean threadsPinned = GeneratorProperties.text("vfThreads") != null;
        int threads = threadsPinned ? Math.max(1, GeneratorProperties.integer("vfThreads", 1)) : threadsFor(jobs);
        return new RunOptions(jobs, threads, threadsPinned, heap,
                GeneratorProperties.flag("snapshots"),
                GeneratorProperties.text("from"),
                GeneratorProperties.text("to"),
                GeneratorProperties.integer("limit", -1));
    }

    public RunOptions sizedFor(int versions) {
        int effectiveJobs = Math.clamp(versions, 1, jobs);
        int threads = threadsPinned ? decompilerThreads : threadsFor(effectiveJobs);
        return new RunOptions(effectiveJobs, threads, threadsPinned, decompilerHeap, includeSnapshots, from, to, limit);
    }

    private static int threadsFor(int jobs) {
        return Math.max(2, Runtime.getRuntime().availableProcessors() / jobs);
    }

    private static int defaultJobs(int cores, String heap) {
        int byCores = Math.clamp(cores / 4, 1, MAX_DEFAULT_JOBS);
        long totalMemory = totalMemory();
        if (totalMemory <= 0) {
            return byCores;
        }
        long perJob = parseMemory(heap) + PER_JOB_OVERHEAD;
        long byMemory = (totalMemory - RESERVED_MEMORY) / perJob;
        return (int) Math.clamp(Math.min(byCores, byMemory), 1, MAX_DEFAULT_JOBS);
    }

    private static long totalMemory() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean) {
            return bean.getTotalMemorySize();
        }
        return -1;
    }

    static long parseMemory(String value) {
        String normalized = value.trim().toLowerCase();
        char unit = normalized.charAt(normalized.length() - 1);
        long multiplier = switch (unit) {
            case 'k' -> 1L << 10;
            case 'm' -> 1L << 20;
            case 'g' -> GIB;
            case 't' -> GIB << 10;
            default -> 1L;
        };
        String digits = Character.isDigit(unit) ? normalized : normalized.substring(0, normalized.length() - 1);
        try {
            return Long.parseLong(digits) * multiplier;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("-PvfHeap must look like 4g or 3072m, got '" + value + "'");
        }
    }
}
