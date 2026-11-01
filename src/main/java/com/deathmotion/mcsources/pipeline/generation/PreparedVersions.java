package com.deathmotion.mcsources.pipeline.generation;

import com.deathmotion.mcsources.manifest.MinecraftVersion;
import com.deathmotion.mcsources.version.PreparedVersion;
import com.deathmotion.mcsources.version.VersionPreparer;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PreparedVersions {
    private final VersionPreparer preparer;
    private final Map<String, MinecraftVersion> versions = new LinkedHashMap<>();
    private final Map<String, SoftReference<PreparedVersion>> prepared = new ConcurrentHashMap<>();

    public PreparedVersions(VersionPreparer preparer, List<MinecraftVersion> versions) {
        this.preparer = preparer;
        versions.forEach(version -> this.versions.put(version.id(), version));
    }

    List<String> ids() {
        return List.copyOf(versions.keySet());
    }

    PreparedVersion get(String id) throws IOException {
        synchronized (versions.get(id)) {
            SoftReference<PreparedVersion> reference = prepared.get(id);
            PreparedVersion cached = reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
            PreparedVersion fresh = preparer.prepare(versions.get(id));
            prepared.put(id, new SoftReference<>(fresh));
            return fresh;
        }
    }

    void release(String id) {
        prepared.remove(id);
    }
}
