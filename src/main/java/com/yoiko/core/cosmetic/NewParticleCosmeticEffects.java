package com.yoiko.core.cosmetic;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Distinct, short-lived geometry for the second particle-cosmetic collection.
 *
 * <p>Every dispatch is an exact cosmetic ID match. IDs remain identifiers and are never used to infer a
 * particle category. Shapes are sampled sparsely and stay attached to the current player/companion position,
 * so they do not create persistent world markers.</p>
 */
final class NewParticleCosmeticEffects {
    private static final double TAU = Math.PI * 2.0D;
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final double STANDING_PLAYER_HEIGHT = 1.8D;
    private static final double HORIZONTAL_HEAD_FORWARD = 0.42D;

    private static final Map<String, AnchoredEffect> TRAIL_EFFECTS = Map.ofEntries(
            Map.entry("prism_shards_trail", (level, player, cosmetic, basis, tick) -> spawnPrismTrail(level, basis, tick)),
            Map.entry("soda_pop_trail", (level, player, cosmetic, basis, tick) -> spawnSodaTrail(level, basis, tick)),
            Map.entry("mint_circuit_trail", (level, player, cosmetic, basis, tick) -> spawnCircuitTrail(level, basis, tick)),
            Map.entry("lightning_stitch_trail", (level, player, cosmetic, basis, tick) -> spawnStitchTrail(level, basis, tick)),
            Map.entry("candy_pixel_trail", (level, player, cosmetic, basis, tick) -> spawnCandyTrail(level, basis, tick)),
            Map.entry("koi_stream_trail", (level, player, cosmetic, basis, tick) -> spawnKoiTrail(level, basis, tick)),
            Map.entry("card_trick_trail", (level, player, cosmetic, basis, tick) -> spawnCardTrail(level, basis, tick))
    );
    private static final Map<String, Integer> TRAIL_BURST_COLORS = Map.of(
            "prism_shards_trail", 0x8EF4FF, "soda_pop_trail", 0xD8FBFF,
            "mint_circuit_trail", 0x70F0C0, "lightning_stitch_trail", 0xFFF38A,
            "candy_pixel_trail", 0xFF9ED8, "koi_stream_trail", 0xFF765F,
            "card_trick_trail", 0xD94A66
    );
    private static final Set<String> NEW_TRAILS = TRAIL_EFFECTS.keySet();
    private static final Set<String> ROTATION_ALIGNED_TRAILS = Set.of(
            "prism_shards_trail", "soda_pop_trail", "mint_circuit_trail",
            "lightning_stitch_trail", "candy_pixel_trail", "koi_stream_trail"
    );
    private static final Map<String, AnchoredEffect> RING_EFFECTS = Map.ofEntries(
            Map.entry("compass_ring", (level, player, cosmetic, basis, tick) -> spawnCompassRing(level, basis, tick)),
            Map.entry("pearl_tide_ring", (level, player, cosmetic, basis, tick) -> spawnPearlRing(level, basis, tick)),
            Map.entry("ribbon_knot_ring", (level, player, cosmetic, basis, tick) -> spawnRibbonBow(level, player, cosmetic.offsetY())),
            Map.entry("newbie_sprout_ring", (level, player, cosmetic, basis, tick) -> spawnNewbieSprout(level, player, cosmetic.offsetY())),
            Map.entry("devil_horns_ring", (level, player, cosmetic, basis, tick) -> spawnDevilHorns(level, player, cosmetic.offsetY())),
            Map.entry("clover_crest_ring", (level, player, cosmetic, basis, tick) -> spawnCloverRing(level, basis, tick)),
            Map.entry("honeydrop_ring", (level, player, cosmetic, basis, tick) -> spawnHoneyRing(level, basis, tick)),
            Map.entry("wave_meter_ring", (level, player, cosmetic, basis, tick) -> spawnWaveMeterRing(level, basis, tick)),
            Map.entry("marionette_stage_aura", (level, player, cosmetic, basis, tick) -> spawnMarionetteRing(level, basis, tick)),
            Map.entry("kaleidoscope_ring", (level, player, cosmetic, basis, tick) -> spawnKaleidoscopeRing(level, basis, tick)),
            Map.entry("libra_crown_ring", (level, player, cosmetic, basis, tick) -> spawnLibraRing(level, basis, tick)),
            Map.entry("lotus_mandala_ring", (level, player, cosmetic, basis, tick) -> spawnLotusRing(level, basis, tick))
    );
    private static final Map<String, AnchoredEffect> ORBIT_EFFECTS = Map.ofEntries(
            Map.entry("celestial_armillary_ring", (level, player, cosmetic, basis, tick) -> spawnArmillaryOrbit(level, basis, tick)),
            Map.entry("seal_charms_orbit", (level, player, cosmetic, basis, tick) -> spawnCharmsOrbit(level, basis, tick)),
            Map.entry("glass_baubles_orbit", (level, player, cosmetic, basis, tick) -> spawnBaublesOrbit(level, basis, tick)),
            Map.entry("crayon_bits_orbit", (level, player, cosmetic, basis, tick) -> spawnCrayonsOrbit(level, basis, tick)),
            Map.entry("card_dealer_orbit", (level, player, cosmetic, basis, tick) -> spawnCardDealerOrbit(level, basis, tick)),
            Map.entry("mobius_ribbon_orbit", (level, player, cosmetic, basis, tick) -> spawnMobiusOrbit(level, basis, tick)),
            Map.entry("puzzle_cube_orbit", (level, player, cosmetic, basis, tick) -> spawnPuzzleCubeOrbit(level, basis, tick))
    );
    private static final Map<String, AnchoredEffect> AURA_EFFECTS = Map.of(
            "soaplight_aura", (level, player, cosmetic, basis, tick) -> spawnSoapAura(level, basis, tick),
            "lucky_dice_aura", (level, player, cosmetic, basis, tick) -> spawnLuckyDiceAura(level, basis, tick)
    );
    private static final Map<String, CompanionEffect> COMPANION_EFFECTS = Map.of(
            "paper_crane_companion", (level, player, basis, tick, renderStep) -> spawnCraneCompanion(level, basis, tick),
            "jellyfish_companion", (level, player, basis, tick, renderStep) -> spawnJellyfishCompanion(level, basis, tick),
            "candle_companion", (level, player, basis, tick, renderStep) -> spawnCandleCompanion(level, basis, tick),
            "galaxy_whale_companion", (level, player, basis, tick, renderStep) -> spawnWhaleCompanion(level, basis, tick,
                    renderStep, player.getDeltaMovement().lengthSqr() > 0.001D),
            "butterfly_companion", (level, player, basis, tick, renderStep) -> spawnButterflyCompanion(level, basis, tick)
    );

    @FunctionalInterface
    private interface AnchoredEffect {
        void spawn(Level level, Player player, CosmeticData cosmetic, Basis basis, int tick);
    }

    @FunctionalInterface
    private interface CompanionEffect {
        void spawn(Level level, Player player, Basis basis, int tick, int renderStep);
    }

    private NewParticleCosmeticEffects() {
    }

    static boolean spawnTrail(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        AnchoredEffect effect = TRAIL_EFFECTS.get(id);
        if (effect == null) return false;
        Basis basis = id.equals("koi_stream_trail")
                ? rotationBasis(player, cosmetic.offsetY())
                : ROTATION_ALIGNED_TRAILS.contains(id)
                ? rotationTrailBasis(player, cosmetic.offsetY())
                : trailBasis(player, cosmetic.offsetY());
        effect.spawn(level, player, cosmetic, basis, player.tickCount);
        return true;
    }

