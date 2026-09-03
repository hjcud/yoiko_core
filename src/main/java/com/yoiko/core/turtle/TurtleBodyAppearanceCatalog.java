package com.yoiko.core.turtle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Server-authoritative, immutable-at-birth turtle body-color catalogue. */
public final class TurtleBodyAppearanceCatalog {
    public record Appearance(String id, int weight, boolean rare, int representativeColor) { }

    public static final int TOTAL_WEIGHT = 10_000;
    public static final int RARE_WEIGHT = 50;
    public static final String DEFAULT_ID = "natural";
    public static final String GOLD_ID = "gold";

    private static final Map<String, Appearance> VALUES = new LinkedHashMap<>();

    static {
        // Common 99.50%: colors regularly seen across living turtle species.
        add(DEFAULT_ID, 4_500, false, 0x67864D); // olive
        add("moss",      2_500, false, 0x506F45);
        add("sand",      1_400, false, 0x8A8058);
        add("umber",     1_000, false, 0x66513A);
        add("slate",       550, false, 0x586A68);

        // Rare 0.50% (1/200 total): plausible pigment variations, never fantasy hues.
        add("leucistic",     25, true, 0xD5CDAE);
        add("melanistic",    15, true, 0x292D28);
        add(GOLD_ID,          10, true, 0xB18A31); // xanthic/golden, 1/1,000

        validateDistribution();
    }

    private TurtleBodyAppearanceCatalog() { }

    private static void add(String id, int weight, boolean rare, int representativeColor) {
        if (VALUES.put(id, new Appearance(id, weight, rare, representativeColor)) != null) {
            throw new IllegalStateException("Duplicate turtle body appearance: " + id);
        }
    }

    public static List<Appearance> values() { return List.copyOf(VALUES.values()); }

    public static Appearance get(String id) {
        Appearance value = VALUES.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown turtle body appearance: " + id);
        return value;
    }

    public static boolean isRare(String id) { return get(id).rare(); }

    public static Appearance roll(SplittableRandom random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (Appearance appearance : VALUES.values()) {
            roll -= appearance.weight();
            if (roll < 0) return appearance;
        }
        throw new IllegalStateException("Invalid turtle body appearance distribution");
    }

    private static void validateDistribution() {
        int total = VALUES.values().stream().mapToInt(Appearance::weight).sum();
        int rare = VALUES.values().stream().filter(Appearance::rare).mapToInt(Appearance::weight).sum();
        if (total != TOTAL_WEIGHT || rare != RARE_WEIGHT) {
            throw new IllegalStateException("Turtle body appearance weights must total 10000 with 50 rare weight: "
                    + total + "/" + rare);
        }
    }
}
