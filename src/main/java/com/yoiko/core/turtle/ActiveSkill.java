package com.yoiko.core.turtle;

import java.util.Locale;
import java.util.Set;

public enum ActiveSkill {
    SHELLBREAK_START("shellbreak_start", Set.of("START", "POWER", "FRONT")),
    SAND_SPRINT("sand_sprint", Set.of("SAND", "SPEED")),
    FLOW_RHYTHM("flow_rhythm", Set.of("STRAIGHT", "SPEED", "LANE")),
    MUD_BREAKER("mud_breaker", Set.of("MUD", "POWER")),
    CORAL_CORNER("coral_corner", Set.of("CORNER", "NAVIGATION")),
    SHELL_HOP("shell_hop", Set.of("HOP", "POWER", "TRAFFIC")),
    UNTURNED_HEART("unturned_heart", Set.of("INTERFERENCE", "CALM")),
    FALSE_FOOTPRINTS("false_footprints", Set.of("LANE", "INTERFERENCE", "NAVIGATION")),
    PUDDLE_SURF("puddle_surf", Set.of("PUDDLE", "STAMINA")),
    DEEP_BREATH("deep_breath", Set.of("STAMINA", "CALM")),
    THOUSAND_YEAR_STEP("thousand_year_step", Set.of("FINAL", "CLOSER", "POWER")),
    HOMEWARD_WAVE("homeward_wave", Set.of("FINAL", "CLOSER", "SPEED")),
    SUNLIT_STRIDE("sunlit_stride", Set.of("CLEAR", "STRAIGHT", "SPEED")),
    TAILWIND_SAIL("tailwind_sail", Set.of("WIND", "STRAIGHT", "POWER")),
    RAINSTEP("rainstep", Set.of("RAIN", "LANE")),
    BREAKAWAY_BOUND("breakaway_bound", Set.of("TRAFFIC", "LANE", "POWER")),
    LEAD_GUARD("lead_guard", Set.of("FINAL", "FRONT", "SPEED")),
    RESERVE_RELEASE("reserve_release", Set.of("FINAL", "STEADY", "STAMINA", "POWER")),
    WAKE_CUT("wake_cut", Set.of("FOLLOW", "LANE", "TRAFFIC")),
    FINAL_GAP("final_gap", Set.of("FINAL", "CLOSER", "LANE", "POWER")),
    CORNER_CLAIM("corner_claim", Set.of("CORNER", "LANE", "NAVIGATION")),
    SURFACE_CHAIN("surface_chain", Set.of("SURFACE_CHANGE", "POWER", "STAMINA")),
    LIMIT_SPRINT("limit_sprint", Set.of("FINAL", "CLOSER", "POWER", "STAMINA")),
    SURGING_SPRAY("surging_spray", Set.of("FRONT", "INTERFERENCE", "STAMINA")),
    RECKLESS_PASS("reckless_pass", Set.of("TRAFFIC", "LANE", "POWER", "STAMINA"));

    private final String id;
    private final Set<String> tags;
    ActiveSkill(String id, Set<String> tags) { this.id = id; this.tags = tags; }
    public String id() { return id; }
    public Set<String> tags() { return tags; }

    public static ActiveSkill byId(String id) {
        for (ActiveSkill value : values()) if (value.id.equals(id)) return value;
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
