package com.yoiko.core.economy;

import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.reward.YoikoResetClock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * A shared, offline-accruing gold reserve. Calendar claims and completed activities
 * consume the same reserve, preventing the two reward paths from minting gold twice.
 */
public final class RestedGoldManager {
    private RestedGoldManager() {
    }

    public enum Activity {
        POKEMON_CAPTURE(6, "pokemon_capture"),
        FISHING(4, "fishing"),
        TURTLE_RACE(16, "turtle_race"),
        TURTLE_TIME_TRIAL(16, "turtle_time_trial");

        private final int gold;
        private final String id;

        Activity(int gold, String id) {
            this.gold = gold;
            this.id = id;
        }
    }

    /** Reserves up to the configured calendar amount for attachment to today's reward mail. */
    public static long reserveCalendarClaim(ServerPlayer player) {
        ServerYoikoSavedData saved = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = saved.getOrCreate(player);
        refresh(data, System.currentTimeMillis());
        long claimed = Math.min(data.restedGold,
                Math.max(0L, YoikoCommonConfig.RESTED_GOLD_CALENDAR_CLAIM.get()));
        if (claimed > 0L) {
            data.restedGold -= claimed;
        }
        saved.markDirty(player);
        return claimed;
    }

    /** Restores a calendar reservation when the corresponding mail could not be stored. */
    public static void restoreCalendarClaim(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return;
        }
        ServerYoikoSavedData saved = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = saved.getOrCreate(player);
        refresh(data, System.currentTimeMillis());
        data.restedGold = Math.min(cap(), saturatedAdd(data.restedGold, amount));
        saved.markDirty(player);
    }

    /** Pays a completed activity immediately, limited by the player's rested reserve. */
    public static long awardActivity(ServerPlayer player, Activity activity) {
        if (player == null || activity == null || player.isCreative() || player.isSpectator()) {
            return 0L;
        }
        ServerYoikoSavedData saved = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = saved.getOrCreate(player);
        refresh(data, System.currentTimeMillis());
        long requested = Math.min(data.restedGold, activity.gold);
        if (requested <= 0L) {
            saved.markDirty(player);
            return 0L;
        }
        long added = CurrencyManager.add(data, CurrencyType.GOLD, requested);
        if (added <= 0L) {
            saved.markDirty(player);
            return 0L;
        }
        data.restedGold -= added;
        saved.markDirty(player);
        YoikoAdvancementManager.recordCurrencyBalances(player);
        EconomyManager.recordEconomy(player, "RESTED_ACTIVITY_GOLD_CREATED", added,
                "activity=" + activity.id + ";reserve=" + data.restedGold);
        player.displayClientMessage(Component.translatable(
                "yoiko_core.message.rested_gold.activity", added, data.restedGold)
                .withStyle(ChatFormatting.GOLD), true);
        return added;
    }

    public static long available(ServerPlayer player) {
        ServerYoikoSavedData saved = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = saved.getOrCreate(player);
        refresh(data, System.currentTimeMillis());
        saved.markDirty(player);
        return data.restedGold;
    }

    /** Returns the reserve as of {@code now} without mutating offline player data. */
    public static long projectedAvailable(PlayerYoikoData data, long now) {
        return projectedAvailable(data.restedGold, data.restedGoldAccrualEpochDay, now);
    }

    /** Projection variant used by the compact economy read model. */
    public static long projectedAvailable(long restedGold, long accrualEpochDay, long now) {
        long currentDay = YoikoResetClock.dailyPeriodDate(now).toEpochDay();
        long elapsedDays = accrualEpochDay <= 0L
                ? 1L : Math.max(0L, currentDay - accrualEpochDay);
        long current = Math.min(cap(), Math.max(0L, restedGold));
        if (elapsedDays <= 0L) {
            return current;
        }
        long perDay = Math.max(0L, YoikoCommonConfig.RESTED_GOLD_PER_DAY.get());
        long accrued = elapsedDays > Long.MAX_VALUE / Math.max(1L, perDay)
                ? Long.MAX_VALUE : elapsedDays * perDay;
        return Math.min(cap(), saturatedAdd(current, accrued));
    }

    private static void refresh(PlayerYoikoData data, long now) {
        long currentDay = YoikoResetClock.dailyPeriodDate(now).toEpochDay();
        data.restedGold = projectedAvailable(data, now);
        data.restedGoldAccrualEpochDay = currentDay;
    }

    private static long cap() {
        return Math.max(0L, YoikoCommonConfig.RESTED_GOLD_CAP.get());
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
