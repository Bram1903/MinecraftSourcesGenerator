package com.deathmotion.mcsources.decompile.plan;

import com.deathmotion.mcsources.decompile.store.DecompileStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DecompileWork(Map<String, Run> runs, int uniqueKeys, int stored) {

    public static DecompileWork assign(List<VersionPlan> plans, DecompileStore store) {
        Map<String, Boolean> storedKeys = new HashMap<>();
        Map<String, List<Map.Entry<ClassGroup, String>>> missing = new HashMap<>();
        for (VersionPlan plan : plans) {
            List<Map.Entry<ClassGroup, String>> absent = new ArrayList<>();
            for (Map.Entry<ClassGroup, String> entry : plan.keys().entrySet()) {
                if (!storedKeys.computeIfAbsent(entry.getValue(), store::contains)) {
                    absent.add(entry);
                }
            }
            missing.put(plan.id(), absent);
        }

        List<String> order = new ArrayList<>(missing.keySet());
        order.sort(Comparator.comparingInt((String id) -> missing.get(id).size()).reversed()
                .thenComparing(Comparator.naturalOrder()));
        Set<String> claimed = new HashSet<>();
        Map<String, VersionPlan> byId = new HashMap<>();
        plans.forEach(plan -> byId.put(plan.id(), plan));
        Map<String, Run> runs = new LinkedHashMap<>();
        for (String id : order) {
            Map<String, String> handed = new LinkedHashMap<>();
            Set<String> processed = new HashSet<>();
            for (Map.Entry<ClassGroup, String> entry : missing.get(id)) {
                if (claimed.add(entry.getValue())) {
                    String group = entry.getKey().name();
                    handed.put(group, entry.getValue());
                    processed.add(group);
                    processed.addAll(byId.get(id).companions().getOrDefault(group, Set.of()));
                }
            }
            if (!handed.isEmpty()) {
                runs.put(id, new Run(handed, processed));
            }
        }
        int stored = (int) storedKeys.values().stream().filter(Boolean::booleanValue).count();
        return new DecompileWork(runs, storedKeys.size(), stored);
    }

    public int toDecompile() {
        return runs.values().stream().mapToInt(run -> run.handed().size()).sum();
    }

    public record Run(Map<String, String> handed, Set<String> processed) {
    }
}
