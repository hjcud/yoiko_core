package com.yoiko.core.client.cosmetic;

import com.yoiko.core.config.YoikoClientConfig;

/**
 * A small hysteresis controller for cosmetic particles. It sheds remote-player work first and
 * never completely disables the local player's equipped effect.
 */
public final class ClientParticlePerformanceController {
    private static final double LOW_FPS = 42.0D;
    private static final double RECOVERY_FPS = 58.0D;
    private static final long DEGRADE_AFTER_NANOS = 2_500_000_000L;
    private static final long RECOVER_AFTER_NANOS = 8_000_000_000L;
    private static final int MAX_PRESSURE = 4;

    private static long lastFrameNanos;
    private static long thresholdSinceNanos;
    private static boolean belowThreshold;
    private static double averageFrameNanos;
    private static int pressure;

    private ClientParticlePerformanceController() {
    }

    public static void recordFrame(long nowNanos) {
        if (!YoikoClientConfig.AUTO_ADJUST_PARTICLE_PERFORMANCE.get()) {
            reset();
            return;
        }
        if (lastFrameNanos == 0L) {
            lastFrameNanos = nowNanos;
            return;
        }
        long frameNanos = Math.max(1L, Math.min(250_000_000L, nowNanos - lastFrameNanos));
        lastFrameNanos = nowNanos;
        averageFrameNanos = averageFrameNanos == 0.0D
                ? frameNanos
                : averageFrameNanos * 0.95D + frameNanos * 0.05D;
        double fps = 1_000_000_000.0D / averageFrameNanos;
        if (fps < LOW_FPS) {
            trackThreshold(true, nowNanos, DEGRADE_AFTER_NANOS);
        } else if (fps > RECOVERY_FPS) {
            trackThreshold(false, nowNanos, RECOVER_AFTER_NANOS);
        } else {
            thresholdSinceNanos = 0L;
        }
    }

    private static void trackThreshold(boolean low, long nowNanos, long requiredNanos) {
        if (thresholdSinceNanos == 0L || belowThreshold != low) {
            belowThreshold = low;
            thresholdSinceNanos = nowNanos;
            return;
        }
        if (nowNanos - thresholdSinceNanos < requiredNanos) {
            return;
        }
        pressure = Math.max(0, Math.min(MAX_PRESSURE, pressure + (low ? 1 : -1)));
        thresholdSinceNanos = nowNanos;
    }

    public static int effectiveOtherPlayerLimit(int configuredLimit) {
        if (!YoikoClientConfig.AUTO_ADJUST_PARTICLE_PERFORMANCE.get() || pressure == 0) {
            return configuredLimit;
        }
        return switch (pressure) {
            case 1 -> Math.max(1, configuredLimit * 3 / 4);
            case 2 -> Math.max(1, configuredLimit / 2);
            case 3 -> Math.max(1, configuredLimit / 4);
            default -> 0;
        };
    }

    public static int effectiveDensity(int configuredDensity) {
        if (configuredDensity <= 0) {
            return 0;
        }
        if (!YoikoClientConfig.AUTO_ADJUST_PARTICLE_PERFORMANCE.get()) {
            return configuredDensity;
        }
        int reduction = pressure >= 4 ? 2 : pressure >= 2 ? 1 : 0;
        return Math.max(1, configuredDensity - reduction);
    }

    public static int pressure() {
        return pressure;
    }

    public static void reset() {
        lastFrameNanos = 0L;
        thresholdSinceNanos = 0L;
        belowThreshold = false;
        averageFrameNanos = 0.0D;
        pressure = 0;
    }
}
