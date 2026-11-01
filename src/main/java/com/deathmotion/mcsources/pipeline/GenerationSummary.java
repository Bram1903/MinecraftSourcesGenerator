package com.deathmotion.mcsources.pipeline;

import com.deathmotion.mcsources.mapping.MappingTier;
import com.deathmotion.mcsources.pipeline.generation.StageClock;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class GenerationSummary {
    private GenerationSummary() {
    }

    static void print(List<GenerationResult> results, StageClock clock) {
        Map<GenerationOutcome, Integer> outcomes = new EnumMap<>(GenerationOutcome.class);
        Map<MappingTier, Integer> tiers = new EnumMap<>(MappingTier.class);
        int files = 0;
        for (GenerationResult result : results) {
            outcomes.merge(result.outcome(), 1, Integer::sum);
            if (result.outcome() == GenerationOutcome.GENERATED) {
                tiers.merge(result.tier(), 1, Integer::sum);
                files += result.javaFiles();
            }
        }
        System.out.println();
        System.out.println("========== SUMMARY ==========");
        System.out.println("generated: " + outcomes.getOrDefault(GenerationOutcome.GENERATED, 0)
                + " (" + files + " files)");
        System.out.println("skipped:   " + outcomes.getOrDefault(GenerationOutcome.SKIPPED, 0));
        System.out.println("failed:    " + outcomes.getOrDefault(GenerationOutcome.FAILED, 0));
        System.out.println("wall time: " + Console.seconds(clock.elapsed()));
        clock.stages().forEach((stage, nanos) -> System.out.println("  " + stage + ": " + Console.seconds(nanos)));
        if (!tiers.isEmpty()) {
            System.out.println("by mappings:");
            tiers.forEach((tier, count) -> System.out.println("  " + tier + ": " + count));
        }
        for (GenerationResult result : results) {
            if (result.outcome() == GenerationOutcome.FAILED) {
                System.out.println("  - " + result.versionId() + ": " + result.error());
            }
        }
    }
}
