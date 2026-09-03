package com.yoiko.core.cosmetic;

import java.util.Locale;

public enum CosmeticAnchor {
    NONE,
    HEAD,
    FACE,
    CHEST_FRONT,
    CHEST_BACK,
    SHOULDERS;

    public static CosmeticAnchor fromString(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
