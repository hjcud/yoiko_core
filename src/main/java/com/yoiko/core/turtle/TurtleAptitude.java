package com.yoiko.core.turtle;

public enum TurtleAptitude {
    S(1.04), A(1.02), B(1.00), C(0.97), D(0.93);

    private final double multiplier;

    TurtleAptitude(double multiplier) { this.multiplier = multiplier; }
    public double multiplier() { return multiplier; }
}
