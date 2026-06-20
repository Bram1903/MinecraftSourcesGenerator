package com.deathmotion.mcsources.pipeline;

public enum Phase {
    MAPPINGS("resolving mappings"),
    DOWNLOAD("downloading jars"),
    REMAP("remapping"),
    MERGE("merging classes"),
    TRANSFORM("fixing bytecode"),
    DECOMPILE("decompiling"),
    WRITE("writing sources");

    private final String label;

    Phase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
