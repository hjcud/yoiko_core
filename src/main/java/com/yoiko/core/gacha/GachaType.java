package com.yoiko.core.gacha;

import java.util.Arrays;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public enum GachaType {
    ALL("all", ChatFormatting.GREEN),
    LEGENDARY("legendary", ChatFormatting.GOLD),
    SHINY_ALL("shiny_all", ChatFormatting.YELLOW);

    private final String id;
    private final ChatFormatting color;

    GachaType(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return getTranslatedName().getString();
    }

    public String getTranslationKey() {
        return "yoiko_core.gacha_type." + id;
    }

    public MutableComponent getTranslatedName() {
        return Component.translatable(getTranslationKey());
    }

    public ChatFormatting getColor() {
        return color;
    }

    public static GachaType fromString(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.id.equals(normalized))
                .findFirst()
                .orElse(ALL);
    }

    public static String[] ids() {
        return Arrays.stream(values()).map(GachaType::getId).toArray(String[]::new);
    }
}
