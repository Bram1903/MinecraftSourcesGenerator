package com.deathmotion.mcsources.manifest;

import java.time.Instant;

public record MinecraftVersion(String id, String type, String url, Instant releaseTime) {
    public boolean isRelease() {
        return "release".equals(type);
    }
}
