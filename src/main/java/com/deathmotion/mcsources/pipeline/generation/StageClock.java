package com.deathmotion.mcsources.pipeline.generation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StageClock {
    private final long start = System.nanoTime();
    private final Map<String, Long> stages = new LinkedHashMap<>();

    public <T> T time(String stage, Stage<T> work) throws IOException {
        long begin = System.nanoTime();
        try {
            return work.run();
        } finally {
            stages.put(stage, System.nanoTime() - begin);
        }
    }

    public void time(String stage, VoidStage work) throws IOException {
        time(stage, () -> {
            work.run();
            return null;
        });
    }

    public Map<String, Long> stages() {
        return stages;
    }

    public long elapsed() {
        return System.nanoTime() - start;
    }

    @FunctionalInterface
    public interface Stage<T> {
        T run() throws IOException;
    }

    @FunctionalInterface
    public interface VoidStage {
        void run() throws IOException;
    }
}
