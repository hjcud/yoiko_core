package com.yoiko.core.cosmetic;

public record CosmeticData(String id, String displayName, CosmeticType type, String particle, int intervalTicks,
                           int count, double offsetY, String requiredRank, String creator, CosmeticRarity rarity,
                           ParticleCategory particleCategory, CosmeticModelData modelData) {
    public CosmeticData(String id, String displayName, CosmeticType type, String particle, int intervalTicks,
                        int count, double offsetY, String requiredRank, String creator) {
        this(id, displayName, type, particle, intervalTicks, count, offsetY, requiredRank, creator,
                CosmeticRarity.MYTHIC, ParticleCategory.NONE, CosmeticModelData.NONE);
    }
}
