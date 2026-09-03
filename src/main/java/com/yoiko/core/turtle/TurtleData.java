package com.yoiko.core.turtle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public final class TurtleData {
    public static final int MAX_NAME_CODE_POINTS = 32;
    public static final int DEFAULT_RACE_RATING = 1500;
    public static final int MIN_RACE_RATING = 800;
    public static final int MAX_RACE_RATING = 2400;

    public record LastRaceSummary(int rank, boolean finished, long finishMillis, int staminaPercent, int overtakes,
                                  int laneChanges, int blockedTicks, int breaths, boolean activeUsed,
                                  int ratingChange) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("rank", rank); tag.putBoolean("finished",finished); tag.putLong("finishMillis", finishMillis);
            tag.putInt("staminaPercent", staminaPercent); tag.putInt("overtakes", overtakes);
            tag.putInt("laneChanges", laneChanges); tag.putInt("blockedTicks", blockedTicks);
            tag.putInt("breaths", breaths); tag.putBoolean("activeUsed", activeUsed);
            tag.putInt("ratingChange", ratingChange);
            return tag;
        }

        private static LastRaceSummary load(CompoundTag tag) {
            return new LastRaceSummary(Math.max(1, Math.min(8, tag.getInt("rank"))),!tag.contains("finished")||tag.getBoolean("finished"),
                    Math.max(0, tag.getLong("finishMillis")), Math.max(0, Math.min(100, tag.getInt("staminaPercent"))),
                    Math.max(0, tag.getInt("overtakes")), Math.max(0, tag.getInt("laneChanges")),
                    Math.max(0, tag.getInt("blockedTicks")), Math.max(0, tag.getInt("breaths")),
                    tag.getBoolean("activeUsed"), tag.getInt("ratingChange"));
        }
    }
    private final UUID id;
    private final UUID ownerId;
    private String name;
    private final TurtleRarity rarity;
    private final TurtleArchetype archetype;
    private final TurtleStats baseStats;
    private final TurtleStats stats;
    private final ActiveSkill activeSkill;
    private final List<String> passives;
    private final EnumMap<TurtleSurface, TurtleAptitude> surfaceAptitudes;
    private final EnumMap<TurtleStrategy, TurtleAptitude> strategyAptitudes;
    private TurtleAptitude shortDistanceAptitude;
    private TurtleAptitude middleDistanceAptitude;
    private TurtleAptitude longDistanceAptitude;
    private TurtleStrategy strategy = TurtleStrategy.STEADY;
    private TurtleCompanion companion;
    private int trainingCount;
    private int rerollCredits;
    private int rerollsUsed;
    private int awakeningPoints;
    private int racePoints;
    private int raceRating = DEFAULT_RACE_RATING;
    private int officialStarts;
    private int officialWins;
    private int officialPodiums;
    private long officialRankTotal;
    private long officialStaminaTotal;
    private final List<Integer> recentOfficialRanks = new ArrayList<>();
    private final EnumMap<TurtleStrategy, Integer> strategyStarts = new EnumMap<>(TurtleStrategy.class);
    private final EnumMap<TurtleStrategy, Integer> strategyWins = new EnumMap<>(TurtleStrategy.class);
    private LastRaceSummary lastOfficialRace;
    private boolean locked;
    private boolean favorite;
    private String appearance = TurtleAppearanceCatalog.DEFAULT_ID;
    private final String bodyAppearance;
    private long acquiredAt;
    private long revision;

    public TurtleData(UUID id, UUID ownerId, String name, TurtleRarity rarity, TurtleArchetype archetype,
                      TurtleStats baseStats, TurtleStats stats, ActiveSkill activeSkill, List<String> passives,
                      EnumMap<TurtleSurface, TurtleAptitude> surfaceAptitudes,
                      EnumMap<TurtleStrategy, TurtleAptitude> strategyAptitudes,
                      TurtleAptitude shortDistanceAptitude, TurtleAptitude middleDistanceAptitude,
                      TurtleAptitude longDistanceAptitude, String bodyAppearance, long acquiredAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = validateName(name);
        this.rarity = rarity;
        this.archetype = archetype;
        this.baseStats = baseStats.copy();
        this.stats = stats;
        this.activeSkill = activeSkill;
        this.passives = new ArrayList<>(passives);
        this.surfaceAptitudes = new EnumMap<>(surfaceAptitudes);
        this.strategyAptitudes = new EnumMap<>(strategyAptitudes);
        this.shortDistanceAptitude = shortDistanceAptitude;
        this.middleDistanceAptitude = middleDistanceAptitude;
        this.longDistanceAptitude = longDistanceAptitude;
        this.bodyAppearance = TurtleBodyAppearanceCatalog.get(bodyAppearance).id();
        this.acquiredAt = acquiredAt;
        this.revision = 1L;
        validate();
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public String name() { return name; }
    public Component displayName() { return TurtleNameCatalog.component(name); }
    public TurtleRarity rarity() { return rarity; }
    public TurtleArchetype archetype() { return archetype; }
    public TurtleStats baseStats() { return baseStats.copy(); }
    public TurtleStats stats() { return stats; }
    public ActiveSkill activeSkill() { return activeSkill; }
    public List<String> passives() { return List.copyOf(passives); }
    public TurtleStrategy strategy() { return strategy; }
    public TurtleCompanion companion() { return companion; }
    public int trainingCount() { return trainingCount; }
    public int rerollCredits() { return rerollCredits; }
    public int rerollsUsed() { return rerollsUsed; }
    public int awakeningPoints() { return awakeningPoints; }
    public int activePassiveSlots() { return awakeningPoints >= 30 ? 4 : awakeningPoints >= 15 ? 3 : awakeningPoints >= 5 ? 2 : 1; }
    public boolean locked() { return locked; }
    public String appearance() { return appearance; }
    public String bodyAppearance() { return bodyAppearance; }
    public long acquiredAt() { return acquiredAt; }
    public long revision() { return revision; }
    public int racePoints() { return racePoints; }
    public int raceRating() { return raceRating; }
    public int officialStarts() { return officialStarts; }
    public int officialWins() { return officialWins; }
    public int officialPodiums() { return officialPodiums; }
    public double averageOfficialRank() { return officialStarts == 0 ? 0 : officialRankTotal / (double) officialStarts; }
    public int averageOfficialStamina() { return officialStarts == 0 ? 0 : (int) Math.round(officialStaminaTotal / (double) officialStarts); }
    public List<Integer> recentOfficialRanks() { return List.copyOf(recentOfficialRanks); }
    public int strategyStarts(TurtleStrategy value) { return strategyStarts.getOrDefault(value, 0); }
    public int strategyWins(TurtleStrategy value) { return strategyWins.getOrDefault(value, 0); }
    public LastRaceSummary lastOfficialRace() { return lastOfficialRace; }
    public boolean favorite() { return favorite; }
    public TurtleRaceClass raceClass() { return TurtleRaceClass.forPoints(racePoints); }
    public TurtleLeague league() { return TurtleLeague.forRaceClass(raceClass()); }
    public String growthMark(TurtleStat stat) {
        double ratio = archetype.ratio(stat);
        double maximum = java.util.Arrays.stream(TurtleStat.values()).mapToDouble(archetype::ratio).max().orElse(.2);
        if (ratio >= maximum - 0.0001) return "◎";
        return ratio >= .20 ? "○" : "△";
    }
    public TurtleAptitude surfaceAptitude(TurtleSurface surface) { return surfaceAptitudes.getOrDefault(surface, TurtleAptitude.B); }
    public TurtleAptitude strategyAptitude(TurtleStrategy value) { return strategyAptitudes.getOrDefault(value, TurtleAptitude.B); }
    public TurtleAptitude distanceAptitude(double courseLength) {
        return courseLength <= 260 ? shortDistanceAptitude : courseLength >= 280 ? longDistanceAptitude : middleDistanceAptitude;
    }

    public void rename(String value) {
        String validated = validateName(value);
        if (TurtleNameCatalog.hasReservedPrefix(validated)) throw new IllegalArgumentException("Reserved turtle name prefix");
        name = validated;
        changed();
    }
    public void setStrategy(TurtleStrategy value) { strategy = value; changed(); }
    public void setCompanion(TurtleCompanion value) { companion = value; changed(); }
    public void setLocked(boolean value) { locked = value; changed(); }
    public void setFavorite(boolean value) { favorite = value; changed(); }
    public void setAppearance(String value) { TurtleAppearanceCatalog.get(value); appearance = value; changed(); }
    public void addAwakeningPoints(int amount) { awakeningPoints = Math.min(30, Math.max(0, awakeningPoints + amount)); changed(); }
    public void addRacePoints(int amount) { racePoints = Math.min(1_000_000, Math.max(0, racePoints + amount)); changed(); }
    public int adjustRaceRating(int amount) {
        int before = raceRating;
        raceRating = Math.max(MIN_RACE_RATING, Math.min(MAX_RACE_RATING, raceRating + amount));
        if (raceRating != before) changed();
        return raceRating - before;
    }

    public void recordOfficialRace(LastRaceSummary summary,TurtleStrategy raceStrategy) {
        officialStarts = Math.min(1_000_000, officialStarts + 1);
        if (summary.finished()&&summary.rank() == 1) officialWins = Math.min(1_000_000, officialWins + 1);
        if (summary.finished()&&summary.rank() <= 3) officialPodiums = Math.min(1_000_000, officialPodiums + 1);
        officialRankTotal = Math.min(Long.MAX_VALUE - 8, officialRankTotal + summary.rank());
        officialStaminaTotal = Math.min(Long.MAX_VALUE - 100, officialStaminaTotal + summary.staminaPercent());
        recentOfficialRanks.add(summary.rank());
        while (recentOfficialRanks.size() > 5) recentOfficialRanks.removeFirst();
        strategyStarts.merge(raceStrategy, 1, (left, right) -> Math.min(1_000_000, left + right));
        if (summary.rank() == 1 && summary.finished()) strategyWins.merge(raceStrategy, 1, (left, right) -> Math.min(1_000_000, left + right));
        lastOfficialRace = summary;
        changed();
    }

    void applyTraining(TurtleStat primary, TurtleStat secondary) {
        if (trainingCount >= 24) throw new IllegalStateException("Turtle has completed all training");
        if (stats.get(primary) + 2 > rarity.finalStatCap() || stats.get(secondary) + 1 > rarity.finalStatCap()) {
            throw new IllegalStateException("Training would exceed rarity stat cap");
        }
        stats.add(primary, 2);
        stats.add(secondary, 1);
        trainingCount++;
        if (trainingCount % 6 == 0) rerollCredits++;
        changed();
    }

    void resetTraining() {
        stats.replaceWith(baseStats);
        trainingCount = 0;
        rerollCredits = 0;
        changed();
    }

    void consumePassiveReroll() {
        if (rerollCredits <= 0 || rerollsUsed >= 4) throw new IllegalStateException("No passive retraining credit");
        rerollCredits--;
        rerollsUsed++;
        changed();
    }

    void applyPreparedPassive(int slot, String passiveId) {
        if (slot < 0 || slot >= passives.size()) throw new IllegalArgumentException("Invalid passive slot");
        PassiveSkillCatalog.get(passiveId);
        passives.set(slot, passiveId);
        changed();
    }

    private void changed() { revision = revision == Long.MAX_VALUE ? 1L : revision + 1L; }

    private void validate() {
        if (passives.size() != 4 || new HashSet<>(passives).size() != 4) {
            throw new IllegalArgumentException("A turtle must have four unique passives");
        }
        long specials = passives.stream().map(PassiveSkillCatalog::get).filter(PassiveSkill::special).count();
        if (specials > 1) throw new IllegalArgumentException("A turtle cannot have two special passives");
        if (stats.total() < 240 || stats.total() > rarity.finalBudget()) {
            throw new IllegalArgumentException("Invalid turtle stat budget: " + stats.total());
        }
        for (TurtleStat stat : TurtleStat.values()) {
            if (stats.get(stat) > rarity.finalStatCap()) throw new IllegalArgumentException("Stat exceeds rarity cap");
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("ownerId", ownerId);
        tag.putString("name", name);
        tag.putString("rarity", rarity.name());
        tag.putString("archetype", archetype.name());
        baseStats.save(tag, "baseStats");
        stats.save(tag, "stats");
        tag.putString("activeSkill", activeSkill.id());
        ListTag passiveTags = new ListTag();
        passives.forEach(value -> passiveTags.add(StringTag.valueOf(value)));
        tag.put("passives", passiveTags);
        tag.put("surfaceAptitudes", saveAptitudes(surfaceAptitudes));
        tag.put("strategyAptitudes", saveAptitudes(strategyAptitudes));
        tag.putString("shortDistance", shortDistanceAptitude.name());
        tag.putString("middleDistance", middleDistanceAptitude.name());
        tag.putString("longDistance", longDistanceAptitude.name());
        tag.putString("strategy", strategy.name());
        if (companion != null) tag.putString("companion", companion.name());
        tag.putInt("trainingCount", trainingCount);
        tag.putInt("rerollCredits", rerollCredits);
        tag.putInt("rerollsUsed", rerollsUsed);
        tag.putInt("awakeningPoints", awakeningPoints);
        tag.putInt("racePoints", racePoints);
        tag.putInt("raceRating", raceRating);
        tag.putInt("officialStarts", officialStarts);
        tag.putInt("officialWins", officialWins);
        tag.putInt("officialPodiums", officialPodiums);
        tag.putLong("officialRankTotal", officialRankTotal);
        tag.putLong("officialStaminaTotal", officialStaminaTotal);
        ListTag recentRanks = new ListTag();
        recentOfficialRanks.forEach(value -> recentRanks.add(net.minecraft.nbt.IntTag.valueOf(value)));
        tag.put("recentOfficialRanks", recentRanks);
        CompoundTag strategyStartTag = new CompoundTag(), strategyWinTag = new CompoundTag();
        for (TurtleStrategy value : TurtleStrategy.values()) {
            strategyStartTag.putInt(value.name(), strategyStarts(value));
            strategyWinTag.putInt(value.name(), strategyWins(value));
        }
        tag.put("strategyStarts", strategyStartTag); tag.put("strategyWins", strategyWinTag);
        if (lastOfficialRace != null) tag.put("lastOfficialRace", lastOfficialRace.save());
        tag.putBoolean("locked", locked);
        tag.putBoolean("favorite", favorite);
        tag.putString("appearance", appearance);
        tag.putString("bodyAppearance", bodyAppearance);
        tag.putLong("acquiredAt", acquiredAt);
        tag.putLong("revision", revision);
        return tag;
    }

    private static <E extends Enum<E>> CompoundTag saveAptitudes(EnumMap<E, TurtleAptitude> values) {
        CompoundTag tag = new CompoundTag();
        values.forEach((key, value) -> tag.putString(key.name(), value.name()));
        return tag;
    }

    public static TurtleData load(CompoundTag tag) {
        List<String> passives = new ArrayList<>();
        ListTag passiveTags = tag.getList("passives", Tag.TAG_STRING);
        for (int i = 0; i < passiveTags.size(); i++) passives.add(passiveTags.getString(i));
        EnumMap<TurtleSurface, TurtleAptitude> surfaces = loadAptitudes(tag.getCompound("surfaceAptitudes"), TurtleSurface.class);
        EnumMap<TurtleStrategy, TurtleAptitude> strategies = loadAptitudes(tag.getCompound("strategyAptitudes"), TurtleStrategy.class);
        TurtleData result = new TurtleData(tag.getUUID("id"), tag.getUUID("ownerId"), tag.getString("name"),
                TurtleRarity.valueOf(tag.getString("rarity")), TurtleArchetype.valueOf(tag.getString("archetype")),
                TurtleStats.load(tag, "baseStats"), TurtleStats.load(tag, "stats"), ActiveSkill.byId(tag.getString("activeSkill")), passives, surfaces, strategies,
                TurtleAptitude.valueOf(tag.getString("shortDistance")), TurtleAptitude.valueOf(tag.getString("middleDistance")),
                TurtleAptitude.valueOf(tag.getString("longDistance")), validatedBodyAppearance(tag), tag.getLong("acquiredAt"));
        result.strategy = tag.contains("strategy") ? TurtleStrategy.valueOf(tag.getString("strategy")) : TurtleStrategy.STEADY;
        result.companion = tag.contains("companion") ? TurtleCompanion.valueOf(tag.getString("companion")) : null;
        result.trainingCount = Math.max(0, Math.min(24, tag.getInt("trainingCount")));
        result.rerollCredits = Math.max(0, Math.min(4, tag.getInt("rerollCredits")));
        result.rerollsUsed = Math.max(0, Math.min(4, tag.getInt("rerollsUsed")));
        result.awakeningPoints = Math.max(0, Math.min(30, tag.getInt("awakeningPoints")));
        result.racePoints = Math.max(0, tag.getInt("racePoints"));
        result.raceRating = tag.contains("raceRating")
                ? Math.max(MIN_RACE_RATING, Math.min(MAX_RACE_RATING, tag.getInt("raceRating")))
                : DEFAULT_RACE_RATING;
        result.officialStarts = Math.max(0, tag.getInt("officialStarts"));
        result.officialWins = Math.max(0, Math.min(result.officialStarts, tag.getInt("officialWins")));
        result.officialPodiums = Math.max(result.officialWins, Math.min(result.officialStarts, tag.getInt("officialPodiums")));
        result.officialRankTotal = Math.max(0, tag.getLong("officialRankTotal"));
        result.officialStaminaTotal = Math.max(0, tag.getLong("officialStaminaTotal"));
        ListTag recentRanks = tag.getList("recentOfficialRanks", Tag.TAG_INT);
        for (int i = Math.max(0, recentRanks.size() - 5); i < recentRanks.size(); i++)
            result.recentOfficialRanks.add(Math.max(1, Math.min(8, recentRanks.getInt(i))));
        CompoundTag strategyStartTag = tag.getCompound("strategyStarts"), strategyWinTag = tag.getCompound("strategyWins");
        for (TurtleStrategy value : TurtleStrategy.values()) {
            result.strategyStarts.put(value, Math.max(0, strategyStartTag.getInt(value.name())));
            result.strategyWins.put(value, Math.max(0, strategyWinTag.getInt(value.name())));
        }
        if (tag.contains("lastOfficialRace", Tag.TAG_COMPOUND))
            result.lastOfficialRace = LastRaceSummary.load(tag.getCompound("lastOfficialRace"));
        result.locked = tag.getBoolean("locked");
        result.favorite = tag.getBoolean("favorite");
        result.appearance = tag.contains("appearance") ? tag.getString("appearance") : TurtleAppearanceCatalog.DEFAULT_ID;
        try { TurtleAppearanceCatalog.get(result.appearance); }
        catch (IllegalArgumentException ignored) { result.appearance = TurtleAppearanceCatalog.DEFAULT_ID; }
        result.revision = Math.max(1L, tag.getLong("revision"));
        result.validate();
        return result;
    }

    private static String validatedBodyAppearance(CompoundTag tag) {
        String id = tag.contains("bodyAppearance") ? tag.getString("bodyAppearance") : TurtleBodyAppearanceCatalog.DEFAULT_ID;
        try { return TurtleBodyAppearanceCatalog.get(id).id(); }
        catch (IllegalArgumentException ignored) { return TurtleBodyAppearanceCatalog.DEFAULT_ID; }
    }

    private static <E extends Enum<E>> EnumMap<E, TurtleAptitude> loadAptitudes(CompoundTag tag, Class<E> type) {
        EnumMap<E, TurtleAptitude> result = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            result.put(value, tag.contains(value.name()) ? TurtleAptitude.valueOf(tag.getString(value.name())) : TurtleAptitude.B);
        }
        return result;
    }

    public static String validateName(String raw) {
        if (raw == null) throw new IllegalArgumentException("Turtle name is required");
        String value = raw.strip().replaceAll("\\s+", " ");
        int count = value.codePointCount(0, value.length());
        if (count < 1 || count > MAX_NAME_CODE_POINTS || value.indexOf('\n') >= 0 || value.indexOf('§') >= 0) {
            throw new IllegalArgumentException("Invalid turtle name");
        }
        return value;
    }
}
