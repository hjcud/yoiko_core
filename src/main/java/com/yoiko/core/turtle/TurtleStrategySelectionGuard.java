package com.yoiko.core.turtle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.InteractionHand;

/** Small deterministic one-shot ledger shared by the live service and its regression validator. */
final class TurtleStrategySelectionGuard {
    record Pending(UUID sessionId, InteractionHand hand, long expiresAt) { }

    private final Map<UUID, Pending> pendingByPlayer = new HashMap<>();

    UUID open(UUID playerId, InteractionHand hand, long expiresAt) {
        UUID sessionId=UUID.randomUUID();
        pendingByPlayer.put(playerId,new Pending(sessionId,hand,expiresAt));
        return sessionId;
    }

    Pending claim(UUID playerId, UUID sessionId, long now) {
        Pending pending=pendingByPlayer.remove(playerId);
        if(pending==null||!pending.sessionId().equals(sessionId)||pending.expiresAt()<now)return null;
        return pending;
    }

    boolean cancel(UUID playerId, UUID sessionId) {
        Pending pending=pendingByPlayer.get(playerId);
        if(pending==null||!pending.sessionId().equals(sessionId))return false;
        pendingByPlayer.remove(playerId);
        return true;
    }

    void clear(UUID playerId) {
        pendingByPlayer.remove(playerId);
    }
}