    static boolean spawnTrailBurst(Level level, Player player, CosmeticData cosmetic, boolean jump) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        if (!NEW_TRAILS.contains(id)) {
            return false;
        }
        if (id.equals("card_trick_trail")) {
            if (jump) {
                spawnCardJumpBurst(level, player);
            }
            return true;
        }
        int color = TRAIL_BURST_COLORS.getOrDefault(id, 0x202126);
        double radius = jump ? 0.48D : 0.32D;
        double y = player.getY() + 0.09D;
        int points = jump ? 12 : 8;
        for (int i = 0; i < points; i++) {
            double angle = TAU * i / points;
            send(level, dust(i % 3 == 0 ? 0xFFFFFF : color, 0.58F),
                    new Vec3(player.getX() + Math.cos(angle) * radius, y,
                            player.getZ() + Math.sin(angle) * radius));
        }
        return true;
    }

    private static void spawnCardJumpBurst(Level level, Player player) {
        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {-1, 1}, {1, 1}, {-1, -1}, {1, -1}
        };
        Vec3 origin = new Vec3(player.getX(), player.getY() + 0.13D, player.getZ());
        ParticleOptions sealFace = dust(0xFFF3D5, 0.44F);
        ParticleOptions sealRed = dust(0xC9314E, 0.46F);
        ParticleOptions sealDark = dust(0x342B33, 0.46F);
        Vec3 sealNorth = origin.add(0.0D, 0.004D, -0.220D);
        Vec3 sealEast = origin.add(0.220D, 0.004D, 0.0D);
        Vec3 sealSouth = origin.add(0.0D, 0.004D, 0.220D);
        Vec3 sealWest = origin.add(-0.220D, 0.004D, 0.0D);
        polyline(level, sealFace, 2, sealNorth, sealEast, sealSouth, sealWest, sealNorth);
        line(level, sealRed, origin.add(0.0D, 0.008D, -0.105D),
                origin.add(0.105D, 0.008D, 0.0D), 1);
        line(level, sealDark, origin.add(0.105D, 0.008D, 0.0D),
                origin.add(0.0D, 0.008D, 0.105D), 1);
        line(level, sealRed, origin.add(0.0D, 0.008D, 0.105D),
                origin.add(-0.105D, 0.008D, 0.0D), 1);
        line(level, sealDark, origin.add(-0.105D, 0.008D, 0.0D),
                origin.add(0.0D, 0.008D, -0.105D), 1);
        send(level, sealFace, origin.add(0.0D, 0.012D, 0.0D));
        for (int card = 0; card < directions.length; card++) {
            Vec3 radial = new Vec3(directions[card][0], 0.0D, directions[card][1]).normalize();
            Vec3 tangent = new Vec3(-radial.z(), 0.0D, radial.x());
            Vec3 center = origin.add(radial.scale(0.68D));
            Vec3 velocity = radial.scale(0.125D).add(0.0D, 0.018D, 0.0D);
            ParticleOptions outline = dust(0x332A30, 0.50F);
            ParticleOptions face = dust((card & 1) == 0 ? 0xFFF7E1 : 0xF3E6C9, 0.44F);
            ParticleOptions mark = dust((card & 1) == 0 ? 0x25212B : 0xD83B55, 0.46F);
            rectangleMoving(level, outline, center, tangent, radial, 0.110D, 0.170D, 2, velocity);
            rectangleMoving(level, mark, center.add(0.0D, 0.004D, 0.0D),
                    tangent, radial, 0.076D, 0.128D, 1, velocity);
            for (int row = -1; row <= 1; row++) {
                for (int column = -1; column <= 1; column++) {
                    sendMoving(level, face, center.add(tangent.scale(column * 0.047D))
                            .add(radial.scale(row * 0.066D)).add(0.0D, 0.002D, 0.0D), velocity);
                }
            }
            sendMoving(level, mark, center, velocity);
            sendMoving(level, mark, center.add(radial.scale(0.078D)), velocity);
            sendMoving(level, mark, center.add(tangent.scale(0.045D)).add(radial.scale(-0.078D)), velocity);
        }
    }

    static boolean spawnRing(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        AnchoredEffect effect = RING_EFFECTS.get(id);
        if (effect == null) return false;
        Basis basis = headBasis(player, cosmetic.offsetY());
        effect.spawn(level, player, cosmetic, basis, player.tickCount);
        return true;
    }

    static boolean spawnOrbit(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        AnchoredEffect effect = ORBIT_EFFECTS.get(id);
        if (effect == null) return false;
        Basis basis = bodyBasis(player, cosmetic.offsetY());
        effect.spawn(level, player, cosmetic, basis, player.tickCount);
        return true;
    }

    static boolean spawnAura(Level level, Player player, CosmeticData cosmetic) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        AnchoredEffect effect = AURA_EFFECTS.get(id);
        if (effect == null) return false;
        Basis basis = bodyBasis(player, cosmetic.offsetY());
        effect.spawn(level, player, cosmetic, basis, player.tickCount);
        return true;
    }

    static boolean spawnCompanion(Level level, Player player, CosmeticData cosmetic,
                                  double x, double y, double z, int renderStep) {
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        CompanionEffect effect = COMPANION_EFFECTS.get(id);
        if (effect == null) return false;
        double yaw = Math.toRadians(player.yHeadRot);
        Basis basis = new Basis(x, y, z,
                Math.cos(yaw), Math.sin(yaw), -Math.sin(yaw), Math.cos(yaw));
        effect.spawn(level, player, basis, player.tickCount, renderStep);
        return true;
    }

    private static void spawnPrismTrail(Level level, Basis basis, int tick) {
        int[] colors = {0x79E9FF, 0xFF8ED6, 0xFFE98A};
        double spin = tick * 0.24D;
        for (int shard = 0; shard < 3; shard++) {
            double offset = (shard - 1) * 0.17D;
            double width = 0.075D + 0.018D * Math.cos(spin + shard);
            Vec3 top = basis.local(offset, 0.22D + shard * 0.035D, -shard * 0.06D);
            Vec3 left = basis.local(offset - width, 0.03D, -shard * 0.06D);
            Vec3 right = basis.local(offset + width, 0.03D, -shard * 0.06D);
            Vec3 ridge = basis.local(offset + Math.sin(spin + shard) * width * 0.45D,
                    0.105D, 0.045D - shard * 0.06D);
            ParticleOptions edge = dust(colors[Math.floorMod(tick / 3 + shard, colors.length)], 0.52F);
            polyline(level, edge, 2, top, left, right, top);
            line(level, dust(0xF5FFFF, 0.40F), top, ridge, 2);
            line(level, dust(colors[(shard + 1) % colors.length], 0.38F), ridge, left, 1);
            line(level, dust(colors[(shard + 2) % colors.length], 0.38F), ridge, right, 1);
        }
    }

    private static void spawnSodaTrail(Level level, Basis basis, int tick) {
        double phase = tick * 0.38D;
        for (int bubble = 0; bubble < 3; bubble++) {
            double progress = Math.floorMod(tick + bubble * 3, 10) / 9.0D;
            double radius = 0.045D + progress * 0.038D;
            Vec3 center = basis.local(Math.sin(phase + bubble * 1.9D) * (0.10D + progress * 0.08D),
                    0.02D + progress * 0.31D, -bubble * 0.045D);
            circlePlane(level, center, basis.right(), UP, radius, 7,
                    dust(bubble == 1 ? 0xFFB7DF : 0xBDEFFF, 0.38F));
            send(level, dust(0xFFFFFF, 0.42F), center.add(basis.right().scale(-radius * 0.42D))
                    .add(0.0D, radius * 0.45D, 0.0D));
        }
        if (tick % 10 == 9) {
            send(level, ParticleTypes.BUBBLE_POP, basis.local(Math.sin(phase) * 0.17D, 0.34D, 0.0D));
        }
    }

    private static void spawnCircuitTrail(Level level, Basis basis, int tick) {
        double pulse = Math.floorMod(tick, 12) / 11.0D;
        for (int side : new int[] {-1, 1}) {
            Vec3 contact = basis.local(side * 0.22D, 0.02D, -0.20D);
            Vec3 elbow = basis.local(side * 0.22D, 0.02D, -0.05D);
            Vec3 inner = basis.local(side * 0.08D, 0.02D, -0.05D);
            Vec3 center = basis.local(side * 0.08D, 0.02D, 0.10D);
            ParticleOptions trace = dust(side < 0 ? 0x67F0C0 : 0x8CFFE0, 0.43F);
            polyline(level, trace, 2, contact, elbow, inner, center);
            circleHorizontal(level, contact, 0.035D, 5, 0.0D, dust(0xDFFFF5, 0.40F));
        }
        Vec3 spark = basis.local(-0.08D + pulse * 0.16D, 0.035D, 0.10D);
        send(level, tick % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : dust(0xFFFFFF, 0.52F), spark);
    }

    private static void spawnStitchTrail(Level level, Basis basis, int tick) {
        Vec3[] zigzag = new Vec3[7];
        for (int i = 0; i < zigzag.length; i++) {
            zigzag[i] = basis.local((i & 1) == 0 ? -0.19D : 0.19D,
                    0.025D + (i % 3) * 0.018D, 0.18D - i * 0.065D);
        }
        for (int i = 0; i < zigzag.length - 1; i++) {
            line(level, dust((i + tick / 2) % 2 == 0 ? 0xFFF48B : 0xA8EEFF, 0.43F),
                    zigzag[i], zigzag[i + 1], 2);
            if (i > 0 && i < zigzag.length - 2) {
                line(level, dust(0xF5FFFF, 0.34F),
                        basis.local(-0.07D, 0.055D, 0.18D - i * 0.065D),
                        basis.local(0.07D, 0.055D, 0.18D - i * 0.065D), 1);
            }
        }
        send(level, ParticleTypes.ELECTRIC_SPARK, zigzag[Math.floorMod(tick, zigzag.length)]);
    }

    private static void spawnCandyTrail(Level level, Basis basis, int tick) {
        int[] colors = {0xFF91C8, 0xFFF08A, 0x82E8C8, 0xA8C8FF};
        Vec3 previous = null;
        for (int i = 0; i < 5; i++) {
            double bounce = Math.abs(Math.sin(tick * 0.35D + i * 0.82D));
            Vec3 pixel = basis.local((i - 2) * 0.095D, 0.035D + bounce * 0.17D, -i * 0.045D);
            send(level, dust(colors[i % colors.length], 0.62F), pixel);
            if (previous != null) {
                line(level, dust(0xFFF5D6, 0.30F), previous, pixel, 1);
            }
            previous = pixel;
        }
    }

    private static void spawnKoiTrail(Level level, Basis basis, int tick) {
        spawnKoiBowWake(level, basis, tick);
        for (int fish = 0; fish < 2; fish++) {
            double side = fish == 0 ? -1.0D : 1.0D;
            double phase = tick * 0.115D + fish * Math.PI;
            double travel = Math.sin(phase) * 0.235D;
            double swim = Math.sin(tick * 0.30D + fish * Math.PI) * 0.032D;
            Vec3 center = basis.local(travel, 0.085D + fish * 0.050D,
                    -0.52D + Math.cos(phase * 0.55D) * 0.045D);
            Vec3 forward = basis.forward();
            Vec3 lateral = basis.right();
            ParticleOptions body = dust(fish == 0 ? 0xEF594D : 0x4FA9D8, 0.36F);
            ParticleOptions fin = dust(fish == 0 ? 0xFF9A7A : 0xA8E9FF, 0.30F);

            Vec3 nose = center.add(forward.scale(0.220D));
            Vec3 upperNose = center.add(forward.scale(0.210D)).add(lateral.scale(0.022D));
            Vec3 upperHead = center.add(forward.scale(0.170D)).add(lateral.scale(0.044D));
            Vec3 upperShoulder = center.add(forward.scale(0.105D)).add(lateral.scale(0.058D));
            Vec3 upperBody = center.add(forward.scale(0.010D)).add(lateral.scale(0.067D));
            Vec3 upperRear = center.add(forward.scale(-0.105D)).add(lateral.scale(0.052D));
            Vec3 upperTailRoot = center.add(forward.scale(-0.180D)).add(lateral.scale(0.026D));
            Vec3 tailUpper = center.add(forward.scale(-0.290D))
                    .add(lateral.scale(0.072D + swim * 0.68D));
            Vec3 tailNotch = center.add(forward.scale(-0.245D)).add(lateral.scale(swim * 0.25D));
            Vec3 tailLower = center.add(forward.scale(-0.290D))
                    .add(lateral.scale(-0.072D + swim * 0.68D));
            Vec3 lowerTailRoot = center.add(forward.scale(-0.180D)).add(lateral.scale(-0.026D));
            Vec3 lowerRear = center.add(forward.scale(-0.105D)).add(lateral.scale(-0.052D));
            Vec3 lowerBody = center.add(forward.scale(0.010D)).add(lateral.scale(-0.067D));
            Vec3 lowerShoulder = center.add(forward.scale(0.105D)).add(lateral.scale(-0.058D));
            Vec3 lowerHead = center.add(forward.scale(0.170D)).add(lateral.scale(-0.044D));
            Vec3 lowerNose = center.add(forward.scale(0.210D)).add(lateral.scale(-0.022D));
            polyline(level, body, 1, nose, upperNose, upperHead, upperShoulder, upperBody, upperRear,
                    upperTailRoot, tailUpper, tailNotch, tailLower, lowerTailRoot, lowerRear,
                    lowerBody, lowerShoulder, lowerHead, lowerNose, nose);

            Vec3 leftFinRoot = center.add(forward.scale(-0.005D)).add(lateral.scale(0.054D));
            Vec3 rightFinRoot = center.add(forward.scale(-0.005D)).add(lateral.scale(-0.054D));
            triangle(level, fin, leftFinRoot,
                    center.add(forward.scale(-0.065D)).add(lateral.scale(0.135D)),
                    center.add(forward.scale(0.052D)).add(lateral.scale(0.076D)), 1);
            triangle(level, fin, rightFinRoot,
                    center.add(forward.scale(-0.065D)).add(lateral.scale(-0.135D)),
                    center.add(forward.scale(0.052D)).add(lateral.scale(-0.076D)), 1);

            Vec3 firstPatch = center.add(forward.scale(0.055D)).add(0.0D, 0.012D, 0.0D);
            Vec3 secondPatch = center.add(forward.scale(-0.075D)).add(lateral.scale(side * 0.025D))
                    .add(0.0D, 0.012D, 0.0D);
            circlePlane(level, firstPatch, lateral, forward, 0.034D, 6, dust(0xF7F4E8, 0.28F));
            circlePlane(level, secondPatch, lateral, forward, 0.030D, 6,
                    dust(fish == 0 ? 0xFFF2DD : 0xE8F8FF, 0.27F));
            line(level, dust(0xF5FAFF, 0.24F),
                    center.add(forward.scale(0.030D)).add(lateral.scale(-0.054D)),
                    center.add(forward.scale(-0.090D)).add(lateral.scale(0.050D)), 1);

            for (int eyeSide : new int[] {-1, 1}) {
                Vec3 eye = center.add(forward.scale(0.145D))
                        .add(lateral.scale(eyeSide * 0.031D)).add(0.0D, 0.021D, 0.0D);
                send(level, dust(0xFFFFFF, 0.34F), eye);
                send(level, dust(0x202938, 0.25F), eye.add(0.0D, 0.005D, 0.0D));
            }
            send(level, dust(0xDDF8FF, 0.30F), nose.add(0.0D, 0.018D, 0.0D));
        }
    }

    private static void spawnKoiBowWake(Level level, Basis basis, int tick) {
        for (int stage = 0; stage < 3; stage++) {
            double progress = Math.floorMod(tick + stage * 4, 12) / 11.0D;
            double forwardOffset = 0.24D + progress * 0.62D;
            double halfWidth = 0.08D + progress * 0.36D;
            double wobble = Math.sin(tick * 0.31D + stage * 1.7D) * 0.014D;
            for (int side : new int[] {-1, 1}) {
                Vec3 position = basis.local(side * (halfWidth + wobble),
                        -0.16D, forwardOffset);
                Vec3 velocity = basis.forward().scale(0.018D + progress * 0.012D)
                        .add(basis.right().scale(side * (0.018D + progress * 0.024D)))
                        .add(0.0D, 0.030D + progress * 0.018D, 0.0D);
                sendMoving(level, ParticleTypes.SPLASH, position, velocity);
            }
        }
        if ((tick & 1) == 0) {
            sendMoving(level, ParticleTypes.SPLASH, basis.local(0.0D, -0.15D, 0.22D),
                    basis.forward().scale(0.022D).add(0.0D, 0.050D, 0.0D));
        }
    }

    private static void spawnCardTrail(Level level, Basis basis, int tick) {
        Vec3 origin = basis.local(0.0D, 0.055D, 0.10D);
        int color = (tick & 1) == 0 ? 0xB52342 : 0x25212B;
        send(level, dust(color, 0.46F), origin);
        double side = Math.sin(tick * 0.61D) * 0.022D;
        Vec3 velocity = basis.forward().scale(-0.040D)
                .add(basis.right().scale(side))
                .add(0.0D, 0.008D, 0.0D);
        sendMoving(level, dust(color, 0.36F), origin, velocity);
        if (tick % 3 == 0) {
            int secondColor = color == 0xB52342 ? 0x25212B : 0xB52342;
            sendMoving(level, dust(secondColor, 0.32F),
                    origin.add(basis.right().scale(-side * 1.6D)),
                    basis.forward().scale(-0.031D)
                            .add(basis.right().scale(-side))
                            .add(0.0D, 0.012D, 0.0D));
        }
    }

    private static void spawnCompassRing(Level level, Basis basis, int tick) {
        double phase = tick * 0.018D;
        circleHorizontal(level, basis.origin(), 0.32D, 20, phase, dust(0xD8C27A, 0.44F));
        Vec3 north = basis.origin().add(Math.cos(phase) * 0.27D, 0.02D, Math.sin(phase) * 0.27D);
        Vec3 south = basis.origin().add(-Math.cos(phase) * 0.20D, 0.02D, -Math.sin(phase) * 0.20D);
        Vec3 tangent = new Vec3(-Math.sin(phase), 0.0D, Math.cos(phase));
        triangle(level, dust(0xE45A5A, 0.54F), north, basis.origin().add(tangent.scale(0.055D)),
                basis.origin().add(tangent.scale(-0.055D)), 2);
        triangle(level, dust(0xE8EEF4, 0.48F), south, basis.origin().add(tangent.scale(0.045D)),
                basis.origin().add(tangent.scale(-0.045D)), 2);
        send(level, dust(0xFFF3B0, 0.62F), basis.origin().add(0.0D, 0.025D, 0.0D));
    }

    private static void spawnPearlRing(Level level, Basis basis, int tick) {
        double phase = tick * 0.018D;
        for (int i = 0; i < 8; i++) {
            double angle = phase + TAU * i / 8.0D;
            double y = Math.sin(angle * 2.0D + tick * 0.06D) * 0.07D;
            send(level, dust(i % 2 == 0 ? 0xFFF9E8 : 0xDDEEFF, 0.74F),
                    basis.origin().add(Math.cos(angle) * 0.31D, y, Math.sin(angle) * 0.31D));
            send(level, dust(0xFFFFFF, 0.32F), basis.origin().add(Math.cos(angle) * 0.31D - 0.012D,
                    y + 0.018D, Math.sin(angle) * 0.31D - 0.012D));
        }
        circleHorizontal(level, basis.origin(), 0.31D, 16, phase, dust(0x9DDFF0, 0.28F));
    }

    private static void spawnRibbonBow(Level level, Player player, double offsetY) {
        FacingBasis head = headFacingBasis(player, offsetY);
        Vec3 knot = head.origin().subtract(head.forward().scale(0.31D))
                .subtract(head.up().scale(0.310D));
        ParticleOptions leftColor = dust(0xFFD0E2, 0.44F);
        ParticleOptions rightColor = dust(0xFF6EA8, 0.46F);

        Vec3 leftInner = knot.add(head.right().scale(-0.045D));
        Vec3 leftTop = knot.add(head.right().scale(-0.18D)).add(head.up().scale(0.13D));
        Vec3 leftOuter = knot.add(head.right().scale(-0.29D)).add(head.up().scale(0.015D));
        Vec3 leftBottom = knot.add(head.right().scale(-0.18D)).add(head.up().scale(-0.12D));
        polyline(level, leftColor, 2, leftInner, leftTop, leftOuter, leftBottom, leftInner);

        Vec3 rightInner = knot.add(head.right().scale(0.045D));
        Vec3 rightTop = knot.add(head.right().scale(0.18D)).add(head.up().scale(0.13D));
        Vec3 rightOuter = knot.add(head.right().scale(0.29D)).add(head.up().scale(0.015D));
        Vec3 rightBottom = knot.add(head.right().scale(0.18D)).add(head.up().scale(-0.12D));
        polyline(level, rightColor, 2, rightInner, rightTop, rightOuter, rightBottom, rightInner);

        Vec3 leftTailMid = knot.add(head.right().scale(-0.10D)).add(head.up().scale(-0.20D));
        Vec3 leftTailOuter = knot.add(head.right().scale(-0.18D)).add(head.up().scale(-0.36D));
        Vec3 leftTailInner = knot.add(head.right().scale(-0.055D)).add(head.up().scale(-0.32D));
        triangle(level, leftColor, knot, leftTailMid, leftTailOuter, 2);
        line(level, leftColor, leftTailOuter, leftTailInner, 1);
        line(level, leftColor, leftTailInner, knot, 2);

        Vec3 rightTailMid = knot.add(head.right().scale(0.10D)).add(head.up().scale(-0.20D));
        Vec3 rightTailOuter = knot.add(head.right().scale(0.18D)).add(head.up().scale(-0.36D));
        Vec3 rightTailInner = knot.add(head.right().scale(0.055D)).add(head.up().scale(-0.32D));
        triangle(level, rightColor, knot, rightTailMid, rightTailOuter, 2);
        line(level, rightColor, rightTailOuter, rightTailInner, 1);
        line(level, rightColor, rightTailInner, knot, 2);

        circlePlane(level, knot, head.right(), head.up(), 0.055D, 8, dust(0xFFF1F6, 0.52F));
    }

    private static void spawnNewbieSprout(Level level, Player player, double offsetY) {
        FacingBasis head = headFacingBasis(player, offsetY);
        Vec3 root = head.origin().subtract(head.up().scale(0.175D))
                .subtract(head.forward().scale(0.015D));
        Vec3 junction = root.add(head.up().scale(0.205D));
        ParticleOptions stem = dust(0x4E9D45, 0.30F);
        ParticleOptions leaf = dust(0x72D35B, 0.34F);
        ParticleOptions highlight = dust(0xC8F69A, 0.24F);

        line(level, stem, root, junction, 3);

        Vec3 leftBase = junction.add(head.right().scale(-0.014D)).add(head.up().scale(-0.005D));
        Vec3 leftUpper = junction.add(head.right().scale(-0.060D)).add(head.up().scale(0.050D));
        Vec3 leftTip = junction.add(head.right().scale(-0.145D)).add(head.up().scale(0.020D));
        Vec3 leftLower = junction.add(head.right().scale(-0.070D)).add(head.up().scale(-0.042D));
        polyline(level, leaf, 1, leftBase, leftUpper, leftTip, leftLower, leftBase);
        line(level, highlight, leftBase, leftTip, 1);

        Vec3 rightBase = junction.add(head.right().scale(0.014D)).add(head.up().scale(-0.005D));
        Vec3 rightUpper = junction.add(head.right().scale(0.060D)).add(head.up().scale(0.050D));
        Vec3 rightTip = junction.add(head.right().scale(0.145D)).add(head.up().scale(0.020D));
        Vec3 rightLower = junction.add(head.right().scale(0.070D)).add(head.up().scale(-0.042D));
        polyline(level, leaf, 1, rightBase, rightUpper, rightTip, rightLower, rightBase);
        line(level, highlight, rightBase, rightTip, 1);

        send(level, dust(0xE9FFD0, 0.30F), junction.add(head.up().scale(0.010D)));
    }

    private static void spawnDevilHorns(Level level, Player player, double offsetY) {
        FacingBasis head = headFacingBasis(player, offsetY);
        Vec3 center = head.origin().subtract(head.forward().scale(0.075D))
                .subtract(head.up().scale(0.270D));
        ParticleOptions outer = dust(0x351126, 0.62F);
        ParticleOptions inner = dust(0x9D2345, 0.50F);
        ParticleOptions ridge = dust(0xE4465F, 0.34F);

        for (int side : new int[] {-1, 1}) {
            Vec3 outerBase = center.add(head.right().scale(side * 0.230D));
            Vec3 innerBase = center.add(head.right().scale(side * 0.130D)).add(head.up().scale(0.010D));
            Vec3 outerMid = center.add(head.right().scale(side * 0.370D)).add(head.up().scale(0.220D));
            Vec3 innerMid = center.add(head.right().scale(side * 0.285D)).add(head.up().scale(0.205D));
            Vec3 tip = center.add(head.right().scale(side * 0.165D)).add(head.up().scale(0.455D))
                    .add(head.forward().scale(0.015D));

            quadraticCurve(level, outer, outerBase,
                    center.add(head.right().scale(side * 0.390D)).add(head.up().scale(0.055D)),
                    outerMid, 5);
            quadraticCurve(level, outer, outerMid,
                    center.add(head.right().scale(side * 0.335D)).add(head.up().scale(0.405D)),
                    tip, 5);
            quadraticCurve(level, inner, innerBase,
                    center.add(head.right().scale(side * 0.245D)).add(head.up().scale(0.075D)),
                    innerMid, 4);
            quadraticCurve(level, inner, innerMid,
                    center.add(head.right().scale(side * 0.235D)).add(head.up().scale(0.360D)),
                    tip, 4);

            Vec3 ridgeBase = outerBase.lerp(innerBase, 0.48D);
            Vec3 ridgeMid = outerMid.lerp(innerMid, 0.52D);
            quadraticCurve(level, ridge, ridgeBase,
                    center.add(head.right().scale(side * 0.315D)).add(head.up().scale(0.095D)),
                    ridgeMid, 3);
            quadraticCurve(level, ridge, ridgeMid,
                    center.add(head.right().scale(side * 0.275D)).add(head.up().scale(0.380D)),
                    tip, 3);

            line(level, outer, outerBase, innerBase, 3);
            send(level, dust(0xFF7885, 0.46F), tip);
        }
    }

    private static void spawnCloverRing(Level level, Basis basis, int tick) {
        double phase = tick * 0.010D;
        for (int leaf = 0; leaf < 4; leaf++) {
            double angle = phase + TAU * leaf / 4.0D;
            Vec3 root = radialPoint(basis.origin(), angle, 0.055D, 0.0D);
            Vec3 leftShoulder = radialPoint(basis.origin(), angle - 0.34D, 0.19D, 0.018D);
            Vec3 leftLobe = radialPoint(basis.origin(), angle - 0.20D, 0.32D, 0.025D);
            Vec3 notch = radialPoint(basis.origin(), angle, 0.255D, 0.020D);
            Vec3 rightLobe = radialPoint(basis.origin(), angle + 0.20D, 0.32D, 0.025D);
            Vec3 rightShoulder = radialPoint(basis.origin(), angle + 0.34D, 0.19D, 0.018D);
            ParticleOptions green = dust(leaf % 2 == 0 ? 0x66D47A : 0xA1EE9A, 0.45F);
            polyline(level, green, 2, root, leftShoulder, leftLobe, notch,
                    rightLobe, rightShoulder, root);
            line(level, dust(0xD8FFD4, 0.32F), root, notch, 2);
        }
        circleHorizontal(level, basis.origin(), 0.052D, 6, phase, dust(0xE7FFD9, 0.48F));
    }

    private static void spawnHoneyRing(Level level, Basis basis, int tick) {
        double phase = tick * 0.014D;
        for (int i = 0; i < 6; i++) {
            double angle = phase + TAU * i / 6.0D;
            Vec3 center = basis.origin().add(Math.cos(angle) * 0.30D, 0.0D, Math.sin(angle) * 0.30D);
            Vec3 drop = center.add(0.0D, -0.10D - 0.025D * (i & 1), 0.0D);
            line(level, dust(0xB97828, 0.38F), center, drop, 2);
            triangle(level, dust(0xFFCA52, 0.48F), drop.add(0.0D, 0.045D, 0.0D),
                    drop.add(-0.035D, -0.025D, 0.0D), drop.add(0.035D, -0.025D, 0.0D), 1);
            send(level, dust(0xFFF0A0, 0.38F), drop.add(-0.012D, 0.0D, -0.012D));
        }
        polygonHorizontal(level, basis.origin(), 0.30D, 6, phase, dust(0xB97828, 0.42F), 3);
    }

    private static void spawnWaveMeterRing(Level level, Basis basis, int tick) {
        double phase = tick * 0.017D;
        for (int i = 0; i < 12; i++) {
            double angle = phase + TAU * i / 12.0D;
            double height = 0.035D + (Math.sin(tick * 0.18D + i * 0.9D) + 1.0D) * 0.055D;
            Vec3 base = basis.origin().add(Math.cos(angle) * 0.30D, 0.0D, Math.sin(angle) * 0.30D);
            line(level, dust(i % 3 == 0 ? 0x83EEFF : 0xB3A1FF, 0.46F), base, base.add(0.0D, height, 0.0D), 2);
            send(level, dust(0xF4FFFF, 0.30F), base.add(0.0D, height, 0.0D));
        }
        circleHorizontal(level, basis.origin(), 0.30D, 18, phase, dust(0x6F8BC5, 0.28F));
    }

    private static void spawnArmillaryOrbit(Level level, Basis basis, int tick) {
        Vec3 center = basis.origin().add(0.0D, 0.08D, 0.0D);
        double phase = tick * 0.035D;
        double firstTilt = tick * 0.018D;
        double secondTilt = -tick * 0.014D;
        double thirdTurn = tick * 0.011D;
        Vec3 firstHorizontal = basis.right().scale(Math.cos(firstTilt))
                .add(UP.scale(Math.sin(firstTilt))).normalize();
        Vec3 firstVertical = basis.forward();
        Vec3 secondHorizontal = basis.right();
        Vec3 secondVertical = UP.scale(Math.cos(secondTilt))
                .add(basis.forward().scale(Math.sin(secondTilt))).normalize();
        Vec3 thirdHorizontal = basis.right().scale(Math.cos(thirdTurn))
                .add(basis.forward().scale(Math.sin(thirdTurn))).normalize();
        Vec3 thirdTangent = basis.right().scale(-Math.sin(thirdTurn))
                .add(basis.forward().scale(Math.cos(thirdTurn))).normalize();
        Vec3 thirdVertical = UP.scale(0.70D).add(thirdTangent.scale(0.714D)).normalize();

        circlePlanePhase(level, center, firstHorizontal, firstVertical, 0.72D, 20,
                phase, dust(0xF7D77A, 0.42F));
        circlePlanePhase(level, center, secondHorizontal, secondVertical, 0.78D, 20,
                -phase * 0.82D, dust(0x8DE8FF, 0.42F));
        circlePlanePhase(level, center, thirdHorizontal, thirdVertical, 0.75D, 20,
                phase * 1.14D + 0.65D, dust(0xD4A2FF, 0.42F));

        Vec3 equatorMarker = center.add(firstHorizontal.scale(Math.cos(phase) * 0.72D))
                .add(firstVertical.scale(Math.sin(phase) * 0.72D));
        Vec3 meridianMarker = center.add(secondHorizontal.scale(Math.cos(-phase * 0.82D) * 0.78D))
                .add(secondVertical.scale(Math.sin(-phase * 0.82D) * 0.78D));
        Vec3 eclipticMarker = center.add(thirdHorizontal.scale(
                        Math.cos(phase * 1.14D + 0.65D) * 0.75D))
                .add(thirdVertical.scale(Math.sin(phase * 1.14D + 0.65D) * 0.75D));
        send(level, dust(0xFFF1A8, 0.64F), equatorMarker);
        send(level, dust(0xE8FFFF, 0.64F), meridianMarker);
        send(level, dust(0xF3E5FF, 0.64F), eclipticMarker);

        line(level, dust(0xFFF3BC, 0.24F), center.add(0.0D, -0.82D, 0.0D),
                center.add(0.0D, 0.82D, 0.0D), 7);
    }

    private static void spawnKaleidoscopeRing(Level level, Basis basis, int tick) {
        double open = 0.18D + (Math.sin(tick * 0.09D) + 1.0D) * 0.055D;
        double phase = tick * 0.014D;
        int[] colors = {0xFF86C8, 0x7CE6E0, 0xA991FF};
        for (int triangle = 0; triangle < 6; triangle++) {
            double angle = phase + TAU * triangle / 6.0D;
            Vec3 inner = basis.origin().add(Math.cos(angle) * open, 0.0D, Math.sin(angle) * open);
            Vec3 left = basis.origin().add(Math.cos(angle - 0.32D) * 0.34D, 0.0D,
                    Math.sin(angle - 0.32D) * 0.34D);
            Vec3 right = basis.origin().add(Math.cos(angle + 0.32D) * 0.34D, 0.0D,
                    Math.sin(angle + 0.32D) * 0.34D);
            ParticleOptions particle = dust(colors[triangle % colors.length], 0.48F);
            line(level, particle, inner, left, 2);
            line(level, particle, inner, right, 2);
            line(level, dust(colors[(triangle + 1) % colors.length], 0.34F), left, right, 2);
            send(level, dust(0xFFFFFF, 0.32F), inner.lerp(left.lerp(right, 0.5D), 0.55D));
        }
        polygonHorizontal(level, basis.origin(), 0.34D, 6, phase, dust(0xF7E9FF, 0.28F), 2);
    }

    private static void spawnLibraRing(Level level, Basis basis, int tick) {
        double phase = tick * 0.012D;
        Vec3 axis = basis.origin().add(0.0D, 0.13D, 0.0D);
        Vec3 barDirection = new Vec3(Math.cos(phase), 0.0D, Math.sin(phase));
        line(level, dust(0xE8C875, 0.52F), axis.add(barDirection.scale(-0.28D)), axis.add(barDirection.scale(0.28D)), 6);
        line(level, dust(0xFFF0B0, 0.46F), basis.origin(), axis, 3);
        double tilt = Math.sin(tick * 0.08D) * 0.04D;
        for (int side : new int[] {-1, 1}) {
            Vec3 end = axis.add(barDirection.scale(side * 0.28D)).add(0.0D, side * tilt, 0.0D);
            Vec3 pan = end.add(0.0D, -0.12D, 0.0D);
            Vec3 tangent = new Vec3(-barDirection.z, 0.0D, barDirection.x);
            Vec3 panLeft = pan.add(tangent.scale(-0.085D));
            Vec3 panRight = pan.add(tangent.scale(0.085D));
            line(level, dust(0xE8C875, 0.38F), end, panLeft, 2);
            line(level, dust(0xE8C875, 0.38F), end, panRight, 2);
            arcPlane(level, pan, tangent, UP, 0.085D, Math.PI, TAU, 6, dust(0xFFF0B0, 0.44F));
        }
        triangle(level, dust(0xF4D982, 0.42F), basis.origin().add(0.0D, -0.02D, 0.0D),
                basis.origin().add(barDirection.scale(-0.09D)).add(0.0D, -0.12D, 0.0D),
                basis.origin().add(barDirection.scale(0.09D)).add(0.0D, -0.12D, 0.0D), 2);
    }

    private static void spawnLotusRing(Level level, Basis basis, int tick) {
        double phase = tick * 0.012D;
        double open = 0.12D + (Math.sin(tick * 0.07D) + 1.0D) * 0.035D;
        for (int layer = 0; layer < 2; layer++) {
            double radius = layer == 0 ? 0.22D : 0.32D;
            for (int petal = 0; petal < 8; petal++) {
                double angle = phase * (layer == 0 ? -1.0D : 1.0D) + TAU * petal / 8.0D;
                Vec3 root = basis.origin().add(Math.cos(angle) * (radius - open), 0.0D, Math.sin(angle) * (radius - open));
                Vec3 tip = basis.origin().add(Math.cos(angle) * radius, layer == 0 ? 0.08D : 0.02D,
                        Math.sin(angle) * radius);
                Vec3 tangent = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
                Vec3 left = root.lerp(tip, 0.58D).add(tangent.scale(open * 0.32D));
                Vec3 right = root.lerp(tip, 0.58D).add(tangent.scale(-open * 0.32D));
                ParticleOptions petalColor = dust(layer == 0 ? 0xFFF0F8 : 0xFF91C5, 0.42F);
                polyline(level, petalColor, 1, root, left, tip, right, root);
                line(level, dust(0xFFD8EA, 0.30F), root, tip, 1);
            }
        }
    }

    private static void spawnCharmsOrbit(Level level, Basis basis, int tick) {
        double phase = tick * 0.030D;
        for (int charm = 0; charm < 4; charm++) {
            double angle = phase + TAU * charm / 4.0D;
            Basis local = orbitBasis(basis, angle, 0.68D, Math.sin(angle) * 0.12D);
            Vec3 center = local.origin();
            rectangle(level, dust(0xF3D9A5, 0.44F), center, local.right(), Vec3.ZERO.add(0, 1, 0), 0.075D, 0.12D, 1);
            ParticleOptions seal = dust(charm % 2 == 0 ? 0xD44C4C : 0x5D68C8, 0.42F);
            rectangle(level, seal, center, local.right(), UP, 0.035D, 0.045D, 1);
            line(level, seal, center.add(local.right().scale(-0.055D)).add(0.0D, 0.075D, 0.0D),
                    center.add(local.right().scale(0.055D)).add(0.0D, -0.075D, 0.0D), 2);
            line(level, dust(0xF7EBCB, 0.32F), center.add(local.right().scale(-0.07D)),
                    center.add(local.right().scale(0.07D)), 2);
        }
    }

    private static void spawnBaublesOrbit(Level level, Basis basis, int tick) {
        double phase = tick * 0.028D;
        for (int orb = 0; orb < 4; orb++) {
            double angle = phase + TAU * orb / 4.0D;
            Vec3 center = basis.origin().add(Math.cos(angle) * 0.68D,
                    Math.sin(tick * 0.07D + orb) * 0.14D, Math.sin(angle) * 0.68D);
            Vec3 facing = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
            circlePlane(level, center, facing, UP, 0.105D, 10,
                    dust(orb % 2 == 0 ? 0xC8F7FF : 0xF2D8FF, 0.38F));
            arcPlane(level, center, facing, UP, 0.068D, -1.0D, 1.0D, 5, dust(0xFFFFFF, 0.30F));
            send(level, dust(0xFFFFFF, 0.66F), center.add(-Math.sin(angle) * 0.035D, 0.035D,
                    Math.cos(angle) * 0.035D));
        }
    }

    private static void spawnCrayonsOrbit(Level level, Basis basis, int tick) {
        int[] colors = {0xFF6B7A, 0xFFD45C, 0x62D6B3, 0x6EA8FF};
        double phase = tick * 0.033D;
        for (int i = 0; i < 4; i++) {
            double angle = phase + TAU * i / 4.0D;
            Basis local = orbitBasis(basis, angle, 0.66D, Math.sin(angle * 2.0D) * 0.13D);
            Vec3 a = local.local(0.0D, -0.13D, 0.0D);
            Vec3 b = local.local(0.0D, 0.09D, 0.0D);
            ParticleOptions wax = dust(colors[i], 0.50F);
            line(level, wax, a, b, 4);
            line(level, dust(0xF7E0B8, 0.40F), b, local.local(-0.045D, 0.15D, 0.0D), 2);
            line(level, dust(0xF7E0B8, 0.40F), b, local.local(0.045D, 0.15D, 0.0D), 2);
            send(level, wax, local.local(0.0D, 0.17D, 0.0D));
            line(level, dust(0xF5F1E8, 0.32F), local.local(-0.045D, -0.07D, 0.0D),
                    local.local(0.045D, -0.07D, 0.0D), 1);
        }
    }

    private static void spawnCardDealerOrbit(Level level, Basis basis, int tick) {
        double phase = tick * 0.026D;
        int activeCard = Math.floorMod(tick / 8, 5);
        circleHorizontal(level, basis.origin(), 0.70D, 15, phase,
                dust(0xE8D5AA, 0.22F));
        for (int card = 0; card < 5; card++) {
            double angle = phase + TAU * card / 5.0D;
            double height = Math.sin(tick * 0.055D + card * 1.35D) * 0.14D
                    + (card - 2) * 0.025D;
            Basis local = orbitBasis(basis, angle, 0.70D, height);
            double tilt = Math.sin(tick * 0.045D + card * 0.82D) * 0.20D
                    + (card - 2) * 0.045D;
            Vec3 horizontal = local.right().scale(Math.cos(tilt))
                    .add(UP.scale(Math.sin(tilt))).normalize();
            Vec3 vertical = UP.scale(Math.cos(tilt))
                    .add(local.right().scale(-Math.sin(tilt))).normalize();
            Vec3 center = local.origin();
            ParticleOptions face = dust(card == activeCard ? 0xFFFDF0
                    : (card & 1) == 0 ? 0xFFF7E1 : 0xF1E2C5, 0.44F);
            ParticleOptions inset = dust(0xD3B98E, 0.32F);

            rectangle(level, face, center, horizontal, vertical, 0.115D, 0.170D, 2);
            rectangle(level, inset, center.add(local.forward().scale(0.006D)),
                    horizontal, vertical, 0.083D, 0.132D, 1);
            for (int row = -1; row <= 1; row++) {
                for (int column = -1; column <= 1; column++) {
                    send(level, face, center.add(horizontal.scale(column * 0.048D))
                            .add(vertical.scale(row * 0.060D))
                            .add(local.forward().scale(0.009D)));
                }
            }

            ParticleOptions suit = dust(card < 2 ? 0xD5455D : 0x4D405C, 0.44F);
            Vec3 suitCenter = center.add(local.forward().scale(0.014D));
            spawnOrbitCardSuit(level, suitCenter, horizontal, vertical, card, suit);
            Vec3 firstAfterimage = center.add(local.right().scale(-0.135D));
            Vec3 secondAfterimage = center.add(local.right().scale(-0.225D));
            line(level, dust(0x70DFFF, 0.25F),
                    firstAfterimage.add(vertical.scale(-0.070D)),
                    firstAfterimage.add(vertical.scale(0.070D)), 1);
            send(level, dust(0xB0F3FF, 0.20F), secondAfterimage);
            if (card == activeCard) {
                send(level, dust(card < 2 ? 0xD5455D : 0x4D405C, 0.54F), center);
            }
        }
    }

    private static void spawnOrbitCardSuit(Level level, Vec3 center, Vec3 horizontal,
                                           Vec3 vertical, int suit, ParticleOptions particle) {
        double size = 0.050D;
        if (suit == 0) {
            Vec3 bottom = center.add(vertical.scale(-size));
            Vec3 left = center.add(horizontal.scale(-size));
            Vec3 leftTop = center.add(horizontal.scale(-size * 0.45D)).add(vertical.scale(size));
            Vec3 notch = center.add(vertical.scale(size * 0.45D));
            Vec3 rightTop = center.add(horizontal.scale(size * 0.45D)).add(vertical.scale(size));
            Vec3 right = center.add(horizontal.scale(size));
            polyline(level, particle, 1, bottom, left, leftTop, notch, rightTop, right, bottom);
        } else if (suit == 1) {
            polyline(level, particle, 1,
                    center.add(vertical.scale(-size)),
                    center.add(horizontal.scale(size)),
                    center.add(vertical.scale(size)),
                    center.add(horizontal.scale(-size)),
                    center.add(vertical.scale(-size)));
        } else if (suit == 2) {
            send(level, particle, center.add(vertical.scale(size * 0.45D)));
            send(level, particle, center.add(horizontal.scale(-size * 0.55D)));
            send(level, particle, center.add(horizontal.scale(size * 0.55D)));
            line(level, particle, center, center.add(vertical.scale(-size)), 1);
            line(level, particle, center.add(horizontal.scale(-size * 0.42D))
                    .add(vertical.scale(-size)), center.add(horizontal.scale(size * 0.42D))
                    .add(vertical.scale(-size)), 1);
        } else if (suit == 3) {
            triangle(level, particle, center.add(vertical.scale(size)),
                    center.add(horizontal.scale(-size * 0.80D)).add(vertical.scale(-size * 0.20D)),
                    center.add(horizontal.scale(size * 0.80D)).add(vertical.scale(-size * 0.20D)), 1);
            line(level, particle, center, center.add(vertical.scale(-size)), 1);
        } else {
            star(level, center, horizontal, vertical, size * 1.12D, particle);
        }
    }

    private static void spawnMobiusOrbit(Level level, Basis basis, int tick) {
        int segments = 28;
        double rotation = tick * 0.045D;
        double twistPhase = tick * 0.070D;
        int[] colors = {0x61F4E4, 0x6EA9FF, 0xB77CFF, 0xFF79D1};
        Vec3[] edgeA = new Vec3[segments + 1];
        Vec3[] edgeB = new Vec3[segments + 1];
        Vec3[] centers = new Vec3[segments + 1];

        for (int i = 0; i <= segments; i++) {
            double u = TAU * i / segments;
            double angle = u + rotation;
            Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            double radius = 0.61D + Math.sin(tick * 0.055D + u * 3.0D) * 0.035D;
            double verticalWave = Math.sin(u * 2.0D + tick * 0.075D) * 0.14D;
            Vec3 centerline = basis.origin().add(radial.scale(radius)).add(0.0D, verticalWave, 0.0D);
            double twist = u * 0.5D + twistPhase;
            Vec3 widthDirection = UP.scale(Math.cos(twist)).add(radial.scale(Math.sin(twist))).normalize();
            double halfWidth = 0.105D + Math.sin(tick * 0.090D + u * 2.0D) * 0.020D;

            centers[i] = centerline;
            edgeA[i] = centerline.add(widthDirection.scale(halfWidth));
            edgeB[i] = centerline.add(widthDirection.scale(-halfWidth));
            if (i > 0) {
                int colorIndex = Math.floorMod(i + tick / 4, colors.length);
                line(level, dust(colors[colorIndex], 0.38F), edgeA[i - 1], edgeA[i], 1);
                line(level, dust(colors[(colorIndex + 2) % colors.length], 0.38F),
                        edgeB[i - 1], edgeB[i], 1);
            }
            if (i < segments && i % 4 == 0) {
                line(level, dust(0xF6F3FF, 0.28F), edgeA[i], edgeB[i], 2);
            }
            if (i < segments && i % 7 == 0) {
                Vec3 spark = edgeA[i].lerp(edgeB[i], 0.5D);
                Vec3 velocity = basis.origin().subtract(spark).normalize().scale(0.025D);
                sendMoving(level, ParticleTypes.ENCHANT, spark, velocity);
            }
        }

        int pulse = Math.floorMod(tick, segments);
        int oppositePulse = (pulse + segments / 2) % segments;
        for (int index : new int[] {pulse, oppositePulse}) {
            double angle = TAU * index / segments + rotation;
            Vec3 tangent = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
            star(level, centers[index], tangent, UP, 0.080D, dust(0xFFFFFF, 0.64F));
            send(level, ParticleTypes.END_ROD, centers[index]);
        }
    }

    private static void spawnPuzzleCubeOrbit(Level level, Basis basis, int tick) {
        double phase = tick * 0.025D;
        Vec3 center = basis.origin().add(Math.cos(phase) * 0.64D, Math.sin(phase * 1.4D) * 0.12D,
                Math.sin(phase) * 0.64D);
        double size = 0.16D;
        int[] colors = {0xFF5D66, 0xFFD65D, 0x5DDC8A, 0x5D91FF, 0xEDEDF4, 0xB47CFF};
        Vec3[] corners = cubeCorners(center, size);
        int[][] edges = cubeEdges();
        int activeLayer = Math.floorMod(tick / 10, 3);
        for (int i = 0; i < edges.length; i++) {
            int colorIndex = Math.floorMod(i + activeLayer * 2, colors.length);
            line(level, dust(colors[colorIndex], 0.44F), corners[edges[i][0]], corners[edges[i][1]], 2);
        }
        for (int layer = -1; layer <= 1; layer++) {
            double offset = layer * size * 0.66D;
            line(level, dust(colors[Math.floorMod(activeLayer + layer + 3, colors.length)], 0.30F),
                    center.add(-size, offset, -size), center.add(size, offset, -size), 2);
            line(level, dust(colors[Math.floorMod(activeLayer + layer + 4, colors.length)], 0.30F),
                    center.add(size, -size, offset), center.add(size, size, offset), 2);
        }
    }

    private static void spawnSoapAura(Level level, Basis basis, int tick) {
        ParticleOptions[] colors = {dust(0x91EEFF, 0.38F), dust(0xFF9ED7, 0.38F), dust(0xFFF0A0, 0.38F)};
        double[] laneX = {-0.34D, -0.16D, 0.08D, 0.31D, -0.26D, 0.22D};
        double[] laneForward = {0.12D, -0.16D, 0.19D, -0.08D, -0.24D, 0.27D};
        int cycle = 56;
        for (int bubble = 0; bubble < laneX.length; bubble++) {
            int age = Math.floorMod(tick + bubble * 9, cycle);
            double progress = age / (cycle - 1.0D);
            double drift = Math.sin(progress * Math.PI * 2.0D + bubble * 0.85D) * 0.035D;
            double radius = 0.055D + Math.sin(progress * Math.PI) * 0.025D + (bubble & 1) * 0.008D;
            Vec3 center = basis.local(laneX[bubble] + drift,
                    -0.12D + progress * 1.28D, laneForward[bubble]);
            circlePlane(level, center, basis.right(), UP, radius, 8, colors[bubble % colors.length]);
            arcPlane(level, center, basis.right(), UP, radius * 0.68D, 1.8D, 3.0D, 4,
                    dust(0xFFFFFF, 0.32F));
            if (age >= cycle - 4) {
                send(level, ParticleTypes.BUBBLE_POP, center);
            }
        }
    }

    private static void spawnLuckyDiceAura(Level level, Basis basis, int tick) {
        int face = Math.floorMod(tick / 5, 6) + 1;
        double angle = tick * 0.03D;
        Basis local = orbitBasis(basis, angle, 0.45D, Math.sin(angle * 2.0D) * 0.25D);
        rectangle(level, dust(0xF5F2FF, 0.46F), local.origin(), local.right(), UP, 0.15D, 0.15D, 2);
        dicePips(level, local.origin(), local.right(), UP, 0.085D, face, dust(0x846FB5, 0.54F));
        Vec3 offset = local.forward().scale(0.075D);
        line(level, dust(0xB5A5D0, 0.30F), local.local(-0.15D, 0.15D, 0.0D).add(offset),
                local.local(0.15D, 0.15D, 0.0D).add(offset), 2);
        line(level, dust(0xB5A5D0, 0.30F), local.local(0.15D, -0.15D, 0.0D),
                local.local(0.15D, -0.15D, 0.0D).add(offset), 1);
    }

    private static void spawnMarionetteRing(Level level, Basis basis, int tick) {
        Vec3 controller = basis.origin().add(0.0D, 0.30D, 0.0D);
        ParticleOptions wood = dust(0xB78045, 0.52F);
        ParticleOptions edge = dust(0xE2BD76, 0.42F);
        ParticleOptions stringParticle = dust(0xF4F5FF, 0.38F);

        line(level, wood, controller.add(basis.right().scale(-0.46D)),
                controller.add(basis.right().scale(0.46D)), 8);
        line(level, wood, controller.add(basis.forward().scale(-0.22D)),
                controller.add(basis.forward().scale(0.22D)), 4);
        line(level, edge, controller.add(basis.right().scale(-0.30D)),
                controller.add(basis.right().scale(0.30D)), 5);
        circlePlane(level, controller, basis.right(), basis.forward(), 0.070D, 8,
                dust(0xFFF0B0, 0.48F));

        Vec3[] tops = {
                controller.add(basis.right().scale(-0.40D)),
                controller.add(basis.right().scale(-0.14D)).add(basis.forward().scale(0.08D)),
                controller.add(basis.right().scale(0.14D)).add(basis.forward().scale(0.08D)),
                controller.add(basis.right().scale(0.40D))
        };
        double sway = Math.sin(tick * 0.10D) * 0.018D;
        Vec3[] joints = {
                basis.local(-0.44D - sway, -1.27D, 0.02D),
                basis.local(-0.27D, -0.61D, 0.01D),
                basis.local(0.27D, -0.61D, 0.01D),
                basis.local(0.44D + sway, -1.27D, 0.02D)
        };
        for (int string = 0; string < tops.length; string++) {
            line(level, stringParticle, tops[string], joints[string], 8);
            Vec3 tensionPoint = tops[string].lerp(joints[string], 0.58D);
            send(level, dust(string == 0 || string == 3 ? 0xD9E5FF : 0xFFF0B0, 0.36F),
                    tensionPoint);
            send(level, dust(0xFFF8D0, 0.42F), joints[string]);
        }
    }

    private static void spawnCraneCompanion(Level level, Basis basis, int tick) {
        double flap = Math.sin(tick * 0.20D) * 0.10D;
        Vec3 body = basis.origin();
        Vec3 leftWing = body.add(basis.right().scale(-0.30D)).add(0.0D, flap, 0.0D);
        Vec3 rightWing = body.add(basis.right().scale(0.30D)).add(0.0D, flap, 0.0D);
        Vec3 chest = body.add(0.0D, -0.085D, 0.0D);
        triangle(level, dust(0xF7FBFF, 0.48F), body, leftWing, chest.add(basis.right().scale(-0.07D)), 2);
        triangle(level, dust(0xE2EEF5, 0.46F), body, rightWing, chest.add(basis.right().scale(0.07D)), 2);
        Vec3 neck = basis.local(0.0D, 0.13D, 0.10D);
        Vec3 beak = basis.local(0.0D, 0.12D, 0.25D);
        polyline(level, dust(0xF7FBFF, 0.46F), 2, chest, body, neck, beak);
        Vec3 tail = basis.local(0.0D, -0.04D, -0.22D);
        line(level, dust(0xD5E5EE, 0.42F), body, tail, 3);
        line(level, dust(0xD5E5EE, 0.34F), tail, basis.local(-0.07D, 0.01D, -0.14D), 1);
        line(level, dust(0xD5E5EE, 0.34F), tail, basis.local(0.07D, 0.01D, -0.14D), 1);
        send(level, dust(0x263246, 0.30F), neck.add(basis.right().scale(0.018D)));
    }

    private static void spawnJellyfishCompanion(Level level, Basis basis, int tick) {
        double pulse = 1.0D + Math.sin(tick * 0.18D) * 0.08D;
        Vec3 center = basis.origin().add(0.0D, 0.015D, 0.0D);
        double[] heights = {0.155D, 0.110D, 0.050D, -0.015D};
        double[] radii = {0.035D, 0.105D, 0.155D, 0.175D};
        for (int ring = 0; ring < heights.length; ring++) {
            ParticleOptions color = dust(ring % 2 == 0 ? 0x9BE9FF : 0xD4A7FF,
                    ring == heights.length - 1 ? 0.48F : 0.40F);
            circlePlane(level, center.add(0.0D, heights[ring], 0.0D),
                    basis.right(), basis.forward(), radii[ring] * pulse, ring == 0 ? 6 : 10, color);
        }
        send(level, dust(0xF2FFFF, 0.58F), center.add(0.0D, 0.165D, 0.0D));

        for (int tentacle = 0; tentacle < 6; tentacle++) {
            double angle = TAU * tentacle / 6.0D;
            Vec3 radial = basis.right().scale(Math.cos(angle)).add(basis.forward().scale(Math.sin(angle)));
            Vec3 tangent = basis.right().scale(-Math.sin(angle)).add(basis.forward().scale(Math.cos(angle)));
            Vec3 previous = center.add(radial.scale(0.135D)).add(0.0D, -0.025D, 0.0D);
            for (int segment = 1; segment <= 4; segment++) {
                double sway = Math.sin(tick * 0.15D + tentacle * 0.9D + segment * 0.75D) * 0.035D;
                Vec3 next = center.add(radial.scale(0.135D - segment * 0.008D))
                        .add(tangent.scale(sway))
                        .add(0.0D, -0.025D - segment * 0.070D, 0.0D);
                line(level, dust(tentacle % 2 == 0 ? 0xB9D8FF : 0xD4A7FF, 0.36F),
                        previous, next, 1);
                previous = next;
            }
        }
    }

    private static void spawnCandleCompanion(Level level, Basis basis, int tick) {
        ParticleOptions wax = dust(0xFFF0C9, 0.48F);
        ParticleOptions warmWax = dust(0xF3D79B, 0.40F);
        double radius = 0.055D;
        Vec3 bottom = basis.local(0.0D, -0.155D, 0.0D);
        Vec3 middle = basis.local(0.0D, -0.015D, 0.0D);
        Vec3 top = basis.local(0.0D, 0.135D, 0.0D);

        circlePlane(level, bottom, basis.right(), basis.forward(), radius, 10, warmWax);
        circlePlane(level, middle, basis.right(), basis.forward(), radius, 10, wax);
        circlePlane(level, top, basis.right(), basis.forward(), radius, 10, wax);
        for (int side : new int[] {-1, 1}) {
            line(level, wax, basis.local(side * radius, -0.155D, 0.0D),
                    basis.local(side * radius, 0.135D, 0.0D), 4);
            line(level, warmWax, basis.local(0.0D, -0.155D, side * radius),
                    basis.local(0.0D, 0.135D, side * radius), 4);
        }

        line(level, warmWax, basis.local(0.030D, 0.135D, 0.043D),
                basis.local(0.030D, 0.055D, 0.049D), 2);
        line(level, warmWax, basis.local(-0.018D, 0.135D, -0.052D),
                basis.local(-0.018D, 0.085D, -0.053D), 1);

        double flameLean = Math.sin(tick * 0.19D) * 0.018D;
        Vec3 flameBase = basis.local(0.0D, 0.145D, 0.0D);
        Vec3 flameLeft = basis.local(-0.038D + flameLean, 0.215D, 0.0D);
        Vec3 flameTip = basis.local(flameLean, 0.320D, 0.0D);
        Vec3 flameRight = basis.local(0.038D + flameLean, 0.215D, 0.0D);
        polyline(level, dust(0xFFB13B, 0.42F), 1,
                flameBase, flameLeft, flameTip, flameRight, flameBase);
        send(level, ParticleTypes.SMALL_FLAME, basis.local(flameLean, 0.225D, 0.0D));
        send(level, dust(0xFFF3A6, 0.50F), basis.local(flameLean, 0.240D, 0.0D));

        send(level, dust(0x5B4A42, 0.28F), basis.local(-0.021D, 0.015D, radius + 0.004D));
        send(level, dust(0x5B4A42, 0.28F), basis.local(0.021D, 0.015D, radius + 0.004D));
        arcPlane(level, basis.local(0.0D, -0.025D, radius + 0.006D),
                basis.right(), UP, 0.024D, Math.PI * 1.08D, Math.PI * 1.92D, 4,
                dust(0xB88968, 0.28F));
    }

    private static void spawnWhaleCompanion(Level level, Basis basis, int tick, int renderStep,
                                            boolean moving) {
        double sway = Math.sin(tick * 0.13D) * 0.025D;
        ParticleOptions body = dust(0x718FE8, 0.54F);
        double[] longitudinal = {-0.220D, -0.145D, -0.065D, 0.020D, 0.105D, 0.180D, 0.240D};
        double[] radii = {0.045D, 0.090D, 0.125D, 0.145D, 0.135D, 0.110D, 0.072D};
        Vec3 previousTop = null;
        Vec3 previousBottom = null;
        Vec3 previousLeft = null;
        Vec3 previousRight = null;
        for (int slice = 0; slice < longitudinal.length; slice++) {
            Vec3 center = basis.local(0.0D, sway, longitudinal[slice]);
            if ((slice + renderStep) % 2 == 0) {
                circlePlane(level, center, basis.right(), UP, radii[slice], 8,
                        slice % 2 == 0 ? body : dust(0x8FA7F5, 0.46F));
            }
            Vec3 top = center.add(0.0D, radii[slice], 0.0D);
            Vec3 bottom = center.add(0.0D, -radii[slice] * 0.72D, 0.0D);
            Vec3 left = center.add(basis.right().scale(-radii[slice]));
            Vec3 right = center.add(basis.right().scale(radii[slice]));
            if (previousTop != null) {
                line(level, body, previousTop, top, 1);
                line(level, dust(0x9FB4FF, 0.40F), previousBottom, bottom, 1);
                line(level, dust(0x718FE8, 0.36F), previousLeft, left, 1);
                line(level, dust(0x718FE8, 0.36F), previousRight, right, 1);
            }
            previousTop = top;
            previousBottom = bottom;
            previousLeft = left;
            previousRight = right;
        }

        Vec3 tailRoot = basis.local(0.0D, sway, -0.220D);
        Vec3 tailBack = basis.local(0.0D, sway, -0.350D);
        triangle(level, body, tailRoot, basis.local(-0.180D, 0.075D + sway, -0.390D), tailBack, 2);
        triangle(level, body, tailRoot, basis.local(0.180D, 0.075D + sway, -0.390D), tailBack, 2);
        triangle(level, dust(0x8FA7F5, 0.40F), basis.local(-0.100D, -0.030D + sway, 0.035D),
                basis.local(-0.235D, -0.145D + sway, -0.015D), basis.local(-0.075D, -0.070D + sway, 0.120D), 2);
        triangle(level, dust(0x8FA7F5, 0.40F), basis.local(0.100D, -0.030D + sway, 0.035D),
                basis.local(0.235D, -0.145D + sway, -0.015D), basis.local(0.075D, -0.070D + sway, 0.120D), 2);
        triangle(level, dust(0x9FB4FF, 0.38F), basis.local(0.0D, 0.100D + sway, 0.015D),
                basis.local(0.0D, 0.225D + sway, -0.055D), basis.local(0.0D, 0.105D + sway, -0.110D), 2);

        for (int side : new int[] {-1, 1}) {
            send(level, dust(0xFFFFFF, 0.50F), basis.local(side * 0.060D, 0.045D + sway, 0.245D));
        }
        for (int starPoint = 0; starPoint < 6; starPoint++) {
            if ((starPoint + renderStep) % 2 == 0) {
                double forward = -0.150D + starPoint * 0.065D;
                Vec3 point = basis.local(Math.sin(starPoint * 1.7D) * 0.055D,
                        sway + Math.cos(starPoint * 1.3D) * 0.050D, forward);
                star(level, point, basis.right(), UP, 0.030D,
                        dust(starPoint % 2 == 0 ? 0xF3E6FF : 0xB58CFF, 0.30F));
            }
        }
        if (moving) {
            int[] trailColors = {0x8B70FF, 0x5AA7FF, 0xE9E4FF};
            for (int trail = 0; trail < 3; trail++) {
                double age = Math.floorMod(tick + trail * 4, 14) / 13.0D;
                double spiral = tick * 0.21D + trail * 2.1D;
                Vec3 trailPoint = basis.local(
                        Math.cos(spiral) * (0.025D + age * 0.045D),
                        sway + Math.sin(spiral) * 0.040D + age * 0.055D,
                        -0.335D - age * 0.310D);
                send(level, dust(trailColors[trail], 0.30F + trail * 0.03F), trailPoint);
                if ((tick + trail * 2) % 6 == 0) {
                    star(level, trailPoint, basis.right(), UP, 0.024D,
                            dust(trail == 2 ? 0xFFFFFF : trailColors[trail], 0.25F));
                }
            }
        }
    }

    private static void spawnButterflyCompanion(Level level, Basis basis, int tick) {
        double flapProgress = (Math.sin(tick * 0.24D) + 1.0D) * 0.5D;
        double lift = flapProgress * 0.220D;
        double span = 0.350D - flapProgress * 0.070D;
        ParticleOptions upperWing = dust(0x76E6E2, 0.50F);
        ParticleOptions lowerWing = dust(0xFF91D1, 0.48F);
        ParticleOptions vein = dust(0xFFF0A0, 0.34F);

        Vec3 thorax = basis.origin();
        Vec3 head = basis.local(0.0D, 0.025D, 0.115D);
        Vec3 tail = basis.local(0.0D, -0.025D, -0.145D);
        line(level, dust(0x4E315F, 0.48F), tail, head, 4);
        circlePlane(level, thorax, basis.right(), UP, 0.045D, 7, dust(0x7C4A8F, 0.44F));
        circlePlane(level, head, basis.right(), UP, 0.040D, 6, dust(0xFFD97A, 0.44F));

        for (int side : new int[] {-1, 1}) {
            Vec3 root = basis.local(side * 0.035D, 0.0D, 0.0D);
            Vec3 mainTip = basis.local(side * span, lift, -0.010D);
            Vec3 upperTip = basis.local(side * (span * 0.88D), lift + 0.135D, 0.035D);
            Vec3 lowerTip = basis.local(side * (span * 0.82D), lift - 0.125D, -0.045D);
            triangle(level, upperWing, root, upperTip, mainTip, 2);
            triangle(level, lowerWing, root, mainTip, lowerTip, 2);
            line(level, vein, root, mainTip, 3);
            line(level, dust(0xE8FFFF, 0.30F), root.lerp(upperTip, 0.35D),
                    upperTip.lerp(mainTip, 0.48D), 1);
            line(level, dust(0xFFE1F2, 0.30F), root.lerp(lowerTip, 0.36D),
                    lowerTip.lerp(mainTip, 0.48D), 1);

            Vec3 antennaRoot = head.add(basis.right().scale(side * 0.020D));
            Vec3 antennaTip = basis.local(side * 0.090D, 0.105D, 0.205D);
            quadraticCurve(level, dust(0xFFF0A0, 0.34F), antennaRoot,
                    basis.local(side * 0.055D, 0.085D, 0.165D), antennaTip, 3);
            send(level, dust(0xFFF6C2, 0.46F), antennaTip);
        }
    }

    private static Basis trailBasis(Player player, double offsetY) {
        double forwardX = player.getDeltaMovement().x;
        double forwardZ = player.getDeltaMovement().z;
        double horizontalSpeed = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (horizontalSpeed > 1.0E-4D) {
            forwardX /= horizontalSpeed;
            forwardZ /= horizontalSpeed;
        } else {
            double yaw = Math.toRadians(player.yBodyRot);
            forwardX = -Math.sin(yaw);
            forwardZ = Math.cos(yaw);
        }
        double rightX = forwardZ;
        double rightZ = -forwardX;
        double heightDelta = (player.getBbHeight() - STANDING_PLAYER_HEIGHT) * 0.67D;
        Basis movement = new Basis(player.getX(), player.getY() + offsetY + heightDelta, player.getZ(),
                rightX, rightZ, forwardX, forwardZ);
        double pace = Math.floorMod(player.tickCount, 6) * 0.045D;
        return movement.shift(0.0D, 0.0D, -0.26D - pace);
    }

    private static Basis rotationTrailBasis(Player player, double offsetY) {
        Basis rotation = rotationBasis(player, offsetY);
        double pace = Math.floorMod(player.tickCount, 6) * 0.045D;
        return rotation.shift(0.0D, 0.0D, -0.26D - pace);
    }

    private static Basis rotationBasis(Player player, double offsetY) {
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = forwardZ;
        double rightZ = -forwardX;
        double heightDelta = (player.getBbHeight() - STANDING_PLAYER_HEIGHT) * 0.67D;
        Basis rotation = new Basis(player.getX(), player.getY() + offsetY + heightDelta, player.getZ(),
                rightX, rightZ, forwardX, forwardZ);
        return rotation;
    }

    private static Basis headBasis(Player player, double offsetY) {
        double heightDelta = player.getBbHeight() - STANDING_PLAYER_HEIGHT;
        double forwardOffset = player.getBbHeight() < 1.0F ? HORIZONTAL_HEAD_FORWARD : 0.0D;
        Vec3 look = player.getLookAngle();
        double yaw = Math.toRadians(player.yBodyRot);
        return new Basis(player.getX() + look.x * forwardOffset,
                player.getY() + offsetY + heightDelta + look.y * forwardOffset,
                player.getZ() + look.z * forwardOffset,
                Math.cos(yaw), Math.sin(yaw), -Math.sin(yaw), Math.cos(yaw));
    }

    private static FacingBasis headFacingBasis(Player player, double offsetY) {
        double heightDelta = player.getBbHeight() - STANDING_PLAYER_HEIGHT;
        double forwardOffset = player.getBbHeight() < 1.0F ? HORIZONTAL_HEAD_FORWARD : 0.0D;
        Vec3 forward = player.getLookAngle().normalize();
        double headYaw = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(Math.cos(headYaw), 0.0D, Math.sin(headYaw)).normalize();
        Vec3 up = forward.cross(right);
        if (up.lengthSqr() < 1.0E-6D) {
            up = UP;
        } else {
            up = up.normalize();
        }
        double neckHeight = 1.50D + heightDelta;
        double headOffset = Math.max(0.20D, offsetY - 1.50D);
        Vec3 neckPivot = new Vec3(player.getX(), player.getY() + neckHeight, player.getZ());
        Vec3 origin = neckPivot.add(up.scale(headOffset))
                .add(forward.scale(forwardOffset));
        return new FacingBasis(origin, right, up, forward);
    }

    private static Basis bodyBasis(Player player, double offsetY) {
        double heightDelta = (player.getBbHeight() - STANDING_PLAYER_HEIGHT) * 0.67D;
        double yaw = Math.toRadians(player.yBodyRot);
        return new Basis(player.getX(), player.getY() + offsetY + heightDelta, player.getZ(),
                Math.cos(yaw), Math.sin(yaw), -Math.sin(yaw), Math.cos(yaw));
    }

    private static Basis orbitBasis(Basis center, double angle, double radius, double y) {
        double x = center.x() + Math.cos(angle) * radius;
        double z = center.z() + Math.sin(angle) * radius;
        return new Basis(x, center.y() + y, z, -Math.sin(angle), Math.cos(angle),
                -Math.cos(angle), -Math.sin(angle));
    }

    private static void circleHorizontal(Level level, Vec3 center, double radius, int points,
                                         double phase, ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double angle = phase + TAU * i / points;
            send(level, particle, center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius));
        }
    }

    private static Vec3 radialPoint(Vec3 center, double angle, double radius, double y) {
        return center.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
    }

    private static void polygonHorizontal(Level level, Vec3 center, double radius, int sides,
                                          double phase, ParticleOptions particle, int steps) {
        Vec3 first = null;
        Vec3 previous = null;
        for (int i = 0; i < sides; i++) {
            double angle = phase + TAU * i / sides;
            Vec3 point = center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            if (first == null) {
                first = point;
            }
            if (previous != null) {
                line(level, particle, previous, point, steps);
            }
            previous = point;
        }
        if (previous != null && first != null) {
            line(level, particle, previous, first, steps);
        }
    }

    private static void circlePlane(Level level, Vec3 center, Vec3 horizontal, Vec3 vertical,
                                    double radius, int points, ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double angle = TAU * i / points;
            send(level, particle, center.add(horizontal.scale(Math.cos(angle) * radius))
                    .add(vertical.scale(Math.sin(angle) * radius)));
        }
    }

    private static void circlePlanePhase(Level level, Vec3 center, Vec3 horizontal, Vec3 vertical,
                                         double radius, int points, double phase, ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double angle = phase + TAU * i / points;
            send(level, particle, center.add(horizontal.scale(Math.cos(angle) * radius))
                    .add(vertical.scale(Math.sin(angle) * radius)));
        }
    }

    private static void arcPlane(Level level, Vec3 center, Vec3 horizontal, Vec3 vertical,
                                 double radius, double startAngle, double endAngle, int points,
                                 ParticleOptions particle) {
        int samples = Math.max(2, points);
        Vec3 previous = null;
        for (int i = 0; i < samples; i++) {
            double angle = startAngle + (endAngle - startAngle) * i / (samples - 1.0D);
            Vec3 point = center.add(horizontal.scale(Math.cos(angle) * radius))
                    .add(vertical.scale(Math.sin(angle) * radius));
            if (previous != null) {
                line(level, particle, previous, point, 1);
            }
            previous = point;
        }
    }

    private static void quadraticCurve(Level level, ParticleOptions particle,
                                       Vec3 from, Vec3 control, Vec3 to, int samples) {
        int count = Math.max(2, samples);
        for (int i = 0; i <= count; i++) {
            double t = i / (double) count;
            double inverse = 1.0D - t;
            send(level, particle, from.scale(inverse * inverse)
                    .add(control.scale(2.0D * inverse * t))
                    .add(to.scale(t * t)));
        }
    }

    private static void polyline(Level level, ParticleOptions particle, int steps, Vec3... points) {
        for (int i = 0; i < points.length - 1; i++) {
            line(level, particle, points[i], points[i + 1], steps);
        }
    }

    private static void triangle(Level level, ParticleOptions particle, Vec3 a, Vec3 b, Vec3 c, int steps) {
        polyline(level, particle, steps, a, b, c, a);
    }

    private static void dicePips(Level level, Vec3 center, Vec3 horizontal, Vec3 vertical,
                                 double spacing, int face, ParticleOptions particle) {
        int[][][] patterns = {
                {{0, 0}},
                {{-1, 1}, {1, -1}},
                {{-1, 1}, {0, 0}, {1, -1}},
                {{-1, 1}, {1, 1}, {-1, -1}, {1, -1}},
                {{-1, 1}, {1, 1}, {0, 0}, {-1, -1}, {1, -1}},
                {{-1, 1}, {1, 1}, {-1, 0}, {1, 0}, {-1, -1}, {1, -1}}
        };
        int safeFace = Math.clamp(face, 1, 6);
        for (int[] pip : patterns[safeFace - 1]) {
            send(level, particle, center.add(horizontal.scale(pip[0] * spacing))
                    .add(vertical.scale(pip[1] * spacing)));
        }
    }

    private static void rectangle(Level level, ParticleOptions particle, Vec3 center,
                                  Vec3 horizontal, Vec3 vertical, double halfWidth, double halfHeight, int steps) {
        Vec3 a = center.add(horizontal.scale(-halfWidth)).add(vertical.scale(-halfHeight));
        Vec3 b = center.add(horizontal.scale(halfWidth)).add(vertical.scale(-halfHeight));
        Vec3 c = center.add(horizontal.scale(halfWidth)).add(vertical.scale(halfHeight));
        Vec3 d = center.add(horizontal.scale(-halfWidth)).add(vertical.scale(halfHeight));
        line(level, particle, a, b, steps);
        line(level, particle, b, c, steps);
        line(level, particle, c, d, steps);
        line(level, particle, d, a, steps);
    }

    private static void rectangleMoving(Level level, ParticleOptions particle, Vec3 center,
                                        Vec3 horizontal, Vec3 vertical, double halfWidth, double halfHeight,
                                        int steps, Vec3 velocity) {
        Vec3 a = center.add(horizontal.scale(-halfWidth)).add(vertical.scale(-halfHeight));
        Vec3 b = center.add(horizontal.scale(halfWidth)).add(vertical.scale(-halfHeight));
        Vec3 c = center.add(horizontal.scale(halfWidth)).add(vertical.scale(halfHeight));
        Vec3 d = center.add(horizontal.scale(-halfWidth)).add(vertical.scale(halfHeight));
        movingLine(level, particle, a, b, steps, velocity);
        movingLine(level, particle, b, c, steps, velocity);
        movingLine(level, particle, c, d, steps, velocity);
        movingLine(level, particle, d, a, steps, velocity);
    }

    private static void movingLine(Level level, ParticleOptions particle, Vec3 from, Vec3 to,
                                   int steps, Vec3 velocity) {
        int samples = Math.max(1, steps);
        for (int i = 0; i <= samples; i++) {
            sendMoving(level, particle, from.lerp(to, i / (double) samples), velocity);
        }
    }

    private static void star(Level level, Vec3 center, Vec3 horizontal, Vec3 vertical,
                             double radius, ParticleOptions particle) {
        Vec3[] points = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double angle = -Math.PI / 2.0D + TAU * i / 5.0D;
            points[i] = center.add(horizontal.scale(Math.cos(angle) * radius))
                    .add(vertical.scale(Math.sin(angle) * radius));
        }
        for (int i = 0; i < 5; i++) {
            line(level, particle, points[i], points[(i + 2) % 5], 1);
        }
    }

    private static void line(Level level, ParticleOptions particle, Vec3 from, Vec3 to, int steps) {
        int samples = Math.max(1, steps);
        for (int i = 0; i <= samples; i++) {
            send(level, particle, from.lerp(to, i / (double) samples));
        }
    }

    private static Vec3[] cubeCorners(Vec3 center, double half) {
        return new Vec3[] {
                center.add(-half, -half, -half), center.add(half, -half, -half),
                center.add(half, half, -half), center.add(-half, half, -half),
                center.add(-half, -half, half), center.add(half, -half, half),
                center.add(half, half, half), center.add(-half, half, half)
        };
    }

    private static int[][] cubeEdges() {
        return new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}};
    }

    private static void send(Level level, ParticleOptions particle, Vec3 position) {
        ParticlePacketDispatcher.send(level, particle, position.x(), position.y(), position.z(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void sendMoving(Level level, ParticleOptions particle, Vec3 position, Vec3 velocity) {
        ParticlePacketDispatcher.send(level, particle, position.x(), position.y(), position.z(),
                0, velocity.x(), velocity.y(), velocity.z(), 1.0D);
    }

    private static DustParticleOptions dust(int color, float scale) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        return new DustParticleOptions(new Vector3f(red, green, blue), scale);
    }

    private record FacingBasis(Vec3 origin, Vec3 right, Vec3 up, Vec3 forward) {
    }

    private record Basis(double x, double y, double z,
                         double rightX, double rightZ, double forwardX, double forwardZ) {
        private Vec3 origin() {
            return new Vec3(x, y, z);
        }

        private Vec3 right() {
            return new Vec3(rightX, 0.0D, rightZ);
        }

        private Vec3 forward() {
            return new Vec3(forwardX, 0.0D, forwardZ);
        }

        private Vec3 local(double right, double up, double forward) {
            return new Vec3(x + rightX * right + forwardX * forward,
                    y + up,
                    z + rightZ * right + forwardZ * forward);
        }

        private Basis shift(double right, double up, double forward) {
            return new Basis(x + rightX * right + forwardX * forward, y + up,
                    z + rightZ * right + forwardZ * forward,
                    rightX, rightZ, forwardX, forwardZ);
        }
    }
}
