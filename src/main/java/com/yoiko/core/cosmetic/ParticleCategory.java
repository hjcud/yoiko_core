package com.yoiko.core.cosmetic;

import java.util.Locale;

public enum ParticleCategory {
    NONE,
    TRAIL,
    RING,
    ORBIT,
    WINGS,
    AURA,
    COMPANION;

    public static ParticleCategory fromString(String value) {
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
