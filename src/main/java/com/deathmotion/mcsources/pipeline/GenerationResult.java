package com.deathmotion.mcsources.pipeline;

import com.deathmotion.mcsources.mapping.MappingTier;

public record GenerationResult(String versionId, GenerationOutcome outcome, MappingTier tier, int javaFiles,
                               String error) {

    static GenerationResult generated(String versionId, MappingTier tier, int javaFiles) {
        return new GenerationResult(versionId, GenerationOutcome.GENERATED, tier, javaFiles, null);
    }

    static GenerationResult skipped(String versionId) {
        return new GenerationResult(versionId, GenerationOutcome.SKIPPED, null, 0, null);
    }

    static GenerationResult failed(String versionId, String error) {
        return new GenerationResult(versionId, GenerationOutcome.FAILED, null, 0, error);
    }
}
