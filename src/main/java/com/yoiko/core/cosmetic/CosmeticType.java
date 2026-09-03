package com.yoiko.core.cosmetic;

import java.util.Locale;

public enum CosmeticType {
    HEAD,
    CHEST,
    PARTICLE;

    public static CosmeticType fromString(String value) {
        if (value == null) {
            return HEAD;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        try {
            return CosmeticType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return HEAD;
        }
    }
}
