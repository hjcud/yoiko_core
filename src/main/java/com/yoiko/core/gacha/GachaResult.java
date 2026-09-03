package com.yoiko.core.gacha;

public record GachaResult(String species, GachaRarity rarity, boolean shiny, int level, boolean pityForced) {
    public GachaResult(String species, GachaRarity rarity, boolean shiny, int level) {
        this(species, rarity, shiny, level, false);
    }
}
