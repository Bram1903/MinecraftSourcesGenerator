package com.deathmotion.mcsources.version;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

final class MergedClasses {
    private MergedClasses() {
    }

    static SortedMap<String, byte[]> of(Map<String, byte[]> client, Map<String, byte[]> server,
                                        Set<String> namedClasses) {
        MinecraftClassFilter filter = new MinecraftClassFilter(namedClasses);
        SortedMap<String, byte[]> merged = new TreeMap<>();
        server.forEach((name, bytes) -> {
            if (filter.accept(name)) {
                merged.put(name, bytes);
            }
        });
        client.forEach((name, bytes) -> {
            if (filter.accept(name)) {
                merged.put(name, bytes);
            }
        });
        return merged;
    }
}
