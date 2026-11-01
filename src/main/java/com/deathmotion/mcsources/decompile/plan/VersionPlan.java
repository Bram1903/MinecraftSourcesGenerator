package com.deathmotion.mcsources.decompile.plan;

import com.deathmotion.mcsources.mapping.MappingTier;

import java.util.Map;
import java.util.Set;

public record VersionPlan(String id, MappingTier tier, String mappings, String parchment, int classes,
                          Map<ClassGroup, String> keys, Map<String, Set<String>> companions) {
}
