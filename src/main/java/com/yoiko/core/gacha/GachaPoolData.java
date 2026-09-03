package com.yoiko.core.gacha;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GachaPoolData {
    private final EnumMap<GachaRarity, List<String>> pools = new EnumMap<>(GachaRarity.class);

    public GachaPoolData() {
        for (GachaRarity rarity : GachaRarity.values()) {
            pools.put(rarity, new ArrayList<>());
        }
    }

    public List<String> get(GachaRarity rarity) {
        return pools.get(rarity);
    }

    public Map<GachaRarity, List<String>> pools() {
        return pools;
    }

    public boolean isEmpty(GachaRarity rarity) {
        return get(rarity).isEmpty();
    }

    public String randomSpecies(GachaRarity rarity, Random random) {
        List<String> pool = get(rarity);
        if (pool.isEmpty()) {
            throw new IllegalStateException("Gacha pool is empty for rarity " + rarity);
        }
        return pool.get(random.nextInt(pool.size()));
    }
}
