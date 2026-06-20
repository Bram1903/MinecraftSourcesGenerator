package com.deathmotion.mcsources.mapping.parchment;

import com.deathmotion.mcsources.download.Downloader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ParchmentResolver {
    private static final String REPOSITORY = "https://maven.parchmentmc.org/org/parchmentmc/data";
    private static final Pattern RELEASE = Pattern.compile("<release>([^<]+)</release>");
    private static final Pattern VERSION = Pattern.compile("<version>([^<]+)</version>");

    private ParchmentResolver() {
    }

    public static Optional<Resolved> resolve(String mcId, Path workDir) throws IOException {
        String metadataUrl = REPOSITORY + "/parchment-" + mcId + "/maven-metadata.xml";
        String xml;
        try {
            xml = Downloader.getString(metadataUrl);
        } catch (IOException notPublished) {
            return Optional.empty();
        }
        String version = latestRelease(xml);
        if (version == null) {
            return Optional.empty();
        }
        String fileName = "parchment-" + mcId + "-" + version + ".zip";
        String url = REPOSITORY + "/parchment-" + mcId + "/" + version + "/" + fileName;
        Path zip = Downloader.download(url, workDir.resolve(fileName));
        Path json = extractParchmentJson(zip, workDir.resolve("parchment.json"));
        return Optional.of(new Resolved(version, json));
    }

    private static String latestRelease(String metadataXml) {
        Matcher release = RELEASE.matcher(metadataXml);
        if (release.find() && !release.group(1).contains("SNAPSHOT")) {
            return release.group(1);
        }
        String best = null;
        Matcher version = VERSION.matcher(metadataXml);
        while (version.find()) {
            String candidate = version.group(1);
            if (!candidate.contains("SNAPSHOT")) {
                best = candidate;
            }
        }
        return best;
    }

    private static Path extractParchmentJson(Path zip, Path destination) throws IOException {
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            ZipEntry entry = archive.getEntry("parchment.json");
            if (entry == null) {
                throw new IOException("parchment.json not found in " + zip);
            }
            try (InputStream in = archive.getInputStream(entry)) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return destination;
    }

    public record Resolved(String version, Path json) {
    }
}
