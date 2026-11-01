package com.deathmotion.mcsources.config;

import java.nio.file.Path;

public record GeneratorConfig(Path sourcesDir,
                              Path workDir,
                              boolean force,
                              boolean cacheEnabled,
                              boolean resetCache,
                              boolean serverReports) {

    public static GeneratorConfig fromSystemProperties() {
        return new GeneratorConfig(
                requiredPath("sourcesDir"),
                requiredPath("workDir"),
                GeneratorProperties.flag("force"),
                !GeneratorProperties.flag("noCache"),
                GeneratorProperties.flag("resetCache"),
                !GeneratorProperties.flag("noReports"));
    }

    private static Path requiredPath(String key) {
        String value = GeneratorProperties.text(key);
        if (value == null) {
            throw new IllegalStateException("Missing required system property mcsg." + key);
        }
        return Path.of(value);
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

    public Path downloadsDir(String versionId) {
        return workDir.resolve("downloads").resolve(versionId);
    }

    public Path logsDir() {
        return workDir.resolve("logs");
    }

    public Path reportsDir(String versionId) {
        return workDir.resolve("reports").resolve(versionId);
    }

    public Path diffsDir() {
        return sourcesDir.resolveSibling("diffs");
    }

    public Path workspaceDir() {
        return sourcesDir.resolveSibling("workspace");
    }
}
