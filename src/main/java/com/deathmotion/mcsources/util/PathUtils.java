package com.deathmotion.mcsources.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class PathUtils {
    private PathUtils() {
    }

    public static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    public static int countJavaFiles(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return (int) walk.filter(path -> path.toString().endsWith(".java")).count();
        }
    }
}
