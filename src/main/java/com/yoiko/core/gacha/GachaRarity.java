package com.yoiko.core.gacha;

import java.util.Locale;
import net.minecraft.ChatFormatting;

public enum GachaRarity {
    COMMON(ChatFormatting.WHITE),
    SUB_LEGENDARY(ChatFormatting.AQUA),
    MYTHICAL(ChatFormatting.LIGHT_PURPLE),
    LEGENDARY(ChatFormatting.GOLD);

    private final ChatFormatting color;

    GachaRarity(ChatFormatting color) {
        this.color = color;
    }

    public String getDisplayName() {
        return getTranslatedName().getString();
    }

    public net.minecraft.network.chat.MutableComponent getTranslatedName() {
        return net.minecraft.network.chat.Component.translatable("yoiko_core.dex.rarity." + configKey());
    }

    public ChatFormatting getColor() {
        return color;
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GachaRarity fromString(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sub_legendary", "sublegendary", "sub" -> SUB_LEGENDARY;
            case "mythical" -> MYTHICAL;
            case "legendary" -> LEGENDARY;
            default -> COMMON;
        };
    }
}
