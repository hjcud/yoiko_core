package com.yoiko.core.turtle;

/** Permanent per-turtle race class earned only from official race results. */
public enum TurtleRaceClass {
    D(0),
    C(100),
    B(250),
    A(500),
    S(850);

    private final int minimumPoints;

    TurtleRaceClass(int minimumPoints) { this.minimumPoints = minimumPoints; }

    public int minimumPoints() { return minimumPoints; }

    public int nextThreshold() {
        int next = ordinal() + 1;
        return next >= values().length ? minimumPoints : values()[next].minimumPoints;
    }

    public static TurtleRaceClass forPoints(int points) {
        TurtleRaceClass result = D;
        for (TurtleRaceClass value : values()) {
            if (points >= value.minimumPoints) result = value;
        }
        return result;
    }

    public TurtleLeague timeTrialLeague() {
        return switch (this) {
            case D -> TurtleLeague.TRAINING_D;
            case C -> TurtleLeague.CORAL;
            case B -> TurtleLeague.CURRENT;
            case A -> TurtleLeague.ABYSS;
            case S -> TurtleLeague.OPEN;
        };
    }
}
