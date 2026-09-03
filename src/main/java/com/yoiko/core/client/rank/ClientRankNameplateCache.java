package com.yoiko.core.client.rank;

import com.yoiko.core.network.RankNameplatePayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public final class ClientRankNameplateCache {
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private ClientRankNameplateCache() {
    }

    public static void update(RankNameplatePayload payload) {
        if (payload.rankId().isBlank() || payload.iconTexture().isBlank()) {
            ENTRIES.remove(payload.playerUuid());
            return;
        }
        ResourceLocation texture = ResourceLocation.tryParse(payload.iconTexture());
        if (texture == null) {
            ENTRIES.remove(payload.playerUuid());
            return;
        }
        ENTRIES.put(payload.playerUuid(), new Entry(payload.rankId(), texture, payload.color()));
    }

    public static Entry get(UUID playerUuid) {
        return ENTRIES.get(playerUuid);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public record Entry(String rankId, ResourceLocation texture, int color) {
    }
}
