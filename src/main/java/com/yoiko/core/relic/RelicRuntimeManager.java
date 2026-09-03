package com.yoiko.core.relic;

import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.riding.stats.RidingStat;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.network.RelicEffectHudPayload.ValueKind;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

public final class RelicRuntimeManager {
    private static final ResourceLocation MOVEMENT_SPEED_ID = ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, "relic_movement_speed");
    private static final ResourceLocation MAX_HEALTH_ID = ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, "relic_max_health");
    private static final ResourceLocation BLOCK_INTERACTION_RANGE_ID =
            ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, "relic_block_interaction_range");
    private static final ResourceLocation CRITICAL_STRIKE_CHANCE_ATTRIBUTE_ID =
            ResourceLocation.fromNamespaceAndPath("critical_strike", "chance");
    private static final ResourceLocation CRITICAL_STRIKE_DAMAGE_ATTRIBUTE_ID =
            ResourceLocation.fromNamespaceAndPath("critical_strike", "damage");
    private static final ResourceLocation CRITICAL_STRIKE_CHANCE_ID =
            ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, "relic_critical_strike_chance");
    private static final ResourceLocation CRITICAL_STRIKE_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, "relic_critical_strike_damage");
    private static final ResourceLocation KNOCKBACK_RESISTANCE_ID =
            ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, "relic_knockback_resistance");
    private static final ResourceLocation ARMOR_TOUGHNESS_ID =
            ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, "relic_armor_toughness");
    private static final ResourceLocation LUCK_ID =
            ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, "relic_luck");
    private static final ResourceLocation SURVIVAL_ANKLET_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, "relic_survival_anklet_speed");
    private static final ResourceLocation VICTORY_DRUM_ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, "relic_victory_drum_attack_speed");
    private static final Map<UUID, UUID> LAST_MOUNT = new HashMap<>();
    private static final Map<UUID, Double> LAST_MOUNT_BONUS = new HashMap<>();
    private static final Map<UUID, EnumMap<RidingStyle, Double>> RIDE_SPEED_BASES = new HashMap<>();
    private static final Map<UUID, Integer> LAST_AIR_STEP_SEQUENCE = new HashMap<>();
    private static final Map<UUID, RequestWindow> AIR_STEP_REQUEST_WINDOWS = new HashMap<>();
    private static final Map<UUID, Long> FIRE_COCOON_READY_TICK = new HashMap<>();
    private static final Set<UUID> FIRE_COCOON_COOLDOWN_PENDING = new HashSet<>();
    private static final Map<UUID, FoodUseState> FOOD_USE_STATES = new HashMap<>();
    private static final Map<UUID, PlayerAttributeProfile> LAST_PLAYER_ATTRIBUTES = new HashMap<>();
    private static final Map<UUID, Long> RIPOSTE_CHARGE_EXPIRES = new HashMap<>();
    private static final Map<UUID, Long> RIPOSTE_READY_TICK = new HashMap<>();
    private static final Map<UUID, Long> GUARDIAN_PULSE_READY_TICK = new HashMap<>();
    private static final Map<UUID, ComboState> COMBO_STATES = new HashMap<>();
    private static final Map<UUID, Long> SURVIVAL_ANKLET_READY_TICK = new HashMap<>();
    private static final Map<UUID, Long> SURVIVAL_ANKLET_SPEED_EXPIRES = new HashMap<>();
    private static final Set<UUID> MOB_EFFECT_REAPPLICATION_GUARD = new HashSet<>();
    private static final Map<UUID, TimedTarget> GRUDGE_TARGETS = new HashMap<>();
    private static final Map<UUID, Long> FROST_NEEDLE_READY_TICK = new HashMap<>();
    private static final Map<UUID, Long> VICTORY_DRUM_READY_TICK = new HashMap<>();
    private static final Map<UUID, Long> VICTORY_DRUM_EXPIRES = new HashMap<>();
    private static final Map<UUID, Long> GUARDIAN_HOURGLASS_LAST_DAMAGE_TICK = new HashMap<>();
    private static final Map<UUID, Long> GUARDIAN_HOURGLASS_READY_TICK = new HashMap<>();
    private static final Set<UUID> GUARDIAN_HOURGLASS_READY_NOTIFIED = new HashSet<>();
    private static final Map<UUID, Map<String, Long>> LAST_HUD_PROC_TICKS = new HashMap<>();
    private static final String AIR_STEPS_USED_TAG = YoikoServerCore.MODID + ":air_steps_used";
    private static final String AIR_STEP_LIMIT_TAG = YoikoServerCore.MODID + ":air_step_limit";
    private static final int AIR_STEP_REQUEST_WINDOW_TICKS = 20;
    private static final int MAX_AIR_STEP_REQUESTS_PER_WINDOW = 4;
    private static final int MOUNT_REFRESH_TICKS = 5;
    private static final int RIPOSTE_CHARGE_TICKS = 80;
    private static final int RIPOSTE_COOLDOWN_TICKS = 160;
    private static final int GUARDIAN_PULSE_COOLDOWN_TICKS = 600;
    private static final int COMBO_WINDOW_TICKS = 60;
    private static final int SURVIVAL_ANKLET_DURATION_TICKS = 60;
    private static final int SURVIVAL_ANKLET_COOLDOWN_TICKS = 600;
    private static final int GRUDGE_DURATION_TICKS = 120;
    private static final int FROST_NEEDLE_DURATION_TICKS = 30;
    private static final int FROST_NEEDLE_COOLDOWN_TICKS = 60;
    private static final int VICTORY_DRUM_DURATION_TICKS = 80;
    private static final int VICTORY_DRUM_COOLDOWN_TICKS = 160;
    private static final int GUARDIAN_HOURGLASS_CHARGE_TICKS = 240;
    private static final int GUARDIAN_HOURGLASS_COOLDOWN_TICKS = 600;
    private static final int HUD_REPEAT_INTERVAL_TICKS = 10;
    private static final double HUNTER_HEALTH_THRESHOLD = 0.30D;
    private static final double GUARDIAN_DAMAGE_THRESHOLD = 0.25D;
    private static final double SURVIVAL_ANKLET_HEALTH_THRESHOLD = 0.25D;
    private static final double HAWK_MINIMUM_DISTANCE = 8.0D;
    private static final double HAWK_MAXIMUM_DISTANCE = 24.0D;
    private static final double REAR_ATTACK_DOT_THRESHOLD = -0.5D;
    private static final double COMBO_EXTRA_DAMAGE_CAP = 6.0D;
    private static final AirStepProfile HIGH_AIR_STEP = new AirStepProfile(1, 0.68D, 0.52D);
    private static final AirStepProfile DOUBLE_AIR_STEP = new AirStepProfile(2, 0.48D, 0.38D);

    private RelicRuntimeManager() {
    }

    public static void tick(ServerPlayer player) {
        if (!LAST_PLAYER_ATTRIBUTES.containsKey(player.getUUID())) {
            applyPlayerAttributes(player);
        }
        if (Math.floorMod(player.tickCount + player.getId(), MOUNT_REFRESH_TICKS) == 0) {
            applyMountedPokemonSpeed(player);
        }
        tickAirStep(player);
        tickFireCocoon(player);
        tickSurvivalAnklet(player);
        tickVictoryDrum(player);
        tickGuardianHourglass(player);
    }

    public static void clear(ServerPlayer player) {
        removeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_SPEED_ID);
        removeModifier(player.getAttribute(Attributes.MAX_HEALTH), MAX_HEALTH_ID);
        removeModifier(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), BLOCK_INTERACTION_RANGE_ID);
        removeModifier(optionalAttribute(player, CRITICAL_STRIKE_CHANCE_ATTRIBUTE_ID), CRITICAL_STRIKE_CHANCE_ID);
        removeModifier(optionalAttribute(player, CRITICAL_STRIKE_DAMAGE_ATTRIBUTE_ID), CRITICAL_STRIKE_DAMAGE_ID);
        removeModifier(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK_RESISTANCE_ID);
        removeModifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS), ARMOR_TOUGHNESS_ID);
        removeModifier(player.getAttribute(Attributes.LUCK), LUCK_ID);
        removeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SURVIVAL_ANKLET_SPEED_ID);
        removeModifier(player.getAttribute(Attributes.ATTACK_SPEED), VICTORY_DRUM_ATTACK_SPEED_ID);
        restoreLastMount(player);
        LAST_AIR_STEP_SEQUENCE.remove(player.getUUID());
        AIR_STEP_REQUEST_WINDOWS.remove(player.getUUID());
        FIRE_COCOON_READY_TICK.remove(player.getUUID());
        FIRE_COCOON_COOLDOWN_PENDING.remove(player.getUUID());
        FOOD_USE_STATES.remove(player.getUUID());
        LAST_PLAYER_ATTRIBUTES.remove(player.getUUID());
        RIPOSTE_CHARGE_EXPIRES.remove(player.getUUID());
        RIPOSTE_READY_TICK.remove(player.getUUID());
        GUARDIAN_PULSE_READY_TICK.remove(player.getUUID());
        COMBO_STATES.remove(player.getUUID());
        SURVIVAL_ANKLET_READY_TICK.remove(player.getUUID());
        SURVIVAL_ANKLET_SPEED_EXPIRES.remove(player.getUUID());
        MOB_EFFECT_REAPPLICATION_GUARD.remove(player.getUUID());
        GRUDGE_TARGETS.remove(player.getUUID());
        FROST_NEEDLE_READY_TICK.remove(player.getUUID());
        VICTORY_DRUM_READY_TICK.remove(player.getUUID());
        VICTORY_DRUM_EXPIRES.remove(player.getUUID());
        GUARDIAN_HOURGLASS_LAST_DAMAGE_TICK.remove(player.getUUID());
        GUARDIAN_HOURGLASS_READY_TICK.remove(player.getUUID());
        GUARDIAN_HOURGLASS_READY_NOTIFIED.remove(player.getUUID());
        LAST_HUD_PROC_TICKS.remove(player.getUUID());
    }

    public static void refreshPlayerAttributes(ServerPlayer player) {
        LAST_PLAYER_ATTRIBUTES.remove(player.getUUID());
    }

    public static void onLivingShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getBlocked()
                || event.getBlockedDamage() <= 0.0F
                || RelicManager.effectBonus(player, "riposte_damage_bonus") <= 0.0D) {
            return;
        }

        UUID playerId = player.getUUID();
        long tick = player.server.getTickCount();
        if (tick < RIPOSTE_READY_TICK.getOrDefault(playerId, Long.MIN_VALUE)) {
            return;
        }
        RIPOSTE_CHARGE_EXPIRES.put(playerId, tick + RIPOSTE_CHARGE_TICKS);
        RIPOSTE_READY_TICK.put(playerId, tick + RIPOSTE_COOLDOWN_TICKS);
        RelicHudNotifier.charged(player, "riposte_damage_bonus", ValueKind.DAMAGE_BONUS,
                Math.min(50.0D, RelicManager.effectBonus(player, "riposte_damage_bonus")),
                RIPOSTE_CHARGE_TICKS);
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || event.getEntity() == attacker
                || event.getAmount() <= 0.0F) {
            return;
        }

        LivingEntity target = event.getEntity();
        double damage = event.getAmount();
        boolean modified = false;
        boolean directPlayerAttack = event.getSource().is(DamageTypes.PLAYER_ATTACK)
                && event.getSource().getDirectEntity() == attacker;
        boolean directProjectileAttack = event.getSource().getDirectEntity() instanceof AbstractArrow;

        double hunterBonus = Math.min(20.0D,
                Math.max(0.0D, RelicManager.effectBonus(attacker, "low_health_damage_bonus")));
        if (hunterBonus > 0.0D
                && target.getMaxHealth() > 0.0F
                && target.getHealth() <= target.getMaxHealth() * HUNTER_HEALTH_THRESHOLD) {
            damage *= 1.0D + hunterBonus / 100.0D;
            modified = true;
            showHudProc(attacker, "low_health_damage_bonus", ValueKind.DAMAGE_BONUS, hunterBonus);
        }

        double rangedBonus = Math.min(20.0D,
                Math.max(0.0D, RelicManager.effectBonus(attacker, "ranged_distance_damage_bonus")));
        if (rangedBonus > 0.0D && directProjectileAttack) {
            double distance = attacker.distanceTo(target);
            double distanceFactor = Math.clamp(
                    (distance - HAWK_MINIMUM_DISTANCE) / (HAWK_MAXIMUM_DISTANCE - HAWK_MINIMUM_DISTANCE),
                    0.0D, 1.0D);
            if (distanceFactor > 0.0D) {
                damage *= 1.0D + rangedBonus * distanceFactor / 100.0D;
                modified = true;
                showHudProc(attacker, "ranged_distance_damage_bonus", ValueKind.DAMAGE_BONUS,
                        rangedBonus * distanceFactor);
            }
        }

        double firstStrikeBonus = Math.min(18.0D,
                Math.max(0.0D, RelicManager.effectBonus(attacker, "full_health_first_strike_bonus")));
        if (firstStrikeBonus > 0.0D
                && (directPlayerAttack || directProjectileAttack)
                && target.getMaxHealth() > 0.0F
                && target.getHealth() >= target.getMaxHealth()) {
            damage *= 1.0D + firstStrikeBonus / 100.0D;
            modified = true;
            showHudProc(attacker, "full_health_first_strike_bonus", ValueKind.DAMAGE_BONUS,
                    firstStrikeBonus);
        }

        double grudgeBonus = Math.min(15.0D,
                Math.max(0.0D, RelicManager.effectBonus(attacker, "marked_attacker_damage_bonus")));
        if (grudgeBonus > 0.0D) {
            long tick = attacker.server.getTickCount();
            TimedTarget marked = GRUDGE_TARGETS.get(attacker.getUUID());
            if (marked != null && tick > marked.expiresAtTick()) {
                GRUDGE_TARGETS.remove(attacker.getUUID());
                marked = null;
            }
            if (marked != null && marked.targetId().equals(target.getUUID())) {
                damage *= 1.0D + grudgeBonus / 100.0D;
                modified = true;
                showHudProc(attacker, "marked_attacker_damage_bonus", ValueKind.DAMAGE_BONUS,
                        grudgeBonus);
            }
        }

        if (directPlayerAttack) {
            UUID attackerId = attacker.getUUID();
            long tick = attacker.server.getTickCount();
            double riposteBonus = Math.min(50.0D,
                    Math.max(0.0D, RelicManager.effectBonus(attacker, "riposte_damage_bonus")));
            Long chargeExpires = RIPOSTE_CHARGE_EXPIRES.get(attackerId);
            if (riposteBonus > 0.0D && chargeExpires != null && tick <= chargeExpires) {
                damage *= 1.0D + riposteBonus / 100.0D;
                modified = true;
                RIPOSTE_CHARGE_EXPIRES.remove(attackerId);
                emitCombatProc(attacker, target, false);
                RelicHudNotifier.proc(attacker, "riposte_damage_bonus", ValueKind.DAMAGE_BONUS,
                        riposteBonus);
            } else if (chargeExpires != null && tick > chargeExpires) {
                RIPOSTE_CHARGE_EXPIRES.remove(attackerId);
            }

            double comboBonus = Math.min(40.0D,
                    Math.max(0.0D, RelicManager.effectBonus(attacker, "combo_strike_damage_bonus")));
            if (comboBonus > 0.0D) {
                ComboState previous = COMBO_STATES.get(attackerId);
                int hitCount = previous != null
                        && previous.targetId().equals(target.getUUID())
                        && tick - previous.lastHitTick() <= COMBO_WINDOW_TICKS
                        ? previous.hitCount() + 1
                        : 1;
                if (hitCount >= 3) {
                    damage += Math.min(COMBO_EXTRA_DAMAGE_CAP, damage * comboBonus / 100.0D);
                    modified = true;
                    COMBO_STATES.remove(attackerId);
                    emitCombatProc(attacker, target, true);
                    RelicHudNotifier.proc(attacker, "combo_strike_damage_bonus",
                            ValueKind.DAMAGE_BONUS, comboBonus);
                } else {
                    COMBO_STATES.put(attackerId, new ComboState(target.getUUID(), hitCount, tick));
                    RelicHudNotifier.progress(attacker, "combo_strike_damage_bonus", hitCount, 3,
                            COMBO_WINDOW_TICKS);
                }
            } else {
                COMBO_STATES.remove(attackerId);
            }

            double rearBonus = Math.min(15.0D,
                    Math.max(0.0D, RelicManager.effectBonus(attacker, "rear_attack_damage_bonus")));
            if (rearBonus > 0.0D && isBehindTarget(attacker, target)) {
                damage *= 1.0D + rearBonus / 100.0D;
                modified = true;
                showHudProc(attacker, "rear_attack_damage_bonus", ValueKind.DAMAGE_BONUS, rearBonus);
            }
        }

        if (modified) {
            event.setAmount((float) Math.max(0.0D, Math.min(Float.MAX_VALUE, damage)));
        }
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getNewDamage() <= 0.0F
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        UUID playerId = player.getUUID();
        long tick = player.server.getTickCount();
        double reductionPercent = Math.min(25.0D,
                Math.max(0.0D, RelicManager.effectBonus(player, "heavy_hit_damage_reduction")));
        if (reductionPercent > 0.0D
                && event.getNewDamage() >= player.getMaxHealth() * GUARDIAN_DAMAGE_THRESHOLD
                && tick >= GUARDIAN_PULSE_READY_TICK.getOrDefault(playerId, Long.MIN_VALUE)) {
            event.setNewDamage((float) (event.getNewDamage() * (1.0D - reductionPercent / 100.0D)));
            GUARDIAN_PULSE_READY_TICK.put(playerId, tick + GUARDIAN_PULSE_COOLDOWN_TICKS);
            RelicHudNotifier.proc(player, "heavy_hit_damage_reduction",
                    ValueKind.DAMAGE_REDUCTION, reductionPercent);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        player.getX(), player.getY() + player.getBbHeight() * 0.55D, player.getZ(),
                        12, 0.35D, 0.45D, 0.35D, 0.05D);
                level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                        SoundSource.PLAYERS, 0.7F, 0.75F);
            }
        }

        double absorptionPercent = Math.min(20.0D,
                Math.max(0.0D, RelicManager.effectBonus(player, "out_of_combat_damage_absorption")));
        Long lastDamageTick = GUARDIAN_HOURGLASS_LAST_DAMAGE_TICK.get(playerId);
        if (absorptionPercent > 0.0D
                && lastDamageTick != null
                && tick - lastDamageTick >= GUARDIAN_HOURGLASS_CHARGE_TICKS
                && tick >= GUARDIAN_HOURGLASS_READY_TICK.getOrDefault(playerId, Long.MIN_VALUE)) {
            event.setNewDamage((float) (event.getNewDamage() * (1.0D - absorptionPercent / 100.0D)));
            GUARDIAN_HOURGLASS_READY_TICK.put(playerId, tick + GUARDIAN_HOURGLASS_COOLDOWN_TICKS);
            GUARDIAN_HOURGLASS_READY_NOTIFIED.remove(playerId);
            RelicHudNotifier.proc(player, "out_of_combat_damage_absorption",
                    ValueKind.DAMAGE_REDUCTION, absorptionPercent);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.WAX_OFF,
                        player.getX(), player.getY() + player.getBbHeight() * 0.55D, player.getZ(),
                        10, 0.32D, 0.42D, 0.32D, 0.02D);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, 0.55F, 0.8F);
            }
        }
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && target != attacker) {
            applySuccessfulAttackEffects(event, attacker, target);
        }

        if (!(target instanceof ServerPlayer player)
                || player.getHealth() <= 0.0F
                || player.getMaxHealth() <= 0.0F) {
            return;
        }

        UUID playerId = player.getUUID();
        long tick = player.server.getTickCount();
        if (RelicManager.effectBonus(player, "out_of_combat_damage_absorption") > 0.0D) {
            GUARDIAN_HOURGLASS_LAST_DAMAGE_TICK.put(playerId, tick);
            GUARDIAN_HOURGLASS_READY_NOTIFIED.remove(playerId);
        }
        if (RelicManager.effectBonus(player, "marked_attacker_damage_bonus") > 0.0D
                && event.getSource().getEntity() instanceof LivingEntity aggressor
                && aggressor != player) {
            GRUDGE_TARGETS.put(playerId, new TimedTarget(aggressor.getUUID(), tick + GRUDGE_DURATION_TICKS));
            RelicHudNotifier.marked(player, "marked_attacker_damage_bonus", ValueKind.DAMAGE_BONUS,
                    Math.min(15.0D, RelicManager.effectBonus(player, "marked_attacker_damage_bonus")),
                    GRUDGE_DURATION_TICKS);
        }

        double speedBonus = Math.min(30.0D,
                Math.max(0.0D, RelicManager.effectBonus(player, "low_health_escape_speed_bonus")));
        float thresholdHealth = player.getMaxHealth() * (float) SURVIVAL_ANKLET_HEALTH_THRESHOLD;
        float healthBeforeHit = player.getHealth() + event.getNewDamage();
        if (speedBonus <= 0.0D
                || healthBeforeHit <= thresholdHealth
                || player.getHealth() > thresholdHealth) {
            return;
        }

        if (tick < SURVIVAL_ANKLET_READY_TICK.getOrDefault(playerId, Long.MIN_VALUE)) {
            return;
        }

        applyModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SURVIVAL_ANKLET_SPEED_ID,
                speedBonus / 100.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        SURVIVAL_ANKLET_SPEED_EXPIRES.put(playerId, tick + SURVIVAL_ANKLET_DURATION_TICKS);
        SURVIVAL_ANKLET_READY_TICK.put(playerId, tick + SURVIVAL_ANKLET_COOLDOWN_TICKS);
        RelicHudNotifier.active(player, "low_health_escape_speed_bonus", ValueKind.MOVEMENT_SPEED,
                speedBonus, SURVIVAL_ANKLET_DURATION_TICKS);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.2D, player.getZ(),
                    12, 0.3D, 0.08D, 0.3D, 0.03D);
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(),
                    SoundSource.PLAYERS, 0.55F, 1.3F);
        }
    }

    private static void applySuccessfulAttackEffects(
            LivingDamageEvent.Post event, ServerPlayer attacker, LivingEntity target
    ) {
        boolean directMelee = event.getSource().is(DamageTypes.PLAYER_ATTACK)
                && event.getSource().getDirectEntity() == attacker;
        boolean arrowOrTrident = event.getSource().getDirectEntity() instanceof AbstractArrow;
        if (!directMelee && !arrowOrTrident) {
            return;
        }

        if (directMelee && attacker.isSprinting()) {
            double knockbackBonus = Math.min(40.0D,
                    Math.max(0.0D, RelicManager.effectBonus(attacker, "sprinting_knockback_bonus")));
            if (knockbackBonus > 0.0D) {
                Vec3 direction = target.position().subtract(attacker.position());
                target.knockback(0.5D * knockbackBonus / 100.0D, direction.x, direction.z);
                showHudProc(attacker, "sprinting_knockback_bonus", ValueKind.KNOCKBACK,
                        knockbackBonus);
            }
        }

        double frostChance = Math.min(25.0D,
                Math.max(0.0D, RelicManager.effectBonus(attacker, "frost_slow_chance")));
        UUID attackerId = attacker.getUUID();
        long tick = attacker.server.getTickCount();
        if (frostChance <= 0.0D
                || tick < FROST_NEEDLE_READY_TICK.getOrDefault(attackerId, Long.MIN_VALUE)
                || attacker.getRandom().nextDouble() * 100.0D >= frostChance) {
            return;
        }

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                FROST_NEEDLE_DURATION_TICKS, 0, false, true), attacker);
        FROST_NEEDLE_READY_TICK.put(attackerId, tick + FROST_NEEDLE_COOLDOWN_TICKS);
        RelicHudNotifier.proc(attacker, "frost_slow_chance", ValueKind.SLOWNESS, 1.5D);
        if (attacker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
                    9, 0.25D, 0.3D, 0.25D, 0.015D);
            level.playSound(null, target.blockPosition(), SoundEvents.GLASS_HIT,
                    SoundSource.PLAYERS, 0.45F, 1.45F);
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Enemy)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        double attackSpeedBonus = Math.min(12.0D,
                Math.max(0.0D, RelicManager.effectBonus(player, "kill_attack_speed_bonus")));
        if (attackSpeedBonus <= 0.0D) {
            return;
        }
        UUID playerId = player.getUUID();
        long tick = player.server.getTickCount();
        if (tick < VICTORY_DRUM_READY_TICK.getOrDefault(playerId, Long.MIN_VALUE)) {
            return;
        }

        applyModifier(player.getAttribute(Attributes.ATTACK_SPEED), VICTORY_DRUM_ATTACK_SPEED_ID,
                attackSpeedBonus / 100.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        VICTORY_DRUM_EXPIRES.put(playerId, tick + VICTORY_DRUM_DURATION_TICKS);
        VICTORY_DRUM_READY_TICK.put(playerId, tick + VICTORY_DRUM_COOLDOWN_TICKS);
        RelicHudNotifier.active(player, "kill_attack_speed_bonus", ValueKind.ATTACK_SPEED,
                attackSpeedBonus, VICTORY_DRUM_DURATION_TICKS);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.NOTE,
                    player.getX(), player.getY() + player.getBbHeight() * 0.7D, player.getZ(),
                    6, 0.35D, 0.25D, 0.35D, 0.0D);
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(),
                    SoundSource.PLAYERS, 0.55F, 1.15F);
        }
    }

    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || MOB_EFFECT_REAPPLICATION_GUARD.contains(player.getUUID())) {
            return;
        }
        MobEffectInstance incoming = event.getEffectInstance();
        if (incoming.getEffect().value().getCategory() != MobEffectCategory.HARMFUL
                || incoming.getEffect().value().isInstantenous()
                || incoming.isInfiniteDuration() || incoming.getDuration() <= 1) {
            return;
        }

        double reduction = Math.min(65.0D,
                Math.max(0.0D, RelicManager.effectBonus(player, "harmful_effect_duration_reduction")));
        if (reduction <= 0.0D) {
            return;
        }
        int reducedDuration = Math.max(1,
                (int) Math.ceil(incoming.getDuration() * (1.0D - reduction / 100.0D)));
        if (reducedDuration >= incoming.getDuration()) {
            return;
        }

        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        Entity source = event.getEffectSource();
        MobEffectInstance reduced = new MobEffectInstance(
                incoming.getEffect(), reducedDuration, incoming.getAmplifier(), incoming.isAmbient(),
                incoming.isVisible(), incoming.showIcon());
        player.server.execute(() -> {
            if (player.isRemoved()) {
                return;
            }
            UUID playerId = player.getUUID();
            MOB_EFFECT_REAPPLICATION_GUARD.add(playerId);
            try {
                player.addEffect(reduced, source);
                RelicHudNotifier.proc(player, "harmful_effect_duration_reduction",
                        ValueKind.DURATION_REDUCTION, reduction);
            } finally {
                MOB_EFFECT_REAPPLICATION_GUARD.remove(playerId);
            }
        });
    }

    private static boolean isBehindTarget(ServerPlayer attacker, LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 toAttacker = attacker.position().subtract(target.position());
        double lookLength = Math.sqrt(look.x * look.x + look.z * look.z);
        double attackerLength = Math.sqrt(toAttacker.x * toAttacker.x + toAttacker.z * toAttacker.z);
        if (lookLength < 0.0001D || attackerLength < 0.0001D) {
            return false;
        }
        double dot = (look.x * toAttacker.x + look.z * toAttacker.z) / (lookLength * attackerLength);
        return dot <= REAR_ATTACK_DOT_THRESHOLD;
    }

    private static void emitCombatProc(ServerPlayer attacker, LivingEntity target, boolean combo) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(combo ? ParticleTypes.CRIT : ParticleTypes.SWEEP_ATTACK,
                target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
                combo ? 14 : 4, 0.3D, 0.35D, 0.3D, combo ? 0.12D : 0.02D);
        level.playSound(null, target.blockPosition(),
                combo ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, combo ? 0.75F : 0.6F, combo ? 1.15F : 0.9F);
    }

    private static void showHudProc(
            ServerPlayer player, String effectKey, ValueKind valueKind, double value
    ) {
        long tick = player.server.getTickCount();
        Map<String, Long> perEffect = LAST_HUD_PROC_TICKS.computeIfAbsent(
                player.getUUID(), ignored -> new HashMap<>());
        Long lastTick = perEffect.get(effectKey);
        if (lastTick != null && tick - lastTick < HUD_REPEAT_INTERVAL_TICKS) {
            return;
        }
        perEffect.put(effectKey, tick);
        RelicHudNotifier.proc(player, effectKey, valueKind, value);
    }

    public static void tryAirStep(ServerPlayer player, int sequence) {
        UUID playerId = player.getUUID();
        long tick = player.server.getTickCount();
        int lastSequence = LAST_AIR_STEP_SEQUENCE.getOrDefault(playerId, 0);
        if (sequence <= lastSequence || !acceptAirStepRequest(playerId, tick)) {
            return;
        }
        LAST_AIR_STEP_SEQUENCE.put(playerId, sequence);
        AirStepProfile profile = airStepProfile(player);
        if (profile == null || player.onGround() || player.isPassenger() || player.isFallFlying()
                || player.isInWaterOrBubble() || player.getAbilities().flying) {
            return;
        }
        int used = Math.max(0, player.getPersistentData().getInt(AIR_STEPS_USED_TAG));
        int cycleLimit = used == 0
                ? profile.maximumSteps()
                : Math.max(1, player.getPersistentData().getInt(AIR_STEP_LIMIT_TAG));
        int maximumSteps = Math.min(profile.maximumSteps(), cycleLimit);
        if (used >= maximumSteps) {
            return;
        }

        if (used == 0) {
            player.getPersistentData().putInt(AIR_STEP_LIMIT_TAG, profile.maximumSteps());
        }
        player.getPersistentData().putInt(AIR_STEPS_USED_TAG, used + 1);

        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(
                movement.x,
                Math.max(profile.minimumVelocityY(), movement.y + profile.addedVelocityY()),
                movement.z
        );
        player.resetFallDistance();
        player.hasImpulse = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1D, player.getZ(),
                    8, 0.28D, 0.04D, 0.28D, 0.015D);
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.15D, player.getZ(),
                    5, 0.22D, 0.05D, 0.22D, 0.02D);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.7F, 1.45F);
        RelicHudNotifier.progress(player,
                profile == HIGH_AIR_STEP ? "radiant_high_air_step_count" : "radiant_double_air_step_count",
                used + 1, maximumSteps, 30);
    }

    private static boolean acceptAirStepRequest(UUID playerId, long tick) {
        RequestWindow window = AIR_STEP_REQUEST_WINDOWS.get(playerId);
        if (window == null || tick - window.startedAtTick() >= AIR_STEP_REQUEST_WINDOW_TICKS) {
            AIR_STEP_REQUEST_WINDOWS.put(playerId, new RequestWindow(tick, 1));
            return true;
        }
        if (window.requests() >= MAX_AIR_STEP_REQUESTS_PER_WINDOW) {
            return false;
        }
        AIR_STEP_REQUEST_WINDOWS.put(playerId, new RequestWindow(window.startedAtTick(), window.requests() + 1));
        return true;
    }

    public static void onFoodUseStarted(ServerPlayer player, ItemStack foodStack) {
        FoodProperties food = foodStack.getFoodProperties(player);
        int hungerBeforeUse = player.getFoodData().getFoodLevel();
        if (food == null || food.nutrition() <= 0 || hungerBeforeUse >= 20) {
            FOOD_USE_STATES.remove(player.getUUID());
            return;
        }
        FOOD_USE_STATES.put(
                player.getUUID(),
                new FoodUseState(hungerBeforeUse, food.nutrition())
        );
    }

    public static void onFoodUseStopped(ServerPlayer player) {
        FOOD_USE_STATES.remove(player.getUUID());
    }

    public static void onFoodConsumed(ServerPlayer player) {
        FoodUseState state = FOOD_USE_STATES.remove(player.getUUID());
        if (!RelicManager.hasEquippedEffect(player, "satiation_absorption_conversion") || state == null) {
            return;
        }
        int overflowNutrition = Math.max(0, state.hungerBeforeUse() + state.nutrition() - 20);
        if (overflowNutrition <= 0) {
            return;
        }
        double percent = RelicManager.effectBonus(player, "satiation_absorption_conversion");
        float converted = (float) (overflowNutrition * percent / 100.0D);
        if (converted <= 0.0F) {
            return;
        }
        player.setAbsorptionAmount(Math.min(8.0F, player.getAbsorptionAmount() + converted));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.45F, 1.65F);
        RelicHudNotifier.proc(player, "satiation_absorption_conversion", ValueKind.ABSORPTION,
                converted);
    }

    private static void tickAirStep(ServerPlayer player) {
        if (player.onGround() && (player.getPersistentData().contains(AIR_STEPS_USED_TAG)
                || player.getPersistentData().contains(AIR_STEP_LIMIT_TAG))) {
            player.getPersistentData().remove(AIR_STEPS_USED_TAG);
            player.getPersistentData().remove(AIR_STEP_LIMIT_TAG);
        }
    }

    private static AirStepProfile airStepProfile(ServerPlayer player) {
        if (RelicManager.hasEquippedEffect(player, "radiant_high_air_step_count")) {
            int steps = (int) Math.round(RelicManager.effectBonus(player, "radiant_high_air_step_count"));
            return steps >= 1 ? HIGH_AIR_STEP : null;
        }
        if (RelicManager.hasEquippedEffect(player, "radiant_double_air_step_count")) {
            int steps = (int) Math.round(RelicManager.effectBonus(player, "radiant_double_air_step_count"));
            return steps >= 2 ? DOUBLE_AIR_STEP : null;
        }
        return null;
    }


    private static void tickFireCocoon(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!RelicManager.hasEquippedEffect(player, "fire_cocoon_duration")) {
            FIRE_COCOON_READY_TICK.remove(playerId);
            FIRE_COCOON_COOLDOWN_PENDING.remove(playerId);
            return;
        }
        boolean burning = player.isInLava() || player.isOnFire();
        long tick = player.server.getTickCount();
        long readyTick = FIRE_COCOON_READY_TICK.getOrDefault(playerId, 0L);
        if (!burning && readyTick > 0L && tick >= readyTick
                && FIRE_COCOON_COOLDOWN_PENDING.remove(playerId)) {
            RelicHudNotifier.ready(player, "fire_cocoon_duration");
            player.level().playSound(
                    null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.45F, 1.7F
            );
        }
        if (!burning) {
            return;
        }
        if (tick < readyTick) {
            return;
        }
        int duration = Math.max(20,
                (int) Math.round(RelicManager.effectBonus(player, "fire_cocoon_duration") * 20.0D));
        player.clearFire();
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, duration, 0, false, true));
        FIRE_COCOON_READY_TICK.put(playerId, tick + duration + 400L);
        FIRE_COCOON_COOLDOWN_PENDING.add(playerId);
        RelicHudNotifier.active(player, "fire_cocoon_duration", ValueKind.NONE, 0.0D, duration);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0D, player.getZ(),
                    18, 0.45D, 0.75D, 0.45D, 0.01D);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    private static void tickSurvivalAnklet(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Long expires = SURVIVAL_ANKLET_SPEED_EXPIRES.get(playerId);
        if (expires == null) {
            return;
        }
        if (!RelicManager.hasEquippedEffect(player, "low_health_escape_speed_bonus")
                || player.server.getTickCount() >= expires) {
            removeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SURVIVAL_ANKLET_SPEED_ID);
            SURVIVAL_ANKLET_SPEED_EXPIRES.remove(playerId);
        }
    }

    private static void tickVictoryDrum(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (RelicManager.effectBonus(player, "kill_attack_speed_bonus") <= 0.0D) {
            removeModifier(player.getAttribute(Attributes.ATTACK_SPEED), VICTORY_DRUM_ATTACK_SPEED_ID);
            VICTORY_DRUM_EXPIRES.remove(playerId);
            VICTORY_DRUM_READY_TICK.remove(playerId);
            return;
        }
        Long expires = VICTORY_DRUM_EXPIRES.get(playerId);
        if (expires != null && player.server.getTickCount() >= expires) {
            removeModifier(player.getAttribute(Attributes.ATTACK_SPEED), VICTORY_DRUM_ATTACK_SPEED_ID);
            VICTORY_DRUM_EXPIRES.remove(playerId);
        }
    }

    private static void tickGuardianHourglass(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (RelicManager.effectBonus(player, "out_of_combat_damage_absorption") <= 0.0D) {
            GUARDIAN_HOURGLASS_LAST_DAMAGE_TICK.remove(playerId);
            GUARDIAN_HOURGLASS_READY_TICK.remove(playerId);
            GUARDIAN_HOURGLASS_READY_NOTIFIED.remove(playerId);
            return;
        }
        long tick = player.server.getTickCount();
        long lastDamage = GUARDIAN_HOURGLASS_LAST_DAMAGE_TICK.computeIfAbsent(playerId, ignored -> tick);
        if (tick - lastDamage >= GUARDIAN_HOURGLASS_CHARGE_TICKS
                && tick >= GUARDIAN_HOURGLASS_READY_TICK.getOrDefault(playerId, Long.MIN_VALUE)
                && GUARDIAN_HOURGLASS_READY_NOTIFIED.add(playerId)) {
            RelicHudNotifier.ready(player, "out_of_combat_damage_absorption");
        }
    }

    private static void applyPlayerAttributes(ServerPlayer player) {
        double speedBonus = Math.max(0.0D, RelicManager.effectBonus(player, "player_movement_speed_bonus"));
        double maxHealthBonus = Math.max(0.0D, RelicManager.effectBonus(player, "player_max_health_bonus"));
        double blockInteractionRangeBonus = Math.max(
                0.0D,
                RelicManager.effectBonus(player, "block_interaction_range_bonus")
        );
        double criticalStrikeChanceBonus = Math.max(
                0.0D,
                RelicManager.effectBonus(player, "critical_strike_chance_bonus")
        );
        double criticalStrikeDamageBonus = Math.max(
                0.0D,
                RelicManager.effectBonus(player, "critical_strike_damage_bonus")
        );
        double knockbackResistanceBonus = Math.max(
                0.0D,
                RelicManager.effectBonus(player, "player_knockback_resistance_bonus")
        );
        double armorToughnessBonus = Math.max(
                0.0D,
                RelicManager.effectBonus(player, "player_armor_toughness_bonus")
        );
        double luckBonus = Math.max(0.0D, RelicManager.effectBonus(player, "player_luck_bonus"));
        PlayerAttributeProfile profile = new PlayerAttributeProfile(
                speedBonus,
                maxHealthBonus,
                blockInteractionRangeBonus,
                criticalStrikeChanceBonus,
                criticalStrikeDamageBonus,
                knockbackResistanceBonus,
                armorToughnessBonus,
                luckBonus
        );
        if (profile.equals(LAST_PLAYER_ATTRIBUTES.get(player.getUUID()))) {
            return;
        }
        LAST_PLAYER_ATTRIBUTES.put(player.getUUID(), profile);
        applyModifier(
                player.getAttribute(Attributes.MOVEMENT_SPEED),
                MOVEMENT_SPEED_ID,
                speedBonus / 100.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );

        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        applyModifier(maxHealth, MAX_HEALTH_ID, maxHealthBonus, AttributeModifier.Operation.ADD_VALUE);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }

        applyModifier(
                player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE),
                BLOCK_INTERACTION_RANGE_ID,
                blockInteractionRangeBonus,
                AttributeModifier.Operation.ADD_VALUE
        );
        applyModifier(
                optionalAttribute(player, CRITICAL_STRIKE_CHANCE_ATTRIBUTE_ID),
                CRITICAL_STRIKE_CHANCE_ID,
                criticalStrikeChanceBonus / 100.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );
        applyModifier(
                optionalAttribute(player, CRITICAL_STRIKE_DAMAGE_ATTRIBUTE_ID),
                CRITICAL_STRIKE_DAMAGE_ID,
                criticalStrikeDamageBonus / 100.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );
        applyModifier(
                player.getAttribute(Attributes.KNOCKBACK_RESISTANCE),
                KNOCKBACK_RESISTANCE_ID,
                knockbackResistanceBonus / 100.0D,
                AttributeModifier.Operation.ADD_VALUE
        );
        applyModifier(
                player.getAttribute(Attributes.ARMOR_TOUGHNESS),
                ARMOR_TOUGHNESS_ID,
                armorToughnessBonus,
                AttributeModifier.Operation.ADD_VALUE
        );
        applyModifier(
                player.getAttribute(Attributes.LUCK),
                LUCK_ID,
                luckBonus,
                AttributeModifier.Operation.ADD_VALUE
        );
    }

    private static void applyMountedPokemonSpeed(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        UUID playerId = player.getUUID();
        UUID previousMount = LAST_MOUNT.get(playerId);
        UUID currentMount = vehicle instanceof PokemonEntity ? vehicle.getUUID() : null;

        if (previousMount != null && !previousMount.equals(currentMount)) {
            restoreMount(player, previousMount);
            LAST_MOUNT_BONUS.remove(playerId);
        }
        if (currentMount == null) {
            LAST_MOUNT.remove(playerId);
            LAST_MOUNT_BONUS.remove(playerId);
            return;
        }
        LAST_MOUNT.put(playerId, currentMount);

        PokemonEntity pokemon = (PokemonEntity) vehicle;
        double bonusPercent = Math.max(0.0D, RelicManager.effectBonus(player, "mounted_pokemon_speed_bonus"));
        Double previousBonus = LAST_MOUNT_BONUS.get(playerId);
        if (currentMount.equals(previousMount) && previousBonus != null
                && Math.abs(previousBonus - bonusPercent) < 0.000001D) {
            return;
        }
        if (bonusPercent <= 0.0D) {
            restoreMount(player, currentMount);
            LAST_MOUNT_BONUS.put(playerId, 0.0D);
            return;
        }

        EnumMap<RidingStyle, Double> bases = RIDE_SPEED_BASES.computeIfAbsent(currentMount, ignored -> baseRideSpeeds(pokemon));
        for (Map.Entry<RidingStyle, Double> entry : bases.entrySet()) {
            double boosted = entry.getValue() * (1.0D + bonusPercent / 100.0D);
            pokemon.overrideRideStat$common(entry.getKey(), RidingStat.SPEED, boosted);
        }
        LAST_MOUNT_BONUS.put(playerId, bonusPercent);
    }

    private static EnumMap<RidingStyle, Double> baseRideSpeeds(PokemonEntity pokemon) {
        EnumMap<RidingStyle, Double> bases = new EnumMap<>(RidingStyle.class);
        for (RidingStyle style : RidingStyle.values()) {
            bases.put(style, pokemon.getRawRideStat(RidingStat.SPEED, style));
        }
        return bases;
    }

    private static void restoreLastMount(ServerPlayer player) {
        LAST_MOUNT_BONUS.remove(player.getUUID());
        UUID mountId = LAST_MOUNT.remove(player.getUUID());
        if (mountId != null) {
            restoreMount(player, mountId);
        }
    }

    private static void restoreMount(ServerPlayer player, UUID mountId) {
        EnumMap<RidingStyle, Double> bases = RIDE_SPEED_BASES.remove(mountId);
        if (bases == null) {
            return;
        }
        Entity entity = findEntity(player, mountId);
        if (!(entity instanceof PokemonEntity pokemon)) {
            return;
        }
        for (Map.Entry<RidingStyle, Double> entry : bases.entrySet()) {
            pokemon.overrideRideStat$common(entry.getKey(), RidingStat.SPEED, entry.getValue());
        }
    }

    private static Entity findEntity(ServerPlayer player, UUID entityId) {
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void applyModifier(AttributeInstance attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        if (amount <= 0.0D) {
            removeModifier(attribute, id);
            return;
        }
        AttributeModifier existing = attribute.getModifier(id);
        if (existing != null && existing.operation() == operation
                && Math.abs(existing.amount() - amount) < 0.000001D) {
            return;
        }
        removeModifier(attribute, id);
        attribute.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    private static AttributeInstance optionalAttribute(ServerPlayer player, ResourceLocation attributeId) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(attributeId)
                .map(player::getAttribute)
                .orElse(null);
    }

    private static void removeModifier(AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null && attribute.hasModifier(id)) {
            attribute.removeModifier(id);
        }
    }

    private record RequestWindow(long startedAtTick, int requests) {
    }

    private record AirStepProfile(int maximumSteps, double minimumVelocityY, double addedVelocityY) {
    }

    private record FoodUseState(int hungerBeforeUse, int nutrition) {
    }

    private record ComboState(UUID targetId, int hitCount, long lastHitTick) {
    }

    private record TimedTarget(UUID targetId, long expiresAtTick) {
    }

    private record PlayerAttributeProfile(
            double movementSpeedBonus,
            double maxHealthBonus,
            double blockInteractionRangeBonus,
            double criticalStrikeChanceBonus,
            double criticalStrikeDamageBonus,
            double knockbackResistanceBonus,
            double armorToughnessBonus,
            double luckBonus
    ) {
    }
}
