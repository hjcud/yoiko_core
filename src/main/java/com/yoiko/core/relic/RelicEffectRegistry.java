package com.yoiko.core.relic;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.ModList;

import static com.yoiko.core.relic.RelicEffectDefinition.Aggregation.MAXIMUM;
import static com.yoiko.core.relic.RelicEffectDefinition.RuntimeTarget.*;
import static com.yoiko.core.relic.RelicEffectDefinition.Unit.*;
import static com.yoiko.core.relic.RelicEffectDefinition.Application.*;
import static com.yoiko.core.relic.RelicAppraisalCategory.*;

public final class RelicEffectRegistry {
    private static final Map<String, String> OPTIONAL_EFFECT_MODS = Map.of(
            "critical_strike_chance_bonus", "critical_strike",
            "critical_strike_damage_bonus", "critical_strike",
            "waystone_travel_discount", "waystones",
            "mega_shard_find_bonus", "mega_showdown",
            "quality_food_grade_upgrade_chance", "quality_food"
    );
    private static final Map<String, RelicEffectDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Set<RelicRarity> STANDARD_RARITIES = Set.copyOf(EnumSet.range(
            RelicRarity.COMMON, RelicRarity.MYSTIC));
    private static final Set<RelicRarity> RADIANT_ONLY = Set.of(RelicRarity.RADIANT);

