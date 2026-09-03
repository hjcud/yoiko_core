package com.yoiko.core.relic;

import com.yoiko.core.YoikoServerCore;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Optional Waystones integration kept reflection-only so Yoiko Core also runs without Balm. */
public final class WaystonesRelicBridge {
    private static final String WAYSTONES_MOD_ID = "waystones";
    private static boolean registrationAttempted;
    private static boolean registered;
    private static boolean runtimeFailureLogged;

    private WaystonesRelicBridge() {
    }

    public static void init() {
        if (registrationAttempted) {
            return;
        }
        registrationAttempted = true;
        if (!ModList.get().isLoaded(WAYSTONES_MOD_ID)) {
            return;
        }

        try {
            ClassLoader loader = WaystonesRelicBridge.class.getClassLoader();
            Class<?> balmClass = Class.forName("net.blay09.mods.balm.api.Balm", false, loader);
            Class<?> preEventClass = Class.forName(
                    "net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Pre", false, loader);
            Object events = balmClass.getMethod("getEvents").invoke(null);
            Method onEvent = events.getClass().getMethod("onEvent", Class.class, Consumer.class);
            onEvent.invoke(events, preEventClass, (Consumer<Object>) WaystonesRelicBridge::onWaystoneTeleport);
            registered = true;
            YoikoServerCore.LOGGER.info("Enabled the Waystones relic integration.");
        } catch (LinkageError | ReflectiveOperationException exception) {
            YoikoServerCore.LOGGER.error("Could not register the Waystones relic integration.", exception);
        }
    }

    public static boolean isRegistered() {
        return registered;
    }

    private static void onWaystoneTeleport(Object event) {
        try {
            Object context = invoke(event, "getContext");
            Object entity = invoke(context, "getEntity");
            if (!(entity instanceof ServerPlayer player)) {
                return;
            }

            double discount = Math.max(0.0D, Math.min(
                    90.0D, RelicManager.effectBonus(player, "waystone_travel_discount")));
            if (discount <= 0.0D) {
                return;
            }

            Object requirements = invoke(event, "getRequirements");
            scaleRequirement(requirements, 1.0D - discount / 100.0D);
        } catch (LinkageError | ReflectiveOperationException exception) {
            if (!runtimeFailureLogged) {
                runtimeFailureLogged = true;
                YoikoServerCore.LOGGER.error("Could not apply the Waystones relic discount.", exception);
            }
        }
    }

    private static void scaleRequirement(Object requirement, double multiplier)
            throws ReflectiveOperationException {
        if (requirement == null) {
            return;
        }

        String type = requirement.getClass().getName();
        if (type.equals("net.blay09.mods.waystones.requirement.CombinedRequirement")) {
            Object nested = invoke(requirement, "getRequirements");
            if (nested instanceof Collection<?> requirements) {
                for (Object child : requirements) {
                    scaleRequirement(child, multiplier);
                }
            }
            return;
        }

        if (type.equals("net.blay09.mods.waystones.requirement.ExperienceLevelRequirement")) {
            scaleInteger(requirement, "getLevels", "setLevels", multiplier);
        } else if (type.equals("net.blay09.mods.waystones.requirement.ExperiencePointsRequirement")) {
            scaleInteger(requirement, "getPoints", "setPoints", multiplier);
        } else if (type.equals("net.blay09.mods.waystones.requirement.CooldownRequirement")
                || type.equals("net.blay09.mods.waystones.requirement.SoftCooldownRequirement")) {
            ResourceLocation key = (ResourceLocation) invoke(requirement, "getCooldownKey");
            int seconds = ((Number) invoke(requirement, "getCooldownSeconds")).intValue();
            requirement.getClass()
                    .getMethod("setCooldown", ResourceLocation.class, int.class)
                    .invoke(requirement, key, scaledInteger(seconds, multiplier));
        }
    }

    private static void scaleInteger(Object target, String getter, String setter, double multiplier)
            throws ReflectiveOperationException {
        int value = ((Number) invoke(target, getter)).intValue();
        target.getClass().getMethod(setter, int.class).invoke(target, scaledInteger(value, multiplier));
    }

    private static int scaledInteger(int value, double multiplier) {
        if (value <= 0) {
            return value;
        }
        return Math.max(0, (int) Math.round(value * multiplier));
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }
}
