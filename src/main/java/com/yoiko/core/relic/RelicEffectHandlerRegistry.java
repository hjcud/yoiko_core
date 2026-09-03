package com.yoiko.core.relic;

import com.yoiko.core.YoikoServerCore;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.yoiko.core.relic.RelicEffectDefinition.RuntimeTarget.*;

public final class RelicEffectHandlerRegistry {
    private static final String COBBLEMON_BRIDGE = "com.yoiko.core.relic.CobblemonRelicBridge";
    private static final String MEGA_SHOWDOWN_BRIDGE = "com.yoiko.core.relic.MegaShowdownRelicBridge";
    private static final String WAYSTONES_BRIDGE = "com.yoiko.core.relic.WaystonesRelicBridge";
    private static final String QUALITY_FOOD_BRIDGE = "com.yoiko.core.relic.QualityFoodRelicBridge";
    private static final String TREASURE_RABBIT_MANAGER = "com.yoiko.core.treasure.TreasureRabbitManager";
    private static final String RUNTIME = "com.yoiko.core.relic.RelicRuntimeManager";
    private static final String MANAGER = "com.yoiko.core.relic.RelicManager";
    private static final String SERVER_EVENTS = "com.yoiko.core.server.YoikoServerEvents";
    private static final String STORAGE_MANAGER = "com.yoiko.core.storage.YoikoStorageManager";
    private static final Map<String, RelicEffectHandler> HANDLERS = new LinkedHashMap<>();

    static {
        register("pokemon_friendship_gain_bonus", COBBLEMON_EVENT, COBBLEMON_BRIDGE, "onFriendshipUpdated", true);
        register("pokemon_exp_multiplier_bonus", COBBLEMON_EVENT, COBBLEMON_BRIDGE, "onExperienceGained", true);
        register("pokemon_catch_rate_bonus", COBBLEMON_EVENT, COBBLEMON_BRIDGE, "onPokemonCatchRate", true);
        register("natural_shiny_chance_bonus", COBBLEMON_EVENT, COBBLEMON_BRIDGE, "onShinyChanceCalculation", true);
        register("pokemon_common_spawn_weight_bonus", COBBLEMON_SPAWN, COBBLEMON_BRIDGE, "onSpawnBucketChosen", true);
        register("pokemon_rare_spawn_weight_bonus", COBBLEMON_SPAWN, COBBLEMON_BRIDGE, "onSpawnBucketChosen", true);
        register("pokemon_ultra_rare_spawn_weight_bonus", COBBLEMON_SPAWN, COBBLEMON_BRIDGE, "onSpawnBucketChosen", true);
        register("failed_pokeball_return_chance", COBBLEMON_EVENT, COBBLEMON_BRIDGE, "onPokeBallCaptureCalculated", true);
        register("battle_victory_heal_percent", COBBLEMON_EVENT, COBBLEMON_BRIDGE, "onBattleVictory", true);
        register("battle_victory_status_cure_chance", COBBLEMON_EVENT, COBBLEMON_BRIDGE, "onBattleVictory", true);
        register("battle_victory_move_pp_restore_chance", COBBLEMON_EVENT, COBBLEMON_BRIDGE, "onBattleVictory", true);
        register("mercy_hp_floor", COBBLEMON_EVENT, COBBLEMON_BRIDGE, "onBattleFainted", true);
        register("mega_shard_find_bonus", COBBLEMON_EVENT, MEGA_SHOWDOWN_BRIDGE, "onLootDropped", true);

        register("player_exp_multiplier_bonus", PLAYER_EVENT, SERVER_EVENTS, "onPlayerXpChange", false);
        register("waystone_travel_discount", PLAYER_EVENT, WAYSTONES_BRIDGE, "onWaystoneTeleport", false);
        register("totemless_revive_chance", PLAYER_EVENT, SERVER_EVENTS, "onLivingDeath", false);
        register("low_health_damage_bonus", PLAYER_EVENT, RUNTIME, "onLivingIncomingDamage", false);
        register("riposte_damage_bonus", PLAYER_EVENT, RUNTIME, "onLivingShieldBlock", false);
        register("heavy_hit_damage_reduction", PLAYER_EVENT, RUNTIME, "onLivingDamagePre", false);
        register("ranged_distance_damage_bonus", PLAYER_EVENT, RUNTIME, "onLivingIncomingDamage", false);
        register("harmful_effect_duration_reduction", PLAYER_EVENT, RUNTIME, "onMobEffectApplicable", false);
        register("low_health_escape_speed_bonus", PLAYER_EVENT, RUNTIME, "onLivingDamagePost", false);
        register("rear_attack_damage_bonus", PLAYER_EVENT, RUNTIME, "onLivingIncomingDamage", false);
        register("full_health_first_strike_bonus", PLAYER_EVENT, RUNTIME, "onLivingIncomingDamage", false);
        register("marked_attacker_damage_bonus", PLAYER_EVENT, RUNTIME, "onLivingIncomingDamage", false);
        register("sprinting_knockback_bonus", PLAYER_EVENT, RUNTIME, "onLivingDamagePost", false);
        register("frost_slow_chance", PLAYER_EVENT, RUNTIME, "onLivingDamagePost", false);
        register("kill_attack_speed_bonus", PLAYER_EVENT, RUNTIME, "onLivingDeath", false);
        register("out_of_combat_damage_absorption", PLAYER_EVENT, RUNTIME, "onLivingDamagePre", false);
        register("quality_food_grade_upgrade_chance", PLAYER_EVENT, QUALITY_FOOD_BRIDGE, "tryUpgradeQuality", false);
        register("treasure_rabbit_spawn_chance_bonus", PLAYER_EVENT, TREASURE_RABBIT_MANAGER, "applySpawnChanceBonus", false);
        register("combo_strike_damage_bonus", PLAYER_EVENT, RUNTIME, "onLivingIncomingDamage", false);
        register("yoiko_bag_slots", STORAGE, STORAGE_MANAGER, "activeSlots", false);
        register("player_movement_speed_bonus", PLAYER_ATTRIBUTE, RUNTIME, "applyPlayerAttributes", false);
        register("player_max_health_bonus", PLAYER_ATTRIBUTE, RUNTIME, "applyPlayerAttributes", false);
        register("block_interaction_range_bonus", PLAYER_ATTRIBUTE, RUNTIME, "applyPlayerAttributes", false);
        register("critical_strike_chance_bonus", PLAYER_ATTRIBUTE, RUNTIME, "applyPlayerAttributes", false);
        register("critical_strike_damage_bonus", PLAYER_ATTRIBUTE, RUNTIME, "applyPlayerAttributes", false);
        register("player_knockback_resistance_bonus", PLAYER_ATTRIBUTE, RUNTIME, "applyPlayerAttributes", false);
        register("player_armor_toughness_bonus", PLAYER_ATTRIBUTE, RUNTIME, "applyPlayerAttributes", false);
        register("player_luck_bonus", PLAYER_ATTRIBUTE, RUNTIME, "applyPlayerAttributes", false);
        register("mounted_pokemon_speed_bonus", COBBLEMON_RIDING, RUNTIME, "applyMountedPokemonSpeed", true);
        register("radiant_high_air_step_count", PLAYER_EVENT, RUNTIME, "tryAirStep", false);
        register("radiant_double_air_step_count", PLAYER_EVENT, RUNTIME, "tryAirStep", false);
        register("satiation_absorption_conversion", PLAYER_EVENT, RUNTIME, "onFoodConsumed", false);
        register("fire_cocoon_duration", PLAYER_EVENT, RUNTIME, "tickFireCocoon", false);
        register("equipped_relic_level_bonus", CORE, MANAGER, "effectiveEquippedLevels", false);
        register("weakest_relic_level_bonus", CORE, MANAGER, "effectiveEquippedLevels", false);
        register("chromatic_contract_level_shift", CORE, MANAGER, "effectiveEquippedLevels", false);
    }

