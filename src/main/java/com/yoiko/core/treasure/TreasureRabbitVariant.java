package com.yoiko.core.treasure;

import java.util.Locale;

public enum TreasureRabbitVariant {
    GOLDEN,
    RADIANT,
    MIRROR,
    CROWN;

    public static TreasureRabbitVariant fromString(String value) {
        if (value == null || value.isBlank()) {
            return GOLDEN;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GOLDEN;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
