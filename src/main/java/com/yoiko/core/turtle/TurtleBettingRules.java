package com.yoiko.core.turtle;

/** Shared server/client limits for official turtle-race betting. */
public final class TurtleBettingRules {
    public static final long MIN_STAKE = 20L;
    public static final long MAX_STAKE_PER_RACE = 200L;
    public static final long MAX_DAILY_STAKE = 4_000L;
    public static final long MAX_GROSS_PAYOUT_PER_RACE = 4_000L;
    private TurtleBettingRules() {
    }

    public static boolean validStake(long amount) {
        return amount >= MIN_STAKE && amount <= MAX_STAKE_PER_RACE;
    }

    public static long capGrossPayout(long payout) {
        return Math.min(Math.max(0L, payout), MAX_GROSS_PAYOUT_PER_RACE);
    }
}
