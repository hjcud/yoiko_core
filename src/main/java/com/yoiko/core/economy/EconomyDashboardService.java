package com.yoiko.core.economy;

import com.yoiko.core.data.ServerYoikoEconomySavedData;
import com.yoiko.core.data.ServerYoikoMarketSavedData;
import com.yoiko.core.reward.YoikoResetClock;
import com.yoiko.core.turtle.TurtleRacingSavedData;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class EconomyDashboardService {
    public static final int MAX_HISTORY_DAYS = 180;

    private EconomyDashboardService() {
    }

    public static Snapshot snapshot(MinecraftServer server, int days) {
        return snapshot(server, days, true);
    }

    /** Flow-only report; does not touch the account index. */
    public static Snapshot flowSnapshot(MinecraftServer server, int days) {
        return snapshot(server, days, false);
    }

    public static WealthSnapshot wealthSnapshot(MinecraftServer server) {
        Wealth wealth = wealth(server, System.currentTimeMillis());
        TurtleRacingSavedData turtleData = TurtleRacingSavedData.get(server);
        return new WealthSnapshot(wealth.knownPlayers(), wealth.totalGold(), wealth.totalGems(),
                wealth.goldP50(), wealth.goldP90(), wealth.goldP99(),
                wealth.gemP50(), wealth.gemP90(), wealth.gemP99(), wealth.topTenGoldShareBps(),
                wealth.projectedRestedGold(), wealth.pendingMailGold(), wealth.pendingMailGems(),
                turtleEscrowGold(turtleData));
    }

    private static Snapshot snapshot(MinecraftServer server, int days, boolean includeWealth) {
        int windowDays = Math.max(1, Math.min(MAX_HISTORY_DAYS, days));
        long now = System.currentTimeMillis();
        LocalDate periodTo = YoikoResetClock.dailyPeriodDate(now);
        LocalDate periodFrom = periodTo.minusDays(windowDays - 1L);
        ServerYoikoEconomySavedData economy = ServerYoikoEconomySavedData.get(server);
        List<EconomyDailyAggregate> allDaily = economy.dailyAggregates();
        Map<String, EconomyDailyAggregate> selected = new HashMap<>();
        for (EconomyDailyAggregate aggregate : allDaily) {
            LocalDate date = parsePeriod(aggregate.period());
            if (date != null && !date.isBefore(periodFrom) && !date.isAfter(periodTo)) {
                selected.put(aggregate.period(), aggregate);
            }
        }

        Map<String, Totals> goldSources = new HashMap<>();
        Map<String, Totals> goldSinks = new HashMap<>();
        Map<String, Totals> gemSources = new HashMap<>();
        Map<String, Totals> gemSinks = new HashMap<>();
        Map<String, Totals> details = new HashMap<>();
        Set<UUID> activePlayers = new HashSet<>();
        for (EconomyDailyAggregate aggregate : selected.values()) {
            activePlayers.addAll(aggregate.participants());
            aggregate.metrics().forEach((action, metric) -> {
                if (isGoldSource(action)) merge(goldSources, action, metric);
                if (isGoldSink(action)) merge(goldSinks, action, metric);
                if (isGemSource(action)) merge(gemSources, action, metric);
                if (isGemSink(action)) merge(gemSinks, action, metric);
            });
            aggregate.breakdowns().forEach((key, metric) -> {
                int separator = key.indexOf('|');
                String action = separator < 0 ? key : key.substring(0, separator);
                if (isCurrencyFlow(action) || "PLAYER_SALE".equals(action)) {
                    merge(details, key, metric);
                }
            });
        }

        long goldCreated = amount(goldSources);
        long goldBurned = amount(goldSinks);
        long gemsCreated = amount(gemSources);
        long gemsSpent = amount(gemSinks);
        long serverBuyGold = amount(selected, "SERVER_BUY");
        long rabbitGold = amount(selected, "TREASURE_RABBIT_GOLD");
        long restedGoldCreated = saturatedAdd(amount(selected, "RESTED_ACTIVITY_GOLD_CREATED"),
                amount(selected, "RESTED_CALENDAR_GOLD_CREATED"));
        long welcomeGoldCreated = amount(selected, "FIRST_LOGIN_GOLD_CREATED");
        long goldShopSpent = amount(selected, "GOLD_SPENT");
        long feesBurned = amount(selected, EconomyDailyAggregate.MARKET_FEES);
        long turtleBetBurned = amount(selected, "TURTLE_BET_GOLD_BURNED");
        long turtleBetCreated = amount(selected, "TURTLE_BET_GOLD_CREATED");
        long adminGoldCreated = amount(selected, "ADMIN_GOLD_CREATED");
        long adminGoldRemoved = amount(selected, "ADMIN_GOLD_REMOVED");
        long adminGemsCreated = amount(selected, "ADMIN_GEMS_CREATED");
        long adminGemsRemoved = amount(selected, "ADMIN_GEMS_REMOVED");
        long tradeVolume = amount(selected, "PLAYER_SALE");
        int tradeCount = safeInt(events(selected, "PLAYER_SALE"));
        int suspicious = safeInt(events(selected, EconomyDailyAggregate.MARKET_SUSPICIOUS));
        long turtleBetStaked = amount(selected, "TURTLE_BET_STAKED");
        long turtleBetPayout = amount(selected, "TURTLE_BET_PAYOUT");
        long turtleBetRefund = amount(selected, "TURTLE_BET_REFUND");

        var listings = ServerYoikoMarketSavedData.get(server).marketplaceListings();
        long listingValue = listings.stream().mapToLong(MarketplaceListing::price)
                .reduce(0L, EconomyDashboardService::saturatedAdd);

        Wealth wealth = includeWealth ? wealth(server, now) : Wealth.EMPTY;
        TurtleRacingSavedData turtleData = TurtleRacingSavedData.get(server);
        long turtleEscrowGold = turtleEscrowGold(turtleData);
        List<DailyFlow> dailyFlows = new ArrayList<>();
        for (LocalDate date = periodFrom; !date.isAfter(periodTo); date = date.plusDays(1L)) {
            dailyFlows.add(dailyFlow(date, selected.get(date.toString())));
        }

        String trackingSince = economy.trackingStartedPeriod();
        LocalDate firstTracked = parsePeriod(trackingSince);
        boolean historyLimited = firstTracked != null && periodFrom.isBefore(firstTracked);
        int observedDays = historyLimited
                ? safeInt(ChronoUnit.DAYS.between(firstTracked, periodTo) + 1L) : windowDays;
        return new Snapshot(windowDays, Math.max(1, observedDays), periodFrom.toString(), periodTo.toString(), trackingSince,
                goldCreated, goldBurned, serverBuyGold, rabbitGold, restedGoldCreated, welcomeGoldCreated,
                goldShopSpent, feesBurned, turtleBetBurned, turtleBetCreated, adminGoldCreated, adminGoldRemoved,
                gemsCreated, gemsSpent, adminGemsCreated, adminGemsRemoved,
                tradeCount, tradeVolume, suspicious, listings.size(), listingValue,
                turtleBetStaked, turtleBetPayout, turtleBetRefund,
                activePlayers.size(), wealth.knownPlayers(), wealth.totalGold(), wealth.totalGems(),
                wealth.goldP50(), wealth.goldP90(), wealth.goldP99(),
                wealth.gemP50(), wealth.gemP90(), wealth.gemP99(), wealth.topTenGoldShareBps(),
                wealth.projectedRestedGold(), wealth.pendingMailGold(), wealth.pendingMailGems(),
                turtleEscrowGold,
                flowList(goldSources), flowList(goldSinks), flowList(gemSources), flowList(gemSinks),
                detailList(details), List.copyOf(dailyFlows), historyLimited);
    }

    private static long turtleEscrowGold(TurtleRacingSavedData turtleData) {
        return turtleData.competition()
                .filter(competition -> switch (competition.phase()) {
                    case RESULT_OPEN, CLEANING_MAP, COMPLETED, CANCELLING, CANCELLED -> false;
                    default -> true;
                })
                .map(competition -> competition.bets().stream().mapToLong(bet -> bet.amount())
                        .reduce(0L, EconomyDashboardService::saturatedAdd))
                .orElse(0L);
    }

    private static Wealth wealth(MinecraftServer server, long now) {
        ServerYoikoEconomySavedData economy = ServerYoikoEconomySavedData.get(server);
        List<Long> gold = new ArrayList<>();
        List<Long> gems = new ArrayList<>();
        long totalGold = 0L;
        long totalGems = 0L;
        long restedGold = 0L;
        long pendingGold = 0L;
        long pendingGems = 0L;
        List<ServerYoikoEconomySavedData.AccountSnapshot> accounts = economy.accountSnapshots();
        for (ServerYoikoEconomySavedData.AccountSnapshot player : accounts) {
            long playerGold = player.gold();
            long playerGems = player.gems();
            gold.add(playerGold);
            gems.add(playerGems);
            totalGold = saturatedAdd(totalGold, playerGold);
            totalGems = saturatedAdd(totalGems, playerGems);
            restedGold = saturatedAdd(restedGold, RestedGoldManager.projectedAvailable(
                    player.restedGold(), player.restedGoldAccrualEpochDay(), now));
            pendingGold = saturatedAdd(pendingGold, player.pendingMailGold());
            pendingGems = saturatedAdd(pendingGems, player.pendingMailGems());
        }
        Collections.sort(gold);
        Collections.sort(gems);
        int topTenShare = topShareBasisPoints(gold, totalGold, 0.10D);
        return new Wealth(accounts.size(), totalGold, totalGems,
                percentile(gold, 0.50D), percentile(gold, 0.90D), percentile(gold, 0.99D),
                percentile(gems, 0.50D), percentile(gems, 0.90D), percentile(gems, 0.99D),
                topTenShare, restedGold, pendingGold, pendingGems);
    }

    private static DailyFlow dailyFlow(LocalDate date, EconomyDailyAggregate aggregate) {
        if (aggregate == null) {
            return new DailyFlow(date.toString(), 0L, 0L, 0L, 0L, 0, 0L, 0L);
        }
        long goldCreated = 0L;
        long goldBurned = 0L;
        long gemsCreated = 0L;
        long gemsSpent = 0L;
        for (var entry : aggregate.metrics().entrySet()) {
            String action = entry.getKey();
            long value = entry.getValue().amount();
            if (isGoldSource(action)) goldCreated = saturatedAdd(goldCreated, value);
            if (isGoldSink(action)) goldBurned = saturatedAdd(goldBurned, value);
            if (isGemSource(action)) gemsCreated = saturatedAdd(gemsCreated, value);
            if (isGemSink(action)) gemsSpent = saturatedAdd(gemsSpent, value);
        }
        return new DailyFlow(date.toString(), goldCreated, goldBurned, gemsCreated, gemsSpent,
                aggregate.participants().size(), aggregate.metric("PLAYER_SALE").amount(),
                aggregate.metric("TURTLE_BET_STAKED").amount());
    }

    private static boolean isCurrencyFlow(String action) {
        return isGoldSource(action) || isGoldSink(action) || isGemSource(action) || isGemSink(action);
    }

    private static boolean isGoldSource(String action) {
        return "SERVER_BUY".equals(action) || "TREASURE_RABBIT_GOLD".equals(action)
                || action.endsWith("_GOLD_CREATED");
    }

    private static boolean isGoldSink(String action) {
        return EconomyDailyAggregate.MARKET_FEES.equals(action) || "GOLD_SPENT".equals(action)
                || action.endsWith("_GOLD_BURNED") || action.endsWith("_GOLD_REMOVED");
    }

    private static boolean isGemSource(String action) {
        return "TREASURE_RABBIT_GEMS".equals(action) || action.endsWith("_GEMS_CREATED")
                || action.endsWith("_GEM_CREATED");
    }

    private static boolean isGemSink(String action) {
        return "GEM_SPENT".equals(action) || action.endsWith("_GEMS_REMOVED");
    }

    private static long amount(Map<String, Totals> values) {
        long total = 0L;
        for (Totals value : values.values()) {
            total = saturatedAdd(total, value.amount);
        }
        return total;
    }

    private static long amount(Map<String, EconomyDailyAggregate> days, String action) {
        long total = 0L;
        for (EconomyDailyAggregate day : days.values()) {
            total = saturatedAdd(total, day.metric(action).amount());
        }
        return total;
    }

    private static long events(Map<String, EconomyDailyAggregate> days, String action) {
        long total = 0L;
        for (EconomyDailyAggregate day : days.values()) {
            total = saturatedAdd(total, day.metric(action).events());
        }
        return total;
    }

    private static void merge(Map<String, Totals> destination, String key,
                              EconomyDailyAggregate.Metric metric) {
        Totals total = destination.computeIfAbsent(key, ignored -> new Totals());
        total.events = saturatedAdd(total.events, metric.events());
        total.amount = saturatedAdd(total.amount, metric.amount());
    }

    private static List<FlowSource> flowList(Map<String, Totals> source) {
        return source.entrySet().stream()
                .map(entry -> new FlowSource(entry.getKey(), entry.getValue().events, entry.getValue().amount))
                .sorted(Comparator.comparingLong(FlowSource::amount).reversed()
                        .thenComparing(FlowSource::action))
                .toList();
    }

    private static List<FlowBreakdown> detailList(Map<String, Totals> source) {
        return source.entrySet().stream().map(entry -> {
                    int separator = entry.getKey().indexOf('|');
                    String action = separator < 0 ? entry.getKey() : entry.getKey().substring(0, separator);
                    String detail = separator < 0 ? "" : entry.getKey().substring(separator + 1);
                    return new FlowBreakdown(action, detail, entry.getValue().events, entry.getValue().amount);
                })
                .sorted(Comparator.comparingLong(FlowBreakdown::amount).reversed()
                        .thenComparing(FlowBreakdown::action).thenComparing(FlowBreakdown::detail))
                .toList();
    }

    private static int topShareBasisPoints(List<Long> sorted, long total, double fraction) {
        if (sorted.isEmpty() || total <= 0L) return 0;
        int count = Math.max(1, (int) Math.ceil(sorted.size() * fraction));
        long top = 0L;
        for (int index = sorted.size() - 1; index >= sorted.size() - count; index--) {
            top = saturatedAdd(top, sorted.get(index));
        }
        return (int) Math.clamp(Math.round((double) top * 10_000.0D / (double) total), 0L, 10_000L);
    }

    static long percentile(List<Long> sorted, double quantile) {
        if (sorted.isEmpty()) return 0L;
        int index = (int) Math.ceil(Math.clamp(quantile, 0.0D, 1.0D) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static LocalDate parsePeriod(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static int safeInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static final class Totals {
        private long events;
        private long amount;
    }

    private record Wealth(int knownPlayers, long totalGold, long totalGems,
                          long goldP50, long goldP90, long goldP99,
                          long gemP50, long gemP90, long gemP99, int topTenGoldShareBps,
                          long projectedRestedGold, long pendingMailGold, long pendingMailGems) {
        private static final Wealth EMPTY = new Wealth(0, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0, 0L, 0L, 0L);
    }

    public record WealthSnapshot(int knownPlayers, long totalGold, long totalGems,
                                 long goldP50, long goldP90, long goldP99,
                                 long gemP50, long gemP90, long gemP99, int topTenGoldShareBps,
                                 long projectedRestedGold, long pendingMailGold, long pendingMailGems,
                                 long turtleEscrowGold) {
    }

    public record FlowSource(String action, long events, long amount) {
    }

    public record FlowBreakdown(String action, String detail, long events, long amount) {
    }

    public record DailyFlow(String period, long goldCreated, long goldBurned,
                            long gemsCreated, long gemsSpent, int activePlayers,
                            long marketVolume, long turtleBetStake) {
        public long goldNetFlow() { return goldCreated - goldBurned; }
        public long gemNetFlow() { return gemsCreated - gemsSpent; }
    }

    public record Snapshot(int days, int observedDays, String periodFrom, String periodTo, String trackingSince,
                           long goldCreated, long goldBurned, long serverBuyGold,
                           long rabbitGold, long restedGoldCreated, long welcomeGoldCreated,
                           long goldShopSpent, long feesBurned,
                           long turtleBetBurned, long turtleBetCreated,
                           long adminGoldCreated, long adminGoldRemoved,
                           long gemsCreated, long gemsSpent, long adminGemsCreated, long adminGemsRemoved,
                           int tradeCount, long tradeVolume,
                           int suspiciousCount, int activeListings, long activeListingValue,
                           long turtleBetStaked, long turtleBetPayout, long turtleBetRefund,
                           int activePlayers, int knownPlayers, long totalGold, long totalGems,
                           long goldP50, long goldP90, long goldP99,
                           long gemP50, long gemP90, long gemP99, int topTenGoldShareBps,
                           long projectedRestedGold, long pendingMailGold, long pendingMailGems,
                           long turtleEscrowGold,
                           List<FlowSource> goldSources, List<FlowSource> goldSinks,
                           List<FlowSource> gemSources, List<FlowSource> gemSinks,
                           List<FlowBreakdown> details, List<DailyFlow> dailyFlows,
                           boolean historyLimited) {
        public long goldNetFlow() { return goldCreated - goldBurned; }
        public long gemNetFlow() { return gemsCreated - gemsSpent; }
        public long dailyAverageGoldCreated() { return goldCreated / Math.max(1, observedDays); }
        public long dailyAverageGoldBurned() { return goldBurned / Math.max(1, observedDays); }
    }
}
