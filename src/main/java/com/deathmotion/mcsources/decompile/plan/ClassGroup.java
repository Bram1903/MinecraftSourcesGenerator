package com.deathmotion.mcsources.decompile.plan;

import com.deathmotion.mcsources.classfile.InternalNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public record ClassGroup(String name, List<String> members) {

    static List<ClassGroup> of(SortedMap<String, byte[]> classes) {
        Map<String, List<String>> members = new TreeMap<>();
        for (String name : classes.keySet()) {
            members.computeIfAbsent(InternalNames.topLevel(name), key -> new ArrayList<>()).add(name);
        }
        List<ClassGroup> groups = new ArrayList<>(members.size());
        members.forEach((name, names) -> groups.add(new ClassGroup(name, List.copyOf(names))));
        return groups;
    }

    public String packagePath() {
        return InternalNames.packageOf(name);
    }
}
