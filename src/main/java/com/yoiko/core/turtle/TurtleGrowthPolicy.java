package com.yoiko.core.turtle;

/** One authoritative guard for every player-facing turtle development entry point. */
public final class TurtleGrowthPolicy {
    private TurtleGrowthPolicy() { }

    public static boolean isCompetitionLocked(TurtleData turtle) {
        return TurtleRacingManager.get().isRegisteredTurtle(turtle.id());
    }

    public static void requireCompetitionUnlocked(TurtleData turtle) {
        if (isCompetitionLocked(turtle)) {
            throw TurtleLocalizedException.of("yoiko_core.turtle.error.growth_registered");
        }
    }
}
