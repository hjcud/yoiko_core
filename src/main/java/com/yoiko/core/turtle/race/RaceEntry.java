package com.yoiko.core.turtle.race;

import com.yoiko.core.turtle.ActiveSkill;
import com.yoiko.core.turtle.TurtleAptitude;
import com.yoiko.core.turtle.TurtleData;
import com.yoiko.core.turtle.TurtleCompanion;
import com.yoiko.core.turtle.TurtleLeague;
import com.yoiko.core.turtle.TurtleNameCatalog;
import com.yoiko.core.turtle.TurtleRarity;
import com.yoiko.core.turtle.TurtleStat;
import com.yoiko.core.turtle.TurtleStats;
import com.yoiko.core.turtle.TurtleStrategy;
import com.yoiko.core.turtle.TurtleSurface;
import java.util.List;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.network.chat.Component;

public final class RaceEntry {
    private static final int HOP_DURATION_TICKS = 20;
    private static final int HOP_COOLDOWN_TICKS = 170;
    /** Eight 0.36-block baby turtles fit densely across a five-block road with curb clearance. */
    public static final double LANE_SPACING = 3.85/7.0;
    private final String entryId;
    private final UUID ownerId;
    private final UUID turtleId;
    private final String name;
    private final boolean ai;
    private final TurtleRarity rarity;
    private final TurtleStats stats;
    private final ActiveSkill activeSkill;
    private final List<String> passives;
    private final TurtleStrategy strategy;
    private final TurtleData source;
    private final int rating;
    private final String appearance;
    private final String bodyAppearance;
    private int lane;
    private int targetLane;
    private double laneChangeProgress = 1.0;
    private double progress;
    private double speed;
    private double stamina = 1.0;
    private double staminaSpent;
    private int blockedTicks;
    private int totalBlockedTicks;
    private int laneChanges;
    private int overtakes;
    private int hopTicks;
    private int hopCooldownTicks;
    private double hopTakeoffProgress;
    private int accelerationPenaltyTicks;
    private int interferenceTicks;
    private int activeSurgeTicks;
    private double activeSurgeMultiplier = 1.0;
    private int activeAccelerationTicks;
    private double activeAccelerationMultiplier = 1.0;
    private int activeLaneBoostTicks;
    private double activeLaneBoostMultiplier = 1.0;
    private int interferenceImmunityTicks;
    private int laneDecisionCooldown;
    private boolean cornerLineCommitted;
    private int cornerExitTicks;
    private boolean passiveBoosted;
    private boolean passiveSlowed;
    private int startDelayTicks;
    private int initialStartDelayTicks;
    private boolean wasCornering;
    private boolean activeUsed;
    private TurtleSurface previousSurface;
    private long finishTick = -1;
    private double finishFraction;
    private int finishRank;
    private boolean companionRecoveryUsed;
    private boolean companionCongestionUsed;
    private boolean companionSurfaceUsed;
    private boolean calmRecoveryUsed;
    private boolean fourSeasonShellUsed;
    private boolean goldenGapReady;
    private boolean goldenGapUsed;
    private boolean breathing;
    private int breathingTicks;
    private int breathingReturnTicks;
    private int breathingCount;
    private double breathingRecovery;
    private int breathingExitBoostTicks;
    private int wakePressureTicks;
    private int overdrivePenaltyDelayTicks;
    private int overdrivePenaltyTicks;
    private int surfaceTransitions;
    private final EnumSet<TurtleSurface> seenSurfaces = EnumSet.noneOf(TurtleSurface.class);
    private TurtleCompanion validationCompanion;

    private RaceEntry(String entryId, UUID ownerId, UUID turtleId, String name, boolean ai, TurtleRarity rarity,
                      TurtleStats stats, ActiveSkill activeSkill, List<String> passives, TurtleStrategy strategy,
                      TurtleData source, String appearance, String bodyAppearance, int rating, int lane) {
        this.entryId=entryId; this.ownerId=ownerId; this.turtleId=turtleId; this.name=name; this.ai=ai;
        this.rarity=rarity; this.stats=stats.copy(); this.activeSkill=activeSkill; this.passives=List.copyOf(passives);
        this.strategy=strategy; this.source=source; this.appearance=appearance; this.bodyAppearance=bodyAppearance; this.rating=rating; this.lane=lane; this.targetLane=lane;
    }

