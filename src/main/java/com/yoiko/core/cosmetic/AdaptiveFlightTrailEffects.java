package com.yoiko.core.cosmetic;

import java.util.Locale;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Airborne variants for every normal TRAIL cosmetic. Motifs face the player's flight direction. */
final class AdaptiveFlightTrailEffects {
    private static final double TAU = Math.PI * 2.0D;
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final int[] RAINBOW = {
            0xFF9DA7, 0xFFD6A5, 0xFFF3A6, 0xB8F2C8, 0xA7E8F2, 0xC7B8FF, 0xFFB7E8
    };

    private AdaptiveFlightTrailEffects() {
    }

    static void tick(Level level, Player player, CosmeticData cosmetic, int density, boolean reduceAnimations) {
        if (!player.isFallFlying()) {
            return;
        }
        int stride = density == 1 ? 2 : 1;
        if (reduceAnimations) {
            stride *= 2;
        }
        int staggeredTick = player.tickCount + player.getId();
        if (Math.floorMod(staggeredTick, stride) != 0) {
            return;
        }
        int pulse = Math.floorDiv(staggeredTick, stride);
        Frame frame = frame(player);
        String id = cosmetic.id().toLowerCase(Locale.ROOT);
        emitWake(level, frame, cosmetic, id, pulse);

        int motifInterval = cosmetic.rarity() == CosmeticRarity.MYTHIC ? 10 : 14;
        if (Math.floorMod(pulse, motifInterval) == 0) {
            emitMotif(level, frame, id, pulse);
        }
    }

    private static Frame frame(Player player) {
        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.length();
        Vec3 forward = speed > 1.0E-4D ? velocity.scale(1.0D / speed) : player.getLookAngle().normalize();
        Vec3 reference = Math.abs(forward.dot(WORLD_UP)) > 0.92D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : WORLD_UP;
        Vec3 right = forward.cross(reference).normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 body = player.position().add(0.0D, player.getBbHeight() * 0.48D, 0.0D);
        double wakeDistance = 0.62D + Math.min(0.42D, speed * 0.15D);
        return new Frame(body, body.subtract(forward.scale(wakeDistance)), forward, right, up, speed);
    }

    private static void emitWake(Level level, Frame frame, CosmeticData cosmetic, String id, int pulse) {
        ParticleOptions primary = wakeParticle(cosmetic, id, pulse);
        Vec3 backward = frame.forward().scale(-0.018D - Math.min(0.018D, frame.speed() * 0.006D));
        double halfWidth = id.equals("koi_stream_trail") ? 0.27D : 0.18D;
        for (int side = -1; side <= 1; side += 2) {
            double wave = Math.sin(pulse * 0.62D + side * 1.4D) * 0.045D;
            Vec3 position = frame.wake().add(frame.right().scale(side * halfWidth)).add(frame.up().scale(wave));
            emit(level, primary, position,
                    backward.add(frame.right().scale(side * 0.005D)).add(frame.up().scale(wave * 0.08D)));
        }
        if (Math.floorMod(pulse, 3) == 0) {
            emitWakeAccent(level, frame, id, pulse, backward);
        }
    }

    private static ParticleOptions wakeParticle(CosmeticData cosmetic, String id, int pulse) {
        return switch (id) {
            case "rainbow_spark_trail" -> dust(RAINBOW[Math.floorMod(pulse, RAINBOW.length)], 0.48F);
            case "golden_afterglow_trail" -> dust(pulse % 2 == 0 ? 0xFFF3A0 : 0xFFD84A, 0.46F);
            case "arcana_trail" -> dust(pulse % 2 == 0 ? 0x72DFFF : 0xA66BFF, 0.46F);
            case "prism_shards_trail" -> dust(RAINBOW[Math.floorMod(pulse * 2, RAINBOW.length)], 0.44F);
            case "mint_circuit_trail" -> dust(0x70F0C0, 0.42F);
            case "lightning_stitch_trail" -> dust(pulse % 2 == 0 ? 0xFFF38A : 0xB9F4FF, 0.44F);
            case "candy_pixel_trail" -> dust(RAINBOW[Math.floorMod(pulse + 2, RAINBOW.length)], 0.44F);
            case "koi_stream_trail" -> dust(pulse % 2 == 0 ? 0xFF765F : 0x69B9FF, 0.46F);
            case "card_trick_trail" -> dust(pulse % 2 == 0 ? 0xD84242 : 0x25232B, 0.44F);
            default -> particle(cosmetic.particle());
        };
    }

