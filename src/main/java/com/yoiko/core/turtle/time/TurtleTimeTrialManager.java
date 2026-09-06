package com.yoiko.core.turtle.time;

import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.reward.YoikoResetClock;
import com.yoiko.core.turtle.TurtleData;
import com.yoiko.core.turtle.TurtleAchievementManager;
import com.yoiko.core.turtle.race.TurtleCompetitionData;
import com.yoiko.core.turtle.TurtleLeague;
import com.yoiko.core.turtle.TurtleRaceClass;
import com.yoiko.core.turtle.TurtlePlayerProgress;
import com.yoiko.core.turtle.TurtleRacingSavedData;
import com.yoiko.core.turtle.TurtleWeather;
import com.yoiko.core.turtle.arena.TurtleArenaManager;
import com.yoiko.core.turtle.race.AiTurtleCatalog;
import com.yoiko.core.turtle.race.RaceEntry;
import com.yoiko.core.turtle.race.RaceSimulation;
import com.yoiko.core.turtle.race.TurtleRaceWorldController;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.yoiko.core.network.TurtleTimeTrialGhostPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TurtleTimeTrialManager {
    private static final long PODIUM_DELAY_TICKS=220L;
    private static final long PODIUM_VIEW_TICKS=300L;
    public enum State { IDLE, BUILDING, READY, COUNTDOWN, RUNNING, RESULT, CLEANING }
    private record Request(UUID playerId,UUID turtleId,TurtleRaceClass difficulty){}
    private static final String[] PRESET_IDS={"coast_sprint","boardwalk_turns","tailwind_longrun","forest_quickstep","mist_technical","forest_endurance"};
    private static final TurtleWeather[] PRESET_WEATHER={TurtleWeather.CLEAR,TurtleWeather.LIGHT_RAIN,TurtleWeather.SEA_BREEZE,TurtleWeather.CLEAR,TurtleWeather.FOREST_MIST,TurtleWeather.LIGHT_RAIN};
    private final MinecraftServer server;private final TurtleArenaManager arena;private final Deque<Request> queue=new ArrayDeque<>();
    private State state=State.IDLE;private Request active;private RaceSimulation race;private TurtleRaceWorldController world;private RaceEntry playerEntry;private final List<TimeTrialRecord.GhostSample> samples=new ArrayList<>();private TimeTrialRecord ghostRecord;private int attempts;private long stateTick;private int presetIndex=-1;private int visualEventCount;private boolean podiumShown;private long playerFinishedTick=-1;private long playerFinishMillis=-1;private boolean playerResultRecorded;
    public TurtleTimeTrialManager(MinecraftServer server,TurtleArenaManager arena){this.server=server;this.arena=arena;}
    public State state(){return state;} public UUID activePlayer(){return active==null?null:active.playerId;} public int queuePosition(UUID player){int i=1;for(Request r:queue){if(r.playerId.equals(player))return i;i++;}return 0;}
    public boolean hasPendingWork(){return state!=State.IDLE||active!=null||!queue.isEmpty();}
    public boolean isActiveTurtle(UUID turtleId){return active!=null&&active.turtleId.equals(turtleId)||queue.stream().anyMatch(value->value.turtleId.equals(turtleId));}
    public String presetId(){return PRESET_IDS[currentPresetIndex()];}

    public void request(ServerPlayer player,TurtleData turtle,TurtleRaceClass difficulty){
        if(!turtle.ownerId().equals(player.getUUID()))throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.not_owner");
        if(!withinOpenWindow())throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.time_trial_closed");
        Request request=new Request(player.getUUID(),turtle.id(),difficulty);
        TurtleCompetitionData competition=TurtleRacingSavedData.get(server).competition().orElse(null);
        if(competition!=null&&competition.registrations().stream().anyMatch(value->value.turtleId().equals(turtle.id())))
            throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.time_trial_registered");
        if(active!=null&&active.playerId.equals(player.getUUID())&&state==State.READY){if(world!=null){world.discard();world=null;}active=request;startAttempt();return;}
        if(active!=null&&active.playerId.equals(player.getUUID()))throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.time_trial_active");
        if(queue.stream().anyMatch(v->v.playerId.equals(player.getUUID())))throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.time_trial_queued");
        if(active==null&&state==State.IDLE&&queue.isEmpty()&&canAcquireArena()){
            beginLease();
            active=request;attempts=0;
            return;
        }
        if(queue.size()>=16)throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.time_trial_queue_full",16);
        queue.addLast(request);
    }
    public void cancel(UUID playerId){
        if(active!=null&&active.playerId.equals(playerId)){
            if(world!=null){world.discard();world=null;}
            active=null;race=null;playerEntry=null;
            if(arena.phase()==TurtleArenaManager.Phase.READY&&!queue.isEmpty())advanceQueueOrIdle();
            else beginCleanupOrIdle();
        }else queue.removeIf(v->v.playerId.equals(playerId));
    }

    public void tick(){
        if(state==State.IDLE&&active!=null){
            if(queue.stream().noneMatch(value->value.playerId.equals(active.playerId)))queue.addFirst(active);
            active=null;
        }
        if(state==State.IDLE&&active==null&&!queue.isEmpty()&&canAcquireArena())startQueuedLease();
        if(state==State.BUILDING&&arena.phase()==TurtleArenaManager.Phase.READY){state=State.READY;stateTick=server.getTickCount();startAttempt();}
        else if(state==State.BUILDING&&arena.failureReason()!=null&&arena.phase()!=TurtleArenaManager.Phase.BUILDING){failBuild(arena.failureReason());}
        else if(state==State.COUNTDOWN){long elapsed=server.getTickCount()-stateTick;world.syncRaceHud();world.emitPreRaceWeather(PRESET_WEATHER[presetIndex],server.getTickCount());
            if(elapsed==TurtleRaceWorldController.START_ANNOUNCEMENT_INTERVAL_TICKS)world.showStartAnnouncement(
                    Component.translatable("yoiko_core.turtle.start_sequence.lineup_title").withStyle(ChatFormatting.AQUA,ChatFormatting.BOLD),
                    Component.translatable("yoiko_core.turtle.start_sequence.lineup_subtitle").withStyle(ChatFormatting.WHITE));
            else if(elapsed==TurtleRaceWorldController.START_ANNOUNCEMENT_INTERVAL_TICKS*2L)world.showStartAnnouncement(
                    Component.translatable("yoiko_core.turtle.start_sequence.ready_title").withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD),
                    Component.translatable("yoiko_core.turtle.start_sequence.ready_subtitle").withStyle(ChatFormatting.WHITE));
            else if(elapsed==TurtleRaceWorldController.START_COUNTDOWN_TICK)world.showCountdown(3);
            else if(elapsed==TurtleRaceWorldController.START_COUNTDOWN_TICK+TurtleRaceWorldController.COUNTDOWN_STEP_TICKS)world.showCountdown(2);
            else if(elapsed==TurtleRaceWorldController.START_COUNTDOWN_TICK+TurtleRaceWorldController.COUNTDOWN_STEP_TICKS*2L)world.showCountdown(1);
            else if(elapsed>=TurtleRaceWorldController.RACE_START_TICK){world.showCountdown(0);world.beginRace();arena.setLineState(TurtleArenaManager.LineState.RACING);state=State.RUNNING;stateTick=server.getTickCount();}}
        else if(state==State.RUNNING){ServerPlayer player=server.getPlayerList().getPlayer(active.playerId);if(player==null){cancel(active.playerId);return;}race.tick();world.tick();world.emitWeather(PRESET_WEATHER[presetIndex],race.currentTick());if(race.standings().getFirst().progress()>=race.course().length()*.82)arena.setLineState(TurtleArenaManager.LineState.FINAL_STRETCH);emitRaceEvents();if(race.currentTick()%4==0&&!playerEntry.finished())sample();if(race.currentTick()%10==0&&!playerEntry.finished())showLiveRecord(player);if(playerEntry.finished()&&playerFinishedTick<0){playerFinishedTick=race.currentTick();recordPlayerFinish();}if(playerFinishedTick>=0&&(race.entries().stream().allMatch(RaceEntry::finished)||race.currentTick()-playerFinishedTick>=200)){if(race.entries().stream().anyMatch(e->!e.finished())){race.forceFinishRemaining();world.tick();emitRaceEvents();}finishAttempt();}else if(race.currentTick()>=3_000)finishAttempt();}
        else if(state==State.RESULT){long shown=server.getTickCount()-stateTick;if(!podiumShown&&world!=null)world.syncRaceHud();if(!podiumShown&&shown>=PODIUM_DELAY_TICKS){if(world!=null)world.showPodium(race.standings());podiumShown=true;}if(shown>=PODIUM_DELAY_TICKS+PODIUM_VIEW_TICKS){if(world!=null){world.discard();world=null;}race=null;if(attempts>=2){active=null;advanceQueueOrIdle();}else state=State.READY;}}
        else if(state==State.READY&&active==null&&server.getTickCount()-stateTick>=12_000){state=State.CLEANING;arena.beginCleanup();}
        else if(state==State.CLEANING&&arena.phase()==TurtleArenaManager.Phase.IDLE){state=State.IDLE;presetIndex=-1;}
        // IDLE does not own the shared arena: it may have just been built for a competition.
        // Only an unused, leased time-trial arena may be released when its window closes.
        if(state==State.READY&&presetIndex>=0&&active==null
                &&arena.phase()==TurtleArenaManager.Phase.READY
                &&TurtleRacingSavedData.get(server).competition().isEmpty()
                &&!withinOpenWindow())beginCleanupOrIdle();
    }

    private boolean canAcquireArena(){return TurtleRacingSavedData.get(server).competition().isEmpty()&&arena.phase()==TurtleArenaManager.Phase.IDLE;}
    private void beginLease(){if(!canAcquireArena())throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.arena_busy");presetIndex=currentPresetIndex();arena.beginBuildPreset(presetIndex);state=State.BUILDING;stateTick=server.getTickCount();}
    private void startAttempt(){TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);TurtleData turtle=saved.turtle(active.turtleId).orElseThrow(()->com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.turtle_not_found"));List<RaceEntry> entries=new ArrayList<>();String[] ids={"pebble","wavelet","sesame"};for(int i=0;i<3;i++)entries.add(RaceEntry.aiTimeTrial(AiTurtleCatalog.get(ids[i]),active.difficulty,i));playerEntry=RaceEntry.player(turtle,3);entries.add(playerEntry);String[] after={"coral_bean","sea_lantern","blue_crown","homeward"};for(int i=0;i<4;i++)entries.add(RaceEntry.aiTimeTrial(AiTurtleCatalog.get(after[i]),active.difficulty,i+4));long seed=(long)presetIndex*31+active.difficulty.ordinal()*997+1;race=new RaceSimulation(arena.course(),PRESET_WEATHER[presetIndex],seed,entries);ServerLevel level=arenaLevel(saved);world=new TurtleRaceWorldController(level,race,saved.arenaCenter().orElseThrow());arena.setLineState(TurtleArenaManager.LineState.WAITING);world.spawn();samples.clear();visualEventCount=0;podiumShown=false;playerFinishedTick=-1;playerFinishMillis=-1;playerResultRecorded=false;attempts++;state=State.COUNTDOWN;stateTick=server.getTickCount();world.showStartAnnouncement(Component.translatable("yoiko_core.turtle.start_sequence.time_trial_title").withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD),Component.translatable("yoiko_core.turtle.start_sequence.time_trial_subtitle",Component.translatable("yoiko_core.turtle.ui.preset."+presetId()),active.difficulty.name(),attempts).withStyle(ChatFormatting.WHITE));ServerPlayer player=server.getPlayerList().getPlayer(active.playerId);ghostRecord=saved.getOrCreatePlayer(active.playerId).timeTrialRecord(recordKey(active.difficulty));if(player!=null){List<TurtleTimeTrialGhostPayload.Sample> ghost=ghostRecord==null?List.of():ghostRecord.samples().stream().map(v->new TurtleTimeTrialGhostPayload.Sample(v.progressU16(),v.laneOffset(),v.hopHeightU8())).toList();var center=saved.arenaCenter().orElseThrow();PacketDistributor.sendToPlayer(player,new TurtleTimeTrialGhostPayload(center.getX(),center.getY(),center.getZ(),presetIndex,ghost));}}
    private void sample(){int p=(int)Math.round(playerEntry.progress()/race.course().length()*65_535);byte lane=(byte)Math.max(-127,Math.min(127,Math.round(playerEntry.laneOffset()/2.5*127)));short hop=(short)Math.max(0,Math.min(255,Math.round(playerEntry.hopYOffset()/.42*255)));samples.add(new TimeTrialRecord.GhostSample(p,lane,hop));}
    private void showLiveRecord(ServerPlayer player){long elapsed=race.currentTick()*50L;if(ghostRecord==null||ghostRecord.samples().isEmpty()){player.displayClientMessage(Component.translatable("yoiko_core.turtle.time_trial.live",formatNumber(elapsed)).withStyle(ChatFormatting.AQUA),true);return;}int progress=(int)Math.round(playerEntry.progress()/race.course().length()*65_535);int index=0;List<TimeTrialRecord.GhostSample> ghost=ghostRecord.samples();while(index+1<ghost.size()&&ghost.get(index).progressU16()<progress)index++;long expected=(index+1)*200L;long delta=elapsed-expected;String signed=(delta>=0?"+":"-")+formatNumber(Math.abs(delta));player.displayClientMessage(Component.translatable("yoiko_core.turtle.time_trial.live_delta",formatNumber(elapsed),signed).withStyle(delta<=0?ChatFormatting.GREEN:ChatFormatting.RED),true);}
    private void recordPlayerFinish(){if(playerResultRecorded)return;playerResultRecorded=true;playerFinishMillis=finishMillis(playerEntry);ServerPlayer player=server.getPlayerList().getPlayer(active.playerId);String key=recordKey(active.difficulty);TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);TurtlePlayerProgress progress=saved.getOrCreatePlayer(active.playerId);boolean best=progress.recordTimeTrial(new TimeTrialRecord(key,playerFinishMillis,System.currentTimeMillis(),samples));saved.markChanged();if(player!=null){TurtleAchievementManager.sync(player,progress);com.yoiko.core.economy.RestedGoldManager.awardActivity(player,com.yoiko.core.economy.RestedGoldManager.Activity.TURTLE_TIME_TRIAL);player.sendSystemMessage(Component.translatable(best?"yoiko_core.turtle.time_trial.finish_best":"yoiko_core.turtle.time_trial.finish",formatNumber(playerFinishMillis),10).withStyle(best?ChatFormatting.GREEN:ChatFormatting.WHITE));}ServerYoikoAuditSavedData.get(server).addOperational("TURTLE","TIME_TRIAL_FINISHED",active.playerId,player==null?"":player.getGameProfile().getName(),key+",millis="+playerFinishMillis+",best="+best);}
    private void finishAttempt(){arena.setLineState(TurtleArenaManager.LineState.RESULT);ServerPlayer player=server.getPlayerList().getPlayer(active.playerId);if(!playerEntry.finished()&&player!=null)player.sendSystemMessage(Component.translatable("yoiko_core.turtle.time_trial.timeout",150).withStyle(ChatFormatting.RED));List<RaceEntry> order=race.standings();if(order.size()>1&&order.get(0).finished()&&order.get(1).finished()){long delta=Math.abs(finishMillis(order.get(0))-finishMillis(order.get(1)));if(delta<=50)world.showPhotoFinish(order.get(0),order.get(1),delta);}podiumShown=false;state=State.RESULT;stateTick=server.getTickCount();}
    private void emitRaceEvents(){var events=race.events();for(int i=visualEventCount;i<events.size();i++){var event=events.get(i);String source=event.sourceEntryId();race.entries().stream().filter(v->v.entryId().equals(source)).findFirst().ifPresent(entry->{if(event.type().equals("ACTIVE"))world.emitActive(entry);else if(event.type().equals("BREATH_START"))world.emitBreathing(entry,true);else if(event.type().equals("BREATH_END"))world.emitBreathing(entry,false);else if(event.type().equals("FINISH"))world.emitFinish(entry,entry.finishRank());});}visualEventCount=events.size();}
    private void failBuild(String reason){if(active!=null){ServerPlayer player=server.getPlayerList().getPlayer(active.playerId);if(player!=null)player.sendSystemMessage(Component.translatable("yoiko_core.turtle.time_trial.arena_failed",reason).withStyle(ChatFormatting.RED));}for(Request request:queue){ServerPlayer player=server.getPlayerList().getPlayer(request.playerId);if(player!=null)player.sendSystemMessage(Component.translatable("yoiko_core.turtle.time_trial.queue_released").withStyle(ChatFormatting.RED));}queue.clear();active=null;race=null;if(world!=null){world.discard();world=null;}state=State.CLEANING;ServerYoikoAuditSavedData.get(server).addOperational("TURTLE","TIME_TRIAL_ARENA_FAILED",null,"server",reason);}
    private static long finishMillis(RaceEntry entry){return Math.round(((entry.finishTick()-1)+entry.finishFraction())*50.0);}
    private void startQueuedLease(){
        Request next=queue.peekFirst();
        beginLease();
        active=queue.removeFirst();attempts=0;
        if(!active.equals(next))throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.time_trial_queue_changed");
    }
    private void advanceQueueOrIdle(){
        if(!queue.isEmpty()&&arena.phase()==TurtleArenaManager.Phase.READY){active=queue.removeFirst();attempts=0;state=State.READY;startAttempt();}
        else beginCleanupOrIdle();
    }
    private void beginCleanupOrIdle(){
        active=null;
        if(arena.phase()==TurtleArenaManager.Phase.IDLE){state=State.IDLE;presetIndex=-1;stateTick=server.getTickCount();}
        else{state=State.CLEANING;arena.beginCleanup();stateTick=server.getTickCount();}
    }
    private int currentPresetIndex(){long day=YoikoResetClock.dailyPeriodDate(System.currentTimeMillis()).toEpochDay();return Math.floorMod((int)day,6);}
    private String recordKey(TurtleRaceClass difficulty){return PRESET_IDS[presetIndex]+":"+difficulty.name()+":preset1:balance2:ai2";}
    public String currentRecordKey(TurtleRaceClass difficulty){return PRESET_IDS[currentPresetIndex()]+":"+difficulty.name()+":preset1:balance2:ai2";}
    private boolean withinOpenWindow(){LocalTime time=Instant.ofEpochMilli(System.currentTimeMillis()).atZone(YoikoResetClock.zone()).toLocalTime();return (time.isAfter(LocalTime.of(5,9))&&time.isBefore(LocalTime.of(18,30)))||time.isAfter(LocalTime.of(20,39))||time.isBefore(LocalTime.of(4,40));}
    private ServerLevel arenaLevel(TurtleRacingSavedData saved){ResourceKey<Level> key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(saved.arenaDimension()));ServerLevel level=server.getLevel(key);if(level==null)throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.arena_dimension_missing");return level;}
    private static String formatNumber(long millis){return String.format(java.util.Locale.ROOT,"%d.%03d",millis/1000,millis%1000);}
}
