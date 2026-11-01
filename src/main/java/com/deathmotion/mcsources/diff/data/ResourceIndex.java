package com.deathmotion.mcsources.diff.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ResourceIndex {
    private static final long TEXT_LIMIT = 32L * 1024 * 1024;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "json", "mcmeta", "txt", "properties", "mcfunction", "fsh", "vsh", "glsl", "toml", "cfg", "snbt");

    private final Map<String, Resource> resources;

    public record Resource(String path, long crc, long size, byte[] text) {
    }

    private ResourceIndex(Map<String, Resource> resources) {
        this.resources = resources;
    }

    public static ResourceIndex fromJars(List<Path> jars) throws IOException {
        Map<String, Resource> resources = new TreeMap<>();
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || name.endsWith(".class") || name.startsWith("META-INF/")
                            || resources.containsKey(name)) {
                        continue;
                    }
                    byte[] text = null;
                    if (isText(name) && entry.getSize() <= TEXT_LIMIT) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            text = in.readAllBytes();
                        }
                    }
                    resources.put(name, new Resource(name, entry.getCrc(), entry.getSize(), text));
                }
            }
        }
        return new ResourceIndex(resources);
    }

    public static ResourceIndex fromDirectory(Path root) throws IOException {
        Map<String, Resource> resources = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String name = root.relativize(file).toString().replace('\\', '/');
                byte[] bytes = Files.readAllBytes(file);
                CRC32 crc = new CRC32();
                crc.update(bytes);
                resources.put(name, new Resource(name, crc.getValue(), bytes.length, isText(name) ? bytes : null));
            }
        }
        return new ResourceIndex(resources);
    }

    private static boolean isText(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && TEXT_EXTENSIONS.contains(name.substring(dot + 1));
    }

    public Map<String, Resource> resources() {
        return resources;
    }

    public Optional<Resource> find(String path) {
        return Optional.ofNullable(resources.get(path));
    }
}
