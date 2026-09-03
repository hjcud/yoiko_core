package com.yoiko.core.turtle;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;
import com.yoiko.core.turtle.race.TurtleCompetitionData;
import com.yoiko.core.turtle.race.TurtleWeeklyResult;

public final class TurtleRacingSavedData extends SavedData {
    public static final String DATA_NAME = YoikoServerCore.MODID + "_turtle_racing";
    public static final int DATA_VERSION = 6;
    private static final int MAX_TURTLES_ON_LOAD = 200_000;
    private static final int MAX_PLAYERS_ON_LOAD = 100_000;

    private final Map<UUID, TurtleData> turtles = new HashMap<>();
    private final Map<UUID, TurtlePlayerProgress> players = new HashMap<>();
    private long revision = 1L;
    private String arenaDimension = "";
    private BlockPos arenaCenter;
    private TurtleCompetitionData competition;
    private String lastScheduledPeriodKey = "";
    private long spectatorCarryoverGold;
    private String manualOfficialOpenedPeriodKey = "";
    private String activeWeeklyKey = "";
    private String settledWeeklyKey = "";
    private final List<TurtleWeeklyResult> weeklyResults = new ArrayList<>();
    /** Blocks outside the normal arena reset cuboid that were replaced by generated trees. */
    private final Map<BlockPos, BlockState> arenaTreeRestore = new HashMap<>();

