package com.deathmotion.mcsources.decompile.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface DecompileStore {
    String FORMAT = "4";

    static DecompileStore onDisk(Path root) throws IOException {
        return DiskStore.open(root);
    }

    static DecompileStore inMemory() {
        return new MemoryStore();
    }

    boolean contains(String key);

    Map<String, String> get(String key) throws IOException;

    void put(String key, Map<String, String> files) throws IOException;
}
