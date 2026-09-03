package com.yoiko.core.event;

import com.yoiko.core.YoikoServerCore;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Persists the single active server-wide breaking-news incident and its result counters. */
public final class BreakingNewsSavedData extends SavedData {
    private static final String DATA_NAME = YoikoServerCore.MODID + "_breaking_news";
    // This feature is still in development; old incident data is intentionally discarded.
    private static final int DATA_VERSION = 4;

    private long runId;
    private String type = "";
    private long startedAt;
    private long activatedAt;
    private long endsAt;
    private long durationMillis;
    private boolean waitingForActivation;
    private long lastEndedAt;
    private long nextAutomaticCheckAt;
    private String lastFishingScheduleKey = "";
    private String biomeId = "";
    private String directionId = "";
    private String distanceId = "";
    private String dimensionId = "";
    private String targetSpecies = "";
    private int zoneCenterX;
    private int zoneCenterZ;
    private int zoneRadius;
    private int siteX;
    private int siteZ;
    private int goal;
    private int spawned;
    private int primaryCount;
    private int secondaryCount;
    private int tertiaryCount;
    private int rewardCount;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> rewardedPlayers = new LinkedHashSet<>();
    private final Set<UUID> eventEntities = new LinkedHashSet<>();
    private final Set<Long> forcedChunks = new LinkedHashSet<>();
    private final Map<UUID, Integer> contributions = new LinkedHashMap<>();