    static {
        registerStandard("pokemon_friendship_gain_bonus", POKEMON, PERCENT, 500.0D, "%.2f", COBBLEMON_EVENT, true);
        registerStandard("pokemon_exp_multiplier_bonus", POKEMON, PERCENT, 500.0D, "%.2f", COBBLEMON_EVENT, true);
        registerStandard("pokemon_catch_rate_bonus", POKEMON, PERCENT, 100.0D, "%.2f", COBBLEMON_EVENT, true);
        registerStandard("natural_shiny_chance_bonus", POKEMON, PERCENT, 100.0D, "%.3f", COBBLEMON_EVENT, true);
        registerStandard("player_exp_multiplier_bonus", EXPLORATION, PERCENT, 500.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("yoiko_bag_slots", EXPLORATION, SLOTS, 5_373.0D, "%.0f", STORAGE, false);
        registerStandard("player_movement_speed_bonus", EXPLORATION, PERCENT, 100.0D, "%.2f", PLAYER_ATTRIBUTE, false);
        registerStandard("player_max_health_bonus", DEFENSE, NUMBER, 1_024.0D, "%.2f", PLAYER_ATTRIBUTE, false);
        registerStandard("block_interaction_range_bonus", EXPLORATION, BLOCKS, 32.0D, "%.2f", PLAYER_ATTRIBUTE, false);
        registerStandard("critical_strike_chance_bonus", COMBAT, CHANCE, 100.0D, "%.2f", PLAYER_ATTRIBUTE, false);
        registerStandard("critical_strike_damage_bonus", COMBAT, PERCENT, 200.0D, "%.2f", PLAYER_ATTRIBUTE, false);
        registerStandard("low_health_damage_bonus", COMBAT, PERCENT, 20.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("riposte_damage_bonus", COMBAT, PERCENT, 50.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("heavy_hit_damage_reduction", DEFENSE, PERCENT, 25.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("ranged_distance_damage_bonus", COMBAT, PERCENT, 20.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("harmful_effect_duration_reduction", DEFENSE, PERCENT, 65.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("low_health_escape_speed_bonus", DEFENSE, PERCENT, 30.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("rear_attack_damage_bonus", COMBAT, PERCENT, 15.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("full_health_first_strike_bonus", COMBAT, PERCENT, 18.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("marked_attacker_damage_bonus", COMBAT, PERCENT, 15.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("sprinting_knockback_bonus", COMBAT, PERCENT, 40.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("frost_slow_chance", COMBAT, CHANCE, 25.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("kill_attack_speed_bonus", COMBAT, PERCENT, 12.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("out_of_combat_damage_absorption", DEFENSE, PERCENT, 20.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("waystone_travel_discount", EXPLORATION, PERCENT, 90.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("mega_shard_find_bonus", POKEMON, PERCENT, 100.0D, "%.2f", COBBLEMON_EVENT, true);
        registerStandard("player_knockback_resistance_bonus", DEFENSE, PERCENT, 100.0D, "%.2f", PLAYER_ATTRIBUTE, false);
        registerStandard("player_armor_toughness_bonus", DEFENSE, NUMBER, 20.0D, "%.2f", PLAYER_ATTRIBUTE, false);
        registerStandard("player_luck_bonus", EXPLORATION, NUMBER, 10.0D, "%.2f", PLAYER_ATTRIBUTE, false);
        registerStandard("pokemon_common_spawn_weight_bonus", POKEMON, PERCENT, 500.0D, "%.2f", COBBLEMON_SPAWN, true);
        registerStandard("pokemon_rare_spawn_weight_bonus", POKEMON, PERCENT, 500.0D, "%.2f", COBBLEMON_SPAWN, true);
        registerStandard("pokemon_ultra_rare_spawn_weight_bonus", POKEMON, PERCENT, 500.0D, "%.2f", COBBLEMON_SPAWN, true);
        registerStandard("totemless_revive_chance", DEFENSE, CHANCE, 100.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("mounted_pokemon_speed_bonus", POKEMON, PERCENT, 500.0D, "%.2f", COBBLEMON_RIDING, true);
        registerStandard("failed_pokeball_return_chance", POKEMON, CHANCE, 100.0D, "%.2f", COBBLEMON_EVENT, true);
        registerStandard("battle_victory_heal_percent", POKEMON, PERCENT, 12.0D, "%.2f", COBBLEMON_EVENT, true);
        registerStandard("battle_victory_status_cure_chance", POKEMON, CHANCE, 80.0D, "%.2f", COBBLEMON_EVENT, true);
        registerStandard("battle_victory_move_pp_restore_chance", POKEMON, CHANCE, 50.0D, "%.2f", COBBLEMON_EVENT, true);
        registerStandard("quality_food_grade_upgrade_chance", EXPLORATION, CHANCE, 20.0D, "%.2f", PLAYER_EVENT, false);
        registerStandard("treasure_rabbit_spawn_chance_bonus", EXPLORATION, PERCENT, 25.0D, "%.2f", PLAYER_EVENT, false);
        registerRadiant("mercy_hp_floor", POKEMON, CHANCE, 100.0D, "%.0f", COBBLEMON_EVENT, true);
        registerRadiant("equipped_relic_level_bonus", ALL, LEVELS, 1.0D, "%.0f", CORE, false);
        registerRadiant("weakest_relic_level_bonus", ALL, LEVELS, 2.0D, "%.0f", CORE, false);
        registerRadiant("chromatic_contract_level_shift", ALL, LEVELS, 3.0D, "%.0f", CORE, false);
        registerStandard("satiation_absorption_conversion", DEFENSE, PERCENT, 100.0D, "%.2f", PLAYER_EVENT, false);
        registerRadiant("fire_cocoon_duration", DEFENSE, SECONDS, 60.0D, "%.0f", PLAYER_EVENT, false);
        registerRadiant("radiant_high_air_step_count", EXPLORATION, NUMBER, 1.0D, "%.0f", PLAYER_EVENT, false);
        registerRadiant("radiant_double_air_step_count", EXPLORATION, NUMBER, 2.0D, "%.0f", PLAYER_EVENT, false);
        registerRadiant("combo_strike_damage_bonus", COMBAT, PERCENT, 40.0D, "%.0f", PLAYER_EVENT, false);
    }

    private RelicEffectRegistry() {
    }

    public static RelicEffectDefinition get(String key) {
        return DEFINITIONS.get(key);
    }

    public static boolean contains(String key) {
        return DEFINITIONS.containsKey(key);
    }

    public static Collection<RelicEffectDefinition> definitions() {
        return java.util.List.copyOf(DEFINITIONS.values());
    }

    /**
     * Optional integrations stay registered so configured/owned relic data remains readable, but
     * they are omitted from rolls and the dex while their provider mod is absent.
     */
    public static boolean isRuntimeAvailable(String key) {
        String requiredMod = OPTIONAL_EFFECT_MODS.get(key);
        return requiredMod == null || ModList.get().isLoaded(requiredMod);
    }

    private static void registerStandard(String key, RelicAppraisalCategory category,
                                         RelicEffectDefinition.Unit unit, double maximumValue,
                                         String format, RelicEffectDefinition.RuntimeTarget target,
                                         boolean cobblemonDependent) {
        register(key, category, unit, maximumValue, format, target, cobblemonDependent, STANDARD_RARITIES);
    }

    private static void registerRadiant(String key, RelicAppraisalCategory category,
                                        RelicEffectDefinition.Unit unit, double maximumValue,
                                        String format, RelicEffectDefinition.RuntimeTarget target,
                                        boolean cobblemonDependent) {
        register(key, category, unit, maximumValue, format, target, cobblemonDependent, RADIANT_ONLY);
    }

    private static void register(String key, RelicAppraisalCategory category,
                                 RelicEffectDefinition.Unit unit, double maximumValue,
                                 String format, RelicEffectDefinition.RuntimeTarget target,
                                 boolean cobblemonDependent, Set<RelicRarity> allowedRarities) {
        RelicEffectDefinition.Application application = switch (unit) {
            case PERCENT -> MULTIPLY_PERCENT;
            case CHANCE -> VALUE;
            case SLOTS, BLOCKS, SECONDS, LEVELS, NUMBER -> ADD_FLAT;
        };
        RelicEffectDefinition definition = new RelicEffectDefinition(
                key, category, unit, MAXIMUM, maximumValue, format, target, cobblemonDependent,
                application, allowedRarities);
        if (DEFINITIONS.putIfAbsent(key, definition) != null) {
            throw new IllegalStateException("Duplicate relic effect key: " + key);
        }
    }
}
