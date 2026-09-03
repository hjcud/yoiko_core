package com.yoiko.core.client.cosmetic;

import com.yoiko.core.cosmetic.CosmeticData;
import com.yoiko.core.cosmetic.CosmeticModelData;
import com.yoiko.core.cosmetic.CosmeticRarity;
import com.yoiko.core.cosmetic.CosmeticType;
import com.yoiko.core.cosmetic.ParticleCategory;
import com.yoiko.core.network.ParticleCosmeticCatalogPayload;
import java.util.HashMap;
import java.util.Map;

public final class ClientParticleCosmeticCatalog {
    private static Map<String, CosmeticData> definitions = Map.of();
    private static int revision = -1;

    private ClientParticleCosmeticCatalog() {
    }

    public static void replace(ParticleCosmeticCatalogPayload payload) {
        Map<String, CosmeticData> replacement = new HashMap<>();
        for (ParticleCosmeticCatalogPayload.Entry entry : payload.entries()) {
            ParticleCategory category = ParticleCategory.fromString(entry.particleCategory());
            if (entry.cosmeticId().isBlank() || category == ParticleCategory.NONE
                    || entry.intervalTicks() < 1 || entry.intervalTicks() > 1_200
                    || entry.count() < 0 || entry.count() > 256
                    || !Double.isFinite(entry.offsetY()) || Math.abs(entry.offsetY()) > 16.0D) {
                continue;
            }
            replacement.put(entry.cosmeticId(), new CosmeticData(
                    entry.cosmeticId(), entry.cosmeticId(), CosmeticType.PARTICLE, entry.particle(),
                    entry.intervalTicks(), entry.count(), entry.offsetY(), "", "",
                    CosmeticRarity.fromString(entry.rarity()), category, CosmeticModelData.NONE
            ));
        }
        definitions = Map.copyOf(replacement);
        revision = payload.revision();
    }

    public static CosmeticData get(String cosmeticId, int expectedRevision) {
        return revision == expectedRevision ? definitions.get(cosmeticId) : null;
    }

    public static int revision() {
        return revision;
    }

    public static void clear() {
        definitions = Map.of();
        revision = -1;
    }
}
