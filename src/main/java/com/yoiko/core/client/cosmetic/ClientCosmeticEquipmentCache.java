package com.yoiko.core.client.cosmetic;

import com.yoiko.core.network.CosmeticEquipmentSyncPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientCosmeticEquipmentCache {
    private static final Map<UUID, EquippedModels> EQUIPPED = new HashMap<>();

    private ClientCosmeticEquipmentCache() {
    }

    public static void update(CosmeticEquipmentSyncPayload payload) {
        if (!payload.head().present() && !payload.chest().present()) {
            EQUIPPED.remove(payload.playerUuid());
            return;
        }
        EQUIPPED.put(payload.playerUuid(), new EquippedModels(payload.head(), payload.chest()));
    }

    public static EquippedModels equipped(UUID playerUuid) {
        return EQUIPPED.getOrDefault(playerUuid, EquippedModels.EMPTY);
    }

    public static void clear() {
        EQUIPPED.clear();
    }

    public record EquippedModels(
            CosmeticEquipmentSyncPayload.ModelEntry head,
            CosmeticEquipmentSyncPayload.ModelEntry chest
    ) {
        public static final EquippedModels EMPTY = new EquippedModels(
                CosmeticEquipmentSyncPayload.ModelEntry.EMPTY,
                CosmeticEquipmentSyncPayload.ModelEntry.EMPTY
        );
    }
}
