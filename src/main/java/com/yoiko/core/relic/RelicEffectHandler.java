package com.yoiko.core.relic;

import java.lang.reflect.Method;
import java.util.Arrays;
import net.neoforged.fml.ModList;

/**
 * Connects a configured effect key to the Java entry point that actually consumes it.
 * This is deliberately metadata-only: the listed method remains the real event/runtime
 * handler, while configuration validation can reject orphaned effect definitions.
 */
public record RelicEffectHandler(
        String effectKey,
        RelicEffectDefinition.RuntimeTarget runtimeTarget,
        String implementationClass,
        String implementationMethod,
        boolean cobblemonDependent
) {
    public String validationError() {
        if (cobblemonDependent && !ModList.get().isLoaded("cobblemon")) {
            return "required mod 'cobblemon' is not loaded";
        }
        try {
            Class<?> type = Class.forName(implementationClass, false, RelicEffectHandler.class.getClassLoader());
            boolean methodExists = Arrays.stream(type.getDeclaredMethods())
                    .map(Method::getName)
                    .anyMatch(implementationMethod::equals);
            return methodExists ? "" : "implementation method is missing: "
                    + implementationClass + "#" + implementationMethod;
        } catch (LinkageError | ReflectiveOperationException exception) {
            return "implementation class is unavailable: " + implementationClass
                    + " (" + exception.getClass().getSimpleName() + ")";
        }
    }
}
