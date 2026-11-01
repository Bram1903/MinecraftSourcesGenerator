package com.deathmotion.mcsources.pipeline.generation;

import com.deathmotion.mcsources.pipeline.Console;
import com.deathmotion.mcsources.decompile.plan.DecompileWork;
import com.deathmotion.mcsources.decompile.plan.VersionPlan;
import com.deathmotion.mcsources.decompile.store.DecompileStore;
import com.deathmotion.mcsources.decompile.vineflower.VineflowerRun;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class Decompilation {
    private Decompilation() {
    }

    public static void run(List<VersionPlan> plans, PreparedVersions prepared, DecompileStore store, int jobs, int threads,
                    Path logs, Map<String, String> failures) {
        boolean first = true;
        while (true) {
            List<VersionPlan> healthy = plans.stream().filter(plan -> !failures.containsKey(plan.id())).toList();
            DecompileWork work = DecompileWork.assign(healthy, store);
            if (work.runs().isEmpty()) {
                return;
            }
            Console.line("plan", first
                            ? "%d unique classes, %d already decompiled, %d to decompile in %d run(s)"
                            : "%4$d run(s) failed, handing %3$d classes to other versions",
                    work.uniqueKeys(), work.stored(), work.toDecompile(), work.runs().size());
            first = false;
            int failedBefore = failures.size();
            VersionTasks.run("decompile", List.copyOf(work.runs().keySet()), jobs, id -> {
                DecompileWork.Run run = work.runs().get(id);
                Map<String, Map<String, String>> output = VineflowerRun.decompile(prepared.get(id),
                        run.processed()::contains, threads, logs.resolve(id + ".log"));
                for (Map.Entry<String, String> group : run.handed().entrySet()) {
                    store.put(group.getValue(), output.getOrDefault(group.getKey(), Map.of()));
                }
                prepared.release(id);
                return run.handed().size() + " classes";
            }, failures);
            if (failures.size() == failedBefore) {
                return;
            }
        }
    }
}
