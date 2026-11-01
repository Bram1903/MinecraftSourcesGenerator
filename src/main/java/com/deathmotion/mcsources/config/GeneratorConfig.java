package com.deathmotion.mcsources.config;

import java.nio.file.Path;

public record GeneratorConfig(Path sourcesDir,
                              Path workDir,
                              boolean force,
                              boolean keepWork,
                              boolean cacheEnabled,
                              boolean resetCache,
                              String vineflowerHeap,
                              int decompilerThreads,
                              boolean serverReports) {

    public static GeneratorConfig fromSystemProperties(RunOptions options) {
        return new GeneratorConfig(
                requiredPath("sourcesDir"),
                requiredPath("workDir"),
                GeneratorProperties.flag("force"),
                GeneratorProperties.flag("keepWork"),
                !GeneratorProperties.flag("noCache"),
                GeneratorProperties.flag("resetCache"),
                options.decompilerHeap(),
                options.decompilerThreads(),
                !GeneratorProperties.flag("noReports"));
    }

    private static Path requiredPath(String key) {
        String value = GeneratorProperties.text(key);
        if (value == null) {
            throw new IllegalStateException("Missing required system property mcsg." + key);
        }
        return Path.of(value);
    }

    public GeneratorConfig withDecompilerThreads(int threads) {
        return new GeneratorConfig(sourcesDir, workDir, force, keepWork, cacheEnabled, resetCache, vineflowerHeap,
                threads, serverReports);
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

    public Path jarsDir(String versionId) {
        return workDir.resolve("jars").resolve(versionId);
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
