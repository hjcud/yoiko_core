package com.yoiko.core.relic;

import java.util.Locale;

/** Player-selectable bias for sealed-relic appraisal. Rarity odds are never changed. */
public enum RelicAppraisalCategory {
    ALL,
    COMBAT,
    DEFENSE,
    POKEMON,
    EXPLORATION;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "yoiko_core.relic.appraisal_category." + id();
    }

    public RelicAppraisalCategory next() {
        RelicAppraisalCategory[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean matches(String effect) {
        if (this == ALL) {
            return true;
        }
        RelicEffectDefinition definition = RelicEffectRegistry.get(effect);
        return definition != null && definition.category() == this;
    }

    public static RelicAppraisalCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }
}
