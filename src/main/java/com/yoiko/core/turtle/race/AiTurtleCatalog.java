package com.yoiko.core.turtle.race;

import com.yoiko.core.turtle.ActiveSkill;
import com.yoiko.core.turtle.TurtleStats;
import com.yoiko.core.turtle.TurtleStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class AiTurtleCatalog {
    private static final Map<String, AiTurtleProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<String,String> BODY_APPEARANCES = Map.ofEntries(
            Map.entry("coral_bean","sand"), Map.entry("wavelet","slate"),
            Map.entry("sand_star","sand"), Map.entry("pebble","umber"),
            Map.entry("sea_lantern","moss"), Map.entry("salty","natural"),
            Map.entry("moon_shard","slate"), Map.entry("sunny","sand"),
            Map.entry("water_scale","moss"), Map.entry("sesame","natural"),
            Map.entry("blue_crown","slate"), Map.entry("homeward","umber"));

    static {
        add("coral_bean", "coral", TurtleStrategy.FRONT, 83,65,72,45,55,.98, ActiveSkill.LEAD_GUARD,
                "leaders_ease","starting_focus","strong_flippers","golden_gap");
        add("wavelet", "ocean", TurtleStrategy.STEADY, 74,64,62,62,58,.96, ActiveSkill.DEEP_BREATH,
                "long_breath","corner_cushion","steady_cruise","changing_surface");
        add("sand_star", "gold", TurtleStrategy.FOLLOW, 65,57,73,57,68,.97, ActiveSkill.SAND_SPRINT,
                "sand_friend","strong_flippers","wake_follower","gap_finder");
        add("pebble", "obsidian", TurtleStrategy.STEADY, 70,70,66,62,52,.97, ActiveSkill.MUD_BREAKER,
                "passing_spring","hard_shell","long_breath","steady_cruise");
        add("sea_lantern", "pearl", TurtleStrategy.FOLLOW, 64,60,57,65,74,1.00, ActiveSkill.CORAL_CORNER,
                "good_sense_of_direction","corner_expert","gap_finder","wake_follower");
        add("salty", "mint", TurtleStrategy.STEADY, 72,61,60,72,55,.96, ActiveSkill.UNTURNED_HEART,
                "hard_shell","shell_reflection","long_breath","steady_cruise");
        add("moon_shard", "midnight", TurtleStrategy.CLOSER, 71,72,66,53,58,.99, ActiveSkill.THOUSAND_YEAR_STEP,
                "final_savings","long_breath","final_route","strong_flippers");
        add("sunny", "amber", TurtleStrategy.FRONT, 84,62,71,46,57,.98, ActiveSkill.FLOW_RHYTHM,
                "reclaim_sense","lane_cadence","light_steps","starting_focus");
        add("water_scale", "ocean", TurtleStrategy.STEADY, 64,71,60,61,64,.97, ActiveSkill.PUDDLE_SURF,
                "puddle_swimmer","changing_surface","long_breath","good_sense_of_direction");
        add("sesame", "natural", TurtleStrategy.FOLLOW, 65,58,62,69,66,.96, ActiveSkill.FALSE_FOOTPRINTS,
                "gap_finder","shell_reflection","wake_follower","corner_expert");
        add("blue_crown", "sky", TurtleStrategy.FRONT, 81,62,74,45,58,1.00, ActiveSkill.SHELL_HOP,
                "hop_landing","gap_finder","strong_flippers","front_instinct");
        add("homeward", "violet", TurtleStrategy.CLOSER, 70,74,65,52,59,.99, ActiveSkill.HOMEWARD_WAVE,
                "final_savings","long_breath","light_steps","straight_focus");
        if (PROFILES.size() != 12) throw new ExceptionInInitializerError("Expected 12 AI turtle profiles");
    }

    private AiTurtleCatalog() { }

    private static void add(String id, String appearance, TurtleStrategy strategy,
                            int speed, int stamina, int power, int calm, int navigation, double coefficient,
                            ActiveSkill active, String... passives) {
        List<String> profilePassives=id.equals("water_scale")
                ?List.of("puddle_swimmer","hard_shell","quiet_champion","good_sense_of_direction")
                :List.of(passives);
        PROFILES.put(id, new AiTurtleProfile(id, strategy,
                new TurtleStats(speed, stamina, power, calm, navigation),
                coefficient, active, profilePassives, appearance));
    }

    public static List<AiTurtleProfile> all() { return List.copyOf(PROFILES.values()); }
    public static AiTurtleProfile get(String id) { return PROFILES.get(id); }
    public static String bodyAppearance(String id) {
        String value=BODY_APPEARANCES.get(id);
        if(value==null)throw new IllegalArgumentException("Unknown AI turtle body appearance: "+id);
        return value;
    }

    public static List<AiTurtleProfile> select(long seed, int count, boolean beachTheme,
                                               List<TurtleStrategy> occupiedStrategies) {
        SplittableRandom random = new SplittableRandom(seed);
        List<AiTurtleProfile> available = new ArrayList<>(all());
        List<AiTurtleProfile> result = new ArrayList<>();
        Map<TurtleStrategy, Integer> strategyCounts = new java.util.EnumMap<>(TurtleStrategy.class);
        occupiedStrategies.forEach(value -> strategyCounts.merge(value, 1, Integer::sum));
        while (result.size() < count && !available.isEmpty()) {
            List<AiTurtleProfile> legal = available.stream()
                    .filter(value -> strategyCounts.getOrDefault(value.strategy(), 0) < 3).toList();
            if (legal.isEmpty()) legal = List.copyOf(available);
            int total = legal.stream().mapToInt(value -> preferred(value, beachTheme) ? 3 : 1).sum();
            int roll = random.nextInt(total);
            AiTurtleProfile selected = legal.get(legal.size() - 1);
            for (AiTurtleProfile value : legal.stream().sorted(Comparator.comparing(AiTurtleProfile::id)).toList()) {
                roll -= preferred(value, beachTheme) ? 3 : 1;
                if (roll < 0) { selected = value; break; }
            }
            result.add(selected);
            available.remove(selected);
            strategyCounts.merge(selected.strategy(), 1, Integer::sum);
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean preferred(AiTurtleProfile value, boolean beach) {
        boolean beachProfile = switch (value.id()) {
            case "coral_bean", "sand_star", "sunny", "water_scale", "sesame", "blue_crown" -> true;
            default -> false;
        };
        return beach == beachProfile;
    }
}