    public static RaceEntry player(TurtleData turtle, int lane) {
        return new RaceEntry("player:"+turtle.id(), turtle.ownerId(), turtle.id(), turtle.name(), false, turtle.rarity(),
                turtle.stats(), turtle.activeSkill(), turtle.passives().subList(0, turtle.activePassiveSlots()),
                turtle.strategy(), turtle, turtle.appearance(), turtle.bodyAppearance(), turtle.raceRating(), lane);
    }

    public static RaceEntry ai(AiTurtleProfile profile, TurtleLeague league, long seed, int lane) {
        int slots=profile.activePassiveCount(league);
        return new RaceEntry("ai:"+profile.id(), null, null, profile.id(), true, TurtleRarity.RARE,
                profile.statsFor(league, seed), profile.activeSkill(), profile.passives().subList(0, slots),
                profile.strategy(), null, profile.appearance(), AiTurtleCatalog.bodyAppearance(profile.id()), TurtleData.DEFAULT_RACE_RATING, lane);
    }

    public static RaceEntry aiTimeTrial(AiTurtleProfile profile,TurtleLeague league,int lane){int slots=profile.activePassiveCount(league);return new RaceEntry("ai:"+profile.id(),null,null,profile.id(),true,TurtleRarity.RARE,profile.statsForTimeTrial(league),profile.activeSkill(),profile.passives().subList(0,slots),profile.strategy(),null,profile.appearance(),AiTurtleCatalog.bodyAppearance(profile.id()),TurtleData.DEFAULT_RACE_RATING,lane);}
    public static RaceEntry aiTimeTrial(AiTurtleProfile profile,com.yoiko.core.turtle.TurtleRaceClass difficulty,int lane){return aiTimeTrial(profile,difficulty.timeTrialLeague(),lane);}

    /** Identical-loadout entry used only by the deterministic strategy balance validator. */
    public static RaceEntry validation(String id,TurtleStats stats,ActiveSkill activeSkill,List<String> passives,TurtleStrategy strategy,int lane){return new RaceEntry("validation:"+id,null,null,id,true,TurtleRarity.RARE,compactValidationStats(stats),activeSkill,passives,strategy,null,"natural","natural",TurtleData.DEFAULT_RACE_RATING,lane);}

    private static TurtleStats compactValidationStats(TurtleStats stats) {
        if (stats.total() <= 500) return stats;
        int[] values = stats.copyValues();
        return new TurtleStats((values[0]+5)/10,(values[1]+5)/10,(values[2]+5)/10,(values[3]+5)/10,(values[4]+5)/10);
    }

