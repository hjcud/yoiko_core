package com.yoiko.core.turtle;

import java.util.Set;

public record PassiveSkill(String id, PassiveCategory category, Set<String> tags) {
    public boolean special() { return category == PassiveCategory.RARITY_SPECIAL; }
    public boolean synergizes(ActiveSkill active, TurtleArchetype archetype) {
        if (tags.stream().anyMatch(active.tags()::contains)) return true;
        return switch (archetype) {
            case SPEED -> tags.contains("SPEED");
            case ENDURANCE -> tags.contains("STAMINA");
            case POWER -> tags.contains("POWER") || tags.contains("HOP");
            case CALM -> tags.contains("CALM") || tags.contains("INTERFERENCE");
            case NAVIGATION -> tags.contains("NAVIGATION") || tags.contains("LANE") || tags.contains("CORNER");
            case FRONT -> tags.contains("FRONT") || tags.contains("START");
            case STEADY -> tags.contains("STEADY") || tags.contains("STAMINA");
            case FOLLOW -> tags.contains("FOLLOW") || tags.contains("LANE");
            case CLOSER -> tags.contains("CLOSER") || tags.contains("FINAL");
            case BALANCED -> category == PassiveCategory.BASIC;
        };
    }
}
