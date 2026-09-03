package com.yoiko.core.turtle;

public enum TurtleTrainingType {
    SPEED_DRILL(TurtleStat.SPEED, TurtleStat.POWER),
    ENDURANCE_SWIM(TurtleStat.STAMINA, TurtleStat.CALM),
    PUSH_OFF_DRILL(TurtleStat.POWER, TurtleStat.NAVIGATION),
    DODGE_DRILL(TurtleStat.CALM, TurtleStat.STAMINA),
    COURSE_STUDY(TurtleStat.NAVIGATION, TurtleStat.SPEED);

    private final TurtleStat primary;
    private final TurtleStat secondary;
    TurtleTrainingType(TurtleStat primary, TurtleStat secondary) { this.primary = primary; this.secondary = secondary; }
    public TurtleStat primary() { return primary; }
    public TurtleStat secondary() { return secondary; }
}
