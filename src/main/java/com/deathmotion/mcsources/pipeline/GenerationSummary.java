package com.deathmotion.mcsources.pipeline;

import com.deathmotion.mcsources.mapping.MappingTier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class GenerationSummary {
    private GenerationSummary() {
    }

    public static void print(List<GenerationResult> results, long wallMillis) {
        int generated = 0;
        int skipped = 0;
        int failed = 0;
        Map<MappingTier, Integer> perTier = new EnumMap<>(MappingTier.class);
        List<String> failures = new ArrayList<>();

        for (GenerationResult result : results) {
            switch (result.outcome()) {
                case GENERATED -> {
                    generated++;
                    perTier.merge(result.tier(), 1, Integer::sum);
                }
                case SKIPPED -> skipped++;
                case FAILED -> {
                    failed++;
                    failures.add(result.versionId() + ": " + result.error());
                }
            }
        }

        System.out.println();
        System.out.println("========== SUMMARY ==========");
        System.out.println("generated: " + generated);
        System.out.println("skipped:   " + skipped);
        System.out.println("failed:    " + failed);
        System.out.println("wall time: " + (wallMillis / 1000) + "s");
        if (!perTier.isEmpty()) {
            System.out.println("by mappings:");
            perTier.forEach((tier, count) -> System.out.println("  " + tier + ": " + count));
        }
        if (!failures.isEmpty()) {
            System.out.println("failures:");
            failures.forEach(failure -> System.out.println("  - " + failure));
        }
    }
}
