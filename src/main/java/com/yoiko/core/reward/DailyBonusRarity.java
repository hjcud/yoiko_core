package com.yoiko.core.reward;

import java.util.Locale;
import net.minecraft.world.item.Rarity;

public enum DailyBonusRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHIC;

    public static DailyBonusRarity fromString(String value) {
        if (value == null || value.isBlank()) {
            return COMMON;
        }
        try {
            return DailyBonusRarity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return COMMON;
        }
    }

    public static DailyBonusRarity fromMinecraft(Rarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> UNCOMMON;
            case RARE -> RARE;
            case EPIC -> EPIC;
            default -> COMMON;
        };
    }
}
