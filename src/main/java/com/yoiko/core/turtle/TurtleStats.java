package com.yoiko.core.turtle;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;

public final class TurtleStats {
    private final int[] values;

    public TurtleStats(int speed, int stamina, int power, int calm, int navigation) {
        this.values = new int[]{speed, stamina, power, calm, navigation};
    }

    private TurtleStats(int[] values) {
        this.values = Arrays.copyOf(values, TurtleStat.values().length);
    }

    public int get(TurtleStat stat) { return values[stat.ordinal()]; }

    public void add(TurtleStat stat, int amount) {
        values[stat.ordinal()] = Math.max(0, values[stat.ordinal()] + amount);
    }

    public int total() { return Arrays.stream(values).sum(); }

    public int[] copyValues() { return Arrays.copyOf(values, values.length); }

    public TurtleStats copy() { return new TurtleStats(values); }

    public void replaceWith(TurtleStats source) {
        System.arraycopy(source.values, 0, values, 0, values.length);
    }

    @Override public String toString() { return Arrays.toString(values); }

    public void save(CompoundTag tag, String key) { tag.put(key, new IntArrayTag(values)); }

    public static TurtleStats load(CompoundTag tag, String key) {
        int[] raw = tag.getIntArray(key);
        if (raw.length != TurtleStat.values().length) {
            throw new IllegalArgumentException("Expected five turtle stats, got " + raw.length);
        }
        for (int value : raw) {
            if (value < 0 || value > 100) {
                throw new IllegalArgumentException("Turtle stat outside 0..100: " + value);
            }
        }
        return new TurtleStats(raw);
    }

    public static TurtleAptitude grade(int value) {
        if (value >= 75) return TurtleAptitude.S;
        if (value >= 60) return TurtleAptitude.A;
        if (value >= 45) return TurtleAptitude.B;
        if (value >= 30) return TurtleAptitude.C;
        return TurtleAptitude.D;
    }
}