    private static void emitWakeAccent(Level level, Frame frame, String id, int pulse, Vec3 backward) {
        ParticleOptions accent = switch (id) {
            case "aqua_drops_trail", "soda_pop_trail", "koi_stream_trail" -> ParticleTypes.BUBBLE_POP;
            case "sakura_shower_trail" -> ParticleTypes.CHERRY_LEAVES;
            case "black_haze_trail" -> ParticleTypes.LARGE_SMOKE;
            case "soulflame_trail" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "venom_mist_trail" -> ParticleTypes.WITCH;
            case "emerald_glow_trail" -> ParticleTypes.HAPPY_VILLAGER;
            case "ember_steps_trail" -> ParticleTypes.SMALL_FLAME;
            case "frost_trace_trail" -> ParticleTypes.SNOWFLAKE;
            case "mint_circuit_trail", "lightning_stitch_trail" -> ParticleTypes.ELECTRIC_SPARK;
            case "candy_pixel_trail" -> ParticleTypes.GLOW;
            default -> ParticleTypes.END_ROD;
        };
        double side = (pulse / 3 & 1) == 0 ? -0.08D : 0.08D;
        emit(level, accent, frame.wake().add(frame.right().scale(side)), backward.scale(1.25D));
    }

    private static void emitMotif(Level level, Frame frame, String id, int pulse) {
        Vec3 center = frame.body().subtract(frame.forward().scale(1.12D));
        switch (id) {
            case "aqua_drops_trail" -> waterMotif(level, frame, center);
            case "sakura_shower_trail" -> blossomMotif(level, frame, center);
            case "rainbow_spark_trail" -> rainbowMotif(level, frame, center);
            case "black_haze_trail" -> spiralMotif(level, frame, center, ParticleTypes.SMOKE, 0x5A5266);
            case "golden_afterglow_trail" -> starMotif(level, frame, center,
                    new ParticleOptions[]{dust(0xFFF3A0, 0.48F), dust(0xFFD84A, 0.46F)});
            case "melody_trail" -> melodyMotif(level, frame, center);
            case "soulflame_trail" -> flameMotif(level, frame, center, true);
            case "arcana_trail" -> magicCircleMotif(level, frame, center);
            case "venom_mist_trail" -> venomMotif(level, frame, center);
            case "emerald_glow_trail" -> diamondMotif(level, frame, center,
                    dust(0x65F28C, 0.48F), ParticleTypes.HAPPY_VILLAGER);
            case "stardust_steps_trail" -> starMotif(level, frame, center,
                    new ParticleOptions[]{ParticleTypes.END_ROD, dust(0xBCEBFF, 0.45F)});
            case "meteor_shower_trail" -> meteorMotif(level, frame, center);
            case "ember_steps_trail" -> flameMotif(level, frame, center, false);
            case "frost_trace_trail" -> snowflakeMotif(level, frame, center);
            case "wind_trace_trail" -> spiralMotif(level, frame, center,
                    particle("minecraft:small_gust"), 0xD9F8FF);
            case "sandstorm_trail" -> sandMotif(level, frame, center);
            case "prism_shards_trail" -> prismMotif(level, frame, center);
            case "soda_pop_trail" -> bubbleMotif(level, frame, center);
            case "mint_circuit_trail" -> circuitMotif(level, frame, center);
            case "lightning_stitch_trail" -> lightningMotif(level, frame, center);
            case "candy_pixel_trail" -> candyHeartMotif(level, frame, center);
            case "koi_stream_trail" -> koiMotif(level, frame, center, pulse);
            case "card_trick_trail" -> cardMotif(level, frame, center);
            default -> circle(level, frame, center, 0.50D, 14,
                    new ParticleOptions[]{ParticleTypes.END_ROD});
        }
    }

