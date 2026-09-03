package com.yoiko.core.turtle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-authoritative turtle appearance catalogue. Additional entries can require an unlock. */
public final class TurtleAppearanceCatalog {
    public enum UnlockKind { FREE, ACHIEVEMENT, GEM }
    public record Appearance(String id, int color, UnlockKind unlockKind, String unlockKey, int gemPrice) { }

    public static final String DEFAULT_ID = "natural";
    private static final Map<String, Appearance> VALUES = new LinkedHashMap<>();

    static {
        // Representative colors are measured from the opaque pixels of each shell texture.
        free("natural", 0x55844B);
        free("emerald", 0x288451);
        free("ocean", 0x2F6F7D);
        free("sky", 0x4B908C);
        free("violet", 0x4C4581);
        free("rose", 0x80446D);
        free("amber", 0x897031);
        free("obsidian", 0x3C434E);
        free("coral", 0xAB544C);
        free("pearl", 0xA49F8D);
        free("frost", 0x7F99AB);
        free("copper", 0x825124);
        free("mint", 0x52997D);
        free("crimson", 0x702436);
        achievement("gold", 0xA0812B, "golden_shell_mastery");
        free("midnight", 0x1B264E);
    }

    private TurtleAppearanceCatalog() { }

    private static void free(String id, int color) { add(id,color,UnlockKind.FREE,"",0); }
    private static void achievement(String id,int color,String key){add(id,color,UnlockKind.ACHIEVEMENT,key,0);}
    /** Reserved for future purchasable shell textures; purchase validation is already implemented. */
    @SuppressWarnings("unused")
    private static void gem(String id,int color,int price){add(id,color,UnlockKind.GEM,"",price);}
    private static void add(String id,int color,UnlockKind kind,String key,int price){
        VALUES.put(id,new Appearance(id,color,kind,key,price));
    }

    public static Appearance get(String id) {
        Appearance value = VALUES.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown turtle appearance: " + id);
        return value;
    }

    public static List<Appearance> values() { return List.copyOf(VALUES.values()); }

    public static boolean isUnlocked(TurtlePlayerProgress progress, String id) {
        Appearance appearance = get(id);
        if (id.equals("gold")) return progress.hasGoldenShell();
        return appearance.unlockKind() == UnlockKind.FREE || progress.hasAppearance(id);
    }
}
