package com.yoiko.core.cosmetic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.joml.Vector3f;

public final class ParticleTrailManager {
    private static final Map<EffectKey, ParticleState> STATES = new HashMap<>();
    private static final Map<String, SimpleParticleType> PARTICLE_CACHE = new HashMap<>();
    private static final double MOVEMENT_EPSILON_SQR = 0.0004D;
    private static final int TRAIL_BURST_COOLDOWN_TICKS = 8;
    private static final double STANDING_PLAYER_HEIGHT = 1.8D;
    private static final double HORIZONTAL_HEAD_FORWARD = 0.42D;
    private static final int METEOR_MAX_ACTIVE = 2;
    private static final int METEOR_MIN_SPAWN_INTERVAL_TICKS = 7;
    private static final int METEOR_SPAWN_INTERVAL_VARIANCE_TICKS = 10;
    private static final int METEOR_FLIGHT_TICKS = 10;
    private static final int METEOR_AMBIENT_STAR_INTERVAL_TICKS = 4;
    private static final int METEOR_AMBIENT_STARS_PER_PULSE = 2;
    private static final int COMPANION_SPHERE_POINTS = 12;
    private static final double COMPANION_SPHERE_RADIUS = 0.115D;
    private static final double CLOUD_COMPANION_SPHERE_RADIUS = 0.145D;
    private static final double COMPANION_LATERAL_DISTANCE = 0.68D;
    private static final double COMPANION_REAR_OFFSET = 0.06D;
    private static final double COMPANION_MAX_REAR_DISTANCE = 0.18D;
    private static final int YOIKO_MELODY_GRID_TICKS = 2;
    private static final int YOIKO_MELODY_STEPS_PER_BAR = 16;
    private static final int[] YOIKO_BASS_ROOTS = {0, -5, -7, -5, 0, -5, -7, -5};
    private static final int[] YOIKO_BASS_FIFTHS = {7, 2, 0, 2, 7, 2, 0, 2};
    private static final int[] YOIKO_CHIME_STEPS = {0, 32, 64, 96};
    private static final int[] YOIKO_CHIME_SEMITONES = {12, 5, 12, 5};
    /** Eight-bar, 150 BPM D-major chiptune. Durations are measured in sixteenth-note grid steps. */
    private static final MelodyEvent[] YOIKO_MELODY_EVENTS = {
            // Bar 1
            melody(0, 2), melody(4, 2), melody(7, 2), melody(12, 2),
            melody(11, 1), melody(9, 1), melody(7, 2), melody(4, 2), melody(7, 2),
            // Bar 2
            melody(2, 2), melody(7, 2), melody(11, 2), melody(14, 2),
            melody(12, 1), melody(11, 1), melody(9, 2), melody(7, 2), melody(2, 2),
            // Bar 3
            melody(5, 2), melody(9, 2), melody(12, 2), melody(16, 2),
            melody(14, 1), melody(12, 1), melody(9, 2), melody(7, 2), melody(9, 2),
            // Bar 4
            melody(7, 2), melody(11, 2), melody(14, 2), melody(16, 2),
            melody(14, 1), melody(11, 1), melody(9, 2), melody(7, 2), melody(2, 2),
            // Bar 5
            melody(0, 1), melody(2, 1), melody(4, 2), melody(7, 2), melody(12, 2),
            melody(11, 1), melody(12, 1), melody(14, 2), melody(16, 2), melody(7, 2),
            // Bar 6
            melody(2, 1), melody(4, 1), melody(7, 2), melody(11, 2), melody(14, 2),
            melody(12, 1), melody(11, 1), melody(9, 2), melody(11, 2), melody(14, 2),
            // Bar 7
            melody(5, 2), melody(7, 2), melody(9, 2), melody(12, 2),
            melody(14, 1), melody(16, 1), melody(14, 2), melody(12, 2), melody(9, 2),
            // Bar 8
            melody(7, 2), melody(11, 2), melody(14, 1), melody(16, 1),
            melody(14, 2), melody(11, 2), melody(9, 2), melody(7, 2), melody(2, 2)
    };
    private static final MelodyStep[] YOIKO_MELODY = buildYoikoMelody();
    private static final Map<String, TrailEffect> TRAIL_EFFECTS = Map.ofEntries(
            Map.entry("stardust_steps_trail", (level, player, cosmetic, state) -> spawnDirectionalTrail(level, player,
                    player.tickCount % 7 == 0 ? ParticleTypes.FIREWORK
                            : player.tickCount % 3 == 0 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.END_ROD, 0.026D)),
            Map.entry("ember_steps_trail", (level, player, cosmetic, state) -> spawnDirectionalTrail(level, player,
                    player.tickCount % 12 == 0 ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME, 0.028D)),
            Map.entry("frost_trace_trail", (level, player, cosmetic, state) -> spawnDirectionalTrail(level, player,
                    player.tickCount % 7 == 0 ? particle("minecraft:item_snowball")
                            : player.tickCount % 3 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.SNOWFLAKE, 0.018D)),
            Map.entry("wind_trace_trail", (level, player, cosmetic, state) ->
                    spawnDirectionalTrail(level, player, particle("minecraft:small_gust"), 0.018D)),
            Map.entry("sandstorm_trail", (level, player, cosmetic, state) -> spawnDirectionalTrail(level, player,
                    player.tickCount % 3 == 0
                            ? new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState())
                            : particle("minecraft:dust_plume"), player.isSprinting() ? 0.045D : 0.022D)),
            Map.entry("aqua_drops_trail", (level, player, cosmetic, state) -> spawnBubbleTrail(level, player)),
            Map.entry("golden_afterglow_trail", (level, player, cosmetic, state) -> spawnGoldenTrail(level, player, cosmetic)),
            Map.entry("arcana_trail", (level, player, cosmetic, state) -> spawnRuneTrail(level, player, cosmetic, true)),
            Map.entry("rainbow_spark_trail", (level, player, cosmetic, state) -> spawnRainbowTrail(level, player, cosmetic)),
            Map.entry("melody_trail", ParticleTrailManager::spawnNoteTrail),
            Map.entry("venom_mist_trail", (level, player, cosmetic, state) ->
                    spawnDirectionalTrail(level, player, ParticleTypes.WITCH, 0.035D)),
            Map.entry("emerald_glow_trail", (level, player, cosmetic, state) ->
                    spawnDirectionalTrail(level, player, ParticleTypes.HAPPY_VILLAGER, 0.038D)),
            Map.entry("soulflame_trail", (level, player, cosmetic, state) ->
                    spawnDirectionalTrail(level, player, ParticleTypes.SOUL_FIRE_FLAME, 0.033D)),
            Map.entry("sakura_shower_trail", (level, player, cosmetic, state) -> spawnCherryTrail(level, player, cosmetic)),
            Map.entry("black_haze_trail", (level, player, cosmetic, state) ->
                    spawnDirectionalTrail(level, player, ParticleTypes.SMOKE, 0.026D))
    );
    private static final Map<String, ParticleEffect> RING_EFFECTS = Map.ofEntries(
            Map.entry("thorn_crown_ring", ParticleTrailManager::spawnDesignedHalo),
            Map.entry("crystal_diadem_ring", ParticleTrailManager::spawnDesignedHalo),
            Map.entry("ember_crown_ring", ParticleTrailManager::spawnDesignedHalo),
            Map.entry("deep_crown_ring", ParticleTrailManager::spawnDesignedHalo),
            Map.entry("zephyr_ring", ParticleTrailManager::spawnBreezeSpiralRing),
            Map.entry("rain_cloud_ring", ParticleTrailManager::spawnRainCloudAura),
            Map.entry("star_crown_ring", ParticleTrailManager::spawnStarCrown),
            Map.entry("crescent_moon_ring", ParticleTrailManager::spawnMoonRing),
            Map.entry("flower_crown_ring", ParticleTrailManager::spawnFlowerCrownRing),
            Map.entry("angel_halo_ring", ParticleTrailManager::spawnGenericHalo),
            Map.entry("soulfire_halo_ring", ParticleTrailManager::spawnGenericHalo),
            Map.entry("portal_ring", ParticleTrailManager::spawnGenericHalo)
    );
    private static final Map<String, ParticleEffect> ORBIT_EFFECTS = Map.ofEntries(
            Map.entry("aurora_veil_aura", ParticleTrailManager::spawnAuroraWallOrbit),
            Map.entry("twin_moons_orbit", ParticleTrailManager::spawnDesignedOrbit),
            Map.entry("crystal_satellites_orbit", ParticleTrailManager::spawnDesignedOrbit),
            Map.entry("void_satellite_orbit", ParticleTrailManager::spawnDesignedOrbit),
            Map.entry("firefly_swarm_orbit", ParticleTrailManager::spawnDesignedOrbit),
            Map.entry("elemental_trinity_orbit", ParticleTrailManager::spawnElementOrbit),
            Map.entry("arcane_runes_orbit", ParticleTrailManager::spawnMagicRuneOrbit),
            Map.entry("wandering_souls_orbit", ParticleTrailManager::spawnGenericOrbit),
            Map.entry("end_echo_orbit", ParticleTrailManager::spawnGenericOrbit),
            Map.entry("emerald_arc_orbit", ParticleTrailManager::spawnGenericOrbit)
    );
    private static final Map<String, ParticleEffect> AURA_EFFECTS = Map.ofEntries(
            Map.entry("sculk_pulse_aura", ParticleTrailManager::spawnSculkHeartbeatAura),
            Map.entry("arcane_pulse_aura", ParticleTrailManager::spawnDesignedAura),
            Map.entry("snow_blossom_aura", ParticleTrailManager::spawnDesignedAura),
            Map.entry("ominous_rite_aura", ParticleTrailManager::spawnDesignedAura),
            Map.entry("runic_glow_aura", (level, player, cosmetic) -> spawnEnchantAura(level, player)),
            Map.entry("supporter_light_aura", ParticleTrailManager::spawnSupporterAura),
            Map.entry("heartlight_aura", ParticleTrailManager::spawnHeartAura),
            Map.entry("starfield_aura", ParticleTrailManager::spawnCosmicAura),
            Map.entry("champion_bronze_glory_aura", ParticleTrailManager::spawnChampionFlare),
            Map.entry("champion_silver_glory_aura", ParticleTrailManager::spawnChampionFlare),
            Map.entry("champion_gold_glory_aura", ParticleTrailManager::spawnChampionFlare),
            Map.entry("champion_platinum_glory_aura", ParticleTrailManager::spawnChampionFlare),
            Map.entry("champion_diamond_glory_aura", ParticleTrailManager::spawnChampionFlare),
            Map.entry("soft_sparkle_aura", ParticleTrailManager::spawnGenericAura),
            Map.entry("rose_glow_aura", ParticleTrailManager::spawnGenericAura),
            Map.entry("pixel_glitch_aura", ParticleTrailManager::spawnGenericAura),
            Map.entry("dimensional_rift_aura", ParticleTrailManager::spawnGenericAura)
    );
    private static final Set<String> COMPANION_EFFECTS = Set.of(
            "azure_wisp_companion", "fairy_light_companion", "fire_wisp_companion",
            "aqua_wisp_companion", "forest_wisp_companion", "cloud_wisp_companion",
            "void_eye_companion", "trial_flame_companion", "firefly_wisp_companion"
    );

    @FunctionalInterface
    private interface ParticleEffect {
        void spawn(Level level, Player player, CosmeticData cosmetic);
    }

    @FunctionalInterface
    private interface TrailEffect {
        void spawn(Level level, Player player, CosmeticData cosmetic, ParticleState state);
    }

    private ParticleTrailManager() {
    }

    public static void clear() {
        STATES.clear();
    }

    public static void retainStates(Set<UUID> playerUuids) {
        STATES.keySet().removeIf(key -> !playerUuids.contains(key.playerUuid()));
    }

    public static void tick(Player player, CosmeticData cosmetic, int density, boolean reduceAnimations) {
        if (!player.level().isClientSide || density <= 0) {
            return;
        }
        Level level = player.level();
        if (cosmetic == null || cosmetic.type() != CosmeticType.PARTICLE) {
            removePlayerStates(player.getUUID());
            return;
        }
        // A client render layer transforms body-local wing points and emits vanilla world particles.
        if (cosmetic.particleCategory() == ParticleCategory.WINGS) {
            STATES.remove(new EffectKey(player.getUUID(), cosmetic.id()));
            return;
        }
        EffectKey effectKey = new EffectKey(player.getUUID(), cosmetic.id());
        ParticleState state = STATES.computeIfAbsent(effectKey, ignored -> new ParticleState(player));
        ParticleCategory shape = cosmetic.particleCategory();
        boolean moving = isMoving(player, state);
        boolean sneaking = player.isShiftKeyDown();

        if (shape == ParticleCategory.COMPANION) {
            state.updateCompanion(player, cosmetic);
        }

        if (shape == ParticleCategory.TRAIL && player.isFallFlying()) {
            AdaptiveFlightTrailEffects.tick(level, player, cosmetic, density, reduceAnimations);
            state.update(player, moving);
            return;
        }

        if (shape == ParticleCategory.TRAIL && !sneaking) {
            boolean jumpStart = state.wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0.05D;
            boolean dashStart = moving && !state.wasMoving && player.isSprinting();
            if (jumpStart && player.tickCount - state.lastJumpBurstTick >= TRAIL_BURST_COOLDOWN_TICKS) {
                spawnTrailBurst(level, player, cosmetic, true);
                state.lastJumpBurstTick = player.tickCount;
            } else if (dashStart && player.tickCount - state.lastDashBurstTick >= TRAIL_BURST_COOLDOWN_TICKS) {
                spawnTrailBurst(level, player, cosmetic, false);
                state.lastDashBurstTick = player.tickCount;
            }
        }

        int interval = effectiveInterval(cosmetic, shape);
        if (density == 1) {
            interval *= 2;
        }
        if (reduceAnimations) {
            interval *= 2;
        }
        if (player.tickCount % interval == 0) {
            switch (shape) {
                case RING -> spawnHalo(level, player, cosmetic);
                case ORBIT -> {
                    if (!moving) {
                        spawnOrbit(level, player, cosmetic);
                    }
                }
                case COMPANION -> spawnCompanion(level, player, cosmetic, state);
                case AURA -> {
                    if (!moving) {
                        spawnAura(level, player, cosmetic);
                    }
                }
                case TRAIL -> {
                    if (cosmetic.id().equals("meteor_shower_trail")) {
                        tickMeteorShowerTrail(level, player, state, moving && !sneaking);
                    } else if (moving && !sneaking) {
                        spawnTrail(level, player, cosmetic, state);
                    }
                }
                case NONE, WINGS -> {
                }
            }
        }
        state.update(player, moving);
    }

    private static void removePlayerStates(UUID playerUuid) {
        STATES.keySet().removeIf(key -> key.playerUuid().equals(playerUuid));
    }

    private static int effectiveInterval(CosmeticData cosmetic, ParticleCategory shape) {
        int interval = Math.max(1, cosmetic.intervalTicks());
        if (shape == ParticleCategory.TRAIL) {
            // A single particle every tick reads as a continuous trail without dense clumps.
            return 1;
        }
        return interval;
    }

    private static boolean isMoving(Player player, ParticleState state) {
        double dx = player.getX() - state.lastX;
        double dz = player.getZ() - state.lastZ;
        double positionDeltaSqr = dx * dx + dz * dz;
        double velocityDeltaSqr = player.getDeltaMovement().x * player.getDeltaMovement().x
                + player.getDeltaMovement().z * player.getDeltaMovement().z;
        return positionDeltaSqr > MOVEMENT_EPSILON_SQR || velocityDeltaSqr > MOVEMENT_EPSILON_SQR;
    }

    private static void spawnTrail(Level level, Player player, CosmeticData cosmetic, ParticleState state) {
        if (NewParticleCosmeticEffects.spawnTrail(level, player, cosmetic)) {
            return;
        }
        TrailEffect effect = TRAIL_EFFECTS.get(cosmetic.id());
        if (effect != null) {
            effect.spawn(level, player, cosmetic, state);
            return;
        }
        // Custom data entries without authored geometry still render their declared vanilla particle.
        ParticlePacketDispatcher.send(level,
                particle(cosmetic.particle()),
                player.getX(),
                player.getY() + cosmetic.offsetY(),
                player.getZ(),
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );
    }

    private static void tickMeteorShowerTrail(Level level, Player player, ParticleState state,
                                              boolean active) {
        if (!active) {
            state.meteors.clear();
            state.nextMeteorTick = player.tickCount;
            return;
        }

        if (player.tickCount >= state.nextMeteorTick && state.meteors.size() < METEOR_MAX_ACTIVE) {
            launchMeteor(player, level, state);
            state.nextMeteorTick = player.tickCount + METEOR_MIN_SPAWN_INTERVAL_TICKS
                    + player.getRandom().nextInt(METEOR_SPAWN_INTERVAL_VARIANCE_TICKS);
        }

        if (player.tickCount % METEOR_AMBIENT_STAR_INTERVAL_TICKS == 0) {
            for (int star = 0; star < METEOR_AMBIENT_STARS_PER_PULSE; star++) {
                spawnMeteorAmbientStar(level, player);
            }
        }

        Iterator<FallingMeteor> iterator = state.meteors.iterator();
        while (iterator.hasNext()) {
            FallingMeteor meteor = iterator.next();
            meteor.age++;
            double progress = Math.clamp((double) meteor.age / meteor.duration, 0.0D, 1.0D);
            double easedProgress = progress * progress * (3.0D - 2.0D * progress);
            Vec3 head = meteor.start.lerp(meteor.impact, easedProgress);
            spawnMeteorBody(level, meteor, head);
            if ((meteor.age + player.tickCount) % 3 == 0) {
                spawnMeteorFlightStar(level, meteor, head);
            }

            if (meteor.age >= meteor.duration) {
                spawnMeteorImpact(level, meteor.impact);
                iterator.remove();
            }
        }
    }

    private static void launchMeteor(Player player, Level level, ParticleState state) {
        double moveX = player.getX() - state.lastX;
        double moveZ = player.getZ() - state.lastZ;
        double movementLength = Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (movementLength <= 1.0E-4D) {
            moveX = player.getDeltaMovement().x;
            moveZ = player.getDeltaMovement().z;
            movementLength = Math.sqrt(moveX * moveX + moveZ * moveZ);
        }
        if (movementLength <= 1.0E-4D) {
            double yaw = Math.toRadians(player.yBodyRot);
            moveX = -Math.sin(yaw);
            moveZ = Math.cos(yaw);
            movementLength = 1.0D;
        }

        double forwardX = moveX / movementLength;
        double forwardZ = moveZ / movementLength;
        double rightX = -forwardZ;
        double rightZ = forwardX;
        // Pick each side independently instead of alternating so the shower does not read as a metronome.
        double sideSign = player.getRandom().nextBoolean() ? 1.0D : -1.0D;
        double impactForwardDistance = 2.15D + player.getRandom().nextDouble() * 1.85D;
        double impactSideDistance = sideSign * (0.45D + player.getRandom().nextDouble() * 1.15D);
        double impactX = player.getX() + forwardX * impactForwardDistance + rightX * impactSideDistance;
        double impactZ = player.getZ() + forwardZ * impactForwardDistance + rightZ * impactSideDistance;
        double impactY = findMeteorGroundY(level, impactX, player.getY(), impactZ);
        Vec3 impact = new Vec3(impactX, impactY, impactZ);
        double approachDistance = 1.35D + player.getRandom().nextDouble() * 0.75D;
        double approachSideDrift = (player.getRandom().nextDouble() - 0.5D) * 0.70D;
        Vec3 start = new Vec3(
                impactX - forwardX * approachDistance + rightX * approachSideDrift,
                impactY + 1.85D + player.getRandom().nextDouble() * 0.75D,
                impactZ - forwardZ * approachDistance + rightZ * approachSideDrift
        );
        int duration = METEOR_FLIGHT_TICKS + player.getRandom().nextInt(5);
        state.meteors.add(new FallingMeteor(start, impact, duration));
    }

    private static double findMeteorGroundY(Level level, double x, double referenceY, double z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int topY = (int) Math.floor(referenceY + 1.0D);
        int bottomY = topY - 8;
        for (int y = topY; y >= bottomY; y--) {
            cursor.set(blockX, y, blockZ);
            var shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                return y + shape.max(net.minecraft.core.Direction.Axis.Y) + 0.035D;
            }
        }
        return referenceY + 0.035D;
    }

    private static void spawnMeteorBody(Level level, FallingMeteor meteor, Vec3 head) {
        Vec3 direction = meteor.impact.subtract(meteor.start).normalize();
        Vec3 side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        Vec3 normal = side.cross(direction).normalize();
        double fallProgress = Math.clamp((double) meteor.age / meteor.duration, 0.0D, 1.0D);
        double smoothProgress = fallProgress * fallProgress * (3.0D - 2.0D * fallProgress);
        double thickness = 0.72D + smoothProgress * 0.50D;

        // Three axial white points make a compact teardrop head instead of leaving a warm-colored center.
        Vec3 coreVelocity = direction.scale(0.13D);
        spawnMeteorParticleMoving(level, dust(0xFFFFFF, (float) (1.02D * thickness)),
                head.add(direction.scale(0.040D * thickness)), coreVelocity);
        spawnMeteorParticleMoving(level, dust(0xEAFBFF, (float) (0.86D * thickness)), head, coreVelocity);
        spawnMeteorParticleMoving(level, dust(0xC9F4FF, (float) (0.68D * thickness)),
                head.subtract(direction.scale(0.060D * thickness)), coreVelocity);

        // A fixed four-point cyan shell preserves a clean, readable head without mixing rotating colors.
        int[] shellColors = {0xF4FEFF, 0xBFEFFF, 0x83D9FF, 0xD9F8FF};
        Vec3 shellCenter = head.subtract(direction.scale(0.025D));
        for (int i = 0; i < shellColors.length; i++) {
            double angle = Math.PI * 2.0D * i / shellColors.length;
            Vec3 shell = shellCenter
                    .add(side.scale(Math.cos(angle) * 0.072D * thickness))
                    .add(normal.scale(Math.sin(angle) * 0.052D * thickness));
            spawnMeteorParticleMoving(level, dust(shellColors[i], (float) (0.52D * thickness)),
                    shell, coreVelocity);
        }

        // Sample a straight tapered tail. Alternating samples halve each tick's density while persistent
        // particles join into one continuous blue streak.
        double travelled = meteor.start.distanceTo(head);
        int tailPoints = Math.min(8, 1 + (int) Math.floor(travelled / 0.10D));
        int parity = Math.floorMod(meteor.age, 2);
        int[] tailColors = {0xE9FCFF, 0xBDEFFF, 0x8BDFFF, 0x5EC8FF,
                0x389FFF, 0x2679E8, 0x1C58BC, 0x173F8F};
        for (int i = parity; i < tailPoints; i += 2) {
            double taper = tailPoints <= 1 ? 0.0D : (double) i / (tailPoints - 1);
            double distance = 0.13D + i * 0.105D;
            double width = 0.050D * thickness * (1.0D - taper);
            double sideOffset = ((i & 2) == 0 ? -1.0D : 1.0D) * width;
            Vec3 tail = head.subtract(direction.scale(distance)).add(side.scale(sideOffset));
            float scale = (float) (Math.max(0.28F, 0.58F - i * 0.040F) * thickness);
            spawnMeteorParticle(level, dust(tailColors[i], scale), tail);
        }
    }

    private static void spawnMeteorParticle(Level level, ParticleOptions particle, Vec3 position) {
        ParticlePacketDispatcher.send(level, particle, position.x, position.y, position.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void spawnMeteorParticleMoving(Level level, ParticleOptions particle, Vec3 position,
                                                   Vec3 velocity) {
        sendMovingParticle(level, particle, position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z);
    }

    private static void spawnMeteorAmbientStar(Level level, Player player) {
        double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 0.95D + player.getRandom().nextDouble() * 1.20D;
        Vec3 center = new Vec3(
                player.getX() + Math.cos(angle) * radius,
                player.getY() + 0.55D + player.getRandom().nextDouble() * 1.70D,
                player.getZ() + Math.sin(angle) * radius
        );
        Vec3 tangent = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
        int[] colors = {0xFFFFFF, 0xDDF8FF, 0xA9E4FF};
        double armLength = 0.065D + player.getRandom().nextDouble() * 0.035D;

        spawnMeteorParticle(level, dust(colors[0], 0.58F), center);
        for (int direction : new int[] {-1, 1}) {
            spawnMeteorParticle(level, dust(colors[1], 0.42F),
                    center.add(tangent.scale(direction * armLength * 0.55D)));
            spawnMeteorParticle(level, dust(colors[2], 0.32F),
                    center.add(tangent.scale(direction * armLength)));
            spawnMeteorParticle(level, dust(colors[1], 0.42F),
                    center.add(0.0D, direction * armLength * 0.55D, 0.0D));
            spawnMeteorParticle(level, dust(colors[2], 0.32F),
                    center.add(0.0D, direction * armLength, 0.0D));
        }
    }

    private static void spawnMeteorFlightStar(Level level, FallingMeteor meteor, Vec3 head) {
        Vec3 direction = meteor.impact.subtract(meteor.start).normalize();
        Vec3 side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        Vec3 normal = side.cross(direction).normalize();
        double sign = (meteor.age & 1) == 0 ? -1.0D : 1.0D;
        Vec3 center = head.subtract(direction.scale(0.34D + (meteor.age % 3) * 0.08D))
                .add(side.scale(sign * 0.12D))
                .add(normal.scale(0.04D));
        double arm = 0.055D;
        spawnMeteorParticle(level, dust(0xFFFFFF, 0.46F), center);
        spawnMeteorParticle(level, dust(0xC9F4FF, 0.34F), center.add(side.scale(arm)));
        spawnMeteorParticle(level, dust(0xC9F4FF, 0.34F), center.add(side.scale(-arm)));
        spawnMeteorParticle(level, dust(0x83D9FF, 0.30F), center.add(normal.scale(arm)));
        spawnMeteorParticle(level, dust(0x83D9FF, 0.30F), center.add(normal.scale(-arm)));
    }

    private static void spawnMeteorImpact(Level level, Vec3 impact) {
        ParticlePacketDispatcher.send(level, ParticleTypes.POOF, impact.x, impact.y + 0.03D, impact.z,
                3, 0.08D, 0.020D, 0.08D, 0.012D);
        ParticlePacketDispatcher.send(level, ParticleTypes.FIREWORK, impact.x, impact.y + 0.05D, impact.z,
                2, 0.055D, 0.025D, 0.055D, 0.010D);
        int[] impactColors = {0xF7FEFF, 0xC9F4FF, 0x82D9FF, 0x3F9FFF, 0x276ED3};
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI * 2.0D * i / 10.0D;
            sendMovingParticle(level, dust(impactColors[i % impactColors.length], 0.52F),
                    impact.x, impact.y + 0.045D, impact.z,
                    Math.cos(angle) * 0.050D, 0.020D, Math.sin(angle) * 0.050D);
        }
    }

    private static void spawnGoldenTrail(Level level, Player player, CosmeticData cosmetic) {
        int[] colors = {0xFFB719, 0xFFD84A, 0xFFF2A1};
        int color = colors[Math.floorMod(player.tickCount, colors.length)];
        spawnDirectionalTrail(level, player, dust(color, 0.72F), 0.032D);
    }

    private static void spawnBubbleTrail(Level level, Player player) {
        double dx = player.getDeltaMovement().x;
        double dz = player.getDeltaMovement().z;
        double length = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        double backX = -dx / length;
        double backZ = -dz / length;
        double side = Math.sin(player.tickCount * 1.1D) * 0.10D;
        sendMovingParticle(
                level,
                ParticleTypes.SPLASH,
                player.getX() + backX * 0.24D - backZ * side,
                player.getY() + 0.10D + Math.floorMod(player.tickCount, 3) * 0.035D,
                player.getZ() + backZ * 0.24D + backX * side,
                backX * 0.045D - backZ * side * 0.06D,
                0.018D + Math.floorMod(player.tickCount, 2) * 0.010D,
                backZ * 0.045D + backX * side * 0.06D
        );
    }

    private static void spawnRuneTrail(Level level, Player player, CosmeticData cosmetic, boolean mythic) {
        spawnDirectionalTrail(level, player, mythic ? ParticleTypes.WITCH : ParticleTypes.ENCHANT,
                mythic ? 0.032D : 0.030D);
    }

    private static void spawnNoteTrail(Level level, Player player, CosmeticData cosmetic, ParticleState state) {
        if (player.tickCount >= state.nextMelodySoundTick) {
            MelodyStep melodyStep = YOIKO_MELODY[state.melodyStep];
            if (melodyStep.melodyAttack()) {
                state.melodySemitone = melodyStep.semitones();
                playMelodySound(level, player, MelodyInstrument.BIT, melodyStep.semitones(), 1.0F);
            }
            for (int chime = 0; chime < YOIKO_CHIME_STEPS.length; chime++) {
                if (state.melodyStep == YOIKO_CHIME_STEPS[chime]) {
                    playMelodySound(level, player, MelodyInstrument.CHIME,
                            YOIKO_CHIME_SEMITONES[chime], 1.0F);
                    break;
                }
            }

            int bar = state.melodyStep / YOIKO_MELODY_STEPS_PER_BAR;
            int stepInBar = state.melodyStep % YOIKO_MELODY_STEPS_PER_BAR;
            if (stepInBar % 4 == 0) {
                int beat = stepInBar / 4;
                int bassSemitone = (beat & 1) == 0 ? YOIKO_BASS_ROOTS[bar] : YOIKO_BASS_FIFTHS[bar];
                playMelodySound(level, player, MelodyInstrument.BASS, bassSemitone, 1.0F);
            }
            if (stepInBar == 0 || stepInBar == 8) {
                playMelodySound(level, player, MelodyInstrument.BASEDRUM, 0, 1.0F);
            } else if (stepInBar == 4 || stepInBar == 12) {
                playMelodySound(level, player, MelodyInstrument.SNARE, 0, 1.0F);
            }
            if ((stepInBar & 1) == 0) {
                int hatSemitone = stepInBar % 4 == 0 ? 2 : 0;
                playMelodySound(level, player, MelodyInstrument.HAT, hatSemitone, 1.0F);
            }

            state.nextMelodySoundTick = player.tickCount + YOIKO_MELODY_GRID_TICKS;
            state.melodyStep = (state.melodyStep + 1) % YOIKO_MELODY.length;
        }

        double dx = player.getDeltaMovement().x;
        double dz = player.getDeltaMovement().z;
        double length = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        double color = Math.floorMod(state.melodySemitone * 2, 24) / 24.0D;
        ParticlePacketDispatcher.send(level, ParticleTypes.NOTE,
                player.getX() - dx / length * 0.24D,
                player.getY() + 0.18D + Math.sin(player.tickCount * 0.45D) * 0.035D,
                player.getZ() - dz / length * 0.24D,
                0, color, 0.0D, 0.0D, 1.0D);
    }

    private static MelodyEvent melody(int semitones, int durationSteps) {
        return new MelodyEvent(semitones, durationSteps);
    }

    private static MelodyStep[] buildYoikoMelody() {
        List<MelodyStep> steps = new ArrayList<>(8 * YOIKO_MELODY_STEPS_PER_BAR);
        for (MelodyEvent event : YOIKO_MELODY_EVENTS) {
            steps.add(new MelodyStep(event.semitones(), true));
            for (int sustain = 1; sustain < event.durationSteps(); sustain++) {
                steps.add(new MelodyStep(event.semitones(), false));
            }
        }
        int expectedSteps = 8 * YOIKO_MELODY_STEPS_PER_BAR;
        if (steps.size() != expectedSteps) {
            throw new IllegalStateException("YOIKO_MELODY must contain exactly " + expectedSteps
                    + " sixteenth-note steps, found " + steps.size());
        }
        return steps.toArray(MelodyStep[]::new);
    }

    private static void playMelodySound(Level level, Player player,
                                        MelodyInstrument instrument, int semitones, float volume) {
        level.playLocalSound(player.getX(), player.getY(), player.getZ(), melodySound(instrument),
                SoundSource.PLAYERS, volume, melodyPitch(semitones), false);
    }

    private static SoundEvent melodySound(MelodyInstrument instrument) {
        return switch (instrument) {
            case BIT -> SoundEvents.NOTE_BLOCK_BIT.value();
            case CHIME -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case BASS -> SoundEvents.NOTE_BLOCK_BASS.value();
            case BASEDRUM -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
            case SNARE -> SoundEvents.NOTE_BLOCK_SNARE.value();
            case HAT -> SoundEvents.NOTE_BLOCK_HAT.value();
        };
    }

    private static float melodyPitch(int semitones) {
        return (float) (0.75D * Math.pow(2.0D, semitones / 12.0D));
    }

    private static void spawnCherryTrail(Level level, Player player, CosmeticData cosmetic) {
        spawnDirectionalTrail(level, player, ParticleTypes.CHERRY_LEAVES, 0.026D);
    }

    private static void spawnDirectionalTrail(Level level, Player player, ParticleOptions particle, double speed) {
        double dx = player.getDeltaMovement().x;
        double dz = player.getDeltaMovement().z;
        double length = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        double backX = -dx / length;
        double backZ = -dz / length;
        double rightX = -backZ;
        double rightZ = backX;
        double phase = player.tickCount * 0.72D;
        double longitudinal = 0.17D + (Math.sin(phase * 0.53D) + 1.0D) * 0.09D;
        double side = Math.sin(phase) * 0.07D;
        sendMovingParticle(
                level,
                particle,
                player.getX() + backX * longitudinal + rightX * side,
                player.getY() + 0.10D + Math.sin(phase * 0.61D) * 0.025D,
                player.getZ() + backZ * longitudinal + rightZ * side,
                backX * speed,
                0.012D,
                backZ * speed
        );
    }

    private static void spawnRainbowTrail(Level level, Player player, CosmeticData cosmetic) {
        int[] colors = {0xFF9AA2, 0xFFDAC1, 0xFFFFB7, 0xC7F9CC, 0xA0E7E5, 0xBDB2FF, 0xFFC6FF};
        int color = colors[Math.floorMod(player.tickCount / 2, colors.length)];
        spawnDirectionalTrail(level, player, dust(color, 0.75F), 0.032D);
    }

    private static void spawnTrailBurst(Level level, Player player, CosmeticData cosmetic, boolean jump) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        if (id.equals("meteor_shower_trail")) {
            return;
        }
        if (NewParticleCosmeticEffects.spawnTrailBurst(level, player, cosmetic, jump)) {
            return;
        }
        if (jump && id.equals("stardust_steps_trail")) {
            spawnStardustJumpBurst(level, player);
            return;
        }
        if (jump && id.equals("sakura_shower_trail")) {
            spawnCherryBlossomBurst(level, player);
            return;
        }
        if (jump && id.equals("melody_trail")) {
            return;
        }
        if (jump && id.equals("golden_afterglow_trail")) {
            spawnGoldenJumpBurst(level, player);
            return;
        }
        if (jump && id.equals("emerald_glow_trail")) {
            spawnEmeraldJumpBurst(level, player);
            return;
        }
        if (jump && id.equals("wind_trace_trail")) {
            ParticlePacketDispatcher.send(level, particle("minecraft:gust_emitter_small"), player.getX(), player.getY() + 0.05D,
                    player.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            return;
        }
        if (jump && id.equals("arcana_trail")) {
            spawnMagicCircleBurst(level, player);
            return;
        }
        if (jump && id.equals("soulflame_trail")) {
            spawnRadialBurst(level, player, ParticleTypes.SOUL_FIRE_FLAME, 22, 0.18D, 0.080D, 0.055D);
            return;
        }
        if (jump && id.equals("ember_steps_trail")) {
            spawnScatterBurst(level, player, ParticleTypes.SMOKE, 22, 0.085D, 0.045D);
            return;
        }
        if (jump && id.equals("rainbow_spark_trail")) {
            spawnRainbowSparkBurst(level, player);
            return;
        }
        if (jump && id.equals("venom_mist_trail")) {
            spawnRadialBurst(level, player, ParticleTypes.WITCH, 24, 0.18D, 0.080D, 0.055D);
            return;
        }
        ParticleOptions particle = burstParticle(cosmetic);
        int points = jump ? 16 : 10;
        double radius = jump ? 0.28D : 0.18D;
        double outwardSpeed = jump ? 0.075D : 0.05D;
        double upwardSpeed = jump ? 0.035D : 0.018D;
        double moveX = player.getDeltaMovement().x;
        double moveZ = player.getDeltaMovement().z;
        double baseAngle = jump ? 0.0D : Math.toRadians(player.getYRot()) + Math.PI;
        double arc = jump ? Math.PI * 2.0D : Math.PI * 0.9D;

        for (int i = 0; i < points; i++) {
            double t = points == 1 ? 0.0D : (double) i / (points - 1);
            double jitter = Math.sin(player.tickCount * 0.37D + i * 1.91D) * 0.045D;
            double angle = jump
                    ? Math.PI * 2.0D * t + jitter
                    : baseAngle - arc * 0.5D + arc * t + jitter;
            double wave = Math.sin(i * 2.4D + player.tickCount * 0.2D) * 0.012D;
            double px = player.getX() + Math.cos(angle) * radius;
            double py = player.getY() + 0.08D + wave;
            double pz = player.getZ() + Math.sin(angle) * radius;
            double vx = Math.cos(angle) * outwardSpeed - moveX * 0.08D;
            double vy = upwardSpeed + (i % 3) * 0.012D;
            double vz = Math.sin(angle) * outwardSpeed - moveZ * 0.08D;
            sendMovingParticle(level, particle, px, py, pz, vx, vy, vz);
        }

        int centerPoints = jump ? 4 : 2;
        for (int i = 0; i < centerPoints; i++) {
            double side = i - (centerPoints - 1) * 0.5D;
            sendMovingParticle(
                    level,
                    particle,
                    player.getX() + side * 0.04D,
                    player.getY() + 0.12D,
                    player.getZ() - side * 0.04D,
                    -moveX * 0.04D,
                    jump ? 0.055D : 0.03D,
                    -moveZ * 0.04D
            );
        }
    }

    private static void spawnMagicCircleBurst(Level level, Player player) {
        double baseY = player.getY() + 0.07D;
        int points = 36;
        for (int ring = 0; ring < 2; ring++) {
            double radius = ring == 0 ? 0.58D : 1.00D;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0D * i / points;
                ParticleOptions particle = (i + ring) % 2 == 0
                        ? dust(ring == 0 ? 0x63D8FF : 0xA6EEFF, 0.62F)
                        : ParticleTypes.ENCHANT;
                sendMovingParticle(level, particle,
                        player.getX() + Math.cos(angle) * radius, baseY,
                        player.getZ() + Math.sin(angle) * radius,
                        0.0D, 0.004D, 0.0D);
            }
        }
        int vertices = 6;
        for (int edge = 0; edge < vertices; edge++) {
            double a0 = Math.PI * 2.0D * edge / vertices;
            double a1 = Math.PI * 2.0D * ((edge + 2) % vertices) / vertices;
            for (int point = 0; point <= 4; point++) {
                double t = point / 4.0D;
                double x = Math.cos(a0) * (1.0D - t) + Math.cos(a1) * t;
                double z = Math.sin(a0) * (1.0D - t) + Math.sin(a1) * t;
                ParticleOptions particle = (edge + point) % 2 == 0 ? ParticleTypes.ENCHANT : dust(0x72DFFF, 0.58F);
                ParticlePacketDispatcher.send(level, particle, player.getX() + x * 0.74D, baseY + 0.01D,
                        player.getZ() + z * 0.74D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void spawnCherryBlossomBurst(Level level, Player player) {
        double centerY = player.getY() + 0.10D;
        double petalArc = Math.PI * 2.0D / 5.0D;
        double notchHalfAngle = 0.12D;
        double baseRadius = 0.33D;
        double tipRadius = 0.61D;
        double notchRadius = 0.46D;
        int sideSteps = 5;

        for (int petal = 0; petal < 5; petal++) {
            double direction = -Math.PI * 0.5D + petal * petalArc;
            double leftBaseAngle = direction - petalArc * 0.5D;
            double leftTipAngle = direction - notchHalfAngle;
            double rightTipAngle = direction + notchHalfAngle;
            double rightBaseAngle = direction + petalArc * 0.5D;

            for (int step = 1; step <= sideSteps; step++) {
                double t = step / (double) sideSteps;
                double smooth = t * t * (3.0D - 2.0D * t);
                double angle = leftBaseAngle + (leftTipAngle - leftBaseAngle) * smooth;
                double radius = baseRadius + (tipRadius - baseRadius) * smooth
                        + Math.sin(Math.PI * t) * 0.055D;
                spawnCherryOutlinePoint(level, player, centerY, angle, radius);
            }

            spawnCherryOutlineLine(level, player, centerY,
                    leftTipAngle, tipRadius, direction, notchRadius, 2);
            spawnCherryOutlineLine(level, player, centerY,
                    direction, notchRadius, rightTipAngle, tipRadius, 2);

            for (int step = 1; step <= sideSteps; step++) {
                double t = step / (double) sideSteps;
                double smooth = t * t * (3.0D - 2.0D * t);
                double angle = rightTipAngle + (rightBaseAngle - rightTipAngle) * smooth;
                double radius = tipRadius + (baseRadius - tipRadius) * smooth
                        + Math.sin(Math.PI * t) * 0.055D;
                spawnCherryOutlinePoint(level, player, centerY, angle, radius);
            }
        }
    }

    private static void spawnCherryOutlineLine(Level level, Player player, double y,
                                                double fromAngle, double fromRadius,
                                                double toAngle, double toRadius, int steps) {
        double fromX = Math.cos(fromAngle) * fromRadius;
        double fromZ = Math.sin(fromAngle) * fromRadius;
        double toX = Math.cos(toAngle) * toRadius;
        double toZ = Math.sin(toAngle) * toRadius;
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            ParticlePacketDispatcher.send(level, ParticleTypes.CHERRY_LEAVES,
                    player.getX() + fromX + (toX - fromX) * t,
                    y,
                    player.getZ() + fromZ + (toZ - fromZ) * t,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void spawnCherryOutlinePoint(Level level, Player player,
                                                 double y, double angle, double radius) {
        ParticlePacketDispatcher.send(level, ParticleTypes.CHERRY_LEAVES,
                player.getX() + Math.cos(angle) * radius,
                y,
                player.getZ() + Math.sin(angle) * radius,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void spawnRainbowSparkBurst(Level level, Player player) {
        int[] colors = {0xFF9DA7, 0xFFD6A5, 0xFFF3A6, 0xB8F2C8, 0xA7E8F2, 0xC7B8FF};
        double baseY = player.getY() + 0.09D;
        for (int i = 0; i < colors.length; i++) {
            double angle = Math.PI * 2.0D * i / colors.length;
            for (int point = 1; point <= 2; point++) {
                double radius = 0.27D * point;
                sendMovingParticle(level, dust(colors[i], point == 2 ? 0.85F : 0.62F),
                        player.getX() + Math.cos(angle) * radius, baseY,
                        player.getZ() + Math.sin(angle) * radius,
                        Math.cos(angle) * 0.075D, 0.042D, Math.sin(angle) * 0.075D);
            }
        }
    }

    private static void spawnGoldenJumpBurst(Level level, Player player) {
        int[] colors = {0xFFB719, 0xFFD84A, 0xFFF3A0};
        double baseY = player.getY() + 0.10D;
        double rotation = Math.toRadians(player.yBodyRot);
        for (int ray = 0; ray < 5; ray++) {
            double baseAngle = rotation + Math.PI * 2.0D * ray / 5.0D;
            for (int point = 1; point <= 6; point++) {
                double t = point / 6.0D;
                double angle = baseAngle + t * 0.52D;
                double radius = 0.09D + point * 0.095D;
                ParticlePacketDispatcher.send(level, dust(colors[(ray + point) % colors.length], 0.72F),
                        player.getX() + Math.cos(angle) * radius,
                        baseY + Math.sin(t * Math.PI) * 0.16D,
                        player.getZ() + Math.sin(angle) * radius,
                        1, 0.001D, 0.001D, 0.001D, 0.0D);
            }
        }
    }

    private static void spawnEmeraldJumpBurst(Level level, Player player) {
        ItemParticleOption emerald = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.EMERALD));
        double baseY = player.getY() + 0.16D;
        int shards = 18;
        for (int shard = 0; shard < shards; shard++) {
            double angle = Math.PI * 2.0D * shard / shards + player.tickCount * 0.13D;
            double speed = 0.065D + (shard % 4) * 0.012D;
            double startRadius = 0.07D + (shard % 2) * 0.035D;
            sendMovingParticle(level, emerald,
                    player.getX() + Math.cos(angle) * startRadius,
                    baseY + (shard % 3) * 0.025D,
                    player.getZ() + Math.sin(angle) * startRadius,
                    Math.cos(angle) * speed,
                    0.070D + (shard % 5) * 0.010D,
                    Math.sin(angle) * speed);
        }
    }

    private static void spawnStardustJumpBurst(Level level, Player player) {
        double baseY = player.getY() + 0.10D;
        for (int ray = 0; ray < 5; ray++) {
            double angle = -Math.PI / 2.0D + Math.PI * 2.0D * ray / 5.0D;
            for (int point = 1; point <= 4; point++) {
                double radius = 0.13D * point;
                ParticleOptions option = point == 4 ? ParticleTypes.FIREWORK
                        : point % 2 == 0 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.END_ROD;
                sendMovingParticle(level, option,
                        player.getX() + Math.cos(angle) * radius,
                        baseY + point * 0.012D,
                        player.getZ() + Math.sin(angle) * radius,
                        Math.cos(angle) * (0.025D + point * 0.008D),
                        0.025D + point * 0.006D,
                        Math.sin(angle) * (0.025D + point * 0.008D));
            }
        }
    }

    private static void spawnScatterBurst(Level level, Player player, ParticleOptions particle,
                                          int points, double horizontalSpeed, double verticalSpeed) {
        double baseY = player.getY() + 0.10D;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points + Math.sin(i * 2.17D) * 0.18D;
            double speed = horizontalSpeed * (0.45D + (i % 5) * 0.13D);
            sendMovingParticle(level, particle,
                    player.getX() + Math.cos(angle) * 0.025D,
                    baseY + (i % 3) * 0.012D,
                    player.getZ() + Math.sin(angle) * 0.025D,
                    Math.cos(angle) * speed,
                    verticalSpeed * (0.35D + (i % 4) * 0.18D),
                    Math.sin(angle) * speed);
        }
    }

    private static void spawnRadialBurst(Level level, Player player, ParticleOptions particle,
                                         int points, double radius, double horizontalSpeed, double verticalSpeed) {
        double baseY = player.getY() + 0.10D;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            double layerRadius = radius + (i % 3) * 0.055D;
            sendMovingParticle(level, particle,
                    player.getX() + Math.cos(angle) * layerRadius,
                    baseY + (i % 2) * 0.025D,
                    player.getZ() + Math.sin(angle) * layerRadius,
                    Math.cos(angle) * horizontalSpeed,
                    verticalSpeed + (i % 4) * 0.008D,
                    Math.sin(angle) * horizontalSpeed);
        }
    }

    private static void sendMovingParticle(Level level, ParticleOptions particle, double x, double y, double z,
                                           double vx, double vy, double vz) {
        ParticlePacketDispatcher.send(level, particle, x, y, z, 0, vx, vy, vz, 1.0D);
    }

    private static ParticleOptions burstParticle(CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "black_haze_trail" -> dust(0x2C2A35, 0.65F);
            case "aqua_drops_trail" -> ParticleTypes.SPLASH;
            case "melody_trail" -> ParticleTypes.NOTE;
            case "soulflame_trail" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "frost_trace_trail" -> ParticleTypes.SNOWFLAKE;
            case "ember_steps_trail" -> ParticleTypes.FLAME;
            case "sakura_shower_trail" -> ParticleTypes.CHERRY_LEAVES;
            case "rainbow_spark_trail" -> dust(0xBDB2FF, 0.7F);
            case "golden_afterglow_trail" -> dust(0xFFD45C, 0.8F);
            default -> followParticle(cosmetic);
        };
    }

    private static void spawnHalo(Level level, Player player, CosmeticData cosmetic) {
        if (NewParticleCosmeticEffects.spawnRing(level, player, cosmetic)) {
            return;
        }
        ParticleEffect effect = RING_EFFECTS.get(cosmetic.id());
        if (effect != null) {
            effect.spawn(level, player, cosmetic);
            return;
        }
        spawnGenericHalo(level, player, cosmetic);
    }

    private static void spawnGenericHalo(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        ParticleOptions particle = switch (id) {
            case "soulfire_halo_ring" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "angel_halo_ring" -> dust(0xFFF2A8, 0.65F);
            case "portal_ring" -> dust(0xA66BFF, 0.68F);
            default -> followParticle(cosmetic);
        };
        int points = id.equals("soulfire_halo_ring") ? 8
                : id.equals("portal_ring") ? 12 : Math.max(8, cosmetic.count());
        double radius = 0.32D;
        double height = cosmetic.offsetY() + (id.equals("soulfire_halo_ring") ? 0.12D : 0.0D);
        Anchor anchor = headAnchor(player, height);
        double phase = (player.tickCount % 360) * 0.045D;
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            ParticlePacketDispatcher.send(level,
                    particle,
                    anchor.x() + Math.cos(angle) * radius,
                    anchor.y(),
                    anchor.z() + Math.sin(angle) * radius,
                    1,
                    0.01D,
                    0.01D,
                    0.01D,
                    0.0D
            );
        }
    }

    private static void spawnDesignedHalo(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        Anchor anchor = headAnchor(player, cosmetic.offsetY());
        boolean deepCrown = id.equals("deep_crown_ring");
        double phase = player.tickCount * (deepCrown ? 0.020D : 0.009D);
        boolean crystal = id.equals("crystal_diadem_ring");
        boolean thorn = id.equals("thorn_crown_ring");
        boolean ember = id.equals("ember_crown_ring");
        int points = crystal || thorn ? 15 : 12;
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            double radius = crystal ? 0.29D : 0.32D;
            double y = anchor.y();
            ParticleOptions option;
            if (thorn) {
                option = i % 5 == 0 ? ParticleTypes.HAPPY_VILLAGER
                        : i % 3 == 0 ? ParticleTypes.COMPOSTER : dust(0x4FAE59, 0.58F);
                y += i % 3 == 0 ? 0.11D : 0.0D;
            } else if (crystal) {
                option = i % 3 == 0 ? dust(0x83E7FF, 0.72F)
                        : i % 3 == 1 ? dust(0xC8F7FF, 0.62F) : ParticleTypes.GLOW;
                y += i % 3 == 0 ? 0.12D : 0.0D;
            } else if (ember) {
                option = player.tickCount % 48 == 0 && i == 0 ? ParticleTypes.LAVA
                        : i % 4 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME;
                y += i % 3 == 0 ? 0.12D : 0.0D;
            } else {
                option = i % 3 == 0
                        ? new SculkChargeParticleOptions((float) angle)
                        : i % 3 == 1 ? ParticleTypes.SCULK_CHARGE_POP : dust(0x38C5B5, 0.60F);
                y += Math.sin(i * Math.PI * 2.0D / points) * 0.06D;
            }
            ParticlePacketDispatcher.send(level, option, anchor.x() + Math.cos(angle) * radius, y,
                    anchor.z() + Math.sin(angle) * radius, 1, 0.002D, 0.002D, 0.002D, 0.0D);
        }
        if (deepCrown && player.tickCount % 96 == 0) {
            ParticlePacketDispatcher.send(level, new ShriekParticleOption(0), anchor.x(), anchor.y() + 0.22D, anchor.z(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void spawnBreezeSpiralRing(Level level, Player player, CosmeticData cosmetic) {
        Anchor anchor = headAnchor(player, cosmetic.offsetY());
        double phase = player.tickCount * 0.026D;
        for (int i = 0; i < 10; i++) {
            double angle = phase + Math.PI * 2.0D * i / 10.0D;
            double radius = 0.30D + Math.sin(angle * 2.0D) * 0.045D;
            double y = anchor.y() + Math.sin(angle) * 0.035D;
            ParticlePacketDispatcher.send(level, i % 3 == 0 ? ParticleTypes.SMALL_GUST : dust(0xD8F7FF, 0.46F),
                    anchor.x() + Math.cos(angle) * radius, y, anchor.z() + Math.sin(angle) * radius,
                    1, 0.001D, 0.001D, 0.001D, 0.0D);
        }
    }

    private static void spawnMoonRing(Level level, Player player, CosmeticData cosmetic) {
        ParticleOptions particle = dust(0xD8E6FF, 0.65F);
        Anchor anchor = headAnchor(player, cosmetic.offsetY());
        double phase = Math.toRadians(player.getYRot()) + player.tickCount * 0.009D;
        double radius = 0.31D;
        double cutoutOffset = 0.13D;
        double cutoutRadius = radius * 0.98D;
        int samples = 11;
        for (int ix = 0; ix < samples; ix++) {
            for (int iz = 0; iz < samples; iz++) {
                double x = -radius + radius * 2.0D * ix / (samples - 1);
                double z = -radius + radius * 2.0D * iz / (samples - 1);
                boolean inMoon = x * x + z * z <= radius * radius;
                double cutX = x - cutoutOffset;
                boolean inCutout = cutX * cutX + z * z <= cutoutRadius * cutoutRadius;
                if (!inMoon || inCutout) {
                    continue;
                }
                double rotatedX = x * Math.cos(phase) - z * Math.sin(phase);
                double rotatedZ = x * Math.sin(phase) + z * Math.cos(phase);
                ParticlePacketDispatcher.send(level, particle, anchor.x() + rotatedX, anchor.y(), anchor.z() + rotatedZ,
                        1, 0.001D, 0.001D, 0.001D, 0.0D);
            }
        }
    }

    private static void spawnFlowerCrownRing(Level level, Player player, CosmeticData cosmetic) {
        Anchor anchor = headAnchor(player, cosmetic.offsetY());
        double phase = player.tickCount * 0.010D;
        int points = 12;
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            double centerX = anchor.x() + Math.cos(angle) * 0.30D;
            double centerZ = anchor.z() + Math.sin(angle) * 0.30D;
            ParticlePacketDispatcher.send(level, dust(i % 2 == 0 ? 0x65C96F : 0x8BDD83, 0.55F),
                    centerX, anchor.y() - 0.018D, centerZ, 1, 0.002D, 0.002D, 0.002D, 0.0D);
            if (i % 2 == 0) {
                ParticlePacketDispatcher.send(level, dust(i % 4 == 0 ? 0xFF9FC8 : 0xFFD3E4, 0.70F),
                        centerX, anchor.y() + 0.045D, centerZ,
                        1, 0.001D, 0.001D, 0.001D, 0.0D);
            }
        }
    }

    private static void spawnStarCrown(Level level, Player player, CosmeticData cosmetic) {
        Anchor anchor = headAnchor(player, cosmetic.offsetY());
        double baseY = anchor.y();
        double phase = Math.toRadians(player.getYRot()) + player.tickCount * 0.0035D;
        ParticleOptions bandParticle = dust(0xFFF2A8, 0.58F);
        ParticleOptions peakParticle = bandParticle;
        int bandPoints = 20;
        for (int i = 0; i < bandPoints; i++) {
            double angle = phase + Math.PI * 2.0D * i / bandPoints;
            spawnCrownParticle(level, anchor, bandParticle, angle, 0.32D, baseY, 0.003D);
        }

        int spikes = 5;
        for (int i = 0; i < spikes; i++) {
            double center = phase + Math.PI * 2.0D * i / spikes;
            double peakY = baseY + 0.16D + (i == 0 ? 0.03D : 0.0D);
            for (int step = 1; step <= 3; step++) {
                spawnCrownParticle(level, anchor, peakParticle, center, 0.32D + step * 0.008D,
                        baseY + (peakY - baseY) * step / 3.0D, 0.002D);
            }
        }
    }

    private static void spawnCrownParticle(Level level, Anchor anchor, ParticleOptions particle,
                                           double angle, double radius, double y, double spread) {
        ParticlePacketDispatcher.send(level,
                particle,
                anchor.x() + Math.cos(angle) * radius,
                y,
                anchor.z() + Math.sin(angle) * radius,
                1,
                spread,
                spread,
                spread,
                0.0D
        );
    }

    private static void spawnOrbit(Level level, Player player, CosmeticData cosmetic) {
        if (NewParticleCosmeticEffects.spawnOrbit(level, player, cosmetic)) {
            return;
        }
        ParticleEffect effect = ORBIT_EFFECTS.get(cosmetic.id());
        if (effect != null) {
            effect.spawn(level, player, cosmetic);
            return;
        }
        spawnGenericOrbit(level, player, cosmetic);
    }

    private static void spawnGenericOrbit(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        ParticleOptions particle = id.equals("end_echo_orbit")
                ? dust(0xA56BFF, 0.68F)
                : id.equals("emerald_arc_orbit")
                ? dust(0x63E878, 0.72F)
                : particle(cosmetic.particle());
        int points = id.equals("wandering_souls_orbit") ? 3
                : id.equals("end_echo_orbit") ? Math.max(24, cosmetic.count() + 21)
                : id.equals("emerald_arc_orbit") ? Math.max(5, cosmetic.count() + 2)
                : Math.max(3, cosmetic.count());
        double phase = (player.tickCount % 360) * 0.05D;
        double radius = 0.62D;
        Anchor anchor = bodyAnchor(player, cosmetic.offsetY());
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            double wave = Math.sin(phase * 0.6D + i) * 0.16D;
            ParticlePacketDispatcher.send(level,
                    particle,
                    anchor.x() + Math.cos(angle) * radius,
                    anchor.y() + wave,
                    anchor.z() + Math.sin(angle) * radius,
                    1,
                    0.003D,
                    0.003D,
                    0.003D,
                    0.0D
            );
            if (id.equals("emerald_arc_orbit") && (player.tickCount + i) % 3 == 0) {
                ParticlePacketDispatcher.send(level, ParticleTypes.HAPPY_VILLAGER,
                        anchor.x() + Math.cos(angle) * radius,
                        anchor.y() + wave + 0.03D,
                        anchor.z() + Math.sin(angle) * radius,
                        1, 0.002D, 0.002D, 0.002D, 0.0D);
            }
        }
    }

    private static void spawnDesignedOrbit(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        Anchor anchor = bodyAnchor(player, cosmetic.offsetY());
        boolean twin = id.equals("twin_moons_orbit");
        boolean crystal = id.equals("crystal_satellites_orbit");
        boolean voidSatellite = id.equals("void_satellite_orbit");
        boolean firefly = id.equals("firefly_swarm_orbit");
        int points = twin ? 2 : crystal ? 3 : firefly ? Math.max(10, cosmetic.count()) : Math.max(5, cosmetic.count());
        double speed = voidSatellite ? 0.075D : 0.038D;
        double phase = player.tickCount * speed;
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            double radius = firefly ? 0.68D + Math.sin(i * 2.3D + phase) * 0.08D : 0.66D;
            double y = anchor.y() + Math.sin(angle) * 0.17D;
            if (twin) {
                spawnMoonCluster(level, anchor, angle, y, radius,
                        i == 0 ? 0xF8FCFF : 0xB995FF);
                continue;
            }
            ParticleOptions option;
            if (crystal) {
                option = ParticleTypes.END_ROD;
            } else if (voidSatellite) {
                option = i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL;
            } else {
                option = dust(i % 3 == 0 ? 0xFFF5A3 : 0xFFE05C, i % 2 == 0 ? 0.68F : 0.54F);
            }
            ParticlePacketDispatcher.send(level, option, anchor.x() + Math.cos(angle) * radius, y,
                    anchor.z() + Math.sin(angle) * radius, 1, 0.003D, 0.003D, 0.003D, 0.0D);

            if (voidSatellite && (player.tickCount + i) % 3 == 0) {
                ParticlePacketDispatcher.send(level, dust(0x7040A8, 0.46F),
                        anchor.x() + Math.cos(angle) * (radius - 0.055D), y,
                        anchor.z() + Math.sin(angle) * (radius - 0.055D),
                        1, 0.001D, 0.001D, 0.001D, 0.0D);
            } else if (firefly && (player.tickCount + i) % 5 == 0) {
                ParticlePacketDispatcher.send(level, dust(0xFFFBD1, 0.78F), anchor.x() + Math.cos(angle) * radius, y,
                        anchor.z() + Math.sin(angle) * radius,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void spawnMoonCluster(Level level, Anchor anchor, double angle, double y,
                                         double orbitRadius, int color) {
        double centerX = anchor.x() + Math.cos(angle) * orbitRadius;
        double centerZ = anchor.z() + Math.sin(angle) * orbitRadius;
        int points = 10;
        double goldenAngle = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int point = 0; point < points; point++) {
            double normalizedY = 1.0D - 2.0D * (point + 0.5D) / points;
            double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - normalizedY * normalizedY));
            double localAngle = point * goldenAngle;
            ParticlePacketDispatcher.send(level, dust(color, point % 3 == 0 ? 0.82F : 0.66F),
                    centerX + Math.cos(localAngle) * horizontal * 0.105D,
                    y + normalizedY * 0.095D,
                    centerZ + Math.sin(localAngle) * horizontal * 0.105D,
                    1, 0.001D, 0.001D, 0.001D, 0.0D);
        }
    }

    private static void spawnMagicRuneOrbit(Level level, Player player, CosmeticData cosmetic) {
        double phase = (player.tickCount % 360) * 0.038D;
        Anchor anchor = bodyAnchor(player, cosmetic.offsetY());
        int points = 3;
        int[] colors = {0x69E3FF, 0x9C7BFF, 0xD77CFF};
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            double y = anchor.y() + Math.sin(phase * 0.7D + i * 1.8D) * 0.18D;
            ParticlePacketDispatcher.send(level, dust(colors[i], 0.76F),
                    anchor.x() + Math.cos(angle) * 0.62D,
                    y,
                    anchor.z() + Math.sin(angle) * 0.62D,
                    1, 0.002D, 0.002D, 0.002D, 0.0D);
            if ((player.tickCount + i) % 3 == 0) {
                ParticlePacketDispatcher.send(level, ParticleTypes.ENCHANT,
                        anchor.x() + Math.cos(angle) * 0.60D, y,
                        anchor.z() + Math.sin(angle) * 0.60D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void spawnElementOrbit(Level level, Player player, CosmeticData cosmetic) {
        int[] colors = {0xFF7A45, 0x65D6FF, 0x73F080};
        double phase = (player.tickCount % 360) * 0.04D;
        Anchor anchor = bodyAnchor(player, cosmetic.offsetY());
        for (int i = 0; i < colors.length; i++) {
            double angle = phase + Math.PI * 2.0D * i / colors.length;
            double y = anchor.y() + Math.sin(phase + i) * 0.08D;
            ParticlePacketDispatcher.send(level, dust(colors[i], 0.75F),
                    anchor.x() + Math.cos(angle) * 0.62D,
                    y,
                    anchor.z() + Math.sin(angle) * 0.62D,
                    1,
                    0.004D,
                    0.004D,
                    0.004D,
                    0.0D);
            for (int satellite = 0; satellite < 6; satellite++) {
                double local = phase * 1.7D + Math.PI * 2.0D * satellite / 6.0D;
                ParticlePacketDispatcher.send(level, dust(colors[i], 0.52F),
                        anchor.x() + Math.cos(angle) * 0.62D + Math.cos(local) * 0.075D,
                        y + Math.sin(local) * 0.075D,
                        anchor.z() + Math.sin(angle) * 0.62D + Math.sin(local) * 0.075D,
                        1, 0.001D, 0.001D, 0.001D, 0.0D);
            }
        }
    }

    private static void spawnAura(Level level, Player player, CosmeticData cosmetic) {
        if (NewParticleCosmeticEffects.spawnAura(level, player, cosmetic)) {
            return;
        }
        ParticleEffect effect = AURA_EFFECTS.get(cosmetic.id());
        if (effect != null) {
            effect.spawn(level, player, cosmetic);
            return;
        }
        spawnGenericAura(level, player, cosmetic);
    }

    private static void spawnGenericAura(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        ParticleOptions particle = auraParticle(cosmetic);
        int points = Math.max(2, cosmetic.count());
        double phase = (player.tickCount % 360) * 0.065D;
        Anchor anchor = bodyAnchor(player, 0.35D);
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double angle = phase + Math.PI * 2.0D * t + (id.equals("pixel_glitch_aura") ? i * 0.4D : 0.0D);
            double radius = 0.18D + (i % 3) * 0.09D;
            double y = anchor.y() + (t * 1.0D);
            ParticlePacketDispatcher.send(level,
                    particle,
                    anchor.x() + Math.cos(angle) * radius,
                    y,
                    anchor.z() + Math.sin(angle) * radius,
                    1,
                    0.015D,
                    0.015D,
                    0.015D,
                    0.0D
            );
        }
    }

    private static void spawnDesignedAura(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        Anchor anchor = bodyAnchor(player, cosmetic.offsetY());
        int points = Math.max(4, cosmetic.count());
        double phase = player.tickCount * 0.052D;
        int ominousStage = Math.floorMod(player.tickCount / 12, 10);
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            double radius = 0.30D + (i % 2) * 0.12D;
            double y = anchor.y() + (i % 4) * 0.18D;
            ParticleOptions option;
            if (id.equals("arcane_pulse_aura")) {
                option = i % 4 == 0 ? ParticleTypes.DRAGON_BREATH
                        : i % 2 == 0 ? ParticleTypes.ENCHANT : ParticleTypes.WITCH;
                radius += (player.tickCount % 24) * 0.012D;
            } else if (id.equals("snow_blossom_aura")) {
                option = i % 4 == 0 ? ParticleTypes.WHITE_ASH
                        : i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SNOWFLAKE;
            } else {
                option = ominousStage < 5 ? particle("minecraft:ominous_spawning")
                        : ominousStage < 9 ? particle("minecraft:trial_omen")
                        : i == 0 ? particle("minecraft:trial_spawner_detection_ominous") : dust(0x5CC8B8, 0.52F);
            }
            ParticlePacketDispatcher.send(level, option, anchor.x() + Math.cos(angle) * radius, y,
                    anchor.z() + Math.sin(angle) * radius, 1, 0.004D, 0.004D, 0.004D, 0.0D);
        }
    }

    private static void spawnSculkHeartbeatAura(Level level, Player player, CosmeticData cosmetic) {
        Anchor anchor = bodyAnchor(player, cosmetic.offsetY());
        double pulse = 0.32D + (Math.sin(player.tickCount * 0.22D) + 1.0D) * 0.10D;
        int points = 8;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            ParticleOptions option = i % 2 == 0
                    ? new SculkChargeParticleOptions((float) angle)
                    : dust(0x27B7A6, 0.56F);
            ParticlePacketDispatcher.send(level, option,
                    anchor.x() + Math.cos(angle) * pulse,
                    anchor.y() + 0.20D + Math.sin(angle * 2.0D) * 0.08D,
                    anchor.z() + Math.sin(angle) * pulse,
                    1, 0.001D, 0.001D, 0.001D, 0.0D);
        }
        if (player.tickCount % 32 == 0) {
            ParticlePacketDispatcher.send(level, ParticleTypes.SCULK_SOUL, anchor.x(), anchor.y() + 0.30D, anchor.z(),
                    2, 0.08D, 0.05D, 0.08D, 0.0D);
        }
        if (player.tickCount % 128 == 0) {
            ParticlePacketDispatcher.send(level, new ShriekParticleOption(0), anchor.x(), anchor.y() + 0.42D, anchor.z(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void spawnAuroraWallOrbit(Level level, Player player, CosmeticData cosmetic) {
        int[] colors = {0x69F1C8, 0x78E8F2, 0x8ABEFF, 0xC69BFF, 0xFF9ED8};
        Anchor anchor = bodyAnchor(player, cosmetic.offsetY());
        double phase = player.tickCount * 0.032D;
        int walls = 4;
        int columns = 3;
        int rows = 4;
        double orbitRadius = 0.68D;
        double wallWidth = 0.32D;
        double wallHeight = 0.48D;

        for (int wall = 0; wall < walls; wall++) {
            double angle = phase + Math.PI * 2.0D * wall / walls;
            double centerX = anchor.x() + Math.cos(angle) * orbitRadius;
            double centerZ = anchor.z() + Math.sin(angle) * orbitRadius;
            double tangentX = -Math.sin(angle);
            double tangentZ = Math.cos(angle);
            for (int row = 0; row < rows; row++) {
                double vertical = (row / (double) (rows - 1) - 0.5D) * wallHeight;
                for (int column = 0; column < columns; column++) {
                    double horizontal = (column / (double) (columns - 1) - 0.5D) * wallWidth;
                    int colorIndex = Math.floorMod(wall + row + column + player.tickCount / 10, colors.length);
                    boolean corner = (row == 0 || row == rows - 1) && (column == 0 || column == columns - 1);
                    ParticlePacketDispatcher.send(level, dust(colors[colorIndex], corner ? 0.78F : 0.60F),
                            centerX + tangentX * horizontal,
                            anchor.y() + vertical,
                            centerZ + tangentZ * horizontal,
                            1, 0.001D, 0.001D, 0.001D, 0.0D);
                }
            }
        }
    }

    private static void spawnRainCloudAura(Level level, Player player, CosmeticData cosmetic) {
        double phase = (player.tickCount % 360) * 0.045D;
        Anchor anchor = headAnchor(player, 2.45D);
        double cloudY = anchor.y();
        int points = Math.max(6, cosmetic.count() + 2);
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            double px = anchor.x() + Math.cos(angle) * 0.34D;
            double pz = anchor.z() + Math.sin(angle) * 0.34D;
            ParticlePacketDispatcher.send(level, ParticleTypes.CLOUD, px, cloudY, pz, 2, 0.045D, 0.018D, 0.045D, 0.005D);
            if ((player.tickCount + i) % 2 == 0) {
                sendMovingParticle(level, ParticleTypes.SPLASH, px, cloudY - 0.10D, pz, 0.0D, -0.035D, 0.0D);
            }
        }
    }

    private static void spawnEnchantAura(Level level, Player player) {
        Anchor anchor = bodyAnchor(player, 0.35D);
        int[] colors = {0x72E8FF, 0xA884FF, 0xE284FF};
        double phase = player.tickCount * 0.052D;
        for (int i = 0; i < 5; i++) {
            double angle = phase + Math.PI * 2.0D * i / 5.0D;
            double y = anchor.y() + 0.18D + i * 0.20D;
            ParticlePacketDispatcher.send(level, ParticleTypes.ENCHANT,
                    anchor.x() + Math.cos(angle) * 0.34D, y, anchor.z() + Math.sin(angle) * 0.34D,
                    2, 0.025D, 0.025D, 0.025D, 0.0D);
            ParticlePacketDispatcher.send(level, dust(colors[i % colors.length], 0.92F),
                    anchor.x() + Math.cos(angle) * 0.31D, y, anchor.z() + Math.sin(angle) * 0.31D,
                    1, 0.002D, 0.002D, 0.002D, 0.0D);
        }
    }

    private static void spawnSupporterAura(Level level, Player player, CosmeticData cosmetic) {
        int[] colors = {0x8EDFFF, 0xF8FCFF, 0xFFC9E8};
        Anchor anchor = bodyAnchor(player, 0.35D);
        double phase = player.tickCount * 0.045D;
        int points = Math.max(9, cosmetic.count() * 3);
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            double y = anchor.y() + 0.18D + i * (0.82D / points);
            ParticlePacketDispatcher.send(level, dust(colors[i % colors.length], 0.68F),
                    anchor.x() + Math.cos(angle) * 0.28D, y, anchor.z() + Math.sin(angle) * 0.28D,
                    1, 0.004D, 0.004D, 0.004D, 0.0D);
        }
    }

    private static void spawnHeartAura(Level level, Player player, CosmeticData cosmetic) {
        Anchor anchor = bodyAnchor(player, 0.20D);
        double phase = player.tickCount * 0.04D;
        int points = Math.max(2, cosmetic.count());
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            ParticlePacketDispatcher.send(level, ParticleTypes.HEART,
                    anchor.x() + Math.cos(angle) * 0.27D,
                    anchor.y() + 0.08D + i * 0.22D,
                    anchor.z() + Math.sin(angle) * 0.27D,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    private static void spawnCosmicAura(Level level, Player player, CosmeticData cosmetic) {
        Anchor anchor = bodyAnchor(player, 0.35D);
        int[] colors = {0xEAF6FF, 0x8EC8FF, 0xB79BFF};
        int points = Math.max(4, cosmetic.count());
        for (int i = 0; i < points; i++) {
            double seed = player.tickCount * 0.19D + i * 2.399D;
            double angle = seed * 0.73D;
            double radius = 0.24D + (Math.sin(seed) + 1.0D) * 0.16D;
            double y = anchor.y() + 0.12D + (Math.cos(seed * 0.61D) + 1.0D) * 0.52D;
            ParticlePacketDispatcher.send(level, dust(colors[i % colors.length], i % 3 == 0 ? 0.86F : 0.54F),
                    anchor.x() + Math.cos(angle) * radius, y, anchor.z() + Math.sin(angle) * radius,
                    1, 0.001D, 0.001D, 0.001D, 0.0D);
            if ((player.tickCount + i) % 5 == 0) {
                ParticlePacketDispatcher.send(level, ParticleTypes.END_ROD,
                        anchor.x() + Math.cos(angle) * radius, y, anchor.z() + Math.sin(angle) * radius,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void spawnChampionFlare(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        int color = switch (id) {
            case "champion_bronze_glory_aura" -> 0xC77A3C;
            case "champion_silver_glory_aura" -> 0xD5DEE8;
            case "champion_platinum_glory_aura" -> 0xB7F1E8;
            case "champion_diamond_glory_aura" -> 0x72D7FF;
            default -> 0xFFD45C;
        };
        int highlight = switch (id) {
            case "champion_bronze_glory_aura" -> 0xE6A064;
            case "champion_silver_glory_aura" -> 0xF4F8FC;
            case "champion_platinum_glory_aura" -> 0xE2FFFA;
            case "champion_diamond_glory_aura" -> 0xC5F2FF;
            default -> 0xFFF0A3;
        };
        spawnChampionSpiralAura(level, player, color, highlight);
    }

    private static void spawnChampionSpiralAura(Level level, Player player, int color, int highlight) {
        Anchor anchor = bodyAnchor(player, 0.35D);
        double phase = (player.tickCount % 360) * 0.065D;
        int points = 30;
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double angle = phase + Math.PI * 2.0D * t;
            double radius = 0.18D + (i % 3) * 0.09D;
            ParticlePacketDispatcher.send(level, dust(i % 4 == 0 ? highlight : color, i % 4 == 0 ? 0.84F : 0.70F),
                        anchor.x() + Math.cos(angle) * radius,
                        anchor.y() + t,
                        anchor.z() + Math.sin(angle) * radius,
                        1, 0.001D, 0.001D, 0.001D, 0.0D);
        }
    }

    private static ParticleOptions auraParticle(CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "dimensional_rift_aura" -> ParticleTypes.REVERSE_PORTAL;
            case "runic_glow_aura" -> ParticleTypes.ENCHANT;
            case "pixel_glitch_aura" -> dust((cosmetic.count() + cosmetic.id().length()) % 2 == 0
                    ? 0x55F0FF : 0xFF67D8, 0.7F);
            case "rose_glow_aura" -> dust(0xFF9BEF, 0.75F);
            default -> particle(cosmetic.particle());
        };
    }

    private static void spawnCompanion(Level level, Player player, CosmeticData cosmetic, ParticleState state) {
        double phase = player.tickCount * 0.24D;
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        double orbitRadius = 0.17D;
        double sphereRadius = id.equals("cloud_wisp_companion")
                ? CLOUD_COMPANION_SPHERE_RADIUS
                : COMPANION_SPHERE_RADIUS;
        double x = state.companionX;
        double y = state.companionY;
        double z = state.companionZ;

        if (NewParticleCosmeticEffects.spawnCompanion(level, player, cosmetic, x, y, z,
                state.companionRenderStep)) {
            state.companionRenderStep++;
            return;
        }

        CompanionParticles particles = companionParticles(cosmetic);

        if (id.equals("cloud_wisp_companion")) {
            spawnHoveringCloudSphere(level, x, y, z, sphereRadius, phase, state.companionRenderStep);
            spawnCompanionSphere(level, dust(0xE7F4FF, 0.46F), x, y + 0.008D, z,
                    sphereRadius * 0.72D, -phase * 0.61D, state.companionRenderStep + 1);
        } else {
            spawnCompanionSphere(level, particles.core(), x, y, z, sphereRadius, phase,
                    state.companionRenderStep);
        }
        if (id.equals("void_eye_companion")) {
            for (int i = 0; i < 4; i++) {
                double angle = phase * 0.45D + Math.PI * 2.0D * i / 4.0D;
                ParticlePacketDispatcher.send(level, dust(i % 2 == 0 ? 0x8C59C8 : 0x5C358F, 0.58F),
                        x + Math.cos(angle) * 0.12D, y + Math.sin(angle) * 0.072D,
                        z + Math.sin(angle + phase * 0.23D) * 0.025D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            if (player.tickCount % 12 == 0) {
                ParticlePacketDispatcher.send(level, ParticleTypes.SCULK_SOUL, x, y + 0.015D, z,
                        1, 0.015D, 0.015D, 0.015D, 0.0D);
            }
        }
        for (int i = 0; i < 2; i++) {
            double angle = phase + Math.PI * i;
            ParticlePacketDispatcher.send(level,
                    particles.orbit(),
                    x + Math.cos(angle) * orbitRadius,
                    y + Math.sin(angle * 0.7D) * 0.07D,
                    z + Math.sin(angle) * orbitRadius,
                    1,
                    0.004D,
                    0.004D,
                    0.004D,
                    0.0D
            );
        }

        if (!id.equals("cloud_wisp_companion") && player.tickCount % 8 == 0) {
            sendMovingParticle(level, particles.trail(), x, y - 0.04D, z, 0.0D, -0.012D, 0.0D);
        }
        state.companionRenderStep++;
    }

    private static void spawnCompanionSphere(Level level, ParticleOptions particle,
                                             double x, double y, double z, double radius,
                                             double phase, int renderStep) {
        // Alternate halves by companion render pass rather than world-tick parity. Companions with an even
        // interval would otherwise redraw only one half of the sphere forever.
        int parity = Math.floorMod(renderStep, 2);
        double goldenAngle = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = parity; i < COMPANION_SPHERE_POINTS; i += 2) {
            double normalizedY = 1.0D - 2.0D * (i + 0.5D) / COMPANION_SPHERE_POINTS;
            double horizontalRadius = Math.sqrt(Math.max(0.0D, 1.0D - normalizedY * normalizedY));
            double angle = i * goldenAngle + phase * 0.42D;
            double px = x + Math.cos(angle) * horizontalRadius * radius;
            double py = y + normalizedY * radius;
            double pz = z + Math.sin(angle) * horizontalRadius * radius;
            ParticlePacketDispatcher.send(level, particle, px, py, pz,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void spawnHoveringCloudSphere(Level level, double x, double y, double z,
                                                  double radius, double phase, int renderStep) {
        int parity = Math.floorMod(renderStep, 2);
        double goldenAngle = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = parity; i < COMPANION_SPHERE_POINTS; i += 2) {
            double normalizedY = 1.0D - 2.0D * (i + 0.5D) / COMPANION_SPHERE_POINTS;
            double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - normalizedY * normalizedY));
            double angle = i * goldenAngle + phase * 0.42D;
            sendMovingParticle(level, ParticleTypes.CLOUD,
                    x + Math.cos(angle) * horizontal * radius,
                    y + normalizedY * radius * 0.92D,
                    z + Math.sin(angle) * horizontal * radius,
                    0.0D, 0.075D, 0.0D);
        }
    }

    private static CompanionParticles companionParticles(CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        if (!COMPANION_EFFECTS.contains(id)) {
            SimpleParticleType configured = particle(cosmetic.particle());
            return new CompanionParticles(configured, configured, configured);
        }
        return switch (id) {
            case "trial_flame_companion" -> new CompanionParticles(ParticleTypes.SMALL_FLAME,
                    ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER, ParticleTypes.WAX_ON);
            case "cloud_wisp_companion" -> new CompanionParticles(ParticleTypes.CLOUD,
                    dust(0xE7F4FF, 0.46F), dust(0xFFFFFF, 0.38F));
            case "void_eye_companion" -> new CompanionParticles(dust(0x7040A8, 0.64F),
                    dust(0xA66BFF, 0.54F), dust(0x4D286F, 0.40F));
            case "fairy_light_companion" -> new CompanionParticles(dust(0xF4FCFF, 0.62F),
                    dust(0xBDEBFF, 0.58F), ParticleTypes.END_ROD);
            case "fire_wisp_companion" -> new CompanionParticles(ParticleTypes.SMALL_FLAME,
                    dust(0xFF9A38, 0.58F), ParticleTypes.FLAME);
            case "aqua_wisp_companion" -> new CompanionParticles(dust(0x6ECFFF, 0.62F),
                    dust(0xB9EEFF, 0.52F), ParticleTypes.FALLING_WATER);
            case "forest_wisp_companion" -> new CompanionParticles(dust(0x69C96B, 0.62F),
                    dust(0xB8E58A, 0.52F), dust(0x76D77B, 0.40F));
            case "firefly_wisp_companion" -> new CompanionParticles(dust(0xFFF3A1, 0.62F),
                    dust(0xFFE05C, 0.56F), ParticleTypes.GLOW);
            default -> new CompanionParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    dust(0x69DFFF, 0.62F), ParticleTypes.ELECTRIC_SPARK);
        };
    }

    private static SimpleParticleType followParticle(CosmeticData cosmetic) {
        SimpleParticleType configured = particle(cosmetic.particle());
        return configured == ParticleTypes.END_ROD ? ParticleTypes.ELECTRIC_SPARK : configured;
    }

    private static SimpleParticleType particle(String id) {
        if (id == null || id.isBlank()) {
            return ParticleTypes.END_ROD;
        }
        return PARTICLE_CACHE.computeIfAbsent(id, ParticleTrailManager::resolveParticle);
    }

    private static SimpleParticleType resolveParticle(String id) {
        try {
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(id));
            if (type instanceof SimpleParticleType simple) {
                return simple;
            }
        } catch (RuntimeException ignored) {
        }
        return ParticleTypes.END_ROD;
    }

    private static DustParticleOptions dust(int color, float scale) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        return new DustParticleOptions(new Vector3f(red, green, blue), scale);
    }

    private static Anchor headAnchor(Player player, double standingOffsetY) {
        double heightDelta = player.getBbHeight() - STANDING_PLAYER_HEIGHT;
        double forwardOffset = player.getBbHeight() < 1.0F ? HORIZONTAL_HEAD_FORWARD : 0.0D;
        Vec3 look = player.getLookAngle();
        return new Anchor(
                player.getX() + look.x * forwardOffset,
                player.getY() + standingOffsetY + heightDelta + look.y * forwardOffset,
                player.getZ() + look.z * forwardOffset
        );
    }

    private static Anchor bodyAnchor(Player player, double standingOffsetY) {
        double heightDelta = (player.getBbHeight() - STANDING_PLAYER_HEIGHT) * 0.67D;
        return new Anchor(player.getX(), player.getY() + standingOffsetY + heightDelta, player.getZ());
    }

    private record Anchor(double x, double y, double z) {
    }

    private record CompanionParticles(ParticleOptions core, ParticleOptions orbit, ParticleOptions trail) {
    }

    private static final class FallingMeteor {
        private final Vec3 start;
        private final Vec3 impact;
        private final int duration;
        private int age;

        private FallingMeteor(Vec3 start, Vec3 impact, int duration) {
            this.start = start;
            this.impact = impact;
            this.duration = duration;
        }
    }

    private record EffectKey(UUID playerUuid, String cosmeticId) {
    }

    private static final class ParticleState {
        private double lastX;
        private double lastZ;
        private boolean wasOnGround;
        private boolean wasMoving;
        private int lastJumpBurstTick = -9999;
        private int lastDashBurstTick = -9999;
        private int nextMelodySoundTick;
        private int melodyStep;
        private int melodySemitone;
        private int nextMeteorTick;
        private final List<FallingMeteor> meteors = new ArrayList<>();
        private double companionX;
        private double companionY;
        private double companionZ;
        private boolean companionInitialized;
        private int companionRenderStep;

        private ParticleState(Player player) {
            update(player, false);
        }

        private void update(Player player, boolean moving) {
            lastX = player.getX();
            lastZ = player.getZ();
            wasOnGround = player.onGround();
            wasMoving = moving;
        }

        private void updateCompanion(Player player, CosmeticData cosmetic) {
            Anchor body = bodyAnchor(player, cosmetic.offsetY() + 0.34D);
            double yaw = Math.toRadians(player.getYRot());
            double backX = Math.sin(yaw);
            double backZ = -Math.cos(yaw);
            double rightX = Math.cos(yaw);
            double rightZ = Math.sin(yaw);
            double phase = player.tickCount * 0.085D + (cosmetic.id().hashCode() & 31);
            double lateral = COMPANION_LATERAL_DISTANCE + Math.sin(phase) * 0.045D;
            double targetX = body.x() + backX * COMPANION_REAR_OFFSET + rightX * lateral;
            double targetY = body.y() + Math.sin(phase * 1.35D) * 0.09D;
            double targetZ = body.z() + backZ * COMPANION_REAR_OFFSET + rightZ * lateral;
            double distanceSqr = distanceSqr(companionX, companionY, companionZ, targetX, targetY, targetZ);

            if (!companionInitialized || distanceSqr > 16.0D) {
                companionX = targetX;
                companionY = targetY;
                companionZ = targetZ;
                companionInitialized = true;
                return;
            }

            double followSpeed = 0.48D;
            companionX += (targetX - companionX) * followSpeed;
            companionY += (targetY - companionY) * followSpeed;
            companionZ += (targetZ - companionZ) * followSpeed;

            double rearDistance = (companionX - body.x()) * backX + (companionZ - body.z()) * backZ;
            if (rearDistance > COMPANION_MAX_REAR_DISTANCE) {
                double correction = rearDistance - COMPANION_MAX_REAR_DISTANCE;
                companionX -= backX * correction;
                companionZ -= backZ * correction;
            }
        }

        private static double distanceSqr(double x1, double y1, double z1, double x2, double y2, double z2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double dz = z2 - z1;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private enum MelodyInstrument {
        BIT,
        CHIME,
        BASS,
        BASEDRUM,
        SNARE,
        HAT
    }

    private record MelodyEvent(int semitones, int durationSteps) {
        private MelodyEvent {
            durationSteps = Math.max(1, durationSteps);
        }
    }

    private record MelodyStep(int semitones, boolean melodyAttack) {
    }
}
