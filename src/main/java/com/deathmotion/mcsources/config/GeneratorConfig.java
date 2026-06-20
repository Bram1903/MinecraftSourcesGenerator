package com.deathmotion.mcsources.config;

import java.nio.file.Path;

public record GeneratorConfig(Path sourcesDir,
                              Path workDir,
                              boolean force,
                              boolean keepWork,
                              boolean cacheEnabled,
                              String vineflowerHeap,
                              int decompilerThreads) {

    public static GeneratorConfig fromSystemProperties(int decompilerThreads) {
        return new GeneratorConfig(
                requiredPath("mcsg.sourcesDir"),
                requiredPath("mcsg.workDir"),
                flag("mcsg.force"),
                flag("mcsg.keepWork"),
                !flag("mcsg.noCache"),
                System.getProperty("mcsg.vfHeap", "4g"),
                decompilerThreads);
    }

    private static Path requiredPath(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property " + key);
        }
        return Path.of(value);
    }

    private static boolean flag(String key) {
        String value = System.getProperty(key);
        return value != null && (value.isEmpty() || value.equals("1") || Boolean.parseBoolean(value));
    }

    public Path cacheDir() {
        return workDir.resolve("cache");
    }

    public Path decompileCacheDir() {
        return workDir.resolve("decompile-cache");
    }

    public Path librariesDir() {
        return workDir.resolve("libs");
    }

    public Path scratchDir(String versionId) {
        return workDir.resolve("tmp").resolve(versionId);
    }
}
