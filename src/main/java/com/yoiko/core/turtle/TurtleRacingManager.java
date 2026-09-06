package com.yoiko.core.turtle;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.economy.CurrencyManager;
import com.yoiko.core.economy.CurrencyType;
import com.yoiko.core.reward.YoikoResetClock;
import com.yoiko.core.turtle.arena.TurtleArenaManager;
import com.yoiko.core.turtle.race.AiTurtleCatalog;
import com.yoiko.core.turtle.race.AiTurtleProfile;
import com.yoiko.core.turtle.race.RaceEntry;
import com.yoiko.core.turtle.race.RaceEvent;
import com.yoiko.core.turtle.race.RaceSimulation;
import com.yoiko.core.turtle.race.TurtleCompetitionData;
import com.yoiko.core.turtle.race.TurtleCompetitionPhase;
import com.yoiko.core.turtle.race.TurtleCourse;
import com.yoiko.core.turtle.race.TurtleRaceWorldController;
import com.yoiko.core.turtle.race.TurtleLineupDisplay;
import com.yoiko.core.turtle.race.TurtleWeeklyResult;
import com.yoiko.core.turtle.time.TurtleTimeTrialManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.network.chat.numbers.BlankFormat;

public final class TurtleRacingManager {
    public record ReplayResult(long seed,long ticks,int hops,List<String> order){ }
    public record ForceStopResult(String competitionId,TurtleCompetitionPhase previousPhase,int refundedBets,long refundedGold){ }
    private record RefundSummary(int bets,long gold){ }
    private record LastRace(TurtleCourse course,TurtleWeather weather,long seed,List<RaceEntry> entries){ }
    private record ForecastHeat(int heat,List<RaceEntry> entries){ }
    private static final long PODIUM_DELAY_TICKS = 220L;
    private static final long PODIUM_VIEW_TICKS = 300L;
    private static TurtleRacingManager instance;
    private final MinecraftServer server;
    private final TurtleArenaManager arena;
    private final TurtleTimeTrialManager timeTrial;
    private RaceSimulation race;
    private TurtleRaceWorldController worldController;
    private TurtleLineupDisplay lineupDisplay;
    private long lastLineupStatusSecond=-1;
    private int lineupHash;
    private int announcedEventCount;
    private long resultOpenedTick;
    private long countdownStartedTick;
    private boolean resultPresentationCleared;
    private boolean podiumShown;
    private Objective previousSidebar;
    private Objective raceSidebar;
    private final Set<UUID> heatSpectators=new HashSet<>();
    private final Map<UUID,Integer> heatCheers=new HashMap<>();
    private final Map<UUID,Long> lastCheerTick=new HashMap<>();
    private final Map<String,Long> skillHighlightUntil=new HashMap<>();
    private final Map<String,String> sidebarLineCache=new HashMap<>();
    private final Map<UUID,String> personalHudCache=new HashMap<>();
    private String sidebarTitleCache="";
    private int commentaryStage;
    private long lastCommentaryTick=-100;
    private long racingLineVisualUntil;
    private long nextWeeklyBoardCheckTick;
    private LastRace lastRace;
    private CompletableFuture<List<TurtleCompetitionData.BetQuote>> bettingForecast;
    private String bettingForecastCompetitionId="";

    private TurtleRacingManager(MinecraftServer server) { this.server=server;this.arena=new TurtleArenaManager(server);this.timeTrial=new TurtleTimeTrialManager(server,arena); }