    public static TurtleRacingSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TurtleRacingSavedData::new, TurtleRacingSavedData::load,
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE), DATA_NAME);
    }

    public TurtlePlayerProgress getOrCreatePlayer(UUID playerId) {
        return players.computeIfAbsent(playerId, id -> {
            markChanged();
            return new TurtlePlayerProgress(id);
        });
    }

    public Optional<TurtleData> turtle(UUID id) { return Optional.ofNullable(turtles.get(id)); }
    public Collection<TurtleData> turtles() { return List.copyOf(turtles.values()); }
    public Collection<TurtlePlayerProgress> players() { return List.copyOf(players.values()); }
    public long revision() { return revision; }
    public String arenaDimension() { return arenaDimension; }
    public Optional<BlockPos> arenaCenter() { return Optional.ofNullable(arenaCenter); }
    public Optional<TurtleCompetitionData> competition() { return Optional.ofNullable(competition); }
    public String lastScheduledPeriodKey() { return lastScheduledPeriodKey; }
    public long spectatorCarryoverGold() { return spectatorCarryoverGold; }
    public String manualOfficialOpenedPeriodKey() { return manualOfficialOpenedPeriodKey; }
    public String activeWeeklyKey(){return activeWeeklyKey;} public String settledWeeklyKey(){return settledWeeklyKey;} public List<TurtleWeeklyResult> weeklyResults(){return List.copyOf(weeklyResults);}
    public Map<BlockPos,BlockState> arenaTreeRestore(){return Map.copyOf(arenaTreeRestore);}

    public void setArena(String dimension, BlockPos center) {
        this.arenaDimension = dimension;
        this.arenaCenter = center.immutable();
        markChanged();
    }

    public void setCompetition(TurtleCompetitionData value) { competition = value; markChanged(); }
    public void clearCompetition() { competition = null; markChanged(); }
    public void setLastScheduledPeriodKey(String value) { lastScheduledPeriodKey = value; markChanged(); }
    public void addSpectatorCarryoverGold(long amount) { spectatorCarryoverGold = Math.max(0, spectatorCarryoverGold + amount); markChanged(); }
    public long takeSpectatorCarryoverGold() {
        long value = spectatorCarryoverGold;
        spectatorCarryoverGold = 0L;
        if (value > 0L) markChanged();
        return value;
    }
    public void setManualOfficialOpenedPeriodKey(String value) { manualOfficialOpenedPeriodKey=value; markChanged(); }
    public void setActiveWeeklyKey(String value){activeWeeklyKey=value;markChanged();} public void setSettledWeeklyKey(String value){settledWeeklyKey=value;markChanged();}
    public void addWeeklyResult(TurtleWeeklyResult value){weeklyResults.add(value);while(weeklyResults.size()>20_000)weeklyResults.remove(0);markChanged();}
    public void clearWeeklyResults(String weekKey){weeklyResults.removeIf(value->value.weekKey().equals(weekKey));markChanged();}
    public void setArenaTreeRestore(Map<BlockPos,BlockState> values){arenaTreeRestore.clear();values.forEach((pos,state)->arenaTreeRestore.put(pos.immutable(),state));markChanged();}
    public void clearArenaTreeRestore(){if(arenaTreeRestore.isEmpty())return;arenaTreeRestore.clear();markChanged();}

    public List<TurtleData> ownedBy(UUID playerId) {
        TurtlePlayerProgress progress = players.get(playerId);
        if (progress == null) return List.of();
        return progress.turtleIds().stream().map(turtles::get).filter(java.util.Objects::nonNull).toList();
    }

    public void addTurtle(TurtleData turtle) {
        if (turtles.containsKey(turtle.id())) throw new IllegalStateException("Duplicate turtle id");
        TurtlePlayerProgress owner = getOrCreatePlayer(turtle.ownerId());
        owner.addTurtle(turtle.id());
        turtles.put(turtle.id(), turtle);
        markChanged();
    }

    public TurtleData releaseTurtle(UUID ownerId, UUID turtleId) {
        TurtleData turtle = turtles.get(turtleId);
        if (turtle == null || !turtle.ownerId().equals(ownerId)) throw new IllegalArgumentException("Turtle is not owned by player");
        if (turtle.locked()) throw new IllegalStateException("Locked turtle cannot be released");
        TurtlePlayerProgress progress = getOrCreatePlayer(ownerId);
        if (!progress.removeTurtle(turtleId)) throw new IllegalStateException("Ownership index is inconsistent");
        turtles.remove(turtleId);
        markChanged();
        return turtle;
    }

    public void markChanged() {
        revision = revision == Long.MAX_VALUE ? 1L : revision + 1L;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putLong("revision", revision);
        tag.putString("arenaDimension", arenaDimension);
        if (arenaCenter != null) tag.putIntArray("arenaCenter", new int[]{arenaCenter.getX(), arenaCenter.getY(), arenaCenter.getZ()});
        if (competition != null) tag.put("competition", competition.save());
        tag.putString("lastScheduledPeriodKey", lastScheduledPeriodKey);
        tag.putLong("spectatorCarryoverGold", spectatorCarryoverGold);
        tag.putString("manualOfficialOpenedPeriodKey", manualOfficialOpenedPeriodKey);
        tag.putString("activeWeeklyKey",activeWeeklyKey);tag.putString("settledWeeklyKey",settledWeeklyKey);ListTag weekly=new ListTag();weeklyResults.forEach(value->weekly.add(value.save()));tag.put("weeklyResults",weekly);
        ListTag treeRestore=new ListTag();arenaTreeRestore.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparingLong(BlockPos::asLong))).forEach(entry->{CompoundTag value=new CompoundTag();value.putLong("pos",entry.getKey().asLong());value.put("state",NbtUtils.writeBlockState(entry.getValue()));treeRestore.add(value);});tag.put("arenaTreeRestore",treeRestore);
        ListTag turtleTags = new ListTag();
        turtles.values().stream().sorted(java.util.Comparator.comparing(value -> value.id().toString()))
                .forEach(value -> turtleTags.add(value.save()));
        tag.put("turtles", turtleTags);
        ListTag playerTags = new ListTag();
        players.values().stream().sorted(java.util.Comparator.comparing(value -> value.playerId().toString()))
                .forEach(value -> playerTags.add(value.save()));
        tag.put("players", playerTags);
        return tag;
    }

    private static TurtleRacingSavedData load(CompoundTag raw, HolderLookup.Provider registries) {
        int version = Math.max(0, raw.getInt("dataVersion"));
        if (version != DATA_VERSION) {
            throw new IllegalStateException("Turtle racing data version " + version + " does not match " + DATA_VERSION + "; reset development data");
        }
        TurtleRacingSavedData data = new TurtleRacingSavedData();
        data.revision = Math.max(1L, raw.getLong("revision"));
        data.arenaDimension = raw.getString("arenaDimension");
        int[] center = raw.getIntArray("arenaCenter");
        if (center.length == 3) data.arenaCenter = new BlockPos(center[0], center[1], center[2]);
        if (raw.contains("competition", Tag.TAG_COMPOUND)) data.competition = TurtleCompetitionData.load(raw.getCompound("competition"));
        data.lastScheduledPeriodKey = raw.getString("lastScheduledPeriodKey");
        data.spectatorCarryoverGold = Math.max(0, raw.getLong("spectatorCarryoverGold"));
        data.manualOfficialOpenedPeriodKey = raw.getString("manualOfficialOpenedPeriodKey");
        data.activeWeeklyKey=raw.getString("activeWeeklyKey");data.settledWeeklyKey=raw.getString("settledWeeklyKey");ListTag weekly=raw.getList("weeklyResults",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(weekly.size(),20_000);i++)data.weeklyResults.add(TurtleWeeklyResult.load(weekly.getCompound(i)));
        ListTag treeRestore=raw.getList("arenaTreeRestore",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(treeRestore.size(),20_000);i++){CompoundTag value=treeRestore.getCompound(i);data.arenaTreeRestore.put(BlockPos.of(value.getLong("pos")),NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),value.getCompound("state")));}
        ListTag turtleTags = raw.getList("turtles", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(turtleTags.size(), MAX_TURTLES_ON_LOAD); i++) {
            TurtleData turtle = TurtleData.load(turtleTags.getCompound(i));
            if (data.turtles.putIfAbsent(turtle.id(), turtle) != null) throw new IllegalArgumentException("Duplicate turtle UUID at saved index " + i);
        }
        ListTag playerTags = raw.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(playerTags.size(), MAX_PLAYERS_ON_LOAD); i++) {
            TurtlePlayerProgress progress = TurtlePlayerProgress.load(playerTags.getCompound(i));
            if (data.players.putIfAbsent(progress.playerId(), progress) != null) throw new IllegalArgumentException("Duplicate turtle player UUID at saved index " + i);
        }
        data.validateOwnershipIndexes();
        return data;
    }

    private void validateOwnershipIndexes() {
        for (TurtleData turtle : turtles.values()) {
            TurtlePlayerProgress progress = players.get(turtle.ownerId());
            if (progress == null || !progress.turtleIds().contains(turtle.id())) throw new IllegalStateException("Turtle ownership index is inconsistent; reset development data");
        }
        for (TurtlePlayerProgress progress : players.values()) {
            for (UUID turtleId : progress.turtleIds()) {
                TurtleData turtle = turtles.get(turtleId);
                if (turtle == null || !turtle.ownerId().equals(progress.playerId())) throw new IllegalStateException("Player turtle index is inconsistent; reset development data");
            }
        }
    }
}
