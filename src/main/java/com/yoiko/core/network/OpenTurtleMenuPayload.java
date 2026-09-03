package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenTurtleMenuPayload(
        long revision, int training, int medals, long gems, int owned, int maxOwned,
        int standardTickets, int pickupTickets, int rareTickets, int epicTickets, int trainingResetTickets,
        String banner, CompetitionStatus competition, TimeTrialStatus timeTrial, String initialTab,
        String forecastWeather, int forecastSurfaceMask,
        int turtlePage, int turtlePages, int goldenShellWinMask,
        RerollOffer rerollOffer, RerollCandidates rerollCandidates,
        List<AppearanceDefinition> appearanceCatalog, List<AppearanceState> appearanceStates,
        List<String> companions, List<BetCandidate> betCandidates, List<BetPool> betPools,
        List<Card> turtles) implements CustomPacketPayload {

    public record Card(UUID id, long revision, String name, String rarity, int total, String league,
                       String raceClass, int racePoints,
                       int training, int rerollCredits, int rerollsUsed, int awakening,
                       boolean locked, boolean releasePending, boolean registered, boolean favorite,
                       String active, List<String> passives, String strategy, String companion, String appearance, String bodyAppearance,
                       List<String> strategyForecasts, List<Integer> stats, List<String> statGrades,
                       List<Integer> baseStats, List<Integer> projectedStats,
                       List<String> trainableTrainings, String recommendedTraining,
                       Career career, RaceAnalysis lastRace) { }

    public record Career(int rating,int starts,int wins,int podiums,int averageRankTenths,int averageStamina,
                         List<Integer> recentRanks,
                         List<Integer> strategyStarts,List<Integer> strategyWins) { }

    public record RaceAnalysis(int rank,boolean finished,long finishMillis,int staminaPercent,int overtakes,int laneChanges,
                               int blockedTicks,int breaths,boolean activeUsed,int ratingChange) { }

    public record CompetitionStatus(boolean active,String league,String phase,int registrations,int currentHeat,
                                    boolean playerRegistered,int weeklyFinishes,int weeklyBestRank,
                                    String nextLeague,long nextStartAt) { }

    public record TimeTrialBest(String raceClass,long finishMillis) { }

    public record TimeTrialStatus(String preset,String state,int queuePosition,boolean engaged,
                                  List<TimeTrialBest> bests) {
        public TimeTrialStatus { bests=List.copyOf(bests); }
    }

    public record AppearanceDefinition(String id, int color, String unlockKind,
                                       String unlockKey, int gemPrice) { }

    public record RerollOffer(UUID turtleId, int slot, String currentPassive,
                              String candidatePassive, long turtleRevision) { }

    public record RerollCandidates(UUID turtleId, int slot, List<String> candidates,
                                   long turtleRevision) {
        public RerollCandidates { candidates = List.copyOf(candidates); }
    }

    public record AppearanceState(String id, boolean unlocked, boolean favorite,
                                  int progress, int target) { }

    public record BetCandidate(int heat,String entryId,String label,String raceClass,int rating,
                               String strategy,String active,String recentForm,int winBasisPoints,int oddsMilli) { }

    public record BetPool(int heat,String entryId,long totalPool,long myStake) { }

    public static final Type<OpenTurtleMenuPayload> TYPE = new Type<>(YoikoServerCore.id("open_turtle_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTurtleMenuPayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenTurtleMenuPayload::write, OpenTurtleMenuPayload::new);

    public OpenTurtleMenuPayload {
        appearanceCatalog = List.copyOf(appearanceCatalog);
        appearanceStates = List.copyOf(appearanceStates);
        companions = List.copyOf(companions);
        betCandidates=List.copyOf(betCandidates);
        betPools=List.copyOf(betPools);
        turtles = List.copyOf(turtles);
        if (appearanceCatalog.size() > 32 || appearanceStates.size() > 32
                || companions.size() > 13 || betCandidates.size() > 24 || betPools.size() > 24 || turtles.size() > 8) {
            throw new IllegalArgumentException("Oversized turtle menu payload");
        }
    }

    private OpenTurtleMenuPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(64), readCompetitionStatus(buffer), readTimeTrialStatus(buffer), buffer.readUtf(24),
                buffer.readUtf(24), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), readRerollOffer(buffer), readRerollCandidates(buffer), readAppearanceCatalog(buffer), readAppearanceStates(buffer),
                readStrings(buffer, 13, 32), readBetCandidates(buffer),readBetPools(buffer), readCards(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeLong(revision);
        buffer.writeVarInt(training);
        buffer.writeVarInt(medals);
        buffer.writeVarLong(gems);
        buffer.writeVarInt(owned);
        buffer.writeVarInt(maxOwned);
        buffer.writeVarInt(standardTickets);
        buffer.writeVarInt(pickupTickets);
        buffer.writeVarInt(rareTickets);
        buffer.writeVarInt(epicTickets);
        buffer.writeVarInt(trainingResetTickets);
        buffer.writeUtf(banner, 64);
        writeCompetitionStatus(buffer,competition);
        writeTimeTrialStatus(buffer,timeTrial);
        buffer.writeUtf(initialTab, 24);
        buffer.writeUtf(forecastWeather, 24);
        buffer.writeVarInt(forecastSurfaceMask);
        buffer.writeVarInt(turtlePage);
        buffer.writeVarInt(turtlePages);
        buffer.writeVarInt(goldenShellWinMask);
        buffer.writeBoolean(rerollOffer!=null);
        if(rerollOffer!=null){buffer.writeUUID(rerollOffer.turtleId());buffer.writeVarInt(rerollOffer.slot());buffer.writeUtf(rerollOffer.currentPassive(),64);buffer.writeUtf(rerollOffer.candidatePassive(),64);buffer.writeLong(rerollOffer.turtleRevision());}
        buffer.writeBoolean(rerollCandidates!=null);
        if(rerollCandidates!=null){buffer.writeUUID(rerollCandidates.turtleId());buffer.writeVarInt(rerollCandidates.slot());writeStrings(buffer,rerollCandidates.candidates(),64);buffer.writeLong(rerollCandidates.turtleRevision());}
        buffer.writeVarInt(appearanceCatalog.size());
        for(AppearanceDefinition appearance:appearanceCatalog){buffer.writeUtf(appearance.id(),32);buffer.writeInt(appearance.color());buffer.writeUtf(appearance.unlockKind(),16);buffer.writeUtf(appearance.unlockKey(),32);buffer.writeVarInt(appearance.gemPrice());}
        buffer.writeVarInt(appearanceStates.size());
        for(AppearanceState state:appearanceStates){buffer.writeUtf(state.id(),32);buffer.writeBoolean(state.unlocked());buffer.writeBoolean(state.favorite());buffer.writeVarInt(state.progress());buffer.writeVarInt(state.target());}
        writeStrings(buffer, companions, 32);
        buffer.writeVarInt(betCandidates.size());
        for(BetCandidate candidate:betCandidates){
            buffer.writeVarInt(candidate.heat());buffer.writeUtf(candidate.entryId(),64);buffer.writeUtf(candidate.label(),48);
            buffer.writeUtf(candidate.raceClass(),2);buffer.writeVarInt(candidate.rating());buffer.writeUtf(candidate.strategy(),24);
            buffer.writeUtf(candidate.active(),64);buffer.writeUtf(candidate.recentForm(),32);buffer.writeVarInt(candidate.winBasisPoints());buffer.writeVarInt(candidate.oddsMilli());
        }
        buffer.writeVarInt(betPools.size());
        for(BetPool pool:betPools){
            buffer.writeVarInt(pool.heat());buffer.writeUtf(pool.entryId(),64);buffer.writeVarLong(pool.totalPool());buffer.writeVarLong(pool.myStake());
        }
        buffer.writeVarInt(turtles.size());
        for (Card card : turtles) {
            buffer.writeUUID(card.id());
            buffer.writeLong(card.revision());
            buffer.writeUtf(card.name(), 32);
            buffer.writeUtf(card.rarity(), 16);
            buffer.writeVarInt(card.total());
            buffer.writeUtf(card.league(), 24);
            buffer.writeUtf(card.raceClass(), 2);
            buffer.writeVarInt(card.racePoints());
            buffer.writeVarInt(card.training());
            buffer.writeVarInt(card.rerollCredits());
            buffer.writeVarInt(card.rerollsUsed());
            buffer.writeVarInt(card.awakening());
            buffer.writeBoolean(card.locked());
            buffer.writeBoolean(card.releasePending());
            buffer.writeBoolean(card.registered());
            buffer.writeBoolean(card.favorite());
            buffer.writeUtf(card.active(), 64);
            writeStrings(buffer, card.passives(), 64);
            buffer.writeUtf(card.strategy(), 24);
            buffer.writeUtf(card.companion(), 32);
            buffer.writeUtf(card.appearance(),32);
            buffer.writeUtf(card.bodyAppearance(),16);
            writeStrings(buffer,card.strategyForecasts(),128);
            buffer.writeVarInt(card.stats().size());
            for (int stat : card.stats()) buffer.writeVarInt(stat);
            writeStrings(buffer, card.statGrades(), 2);
            buffer.writeVarInt(card.baseStats().size());
            for (int stat : card.baseStats()) buffer.writeVarInt(stat);
            buffer.writeVarInt(card.projectedStats().size());
            for (int stat : card.projectedStats()) buffer.writeVarInt(stat);
            writeStrings(buffer, card.trainableTrainings(), 32);
            buffer.writeUtf(card.recommendedTraining(), 32);
            writeCareer(buffer,card.career());
            writeRaceAnalysis(buffer,card.lastRace());
        }
    }

    private static List<Card> readCards(RegistryFriendlyByteBuf buffer) {
        int size = checkedSize(buffer.readVarInt(), 8);
        List<Card> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = buffer.readUUID();
            long revision = buffer.readLong();
            String name = buffer.readUtf(32);
            String rarity = buffer.readUtf(16);
            int total = buffer.readVarInt();
            String league = buffer.readUtf(24);
            String raceClass = buffer.readUtf(2);
            int racePoints = buffer.readVarInt();
            int training = buffer.readVarInt();
            int rerollCredits = buffer.readVarInt();
            int rerollsUsed = buffer.readVarInt();
            int awakening = buffer.readVarInt();
            boolean locked = buffer.readBoolean();
            boolean releasePending = buffer.readBoolean();
            boolean registered=buffer.readBoolean();
            boolean favorite=buffer.readBoolean();
            String active = buffer.readUtf(64);
            List<String> passives = readStrings(buffer, 4, 64);
            if (passives.size() != 4) throw new IllegalArgumentException("Invalid passive count");
            String strategy=buffer.readUtf(24),companion=buffer.readUtf(32),appearance=buffer.readUtf(32),bodyAppearance=buffer.readUtf(16);
            List<String> forecasts=readStrings(buffer,4,128);
            int statSize=checkedSize(buffer.readVarInt(),5);List<Integer> stats=new ArrayList<>(statSize);
            for(int stat=0;stat<statSize;stat++)stats.add(buffer.readVarInt());
            if(stats.size()!=5)throw new IllegalArgumentException("Invalid turtle stat count");
            List<String> statGrades=readStrings(buffer,5,2);
            int baseStatSize=checkedSize(buffer.readVarInt(),5);List<Integer> baseStats=new ArrayList<>(baseStatSize);
            for(int stat=0;stat<baseStatSize;stat++)baseStats.add(buffer.readVarInt());
            int projectedStatSize=checkedSize(buffer.readVarInt(),5);List<Integer> projectedStats=new ArrayList<>(projectedStatSize);
            for(int stat=0;stat<projectedStatSize;stat++)projectedStats.add(buffer.readVarInt());
            if(statGrades.size()!=5||baseStats.size()!=5||projectedStats.size()!=5)throw new IllegalArgumentException("Invalid turtle stat metadata");
            List<String> trainableTrainings=readStrings(buffer,5,32);
            result.add(new Card(id, revision, name, rarity, total, league, raceClass, racePoints, training, rerollCredits,
                    rerollsUsed, awakening, locked,
                    releasePending,registered,favorite,active, passives, strategy, companion,appearance,bodyAppearance,forecasts,stats,statGrades,baseStats,projectedStats,trainableTrainings,buffer.readUtf(32),readCareer(buffer),readRaceAnalysis(buffer)));
        }
        return result;
    }

    public static void writeCompetitionStatus(RegistryFriendlyByteBuf buffer,CompetitionStatus value){
        buffer.writeBoolean(value.active());buffer.writeUtf(value.league(),24);buffer.writeUtf(value.phase(),32);buffer.writeVarInt(value.registrations());buffer.writeVarInt(value.currentHeat());
        buffer.writeBoolean(value.playerRegistered());buffer.writeVarInt(value.weeklyFinishes());buffer.writeVarInt(value.weeklyBestRank());
        buffer.writeUtf(value.nextLeague(),24);buffer.writeVarLong(value.nextStartAt());
    }

    public static CompetitionStatus readCompetitionStatus(RegistryFriendlyByteBuf buffer){return new CompetitionStatus(buffer.readBoolean(),buffer.readUtf(24),buffer.readUtf(32),buffer.readVarInt(),buffer.readVarInt(),buffer.readBoolean(),buffer.readVarInt(),buffer.readVarInt(),buffer.readUtf(24),buffer.readVarLong());}

    public static void writeTimeTrialStatus(RegistryFriendlyByteBuf buffer,TimeTrialStatus value){
        buffer.writeUtf(value.preset(),32);buffer.writeUtf(value.state(),24);buffer.writeVarInt(value.queuePosition());buffer.writeBoolean(value.engaged());
        buffer.writeVarInt(value.bests().size());for(TimeTrialBest best:value.bests()){buffer.writeUtf(best.raceClass(),2);buffer.writeVarLong(best.finishMillis());}
    }

    public static TimeTrialStatus readTimeTrialStatus(RegistryFriendlyByteBuf buffer){
        String preset=buffer.readUtf(32),state=buffer.readUtf(24);int queue=buffer.readVarInt();boolean engaged=buffer.readBoolean();
        int count=checkedSize(buffer.readVarInt(),5);List<TimeTrialBest> bests=new ArrayList<>(count);
        for(int i=0;i<count;i++)bests.add(new TimeTrialBest(buffer.readUtf(2),buffer.readVarLong()));
        return new TimeTrialStatus(preset,state,queue,engaged,bests);
    }

    private static List<AppearanceDefinition> readAppearanceCatalog(RegistryFriendlyByteBuf buffer){
        int size=checkedSize(buffer.readVarInt(),32);List<AppearanceDefinition> result=new ArrayList<>(size);
        for(int i=0;i<size;i++)result.add(new AppearanceDefinition(buffer.readUtf(32),buffer.readInt(),buffer.readUtf(16),buffer.readUtf(32),buffer.readVarInt()));
        return result;
    }

    private static RerollOffer readRerollOffer(RegistryFriendlyByteBuf buffer){
        if(!buffer.readBoolean())return null;
        UUID turtleId=buffer.readUUID();int slot=buffer.readVarInt();
        if(slot<0||slot>=4)throw new IllegalArgumentException("Invalid passive reroll slot");
        return new RerollOffer(turtleId,slot,buffer.readUtf(64),buffer.readUtf(64),buffer.readLong());
    }

    private static RerollCandidates readRerollCandidates(RegistryFriendlyByteBuf buffer){
        if(!buffer.readBoolean())return null;
        UUID turtleId=buffer.readUUID();int slot=buffer.readVarInt();
        if(slot<0||slot>=4)throw new IllegalArgumentException("Invalid passive candidate slot");
        return new RerollCandidates(turtleId,slot,readStrings(buffer,49,64),buffer.readLong());
    }

    private static List<AppearanceState> readAppearanceStates(RegistryFriendlyByteBuf buffer){
        int size=checkedSize(buffer.readVarInt(),32);List<AppearanceState> result=new ArrayList<>(size);
        for(int i=0;i<size;i++)result.add(new AppearanceState(buffer.readUtf(32),buffer.readBoolean(),buffer.readBoolean(),buffer.readVarInt(),buffer.readVarInt()));
        return result;
    }

    private static List<BetCandidate> readBetCandidates(RegistryFriendlyByteBuf buffer){int size=checkedSize(buffer.readVarInt(),24);List<BetCandidate> result=new ArrayList<>(size);for(int i=0;i<size;i++)result.add(new BetCandidate(buffer.readVarInt(),buffer.readUtf(64),buffer.readUtf(48),buffer.readUtf(2),buffer.readVarInt(),buffer.readUtf(24),buffer.readUtf(64),buffer.readUtf(32),buffer.readVarInt(),buffer.readVarInt()));return result;}
    private static List<BetPool> readBetPools(RegistryFriendlyByteBuf buffer){int size=checkedSize(buffer.readVarInt(),24);List<BetPool> result=new ArrayList<>(size);for(int i=0;i<size;i++)result.add(new BetPool(buffer.readVarInt(),buffer.readUtf(64),buffer.readVarLong(),buffer.readVarLong()));return result;}

    private static void writeCareer(RegistryFriendlyByteBuf buffer,Career value){
        buffer.writeVarInt(value.rating());buffer.writeVarInt(value.starts());buffer.writeVarInt(value.wins());buffer.writeVarInt(value.podiums());
        buffer.writeVarInt(value.averageRankTenths());buffer.writeVarInt(value.averageStamina());
        buffer.writeVarInt(value.recentRanks().size());for(int rank:value.recentRanks())buffer.writeVarInt(rank);
        buffer.writeVarInt(value.strategyStarts().size());for(int count:value.strategyStarts())buffer.writeVarInt(count);
        buffer.writeVarInt(value.strategyWins().size());for(int count:value.strategyWins())buffer.writeVarInt(count);
    }

    private static Career readCareer(RegistryFriendlyByteBuf buffer){
        int rating=buffer.readVarInt(),starts=buffer.readVarInt(),wins=buffer.readVarInt(),podiums=buffer.readVarInt();
        int averageRank=buffer.readVarInt(),averageStamina=buffer.readVarInt();
        return new Career(rating,starts,wins,podiums,averageRank,averageStamina,readInts(buffer,5),readInts(buffer,4),readInts(buffer,4));
    }

    private static void writeRaceAnalysis(RegistryFriendlyByteBuf buffer,RaceAnalysis value){
        buffer.writeBoolean(value!=null);if(value==null)return;
        buffer.writeVarInt(value.rank());buffer.writeBoolean(value.finished());buffer.writeVarLong(value.finishMillis());buffer.writeVarInt(value.staminaPercent());buffer.writeVarInt(value.overtakes());
        buffer.writeVarInt(value.laneChanges());buffer.writeVarInt(value.blockedTicks());buffer.writeVarInt(value.breaths());buffer.writeBoolean(value.activeUsed());
        buffer.writeInt(value.ratingChange());
    }

    private static RaceAnalysis readRaceAnalysis(RegistryFriendlyByteBuf buffer){
        if(!buffer.readBoolean())return null;
        return new RaceAnalysis(buffer.readVarInt(),buffer.readBoolean(),buffer.readVarLong(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),
                buffer.readVarInt(),buffer.readVarInt(),buffer.readBoolean(),buffer.readInt());
    }

    private static List<Integer> readInts(RegistryFriendlyByteBuf buffer,int maximum){int size=checkedSize(buffer.readVarInt(),maximum);List<Integer> result=new ArrayList<>(size);for(int i=0;i<size;i++)result.add(buffer.readVarInt());return result;}

    private static List<String> readStrings(RegistryFriendlyByteBuf buffer, int maximum, int maxLength) {
        int size = checkedSize(buffer.readVarInt(), maximum);
        List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(buffer.readUtf(maxLength));
        return result;
    }

    private static void writeStrings(RegistryFriendlyByteBuf buffer, List<String> values, int maxLength) {
        buffer.writeVarInt(values.size());
        for (String value : values) buffer.writeUtf(value, maxLength);
    }

    private static int checkedSize(int value, int maximum) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException("Invalid collection size");
        return value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
