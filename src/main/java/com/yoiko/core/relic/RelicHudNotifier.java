package com.yoiko.core.relic;

import com.yoiko.core.network.RelicEffectHudPayload;
import com.yoiko.core.network.RelicEffectHudPayload.DisplayKind;
import com.yoiko.core.network.RelicEffectHudPayload.ValueKind;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends compact relic feedback only after the server has committed the represented effect. */
public final class RelicHudNotifier {
    private static final int MOMENTARY_TICKS = 24;
    private static final int STATE_NOTICE_TICKS = 36;

    private RelicHudNotifier() {
    }

    public static void proc(ServerPlayer player, String effectKey, ValueKind valueKind, double value) {
        send(player, effectKey, DisplayKind.PROC, valueKind, value, 0, MOMENTARY_TICKS, 0, 0);
    }

    public static void active(
            ServerPlayer player, String effectKey, ValueKind valueKind, double value, int durationTicks
    ) {
        int duration = Math.max(1, durationTicks);
        send(player, effectKey, DisplayKind.ACTIVE, valueKind, value, duration, duration, 0, 0);
    }

    public static void charged(
            ServerPlayer player, String effectKey, ValueKind valueKind, double value, int durationTicks
    ) {
        int duration = Math.max(1, durationTicks);
        send(player, effectKey, DisplayKind.CHARGED, valueKind, value, duration, duration, 0, 0);
    }

    public static void marked(
            ServerPlayer player, String effectKey, ValueKind valueKind, double value, int durationTicks
    ) {
        int duration = Math.max(1, durationTicks);
        send(player, effectKey, DisplayKind.MARKED, valueKind, value, duration, duration, 0, 0);
    }

    public static void progress(
            ServerPlayer player, String effectKey, int progress, int maximum, int durationTicks
    ) {
        int duration = Math.max(1, durationTicks);
        send(player, effectKey, DisplayKind.PROGRESS, ValueKind.NONE, 0.0D,
                duration, duration, progress, maximum);
    }

    public static void ready(ServerPlayer player, String effectKey) {
        send(player, effectKey, DisplayKind.READY, ValueKind.NONE, 0.0D,
                0, STATE_NOTICE_TICKS, 0, 0);
    }

    public static void cooldown(ServerPlayer player, String effectKey, int cooldownTicks) {
        send(player, effectKey, DisplayKind.COOLDOWN, ValueKind.NONE, 0.0D,
                Math.max(1, cooldownTicks), STATE_NOTICE_TICKS, 0, 0);
    }

    private static void send(
            ServerPlayer player,
            String effectKey,
            DisplayKind displayKind,
            ValueKind valueKind,
            double value,
            int stateTicks,
            int displayTicks,
            int progress,
            int progressMaximum
    ) {
        if (player == null || player.hasDisconnected() || effectKey == null || effectKey.isBlank()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new RelicEffectHudPayload(
                effectKey,
                displayKind,
                valueKind,
                Double.isFinite(value) ? value : 0.0D,
                Math.clamp(stateTicks, 0, 72_000),
                Math.clamp(displayTicks, 1, 1_200),
                Math.max(0, progress),
                Math.max(0, progressMaximum)
        ));
    }
}
