package com.yoiko.core.turtle;

public enum TurtleSurface {
    SAND,
    MUD,
    PUDDLE;

    /** Rendering-only foot offset; race progress and surface physics remain two-dimensional. */
    public double visualYOffset() { return this == PUDDLE ? -0.18 : 0.0; }
}
