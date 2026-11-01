package com.deathmotion.mcsources.decompile;

import com.deathmotion.mcsources.util.PathUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class VineflowerDecompiler {
    private final String heap;
    private final int threads;

    public VineflowerDecompiler(String heap, int threads) {
        this.heap = heap;
        this.threads = threads;
    }

    public Path decompile(Path inputJar, Path outputDir, List<Path> libraries, Path logFile,
                          DecompileSettings settings) throws IOException, InterruptedException {
        PathUtils.deleteRecursively(outputDir);
        Files.createDirectories(outputDir);
        Files.createDirectories(logFile.getParent());

        Path librariesFile = logFile.resolveSibling(logFile.getFileName() + ".libraries");
        Files.write(librariesFile, libraries.stream().map(Path::toString).toList(), StandardCharsets.UTF_8);

        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = List.of(
                javaBinary,
                "-Xmx" + heap,
                "-cp", System.getProperty("java.class.path"),
                VineflowerWorker.class.getName(),
                outputDir.toString(),
                inputJar.toString(),
                String.valueOf(threads),
                settings.javadocSpec(),
                String.valueOf(settings.renameLocals()),
                librariesFile.toString());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
        int exitCode = process.waitFor();
        Files.deleteIfExists(librariesFile);

        boolean producedJava;
        try (Stream<Path> walk = Files.walk(outputDir)) {
            producedJava = walk.anyMatch(path -> path.toString().endsWith(".java"));
        }
        if (exitCode != 0 || !producedJava) {
            throw new IOException("Vineflower produced no sources (exit " + exitCode + "), see " + logFile);
        }
        return outputDir;
    }
}
