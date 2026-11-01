package com.deathmotion.mcsources.decompile.vineflower;

import com.deathmotion.mcsources.classfile.InternalNames;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

final class MemoryClassSource implements IContextSource {
    private final String name;
    private final SortedMap<String, byte[]> classes;
    private final Map<String, Map<String, String>> output;

    MemoryClassSource(String name, SortedMap<String, byte[]> classes, Map<String, Map<String, String>> output) {
        this.name = name;
        this.classes = classes;
        this.output = output;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Entries getEntries() {
        List<Entry> entries = classes.keySet().stream().map(Entry::atBase).toList();
        return new Entries(entries, List.of(), List.of());
    }

    @Override
    public byte[] getClassBytes(String className) {
        return classes.get(className);
    }

    @Override
    public InputStream getInputStream(String resource) {
        if (!resource.endsWith(CLASS_SUFFIX)) {
            return null;
        }
        byte[] bytes = classes.get(resource.substring(0, resource.length() - CLASS_SUFFIX.length()));
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }

    @Override
    public IOutputSink createOutputSink(IResultSaver saver) {
        return new IOutputSink() {
            @Override
            public void begin() {
            }

            @Override
            public void acceptClass(String qualifiedName, String fileName, String content, int[] mapping) {
                String file = fileName.substring(fileName.lastIndexOf('/') + 1);
                output.computeIfAbsent(InternalNames.topLevel(qualifiedName), group -> new TreeMap<>())
                        .put(file, content);
            }

            @Override
            public void acceptDirectory(String directory) {
            }

            @Override
            public void acceptOther(String path) {
            }

            @Override
            public void close() {
            }
        };
    }
}
