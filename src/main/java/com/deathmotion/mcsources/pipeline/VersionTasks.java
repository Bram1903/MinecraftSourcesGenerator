package com.deathmotion.mcsources.pipeline;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class VersionTasks {
    private VersionTasks() {
    }

    static void run(String stage, List<String> ids, int jobs, Task task, Map<String, String> failures) {
        AtomicInteger finished = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(jobs)) {
            for (String id : ids) {
                pool.execute(() -> {
                    long start = System.nanoTime();
                    try {
                        String detail = task.run(id);
                        Console.line(stage, "%d/%d %s%s in %s", finished.incrementAndGet(), ids.size(), id,
                                detail.isEmpty() ? "" : ", " + detail, Console.since(start));
                    } catch (Throwable failure) {
                        finished.incrementAndGet();
                        failures.put(id, Console.describe(failure));
                        Console.line(stage, "%s FAILED: %s", id, Console.describe(failure));
                    }
                });
            }
        }
    }

    @FunctionalInterface
    interface Task {
        String run(String id) throws Exception;
    }
}
