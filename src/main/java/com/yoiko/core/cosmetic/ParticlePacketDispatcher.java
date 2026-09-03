package com.yoiko.core.cosmetic;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Replays the authored particle call locally on the observing client.
 */
final class ParticlePacketDispatcher {
    private ParticlePacketDispatcher() {
    }

    static void send(Level level, ParticleOptions particle,
                     double x, double y, double z, int count,
                     double offsetX, double offsetY, double offsetZ, double speed) {
        if (!level.isClientSide) {
            return;
        }
        if (count <= 0) {
            level.addParticle(particle, x, y, z,
                    offsetX * speed, offsetY * speed, offsetZ * speed);
            return;
        }
        RandomSource random = level.getRandom();
        for (int index = 0; index < count; index++) {
            double particleX = x + (random.nextDouble() * 2.0D - 1.0D) * offsetX;
            double particleY = y + (random.nextDouble() * 2.0D - 1.0D) * offsetY;
            double particleZ = z + (random.nextDouble() * 2.0D - 1.0D) * offsetZ;
            level.addParticle(particle, particleX, particleY, particleZ,
                    random.nextGaussian() * speed,
                    random.nextGaussian() * speed,
                    random.nextGaussian() * speed);
        }
    }
}
