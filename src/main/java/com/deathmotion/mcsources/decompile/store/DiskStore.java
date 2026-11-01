package com.deathmotion.mcsources.decompile.store;

import com.deathmotion.mcsources.io.Directories;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

final class DiskStore implements DecompileStore {
    private static final String FORMAT_FILE = "FORMAT";

    private final Path root;

    private DiskStore(Path root) {
        this.root = root;
    }

    static DiskStore open(Path root) throws IOException {
        Path format = root.resolve(FORMAT_FILE);
        if (!Files.exists(format) || !Files.readString(format).equals(FORMAT)) {
            Directories.deleteRecursively(root);
            Files.createDirectories(root);
            Files.writeString(format, FORMAT);
        }
        return new DiskStore(root);
    }

    @Override
    public boolean contains(String key) {
        return Files.exists(pathFor(key));
    }

    @Override
    public Map<String, String> get(String key) throws IOException {
        Path path = pathFor(key);
        if (!Files.exists(path)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            int count = in.readInt();
            Map<String, String> files = new TreeMap<>();
            for (int i = 0; i < count; i++) {
                String name = in.readUTF();
                files.put(name, new String(in.readNBytes(in.readInt()), StandardCharsets.UTF_8));
            }
            if (in.read() != -1) {
                throw new EOFException("trailing bytes");
            }
            return files;
        } catch (EOFException | UTFDataFormatException e) {
            Files.deleteIfExists(path);
            throw new IOException("Decompile cache entry " + key + " was damaged and has been removed, run again", e);
        }
    }

    @Override
    public void put(String key, Map<String, String> files) throws IOException {
        Path path = pathFor(key);
        if (Files.exists(path)) {
            return;
        }
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), key, ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(files.size());
                for (Map.Entry<String, String> entry : files.entrySet()) {
                    out.writeUTF(entry.getKey());
                    byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                    out.writeInt(content.length);
                    out.write(content);
                }
            }
            moveIntoPlace(temp, path);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void moveIntoPlace(Path temp, Path path) throws IOException {
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path);
        } catch (IOException e) {
            if (!Files.exists(path)) {
                throw e;
            }
        }
    }

    private Path pathFor(String key) {
        return root.resolve(key.substring(0, 2)).resolve(key);
    }
}
