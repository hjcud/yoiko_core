package com.yoiko.core.relic;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public record RelicData(
        String id,
        String displayName,
        String effect,
        Set<RelicRarity> allowedRarities,
        RelicEquipGroup equipGroup,
        Map<RelicRarity, Double> values,
        Map<RelicRarity, Double> upgradeBonuses,
        boolean enabled
) {
    public RelicData {
        allowedRarities = allowedRarities == null || allowedRarities.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(allowedRarities));
        equipGroup = equipGroup == null ? RelicEquipGroup.STANDARD : equipGroup;
        values = Collections.unmodifiableMap(new EnumMap<>(values));
        upgradeBonuses = Collections.unmodifiableMap(new EnumMap<>(upgradeBonuses));
    }

    public boolean allows(RelicRarity rarity) {
        return allowedRarities.contains(rarity);
    }

    public double baseValue(RelicRarity rarity) {
        return values.getOrDefault(rarity, 0.0D);
    }

    public double upgradeBonus(RelicRarity rarity) {
        return upgradeBonuses.getOrDefault(rarity, 0.0D);
    }

    public double value(RelicRarity rarity, int level) {
        return baseValue(rarity) + upgradeBonus(rarity) * Math.max(0, level);
    }
}
