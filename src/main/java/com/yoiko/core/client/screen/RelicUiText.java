package com.yoiko.core.client.screen;

import com.yoiko.core.relic.RelicEffectRegistry;

/** Shared localized labels and colors used by relic panels and tooltips. */
final class RelicUiText {
    private RelicUiText() {
    }

    static String effectName(String effect) {
        if (effect == null || effect.isBlank()) {
            return YoikoClientText.text("yoiko_core.effect.none");
        }
        return RelicEffectRegistry.contains(effect)
                ? YoikoClientText.text("yoiko_core.effect." + effect)
                : effect;
    }

    static String effectSummary(String effect) {
        return RelicEffectRegistry.contains(effect)
                ? YoikoClientText.text("yoiko_core.effect_short." + effect)
                : effectName(effect);
    }

    static int rarityColor(String rarity) {
        return switch (normalizedRarity(rarity)) {
            case "UNCOMMON" -> 0xFF6BFF8D;
            case "RARE" -> 0xFF6FA8FF;
            case "EPIC" -> 0xFFFF73FF;
            case "LEGENDARY" -> 0xFFFFD36A;
            case "MYSTIC" -> 0xFFFF6D6D;
            case "RADIANT" -> 0xFF9DF2FF;
            default -> 0xFFD0D0D0;
        };
    }

    static int identityRarityColor(String rarity) {
        return switch (normalizedRarity(rarity)) {
            case "UNCOMMON" -> 0xFF4F8A58;
            case "RARE" -> 0xFF397E91;
            case "EPIC" -> 0xFF91489A;
            case "LEGENDARY" -> 0xFF9B6B18;
            case "MYSTIC" -> 0xFFB84444;
            case "RADIANT" -> 0xFF397E91;
            default -> 0xFF665B50;
        };
    }

    static String rarityLabel(String rarity) {
        return switch (normalizedRarity(rarity)) {
            case "UNCOMMON" -> YoikoClientText.text("yoiko_core.rarity.uncommon");
            case "RARE" -> YoikoClientText.text("yoiko_core.rarity.rare");
            case "EPIC" -> YoikoClientText.text("yoiko_core.rarity.epic");
            case "LEGENDARY" -> YoikoClientText.text("yoiko_core.rarity.legendary");
            case "MYSTIC" -> YoikoClientText.text("yoiko_core.rarity.mystic");
            case "RADIANT" -> YoikoClientText.text("yoiko_core.rarity.radiant");
            default -> YoikoClientText.text("yoiko_core.rarity.common");
        };
    }

    private static String normalizedRarity(String rarity) {
        if (rarity == null) {
            return "";
        }
        return "UNIQUE".equalsIgnoreCase(rarity) ? "MYSTIC" : rarity.toUpperCase(java.util.Locale.ROOT);
    }
}
