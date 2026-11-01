package com.deathmotion.mcsources.pipeline;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class StageClock {
    private final long start = System.nanoTime();
    private final Map<String, Long> stages = new LinkedHashMap<>();

    <T> T time(String stage, Stage<T> work) throws IOException {
        long begin = System.nanoTime();
        try {
            return work.run();
        } finally {
            stages.put(stage, System.nanoTime() - begin);
        }
    }

    void time(String stage, VoidStage work) throws IOException {
        time(stage, () -> {
            work.run();
            return null;
        });
    }

    Map<String, Long> stages() {
        return stages;
    }

    long elapsed() {
        return System.nanoTime() - start;
    }

    @FunctionalInterface
    interface Stage<T> {
        T run() throws IOException;
    }

    @FunctionalInterface
    interface VoidStage {
        void run() throws IOException;
    }
}