    private static void waterMotif(Level level, Frame frame, Vec3 center) {
        circle(level, frame, center, 0.48D, 16,
                new ParticleOptions[]{ParticleTypes.SPLASH, dust(0x8EEBFF, 0.40F)});
        circle(level, frame, center, 0.25D, 8,
                new ParticleOptions[]{ParticleTypes.BUBBLE_POP});
    }

    private static void blossomMotif(Level level, Frame frame, Vec3 center) {
        for (int index = 0; index < 35; index++) {
            double angle = TAU * index / 35.0D;
            double radius = 0.36D + 0.17D * Math.cos(5.0D * angle);
            point(level, frame, center, Math.cos(angle) * radius, Math.sin(angle) * radius,
                    index % 4 == 0 ? dust(0xFFD1E4, 0.42F) : ParticleTypes.CHERRY_LEAVES);
        }
    }

    private static void rainbowMotif(Level level, Frame frame, Vec3 center) {
        for (int index = 0; index < 21; index++) {
            double angle = TAU * index / 21.0D;
            point(level, frame, center, Math.cos(angle) * 0.55D, Math.sin(angle) * 0.55D,
                    dust(RAINBOW[index / 3 % RAINBOW.length], 0.46F));
        }
    }

    private static void spiralMotif(Level level, Frame frame, Vec3 center,
                                    ParticleOptions particle, int accentColor) {
        for (int index = 0; index < 22; index++) {
            double progress = index / 21.0D;
            double angle = progress * TAU * 1.75D;
            double radius = 0.12D + progress * 0.48D;
            point(level, frame, center, Math.cos(angle) * radius, Math.sin(angle) * radius,
                    index % 4 == 0 ? dust(accentColor, 0.40F) : particle);
        }
    }

    private static void starMotif(Level level, Frame frame, Vec3 center, ParticleOptions[] particles) {
        double[][] vertices = new double[10][2];
        for (int vertex = 0; vertex < vertices.length; vertex++) {
            double angle = -Math.PI * 0.5D + vertex * Math.PI / 5.0D;
            double radius = vertex % 2 == 0 ? 0.58D : 0.25D;
            vertices[vertex][0] = Math.cos(angle) * radius;
            vertices[vertex][1] = Math.sin(angle) * radius;
        }
        polyline(level, frame, center, vertices, true, 3, particles);
    }

    private static void melodyMotif(Level level, Frame frame, Vec3 center) {
        line(level, frame, center, 0.10D, -0.24D, 0.10D, 0.36D, 7,
                new ParticleOptions[]{ParticleTypes.NOTE, dust(0x8FD8FF, 0.40F)});
        line(level, frame, center, 0.10D, 0.36D, 0.39D, 0.20D, 5,
                new ParticleOptions[]{ParticleTypes.NOTE});
        circleAt(level, frame, center, 0.00D, -0.26D, 0.15D, 9,
                new ParticleOptions[]{ParticleTypes.NOTE, dust(0xC9A7FF, 0.40F)});
    }

    private static void flameMotif(Level level, Frame frame, Vec3 center, boolean soul) {
        double[][] flame = {
                {0.00D, 0.58D}, {-0.12D, 0.26D}, {-0.30D, 0.02D}, {-0.18D, -0.38D},
                {0.00D, -0.53D}, {0.20D, -0.34D}, {0.29D, 0.02D}, {0.12D, 0.23D}
        };
        ParticleOptions flameParticle = soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME;
        ParticleOptions glow = dust(soul ? 0x62F4E8 : 0xFFB14A, 0.44F);
        polyline(level, frame, center, flame, true, 3, new ParticleOptions[]{flameParticle, glow});
    }

