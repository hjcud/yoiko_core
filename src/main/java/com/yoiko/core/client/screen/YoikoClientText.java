package com.yoiko.core.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class YoikoClientText {
    private YoikoClientText() {
    }

    static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    static Component data(String value) {
        if (value != null && value.indexOf('\n') >= 0) {
            MutableComponent result = Component.empty();
            String[] lines = value.split("\\n", -1);
            for (int index = 0; index < lines.length; index++) {
                if (index > 0) {
                    result.append("\n");
                }
                result.append(dataLine(lines[index]));
            }
            return result;
        }
        return dataLine(value);
    }

    private static Component dataLine(String value) {
        if (value != null && value.startsWith("yoiko_core.")) {
            String[] parts = value.split("\\|", -1);
            if (parts.length == 1) {
                return Component.translatable(value);
            }
            Object[] args = new Object[parts.length - 1];
            for (int index = 0; index < args.length; index++) {
                String argument = parts[index + 1];
                args[index] = argument.startsWith("@")
                        ? Component.translatable(argument.substring(1))
                        : argument;
            }
            return Component.translatable(parts[0], args);
        }
        return Component.literal(value == null ? "" : value);
    }

    static String dataText(String value) {
        return data(value).getString();
    }
}
