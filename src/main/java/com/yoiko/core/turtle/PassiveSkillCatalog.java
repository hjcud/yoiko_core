package com.yoiko.core.turtle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PassiveSkillCatalog {
    private static final Map<String, PassiveSkill> BY_ID = new LinkedHashMap<>();

    static {
        add("light_steps", PassiveCategory.BASIC, "SPEED");
        add("strong_flippers", PassiveCategory.BASIC, "POWER", "START", "HOP");
        add("long_breath", PassiveCategory.BASIC, "STAMINA");
        add("hard_shell", PassiveCategory.BASIC, "CALM", "INTERFERENCE", "TRAFFIC");
        add("good_sense_of_direction", PassiveCategory.BASIC, "NAVIGATION", "CORNER", "LANE");
        add("early_rhythm", PassiveCategory.BASIC, "STAMINA", "START");

        add("sand_friend", PassiveCategory.SURFACE_EXACT, "SAND");
        add("puddle_swimmer", PassiveCategory.SURFACE_EXACT, "PUDDLE", "SPEED");
        add("mud_grip", PassiveCategory.SURFACE_EXACT, "MUD", "POWER");

        add("lane_cadence", PassiveCategory.SITUATION, "LANE");
        add("steady_breath", PassiveCategory.BASIC, "STAMINA");
        add("corner_cushion", PassiveCategory.COURSE, "CORNER", "CALM");
        add("passing_spring", PassiveCategory.SITUATION, "TRAFFIC", "LANE", "POWER");

        add("straight_focus", PassiveCategory.COURSE, "STRAIGHT", "SPEED");
        add("corner_expert", PassiveCategory.COURSE, "CORNER", "NAVIGATION");
        add("short_course_focus", PassiveCategory.COURSE_LENGTH, "SHORT", "POWER");
        add("long_course_pace", PassiveCategory.COURSE_LENGTH, "LONG", "STAMINA");
        add("changing_surface", PassiveCategory.COURSE, "SURFACE_CHANGE", "NAVIGATION");
        add("corner_exit", PassiveCategory.COURSE, "CORNER", "POWER");

        add("front_instinct", PassiveCategory.STRATEGY, "FRONT", "STAMINA");
        add("steady_cruise", PassiveCategory.STRATEGY, "STEADY", "CALM");
        add("wake_follower", PassiveCategory.STRATEGY, "FOLLOW", "STAMINA", "LANE");
        add("final_savings", PassiveCategory.STRATEGY, "CLOSER", "FINAL", "STAMINA");
        add("leaders_ease", PassiveCategory.STRATEGY, "FRONT", "STAMINA");
        add("reclaim_sense", PassiveCategory.STRATEGY, "FRONT", "LANE", "POWER");
        add("even_cadence", PassiveCategory.STRATEGY, "STEADY", "CALM", "SURFACE_CHANGE");
        add("reserve_control", PassiveCategory.STRATEGY, "STEADY", "FINAL", "SPEED");
        add("draft_posture", PassiveCategory.STRATEGY, "FOLLOW", "STAMINA", "TRAFFIC");
        add("passing_vision", PassiveCategory.STRATEGY, "FOLLOW", "LANE", "NAVIGATION");
        add("final_route", PassiveCategory.STRATEGY, "CLOSER", "FINAL", "LANE");
        add("chasing_spring", PassiveCategory.STRATEGY, "CLOSER", "FINAL", "POWER");

        add("starting_focus", PassiveCategory.SITUATION, "START", "CALM");
        add("gap_finder", PassiveCategory.SITUATION, "LANE", "NAVIGATION");
        add("hop_landing", PassiveCategory.SITUATION, "HOP", "POWER");
        add("shell_reflection", PassiveCategory.SITUATION, "INTERFERENCE", "CALM");
        add("outer_lane_flow", PassiveCategory.SITUATION, "LANE", "TRAFFIC");
        add("calm_recovery", PassiveCategory.SITUATION, "CALM", "STAMINA");
        add("breathing_switch", PassiveCategory.SITUATION, "CALM", "STAMINA");

        add("clear_sky_shell", PassiveCategory.WEATHER, "CLEAR", "SPEED");
        add("wind_reader", PassiveCategory.WEATHER, "WIND", "SAND", "STRAIGHT");
        add("rain_cadence", PassiveCategory.WEATHER, "RAIN", "LANE");
        add("mist_compass", PassiveCategory.WEATHER, "MIST", "NAVIGATION", "CORNER");
        add("mist_breathing", PassiveCategory.WEATHER, "MIST", "STAMINA");
        add("rainwash", PassiveCategory.WEATHER, "RAIN", "SURFACE_CHANGE", "LANE");

        add("ancient_patience", PassiveCategory.RARITY_SPECIAL, "FINAL", "CLOSER", "SPEED");
        add("golden_gap", PassiveCategory.RARITY_SPECIAL, "LANE", "TRAFFIC", "POWER");
        add("weather_crown", PassiveCategory.RARITY_SPECIAL, "WEATHER", "POWER");
        add("adaptive_shell", PassiveCategory.RARITY_SPECIAL, "SURFACE_CHANGE", "STAMINA");
        add("quiet_champion", PassiveCategory.RARITY_SPECIAL, "FINAL", "TRAFFIC", "NAVIGATION");
        add("comeback_star", PassiveCategory.RARITY_SPECIAL, "FINAL", "CLOSER", "POWER");

        if (BY_ID.size() != 50) throw new ExceptionInInitializerError("Expected 50 turtle passives");
    }

    private PassiveSkillCatalog() { }

    private static void add(String id, PassiveCategory category, String... tags) {
        PassiveSkill previous = BY_ID.put(id, new PassiveSkill(id, category, Set.of(tags)));
        if (previous != null) throw new IllegalStateException("Duplicate passive: " + id);
    }

    public static PassiveSkill get(String id) {
        PassiveSkill result = BY_ID.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown turtle passive: " + id);
        return result;
    }

    public static List<PassiveSkill> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

}
