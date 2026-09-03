package com.yoiko.core.relic;

import java.util.Locale;
import java.util.Optional;

public enum RelicEquipGroup {
    STANDARD(Integer.MAX_VALUE),
    SPECIAL(1);

    private final int maxEquipped;

    RelicEquipGroup(int maxEquipped) {
        this.maxEquipped = maxEquipped;
    }

    public int maxEquipped() {
        return maxEquipped;
    }

    public static Optional<RelicEquipGroup> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
