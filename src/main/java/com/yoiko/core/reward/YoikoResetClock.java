package com.yoiko.core.reward;

import com.yoiko.core.config.YoikoCommonConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Shared reset-period clock for daily rewards, bonus boxes, and economy limits.
 */
public final class YoikoResetClock {
    private YoikoResetClock() {
    }

    public static ZoneId zone() {
        try {
            return ZoneId.of(YoikoCommonConfig.DAILY_TIMEZONE.get());
        } catch (RuntimeException exception) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    public static LocalDate dailyPeriodDate(long millis) {
        return dailyPeriodDate(millis, zone(), YoikoCommonConfig.DAILY_RESET_HOUR.get());
    }

    public static LocalDate dailyPeriodDate(long millis, ZoneId zone, int resetHour) {
        ZonedDateTime time = Instant.ofEpochMilli(millis).atZone(zone);
        LocalTime resetTime = LocalTime.of(Math.max(0, Math.min(23, resetHour)), 0);
        LocalDate date = time.toLocalDate();
        return time.toLocalTime().isBefore(resetTime) ? date.minusDays(1) : date;
    }

    public static String dailyPeriodKey(long millis) {
        return "daily:" + dailyPeriodDate(millis).toEpochDay();
    }

    public static ZonedDateTime nextDailyReset(long millis) {
        ZonedDateTime now = Instant.ofEpochMilli(millis).atZone(zone());
        ZonedDateTime candidate = now.toLocalDate()
                .atTime(YoikoCommonConfig.DAILY_RESET_HOUR.get(), 0)
                .atZone(zone());
        return now.isBefore(candidate) ? candidate : candidate.plusDays(1);
    }
}
