package com.deathmotion.mcsources.jar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ServerBundle {
    private ServerBundle() {
    }

    public static Path extractServerJar(Path serverJar, Path destination) throws IOException {
        try (ZipFile zip = new ZipFile(serverJar.toFile())) {
            ZipEntry versionsList = zip.getEntry("META-INF/versions.list");
            if (versionsList == null) {
                return serverJar;
            }

            String innerEntryName;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zip.getInputStream(versionsList), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line == null) {
                    return serverJar;
                }
                String[] columns = line.split("\t");
                innerEntryName = "META-INF/versions/" + columns[columns.length - 1];
            }

            ZipEntry inner = zip.getEntry(innerEntryName);
            if (inner == null) {
                return serverJar;
            }
            Files.createDirectories(destination.getParent());
            try (InputStream in = zip.getInputStream(inner)) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return destination;
        }
    }
}
