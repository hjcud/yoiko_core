package com.yoiko.core.turtle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.yoiko.core.turtle.time.TimeTrialRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public final class TurtlePlayerProgress {
    private final UUID playerId;
    private final List<UUID> turtleIds = new ArrayList<>();
    private final EnumMap<TurtleTicketType, Integer> tickets = new EnumMap<>(TurtleTicketType.class);
    private final EnumSet<TurtleCompanion> unlockedCompanions = EnumSet.noneOf(TurtleCompanion.class);
    private final LinkedHashSet<String> unlockedAppearances = new LinkedHashSet<>();
    private final LinkedHashSet<String> favoriteAppearances = new LinkedHashSet<>();
    private final EnumSet<TurtleStrategy> goldenShellStrategyWins = EnumSet.noneOf(TurtleStrategy.class);
    private String trainingPeriodKey = "";
    private int dailyBaseTraining = 6;
    private int restedTraining;
    private int bonusTraining;
    private int rarePity;
    private int epicPity;
    private int legendaryPity;
    private int pickupActivePity;
    private int shellMedals;
    private int turtlesHatched;
    private int officialFinishes;
    private int officialWins;
    private String medalAwakeningWeek = "";
    private int recentUnselectedCount;
    private String bettingPeriodKey="";
    private long dailyBetStake;
    private long revision = 1L;
    private final LinkedHashSet<String> rewardKeys = new LinkedHashSet<>();
    private final LinkedHashMap<String, TimeTrialRecord> timeTrialRecords = new LinkedHashMap<>();
    private PendingPassiveReroll pendingPassiveReroll;

    public record PendingPassiveReroll(UUID turtleId, int slot, String currentPassive,
                                       String candidatePassive) {
        public PendingPassiveReroll {
            if (turtleId == null || slot < 0 || slot >= 4
                    || currentPassive == null || currentPassive.isBlank()
                    || candidatePassive == null || candidatePassive.isBlank()
                    || currentPassive.length() > 64 || candidatePassive.length() > 64) {
                throw new IllegalArgumentException("Invalid pending passive reroll");
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("turtleId", turtleId);
            tag.putInt("slot", slot);
            tag.putString("currentPassive", currentPassive);
            tag.putString("candidatePassive", candidatePassive);
            return tag;
        }

        private static PendingPassiveReroll load(CompoundTag tag) {
            return new PendingPassiveReroll(tag.getUUID("turtleId"), tag.getInt("slot"),
                    tag.getString("currentPassive"), tag.getString("candidatePassive"));
        }
    }

    public TurtlePlayerProgress(UUID playerId) {
        this.playerId = playerId;
        for (TurtleTicketType type : TurtleTicketType.values()) tickets.put(type, 0);
    }

    public UUID playerId() { return playerId; }
    public List<UUID> turtleIds() { return List.copyOf(turtleIds); }
    public int ticketCount(TurtleTicketType type) { return tickets.getOrDefault(type, 0); }
    public Set<TurtleCompanion> unlockedCompanions() { return Set.copyOf(unlockedCompanions); }
    public Set<String> unlockedAppearances() { return Set.copyOf(unlockedAppearances); }
    public boolean hasAppearance(String id) { return unlockedAppearances.contains(id); }
    public Set<String> favoriteAppearances() { return Set.copyOf(favoriteAppearances); }
    public boolean isFavoriteAppearance(String id) { return favoriteAppearances.contains(id); }
    public Set<TurtleStrategy> goldenShellStrategyWins() { return Set.copyOf(goldenShellStrategyWins); }
    public boolean hasGoldenShell() { return goldenShellStrategyWins.size() == TurtleStrategy.values().length; }
    public int availableTraining() { return dailyBaseTraining + restedTraining + bonusTraining; }
    public int bonusTraining(){return bonusTraining;}
    public int shellMedals() { return shellMedals; }
    public int turtlesHatched() { return turtlesHatched; }
    public int officialFinishes() { return officialFinishes; }
    public int officialWins() { return officialWins; }
    public boolean usedMedalAwakening(String weekKey) { return medalAwakeningWeek.equals(weekKey); }
    public int recentUnselectedCount() { return recentUnselectedCount; }
    public long dailyBetStake(String periodKey){return bettingPeriodKey.equals(periodKey)?dailyBetStake:0L;}
    public int rarePity() { return rarePity; }
    public int epicPity() { return epicPity; }
    public int legendaryPity() { return legendaryPity; }
    public int pickupActivePity() { return pickupActivePity; }
    public long revision() { return revision; }
    public Optional<PendingPassiveReroll> pendingPassiveReroll() { return Optional.ofNullable(pendingPassiveReroll); }
    public void setPendingPassiveReroll(PendingPassiveReroll pending) {
        if (pendingPassiveReroll != null) throw new IllegalStateException("Passive reroll is already pending");
        pendingPassiveReroll = pending;
        changed();
    }
    public boolean clearPendingPassiveReroll() {
        if (pendingPassiveReroll == null) return false;
        pendingPassiveReroll = null;
        changed();
        return true;
    }
    public boolean hasReward(String key) { return rewardKeys.contains(key); }
    public boolean claimReward(String key) {
        if (!rewardKeys.add(key)) return false;
        while (rewardKeys.size() > 500) rewardKeys.remove(rewardKeys.iterator().next());
        changed(); return true;
    }
    public TimeTrialRecord timeTrialRecord(String key) { return timeTrialRecords.get(key); }
    public int timeTrialRecordCount(){return timeTrialRecords.size();}
    public boolean recordTimeTrial(TimeTrialRecord record) {
        TimeTrialRecord before=timeTrialRecords.get(record.key());if(before!=null&&before.finishMillis()<=record.finishMillis())return false;
        timeTrialRecords.put(record.key(),record);while(timeTrialRecords.size()>24)timeTrialRecords.remove(timeTrialRecords.keySet().iterator().next());changed();return true;
    }

    public void refreshTraining(String periodKey) {
        if (periodKey.equals(trainingPeriodKey)) return;
        if (!trainingPeriodKey.isEmpty()) restedTraining = Math.min(6, restedTraining + dailyBaseTraining);
        trainingPeriodKey = periodKey;
        dailyBaseTraining = 6;
        changed();
    }

    public boolean consumeTraining() {
        if (dailyBaseTraining > 0) dailyBaseTraining--;
        else if (restedTraining > 0) restedTraining--;
        else if (bonusTraining > 0) bonusTraining--;
        else return false;
        changed();
        return true;
    }

    public void addTurtle(UUID id) {
        if (turtleIds.size() >= 40) throw new IllegalStateException("Turtle ownership limit reached");
        if (!turtleIds.add(id)) throw new IllegalStateException("Duplicate turtle ownership");
        turtlesHatched = Math.min(1_000_000, turtlesHatched + 1);
        changed();
    }

    public boolean removeTurtle(UUID id) { boolean changed = turtleIds.remove(id); if (changed) changed(); return changed; }
    public void addTicket(TurtleTicketType type, int amount) { tickets.merge(type, Math.max(0, amount), Integer::sum); changed(); }
    public boolean consumeTicket(TurtleTicketType type) { int count=ticketCount(type); if(count<=0)return false; tickets.put(type,count-1); changed(); return true; }
    public int grantBonusTraining(int amount){int before=bonusTraining;bonusTraining=Math.min(10_000,bonusTraining+Math.max(0,amount));if(bonusTraining!=before)changed();return bonusTraining-before;}
    public void unlock(TurtleCompanion companion) { if (unlockedCompanions.add(companion)) changed(); }
    public void unlockAppearance(String id) { TurtleAppearanceCatalog.get(id); if (unlockedAppearances.add(id)) changed(); }
    public void toggleFavoriteAppearance(String id) {
        TurtleAppearanceCatalog.get(id);
        if (!favoriteAppearances.remove(id)) favoriteAppearances.add(id);
        changed();
    }
    public boolean recordGoldenShellStrategyWin(TurtleStrategy strategy) {
        boolean added=goldenShellStrategyWins.add(strategy);
        if(added)changed();
        return added;
    }
    public void addShellMedals(int amount) { shellMedals = Math.max(0, shellMedals + amount); changed(); }
    public boolean takeShellMedals(int amount) { if(amount<0||shellMedals<amount)return false; shellMedals-=amount; changed(); return true; }
    public void recordOfficialFinish() { officialFinishes=Math.min(1_000_000,officialFinishes+1); changed(); }
    public void recordOfficialWin() { officialWins=Math.min(1_000_000,officialWins+1); changed(); }
    public void markMedalAwakening(String weekKey) { medalAwakeningWeek=weekKey; changed(); }
    public void selectedForRace() { recentUnselectedCount=0; changed(); }
    public void notSelectedForRace() { recentUnselectedCount=Math.min(1_000_000,recentUnselectedCount+1); changed(); }
    public void recordBetStake(String periodKey,long amount){if(!bettingPeriodKey.equals(periodKey)){bettingPeriodKey=periodKey;dailyBetStake=0L;}dailyBetStake=Math.max(0L,Math.min(Long.MAX_VALUE-Math.max(0L,amount),dailyBetStake)+Math.max(0L,amount));changed();}
    public void refundBetStake(String periodKey,long amount){if(!bettingPeriodKey.equals(periodKey)||amount<=0L)return;dailyBetStake=Math.max(0L,dailyBetStake-amount);changed();}
    public void updatePity(TurtleRarity result, boolean pickupHit, boolean affectsRarity, boolean affectsPickup) {
        if (affectsRarity) {
            rarePity = result.ordinal() >= TurtleRarity.RARE.ordinal() ? 0 : rarePity + 1;
            epicPity = result.ordinal() >= TurtleRarity.EPIC.ordinal() ? 0 : epicPity + 1;
            legendaryPity = result == TurtleRarity.LEGENDARY ? 0 : legendaryPity + 1;
        }
        if (affectsPickup) pickupActivePity = pickupHit ? 0 : pickupActivePity + 1;
        changed();
    }

    private void changed() { revision = revision == Long.MAX_VALUE ? 1L : revision + 1L; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("playerId", playerId);
        ListTag ids = new ListTag();
        turtleIds.forEach(id -> ids.add(StringTag.valueOf(id.toString())));
        tag.put("turtleIds", ids);
        CompoundTag ticketTag = new CompoundTag();
        tickets.forEach((type, count) -> ticketTag.putInt(type.name(), count));
        tag.put("tickets", ticketTag);
        ListTag companions = new ListTag();
        unlockedCompanions.forEach(value -> companions.add(StringTag.valueOf(value.name())));
        tag.put("unlockedCompanions", companions);
        ListTag appearances = new ListTag();
        unlockedAppearances.forEach(value -> appearances.add(StringTag.valueOf(value)));
        tag.put("unlockedAppearances", appearances);
        ListTag favorites = new ListTag();
        favoriteAppearances.forEach(value -> favorites.add(StringTag.valueOf(value)));
        tag.put("favoriteAppearances", favorites);
        ListTag goldenWins = new ListTag();
        goldenShellStrategyWins.forEach(value -> goldenWins.add(StringTag.valueOf(value.name())));
        tag.put("goldenShellStrategyWins", goldenWins);
        tag.putString("trainingPeriodKey", trainingPeriodKey);
        tag.putInt("dailyBaseTraining", dailyBaseTraining);
        tag.putInt("restedTraining", restedTraining);
        tag.putInt("bonusTraining",bonusTraining);
        tag.putInt("rarePity", rarePity);
        tag.putInt("epicPity", epicPity);
        tag.putInt("legendaryPity", legendaryPity);
        tag.putInt("pickupActivePity", pickupActivePity);
        tag.putInt("shellMedals", shellMedals);
        tag.putInt("turtlesHatched", turtlesHatched);
        tag.putInt("officialFinishes", officialFinishes);
        tag.putInt("officialWins", officialWins);
        tag.putString("medalAwakeningWeek", medalAwakeningWeek);
        tag.putInt("recentUnselectedCount", recentUnselectedCount);
        tag.putString("bettingPeriodKey",bettingPeriodKey);
        tag.putLong("dailyBetStake",dailyBetStake);
        tag.putLong("revision", revision);
        ListTag rewards = new ListTag(); rewardKeys.forEach(value -> rewards.add(StringTag.valueOf(value))); tag.put("rewardKeys", rewards);
        ListTag records = new ListTag(); timeTrialRecords.values().forEach(value -> records.add(value.save())); tag.put("timeTrialRecords", records);
        if (pendingPassiveReroll != null) tag.put("pendingPassiveReroll", pendingPassiveReroll.save());
        return tag;
    }

    public static TurtlePlayerProgress load(CompoundTag tag) {
        TurtlePlayerProgress result = new TurtlePlayerProgress(tag.getUUID("playerId"));
        ListTag ids = tag.getList("turtleIds", Tag.TAG_STRING);
        for (int i=0; i<Math.min(ids.size(), 40); i++) result.turtleIds.add(UUID.fromString(ids.getString(i)));
        CompoundTag ticketTag = tag.getCompound("tickets");
        for (TurtleTicketType type : TurtleTicketType.values()) result.tickets.put(type, Math.max(0, ticketTag.getInt(type.name())));
        ListTag companions = tag.getList("unlockedCompanions", Tag.TAG_STRING);
        for (int i=0; i<companions.size(); i++) {
            try { result.unlockedCompanions.add(TurtleCompanion.valueOf(companions.getString(i))); } catch (IllegalArgumentException ignored) { }
        }
        ListTag appearances = tag.getList("unlockedAppearances", Tag.TAG_STRING);
        for (int i=0; i<appearances.size(); i++) {
            String id=appearances.getString(i);
            try { TurtleAppearanceCatalog.get(id); result.unlockedAppearances.add(id); }
            catch (IllegalArgumentException ignored) { }
        }
        ListTag favorites = tag.getList("favoriteAppearances", Tag.TAG_STRING);
        for (int i=0; i<favorites.size(); i++) {
            String id=favorites.getString(i);
            try { TurtleAppearanceCatalog.get(id); result.favoriteAppearances.add(id); }
            catch (IllegalArgumentException ignored) { }
        }
        ListTag goldenWins = tag.getList("goldenShellStrategyWins", Tag.TAG_STRING);
        for (int i=0; i<goldenWins.size(); i++) {
            try { result.goldenShellStrategyWins.add(TurtleStrategy.valueOf(goldenWins.getString(i))); }
            catch (IllegalArgumentException ignored) { }
        }
        result.trainingPeriodKey = tag.getString("trainingPeriodKey");
        result.dailyBaseTraining = Math.max(0, Math.min(6, tag.getInt("dailyBaseTraining")));
        result.restedTraining = Math.max(0, Math.min(6, tag.getInt("restedTraining")));
        result.bonusTraining=Math.max(0,Math.min(10_000,tag.getInt("bonusTraining")));
        result.rarePity = Math.max(0, tag.getInt("rarePity"));
        result.epicPity = Math.max(0, tag.getInt("epicPity"));
        result.legendaryPity = Math.max(0, tag.getInt("legendaryPity"));
        result.pickupActivePity = Math.max(0, tag.getInt("pickupActivePity"));
        result.shellMedals = Math.max(0, tag.getInt("shellMedals"));
        result.turtlesHatched = tag.contains("turtlesHatched", Tag.TAG_INT)
                ? Math.max(result.turtleIds.size(), tag.getInt("turtlesHatched"))
                : result.turtleIds.size();
        result.officialFinishes = Math.max(0, tag.getInt("officialFinishes"));
        result.officialWins = Math.max(0, tag.getInt("officialWins"));
        result.medalAwakeningWeek = tag.getString("medalAwakeningWeek");
        result.recentUnselectedCount = Math.max(0, tag.getInt("recentUnselectedCount"));
        result.bettingPeriodKey=tag.getString("bettingPeriodKey");
        result.dailyBetStake=Math.max(0L,tag.getLong("dailyBetStake"));
        result.revision = Math.max(1L, tag.getLong("revision"));
        ListTag rewards = tag.getList("rewardKeys", Tag.TAG_STRING);
        for (int i=0; i<Math.min(rewards.size(), 500); i++) result.rewardKeys.add(rewards.getString(i));
        ListTag records = tag.getList("timeTrialRecords", Tag.TAG_COMPOUND); for(int i=0;i<Math.min(records.size(),24);i++){TimeTrialRecord record=TimeTrialRecord.load(records.getCompound(i));result.timeTrialRecords.put(record.key(),record);}
        if (tag.contains("pendingPassiveReroll", Tag.TAG_COMPOUND)) {
            result.pendingPassiveReroll = PendingPassiveReroll.load(tag.getCompound("pendingPassiveReroll"));
        }
        return result;
    }
}
