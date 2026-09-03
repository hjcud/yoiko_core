package com.yoiko.core.cosmetic;

import java.util.Locale;

public enum CosmeticRarity {
    LEGENDARY,
    MYTHIC;

    public static CosmeticRarity fromString(String value) {
        if (value == null || value.isBlank()) {
            return MYTHIC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MYTHIC;
        }
    }

    public String translationKey() {
        return "yoiko_core.rarity." + (this == MYTHIC ? "mystic" : "legendary");
    }
}
