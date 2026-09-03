package com.yoiko.core.client.relic;

import com.yoiko.core.relic.RelicAppraisalCategory;
import com.yoiko.core.relic.RelicEffectDefinition;
import com.yoiko.core.relic.RelicEffectRegistry;

/** Shared client presentation derived from the effect registry's explicit category. */
public final class RelicEffectPresentation {
    private static final String ACTIVE_GLYPH = "\uE200";
    private static final String COMBAT_GLYPH = "\uE202";
    private static final String DEFENSE_GLYPH = "\uE203";
    private static final String POKEMON_GLYPH = "\uE204";
    private static final String EXPLORATION_GLYPH = "\uE205";

    private RelicEffectPresentation() {
    }

    public static RelicAppraisalCategory category(String effectKey) {
        RelicEffectDefinition definition = RelicEffectRegistry.get(effectKey);
        return definition == null ? RelicAppraisalCategory.ALL : definition.category();
    }

    public static String markerGlyph(String effectKey) {
        return switch (category(effectKey)) {
            case COMBAT -> COMBAT_GLYPH;
            case DEFENSE -> DEFENSE_GLYPH;
            case POKEMON -> POKEMON_GLYPH;
            case EXPLORATION -> EXPLORATION_GLYPH;
            case ALL -> ACTIVE_GLYPH;
        };
    }

    public static int markerColor(String effectKey) {
        return switch (category(effectKey)) {
            case COMBAT -> 0xFF796B;
            case DEFENSE -> 0x76C8FF;
            case POKEMON, EXPLORATION, ALL -> 0xFFFFFF;
        };
    }

    public static int hudColor(String effectKey) {
        return switch (category(effectKey)) {
            case COMBAT -> 0xFF796B;
            case DEFENSE -> 0x76C8FF;
            case POKEMON -> 0xF06A64;
            case EXPLORATION -> 0xC79CFF;
            case ALL -> 0xF0CB68;
        };
    }
}
