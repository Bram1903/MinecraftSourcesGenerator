package com.deathmotion.mcsources.diff.data;

import com.deathmotion.mcsources.io.Directories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class ServerReports {
    private static final String DONE_MARKER = ".reports-done";
    private static final long TIMEOUT_MINUTES = 15;

    private ServerReports() {
    }

    public static Path generate(Path serverBundle, Path directory) throws IOException, InterruptedException {
        Path reports = directory.resolve("generated").resolve("reports");
        if (Files.exists(directory.resolve(DONE_MARKER)) && Files.isDirectory(reports)) {
            return reports;
        }
        Directories.deleteRecursively(directory);
        Files.createDirectories(directory);

        Path log = directory.resolve("datagen.log");
        String java = ProcessHandle.current().info().command().orElse("java");
        Process process = new ProcessBuilder(java,
                "-DbundlerMainClass=net.minecraft.data.Main",
                "-jar", serverBundle.toAbsolutePath().toString(),
                "--reports",
                "--output", directory.resolve("generated").toAbsolutePath().toString())
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("The data generator did not finish in " + TIMEOUT_MINUTES + " minutes, see " + log);
        }
        if (process.exitValue() != 0 || !Files.isDirectory(reports)) {
            throw new IOException("The data generator exited with " + process.exitValue() + ", see " + log);
        }
        Files.writeString(directory.resolve(DONE_MARKER), "");
        return reports;
    }
}
