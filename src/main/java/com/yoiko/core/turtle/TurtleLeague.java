package com.yoiko.core.turtle;

import java.time.DayOfWeek;

public enum TurtleLeague {
    TRAINING_D(TurtleRaceClass.D, TurtleRaceClass.D),
    CORAL(TurtleRaceClass.D, TurtleRaceClass.D),
    CURRENT(TurtleRaceClass.C, TurtleRaceClass.C),
    ABYSS(TurtleRaceClass.B, TurtleRaceClass.B),
    OPEN(TurtleRaceClass.A, TurtleRaceClass.S);

    private final TurtleRaceClass minimumClass;
    private final TurtleRaceClass maximumClass;

    TurtleLeague(TurtleRaceClass minimumClass, TurtleRaceClass maximumClass) {
        this.minimumClass = minimumClass;
        this.maximumClass = maximumClass;
    }

    public TurtleRaceClass minimumClass() { return minimumClass; }
    public TurtleRaceClass maximumClass() { return maximumClass; }
    public boolean accepts(TurtleRaceClass raceClass) {
        return raceClass.ordinal() >= minimumClass.ordinal() && raceClass.ordinal() <= maximumClass.ordinal();
    }

    public static TurtleLeague forRaceClass(TurtleRaceClass raceClass) {
        return switch (raceClass) {
            case D -> CORAL;
            case C -> CURRENT;
            case B -> ABYSS;
            case A, S -> OPEN;
        };
    }

    public static TurtleLeague scheduled(DayOfWeek day) {
        return switch (day) {
            case MONDAY, THURSDAY -> CORAL;
            case TUESDAY, FRIDAY -> CURRENT;
            case WEDNESDAY, SATURDAY -> ABYSS;
            case SUNDAY -> OPEN;
        };
    }
}
