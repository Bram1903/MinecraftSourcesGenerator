package com.deathmotion.mcsources.diff.data;

import com.deathmotion.mcsources.diff.data.ResourceIndex.Resource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public record ReportComparison(Map<String, List<String>> tables, ResourceComparison rest) {

    public static ReportComparison compare(ResourceIndex before, ResourceIndex after) {
        Map<String, List<String>> tables = new TreeMap<>();
        for (String name : ReportTables.SPECIAL) {
            Optional<Resource> old = before.find(name);
            Optional<Resource> updated = after.find(name);
            if (old.isPresent() && updated.isPresent() && old.get().text() != null && updated.get().text() != null) {
                tables.put(name, ReportTables.of(name, old.get().text(), updated.get().text()));
            }
        }
        return new ReportComparison(tables, ResourceComparison.compare(before, after, ReportTables.SPECIAL));
    }
}
