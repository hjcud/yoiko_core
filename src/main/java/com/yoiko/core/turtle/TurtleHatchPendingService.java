package com.yoiko.core.turtle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative hatch-result gate. A ticket cannot be consumed again until
 * the client explicitly acknowledges the previous result.
 */
public final class TurtleHatchPendingService {
    private static final long CONFIRMATION_TIMEOUT_TICKS = 10L * 60L * 20L;
    private static final Map<UUID, PendingConfirmation> PENDING_BY_PLAYER = new HashMap<>();

    private record PendingConfirmation(UUID turtleId, long expiresAt) { }

    private TurtleHatchPendingService() {
    }

    public static void ensureReady(ServerPlayer player) {
        expire(player);
        if (PENDING_BY_PLAYER.containsKey(player.getUUID())) {
            throw TurtleLocalizedException.of("yoiko_core.turtle.error.confirm_previous_hatch");
        }
    }

    public static void awaitConfirmation(ServerPlayer player, UUID turtleId) {
        long expiresAt = (long) player.server.getTickCount() + CONFIRMATION_TIMEOUT_TICKS;
        PENDING_BY_PLAYER.put(player.getUUID(), new PendingConfirmation(turtleId, expiresAt));
    }

    public static void confirm(ServerPlayer player, UUID turtleId) {
        PENDING_BY_PLAYER.computeIfPresent(player.getUUID(), (playerId, pending) ->
                pending.turtleId().equals(turtleId) ? null : pending);
    }

    public static void tick(ServerPlayer player) {
        expire(player);
    }

    public static void clear(UUID playerId) {
        PENDING_BY_PLAYER.remove(playerId);
    }

    private static void expire(ServerPlayer player) {
        long now = player.server.getTickCount();
        PENDING_BY_PLAYER.computeIfPresent(player.getUUID(), (playerId, pending) ->
                pending.expiresAt() <= now ? null : pending);
    }
}
