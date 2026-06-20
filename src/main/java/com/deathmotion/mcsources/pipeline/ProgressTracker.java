package com.deathmotion.mcsources.pipeline;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressTracker {
    private final Map<String, Phase> phases = new ConcurrentHashMap<>();

    public void enter(String id, Phase phase) {
        phases.put(id, phase);
    }

    public void finish(String id) {
        phases.remove(id);
    }

    public String summary() {
        EnumMap<Phase, Integer> counts = new EnumMap<>(Phase.class);
        for (Phase phase : phases.values()) {
            counts.merge(phase, 1, Integer::sum);
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Phase phase : Phase.values()) {
            Integer count = counts.get(phase);
            if (count != null) {
                joiner.add(phase.label() + " ×" + count);
            }
        }
        return joiner.toString();
    }
}
