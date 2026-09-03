package com.yoiko.core.client.cosmetic;

import com.yoiko.core.network.ParticleCosmeticSyncPayload;
import com.yoiko.core.cosmetic.CosmeticData;
import com.yoiko.core.cosmetic.ParticleCategory;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientParticleCosmeticCache {
    private static final Map<UUID, EquippedParticles> EQUIPPED = new HashMap<>();
    private static long revision;

    private ClientParticleCosmeticCache() {
    }

    public static void update(ParticleCosmeticSyncPayload payload) {
        if (payload.cosmeticIds().isEmpty()) {
            EQUIPPED.remove(payload.playerUuid());
        } else {
            EQUIPPED.put(payload.playerUuid(), new EquippedParticles(
                    payload.cosmeticIds(), payload.catalogRevision()
            ));
        }
        revision++;
    }

    public static boolean isEquipped(UUID playerUuid, String cosmeticId) {
        EquippedParticles equipped = EQUIPPED.get(playerUuid);
        return equipped != null && equipped.cosmeticIds().contains(cosmeticId);
    }

    public static String equipped(UUID playerUuid) {
        EquippedParticles equipped = EQUIPPED.get(playerUuid);
        if (equipped == null) {
            return "";
        }
        for (String cosmeticId : equipped.cosmeticIds()) {
            if (ClientParticleCosmeticCatalog.get(cosmeticId, equipped.catalogRevision()) != null) {
                return cosmeticId;
            }
        }
        return "";
    }

    public static EquippedParticles entry(UUID playerUuid) {
        return EQUIPPED.get(playerUuid);
    }

    public static String equippedForCategory(UUID playerUuid, ParticleCategory category) {
        EquippedParticles equipped = EQUIPPED.get(playerUuid);
        if (equipped == null) {
            return "";
        }
        for (String cosmeticId : equipped.cosmeticIds()) {
            CosmeticData cosmetic = ClientParticleCosmeticCatalog.get(cosmeticId, equipped.catalogRevision());
            if (cosmetic != null && cosmetic.particleCategory() == category) {
                return cosmeticId;
            }
        }
        return "";
    }

    public static void clear() {
        EQUIPPED.clear();
        revision++;
    }

    public static long revision() {
        return revision;
    }

    public record EquippedParticles(List<String> cosmeticIds, int catalogRevision) {
        public EquippedParticles {
            cosmeticIds = List.copyOf(cosmeticIds);
        }
    }
}
