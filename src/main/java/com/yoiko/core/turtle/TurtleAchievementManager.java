package com.yoiko.core.turtle;

import com.yoiko.core.advancement.YoikoAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/** Synchronizes persistent turtle progression into the shared Yoiko advancement tab. */
public final class TurtleAchievementManager {
    private static final String PREFIX = "turtle/";
    private static final String TITLE_PREFIX = "advancement.yoiko_core.turtle.";

    private TurtleAchievementManager() {
    }

    public static void sync(ServerPlayer player, TurtlePlayerProgress progress) {
        int hatches = progress.turtlesHatched();
        if (hatches >= 1) {
            grant(player, "hatch_first");
        }
        syncProgress(player, "hatch_10", hatches, 10, 10);

        int finishes = progress.officialFinishes();
        if (finishes >= 1) {
            grant(player, "race_first");
        }
        syncProgress(player, "race_10", finishes, 10, 10);
        if (progress.officialWins() >= 1) {
            grant(player, "race_win");
        }
        if (progress.timeTrialRecordCount() >= 1) {
            grant(player, "time_trial_first");
        }
    }

    private static void grant(ServerPlayer player, String id) {
        YoikoAdvancementManager.awardExternalCriterion(
                player, PREFIX + id, "complete", "turtle_" + id, TITLE_PREFIX + id);
    }

    private static void syncProgress(ServerPlayer player, String id, int value, int target, int segments) {
        YoikoAdvancementManager.syncExternalProgress(
                player, PREFIX + id, "turtle_" + id, TITLE_PREFIX + id,
                value, target, segments);
    }
}
