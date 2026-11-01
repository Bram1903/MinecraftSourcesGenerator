package com.deathmotion.mcsources.version;

import com.deathmotion.mcsources.decompile.javadoc.JavadocSource;
import com.deathmotion.mcsources.mapping.MappingTier;

import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;

public record PreparedVersion(String id,
                              MappingTier tier,
                              String mappings,
                              String parchment,
                              SortedMap<String, byte[]> classes,
                              List<Path> libraries,
                              JavadocSource javadocs) {

    public boolean renameLocals() {
        return !tier.shipsLocalNames();
    }
}
