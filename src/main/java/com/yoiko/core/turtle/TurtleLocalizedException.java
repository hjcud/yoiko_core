package com.yoiko.core.turtle;

import net.minecraft.network.chat.Component;

/** User-facing turtle error that remains translatable on the receiving client. */
public final class TurtleLocalizedException extends IllegalStateException {
    private final Component component;

    private TurtleLocalizedException(String key, Object... args) {
        super(key);
        this.component = Component.translatable(key, serializableArguments(args));
    }

    public static TurtleLocalizedException of(String key, Object... args) {
        return new TurtleLocalizedException(key, args);
    }

    public Component component() {
        return component;
    }

    public static Component component(RuntimeException exception, String fallbackKey) {
        if (exception instanceof TurtleLocalizedException localized) return localized.component();
        return Component.translatable(fallbackKey);
    }

    /** Chat packets only support primitive values and components as translation arguments. */
    private static Object[] serializableArguments(Object[] args) {
        Object[] safe = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            if (value == null) safe[i] = "";
            else if (value instanceof Component || value instanceof String
                    || value instanceof Number || value instanceof Boolean) safe[i] = value;
            else if (value instanceof Enum<?> enumValue) safe[i] = enumValue.name();
            else safe[i] = String.valueOf(value);
        }
        return safe;
    }
}