    public static void init(MinecraftServer server) {
        instance=new TurtleRacingManager(server);
        Objective staleSidebar=server.getScoreboard().getObjective("yoiko_turtle_race");
        if(staleSidebar!=null){if(server.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR)==staleSidebar)server.getScoreboard().setDisplayObjective(DisplaySlot.SIDEBAR,null);server.getScoreboard().removeObjective(staleSidebar);}
        TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);
        if(saved.competition().isPresent()){
            TurtleCompetitionData stale=saved.competition().get();
            if(stale.phase()!=TurtleCompetitionPhase.COMPLETED&&stale.phase()!=TurtleCompetitionPhase.CANCELLED){
                instance.refundUnsettledBets(stale,"SERVER_RESTART_RECOVERY");saved.clearCompetition();instance.arena.recoverCleanup();
                YoikoServerCore.LOGGER.warn("Cancelled and refunded unrecoverable turtle competition {} after restart",stale.id());
            }
        }
    }

    public static TurtleRacingManager get() { if(instance==null)throw new IllegalStateException("Turtle racing is not initialized");return instance; }
    public TurtleArenaManager arena(){return arena;}
    public RaceSimulation race(){return race;}
    public TurtleTimeTrialManager timeTrial(){return timeTrial;}
    public TurtleCompetitionData competition(){return TurtleRacingSavedData.get(server).competition().orElse(null);}
    public boolean isRegisteredTurtle(UUID turtleId){TurtleCompetitionData c=competition();return c!=null&&c.registrations().stream().anyMatch(v->v.turtleId().equals(turtleId));}

    public void tick() {
        arena.tick();
        timeTrial.tick();
        TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);
        if (server.getTickCount() >= nextWeeklyBoardCheckTick) {
            rollWeeklyBoard(saved);
            nextWeeklyBoardCheckTick = server.getTickCount() + 1_200L;
        }
        TurtleCompetitionData competition=saved.competition().orElse(null);
        try {
            if(competition==null){maybeOpenScheduled(saved);return;}
            tickCompetition(saved,competition);
        } catch(RuntimeException exception) {
            YoikoServerCore.LOGGER.error("Turtle competition failed",exception);
            cancelCompetition(competition,"INTERNAL_ERROR");
        }
    }

    private void maybeOpenScheduled(TurtleRacingSavedData saved) {
        ZonedDateTime now=now();String periodKey=YoikoResetClock.dailyPeriodKey(System.currentTimeMillis());
        if(now.toLocalTime().isBefore(LocalTime.of(19,0))||saved.lastScheduledPeriodKey().equals(periodKey)
                ||timeTrial.hasPendingWork()||arena.phase()!=TurtleArenaManager.Phase.IDLE)return;
        if(saved.arenaCenter().isEmpty())return;
        LocalDate periodDate=YoikoResetClock.dailyPeriodDate(System.currentTimeMillis());
        TurtleLeague league=TurtleLeague.scheduled(periodDate.getDayOfWeek());
        long seed=mix(periodDate.toEpochDay(),server.getWorldData().worldGenOptions().seed());
        boolean beach=(seed&1)==0;
        TurtleWeather weather=scheduledWeather(seed,beach);
        TurtleCompetitionData competition=new TurtleCompetitionData("scheduled:"+periodKey,periodKey,league,seed,mix(seed,0xACE5L),beach,weather,true,true,System.currentTimeMillis());
        saved.setCompetition(competition);saved.setLastScheduledPeriodKey(periodKey);arena.beginBuild(seed,beach);
        broadcast(Component.translatable("yoiko_core.turtle.broadcast.arena_building",leagueComponent(league)).withStyle(ChatFormatting.AQUA));
    }

    private void tickCompetition(TurtleRacingSavedData saved,TurtleCompetitionData c) {
        ZonedDateTime now=now();LocalTime time=now.toLocalTime();long millis=System.currentTimeMillis();
        emitPreRaceWeather(saved,c);
        switch(c.phase()){
            case GENERATING_MAP->{if(arena.phase()==TurtleArenaManager.Phase.READY){c.transition(TurtleCompetitionPhase.MAP_READY,millis);saved.markChanged();}else if(arena.failureReason()!=null&&arena.phase()!=TurtleArenaManager.Phase.BUILDING){broadcast(Component.translatable("yoiko_core.turtle.broadcast.arena_validation_failed",arena.failureReason()).withStyle(ChatFormatting.RED));cancelCompetition(c,"ARENA_VALIDATION_FAILED:"+arena.failureReason());}}
            case MAP_READY->{if(!c.scheduled()||!time.isBefore(LocalTime.of(19,5))){c.transition(TurtleCompetitionPhase.REGISTRATION_OPEN,millis);saved.markChanged();broadcast(Component.translatable("yoiko_core.turtle.broadcast.registration_open").withStyle(ChatFormatting.GREEN));}}
            case REGISTRATION_OPEN->{updateRegistrationLineup(saved,c,millis);if((c.scheduled()&&!time.isBefore(LocalTime.of(19,45)))||(!c.scheduled()&&millis-c.phaseChangedAt()>=c.registrationDurationMillis()))lockRegistration(saved,c);}
            case REGISTRATION_LOCKED->{updateLockedLineupStatus(c,time,millis);if(!c.official()||prepareBettingQuotes(saved,c)){c.transition(c.official()?TurtleCompetitionPhase.BETTING_OPEN:TurtleCompetitionPhase.BETTING_LOCKED,millis);saved.markChanged();}}
            case BETTING_OPEN->{updateLockedLineupStatus(c,time,millis);if((c.scheduled()&&!time.isBefore(LocalTime.of(19,58)))||(!c.scheduled()&&millis-c.phaseChangedAt()>=120_000L)){c.transition(TurtleCompetitionPhase.BETTING_LOCKED,millis);saved.markChanged();broadcast(Component.translatable("yoiko_core.turtle.broadcast.betting_closed").withStyle(ChatFormatting.GOLD));}}
            case BETTING_LOCKED,STAGING->{updateLockedLineupStatus(c,time,millis);if(readyForHeat(c,time)){beginCurrentHeatCountdown(saved,c);}}
            case COUNTDOWN->tickCountdown(saved,c);
            case RUNNING->tickRace(saved,c);
            case RESULT_OPEN->{long shown=server.getTickCount()-resultOpenedTick;long presentationTicks=PODIUM_DELAY_TICKS+PODIUM_VIEW_TICKS;if(!podiumShown&&worldController!=null)worldController.syncRaceHud();if(!podiumShown&&shown>=PODIUM_DELAY_TICKS){if(worldController!=null)worldController.showPodium(race.standings());podiumShown=true;}if(!resultPresentationCleared&&shown>=presentationTicks){discardRacePresentation();resultPresentationCleared=true;}if(c.currentHeat()+1<c.heats().size()&&shown>=presentationTicks){c.nextHeat();c.transition(TurtleCompetitionPhase.BETTING_LOCKED,millis);saved.markChanged();showLockedLineup(saved,c);}else if(c.currentHeat()+1>=c.heats().size()&&shown>=presentationTicks&&(!c.scheduled()||!time.isBefore(LocalTime.of(20,30)))){discardRacePresentation();c.transition(TurtleCompetitionPhase.CLEANING_MAP,millis);arena.beginCleanup();saved.markChanged();}}
            case CLEANING_MAP->{if(arena.phase()==TurtleArenaManager.Phase.IDLE){c.transition(TurtleCompetitionPhase.COMPLETED,millis);saved.markChanged();saved.clearCompetition();broadcast(Component.translatable("yoiko_core.turtle.broadcast.arena_cleaned").withStyle(ChatFormatting.GRAY));}}
            case CANCELLING->{refundUnsettledBets(c,"CANCELLED");discardRacePresentation();arena.beginCleanup();c.transition(TurtleCompetitionPhase.CLEANING_MAP,millis);saved.markChanged();}
            default->{}
        }
    }

    private void emitPreRaceWeather(TurtleRacingSavedData saved,TurtleCompetitionData c){
        if(arena.phase()!=TurtleArenaManager.Phase.READY)return;
        boolean waiting=switch(c.phase()){
            case MAP_READY,REGISTRATION_OPEN,REGISTRATION_LOCKED,BETTING_OPEN,BETTING_LOCKED,STAGING,COUNTDOWN->true;
            default->false;
        };
        if(!waiting)return;
        BlockPos center=saved.arenaCenter().orElse(null);
        if(center!=null)TurtleRaceWorldController.emitPreRaceWeather(arenaLevel(saved),center,c.weather(),server.getTickCount());
    }

    private boolean readyForHeat(TurtleCompetitionData c,LocalTime time){if(!c.scheduled())return true;return !time.isBefore(LocalTime.of(20,0).plusMinutes(c.currentHeat()*8L));}

    private void updateRegistrationLineup(TurtleRacingSavedData saved,TurtleCompetitionData c,long now){
        if(arena.phase()!=TurtleArenaManager.Phase.READY)return;
        long remaining=c.scheduled()?Math.max(0,java.time.Duration.between(now().toLocalTime(),LocalTime.of(19,45)).toSeconds())
                :Math.max(0,(c.registrationDurationMillis()-(now-c.phaseChangedAt()))/1000);
        boolean created=lineupDisplay==null;if(created)lineupDisplay=new TurtleLineupDisplay(arenaLevel(saved),arena.course(),saved.arenaCenter().orElseThrow().getY());
        long second=now/1000;Component status=Component.translatable("yoiko_core.turtle.lineup.registration_status",String.format(java.util.Locale.ROOT,"%02d:%02d",remaining/60,remaining%60),c.registrations().size()).withStyle(ChatFormatting.YELLOW);
        List<TurtleLineupDisplay.Preview> previews=c.registrations().stream().sorted(Comparator.comparingLong(TurtleCompetitionData.Registration::registeredAt)).limit(8)
                .map(registration->saved.turtle(registration.turtleId()).map(t->new TurtleLineupDisplay.Preview(t.displayName(),false,t.appearance(),t.bodyAppearance(),t.ownerId())).orElse(null)).filter(java.util.Objects::nonNull).toList();
        int hash=previews.hashCode();if(created||hash!=lineupHash){lineupDisplay.show(previews,status);lineupHash=hash;}
        else if(lastLineupStatusSecond!=second)lineupDisplay.updateStatus(status);
        lastLineupStatusSecond=second;
    }

    private void showLockedLineup(TurtleRacingSavedData saved,TurtleCompetitionData c){
        if(lineupDisplay==null)lineupDisplay=new TurtleLineupDisplay(arenaLevel(saved),arena.course(),saved.arenaCenter().orElseThrow().getY());
        int heat=c.currentHeat();List<TurtleLineupDisplay.Preview> previews=buildHeatEntries(saved,c,heat).stream().map(e->new TurtleLineupDisplay.Preview(e.displayName(),e.ai(),e.appearance(),e.bodyAppearance(),e.ownerId())).toList();
        lineupDisplay.show(previews,Component.translatable("yoiko_core.turtle.lineup.confirmed",heat+1).withStyle(ChatFormatting.AQUA));
    }

    private void updateLockedLineupStatus(TurtleCompetitionData c,LocalTime time,long millis){if(lineupDisplay==null)return;long remaining;if(c.scheduled()){LocalTime start=LocalTime.of(20,0).plusMinutes(c.currentHeat()*8L);remaining=Math.max(0,java.time.Duration.between(time,start).toSeconds());}else if(c.phase()==TurtleCompetitionPhase.BETTING_OPEN)remaining=Math.max(0,(120_000L-(millis-c.phaseChangedAt()))/1000);else remaining=0;long second=millis/1000;if(lastLineupStatusSecond==second)return;lineupDisplay.updateStatus(Component.translatable("yoiko_core.turtle.lineup.start_status",c.currentHeat()+1,String.format(java.util.Locale.ROOT,"%02d:%02d",remaining/60,remaining%60),8).withStyle(ChatFormatting.AQUA));lastLineupStatusSecond=second;}

    private List<RaceEntry> buildHeatEntries(TurtleRacingSavedData saved,TurtleCompetitionData c,int heat){
        List<TurtleCompetitionData.Registration> registrations=c.heats().get(heat);
        List<RaceEntry> entries=new ArrayList<>();List<TurtleStrategy> strategies=new ArrayList<>();int lane=0;
        for(TurtleCompetitionData.Registration registration:registrations){TurtleData turtle=saved.turtle(registration.turtleId()).orElse(null);if(turtle!=null){entries.add(RaceEntry.player(turtle,lane++));strategies.add(turtle.strategy());}}
        List<AiTurtleProfile> ai=AiTurtleCatalog.select(c.raceSeed()+heat,8-entries.size(),c.beachTheme(),strategies);
        for(AiTurtleProfile profile:ai)entries.add(RaceEntry.ai(profile,c.league(),c.raceSeed()+heat,lane++));
        return entries;
    }

    private void discardLineup(){if(lineupDisplay!=null){lineupDisplay.discard();lineupDisplay=null;}lastLineupStatusSecond=-1;lineupHash=0;}

    private void lockRegistration(TurtleRacingSavedData saved,TurtleCompetitionData c){
        for(TurtleCompetitionData.Registration registration:new ArrayList<>(c.registrations())){
            TurtleData turtle=saved.turtle(registration.turtleId()).orElse(null);
            if(turtle==null||!turtle.ownerId().equals(registration.playerId())||!c.league().accepts(turtle.raceClass()))c.unregister(registration.playerId());
        }
        c.lockEntries(saved);c.transition(TurtleCompetitionPhase.REGISTRATION_LOCKED,System.currentTimeMillis());saved.markChanged();
        showLockedLineup(saved,c);
        broadcast(Component.translatable("yoiko_core.turtle.broadcast.lineup_confirmed",c.selected().size(),c.heats().size()).withStyle(ChatFormatting.YELLOW));
    }

    private boolean prepareBettingQuotes(TurtleRacingSavedData saved,TurtleCompetitionData c){
        int expected=c.heats().size()*8;
        if(c.betQuotes().size()==expected)return true;
        if(bettingForecast==null||!c.id().equals(bettingForecastCompetitionId)){
            List<ForecastHeat> snapshots=new ArrayList<>();
            for(int heat=0;heat<c.heats().size();heat++)snapshots.add(new ForecastHeat(heat,
                    buildHeatEntries(saved,c,heat).stream().map(RaceEntry::replayCopy).toList()));
            TurtleCourse course=arena.course();TurtleWeather weather=c.weather();long seed=c.raceSeed();
            bettingForecastCompetitionId=c.id();
            bettingForecast=CompletableFuture.supplyAsync(()->simulateBettingQuotes(course,weather,seed,snapshots));
            return false;
        }
        if(!bettingForecast.isDone())return false;
        try{
            List<TurtleCompetitionData.BetQuote> quotes=bettingForecast.join();
            if(!c.id().equals(bettingForecastCompetitionId))return false;
            c.replaceBetQuotes(quotes);saved.markChanged();
            broadcast(Component.translatable("yoiko_core.turtle.broadcast.odds_confirmed",100).withStyle(ChatFormatting.GOLD));
            return true;
        }catch(RuntimeException exception){
            YoikoServerCore.LOGGER.error("Failed to simulate turtle betting odds for {}",c.id(),exception);
            c.replaceBetQuotes(uniformBettingQuotes(snapshotsForFallback(saved,c)));saved.markChanged();
            return true;
        }finally{
            bettingForecast=null;bettingForecastCompetitionId="";
        }
    }

    private List<ForecastHeat> snapshotsForFallback(TurtleRacingSavedData saved,TurtleCompetitionData c){
        List<ForecastHeat> snapshots=new ArrayList<>();
        for(int heat=0;heat<c.heats().size();heat++)snapshots.add(new ForecastHeat(heat,buildHeatEntries(saved,c,heat)));
        return snapshots;
    }

    private static List<TurtleCompetitionData.BetQuote> simulateBettingQuotes(TurtleCourse course,TurtleWeather weather,
                                                                               long seed,List<ForecastHeat> heats){
        final int trials=100;List<TurtleCompetitionData.BetQuote> result=new ArrayList<>();
        for(ForecastHeat heat:heats){
            Map<String,Long> wins=java.util.stream.IntStream.range(0,trials).parallel().mapToObj(trial->{
                List<RaceEntry> entries=heat.entries().stream().map(RaceEntry::replayCopy).toList();
                RaceSimulation simulation=new RaceSimulation(course,weather,seed+heat.heat()*10_007L+trial*1_009L,entries);
                while(!simulation.complete())simulation.tick();
                return simulation.standings().getFirst().entryId();
            }).collect(java.util.stream.Collectors.groupingBy(value->value,java.util.stream.Collectors.counting()));
            int entrants=heat.entries().size();
            for(RaceEntry entry:heat.entries()){
                int won=Math.toIntExact(wins.getOrDefault(entry.entryId(),0L));
                double probability=(won+1.0)/(trials+entrants);
                int basisPoints=(int)Math.round(probability*10_000.0);
                int oddsMilli=(int)Math.round(900.0/probability);
                oddsMilli=Math.max(1_050,Math.min(20_000,oddsMilli));
                result.add(new TurtleCompetitionData.BetQuote(heat.heat(),entry.entryId(),trials,won,basisPoints,oddsMilli));
            }
        }
        return List.copyOf(result);
    }

    private static List<TurtleCompetitionData.BetQuote> uniformBettingQuotes(List<ForecastHeat> heats){
        List<TurtleCompetitionData.BetQuote> result=new ArrayList<>();
        for(ForecastHeat heat:heats){int entrants=Math.max(1,heat.entries().size());int bps=10_000/entrants;int odds=Math.min(20_000,900*entrants);for(RaceEntry entry:heat.entries())result.add(new TurtleCompetitionData.BetQuote(heat.heat(),entry.entryId(),100,0,bps,odds));}
        return result;
    }

    private void beginCurrentHeatCountdown(TurtleRacingSavedData saved,TurtleCompetitionData c){
        List<RaceEntry> entries=buildHeatEntries(saved,c,c.currentHeat());
        if(entries.size()!=8)throw new IllegalStateException("AI lineup did not fill all eight lanes");
        race=new RaceSimulation(arena.course(),c.weather(),c.raceSeed()+c.currentHeat(),entries);
        lastRace=new LastRace(arena.course(),c.weather(),c.raceSeed()+c.currentHeat(),entries.stream().map(RaceEntry::replayCopy).toList());
        ServerLevel level=arenaLevel(saved);BlockPos center=saved.arenaCenter().orElseThrow();discardLineup();if(worldController!=null)worldController.discard();worldController=new TurtleRaceWorldController(level,race,center);worldController.spawn();announcedEventCount=0;skillHighlightUntil.clear();podiumShown=false;
        heatSpectators.clear();heatCheers.clear();lastCheerTick.clear();for(ServerPlayer player:level.players())if(player.distanceToSqr(center.getX()+.5,center.getY()+.5,center.getZ()+.5)<=96*96)heatSpectators.add(player.getUUID());
        arena.setLineState(TurtleArenaManager.LineState.WAITING);commentaryStage=0;lastCommentaryTick=-100;c.transition(TurtleCompetitionPhase.COUNTDOWN,System.currentTimeMillis());saved.markChanged();countdownStartedTick=server.getTickCount();
        setupRaceSidebar(c);
        worldController.showStartAnnouncement(
                Component.translatable("yoiko_core.turtle.start_sequence.race_title",c.currentHeat()+1).withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD),
                Component.translatable("yoiko_core.turtle.start_sequence.race_subtitle",leagueComponent(c.league())).withStyle(ChatFormatting.WHITE));
    }

    private void tickCountdown(TurtleRacingSavedData saved,TurtleCompetitionData c){
        long elapsed=server.getTickCount()-countdownStartedTick;
        worldController.syncRaceHud();
        if(elapsed==TurtleRaceWorldController.START_ANNOUNCEMENT_INTERVAL_TICKS)worldController.showStartAnnouncement(
                Component.translatable("yoiko_core.turtle.start_sequence.lineup_title").withStyle(ChatFormatting.AQUA,ChatFormatting.BOLD),
                Component.translatable("yoiko_core.turtle.start_sequence.lineup_subtitle").withStyle(ChatFormatting.WHITE));
        else if(elapsed==TurtleRaceWorldController.START_ANNOUNCEMENT_INTERVAL_TICKS*2L)worldController.showStartAnnouncement(
                Component.translatable("yoiko_core.turtle.start_sequence.ready_title").withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD),
                Component.translatable("yoiko_core.turtle.start_sequence.ready_subtitle").withStyle(ChatFormatting.WHITE));
        else if(elapsed==TurtleRaceWorldController.START_COUNTDOWN_TICK)worldController.showCountdown(3);
        else if(elapsed==TurtleRaceWorldController.START_COUNTDOWN_TICK+TurtleRaceWorldController.COUNTDOWN_STEP_TICKS)worldController.showCountdown(2);
        else if(elapsed==TurtleRaceWorldController.START_COUNTDOWN_TICK+TurtleRaceWorldController.COUNTDOWN_STEP_TICKS*2L)worldController.showCountdown(1);
        else if(elapsed>=TurtleRaceWorldController.RACE_START_TICK){
            worldController.showCountdown(0);worldController.beginRace();arena.setLineState(TurtleArenaManager.LineState.RACING);
            c.transition(TurtleCompetitionPhase.RUNNING,System.currentTimeMillis());saved.markChanged();
            raceCommentary(Component.translatable("yoiko_core.turtle.commentary.start",c.currentHeat()+1),ChatFormatting.AQUA);
        }
    }

    private void tickRace(TurtleRacingSavedData saved,TurtleCompetitionData c){
        race.tick();worldController.tick();worldController.emitWeather(c.weather(),race.currentTick());if(racingLineVisualUntil>server.getTickCount()&&race.currentTick()%10==0)worldController.emitRacingLineDebug();announceRaceEvents();announceRaceFlow();if(race.currentTick()%5==0)updateRaceSidebar(c);if(race.currentTick()%10==0)sendPersonalRaceHud();
        if(!race.complete())return;
        arena.setLineState(TurtleArenaManager.LineState.RESULT);
        // Pay only carryover that existed before this heat. A no-winner pool created
        // by settleHeat therefore remains available for the next official heat.
        settleSpectators(saved,c);settleHeat(saved,c,race);c.transition(TurtleCompetitionPhase.RESULT_OPEN,System.currentTimeMillis());saved.markChanged();resultOpenedTick=server.getTickCount();resultPresentationCleared=false;
        List<RaceEntry> order=race.standings();raceCommentary(Component.translatable("yoiko_core.turtle.commentary.finish",commentaryHighlight(order.get(0).displayName(),ChatFormatting.GOLD),commentaryHighlight(order.get(1).displayName(),ChatFormatting.YELLOW),commentaryHighlight(order.get(2).displayName(),ChatFormatting.YELLOW)),ChatFormatting.GOLD);worldController.showRaceMoment(Component.translatable("yoiko_core.turtle.moment.winner",order.get(0).displayName()).withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD),Component.translatable("yoiko_core.turtle.moment.podium",order.get(1).displayName(),order.get(2).displayName()).withStyle(ChatFormatting.WHITE));
        if(order.get(0).finished()&&order.get(1).finished()){long photoDelta=Math.abs(finishMillis(order.get(0))-finishMillis(order.get(1)));if(photoDelta<=50){worldController.showPhotoFinish(order.get(0),order.get(1),photoDelta);raceCommentary(Component.translatable("yoiko_core.turtle.commentary.photo_finish",commentaryHighlight(order.get(0).displayName(),ChatFormatting.YELLOW),commentaryHighlight(order.get(1).displayName(),ChatFormatting.YELLOW),commentaryHighlight(photoDelta,ChatFormatting.AQUA)),ChatFormatting.YELLOW);}}
    }

    private void announceRaceEvents(){
        List<RaceEvent> events=race.events();
        for(int i=announcedEventCount;i<events.size();i++){
            RaceEvent event=events.get(i);
            RaceEntry e=race.entries().stream().filter(v->v.entryId().equals(event.sourceEntryId())).findFirst().orElse(null);
            if(e==null)continue;
            switch(event.type()){
                case "ACTIVE"->{worldController.emitActive(e);skillHighlightUntil.put(e.entryId(),race.currentTick()+Math.max(40,e.activeEffectTicksRemaining()));if(race.currentTick()-lastCommentaryTick>=12)raceCommentary(Component.translatable("yoiko_core.turtle.commentary.active",commentaryHighlight(e.displayName(),ChatFormatting.LIGHT_PURPLE),commentaryHighlight(activeComponent(e.activeSkill()),ChatFormatting.LIGHT_PURPLE)),ChatFormatting.LIGHT_PURPLE);}
                case "BREATH_START"->{worldController.emitBreathing(e,true);if(race.currentTick()-lastCommentaryTick>=30)raceCommentary(Component.translatable("yoiko_core.turtle.commentary.breathing",commentaryHighlight(e.displayName(),ChatFormatting.AQUA)),ChatFormatting.AQUA);}
                case "BREATH_END"->worldController.emitBreathing(e,false);
                case "FINISH"->worldController.emitFinish(e,e.finishRank());
                case "LEAD_CHANGE"->{RaceEntry previous=race.entries().stream().filter(v->v.entryId().equals(event.targetEntryId())).findFirst().orElse(null);Component text=previous==null?Component.translatable("yoiko_core.turtle.commentary.lead_change_open",commentaryHighlight(e.displayName(),ChatFormatting.GOLD)):Component.translatable("yoiko_core.turtle.commentary.lead_change_pass",commentaryHighlight(e.displayName(),ChatFormatting.GOLD),commentaryHighlight(previous.displayName(),ChatFormatting.YELLOW));raceCommentary(text,ChatFormatting.GOLD);}
                case "HOP"->{RaceEntry target=race.entries().stream().filter(v->v.entryId().equals(event.targetEntryId())).findFirst().orElse(null);if(race.currentTick()-lastCommentaryTick>=10){Component text=target==null?Component.translatable("yoiko_core.turtle.commentary.hop_unknown",commentaryHighlight(e.displayName(),ChatFormatting.GREEN)):Component.translatable("yoiko_core.turtle.commentary.hop",commentaryHighlight(e.displayName(),ChatFormatting.GREEN),commentaryHighlight(target.displayName(),ChatFormatting.YELLOW));raceCommentary(text,ChatFormatting.GREEN);}}
                default->{}
            }
        }
        announcedEventCount=events.size();
    }

    private void setupRaceSidebar(TurtleCompetitionData c){
        var scoreboard=server.getScoreboard();previousSidebar=scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        Objective existing=scoreboard.getObjective("yoiko_turtle_race");if(existing!=null)scoreboard.removeObjective(existing);
        raceSidebar=scoreboard.addObjective("yoiko_turtle_race",ObjectiveCriteria.DUMMY,Component.translatable("yoiko_core.turtle.scoreboard.title",c.currentHeat()+1).withStyle(ChatFormatting.AQUA),ObjectiveCriteria.RenderType.INTEGER,false,BlankFormat.INSTANCE);
        sidebarLineCache.clear();personalHudCache.clear();sidebarTitleCache="";scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR,raceSidebar);updateRaceSidebar(c);
    }

    private void updateRaceSidebar(TurtleCompetitionData c){
        if(raceSidebar==null||race==null)return;var scoreboard=server.getScoreboard();List<RaceEntry> order=race.standings();
        skillHighlightUntil.entrySet().removeIf(value->value.getValue()<race.currentTick());
        for(int rank=0;rank<order.size();rank++){
            RaceEntry entry=order.get(rank);String key=scoreName(entry);var access=scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(key),raceSidebar);
            var line=Component.translatable("yoiko_core.turtle.scoreboard.rank",rank+1,entry.displayName()).withStyle(rank==0?ChatFormatting.GOLD:ChatFormatting.WHITE);
            Long highlightedUntil=skillHighlightUntil.get(entry.entryId());
            if(highlightedUntil!=null&&highlightedUntil>=race.currentTick()){
                ChatFormatting flash=(race.currentTick()/5)%2==0?ChatFormatting.YELLOW:ChatFormatting.LIGHT_PURPLE;
                line.append(Component.literal("  ✦ ").append(activeComponent(entry.activeSkill())).append("!").withStyle(flash,ChatFormatting.BOLD));
            }
            String signature=(8-rank)+"|"+line.getString()+"|"+(highlightedUntil==null?0:(race.currentTick()/5)%2);if(!signature.equals(sidebarLineCache.get(key))){access.set(8-rank);access.display(line);sidebarLineCache.put(key,signature);}
        }
        int percent=Math.min(100,(int)Math.round(order.getFirst().progress()/race.course().length()*100));String title=Integer.toString(percent);if(!title.equals(sidebarTitleCache)){raceSidebar.setDisplayName(Component.translatable("yoiko_core.turtle.scoreboard.progress",percent).withStyle(ChatFormatting.AQUA));sidebarTitleCache=title;}
    }

    private void sendPersonalRaceHud(){if(race==null)return;List<RaceEntry> order=race.standings();for(RaceEntry entry:race.entries()){if(entry.ai())continue;ServerPlayer player=server.getPlayerList().getPlayer(entry.ownerId());if(player==null)continue;int rank=order.indexOf(entry)+1;Component state=null;String stateId="";ChatFormatting color=ChatFormatting.WHITE;if(entry.breathing()){state=Component.translatable("yoiko_core.turtle.hud.breathing");stateId="breathing";color=ChatFormatting.AQUA;}else if(entry.activeEffectRunning()){state=Component.literal("  ◆ ").append(activeComponent(entry.activeSkill()));stateId="active:"+entry.activeSkill().id();color=ChatFormatting.LIGHT_PURPLE;}else if(entry.interfered()){state=Component.translatable("yoiko_core.turtle.hud.interfered");stateId="interfered";color=ChatFormatting.DARK_PURPLE;}else if(entry.accelerationPenaltyActive()){state=Component.translatable("yoiko_core.turtle.hud.hop_landing");stateId="hop";color=ChatFormatting.YELLOW;}else if(entry.passiveSlowed()){state=Component.translatable("yoiko_core.turtle.hud.slowed");stateId="slow";color=ChatFormatting.RED;}else if(entry.passiveBoosted()){state=Component.translatable("yoiko_core.turtle.hud.boosted");stateId="boost";color=ChatFormatting.GREEN;}String signature=rank+"|"+String.format(java.util.Locale.ROOT,"%.1f",entry.speed())+"|"+Math.round(entry.stamina()*100)+"|"+stateId;if(signature.equals(personalHudCache.get(player.getUUID()))&&race.currentTick()%20!=0)continue;var text=Component.literal("────────  ").withStyle(ChatFormatting.DARK_GRAY).append(Component.translatable("yoiko_core.turtle.hud.status",rank,String.format(java.util.Locale.ROOT,"%.2f",entry.speed()),Math.round(entry.stamina()*100)).withStyle(ChatFormatting.AQUA));if(state!=null)text.append(state.copy().withStyle(color,ChatFormatting.BOLD));player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(text));personalHudCache.put(player.getUUID(),signature);}}

    private void announceRaceFlow(){
        if(race==null||race.complete())return;List<RaceEntry> order=race.standings();RaceEntry first=order.get(0),second=order.get(1),third=order.get(2);
        double ratio=first.progress()/race.course().length(),gap=first.progress()-second.progress();
        if(commentaryStage==0&&ratio>=.18){raceCommentary(Component.translatable("yoiko_core.turtle.commentary.early",commentaryHighlight(first.displayName(),ChatFormatting.AQUA),commentaryHighlight(second.displayName(),ChatFormatting.WHITE),commentaryHighlight(third.displayName(),ChatFormatting.WHITE)),ChatFormatting.WHITE);commentaryStage++;}
        else if(commentaryStage==1&&ratio>=.42){Component text=gap<.65?Component.translatable("yoiko_core.turtle.commentary.mid_pack",commentaryHighlight(first.displayName(),ChatFormatting.AQUA)):Component.translatable("yoiko_core.turtle.commentary.mid_lead",commentaryHighlight(first.displayName(),ChatFormatting.AQUA),commentaryHighlight(String.format(java.util.Locale.ROOT,"%.1f",gap),ChatFormatting.AQUA));raceCommentary(text,ChatFormatting.AQUA);commentaryStage++;}
        else if(commentaryStage==2&&ratio>=.67){raceCommentary(Component.translatable("yoiko_core.turtle.commentary.last_corner"),ChatFormatting.YELLOW);worldController.showRaceMoment(Component.translatable("yoiko_core.turtle.moment.last_corner").withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD),Component.translatable("yoiko_core.turtle.moment.last_corner_subtitle").withStyle(ChatFormatting.WHITE));commentaryStage++;}
        else if(commentaryStage==3&&ratio>=.82){arena.setLineState(TurtleArenaManager.LineState.FINAL_STRETCH);raceCommentary(Component.translatable("yoiko_core.turtle.commentary.final_stretch",commentaryHighlight(first.displayName(),ChatFormatting.GOLD),commentaryHighlight(second.displayName(),ChatFormatting.YELLOW),commentaryHighlight(third.displayName(),ChatFormatting.YELLOW)),ChatFormatting.GOLD);worldController.showRaceMoment(Component.translatable("yoiko_core.turtle.moment.final_stretch").withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD),Component.translatable("yoiko_core.turtle.moment.final_stretch_subtitle",first.displayName()).withStyle(ChatFormatting.YELLOW));commentaryStage++;}
        else if(commentaryStage==4&&ratio>=.94){raceCommentary(Component.translatable(gap<.45?"yoiko_core.turtle.commentary.finish_close":"yoiko_core.turtle.commentary.finish_lead",commentaryHighlight(first.displayName(),ChatFormatting.RED),commentaryHighlight(second.displayName(),ChatFormatting.YELLOW)),ChatFormatting.RED);commentaryStage++;}
        else if(race.currentTick()-lastCommentaryTick>=140&&ratio>.25){raceCommentary(Component.translatable("yoiko_core.turtle.commentary.current_order",commentaryHighlight(first.displayName(),ChatFormatting.AQUA),commentaryHighlight(second.displayName(),ChatFormatting.WHITE),commentaryHighlight(third.displayName(),ChatFormatting.WHITE)),ChatFormatting.GRAY);}
    }

    private void raceCommentary(Component text,ChatFormatting accent){
        Component message=Component.empty().append(Component.translatable("yoiko_core.turtle.commentary.live").withStyle(ChatFormatting.AQUA,ChatFormatting.BOLD)).append(Component.literal(" │ ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal("◆ ").withStyle(accent,ChatFormatting.BOLD)).append(text.copy().withStyle(ChatFormatting.WHITE));for(ServerPlayer player:commentaryAudience())player.sendSystemMessage(message);if(race!=null)lastCommentaryTick=race.currentTick();
    }

    private static Component commentaryHighlight(Object value,ChatFormatting color){
        Component component=value instanceof Component existing?existing.copy():Component.literal(String.valueOf(value));
        return component.copy().withStyle(color,ChatFormatting.BOLD);
    }

    private Set<ServerPlayer> commentaryAudience(){Set<ServerPlayer> output=new HashSet<>();TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);TurtleCompetitionData c=competition();if(c==null)return output;if(!c.heats().isEmpty())for(var registration:c.heats().get(c.currentHeat())){ServerPlayer participant=server.getPlayerList().getPlayer(registration.playerId());if(participant!=null)output.add(participant);}BlockPos center=saved.arenaCenter().orElse(null);if(center==null||saved.arenaDimension().isBlank())return output;ServerLevel level=arenaLevel(saved);for(ServerPlayer player:level.players())if(player.distanceToSqr(center.getX()+.5,center.getY()+.5,center.getZ()+.5)<=96*96)output.add(player);return output;}

    public void showRacingLineVisual(){if(race==null||worldController==null)throw TurtleLocalizedException.of("yoiko_core.turtle.error.no_running_race");racingLineVisualUntil=server.getTickCount()+400L;}

    /** Headless deterministic replay for operators; it never pays rewards or mutates the live arena. */
    public ReplayResult replayLast(){if(lastRace==null)throw TurtleLocalizedException.of("yoiko_core.turtle.error.no_replay");List<RaceEntry> entries=lastRace.entries().stream().map(RaceEntry::replayCopy).toList();RaceSimulation replay=new RaceSimulation(lastRace.course(),lastRace.weather(),lastRace.seed(),entries);while(!replay.complete())replay.tick();return new ReplayResult(lastRace.seed(),replay.currentTick(),replay.hopCount(),replay.standings().stream().map(RaceEntry::name).toList());}

    private void discardRacePresentation(){if(worldController!=null){worldController.discard();worldController=null;}discardLineup();clearRaceSidebar();}
    private void clearRaceSidebar(){sidebarLineCache.clear();personalHudCache.clear();sidebarTitleCache="";if(raceSidebar==null)return;var scoreboard=server.getScoreboard();if(scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)==raceSidebar)scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR,previousSidebar);scoreboard.removeObjective(raceSidebar);raceSidebar=null;previousSidebar=null;}
    private static long finishMillis(RaceEntry entry){return Math.round(((entry.finishTick()-1)+entry.finishFraction())*50.0);}
    private static String scoreName(RaceEntry entry){return "§"+Integer.toHexString(Math.floorMod(entry.entryId().hashCode(),16))+"T"+Integer.toUnsignedString(entry.entryId().hashCode(),36);}

    private void settleHeat(TurtleRacingSavedData saved,TurtleCompetitionData c,RaceSimulation simulation){
        List<RaceEntry> order=simulation.standings();boolean valid=order.stream().anyMatch(RaceEntry::finished);
        if(!valid){refundHeat(c,c.currentHeat(),"NO_FINISHER");return;}
        for(int rank=1;rank<=order.size();rank++){RaceEntry entry=order.get(rank-1);if(entry.ai()||!c.official())continue;
            TurtleData turtle=saved.turtle(entry.turtleId()).orElse(null);if(turtle==null)continue;
            boolean finished=entry.finished();
            int racePoints=finished?switch(rank){case 1->40;case 2->25;case 3->15;case 4->8;case 5->4;default->0;}:0;
            TurtleRaceClass previousClass=turtle.raceClass();
            if(finished){int awakening=1+(rank<=3?1:0)+(rank==1?1:0);turtle.addAwakeningPoints(awakening);turtle.addRacePoints(racePoints);}
            int ratingChange=turtle.adjustRaceRating(ratingChange(entry,rank,order,finished));
            long finishMillis=finished?Math.round(((entry.finishTick()-1)+entry.finishFraction())*50.0):0;
            int recordedRank=finished?rank:order.size();
            turtle.recordOfficialRace(new TurtleData.LastRaceSummary(recordedRank,finished,finishMillis,(int)Math.round(entry.stamina()*100),
                    entry.overtakes(),entry.laneChanges(),entry.totalBlockedTicks(),entry.breathingCount(),entry.activeUsed(),
                    ratingChange),entry.strategy());
            ServerPlayer owner=server.getPlayerList().getPlayer(entry.ownerId());
            if(owner!=null){Component result=finished?Component.translatable("yoiko_core.turtle.message.race_points_rating",turtle.displayName(),racePoints,turtle.racePoints(),ratingChange>=0?"+"+ratingChange:String.valueOf(ratingChange),turtle.raceRating()).withStyle(ChatFormatting.AQUA):Component.translatable("yoiko_core.turtle.message.race_dnf_rating",turtle.displayName(),ratingChange,turtle.raceRating()).withStyle(ChatFormatting.RED);if(finished&&previousClass!=turtle.raceClass())result=result.copy().append(Component.translatable("yoiko_core.turtle.message.class_promoted",turtle.raceClass().name()).withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD));owner.sendSystemMessage(result);if(finished)com.yoiko.core.economy.RestedGoldManager.awardActivity(owner,com.yoiko.core.economy.RestedGoldManager.Activity.TURTLE_RACE);}
            if(!finished)continue;
            if(rank==1&&c.league()==TurtleLeague.OPEN){
                TurtleGoldenShellAdvancement.recordWin(server,entry.ownerId(),entry.strategy());
            }
            TurtlePlayerProgress progress=saved.getOrCreatePlayer(entry.ownerId());progress.recordOfficialFinish();if(rank==1)progress.recordOfficialWin();if(owner!=null)TurtleAchievementManager.sync(owner,progress);int medals=rank==1?120:rank==2?90:rank==3?70:rank<=5?50:35;progress.addShellMedals(medals);
            int gems=0;if(c.official()&&progress.claimReward(c.periodKey()+":finish_gem"))gems++;if(c.official()&&rank==1&&progress.claimReward(c.periodKey()+":win_gem"))gems++;
            if(gems>0)addOfflineCurrency(entry.ownerId(),CurrencyType.GEM,gems,"TURTLE_RACE_GEMS_CREATED",c.id());
            saved.addWeeklyResult(new TurtleWeeklyResult(weeklyKey(),c.league(),entry.ownerId(),rank,finishMillis,System.currentTimeMillis()));
        }
        settleBets(c,c.currentHeat(),order.get(0).entryId());saved.markChanged();
    }

    private static int ratingChange(RaceEntry entry,int rank,List<RaceEntry> order,boolean finished){
        if(order.size()<=1)return 0;
        double expected=0;
        for(RaceEntry opponent:order)if(opponent!=entry)expected+=1.0/(1.0+Math.pow(10.0,(opponent.rating()-entry.rating())/400.0));
        expected/=order.size()-1;
        double actual=finished?(order.size()-rank)/(double)(order.size()-1):0;
        return (int)Math.round(48.0*(actual-expected));
    }

    private void settleBets(TurtleCompetitionData c,int heat,String winner){
        List<TurtleCompetitionData.Bet> bets=c.bets().stream().filter(v->v.heatIndex()==heat).toList();
        long total=bets.stream().mapToLong(TurtleCompetitionData.Bet::amount).sum();
        if(total<=0)return;
        Map<UUID,Long> grossByPlayer=new java.util.LinkedHashMap<>();
        for(TurtleCompetitionData.Bet bet:bets){
            if(!bet.entryId().equals(winner))continue;
            long gross=bet.amount()*Math.max(1_000,bet.oddsMilli())/1_000L;
            grossByPlayer.merge(bet.playerId(),gross,Long::sum);
        }
        long paid=0L,capped=0L;
        for(Map.Entry<UUID,Long> payout:grossByPlayer.entrySet()){
            long limited=TurtleBettingRules.capGrossPayout(payout.getValue());
            capped+=Math.max(0L,payout.getValue()-limited);
            paid+=addOfflineCurrency(payout.getKey(),CurrencyType.GOLD,limited,"TURTLE_BET_PAYOUT",c.id());
        }
        long burned=Math.max(0L,total-paid),created=Math.max(0L,paid-total);
        ServerYoikoAuditSavedData audit=ServerYoikoAuditSavedData.get(server);
        if(burned>0L)audit.addOperational("ECONOMY","TURTLE_BET_GOLD_BURNED",null,"server",
                c.id()+",heat="+heat+",amount="+burned+",staked="+total+",paid="+paid+",payout_capped="+capped);
        if(created>0L)audit.addOperational("ECONOMY","TURTLE_BET_GOLD_CREATED",null,"server",
                c.id()+",heat="+heat+",amount="+created+",staked="+total+",paid="+paid+",payout_capped="+capped);
    }
    private void refundHeat(TurtleCompetitionData c,int heat,String reason){TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);for(TurtleCompetitionData.Bet bet:c.bets())if(bet.heatIndex()==heat){addOfflineCurrency(bet.playerId(),CurrencyType.GOLD,bet.amount(),"TURTLE_BET_REFUND",c.id()+":"+reason);saved.getOrCreatePlayer(bet.playerId()).refundBetStake(bet.dailyPeriodKey(),bet.amount());}saved.markChanged();}
    private RefundSummary refundUnsettledBets(TurtleCompetitionData c,String reason){
        int firstUnsettled=switch(c.phase()){
            // Entering map cleanup means either every heat was settled or cancellation already
            // refunded the remaining pool. Treating it as resolved makes force-stop idempotent.
            case CLEANING_MAP,COMPLETED,CANCELLED->Integer.MAX_VALUE;
            case RESULT_OPEN->c.currentHeat()+1;
            default->c.currentHeat()+(race!=null&&race.complete()?1:0);
        };
        int refunded=0;long gold=0;
        for(TurtleCompetitionData.Bet bet:c.bets()){
            if(bet.heatIndex()<firstUnsettled)continue;
            addOfflineCurrency(bet.playerId(),CurrencyType.GOLD,bet.amount(),"TURTLE_BET_REFUND",c.id()+":"+reason);
            TurtleRacingSavedData.get(server).getOrCreatePlayer(bet.playerId()).refundBetStake(bet.dailyPeriodKey(),bet.amount());
            refunded++;gold+=bet.amount();
        }
        return new RefundSummary(refunded,gold);
    }

    public void register(ServerPlayer player,UUID turtleId){TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);TurtleCompetitionData c=saved.competition().orElseThrow(()->TurtleLocalizedException.of("yoiko_core.turtle.error.no_competition"));TurtleData turtle=saved.turtle(turtleId).orElseThrow(()->TurtleLocalizedException.of("yoiko_core.turtle.error.turtle_not_found"));if(!turtle.ownerId().equals(player.getUUID()))throw TurtleLocalizedException.of("yoiko_core.turtle.error.not_owner");if(timeTrial.isActiveTurtle(turtleId))throw TurtleLocalizedException.of("yoiko_core.turtle.error.register_time_trial");if(saved.getOrCreatePlayer(player.getUUID()).pendingPassiveReroll().filter(v->v.turtleId().equals(turtleId)).isPresent())throw TurtleLocalizedException.of("yoiko_core.turtle.error.register_reroll_pending");if(!c.league().accepts(turtle.raceClass()))throw TurtleLocalizedException.of("yoiko_core.turtle.error.league_mismatch",leagueComponent(c.league()),turtle.raceClass().name());c.register(new TurtleCompetitionData.Registration(player.getUUID(),turtleId,System.currentTimeMillis(),turtle.raceRating()));saved.markChanged();updateRegistrationLineup(saved,c,System.currentTimeMillis());}
    public void unregister(ServerPlayer player){TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);TurtleCompetitionData c=saved.competition().orElseThrow(()->TurtleLocalizedException.of("yoiko_core.turtle.error.no_competition"));if(c.phase()!=TurtleCompetitionPhase.REGISTRATION_OPEN)throw TurtleLocalizedException.of("yoiko_core.turtle.error.unregister_closed");if(c.unregister(player.getUUID())){saved.markChanged();updateRegistrationLineup(saved,c,System.currentTimeMillis());}}
    public void bet(ServerPlayer player,int heat,String entryId,long amount){if(!TurtleBettingRules.validStake(amount))throw TurtleLocalizedException.of("yoiko_core.turtle.error.bet_amount",TurtleBettingRules.MIN_STAKE,TurtleBettingRules.MAX_STAKE_PER_RACE);TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);TurtleCompetitionData c=saved.competition().orElseThrow(()->TurtleLocalizedException.of("yoiko_core.turtle.error.no_competition"));if(c.phase()!=TurtleCompetitionPhase.BETTING_OPEN||!c.official()||c.heats().size()!=1||heat!=0)throw TurtleLocalizedException.of("yoiko_core.turtle.error.betting_final_only");if(c.registrations().stream().anyMatch(v->v.playerId().equals(player.getUUID())))throw TurtleLocalizedException.of("yoiko_core.turtle.error.participant_cannot_bet");if(!bettingCandidates(heat).contains(entryId))throw TurtleLocalizedException.of("yoiko_core.turtle.error.invalid_bet_candidate");TurtleCompetitionData.BetQuote quote=c.betQuote(heat,entryId);if(quote==null)throw TurtleLocalizedException.of("yoiko_core.turtle.error.odds_not_ready");if(c.bets().stream().anyMatch(v->v.playerId().equals(player.getUUID())&&v.heatIndex()==heat&&!v.entryId().equals(entryId)))throw TurtleLocalizedException.of("yoiko_core.turtle.error.one_bet_candidate");if(c.betTotal(player.getUUID(),heat)+amount>TurtleBettingRules.MAX_STAKE_PER_RACE)throw TurtleLocalizedException.of("yoiko_core.turtle.error.race_bet_limit",TurtleBettingRules.MAX_STAKE_PER_RACE);String period=YoikoResetClock.dailyPeriodKey(System.currentTimeMillis());TurtlePlayerProgress progress=saved.getOrCreatePlayer(player.getUUID());if(progress.dailyBetStake(period)+amount>TurtleBettingRules.MAX_DAILY_STAKE)throw TurtleLocalizedException.of("yoiko_core.turtle.error.daily_bet_limit",TurtleBettingRules.MAX_DAILY_STAKE);if(!CurrencyManager.take(player,CurrencyType.GOLD,amount))throw TurtleLocalizedException.of("yoiko_core.turtle.error.gold_short");ServerYoikoAuditSavedData.get(server).addOperational("ECONOMY","TURTLE_BET_STAKED",player.getUUID(),player.getGameProfile().getName(),"competition="+c.id()+";heat="+heat+";candidate="+entryId+";odds_milli="+quote.oddsMilli()+";amount="+amount);progress.recordBetStake(period,amount);c.addBet(new TurtleCompetitionData.Bet(player.getUUID(),heat,entryId,amount,quote.oddsMilli(),period));saved.markChanged();}

    public void cheer(ServerPlayer player,String entryId){TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);TurtleCompetitionData c=saved.competition().orElseThrow(()->TurtleLocalizedException.of("yoiko_core.turtle.error.no_competition"));if(c.phase()!=TurtleCompetitionPhase.RUNNING||race==null||worldController==null)throw TurtleLocalizedException.of("yoiko_core.turtle.error.cheer_running_only");BlockPos center=saved.arenaCenter().orElseThrow(()->TurtleLocalizedException.of("yoiko_core.turtle.error.arena_center_missing"));if(!player.level().dimension().location().toString().equals(saved.arenaDimension())||player.distanceToSqr(center.getX()+.5,center.getY()+.5,center.getZ()+.5)>96*96)throw TurtleLocalizedException.of("yoiko_core.turtle.error.cheer_inside_arena");if(race.entries().stream().noneMatch(v->v.entryId().equals(entryId)))throw TurtleLocalizedException.of("yoiko_core.turtle.error.not_current_heat_turtle");int count=heatCheers.getOrDefault(player.getUUID(),0);if(count>=3)throw TurtleLocalizedException.of("yoiko_core.turtle.error.cheer_limit",3);long now=server.getTickCount();long previous=lastCheerTick.getOrDefault(player.getUUID(),Long.MIN_VALUE/2);if(now-previous<100)throw TurtleLocalizedException.of("yoiko_core.turtle.error.cheer_cooldown",5);lastCheerTick.put(player.getUUID(),now);heatCheers.put(player.getUUID(),count+1);worldController.emitCheer(entryId);player.sendSystemMessage(Component.translatable("yoiko_core.turtle.message.cheer",count+1,3));}

    private void settleSpectators(TurtleRacingSavedData saved,TurtleCompetitionData c){
        if(!c.official())return;
        BlockPos center=saved.arenaCenter().orElse(null);if(center==null)return;
        ServerLevel level=arenaLevel(saved);
        Set<UUID> participants=new HashSet<>();
        for(var registration:c.heats().get(c.currentHeat()))participants.add(registration.playerId());
        List<UUID> eligibleCarryover=new ArrayList<>();
        for(UUID playerId:heatSpectators){
            ServerPlayer player=server.getPlayerList().getPlayer(playerId);
            if(player==null||player.level()!=level||player.distanceToSqr(center.getX()+.5,center.getY()+.5,center.getZ()+.5)>96*96)continue;
            TurtlePlayerProgress progress=saved.getOrCreatePlayer(playerId);
            if(progress.claimReward(c.periodKey()+":spectated"))progress.addShellMedals(20);
            if(!participants.contains(playerId))eligibleCarryover.add(playerId);
            if(heatCheers.getOrDefault(playerId,0)>=3){
                if(progress.claimReward(c.periodKey()+":cheer_medals"))progress.addShellMedals(5);
            }
        }
        if(!eligibleCarryover.isEmpty()&&saved.spectatorCarryoverGold()>0L){
            eligibleCarryover.sort(java.util.Comparator.comparing(UUID::toString));
            long pool=saved.takeSpectatorCarryoverGold();
            long share=pool/eligibleCarryover.size();
            long remainder=pool%eligibleCarryover.size();
            long paid=0L;
            for(int i=0;i<eligibleCarryover.size();i++){
                UUID playerId=eligibleCarryover.get(i);
                long payout=share+(i<remainder?1L:0L);
                if(payout<=0L)continue;
                long added=addOfflineCurrency(playerId,CurrencyType.GOLD,payout,"TURTLE_SPECTATOR_CARRYOVER_PAID",c.id());
                paid+=added;
                ServerPlayer player=server.getPlayerList().getPlayer(playerId);
                if(player!=null&&added>0L)player.sendSystemMessage(Component.translatable("yoiko_core.turtle.message.spectator_carryover",added).withStyle(ChatFormatting.GOLD));
            }
            ServerYoikoAuditSavedData.get(server).addOperational("ECONOMY","TURTLE_SPECTATOR_CARRYOVER_DISTRIBUTED",null,"server",
                    c.id()+",amount="+paid+",pool="+pool+",recipients="+eligibleCarryover.size());
            long balanceCapBurn=Math.max(0L,pool-paid);
            if(balanceCapBurn>0L)ServerYoikoAuditSavedData.get(server).addOperational("ECONOMY","TURTLE_BET_GOLD_BURNED",null,"server",
                    c.id()+",amount="+balanceCapBurn+",reason=carryover_balance_cap");
        }
        saved.markChanged();
    }

    public List<String> bettingCandidates(int heat){TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);TurtleCompetitionData c=saved.competition().orElseThrow();if(heat<0||heat>=c.heats().size())return List.of();List<TurtleCompetitionData.Registration> regs=c.heats().get(heat);List<String> ids=new ArrayList<>();List<TurtleStrategy> strategies=new ArrayList<>();for(var r:regs){TurtleData t=saved.turtle(r.turtleId()).orElse(null);if(t!=null){ids.add("player:"+t.id());strategies.add(t.strategy());}}AiTurtleCatalog.select(c.raceSeed()+heat,8-ids.size(),c.beachTheme(),strategies).forEach(ai->ids.add("ai:"+ai.id()));return ids;}

    public void validateManualStart(boolean official){
        TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);
        if(saved.competition().isPresent()||arena.phase()!=TurtleArenaManager.Phase.IDLE||timeTrial.hasPendingWork())throw TurtleLocalizedException.of("yoiko_core.turtle.error.arena_busy");
        if(saved.arenaCenter().isEmpty())throw TurtleLocalizedException.of("yoiko_core.turtle.error.arena_center_first");
        LocalTime t=now().toLocalTime();
        // Exhibitions may use an idle arena at any hour. The shared arena checks above still
        // prevent concurrent races; the scheduler retries after the exhibition's cleanup.
        if(official&&((!t.isBefore(LocalTime.of(18,30))&&t.isBefore(LocalTime.of(20,40)))||(!t.isBefore(LocalTime.of(4,40))&&t.isBefore(LocalTime.of(5,10)))))throw TurtleLocalizedException.of("yoiko_core.turtle.error.protected_schedule");
        String period=YoikoResetClock.dailyPeriodKey(System.currentTimeMillis());
        if(official&&saved.manualOfficialOpenedPeriodKey().equals(period))throw TurtleLocalizedException.of("yoiko_core.turtle.error.manual_official_used");
    }

    public void startManual(TurtleLeague league,boolean official,int registrationMinutes,long seed,boolean beachTheme,String themeId,TurtleWeather weather){
        validateManualStart(official);
        TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);String period=YoikoResetClock.dailyPeriodKey(System.currentTimeMillis());
        TurtleCompetitionData c=new TurtleCompetitionData("manual:"+period+":"+UUID.randomUUID(),period,league,seed,mix(seed,0xBEEFL),beachTheme,weather,official,false,System.currentTimeMillis());
        c.setRegistrationMinutes(registrationMinutes);
        arena.beginBuild(seed,beachTheme,themeId);
        if(arena.failureReason()!=null)throw TurtleLocalizedException.of("yoiko_core.turtle.error.arena_precheck",arena.failureReason());
        saved.setCompetition(c);if(official)saved.setManualOfficialOpenedPeriodKey(period);
        broadcast(Component.translatable(official?"yoiko_core.turtle.broadcast.manual_official":"yoiko_core.turtle.broadcast.manual_exhibition").withStyle(official?ChatFormatting.GOLD:ChatFormatting.YELLOW));
    }

    public void cancelCompetition(TurtleCompetitionData c,String reason){if(c==null)return;c.transition(TurtleCompetitionPhase.CANCELLING,System.currentTimeMillis());TurtleRacingSavedData.get(server).markChanged();ServerYoikoAuditSavedData.get(server).addOperational("TURTLE","TURTLE_RACE_CANCELLED",null,"server",c.id()+":"+reason);}
    public ForceStopResult forceStopCompetition(String reason){
        TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);
        TurtleCompetitionData c=saved.competition().orElseThrow(()->TurtleLocalizedException.of("yoiko_core.turtle.error.no_competition"));
        TurtleCompetitionPhase previousPhase=c.phase();
        String normalizedReason=reason==null||reason.isBlank()?"ADMIN_FORCE_STOP":reason.trim();
        RefundSummary refunds=refundUnsettledBets(c,"FORCE_STOP:"+normalizedReason);

        discardRacePresentation();
        for(TurtleCompetitionData.Registration registration:c.registrations()){
            ServerPlayer player=server.getPlayerList().getPlayer(registration.playerId());
            if(player!=null)player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(Component.empty()));
        }
        race=null;
        heatSpectators.clear();heatCheers.clear();lastCheerTick.clear();skillHighlightUntil.clear();
        announcedEventCount=0;commentaryStage=0;lastCommentaryTick=-100;racingLineVisualUntil=0;
        resultPresentationCleared=true;podiumShown=false;
        arena.setLineState(TurtleArenaManager.LineState.WAITING);
        arena.beginCleanup();

        c.transition(TurtleCompetitionPhase.CANCELLED,System.currentTimeMillis());
        saved.clearCompetition();
        ServerYoikoAuditSavedData.get(server).addOperational("TURTLE","TURTLE_RACE_FORCE_STOPPED",null,"server",
                c.id()+":phase="+previousPhase+":refunds="+refunds.bets()+":gold="+refunds.gold()+":reason="+normalizedReason);
        broadcast(Component.translatable("yoiko_core.turtle.broadcast.force_stopped",refunds.bets(),refunds.gold()).withStyle(ChatFormatting.RED));
        return new ForceStopResult(c.id(),previousPhase,refunds.bets(),refunds.gold());
    }
    public void forceStart(){
        TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);TurtleCompetitionData c=saved.competition().orElseThrow(()->TurtleLocalizedException.of("yoiko_core.turtle.error.no_competition"));
        if(arena.phase()!=TurtleArenaManager.Phase.READY)throw TurtleLocalizedException.of("yoiko_core.turtle.error.arena_not_ready");
        if(c.phase()==TurtleCompetitionPhase.MAP_READY)c.transition(TurtleCompetitionPhase.REGISTRATION_OPEN,System.currentTimeMillis());
        if(c.phase()==TurtleCompetitionPhase.REGISTRATION_OPEN)lockRegistration(saved,c);
        if(c.heats().isEmpty())throw TurtleLocalizedException.of("yoiko_core.turtle.error.no_lineup");
        if(c.phase()==TurtleCompetitionPhase.REGISTRATION_LOCKED||c.phase()==TurtleCompetitionPhase.BETTING_OPEN||c.phase()==TurtleCompetitionPhase.BETTING_LOCKED||c.phase()==TurtleCompetitionPhase.STAGING){c.transition(TurtleCompetitionPhase.BETTING_LOCKED,System.currentTimeMillis());saved.markChanged();beginCurrentHeatCountdown(saved,c);return;}
        throw TurtleLocalizedException.of("yoiko_core.turtle.error.force_start_phase",c.phase().name());
    }
    private ServerLevel arenaLevel(TurtleRacingSavedData saved){ResourceKey<Level> key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(saved.arenaDimension()));ServerLevel level=server.getLevel(key);if(level==null)throw TurtleLocalizedException.of("yoiko_core.turtle.error.arena_dimension_missing");return level;}
    private long addOfflineCurrency(UUID playerId,CurrencyType type,long amount,String action,String detail){ServerYoikoSavedData root=ServerYoikoSavedData.get(server);PlayerYoikoData data=root.get(playerId);long added=CurrencyManager.add(data,type,amount);if(added>0)root.markDirty(playerId);ServerYoikoAuditSavedData.get(server).addOperational("ECONOMY",action,playerId,data.name,detail+",amount="+added);return added;}
    private void rollWeeklyBoard(TurtleRacingSavedData saved){String current=weeklyKey();if(saved.activeWeeklyKey().isBlank()){saved.setActiveWeeklyKey(current);return;}if(saved.activeWeeklyKey().equals(current))return;String previous=saved.activeWeeklyKey();if(!saved.settledWeeklyKey().equals(previous))settleWeeklyBoard(saved,previous);saved.clearWeeklyResults(previous);saved.setSettledWeeklyKey(previous);saved.setActiveWeeklyKey(current);}
    private void settleWeeklyBoard(TurtleRacingSavedData saved,String week){List<TurtleWeeklyResult> all=saved.weeklyResults().stream().filter(v->v.weekKey().equals(week)).toList();record Board(UUID player,TurtleLeague league,int points,int bestRank,long time,long reachedAt,int participants){}List<Board> boards=new ArrayList<>();for(TurtleLeague league:List.of(TurtleLeague.CORAL,TurtleLeague.CURRENT,TurtleLeague.ABYSS,TurtleLeague.OPEN)){Map<UUID,List<TurtleWeeklyResult>> byPlayer=new HashMap<>();all.stream().filter(v->v.league()==league).forEach(v->byPlayer.computeIfAbsent(v.playerId(),ignored->new ArrayList<>()).add(v));int participants=byPlayer.size();for(var entry:byPlayer.entrySet()){int limit=league==TurtleLeague.OPEN?1:2;List<TurtleWeeklyResult> selected=entry.getValue().stream().sorted(Comparator.comparingInt(TurtleWeeklyResult::points).reversed().thenComparingInt(TurtleWeeklyResult::rank).thenComparingLong(TurtleWeeklyResult::finishMillis)).limit(limit).toList();boards.add(new Board(entry.getKey(),league,selected.stream().mapToInt(TurtleWeeklyResult::points).sum(),selected.stream().mapToInt(TurtleWeeklyResult::rank).min().orElse(8),selected.stream().mapToLong(TurtleWeeklyResult::finishMillis).sum(),selected.stream().mapToLong(TurtleWeeklyResult::createdAt).max().orElse(0),participants));}}Map<UUID,Integer> bestRewardRank=new HashMap<>();for(TurtleLeague league:List.of(TurtleLeague.CORAL,TurtleLeague.CURRENT,TurtleLeague.ABYSS,TurtleLeague.OPEN)){List<Board> ranked=boards.stream().filter(v->v.league()==league).sorted(Comparator.comparingInt(Board::points).reversed().thenComparingInt(Board::bestRank).thenComparingLong(Board::time).thenComparingLong(Board::reachedAt)).toList();for(int i=0;i<ranked.size();i++){int rank=i+1;Board board=ranked.get(i);boolean eligible=rank<=3?board.participants()>=3:rank<=10&&board.participants()>=10;if(eligible)bestRewardRank.merge(board.player(),rank,Math::min);}}for(var entry:bestRewardRank.entrySet()){UUID playerId=entry.getKey();int rank=entry.getValue();TurtlePlayerProgress progress=saved.getOrCreatePlayer(playerId);String key="weekly:"+week+":ranking";if(!progress.claimReward(key))continue;int medals=rank==1?200:rank==2?150:rank==3?120:110;progress.addShellMedals(medals);if(rank==1){progress.addTicket(TurtleTicketType.PICKUP,1);addOfflineCurrency(playerId,CurrencyType.GEM,1,"TURTLE_WEEKLY_GEMS_CREATED",week+",rank="+rank);}else if(rank<=3)progress.addTicket(TurtleTicketType.STANDARD,1);}Map<UUID,Long> finishes=new HashMap<>();all.forEach(v->finishes.merge(v.playerId(),1L,Long::sum));for(var entry:finishes.entrySet())if(entry.getValue()>=2){TurtlePlayerProgress progress=saved.getOrCreatePlayer(entry.getKey());if(progress.claimReward("weekly:"+week+":two_finishes"))progress.addShellMedals(50);}saved.markChanged();}
    public record WeeklySummary(int finishes,int bestRank){}
    public WeeklySummary weeklySummary(UUID playerId){String week=weeklyKey();List<TurtleWeeklyResult> results=TurtleRacingSavedData.get(server).weeklyResults().stream().filter(v->v.weekKey().equals(week)&&v.playerId().equals(playerId)).toList();return new WeeklySummary(results.size(),results.stream().mapToInt(TurtleWeeklyResult::rank).min().orElse(0));}
    private static String weeklyKey(){LocalDate date=YoikoResetClock.dailyPeriodDate(System.currentTimeMillis());return "week:"+date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();}
    private ZonedDateTime now(){return Instant.ofEpochMilli(System.currentTimeMillis()).atZone(YoikoResetClock.zone());}
    private void broadcast(Component component){server.getPlayerList().broadcastSystemMessage(component,false);}
    private static long mix(long a,long b){long z=a^Long.rotateLeft(b,21)^0x9E3779B97F4A7C15L;z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);}
    /** 40% clear, 35% theme weather, 25% light rain. */
    private static TurtleWeather scheduledWeather(long seed,boolean beach){int roll=Math.floorMod((int)(seed>>>8),100);if(roll<40)return TurtleWeather.CLEAR;if(roll<75)return beach?TurtleWeather.SEA_BREEZE:TurtleWeather.FOREST_MIST;return TurtleWeather.LIGHT_RAIN;}
    private static Component leagueComponent(TurtleLeague league){return Component.translatable("yoiko_core.turtle.ui.league."+league.name().toLowerCase(java.util.Locale.ROOT));}
    private static Component activeComponent(ActiveSkill skill){return Component.translatable("yoiko_core.turtle.skill.active."+skill.id()+".name");}
}