    private static void magicCircleMotif(Level level, Frame frame, Vec3 center) {
        circle(level, frame, center, 0.58D, 20,
                new ParticleOptions[]{dust(0x72DFFF, 0.44F), ParticleTypes.ENCHANT});
        circle(level, frame, center, 0.30D, 12,
                new ParticleOptions[]{dust(0xC0F6FF, 0.40F)});
        double[][] hexagram = new double[6][2];
        for (int vertex = 0; vertex < 6; vertex++) {
            double angle = -Math.PI * 0.5D + vertex * TAU / 6.0D;
            hexagram[vertex][0] = Math.cos(angle) * 0.45D;
            hexagram[vertex][1] = Math.sin(angle) * 0.45D;
        }
        for (int vertex = 0; vertex < 6; vertex++) {
            int target = (vertex + 2) % 6;
            line(level, frame, center, hexagram[vertex][0], hexagram[vertex][1],
                    hexagram[target][0], hexagram[target][1], 3,
                    new ParticleOptions[]{dust(0xA66BFF, 0.40F)});
        }
    }

    private static void venomMotif(Level level, Frame frame, Vec3 center) {
        for (int lobe = 0; lobe < 3; lobe++) {
            double angle = -Math.PI * 0.5D + lobe * TAU / 3.0D;
            circleAt(level, frame, center, Math.cos(angle) * 0.24D, Math.sin(angle) * 0.24D,
                    0.23D, 9, new ParticleOptions[]{ParticleTypes.WITCH, dust(0x9B5CDB, 0.38F)});
        }
        circleAt(level, frame, center, 0.0D, 0.0D, 0.11D, 7,
                new ParticleOptions[]{dust(0x6CE27B, 0.40F)});
    }

    private static void diamondMotif(Level level, Frame frame, Vec3 center,
                                     ParticleOptions primary, ParticleOptions accent) {
        double[][] outer = {{0.0D, 0.58D}, {0.42D, 0.0D}, {0.0D, -0.58D}, {-0.42D, 0.0D}};
        double[][] inner = {{0.0D, 0.30D}, {0.22D, 0.0D}, {0.0D, -0.30D}, {-0.22D, 0.0D}};
        polyline(level, frame, center, outer, true, 4, new ParticleOptions[]{primary, accent});
        polyline(level, frame, center, inner, true, 3, new ParticleOptions[]{primary});
    }

    private static void meteorMotif(Level level, Frame frame, Vec3 center) {
        starMotif(level, frame, center.add(frame.up().scale(0.12D)),
                new ParticleOptions[]{ParticleTypes.END_ROD, dust(0x77CFFF, 0.42F)});
        for (int ray = -1; ray <= 1; ray++) {
            line(level, frame, center, ray * 0.10D, -0.05D,
                    ray * 0.22D, -0.68D, 5,
                    new ParticleOptions[]{dust(ray == 0 ? 0xEAFBFF : 0x397BFF, 0.38F)});
        }
    }

