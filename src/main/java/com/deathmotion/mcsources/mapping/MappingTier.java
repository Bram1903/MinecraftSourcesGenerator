package com.deathmotion.mcsources.mapping;

public enum MappingTier {
    MOJMAP(false),
    ORNITHE_FEATHER(false),
    VANILLA(true);

    private final boolean shipsLocalNames;

    MappingTier(boolean shipsLocalNames) {
        this.shipsLocalNames = shipsLocalNames;
    }

    public boolean shipsLocalNames() {
        return shipsLocalNames;
    }
}
