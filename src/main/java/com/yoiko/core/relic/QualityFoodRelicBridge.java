package com.yoiko.core.relic;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.network.RelicEffectHudPayload.ValueKind;
import java.lang.reflect.Method;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Optional, reflection-backed integration so Quality Food remains a soft dependency. */
public final class QualityFoodRelicBridge {
    private static final String MOD_ID = "quality_food";
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final ResourceKey<Registry<Object>> QUALITY_TYPE_REGISTRY = (ResourceKey) ResourceKey
            .createRegistryKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, "quality_types"));
    private static volatile Integration integration;
    private static volatile boolean integrationUnavailable;

    private QualityFoodRelicBridge() {
    }

    public static boolean tryUpgradeQuality(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        double chance = Math.min(20.0D,
                Math.max(0.0D, RelicManager.effectBonus(player, "quality_food_grade_upgrade_chance")));
        if (chance <= 0.0D) {
            return false;
        }

        Integration access = integration();
        if (access == null) {
            return false;
        }
        try {
            if ((boolean) access.isInvalidItem().invoke(null, stack)) {
                return false;
            }
            Object quality = access.getQuality().invoke(null, stack);
            int currentLevel = quality == null ? 0 : ((Number) access.qualityLevel().invoke(quality)).intValue();
            Holder.Reference<Object> nextQuality = nextQualityType(player, access, currentLevel);
            if (nextQuality == null || player.getRandom().nextDouble() * 100.0D >= chance) {
                return false;
            }
            return Boolean.TRUE.equals(access.applyQuality().invoke(null, stack, nextQuality));
        } catch (ReflectiveOperationException | LinkageError exception) {
            disableIntegration(exception);
            return false;
        }
    }

    public static void playUpgradeFeedback(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.35F, 1.6F);
        RelicHudNotifier.proc(player, "quality_food_grade_upgrade_chance", ValueKind.NONE, 0.0D);
    }

    private static Holder.Reference<Object> nextQualityType(
            ServerPlayer player, Integration access, int currentLevel
    ) throws ReflectiveOperationException {
        Registry<Object> registry = player.registryAccess().registryOrThrow(QUALITY_TYPE_REGISTRY);
        Holder.Reference<Object> best = null;
        int bestLevel = Integer.MAX_VALUE;
        for (Holder.Reference<Object> holder : registry.holders().toList()) {
            Object qualityType = holder.value();
            if (!access.qualityTypeClass().isInstance(qualityType)) {
                continue;
            }
            int level = ((Number) access.qualityTypeLevel().invoke(qualityType)).intValue();
            if (level > currentLevel && level < bestLevel) {
                best = holder;
                bestLevel = level;
            }
        }
        return best;
    }

    private static Integration integration() {
        if (integrationUnavailable || !ModList.get().isLoaded(MOD_ID)) {
            return null;
        }
        Integration cached = integration;
        if (cached != null) {
            return cached;
        }
        synchronized (QualityFoodRelicBridge.class) {
            if (integration != null || integrationUnavailable) {
                return integration;
            }
            try {
                ClassLoader loader = QualityFoodRelicBridge.class.getClassLoader();
                Class<?> utils = Class.forName("de.cadentem.quality_food.util.QualityUtils", false, loader);
                Class<?> quality = Class.forName("de.cadentem.quality_food.core.codecs.Quality", false, loader);
                Class<?> qualityType = Class.forName("de.cadentem.quality_food.core.codecs.QualityType", false, loader);
                integration = new Integration(
                        qualityType,
                        utils.getMethod("isInvalidItem", ItemStack.class),
                        utils.getMethod("getQuality", ItemStack.class),
                        quality.getMethod("level"),
                        qualityType.getMethod("level"),
                        utils.getMethod("applyQuality", ItemStack.class, Holder.class)
                );
            } catch (ReflectiveOperationException | LinkageError exception) {
                disableIntegration(exception);
            }
            return integration;
        }
    }

    private static void disableIntegration(Throwable exception) {
        if (!integrationUnavailable) {
            integrationUnavailable = true;
            integration = null;
            YoikoServerCore.LOGGER.warn(
                    "Quality Food relic integration is unavailable for the installed version.", exception);
        }
    }

    private record Integration(
            Class<?> qualityTypeClass,
            Method isInvalidItem,
            Method getQuality,
            Method qualityLevel,
            Method qualityTypeLevel,
            Method applyQuality
    ) {
    }
}