    private static void snowflakeMotif(Level level, Frame frame, Vec3 center) {
        for (int arm = 0; arm < 6; arm++) {
            double angle = arm * TAU / 6.0D;
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);
            line(level, frame, center, 0.0D, 0.0D, dx * 0.58D, dy * 0.58D, 5,
                    new ParticleOptions[]{ParticleTypes.SNOWFLAKE, dust(0xD8F7FF, 0.38F)});
            for (int branch = -1; branch <= 1; branch += 2) {
                double branchAngle = angle + branch * Math.PI / 5.0D;
                line(level, frame, center, dx * 0.34D, dy * 0.34D,
                        dx * 0.34D + Math.cos(branchAngle) * 0.18D,
                        dy * 0.34D + Math.sin(branchAngle) * 0.18D, 2,
                        new ParticleOptions[]{dust(0xA8E8FF, 0.36F)});
            }
        }
    }

    private static void sandMotif(Level level, Frame frame, Vec3 center) {
        ParticleOptions sand = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
        circle(level, frame, center, 0.52D, 16,
                new ParticleOptions[]{particle("minecraft:dust_plume"), sand});
        spiralMotif(level, frame, center, sand, 0xD9B879);
    }

    private static void prismMotif(Level level, Frame frame, Vec3 center) {
        double[][] triangle = {{0.0D, 0.58D}, {0.52D, -0.38D}, {-0.52D, -0.38D}};
        polyline(level, frame, center, triangle, true, 6,
                new ParticleOptions[]{dust(0x8EF4FF, 0.42F), dust(0xFF9ED8, 0.42F), dust(0xFFF38A, 0.42F)});
        line(level, frame, center, 0.0D, 0.58D, 0.0D, -0.38D, 5,
                new ParticleOptions[]{ParticleTypes.END_ROD});
    }

    private static void bubbleMotif(Level level, Frame frame, Vec3 center) {
        circleAt(level, frame, center, -0.25D, -0.06D, 0.26D, 10,
                new ParticleOptions[]{ParticleTypes.BUBBLE_POP, dust(0xD8FBFF, 0.36F)});
        circleAt(level, frame, center, 0.22D, 0.18D, 0.20D, 8,
                new ParticleOptions[]{ParticleTypes.BUBBLE_POP});
        circleAt(level, frame, center, 0.28D, -0.30D, 0.12D, 7,
                new ParticleOptions[]{dust(0xA7E8F2, 0.34F)});
    }

    private static void circuitMotif(Level level, Frame frame, Vec3 center) {
        double[][] circuit = {
                {-0.52D, 0.34D}, {-0.18D, 0.34D}, {-0.18D, 0.10D}, {0.22D, 0.10D},
                {0.22D, -0.20D}, {0.52D, -0.20D}
        };
        polyline(level, frame, center, circuit, false, 3,
                new ParticleOptions[]{dust(0x70F0C0, 0.40F), ParticleTypes.ELECTRIC_SPARK});
        circleAt(level, frame, center, -0.52D, 0.34D, 0.07D, 5,
                new ParticleOptions[]{dust(0xC8FFF0, 0.36F)});
        circleAt(level, frame, center, 0.52D, -0.20D, 0.07D, 5,
                new ParticleOptions[]{ParticleTypes.ELECTRIC_SPARK});
    }

    private static void lightningMotif(Level level, Frame frame, Vec3 center) {
        double[][] bolt = {
                {0.18D, 0.60D}, {-0.16D, 0.12D}, {0.08D, 0.12D},
                {-0.20D, -0.56D}, {0.26D, -0.04D}, {0.02D, -0.04D}
        };
        polyline(level, frame, center, bolt, false, 4,
                new ParticleOptions[]{dust(0xFFF38A, 0.44F), ParticleTypes.ELECTRIC_SPARK,
                        dust(0xB9F4FF, 0.38F)});
    }

    private static void candyHeartMotif(Level level, Frame frame, Vec3 center) {
        double[][] pixels = {
                {-0.42D, 0.28D}, {-0.22D, 0.45D}, {0.0D, 0.28D}, {0.22D, 0.45D},
                {0.42D, 0.28D}, {0.42D, 0.02D}, {0.22D, -0.20D}, {0.0D, -0.48D},
                {-0.22D, -0.20D}, {-0.42D, 0.02D}
        };
        for (int index = 0; index < pixels.length; index++) {
            point(level, frame, center, pixels[index][0], pixels[index][1],
                    dust(RAINBOW[index % RAINBOW.length], 0.48F));
        }
    }

    private static void koiMotif(Level level, Frame frame, Vec3 center, int pulse) {
        for (int fish = -1; fish <= 1; fish += 2) {
            int color = fish < 0 ? 0xFF765F : 0x69B9FF;
            for (int index = 0; index < 13; index++) {
                double angle = Math.PI * (index / 12.0D - 0.5D) + pulse * 0.035D;
                double x = fish * 0.14D + Math.cos(angle) * 0.34D;
                double y = fish * 0.13D + Math.sin(angle) * 0.34D;
                point(level, frame, center, x, y,
                        index % 4 == 0 ? ParticleTypes.SPLASH : dust(color, 0.40F));
            }
            point(level, frame, center, fish * 0.31D, fish * 0.13D,
                    dust(0xF4FDFF, 0.34F));
        }
    }

    private static void cardMotif(Level level, Frame frame, Vec3 center) {
        for (int card = -1; card <= 1; card++) {
            double rotation = card * 0.32D;
            double cx = card * 0.28D;
            double cy = Math.abs(card) * -0.06D;
            rectangle(level, frame, center, cx, cy, 0.24D, 0.38D, rotation,
                    new ParticleOptions[]{dust(0xF6E7C5, 0.38F),
                            dust(card == 0 ? 0xD84242 : 0x25232B, 0.38F)});
        }
        diamondMotif(level, frame, center, dust(0xD84242, 0.34F), ParticleTypes.ENCHANT);
    }

    private static void rectangle(Level level, Frame frame, Vec3 center, double cx, double cy,
                                  double width, double height, double rotation, ParticleOptions[] particles) {
        double[][] vertices = {
                {-width, height}, {width, height}, {width, -height}, {-width, -height}
        };
        for (double[] vertex : vertices) {
            double x = vertex[0];
            double y = vertex[1];
            vertex[0] = cx + x * Math.cos(rotation) - y * Math.sin(rotation);
            vertex[1] = cy + x * Math.sin(rotation) + y * Math.cos(rotation);
        }
        polyline(level, frame, center, vertices, true, 2, particles);
    }

    private static void circle(Level level, Frame frame, Vec3 center, double radius, int points,
                               ParticleOptions[] particles) {
        circleAt(level, frame, center, 0.0D, 0.0D, radius, points, particles);
    }

    private static void circleAt(Level level, Frame frame, Vec3 center, double cx, double cy,
                                 double radius, int points, ParticleOptions[] particles) {
        for (int index = 0; index < points; index++) {
            double angle = TAU * index / points;
            point(level, frame, center, cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius,
                    particles[index % particles.length]);
        }
    }

    private static void polyline(Level level, Frame frame, Vec3 center, double[][] vertices,
                                 boolean closed, int steps, ParticleOptions[] particles) {
        int segments = closed ? vertices.length : vertices.length - 1;
        for (int segment = 0; segment < segments; segment++) {
            double[] from = vertices[segment];
            double[] to = vertices[(segment + 1) % vertices.length];
            line(level, frame, center, from[0], from[1], to[0], to[1], steps, particles);
        }
    }

    private static void line(Level level, Frame frame, Vec3 center,
                             double fromX, double fromY, double toX, double toY,
                             int steps, ParticleOptions[] particles) {
        for (int step = 0; step < steps; step++) {
            double progress = steps == 1 ? 0.0D : step / (double) (steps - 1);
            point(level, frame, center,
                    fromX + (toX - fromX) * progress,
                    fromY + (toY - fromY) * progress,
                    particles[step % particles.length]);
        }
    }

    private static void point(Level level, Frame frame, Vec3 center,
                              double horizontal, double vertical, ParticleOptions particle) {
        Vec3 position = center.add(frame.right().scale(horizontal)).add(frame.up().scale(vertical));
        Vec3 radial = frame.right().scale(horizontal).add(frame.up().scale(vertical));
        Vec3 velocity = radial.lengthSqr() > 1.0E-6D
                ? radial.normalize().scale(0.008D).subtract(frame.forward().scale(0.004D))
                : frame.forward().scale(-0.004D);
        emit(level, particle, position, velocity);
    }

    private static void emit(Level level, ParticleOptions particle, Vec3 position, Vec3 velocity) {
        ParticlePacketDispatcher.send(level, particle, position.x(), position.y(), position.z(),
                0, velocity.x(), velocity.y(), velocity.z(), 1.0D);
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F
        ), scale);
    }

    private static SimpleParticleType particle(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return ParticleTypes.END_ROD;
        }
        var type = BuiltInRegistries.PARTICLE_TYPE.get(location);
        return type instanceof SimpleParticleType simple ? simple : ParticleTypes.END_ROD;
    }

    private record Frame(Vec3 body, Vec3 wake, Vec3 forward, Vec3 right, Vec3 up, double speed) {
    }
}
