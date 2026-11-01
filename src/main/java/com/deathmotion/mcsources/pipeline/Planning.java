package com.deathmotion.mcsources.pipeline;

import com.deathmotion.mcsources.decompile.plan.DecompileKeys;
import com.deathmotion.mcsources.decompile.plan.LibraryIndex;
import com.deathmotion.mcsources.decompile.plan.VersionPlan;
import com.deathmotion.mcsources.decompile.vineflower.VineflowerRun;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class Planning {
    private Planning() {
    }

    static List<VersionPlan> plan(PreparedVersions prepared, int jobs, Map<String, String> failures)
            throws IOException {
        Map<String, VersionPlan> plans = new ConcurrentHashMap<>();
        try (LibraryIndex libraries = new LibraryIndex()) {
            DecompileKeys keys = new DecompileKeys(libraries, VineflowerRun.decompiler());
            VersionTasks.run("prepare", prepared.ids(), jobs, id -> {
                plans.put(id, keys.plan(prepared.get(id)));
                return "";
            }, failures);
        }
        return prepared.ids().stream().map(plans::get).filter(Objects::nonNull).toList();
    }
}
