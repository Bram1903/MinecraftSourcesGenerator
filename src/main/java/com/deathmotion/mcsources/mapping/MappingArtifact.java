package com.deathmotion.mcsources.mapping;

public record MappingArtifact(MappingTier tier, String version, String url) {
    public String fileName() {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
