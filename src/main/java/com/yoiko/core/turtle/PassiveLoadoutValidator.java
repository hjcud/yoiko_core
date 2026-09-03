package com.yoiko.core.turtle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PassiveLoadoutValidator {
    private static final Set<String> MAJOR_TRIGGER_TAGS = Set.of(
            "SAND", "PUDDLE", "MUD", "HOP", "FINAL",
            "START", "LANE", "CORNER", "INTERFERENCE", "RAIN", "WIND", "MIST");
    private static final Set<String> FINISH_SPEED = Set.of("final_savings", "reserve_control", "ancient_patience",
            "comeback_star");
    private static final Set<String> FINISH_ACCELERATION = Set.of("comeback_star", "chasing_spring");
    private static final Set<String> STAMINA_EFFICIENCY = Set.of("long_breath", "early_rhythm", "steady_breath",
            "long_course_pace", "steady_cruise", "final_savings",
            "leaders_ease", "draft_posture", "mist_breathing", "breathing_switch");

    private PassiveLoadoutValidator() { }

    public static boolean validPartial(List<PassiveSkill> chosen, int requiredSpecialCount) {
        if (new HashSet<>(chosen.stream().map(PassiveSkill::id).toList()).size() != chosen.size()) return false;
        long special = chosen.stream().filter(PassiveSkill::special).count();
        if (special > 1 || special > requiredSpecialCount) return false;
        if (count(chosen, PassiveCategory.STRATEGY) > 1 || count(chosen, PassiveCategory.WEATHER) > 1
                || count(chosen, PassiveCategory.COURSE_LENGTH) > 1) return false;
        long surfaces = chosen.stream().filter(value -> value.category() == PassiveCategory.SURFACE_EXACT).count();
        if (surfaces > 2) return false;
        if (ids(chosen, FINISH_SPEED) > 1 || ids(chosen, FINISH_ACCELERATION) > 1
                || ids(chosen, STAMINA_EFFICIENCY) > 2) return false;
        Map<PassiveCategory, Integer> major = new HashMap<>();
        for (PassiveSkill skill : chosen) {
            if (skill.category() != PassiveCategory.SURFACE_EXACT && skill.category() != PassiveCategory.RARITY_SPECIAL) {
                major.merge(skill.category(), 1, Integer::sum);
            }
        }
        if (major.values().stream().anyMatch(value -> value > 2)) return false;
        for (String tag : MAJOR_TRIGGER_TAGS) {
            if (chosen.stream().filter(value -> value.tags().contains(tag)).count() > 2) return false;
        }
        return true;
    }

    public static boolean validComplete(List<PassiveSkill> chosen, int requiredSpecialCount,
                                        int minimumSynergies, ActiveSkill active, TurtleArchetype archetype) {
        if (chosen.size() != 4 || !validPartial(chosen, requiredSpecialCount)) return false;
        if (chosen.stream().filter(PassiveSkill::special).count() != requiredSpecialCount) return false;
        long synergy = chosen.stream().filter(value -> value.synergizes(active, archetype)).count();
        if (synergy < minimumSynergies) return false;
        return chosen.stream().anyMatch(value -> value.category() == PassiveCategory.BASIC
                || value.category() == PassiveCategory.COURSE || value.category() == PassiveCategory.SITUATION
                || value.category() == PassiveCategory.RARITY_SPECIAL);
    }

    private static long count(List<PassiveSkill> values, PassiveCategory category) {
        return values.stream().filter(value -> value.category() == category).count();
    }

    private static long ids(List<PassiveSkill> values, Set<String> ids) {
        return values.stream().filter(value -> ids.contains(value.id())).count();
    }
}
