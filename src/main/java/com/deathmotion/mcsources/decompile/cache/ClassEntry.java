package com.deathmotion.mcsources.decompile.cache;

import com.deathmotion.mcsources.util.Hashing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public record ClassEntry(String name, List<String> innerClasses, List<String> superClasses) {
    private static byte[] intToBytes(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    public String sourcesFileName() {
        return name + ".java";
    }

    public List<String> classFileNames() {
        List<String> files = new ArrayList<>(innerClasses.size() + 1);
        files.add(name + ".class");
        for (String inner : innerClasses) {
            files.add(inner + ".class");
        }
        return files;
    }

    public String rawHash(Path root) throws IOException {
        MessageDigest digest = Hashing.newSha256();
        for (String file : classFileNames()) {
            byte[] content = Files.readAllBytes(root.resolve(file));
            digest.update(file.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(intToBytes(content.length));
            digest.update(content);
        }
        return Hashing.hex(digest.digest());
    }

    public void copyTo(Path sourceRoot, Path targetRoot) throws IOException {
        for (String file : classFileNames()) {
            Path target = targetRoot.resolve(file);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(sourceRoot.resolve(file), target);
        }
    }
}
