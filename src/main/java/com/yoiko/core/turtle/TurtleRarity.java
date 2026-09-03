package com.yoiko.core.turtle;

public enum TurtleRarity {
    COMMON(55, 248, 320, 68, 90, 1, 0),
    UNCOMMON(25, 252, 324, 70, 92, 1, 0),
    RARE(14, 256, 328, 72, 94, 2, 0),
    EPIC(5, 260, 332, 75, 97, 2, 35),
    LEGENDARY(1, 264, 336, 78, 100, 3, 100);

    private final int weight;
    private final int hatchBudget;
    private final int finalBudget;
    private final int hatchStatCap;
    private final int finalStatCap;
    private final int minimumSynergies;
    private final int specialPassiveChance;

    TurtleRarity(int weight, int hatchBudget, int finalBudget, int hatchStatCap, int finalStatCap,
                 int minimumSynergies, int specialPassiveChance) {
        this.weight = weight;
        this.hatchBudget = hatchBudget;
        this.finalBudget = finalBudget;
        this.hatchStatCap = hatchStatCap;
        this.finalStatCap = finalStatCap;
        this.minimumSynergies = minimumSynergies;
        this.specialPassiveChance = specialPassiveChance;
    }

    public int weight() { return weight; }
    public int hatchBudget() { return hatchBudget; }
    public int finalBudget() { return finalBudget; }
    public int hatchStatCap() { return hatchStatCap; }
    public int finalStatCap() { return finalStatCap; }
    public int minimumSynergies() { return minimumSynergies; }
    public int specialPassiveChance() { return specialPassiveChance; }

    public String symbol() {
        return switch (this) {
            case COMMON -> "C";
            case UNCOMMON -> "U";
            case RARE -> "R";
            case EPIC -> "E";
            case LEGENDARY -> "L";
        };
    }
}
