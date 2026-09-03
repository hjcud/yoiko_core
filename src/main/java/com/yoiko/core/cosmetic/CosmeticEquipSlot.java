package com.yoiko.core.cosmetic;

import java.util.Locale;

/** Physical cosmetic slots. Cosmetic kind and particle behavior remain independent. */
public enum CosmeticEquipSlot {
    HEAD,
    CHEST,
    FEET;

    public static CosmeticEquipSlot forCosmetic(CosmeticData cosmetic) {
        if (cosmetic == null) {
            return null;
        }
        return switch (cosmetic.type()) {
            case HEAD -> HEAD;
            case CHEST -> CHEST;
            case PARTICLE -> switch (cosmetic.particleCategory()) {
                case RING, COMPANION -> HEAD;
                case WINGS, AURA, ORBIT -> CHEST;
                case TRAIL -> FEET;
                case NONE -> null;
            };
        };
    }

    public static CosmeticEquipSlot fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
