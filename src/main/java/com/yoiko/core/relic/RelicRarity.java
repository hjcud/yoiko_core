package com.yoiko.core.relic;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;

public enum RelicRarity {
    COMMON(ChatFormatting.GRAY),
    UNCOMMON(ChatFormatting.GREEN),
    RARE(ChatFormatting.BLUE),
    EPIC(ChatFormatting.LIGHT_PURPLE),
    LEGENDARY(ChatFormatting.GOLD),
    MYSTIC(ChatFormatting.RED),
    RADIANT(ChatFormatting.AQUA);

    private final ChatFormatting color;

    RelicRarity(ChatFormatting color) {
        this.color = color;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public static Optional<RelicRarity> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if ("unique".equalsIgnoreCase(value)) {
            return Optional.of(MYSTIC);
        }
        try {
            return Optional.of(RelicRarity.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static RelicRarity fromString(String value) {
        return parse(value).orElseThrow(() ->
                new IllegalArgumentException("Unknown relic rarity: " + value));
    }
}
