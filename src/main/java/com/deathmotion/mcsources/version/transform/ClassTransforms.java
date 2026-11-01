package com.deathmotion.mcsources.version.transform;

import com.deathmotion.mcsources.mapping.MappingTier;
import com.deathmotion.mcsources.mapping.parchment.ParchmentData;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class ClassTransforms {
    private ClassTransforms() {
    }

    public static SortedMap<String, byte[]> apply(Map<String, byte[]> classes, MappingTier tier,
                                                  ParchmentData parchment) {
        boolean injectNames = tier == MappingTier.MOJMAP;
        Map<String, byte[]> transformed = new ConcurrentHashMap<>(classes.size());
        classes.entrySet().parallelStream().forEach(entry -> {
            byte[] bytes = ParameterAnnotationFixer.fix(entry.getValue());
            if (injectNames) {
                bytes = ParameterNameInjector.inject(bytes, parchment);
            }
            transformed.put(entry.getKey(), bytes);
        });
        return new TreeMap<>(transformed);
    }
}
