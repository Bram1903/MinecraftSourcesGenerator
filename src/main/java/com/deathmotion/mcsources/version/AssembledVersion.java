package com.deathmotion.mcsources.version;

import com.deathmotion.mcsources.manifest.MinecraftVersion;
import com.deathmotion.mcsources.mapping.ResolvedMappings;

import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;

public record AssembledVersion(MinecraftVersion version,
                               ResolvedMappings mappings,
                               GameJars jars,
                               SortedMap<String, byte[]> classes,
                               List<Path> libraries) {
}
