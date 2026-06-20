package com.deathmotion.mcsources.pipeline;

import com.deathmotion.mcsources.mapping.MappingTier;

public record GenerationResult(String versionId,
                               GenerationOutcome outcome,
                               MappingTier tier,
                               int javaFiles,
                               long millis,
                               String error) {

    public static GenerationResult generated(String versionId, MappingTier tier, int javaFiles, long millis) {
        return new GenerationResult(versionId, GenerationOutcome.GENERATED, tier, javaFiles, millis, null);
    }

    public static GenerationResult skipped(String versionId) {
        return new GenerationResult(versionId, GenerationOutcome.SKIPPED, null, 0, 0, null);
    }

    public static GenerationResult failed(String versionId, String error, long millis) {
        return new GenerationResult(versionId, GenerationOutcome.FAILED, null, 0, millis, error);
    }
}
