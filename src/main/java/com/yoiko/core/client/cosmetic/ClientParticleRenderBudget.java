package com.yoiko.core.client.cosmetic;

import java.util.Set;
import java.util.UUID;

public final class ClientParticleRenderBudget {
    private static Set<UUID> allowedPlayers = Set.of();

    private ClientParticleRenderBudget() {
    }

    public static void replace(Set<UUID> playerUuids) {
        allowedPlayers = Set.copyOf(playerUuids);
    }

    public static boolean isAllowed(UUID playerUuid) {
        return allowedPlayers.contains(playerUuid);
    }

    public static void clear() {
        allowedPlayers = Set.of();
    }
}
