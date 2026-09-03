package com.yoiko.core.economy;

import java.util.Locale;

public enum CurrencyType {
    GOLD,
    GEM;

    public static CurrencyType fromString(String value) {
        try {
            return CurrencyType.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return GOLD;
        }
    }
}
