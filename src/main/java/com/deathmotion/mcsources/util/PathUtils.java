package com.deathmotion.mcsources.util;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

public final class PathUtils {
    private static final int DELETE_ATTEMPTS = 5;

    private PathUtils() {
    }

    public static void deleteRecursively(Path directory) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= DELETE_ATTEMPTS; attempt++) {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            try {
                deleteTree(directory);
                return;
            } catch (IOException e) {
                last = e;
                pause(attempt);
            }
        }
        throw new IOException("Could not delete " + directory + ", a file inside it is probably open in another "
                + "program", last);
    }

    public static int countJavaFiles(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return (int) walk.filter(path -> path.toString().endsWith(".java")).count();
        }
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void delete(Path path) throws IOException {
        try {
            Files.delete(path);
        } catch (NoSuchFileException ignored) {
        } catch (AccessDeniedException e) {
            if (!path.toFile().setWritable(true)) {
                throw e;
            }
            Files.deleteIfExists(path);
        }
    }

    private static void pause(int attempt) throws IOException {
        try {
            Thread.sleep(200L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while deleting", e);
        }
    }
}