    public static BreakingNewsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BreakingNewsSavedData::new, BreakingNewsSavedData::load,
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE), DATA_NAME);
    }

    public boolean isActive(long now) {
        return !type.isBlank() && startedAt > 0L && (waitingForActivation || endsAt > now);
    }

    public String type() { return type; }
    public long runId() { return runId; }
    public long startedAt() { return startedAt; }
    public long activatedAt() { return activatedAt; }
    public long endsAt() { return endsAt; }
    public long lastEndedAt() { return lastEndedAt; }
    public long nextAutomaticCheckAt() { return nextAutomaticCheckAt; }
    public String lastFishingScheduleKey() { return lastFishingScheduleKey; }
    public boolean waitingForActivation() { return waitingForActivation; }
    public String biomeId() { return biomeId; }
    public String directionId() { return directionId; }
    public String distanceId() { return distanceId; }
    public String dimensionId() { return dimensionId; }
    public String targetSpecies() { return targetSpecies; }
    public int zoneCenterX() { return zoneCenterX; }
    public int zoneCenterZ() { return zoneCenterZ; }
    public int zoneRadius() { return zoneRadius; }
    public int siteX() { return siteX; }
    public int siteZ() { return siteZ; }
    public int goal() { return goal; }
    public int spawned() { return spawned; }
    public int primaryCount() { return primaryCount; }
    public int secondaryCount() { return secondaryCount; }
    public int tertiaryCount() { return tertiaryCount; }
    public int rewardCount() { return rewardCount; }
    public int participantCount() { return participants.size(); }
    public List<UUID> participants() { return List.copyOf(participants); }
    public Map<UUID, Integer> contributions() { return Map.copyOf(contributions); }
    public List<UUID> eventEntities() { return List.copyOf(eventEntities); }
    public List<Long> forcedChunks() { return List.copyOf(forcedChunks); }

    public long begin(String eventType, long now, long requestedDurationMillis,
                      boolean waitForActivation) {
        runId = Math.max(runId + 1L, now);
        type = safe(eventType);
        startedAt = now;
        activatedAt = waitForActivation ? 0L : now;
        durationMillis = Math.max(60_000L, requestedDurationMillis);
        waitingForActivation = waitForActivation;
        endsAt = waitForActivation ? 0L : now + durationMillis;
        clearIncidentDetails();
        setDirty();
        return runId;
    }

    public boolean activate(long now) {
        if (!waitingForActivation || type.isBlank()) return false;
        waitingForActivation = false;
        activatedAt = now;
        endsAt = now + durationMillis;
        setDirty();
        return true;
    }

    public void configureRabbitSwarm(String dimension, int mapCenterX, int mapCenterZ,
                                     int mapRadius, int rabbitSiteX, int rabbitSiteZ,
                                     String biome, String direction, String distance,
                                     List<UUID> entityIds, List<Long> newlyForcedChunks) {
        configureZone(dimension, mapCenterX, mapCenterZ, mapRadius);
        siteX = rabbitSiteX;
        siteZ = rabbitSiteZ;
        biomeId = safe(biome);
        directionId = safe(direction);
        distanceId = safe(distance);
        eventEntities.clear();
        if (entityIds != null) eventEntities.addAll(entityIds);
        forcedChunks.clear();
        if (newlyForcedChunks != null) forcedChunks.addAll(newlyForcedChunks);
        spawned = eventEntities.size();
        setDirty();
    }

    public void configureOutbreak(String dimension, int centerX, int centerZ, int radius,
                                  String species) {
        configureZone(dimension, centerX, centerZ, radius);
        targetSpecies = safe(species);
        setDirty();
    }

    public void configureFishingFestival(String dimension, int centerX, int centerZ, int radius,
                                         String biome, String direction, String distance) {
        configureZone(dimension, centerX, centerZ, radius);
        biomeId = safe(biome);
        directionId = safe(direction);
        distanceId = safe(distance);
        setDirty();
    }

    public void configureCaptureGoal(String species, int requestedGoal) {
        targetSpecies = safe(species);
        goal = Math.max(1, requestedGoal);
        setDirty();
    }

    private void configureZone(String dimension, int centerX, int centerZ, int radius) {
        dimensionId = safe(dimension);
        zoneCenterX = centerX;
        zoneCenterZ = centerZ;
        zoneRadius = Math.max(0, radius);
    }

    /** For fishing: primary=patterned Magikarp, secondary=Feebas, tertiary=shiny patterned Magikarp. */
    public void recordFishing(UUID playerId, boolean feebas, boolean shinyPatterned, long now) {
        if (!isTypeActive(BreakingNewsEventManager.Type.FISHING_FESTIVAL, now)) return;
        if (feebas) secondaryCount++;
        else {
            primaryCount++;
            if (shinyPatterned) tertiaryCount++;
        }
        addParticipant(playerId);
        setDirty();
    }

    public boolean claimFishingReward(UUID playerId, long now) {
        if (playerId == null || !isTypeActive(BreakingNewsEventManager.Type.FISHING_FESTIVAL, now)
                || !rewardedPlayers.add(playerId)) return false;
        addParticipant(playerId);
        rewardCount++;
        setDirty();
        return true;
    }

    /** For rabbit swarms: primary=caught, secondary=escaped, tertiary=radiant caught. */
    public void recordRabbitCaught(long rabbitRunId, UUID playerId, boolean radiant, long now) {
        if (rabbitRunId != runId || !isTypeActive(BreakingNewsEventManager.Type.TREASURE_RABBIT_SWARM, now)
                || waitingForActivation) return;
        primaryCount++;
        if (radiant) tertiaryCount++;
        addContribution(playerId, 1);
        setDirty();
    }

    public void recordRabbitEscaped(long rabbitRunId, long now) {
        if (rabbitRunId != runId || !isTypeActive(BreakingNewsEventManager.Type.TREASURE_RABBIT_SWARM, now)
                || waitingForActivation) return;
        secondaryCount++;
        setDirty();
    }

    /** For capture goals: primary=accepted captures. */
    public int recordCaptureGoal(UUID playerId, int perPlayerCap, long now) {
        if (playerId == null || !isTypeActive(BreakingNewsEventManager.Type.POKEMON_CAPTURE_GOAL, now)) return primaryCount;
        int current = contributions.getOrDefault(playerId, 0);
        if (current >= Math.max(1, perPlayerCap) || primaryCount >= goal) return primaryCount;
        primaryCount++;
        addContribution(playerId, 1);
        setDirty();
        return primaryCount;
    }

    /** For outbreaks: primary=target spawns, secondary=target captures, tertiary=shiny target spawns. */
    public void recordOutbreakSpawn(boolean shiny, long now) {
        if (!isTypeActive(BreakingNewsEventManager.Type.MASS_OUTBREAK, now)) return;
        primaryCount++;
        if (shiny) tertiaryCount++;
        setDirty();
    }

    public void recordOutbreakCapture(UUID playerId, long now) {
        if (!isTypeActive(BreakingNewsEventManager.Type.MASS_OUTBREAK, now)) return;
        secondaryCount++;
        addContribution(playerId, 1);
        setDirty();
    }

    public void addRewardCount(int amount) {
        rewardCount = Math.max(0, rewardCount + Math.max(0, amount));
        setDirty();
    }

    public void scheduleNextAutomaticCheck(long when) {
        nextAutomaticCheckAt = Math.max(0L, when);
        setDirty();
    }

    public void markFishingScheduleStarted(String scheduleKey) {
        lastFishingScheduleKey = safe(scheduleKey);
        setDirty();
    }

    public CompletedIncident finish(long now) {
        if (type.isBlank() || startedAt <= 0L) return null;
        if (BreakingNewsEventManager.Type.TREASURE_RABBIT_SWARM.id().equals(type)) {
            secondaryCount = Math.max(secondaryCount, Math.max(0, spawned - primaryCount));
        }
        CompletedIncident result = new CompletedIncident(type, startedAt, Math.max(startedAt, now),
                biomeId, directionId, distanceId, targetSpecies, goal, spawned,
                primaryCount, secondaryCount, tertiaryCount, rewardCount, participants.size());
        lastEndedAt = Math.max(lastEndedAt, now);
        clearActive();
        setDirty();
        return result;
    }

    public void cancelActive() { clearActive(); setDirty(); }

    private boolean isTypeActive(BreakingNewsEventManager.Type expected, long now) {
        return expected.id().equals(type) && isActive(now);
    }

    private void addParticipant(UUID playerId) { if (playerId != null) participants.add(playerId); }

    private void addContribution(UUID playerId, int amount) {
        if (playerId == null) return;
        participants.add(playerId);
        contributions.merge(playerId, Math.max(0, amount), Integer::sum);
    }

    private void clearIncidentDetails() {
        biomeId = "";
        directionId = "";
        distanceId = "";
        dimensionId = "";
        targetSpecies = "";
        zoneCenterX = zoneCenterZ = zoneRadius = siteX = siteZ = goal = 0;
        spawned = primaryCount = secondaryCount = tertiaryCount = rewardCount = 0;
        participants.clear();
        rewardedPlayers.clear();
        eventEntities.clear();
        forcedChunks.clear();
        contributions.clear();
    }

    private void clearActive() {
        type = "";
        startedAt = activatedAt = endsAt = durationMillis = 0L;
        waitingForActivation = false;
        clearIncidentDetails();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putLong("runId", runId);
        tag.putString("type", type);
        tag.putLong("startedAt", startedAt);
        tag.putLong("activatedAt", activatedAt);
        tag.putLong("endsAt", endsAt);
        tag.putLong("durationMillis", durationMillis);
        tag.putBoolean("waitingForActivation", waitingForActivation);
        tag.putLong("lastEndedAt", lastEndedAt);
        tag.putLong("nextAutomaticCheckAt", nextAutomaticCheckAt);
        tag.putString("lastFishingScheduleKey", lastFishingScheduleKey);
        tag.putString("biomeId", biomeId);
        tag.putString("directionId", directionId);
        tag.putString("distanceId", distanceId);
        tag.putString("dimensionId", dimensionId);
        tag.putString("targetSpecies", targetSpecies);
        tag.putInt("zoneCenterX", zoneCenterX);
        tag.putInt("zoneCenterZ", zoneCenterZ);
        tag.putInt("zoneRadius", zoneRadius);
        tag.putInt("siteX", siteX);
        tag.putInt("siteZ", siteZ);
        tag.putInt("goal", goal);
        tag.putInt("spawned", spawned);
        tag.putInt("primaryCount", primaryCount);
        tag.putInt("secondaryCount", secondaryCount);
        tag.putInt("tertiaryCount", tertiaryCount);
        tag.putInt("rewardCount", rewardCount);
        tag.put("participants", uuidList(participants));
        tag.put("rewardedPlayers", uuidList(rewardedPlayers));
        tag.put("eventEntities", uuidList(eventEntities));
        ListTag chunks = new ListTag();
        for (long chunk : forcedChunks) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("value", chunk);
            chunks.add(entry);
        }
        tag.put("forcedChunks", chunks);
        ListTag scores = new ListTag();
        contributions.forEach((uuid, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            entry.putInt("count", Math.max(0, count));
            scores.add(entry);
        });
        tag.put("contributions", scores);
        return tag;
    }

    private static BreakingNewsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        BreakingNewsSavedData data = new BreakingNewsSavedData();
        if (tag.getInt("dataVersion") != DATA_VERSION) return data;
        data.runId = Math.max(0L, tag.getLong("runId"));
        data.type = tag.getString("type");
        data.startedAt = Math.max(0L, tag.getLong("startedAt"));
        data.activatedAt = Math.max(0L, tag.getLong("activatedAt"));
        data.endsAt = Math.max(0L, tag.getLong("endsAt"));
        data.durationMillis = Math.max(0L, tag.getLong("durationMillis"));
        data.waitingForActivation = tag.getBoolean("waitingForActivation");
        data.lastEndedAt = Math.max(0L, tag.getLong("lastEndedAt"));
        data.nextAutomaticCheckAt = Math.max(0L, tag.getLong("nextAutomaticCheckAt"));
        data.lastFishingScheduleKey = tag.getString("lastFishingScheduleKey");
        data.biomeId = tag.getString("biomeId");
        data.directionId = tag.getString("directionId");
        data.distanceId = tag.getString("distanceId");
        data.dimensionId = tag.getString("dimensionId");
        data.targetSpecies = tag.getString("targetSpecies");
        data.zoneCenterX = tag.getInt("zoneCenterX");
        data.zoneCenterZ = tag.getInt("zoneCenterZ");
        data.zoneRadius = Math.max(0, tag.getInt("zoneRadius"));
        data.siteX = tag.getInt("siteX");
        data.siteZ = tag.getInt("siteZ");
        data.goal = Math.max(0, tag.getInt("goal"));
        data.spawned = Math.max(0, tag.getInt("spawned"));
        data.primaryCount = Math.max(0, tag.getInt("primaryCount"));
        data.secondaryCount = Math.max(0, tag.getInt("secondaryCount"));
        data.tertiaryCount = Math.max(0, tag.getInt("tertiaryCount"));
        data.rewardCount = Math.max(0, tag.getInt("rewardCount"));
        readUuidList(tag, "participants", data.participants);
        readUuidList(tag, "rewardedPlayers", data.rewardedPlayers);
        readUuidList(tag, "eventEntities", data.eventEntities);
        ListTag chunks = tag.getList("forcedChunks", Tag.TAG_COMPOUND);
        for (int index = 0; index < chunks.size(); index++) data.forcedChunks.add(chunks.getCompound(index).getLong("value"));
        ListTag scores = tag.getList("contributions", Tag.TAG_COMPOUND);
        for (int index = 0; index < scores.size(); index++) {
            CompoundTag entry = scores.getCompound(index);
            if (entry.hasUUID("uuid")) data.contributions.put(entry.getUUID("uuid"), Math.max(0, entry.getInt("count")));
        }
        return data;
    }

    private static ListTag uuidList(Set<UUID> values) {
        ListTag list = new ListTag();
        for (UUID value : values) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", value);
            list.add(entry);
        }
        return list;
    }

    private static void readUuidList(CompoundTag root, String key, Set<UUID> target) {
        ListTag list = root.getList(key, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.hasUUID("uuid")) target.add(entry.getUUID("uuid"));
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public record CompletedIncident(String type, long startedAt, long endedAt,
                                    String biomeId, String directionId, String distanceId,
                                    String targetSpecies, int goal, int spawned,
                                    int primaryCount, int secondaryCount, int tertiaryCount,
                                    int rewardCount, int participantCount) {
    }
}