    public String entryId(){return entryId;} public UUID ownerId(){return ownerId;} public UUID turtleId(){return turtleId;}
    public String name(){return name;} public boolean ai(){return ai;} public TurtleRarity rarity(){return rarity;}
    public Component displayName(){return ai&&entryId.startsWith("ai:")?Component.translatable("yoiko_core.turtle.ai."+entryId.substring(3)+".name"):TurtleNameCatalog.component(name);}
    public TurtleStats stats(){return stats;} public ActiveSkill activeSkill(){return activeSkill;}
    public List<String> passives(){return passives;} public TurtleStrategy strategy(){return strategy;}
    public int rating(){return rating;}
    public TurtleCompanion companion(){return source==null?validationCompanion:source.companion();}
    public String appearance(){return appearance;}
    public String bodyAppearance(){return bodyAppearance;}
    public int lane(){return lane;} public int targetLane(){return targetLane;} public double progress(){return progress;}
    public double speed(){return speed;} public double stamina(){return stamina;} public boolean finished(){return finishTick>=0;}
    public double staminaSpent(){return staminaSpent;}
    public long finishTick(){return finishTick;} public double finishFraction(){return finishFraction;}
    public int finishRank(){return finishRank;}
    public boolean activeUsed(){return activeUsed;} public int blockedTicks(){return blockedTicks;}
    public int totalBlockedTicks(){return totalBlockedTicks;} public int laneChanges(){return laneChanges;} public int overtakes(){return overtakes;}
    public boolean interfered(){return interferenceTicks>0||wakePressureTicks>0;}
    public boolean hardInterfered(){return interferenceTicks>0;}
    public boolean activeEffectRunning(){return activeSurgeTicks>0||activeAccelerationTicks>0||activeLaneBoostTicks>0||interferenceImmunityTicks>0;}
    public int activeEffectTicksRemaining(){return Math.max(Math.max(activeSurgeTicks,activeAccelerationTicks),Math.max(activeLaneBoostTicks,interferenceImmunityTicks));}
    public boolean accelerationPenaltyActive(){return accelerationPenaltyTicks>0;}
    public boolean passiveBoosted(){return passiveBoosted||breathingExitBoostTicks>0;}
    public boolean passiveSlowed(){return passiveSlowed;}
    public double activeSurgeMultiplier(){return activeSurgeTicks>0?activeSurgeMultiplier:1.0;}
    public double activeAccelerationMultiplier(){return activeAccelerationTicks>0?activeAccelerationMultiplier:1.0;}
    public int laneDecisionCooldown(){return laneDecisionCooldown;}
    public boolean cornerLineCommitted(){return cornerLineCommitted;}
    public int startDelayTicks(){return startDelayTicks;}
    public int initialStartDelayTicks(){return initialStartDelayTicks;}
    public boolean wasCornering(){return wasCornering;}
    public boolean cornerExitActive(){return cornerExitTicks>0;}
    public boolean breathing(){return breathing;}
    public int breathingCount(){return breathingCount;}
    public double breathingRecovery(){return breathingRecovery;}
    public double breathingThrottle(){
        if(breathing){double t=Math.min(1,breathingTicks/30.0);return 1-.30*smooth(t);}
        if(breathingReturnTicks>0){double t=1-breathingReturnTicks/40.0;return .70+.30*smooth(t);}
        return 1;
    }
    public double breathingFatigueScale(){
        if(breathing)return 0;
        if(breathingReturnTicks>0)return smooth(1-breathingReturnTicks/40.0);
        return 1;
    }
    public boolean wakePressured(){return wakePressureTicks>0;}
    public boolean overdrivePenaltyActive(){return overdrivePenaltyTicks>0;}
    public double breathingExitAccelerationMultiplier(){return breathingExitBoostTicks>0?1.12:1.0;}
    public TurtleSurface previousSurface(){return previousSurface;}
    public int surfaceTransitions(){return surfaceTransitions;}
    public double laneOffset(){ double a=laneCenterOffset(lane),b=laneCenterOffset(targetLane),t=smooth(laneChangeProgress); return a+(b-a)*t; }
    public boolean changingLane(){return lane!=targetLane||laneChangeProgress<1;}
    public static double laneCenterOffset(int lane){return (lane-3.5)*LANE_SPACING;}
    public boolean hopping(){return hopTicks>0;}
    public boolean canHop(){return hopCooldownTicks<=0;}
    public double hopPhase(){return hopTicks<=0?1.0:1.0-hopTicks/(double)HOP_DURATION_TICKS;}
    /** Keeps collision/ranking at the authoritative landing position while rendering the full arc. */
    public double visualProgress(){if(hopTicks<=0)return progress;return hopTakeoffProgress+(progress-hopTakeoffProgress)*smooth(hopPhase());}
    public double hopYOffset(){return hopTicks<=0?0:Math.sin(Math.PI*hopPhase())*.48;}
    public boolean has(String passive){return passives.contains(passive);}
    public double n(TurtleStat stat){return Math.max(0,Math.min(1,stats.get(stat)/100.0));}
    public double surfaceAptitude(TurtleSurface surface){return source==null?1.0:source.surfaceAptitude(surface).multiplier();}
    public double strategyAptitude(){return source==null?1.0:source.strategyAptitude(strategy).multiplier();}
    public double distanceAptitude(double length){return source==null?1.0:source.distanceAptitude(length).multiplier();}

