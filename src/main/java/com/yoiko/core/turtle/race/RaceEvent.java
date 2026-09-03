package com.yoiko.core.turtle.race;

public record RaceEvent(long tick, String type, String sourceEntryId, String targetEntryId, String detail) { }
