package com.yoiko.core.client.cosmetic;

import com.yoiko.core.config.YoikoClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;

public final class YoikoParticleSettings {
    private YoikoParticleSettings() {
    }

    public static int density() {
        if (!YoikoClientConfig.FOLLOW_MINECRAFT_PARTICLES.get()) {
            return YoikoClientConfig.PARTICLE_DENSITY.get();
        }
        ParticleStatus status = Minecraft.getInstance().options.particles().get();
        return switch (status) {
            case ALL -> 3;
            case DECREASED -> 2;
            case MINIMAL -> 1;
        };
    }
}
