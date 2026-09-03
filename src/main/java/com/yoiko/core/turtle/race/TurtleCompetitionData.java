package com.yoiko.core.turtle.race;

import com.yoiko.core.turtle.TurtleLeague;
import com.yoiko.core.turtle.TurtlePlayerProgress;
import com.yoiko.core.turtle.TurtleRacingSavedData;
import com.yoiko.core.turtle.TurtleWeather;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class TurtleCompetitionData {
    public record Registration(UUID playerId, UUID turtleId, long registeredAt, int seasonRating) {
        CompoundTag save(){CompoundTag t=new CompoundTag();t.putUUID("playerId",playerId);t.putUUID("turtleId",turtleId);t.putLong("registeredAt",registeredAt);t.putInt("seasonRating",seasonRating);return t;}
        static Registration load(CompoundTag t){return new Registration(t.getUUID("playerId"),t.getUUID("turtleId"),t.getLong("registeredAt"),t.getInt("seasonRating"));}
    }
    public record Bet(UUID playerId,int heatIndex,String entryId,long amount,int oddsMilli,String dailyPeriodKey) {
        CompoundTag save(){CompoundTag t=new CompoundTag();t.putUUID("playerId",playerId);t.putInt("heatIndex",heatIndex);t.putString("entryId",entryId);t.putLong("amount",amount);t.putInt("oddsMilli",oddsMilli);t.putString("dailyPeriodKey",dailyPeriodKey);return t;}
        static Bet load(CompoundTag t){return new Bet(t.getUUID("playerId"),t.getInt("heatIndex"),t.getString("entryId"),t.getLong("amount"),t.contains("oddsMilli")?Math.max(1_000,t.getInt("oddsMilli")):1_000,t.getString("dailyPeriodKey"));}
    }
    public record BetQuote(int heatIndex,String entryId,int trials,int wins,int winBasisPoints,int oddsMilli) {
        public BetQuote { trials=Math.max(1,trials);wins=Math.max(0,Math.min(trials,wins));winBasisPoints=Math.max(1,Math.min(10_000,winBasisPoints));oddsMilli=Math.max(1_000,Math.min(20_000,oddsMilli)); }
        CompoundTag save(){CompoundTag t=new CompoundTag();t.putInt("heatIndex",heatIndex);t.putString("entryId",entryId);t.putInt("trials",trials);t.putInt("wins",wins);t.putInt("winBasisPoints",winBasisPoints);t.putInt("oddsMilli",oddsMilli);return t;}
        static BetQuote load(CompoundTag t){return new BetQuote(t.getInt("heatIndex"),t.getString("entryId"),t.getInt("trials"),t.getInt("wins"),t.getInt("winBasisPoints"),t.getInt("oddsMilli"));}
    }

    private final String id;
    private final String periodKey;
    private final TurtleLeague league;
    private final long courseSeed;
    private final long raceSeed;
    private final boolean beachTheme;
    private final TurtleWeather weather;
    private final boolean official;
    private final boolean scheduled;
    private TurtleCompetitionPhase phase;
    private final List<Registration> registrations=new ArrayList<>();
    private final List<Registration> selected=new ArrayList<>();
    private final List<List<Registration>> heats=new ArrayList<>();
    private final List<Bet> bets=new ArrayList<>();
    private final List<BetQuote> betQuotes=new ArrayList<>();
    private int currentHeat;
    private long phaseChangedAt;
    private long registrationDurationMillis;

    public TurtleCompetitionData(String id,String periodKey,TurtleLeague league,long courseSeed,long raceSeed,
                                 boolean beachTheme,TurtleWeather weather,boolean official,boolean scheduled,long now){
        this.id=id;this.periodKey=periodKey;this.league=league;this.courseSeed=courseSeed;this.raceSeed=raceSeed;
        this.beachTheme=beachTheme;this.weather=weather;this.official=official;this.scheduled=scheduled;
        this.phase=TurtleCompetitionPhase.GENERATING_MAP;this.phaseChangedAt=now;
        this.registrationDurationMillis=scheduled?40L*60_000L:10L*60_000L;
    }
    public String id(){return id;} public String periodKey(){return periodKey;} public TurtleLeague league(){return league;}
    public long courseSeed(){return courseSeed;} public long raceSeed(){return raceSeed;} public boolean beachTheme(){return beachTheme;}
    public TurtleWeather weather(){return weather;} public boolean official(){return official;} public boolean scheduled(){return scheduled;}
    public TurtleCompetitionPhase phase(){return phase;} public long phaseChangedAt(){return phaseChangedAt;}
    public List<Registration> registrations(){return List.copyOf(registrations);} public List<Registration> selected(){return List.copyOf(selected);}
    public List<List<Registration>> heats(){return heats.stream().map(List::copyOf).toList();} public List<Bet> bets(){return List.copyOf(bets);}
    public List<BetQuote> betQuotes(){return List.copyOf(betQuotes);}
    public int currentHeat(){return currentHeat;}
    public long registrationDurationMillis(){return registrationDurationMillis;}
    public void setRegistrationMinutes(int minutes){registrationDurationMillis=Math.max(3,Math.min(30,minutes))*60_000L;}
    public void transition(TurtleCompetitionPhase value,long now){phase=value;phaseChangedAt=now;}
    public void nextHeat(){currentHeat++;}

    public void register(Registration value){
        if(phase!=TurtleCompetitionPhase.REGISTRATION_OPEN)throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.registration_closed",phase.name());
        if(registrations.stream().anyMatch(r->r.playerId.equals(value.playerId)))throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.already_registered_player");
        if(registrations.stream().anyMatch(r->r.turtleId.equals(value.turtleId)))throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.already_registered_turtle");
        registrations.add(value);
    }
    public boolean unregister(UUID playerId){return registrations.removeIf(value->value.playerId.equals(playerId));}
    public void addBet(Bet bet){bets.add(bet);}
    public void replaceBetQuotes(List<BetQuote> values){betQuotes.clear();betQuotes.addAll(values);}
    public BetQuote betQuote(int heatIndex,String entryId){return betQuotes.stream().filter(v->v.heatIndex==heatIndex&&v.entryId.equals(entryId)).findFirst().orElse(null);}
    public long betTotal(UUID playerId){return bets.stream().filter(v->v.playerId.equals(playerId)).mapToLong(Bet::amount).sum();}
    public long betTotal(UUID playerId,int heatIndex){return bets.stream().filter(v->v.playerId.equals(playerId)&&v.heatIndex==heatIndex).mapToLong(Bet::amount).sum();}

    public void lockEntries(TurtleRacingSavedData saved){
        selected.clear();heats.clear();
        List<Registration> ranked=registrations.stream().sorted(Comparator
                .<Registration>comparingInt(value->saved.getOrCreatePlayer(value.playerId).recentUnselectedCount()).reversed()
                .thenComparing(Comparator.comparingInt(Registration::seasonRating).reversed())
                .thenComparingLong(Registration::registeredAt)
                .thenComparingInt(value->value.playerId.hashCode())).toList();
        // An official competition has one eight-lane final. Players not selected here retain the
        // existing recent-unselected priority so they move ahead at the next registration lock.
        selected.addAll(ranked.subList(0,Math.min(8,ranked.size())));
        Set<UUID> selectedIds=selected.stream().map(Registration::playerId).collect(java.util.stream.Collectors.toSet());
        for(Registration registration:registrations){TurtlePlayerProgress p=saved.getOrCreatePlayer(registration.playerId);if(selectedIds.contains(registration.playerId))p.selectedForRace();else p.notSelectedForRace();}
        int heatCount=1;
        for(int i=0;i<heatCount;i++)heats.add(new ArrayList<>());
        List<Registration> byRating=selected.stream().sorted(Comparator.comparingInt(Registration::seasonRating).reversed()).toList();
        int heat=0,direction=1;
        for(Registration registration:byRating){heats.get(heat).add(registration);if(heatCount>1){if(direction>0&&heat==heatCount-1)direction=-1;else if(direction<0&&heat==0)direction=1;else heat+=direction;}}
    }

    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("id",id);t.putString("periodKey",periodKey);t.putString("league",league.name());t.putLong("courseSeed",courseSeed);t.putLong("raceSeed",raceSeed);t.putBoolean("beachTheme",beachTheme);t.putString("weather",weather.name());t.putBoolean("official",official);t.putBoolean("scheduled",scheduled);t.putString("phase",phase.name());t.putLong("phaseChangedAt",phaseChangedAt);t.putInt("currentHeat",currentHeat);t.putLong("registrationDurationMillis",registrationDurationMillis);
        ListTag r=new ListTag();registrations.forEach(v->r.add(v.save()));t.put("registrations",r);ListTag s=new ListTag();selected.forEach(v->s.add(v.save()));t.put("selected",s);ListTag hs=new ListTag();for(List<Registration> h:heats){CompoundTag ht=new CompoundTag();ListTag hl=new ListTag();h.forEach(v->hl.add(v.save()));ht.put("entries",hl);hs.add(ht);}t.put("heats",hs);ListTag bs=new ListTag();bets.forEach(v->bs.add(v.save()));t.put("bets",bs);ListTag qs=new ListTag();betQuotes.forEach(v->qs.add(v.save()));t.put("betQuotes",qs);return t;}
    public static TurtleCompetitionData load(CompoundTag t){TurtleCompetitionData d=new TurtleCompetitionData(t.getString("id"),t.getString("periodKey"),TurtleLeague.valueOf(t.getString("league")),t.getLong("courseSeed"),t.getLong("raceSeed"),t.getBoolean("beachTheme"),TurtleWeather.valueOf(t.getString("weather")),t.getBoolean("official"),t.getBoolean("scheduled"),t.getLong("phaseChangedAt"));d.phase=TurtleCompetitionPhase.valueOf(t.getString("phase"));d.currentHeat=t.getInt("currentHeat");if(t.contains("registrationDurationMillis"))d.registrationDurationMillis=t.getLong("registrationDurationMillis");loadRegs(t.getList("registrations",Tag.TAG_COMPOUND),d.registrations);loadRegs(t.getList("selected",Tag.TAG_COMPOUND),d.selected);ListTag hs=t.getList("heats",Tag.TAG_COMPOUND);for(int i=0;i<hs.size();i++){List<Registration> h=new ArrayList<>();loadRegs(hs.getCompound(i).getList("entries",Tag.TAG_COMPOUND),h);d.heats.add(h);}ListTag bs=t.getList("bets",Tag.TAG_COMPOUND);for(int i=0;i<bs.size();i++)d.bets.add(Bet.load(bs.getCompound(i)));ListTag qs=t.getList("betQuotes",Tag.TAG_COMPOUND);for(int i=0;i<qs.size();i++)d.betQuotes.add(BetQuote.load(qs.getCompound(i)));return d;}
    private static void loadRegs(ListTag tags,List<Registration> output){for(int i=0;i<tags.size();i++)output.add(Registration.load(tags.getCompound(i)));}
}
