package com.deathmotion.mcsources.config;

final class GeneratorProperties {
    private static final String PREFIX = "mcsg.";

    private GeneratorProperties() {
    }

    static boolean flag(String key) {
        String value = System.getProperty(PREFIX + key);
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equals("1") || Boolean.parseBoolean(trimmed);
    }

    static String text(String key) {
        String value = System.getProperty(PREFIX + key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String text(String key, String fallback) {
        String value = text(key);
        return value == null ? fallback : value;
    }

    static int integer(String key, int fallback) {
        String value = text(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("-P" + key + " must be a whole number, got '" + value + "'");
        }
    }
}