    void setTargetLane(int value){if(value!=targetLane){targetLane=value;laneChangeProgress=0;}}
    void prepareRace(long seed,int index){long mixed=mix(seed,entryId.hashCode()*31L+index);int variance=(int)Math.floorMod(mixed,7);startDelayTicks=Math.max(0,variance-(int)Math.round(n(TurtleStat.CALM)*3)-(has("starting_focus")?2:0)-(companion()==TurtleCompanion.RABBIT?2:0));initialStartDelayTicks=startDelayTicks;laneDecisionCooldown=0;}
    boolean waitForStart(){if(startDelayTicks<=0)return false;startDelayTicks--;return true;}
    void clearStartDelay(){startDelayTicks=0;}
    void startSurge(int ticks,double multiplier){activeSurgeTicks=Math.max(activeSurgeTicks,ticks);activeSurgeMultiplier=Math.max(activeSurgeMultiplier,multiplier);}
    void startAccelerationBoost(int ticks,double multiplier){activeAccelerationTicks=Math.max(activeAccelerationTicks,ticks);activeAccelerationMultiplier=Math.max(activeAccelerationMultiplier,multiplier);}
    void startLaneBoost(int ticks,double multiplier){activeLaneBoostTicks=Math.max(activeLaneBoostTicks,ticks);activeLaneBoostMultiplier=Math.max(activeLaneBoostMultiplier,multiplier);}
    void addSpeedImpulse(double value){speed=Math.max(0,speed+value);}
    void grantInterferenceImmunity(int ticks){interferenceImmunityTicks=Math.max(interferenceImmunityTicks,ticks);clearInterference();}
    void setLaneDecisionCooldown(int ticks){laneDecisionCooldown=Math.max(0,ticks);}
    void setCornerLineCommitted(boolean value){cornerLineCommitted=value;}
    void setWasCornering(boolean value){wasCornering=value;}
    void startCornerExit(int ticks){cornerExitTicks=Math.max(cornerExitTicks,ticks);}
    void setPassiveState(boolean boosted,boolean slowed){passiveBoosted=boosted;passiveSlowed=slowed;}
    void tickLane(double seconds){if(companion()==TurtleCompanion.ALLAY&&activeUsed)seconds*=.72;if(activeLaneBoostTicks>0)seconds*=activeLaneBoostMultiplier;if(laneChangeProgress<1){int previousLane=lane;laneChangeProgress=Math.min(1,laneChangeProgress+1.0/(seconds*20));if(laneChangeProgress>=1){lane=targetLane;if(lane!=previousLane)laneChanges++;}}}
    void addProgress(double value){progress+=value;}
    void coastAfterFinish(double limit){if(progress>=limit)return;speed=Math.max(.8,speed-.025);progress=Math.min(limit,progress+speed/20.0);}
    void setProgress(double value){progress=value;}
    void setSpeed(double value){speed=Math.max(0,value);}
    boolean canSpendStamina(double value){return stamina+1.0e-8>=Math.max(0,value);}
    void takeStamina(double value){double spent=Math.min(stamina,Math.max(0,value));stamina-=spent;staminaSpent+=spent;}
    void recoverStamina(double value){stamina=Math.min(1,stamina+value);}
    double breathingRecoveryLimit(){return has("breathing_switch")?.10:.07;}
    double recoverBreathing(double value){double allowed=Math.min(Math.max(0,breathingRecoveryLimit()-breathingRecovery),Math.max(0,value));double before=stamina;stamina=Math.min(1,stamina+allowed);double recovered=stamina-before;breathingRecovery+=recovered;return recovered;}
    boolean canStartBreathing(){return !breathing&&breathingCount==0;}
    void startBreathing(){if(!canStartBreathing())return;breathing=true;breathingTicks=0;breathingCount=1;}
    void stopBreathing(){if(!breathing)return;breathing=false;breathingReturnTicks=40;if(has("breathing_switch"))breathingExitBoostTicks=40;}
    void setBlocked(boolean value){blockedTicks=value?blockedTicks+1:0;if(value)totalBlockedTicks++;}
    void recordOvertakes(int value){overtakes+=Math.max(0,value);}
    void startHop(){hopTakeoffProgress=progress;hopTicks=HOP_DURATION_TICKS;hopCooldownTicks=HOP_COOLDOWN_TICKS;accelerationPenaltyTicks=12;blockedTicks=0;}
    void startHopTo(double landingProgress){hopTakeoffProgress=progress;progress=Math.max(progress,landingProgress);hopTicks=HOP_DURATION_TICKS;hopCooldownTicks=HOP_COOLDOWN_TICKS;accelerationPenaltyTicks=12;blockedTicks=0;}
    void tickTransient(){if(hopTicks>0)hopTicks--;if(hopCooldownTicks>0)hopCooldownTicks--;if(accelerationPenaltyTicks>0)accelerationPenaltyTicks--;if(interferenceTicks>0)interferenceTicks--;if(wakePressureTicks>0)wakePressureTicks--;if(interferenceImmunityTicks>0)interferenceImmunityTicks--;if(activeSurgeTicks>0){activeSurgeTicks--;if(activeSurgeTicks==0)activeSurgeMultiplier=1;}if(activeAccelerationTicks>0){activeAccelerationTicks--;if(activeAccelerationTicks==0)activeAccelerationMultiplier=1;}if(activeLaneBoostTicks>0){activeLaneBoostTicks--;if(activeLaneBoostTicks==0)activeLaneBoostMultiplier=1;}if(laneDecisionCooldown>0)laneDecisionCooldown--;if(cornerExitTicks>0)cornerExitTicks--;if(breathing)breathingTicks++;else if(breathingReturnTicks>0)breathingReturnTicks--;if(breathingExitBoostTicks>0)breathingExitBoostTicks--;if(overdrivePenaltyDelayTicks>0){overdrivePenaltyDelayTicks--;if(overdrivePenaltyDelayTicks==0)overdrivePenaltyTicks=Math.max(overdrivePenaltyTicks,30);}else if(overdrivePenaltyTicks>0)overdrivePenaltyTicks--;}
    double accelerationPenalty(){return accelerationPenaltyTicks>0?(has("hop_landing")?.96:companion()==TurtleCompanion.FROG?.92:companion()==TurtleCompanion.GOAT?.97:.80):1.0;}
    void useActive(){activeUsed=true;}
    void interfere(int ticks,boolean forestMist){if(interferenceImmunityTicks>0)return;double reduction=1-.25*n(TurtleStat.CALM);if(companion()==TurtleCompanion.WOLF)reduction*=forestMist?.55:.65;if(has("hard_shell"))reduction*=.45;if(has("shell_reflection"))reduction*=.62;if(has("quiet_champion"))reduction*=.55;interferenceTicks=Math.max(interferenceTicks,(int)Math.ceil(ticks*reduction));}
    void applyWakePressure(int ticks){if(interferenceImmunityTicks>0)return;double reduction=1-.35*n(TurtleStat.CALM);if(companion()==TurtleCompanion.WOLF)reduction*=.65;if(has("hard_shell"))reduction*=.50;if(has("shell_reflection"))reduction*=.65;wakePressureTicks=Math.max(wakePressureTicks,(int)Math.ceil(ticks*reduction));}
    void clearInterference(){interferenceTicks=0;wakePressureTicks=0;}
    void scheduleOverdrivePenalty(int delayTicks){overdrivePenaltyDelayTicks=Math.max(overdrivePenaltyDelayTicks,delayTicks);}
    void validationInterfere(int ticks){interferenceTicks=Math.max(interferenceTicks,ticks);}
    void setPreviousSurface(TurtleSurface value){if(previousSurface!=null&&previousSurface!=value)surfaceTransitions++;seenSurfaces.add(value);previousSurface=value;}
    void finish(long tick,double fraction,int rank){finishTick=tick;finishFraction=fraction;finishRank=rank;}
    boolean useCompanionRecovery(){if(companionRecoveryUsed)return false;companionRecoveryUsed=true;return true;}
    boolean useCompanionCongestion(){if(companionCongestionUsed)return false;companionCongestionUsed=true;return true;}
    boolean companionCongestionTriggered(){return companionCongestionUsed;}
    boolean useCompanionSurface(){if(companionSurfaceUsed)return false;companionSurfaceUsed=true;return true;}
    boolean useCalmRecovery(){if(calmRecoveryUsed)return false;calmRecoveryUsed=true;return true;}
    boolean useFourSeasonShell(){if(fourSeasonShellUsed||seenSurfaces.size()<2)return false;fourSeasonShellUsed=true;return true;}
    void markGoldenGapFailure(){if(has("golden_gap")&&!goldenGapUsed)goldenGapReady=true;}
    boolean useGoldenGap(){if(!goldenGapReady||goldenGapUsed)return false;goldenGapReady=false;goldenGapUsed=true;return true;}
    public void setValidationCompanion(TurtleCompanion value){validationCompanion=value;}
    public RaceEntry replayCopy(){RaceEntry copy=new RaceEntry(entryId,ownerId,turtleId,name,ai,rarity,stats,activeSkill,passives,strategy,source,appearance,bodyAppearance,rating,lane);copy.validationCompanion=validationCompanion;return copy;}
    private static double smooth(double t){return t*t*(3-2*t);}
    private static long mix(long a,long b){long z=a^Long.rotateLeft(b,21)^0x9E3779B97F4A7C15L;z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);}
}
