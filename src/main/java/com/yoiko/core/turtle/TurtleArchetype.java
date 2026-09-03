package com.yoiko.core.turtle;

public enum TurtleArchetype {
    BALANCED(new double[]{.20, .20, .20, .20, .20}),
    SPEED(new double[]{.24, .19, .21, .17, .19}),
    ENDURANCE(new double[]{.19, .24, .18, .21, .18}),
    POWER(new double[]{.20, .19, .24, .17, .20}),
    CALM(new double[]{.19, .22, .18, .22, .19}),
    NAVIGATION(new double[]{.19, .19, .19, .20, .23}),
    FRONT(new double[]{.24, .18, .23, .17, .18}),
    STEADY(new double[]{.19, .23, .18, .22, .18}),
    FOLLOW(new double[]{.19, .21, .19, .18, .23}),
    CLOSER(new double[]{.23, .21, .22, .16, .18});

    private final double[] ratios;
    TurtleArchetype(double[] ratios) { this.ratios = ratios; }
    public double ratio(TurtleStat stat) { return ratios[stat.ordinal()]; }
}
