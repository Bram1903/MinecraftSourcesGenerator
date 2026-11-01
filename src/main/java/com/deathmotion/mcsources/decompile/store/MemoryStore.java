package com.deathmotion.mcsources.decompile.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class MemoryStore implements DecompileStore {
    private final Map<String, Map<String, String>> entries = new ConcurrentHashMap<>();

    @Override
    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    @Override
    public Map<String, String> get(String key) {
        return entries.get(key);
    }

    @Override
    public void put(String key, Map<String, String> files) {
        entries.put(key, Map.copyOf(files));
    }
}
