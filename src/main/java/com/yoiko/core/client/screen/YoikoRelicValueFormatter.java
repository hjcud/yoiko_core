package com.yoiko.core.client.screen;

import com.yoiko.core.relic.RelicEffectDefinition;
import com.yoiko.core.relic.RelicEffectRegistry;
import java.util.Locale;

/** Formats relic values with the unit and precision declared by the effect registry. */
final class YoikoRelicValueFormatter {
    private YoikoRelicValueFormatter() {
    }

    static String format(String effect, double value) {
        RelicEffectDefinition definition = RelicEffectRegistry.get(effect);
        if (definition == null) {
            return compactNumber(value);
        }

        String number = String.format(Locale.ROOT, definition.uiFormat(), value);
        if (isUseCountEffect(effect)) {
            return YoikoClientText.text("yoiko_core.ui.relic.value.uses", number);
        }
        return switch (definition.unit()) {
            case PERCENT, CHANCE -> number + "%";
            case SLOTS -> YoikoClientText.text("yoiko_core.ui.relic.value.slots", number);
            case BLOCKS -> YoikoClientText.text("yoiko_core.ui.relic.value.blocks", number);
            case SECONDS -> YoikoClientText.text("yoiko_core.ui.relic.value.seconds", number);
            case LEVELS -> YoikoClientText.text("yoiko_core.ui.relic.value.levels", number);
            case NUMBER -> number;
        };
    }

    private static boolean isUseCountEffect(String effect) {
        return "radiant_high_air_step_count".equals(effect)
                || "radiant_double_air_step_count".equals(effect);
    }

    private static String compactNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }
}
