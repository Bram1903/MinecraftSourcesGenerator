package com.deathmotion.mcsources.decompile.cache;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DecompileCache {
    public static final String FORMAT = "2";

    private final Path root;

    public DecompileCache(Path root) {
        this.root = root;
    }

    public Map<String, String> get(String hash) throws IOException {
        Path path = pathFor(hash);
        if (!Files.exists(path)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            int count = in.readInt();
            Map<String, String> files = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String name = in.readUTF();
                int length = in.readInt();
                files.put(name, new String(in.readNBytes(length), StandardCharsets.UTF_8));
            }
            return files;
        }
    }

    public void put(String hash, Map<String, String> files) throws IOException {
        Path path = pathFor(hash);
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), hash, ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
            out.writeInt(files.size());
            for (Map.Entry<String, String> entry : files.entrySet()) {
                out.writeUTF(entry.getKey());
                byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                out.writeInt(content.length);
                out.write(content);
            }
        }
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private Path pathFor(String hash) {
        return root.resolve(hash.substring(0, 2)).resolve(hash);
    }
}
