package com.deathmotion.mcsources.mapping;

import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.nio.file.Path;
import java.util.Set;

public record ResolvedMappings(MappingTier tier,
                               String detail,
                               MemoryMappingTree clientTree,
                               MemoryMappingTree serverTree,
                               Set<String> namedClasses,
                               Path javadocTiny) {

    public static final String OBFUSCATED_NAMESPACE = "official";
    public static final String NAMED_NAMESPACE = "named";

    public boolean requiresRemapping() {
        return tier != MappingTier.VANILLA;
    }
}
