package com.deathmotion.mcsources.config;

public record RunOptions(int jobs, int decompilerThreads, boolean threadsPinned, int prepareJobs,
                         boolean includeSnapshots, String from, String to, int limit) {

    private static final int MAX_DEFAULT_JOBS = 8;
    private static final int MAX_PREPARE_JOBS = 16;
    private static final int CORES_PER_RUN = 8;
    private static final long GIB = 1L << 30;
    private static final long MEMORY_PER_RUN = 5 * GIB;
    private static final long MEMORY_PER_PREPARE = 2 * GIB;

    public static RunOptions fromSystemProperties() {
        int cores = Runtime.getRuntime().availableProcessors();
        long heap = Runtime.getRuntime().maxMemory();
        int defaultJobs = (int) Math.clamp(Math.min(cores / CORES_PER_RUN, heap / MEMORY_PER_RUN), 1, MAX_DEFAULT_JOBS);
        int jobs = Math.max(1, GeneratorProperties.integer("jobs", defaultJobs));
        boolean threadsPinned = GeneratorProperties.text("vfThreads") != null;
        int threads = threadsPinned ? Math.max(1, GeneratorProperties.integer("vfThreads", 1)) : threadsFor(jobs);
        int prepareJobs = (int) Math.clamp(Math.min(cores / 2, heap / MEMORY_PER_PREPARE), 1, MAX_PREPARE_JOBS);
        return new RunOptions(jobs, threads, threadsPinned, prepareJobs,
                GeneratorProperties.flag("snapshots"),
                GeneratorProperties.text("from"),
                GeneratorProperties.text("to"),
                GeneratorProperties.integer("limit", -1));
    }

    public RunOptions sizedFor(int versions) {
        int effectiveJobs = Math.clamp(versions, 1, jobs);
        int threads = threadsPinned ? decompilerThreads : threadsFor(effectiveJobs);
        return new RunOptions(effectiveJobs, threads, threadsPinned, Math.clamp(versions, 1, prepareJobs),
                includeSnapshots, from, to, limit);
    }

    private static int threadsFor(int jobs) {
        return Math.max(2, Runtime.getRuntime().availableProcessors() / jobs);
    }
}
