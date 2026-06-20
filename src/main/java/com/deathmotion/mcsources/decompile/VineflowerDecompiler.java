package com.deathmotion.mcsources.decompile;

import com.deathmotion.mcsources.util.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class VineflowerDecompiler {
    private final String heap;
    private final int threads;

    public VineflowerDecompiler(String heap, int threads) {
        this.heap = heap;
        this.threads = threads;
    }

    public Path decompile(Path inputJar, Path outputDir, List<Path> libraries, Path logFile, String javadocSpec)
            throws IOException, InterruptedException {
        PathUtils.deleteRecursively(outputDir);
        Files.createDirectories(outputDir);

        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        List<String> command = new ArrayList<>();
        command.add(javaBinary);
        command.add("-Xmx" + heap);
        command.add("-cp");
        command.add(classpath);
        command.add(VineflowerWorker.class.getName());
        command.add(outputDir.toString());
        command.add(inputJar.toString());
        command.add(String.valueOf(threads));
        command.add(javadocSpec);
        for (Path library : libraries) {
            command.add(library.toString());
        }

        Files.createDirectories(logFile.getParent());
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
        int exitCode = process.waitFor();

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