    private RelicEffectHandlerRegistry() {
    }

    public static RelicEffectHandler get(String effectKey) {
        return HANDLERS.get(effectKey);
    }

    public static String validationError(RelicEffectDefinition definition) {
        return validationError(definition, false);
    }

    private static String validationError(RelicEffectDefinition definition, boolean requireRegisteredEvents) {
        RelicEffectHandler handler = HANDLERS.get(definition.key());
        if (handler == null) {
            return "no RelicEffectHandler is registered";
        }
        if (handler.runtimeTarget() != definition.runtimeTarget()) {
            return "runtime target mismatch: definition=" + definition.runtimeTarget()
                    + ", handler=" + handler.runtimeTarget();
        }
        if (handler.cobblemonDependent() != definition.cobblemonDependent()) {
            return "Cobblemon dependency mismatch";
        }
        if (requireRegisteredEvents
                && COBBLEMON_BRIDGE.equals(handler.implementationClass())
                && !CobblemonRelicBridge.isRegistered()) {
            return "Cobblemon event bridge is not registered";
        }
        if (requireRegisteredEvents
                && (SERVER_EVENTS.equals(handler.implementationClass())
                || RUNTIME.equals(handler.implementationClass()))
                && !com.yoiko.core.server.YoikoServerEvents.isRegistered()) {
            return "NeoForge server event bridge is not registered";
        }
        return handler.validationError();
    }

    public static boolean validateAll() {
        Set<String> invalidEffects = invalidEffectKeys(true);
        for (RelicEffectDefinition definition : RelicEffectRegistry.definitions()) {
            String error = validationError(definition, true);
            if (!error.isBlank()) {
                YoikoServerCore.LOGGER.error(
                        "Relic effect '{}' is disabled because its handler is invalid: {}",
                        definition.key(), error);
            }
        }
        if (invalidEffects.isEmpty()) {
            YoikoServerCore.LOGGER.info(
                    "Validated {} relic effect handlers.", RelicEffectRegistry.definitions().size());
        }
        return invalidEffects.isEmpty();
    }

    public static Set<String> invalidRuntimeEffectKeys() {
        return invalidEffectKeys(true);
    }

    private static Set<String> invalidEffectKeys(boolean requireRegisteredEvents) {
        Set<String> invalid = new LinkedHashSet<>();
        for (RelicEffectDefinition definition : RelicEffectRegistry.definitions()) {
            if (!validationError(definition, requireRegisteredEvents).isBlank()) {
                invalid.add(definition.key());
            }
        }
        return Set.copyOf(invalid);
    }

    private static void register(
            String effectKey,
            RelicEffectDefinition.RuntimeTarget target,
            String implementationClass,
            String implementationMethod,
            boolean cobblemonDependent
    ) {
        RelicEffectHandler handler = new RelicEffectHandler(
                effectKey, target, implementationClass, implementationMethod, cobblemonDependent);
        if (HANDLERS.putIfAbsent(effectKey, handler) != null) {
            throw new IllegalStateException("Duplicate relic effect handler: " + effectKey);
        }
    }
}
