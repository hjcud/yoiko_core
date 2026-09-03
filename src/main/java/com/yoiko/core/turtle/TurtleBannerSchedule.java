package com.yoiko.core.turtle;

import com.yoiko.core.reward.YoikoResetClock;
import java.time.LocalDate;
import java.util.List;

public final class TurtleBannerSchedule {
    public record Banner(String id, TurtleArchetype archetype, ActiveSkill activeSkill) { }
    private static final List<Banner> ROTATION=List.of(
            new Banner("quick_start",TurtleArchetype.SPEED,ActiveSkill.SHELLBREAK_START),
            new Banner("sand_dash",TurtleArchetype.FRONT,ActiveSkill.SAND_SPRINT),
            new Banner("corner_guide",TurtleArchetype.NAVIGATION,ActiveSkill.CORAL_CORNER),
            new Banner("shallow_wave",TurtleArchetype.STEADY,ActiveSkill.PUDDLE_SURF),
            new Banner("deep_breath",TurtleArchetype.ENDURANCE,ActiveSkill.DEEP_BREATH),
            new Banner("bold_hop",TurtleArchetype.POWER,ActiveSkill.SHELL_HOP),
            new Banner("last_spurt",TurtleArchetype.CLOSER,ActiveSkill.THOUSAND_YEAR_STEP),
            new Banner("homeward",TurtleArchetype.CALM,ActiveSkill.HOMEWARD_WAVE));
    private TurtleBannerSchedule(){}
    public static Banner current(){LocalDate date=YoikoResetClock.dailyPeriodDate(System.currentTimeMillis());return ROTATION.get(Math.floorMod((int)date.toEpochDay(),ROTATION.size()));}
}
