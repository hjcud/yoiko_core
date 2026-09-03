package com.yoiko.core.turtle;

import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import net.minecraft.network.chat.Component;

/** Stable generated-name identifiers. Player-entered names remain literal text. */
public final class TurtleNameCatalog {
    private static final String PREFIX = "@yoiko_name/";
    private static final List<String> IDS = List.of(
            "coco", "mugi", "bean", "cocoa", "tofu", "mocha", "cookie", "hazel", "potato", "chestnut",
            "spring", "haru", "cloud", "tori", "sora", "yuzu", "latte", "bell", "nana", "mei",
            "benji", "buddy", "milo", "charlie", "daisy", "ollie", "penny", "poppy", "willow", "teddy",
            "max", "bailey", "niko", "pico", "joy", "bibi", "toto", "marine", "pearl", "sandy",
            "coby", "noah", "maple", "pumpkin", "biscuit", "pepper", "olive", "ginger", "honey", "lucky",
            "scout", "nova", "comet", "clover", "mango", "jelly", "mochi", "dumpling", "cheese", "amber");
    private static final Set<String> ID_SET = Set.copyOf(IDS);

    static {
        if (ID_SET.size() != IDS.size()) throw new ExceptionInInitializerError("Duplicate turtle name id");
        if (IDS.stream().map(TurtleNameCatalog::encode).anyMatch(value -> value.codePointCount(0, value.length()) > TurtleData.MAX_NAME_CODE_POINTS)) {
            throw new ExceptionInInitializerError("Encoded turtle name exceeds the network limit");
        }
    }

    private TurtleNameCatalog() { }

    public static String pick(SplittableRandom random, Set<String> ownedNames) {
        List<String> available = IDS.stream().map(TurtleNameCatalog::encode).filter(value -> !ownedNames.contains(value)).toList();
        if (available.isEmpty()) throw new IllegalStateException("Generated turtle name catalog is exhausted");
        return available.get(random.nextInt(available.size()));
    }

    public static Component component(String storedName) {
        String id = id(storedName);
        return id == null
                ? Component.literal(storedName)
                : Component.translatable("yoiko_core.turtle.name." + id);
    }

    public static List<String> ids() { return IDS; }

    public static String encode(String id) {
        if (!ID_SET.contains(id)) throw new IllegalArgumentException("Unknown turtle name id: " + id);
        return PREFIX + id;
    }

    public static boolean hasReservedPrefix(String value) { return value != null && value.startsWith(PREFIX); }

    private static String id(String storedName) {
        if (!hasReservedPrefix(storedName)) return null;
        String id = storedName.substring(PREFIX.length());
        return ID_SET.contains(id) ? id : null;
    }
}
