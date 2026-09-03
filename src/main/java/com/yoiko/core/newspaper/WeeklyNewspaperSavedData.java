package com.yoiko.core.newspaper;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.treasure.TreasureRabbitVariant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent weekly counters and the recipient directory for Yoiko Weekly. */
public final class WeeklyNewspaperSavedData extends SavedData {
    private static final String DATA_NAME = YoikoServerCore.MODID + "_weekly_newspaper";
    private static final int DATA_VERSION = 4;
    private static final int MAX_KNOWN_PLAYERS = 10_000;
    private static final int MAX_BREAKING_NEWS = 32;
    private static final int MAX_INCIDENT_ARCHIVE = 128;
    private static final int MAX_ISSUE_ARCHIVE = 52;

    private String currentWeekKey = "";
    private long currentWeekStartedAt;
    private String lastAutoPublishedWeekKey = "";
    private final Map<UUID, String> knownPlayers = new LinkedHashMap<>();
    private final Map<UUID, MutablePlayerStats> playerStats = new LinkedHashMap<>();
    private final List<BreakingNewsReport> breakingNews = new ArrayList<>();
    private final List<BreakingNewsReport> incidentArchive = new ArrayList<>();
    private final List<ArchivedIssue> issueArchive = new ArrayList<>();

    public static WeeklyNewspaperSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WeeklyNewspaperSavedData::new, WeeklyNewspaperSavedData::load,
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_NAME
        );
    }

    public String currentWeekKey() {
        return currentWeekKey;
    }

    public long currentWeekStartedAt() {
        return currentWeekStartedAt;
    }

    public String lastAutoPublishedWeekKey() {
        return lastAutoPublishedWeekKey;
    }

    public void setLastAutoPublishedWeekKey(String weekKey) {
        lastAutoPublishedWeekKey = weekKey == null ? "" : weekKey;
        setDirty();
    }

    public void beginWeek(String weekKey, long startedAt) {
        currentWeekKey = weekKey == null ? "" : weekKey;
        currentWeekStartedAt = Math.max(0L, startedAt);
        playerStats.clear();
        breakingNews.clear();
        setDirty();
    }

    public void touchPlayer(UUID playerId, String playerName) {
        if (playerId == null) {
            return;
        }
        String safeName = playerName == null ? "" : playerName;
        if (knownPlayers.containsKey(playerId) || knownPlayers.size() < MAX_KNOWN_PLAYERS) {
            if (!safeName.equals(knownPlayers.get(playerId))) {
                knownPlayers.put(playerId, safeName);
                setDirty();
            }
        }
        MutablePlayerStats stats = playerStats.get(playerId);
        if (stats != null && !safeName.isBlank() && !safeName.equals(stats.name)) {
            stats.name = safeName;
            setDirty();
        }
    }

    public void recordRadiantRelic(UUID playerId, String playerName) {
        stats(playerId, playerName).radiantRelics++;
        setDirty();
    }

    public void recordShinyGacha(UUID playerId, String playerName) {
        stats(playerId, playerName).shinyGacha++;
        setDirty();
    }

    public void recordTreasureRabbit(UUID playerId, String playerName, TreasureRabbitVariant variant) {
        MutablePlayerStats stats = stats(playerId, playerName);
        switch (variant == null ? TreasureRabbitVariant.GOLDEN : variant) {
            case RADIANT -> stats.radiantRabbits++;
            case MIRROR -> stats.mirrorRabbits++;
            case CROWN -> stats.crownRabbits++;
            default -> stats.goldenRabbits++;
        }
        setDirty();
    }

    public void recordMarketSale(UUID playerId, String playerName, long revenue) {
        MutablePlayerStats stats = stats(playerId, playerName);
        stats.marketSales++;
        stats.marketRevenue = saturatingAdd(stats.marketRevenue, Math.max(0L, revenue));
        setDirty();
    }

    public void recordBreakingNews(BreakingNewsReport report) {
        if (report == null) {
            return;
        }
        while (breakingNews.size() >= MAX_BREAKING_NEWS) {
            breakingNews.remove(0);
        }
        BreakingNewsReport safe = report.sanitized();
        breakingNews.add(safe);
        while (incidentArchive.size() >= MAX_INCIDENT_ARCHIVE) {
            incidentArchive.remove(0);
        }
        incidentArchive.add(safe);
        setDirty();
    }

    public void archiveIssue(ArchivedIssue issue) {
        if (issue == null || issue.issueId().isBlank()
                || issueArchive.stream().anyMatch(existing -> existing.issueId().equals(issue.issueId()))) {
            return;
        }
        while (issueArchive.size() >= MAX_ISSUE_ARCHIVE) {
            issueArchive.remove(0);
        }
        issueArchive.add(issue.sanitized());
        setDirty();
    }

    public List<BreakingNewsReport> incidentArchive() { return List.copyOf(incidentArchive); }
    public List<ArchivedIssue> issueArchive() { return List.copyOf(issueArchive); }

    public Snapshot snapshot() {
        Map<UUID, PlayerStats> copied = new LinkedHashMap<>();
        playerStats.forEach((uuid, stats) -> copied.put(uuid, stats.snapshot()));
        return new Snapshot(currentWeekKey, currentWeekStartedAt,
                Map.copyOf(knownPlayers), Map.copyOf(copied), List.copyOf(breakingNews));
    }

    private MutablePlayerStats stats(UUID playerId, String playerName) {
        touchPlayer(playerId, playerName);
        return playerStats.computeIfAbsent(playerId,
                ignored -> new MutablePlayerStats(playerName == null ? "" : playerName));
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putString("currentWeekKey", currentWeekKey);
        tag.putLong("currentWeekStartedAt", currentWeekStartedAt);
        tag.putString("lastAutoPublishedWeekKey", lastAutoPublishedWeekKey);

        ListTag known = new ListTag();
        knownPlayers.forEach((uuid, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            entry.putString("name", name);
            known.add(entry);
        });
        tag.put("knownPlayers", known);

        ListTag statistics = new ListTag();
        playerStats.forEach((uuid, stats) -> {
            CompoundTag entry = stats.save();
            entry.putUUID("uuid", uuid);
            statistics.add(entry);
        });
        tag.put("playerStats", statistics);

        ListTag incidents = new ListTag();
        for (BreakingNewsReport report : breakingNews) {
            incidents.add(report.save());
        }
        tag.put("breakingNews", incidents);
        ListTag archivedIncidents = new ListTag();
        for (BreakingNewsReport report : incidentArchive) archivedIncidents.add(report.save());
        tag.put("incidentArchive", archivedIncidents);
        ListTag archivedIssues = new ListTag();
        for (ArchivedIssue issue : issueArchive) archivedIssues.add(issue.save());
        tag.put("issueArchive", archivedIssues);
        return tag;
    }

    private static WeeklyNewspaperSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WeeklyNewspaperSavedData data = new WeeklyNewspaperSavedData();
        if (tag.getInt("dataVersion") != DATA_VERSION) {
            return data;
        }
        data.currentWeekKey = tag.getString("currentWeekKey");
        data.currentWeekStartedAt = Math.max(0L, tag.getLong("currentWeekStartedAt"));
        data.lastAutoPublishedWeekKey = tag.getString("lastAutoPublishedWeekKey");

        ListTag known = tag.getList("knownPlayers", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(known.size(), MAX_KNOWN_PLAYERS); index++) {
            CompoundTag entry = known.getCompound(index);
            if (entry.hasUUID("uuid")) {
                data.knownPlayers.put(entry.getUUID("uuid"), entry.getString("name"));
            }
        }

        ListTag statistics = tag.getList("playerStats", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(statistics.size(), MAX_KNOWN_PLAYERS); index++) {
            CompoundTag entry = statistics.getCompound(index);
            if (entry.hasUUID("uuid")) {
                data.playerStats.put(entry.getUUID("uuid"), MutablePlayerStats.load(entry));
            }
        }
        ListTag incidents = tag.getList("breakingNews", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(incidents.size(), MAX_BREAKING_NEWS); index++) {
            data.breakingNews.add(BreakingNewsReport.load(incidents.getCompound(index)));
        }
        ListTag archivedIncidents = tag.getList("incidentArchive", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(archivedIncidents.size(), MAX_INCIDENT_ARCHIVE); index++) {
            data.incidentArchive.add(BreakingNewsReport.load(archivedIncidents.getCompound(index)));
        }
        ListTag archivedIssues = tag.getList("issueArchive", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(archivedIssues.size(), MAX_ISSUE_ARCHIVE); index++) {
            data.issueArchive.add(ArchivedIssue.load(archivedIssues.getCompound(index)));
        }
        return data;
    }

    private static final class MutablePlayerStats {
        private String name;
        private int radiantRelics;
        private int shinyGacha;
        private int goldenRabbits;
        private int radiantRabbits;
        private int mirrorRabbits;
        private int crownRabbits;
        private int marketSales;
        private long marketRevenue;

        private MutablePlayerStats(String name) {
            this.name = name == null ? "" : name;
        }

        private PlayerStats snapshot() {
            return new PlayerStats(name, radiantRelics, shinyGacha, goldenRabbits,
                    radiantRabbits, mirrorRabbits, crownRabbits, marketSales, marketRevenue);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putInt("radiantRelics", Math.max(0, radiantRelics));
            tag.putInt("shinyGacha", Math.max(0, shinyGacha));
            tag.putInt("goldenRabbits", Math.max(0, goldenRabbits));
            tag.putInt("radiantRabbits", Math.max(0, radiantRabbits));
            tag.putInt("mirrorRabbits", Math.max(0, mirrorRabbits));
            tag.putInt("crownRabbits", Math.max(0, crownRabbits));
            tag.putInt("marketSales", Math.max(0, marketSales));
            tag.putLong("marketRevenue", Math.max(0L, marketRevenue));
            return tag;
        }

        private static MutablePlayerStats load(CompoundTag tag) {
            MutablePlayerStats stats = new MutablePlayerStats(tag.getString("name"));
            stats.radiantRelics = Math.max(0, tag.getInt("radiantRelics"));
            stats.shinyGacha = Math.max(0, tag.getInt("shinyGacha"));
            stats.goldenRabbits = Math.max(0, tag.getInt("goldenRabbits"));
            stats.radiantRabbits = Math.max(0, tag.getInt("radiantRabbits"));
            stats.mirrorRabbits = Math.max(0, tag.getInt("mirrorRabbits"));
            stats.crownRabbits = Math.max(0, tag.getInt("crownRabbits"));
            stats.marketSales = Math.max(0, tag.getInt("marketSales"));
            stats.marketRevenue = Math.max(0L, tag.getLong("marketRevenue"));
            return stats;
        }
    }

    public record Snapshot(String weekKey, long weekStartedAt, Map<UUID, String> knownPlayers,
                           Map<UUID, PlayerStats> playerStats,
                           List<BreakingNewsReport> breakingNews) {
    }

    public record BreakingNewsReport(String type, long startedAt, long endedAt,
                                     String biomeId, String directionId, String distanceId,
                                     String targetSpecies, int goal,
                                     int spawned, int primaryCount, int secondaryCount,
                                     int tertiaryCount, int rewardCount, int participantCount) {
        private BreakingNewsReport sanitized() {
            return new BreakingNewsReport(safe(type), Math.max(0L, startedAt), Math.max(0L, endedAt),
                    safe(biomeId), safe(directionId), safe(distanceId), safe(targetSpecies), Math.max(0, goal),
                    Math.max(0, spawned), Math.max(0, primaryCount), Math.max(0, secondaryCount),
                    Math.max(0, tertiaryCount), Math.max(0, rewardCount), Math.max(0, participantCount));
        }

        private CompoundTag save() {
            BreakingNewsReport value = sanitized();
            CompoundTag tag = new CompoundTag();
            tag.putString("type", value.type);
            tag.putLong("startedAt", value.startedAt);
            tag.putLong("endedAt", value.endedAt);
            tag.putString("biomeId", value.biomeId);
            tag.putString("directionId", value.directionId);
            tag.putString("distanceId", value.distanceId);
            tag.putString("targetSpecies", value.targetSpecies);
            tag.putInt("goal", value.goal);
            tag.putInt("spawned", value.spawned);
            tag.putInt("primaryCount", value.primaryCount);
            tag.putInt("secondaryCount", value.secondaryCount);
            tag.putInt("tertiaryCount", value.tertiaryCount);
            tag.putInt("rewardCount", value.rewardCount);
            tag.putInt("participantCount", value.participantCount);
            return tag;
        }

        private static BreakingNewsReport load(CompoundTag tag) {
            return new BreakingNewsReport(tag.getString("type"), tag.getLong("startedAt"),
                    tag.getLong("endedAt"), tag.getString("biomeId"),
                    tag.getString("directionId"), tag.getString("distanceId"),
                    tag.getString("targetSpecies"), tag.getInt("goal"),
                    tag.getInt("spawned"), tag.getInt("primaryCount"),
                    tag.getInt("secondaryCount"), tag.getInt("tertiaryCount"),
                    tag.getInt("rewardCount"), tag.getInt("participantCount")).sanitized();
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    public record PlayerStats(String name, int radiantRelics, int shinyGacha,
                              int goldenRabbits, int radiantRabbits, int mirrorRabbits, int crownRabbits,
                              int marketSales, long marketRevenue) {
        public int totalRabbits() {
            return goldenRabbits + radiantRabbits + mirrorRabbits + crownRabbits;
        }
    }

    public record ArchivedIssue(String issueId, String weekKey, long publishedAt,
                                String title, String message) {
        private ArchivedIssue sanitized() {
            return new ArchivedIssue(safeText(issueId), safeText(weekKey), Math.max(0L, publishedAt),
                    safeText(title), safeText(message));
        }

        private CompoundTag save() {
            ArchivedIssue value = sanitized();
            CompoundTag tag = new CompoundTag();
            tag.putString("issueId", value.issueId);
            tag.putString("weekKey", value.weekKey);
            tag.putLong("publishedAt", value.publishedAt);
            tag.putString("title", value.title);
            tag.putString("message", value.message);
            return tag;
        }

        private static ArchivedIssue load(CompoundTag tag) {
            return new ArchivedIssue(tag.getString("issueId"), tag.getString("weekKey"),
                    tag.getLong("publishedAt"), tag.getString("title"), tag.getString("message")).sanitized();
        }

        private static String safeText(String value) { return value == null ? "" : value; }
    }
}
