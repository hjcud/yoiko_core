package com.yoiko.core.turtle.race;

import com.yoiko.core.turtle.ActiveSkill;
import com.yoiko.core.turtle.TurtleCompanion;
import com.yoiko.core.turtle.TurtleStat;
import com.yoiko.core.turtle.TurtleStrategy;
import com.yoiko.core.turtle.TurtleSurface;
import com.yoiko.core.turtle.TurtleWeather;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RaceSimulation {
    private static final double SAME_LANE_TOLERANCE = .24;
    /** One full course at ordinary race pace spends most, but not all, of the normalized gauge. */
    private static final double STAMINA_DISTANCE_COST = 1.12;
    /** Stamina remains valuable without making a stamina-heavy build finish with half a gauge. */
    private static final double STAMINA_STAT_RELIEF = .36;
    private static final double FATIGUE_RESERVE = .16;
    private static final double BREATH_RECOVERY_PER_TICK = .028/20.0;
    private record Occupancy(RaceEntry entry,double progress,double speed,double offset){}
    private record CornerTarget(TurtleCourse.Sample sample,double distanceAhead){}
    private final TurtleCourse course;
    private final TurtleWeather weather;
    private final long seed;
    private final List<RaceEntry> entries;
    private final List<RaceEvent> events = new ArrayList<>();
    private final List<RaceEntry> entriesView;
    private final List<RaceEvent> eventsView;
    private List<RaceEntry> cachedStandings = List.of();
    private long cachedStandingsTick = Long.MIN_VALUE;
    private long tick;
    private boolean complete;
    private String leaderId;
    private long lastLeaderChangeTick = -100;
    private long allFinishedTick = -1;
    private int finishCount;
    private int hopCount;

    public RaceSimulation(TurtleCourse course, TurtleWeather weather, long seed, List<RaceEntry> entries) {
        if(entries.size()!=8)throw new IllegalArgumentException("A turtle race requires exactly eight entries");
        this.course=course;this.weather=weather;this.seed=seed;this.entries=new ArrayList<>(entries);
        this.entriesView=Collections.unmodifiableList(this.entries);
        this.eventsView=Collections.unmodifiableList(this.events);
        for(int i=0;i<this.entries.size();i++)this.entries.get(i).prepareRace(seed,i);
    }

    public void tick() {
        if(complete)return;
        tick++;
        List<RaceEntry> beforeOrder=standings();
        List<Occupancy> snapshot=entries.stream().map(e->new Occupancy(e,e.progress(),e.speed(),e.laneOffset())).toList();
        Set<String> reservedLandings=new HashSet<>();
        Set<String> reservedLanes=new HashSet<>();
        for(RaceEntry entry:entries.stream().sorted(Comparator.comparing(RaceEntry::entryId)).toList()) {
            if(entry.finished()){entry.coastAfterFinish(course.length()+4.0+entry.finishRank()*.85);continue;}
            TurtleCourse.Sample sample=course.sampleAt(entry.progress());
            double decisionDistance=Math.min(30.0,20.0+entry.speed()*1.10+entry.n(TurtleStat.NAVIGATION)*5.0);
            CornerTarget corner=nextCorner(entry.progress(),decisionDistance);
            // Commit only while physically turning. On the approach, an outside turtle may make
            // several safe one-lane moves instead of being limited to one move for the whole corner.
            // A turtle that has not reached an inner line may keep merging through the broad arc.
            // This is a tactical lane choice, not forced spreading on a straight.
            if(sample.curvature()>.12)entry.setCornerLineCommitted(true);
            else if(sample.curvature()<.08)entry.setCornerLineCommitted(false);
            chooseRacingLine(entry,corner,snapshot,reservedLanes);
            Occupancy blocker=findBlocker(entry,snapshot);
            entry.setBlocked(blocker!=null);
            if(blocker!=null) chooseTrafficMove(entry,blocker,corner,snapshot,reservedLandings);
            applyActive(entry,sample,blocker,corner,snapshot);
            if(entry.waitForStart()){entry.setSpeed(Math.max(0,entry.speed()-.20));entry.tickTransient();continue;}
            updateBreathing(entry,snapshot);
            double speedN=entry.n(TurtleStat.SPEED), staminaN=entry.n(TurtleStat.STAMINA);
            double powerN=entry.n(TurtleStat.POWER),calmN=entry.n(TurtleStat.CALM),navN=entry.n(TurtleStat.NAVIGATION);
            if(entry.has("corner_exit")&&entry.wasCornering()&&sample.curvature()<.08)entry.startCornerExit(30);
            // Native low-speed model. These are the authoritative physical values, not a visual
            // multiplier applied after calculating the old high-speed model.
            // Composure contributes to sustainable pace even in a clean race. The lower constant
            // keeps an average build at the previous speed while preventing CALM from becoming a
            // dead innate stat whenever no interference skill is present.
            double maxSpeed=2.396+.35*speedN+.24*powerN+.21*calmN+.13*navN
                    +.10*navN*navN-.02*speedN*speedN;
            double passiveAcceleration=passiveAcceleration(entry,sample),passiveSpeed=passiveSpeed(entry,sample);
            entry.setPassiveState(passiveAcceleration>1.001||passiveSpeed>1.001,passiveAcceleration<.999||passiveSpeed<.999);
            double acceleration=(1.51+1.85*powerN)*entry.accelerationPenalty()*passiveAcceleration
                    *entry.activeAccelerationMultiplier()*entry.breathingExitAccelerationMultiplier();
            double rabbitAcceleration=entry.companion()==TurtleCompanion.RABBIT&&tick<=50?(weather==TurtleWeather.CLEAR?1.080:1.060):1.0;
            acceleration*=rabbitAcceleration;
            double deceleration=2.02+1.01*calmN;
            double aptitude=1+(entry.distanceAptitude(course.length())-1)*.40
                    +(entry.surfaceAptitude(sample.surface())-1)*.35;
            maxSpeed*=aptitude*surfaceMultiplier(entry,sample.surface());
            maxSpeed*=weatherSpeed(sample.surface());
            maxSpeed*=passiveSpeed;
            double breathingReferenceSpeed=maxSpeed;
            maxSpeed*=entry.activeSurgeMultiplier();
            double companionTechnique=entry.companion()==TurtleCompanion.BEE
                    ?(entry.changingLane()?(weather==TurtleWeather.CLEAR?1.028:1.024):1.012)
                    :entry.companion()==TurtleCompanion.ALLAY&&entry.activeEffectRunning()?1.035
                    :entry.companion()==TurtleCompanion.FROG?(entry.blockedTicks()>0?1.025:1.008)
                    :entry.companion()==TurtleCompanion.WOLF&&entry.interfered()?1.018:1.0;
            if(entry.companion()==TurtleCompanion.PARROT&&entry.progress()/course.length()>=.70)
                companionTechnique*=weather==TurtleWeather.SEA_BREEZE?1.052:1.044;
            if(entry.companion()==TurtleCompanion.FOX&&entry.progress()/course.length()>=.75)
                companionTechnique*=weather==TurtleWeather.FOREST_MIST?1.026:1.018;
            maxSpeed*=companionTechnique;
            maxSpeed*=paceVariation(entry);
            if(entry.hardInterfered())maxSpeed*=entry.companion()==TurtleCompanion.WOLF?1.0:entry.has("hard_shell")?.985:.94;
            if(entry.wakePressured())maxSpeed*=entry.companion()==TurtleCompanion.WOLF?.98:entry.has("hard_shell")?.98:.94;
            if(entry.overdrivePenaltyActive())maxSpeed*=.97;
            double exhaustion=Math.max(0,Math.min(1,(FATIGUE_RESERVE-entry.stamina())/FATIGUE_RESERVE));
            double fatigue=smooth(exhaustion);
            // Catching breath is already a paid 70% pace state. Fatigue must not multiply into it;
            // outside that one recovery window, zero stamina is a decisive 30%/40% speed/accel loss.
            // Limit Sprint may spend the last of the gauge during its burst. Exhaustion begins as
            // soon as the active ends, preserving the double-edged risk without cancelling the
            // once-per-race active halfway through its own animation.
            double fatigueScale=entry.activeSkill()==ActiveSkill.LIMIT_SPRINT&&entry.activeEffectRunning()
                    ?0:entry.breathingFatigueScale();
            maxSpeed*=1-.30*fatigue*fatigueScale;
            acceleration*=1-.40*fatigue*fatigueScale;
            double rawCorner=sample.curvature()*(.14-.09*navN-.035*calmN);
            double cornerReduction=entry.has("corner_expert")?.35:0;
            if(entry.has("good_sense_of_direction"))cornerReduction+=.20;
            if(entry.activeSkill()==ActiveSkill.CORAL_CORNER&&entry.activeEffectRunning())cornerReduction+=.70;
            maxSpeed*=1-Math.max(0,rawCorner)*(1-Math.min(.60,cornerReduction));
            // Strategy changes throttle/position decisions, never the turtle's raw capability.
            // Every strategy receives the same max-speed, acceleration and stamina formulas.
            maxSpeed*=strategyThrottle(entry,snapshot);
            if(blocker!=null && entry.targetLane()==entry.lane()){
                if(entry.companion()==TurtleCompanion.ARMADILLO&&entry.useCompanionCongestion()){maxSpeed=Math.min(maxSpeed,Math.max(.1,blocker.speed+(maxSpeed-blocker.speed)*.75));entry.recoverStamina(.03);}
                else maxSpeed=Math.min(maxSpeed,Math.max(.1,blocker.speed));
            }
            double delta=maxSpeed-entry.speed();
            double next=entry.speed()+Math.max(-deceleration/20,Math.min(acceleration/20,delta));
            double oldProgress=entry.progress();
            entry.setSpeed(next);
            // Speed is physical lane speed. Inner arcs need less distance per metre of centre-line
            // progress, while an outer arc must actually cover its longer radius.
            entry.addProgress((next/20.0)/course.distanceScale(entry.progress(),entry.laneOffset()));
            // Distance, actual speed and the stamina stat are the only authoritative base inputs.
            // The flatter stat relief prevents a stamina-heavy build from carrying 40~60% unused
            // reserve, while the exponential speed curve still makes a fast finish expensive.
            double baseCost=(STAMINA_DISTANCE_COST-STAMINA_STAT_RELIEF*staminaN)/course.length();
            double activeMultiplier=entry.activeSurgeMultiplier();
            // An active is efficient enough to remain decisive, but its extra physical speed is no
            // longer treated as free stamina. Most, rather than all, of its surge is skill efficiency.
            double exertionSpeed=next/Math.pow(activeMultiplier,.80);
            double speedExertion=staminaExertion(exertionSpeed);
            boolean drafting=drafting(entry,snapshot);
            double slipstream=drafting?(entry.has("draft_posture")?.885:.925):1.0;
            // A performance passive represents better technique, not a trap that merely forces the
            // turtle into the exponential stamina curve. Preserve normal speed-based exertion, then
            // credit only the incremental efficiency supplied by that passive.
            double passiveEfficiency=Math.pow(passiveSpeed,2.5)*Math.pow(passiveAcceleration,.7);
            if((entry.has("corner_expert")||entry.has("corner_cushion"))&&sample.curvature()>.05)
                passiveEfficiency*=1.05;
            // Even Cadence deliberately trades burst potential for repeatability.  Give the
            // smoother gait a small, unconditional efficiency return so that its value does not
            // depend on a lucky sixteen-race validation seed.
            if(entry.has("even_cadence"))passiveEfficiency*=1.05;
            double companionCost=(entry.companion()==TurtleCompanion.WOLF&&entry.interfered()?.55:1.0)
                    /Math.pow(rabbitAcceleration,.7)/Math.pow(companionTechnique,2.5);
            if(entry.companion()==TurtleCompanion.PARROT&&entry.progress()/course.length()>=.70)companionCost*=.97;
            entry.takeStamina((next/20.0)*baseCost*speedExertion*surfaceCost(entry,sample.surface())*slipstream
                    *passiveCost(entry)*strategyStaminaCost(entry)*companionCost/passiveEfficiency);
            if(entry.breathing()&&next<=breathingReferenceSpeed*.72){
                double recovery=BREATH_RECOVERY_PER_TICK*(.90+.20*staminaN+.10*calmN)*(entry.has("breathing_switch")?1.20:1.0);
                entry.recoverBreathing(recovery);
            }
            if(entry.companion()==TurtleCompanion.AXOLOTL&&entry.stamina()<.40&&entry.useCompanionRecovery())entry.recoverStamina(weather==TurtleWeather.LIGHT_RAIN?.030:.025);
            if(entry.companion()==TurtleCompanion.BLUE_AXOLOTL&&entry.companionCongestionTriggered()
                    &&entry.stamina()<.45&&entry.useCompanionRecovery())entry.recoverStamina(.06);
            if(entry.has("calm_recovery")&&entry.stamina()<.35&&entry.useCalmRecovery())entry.recoverStamina(.05);
            double laneSeconds=Math.max(.40,Math.min(.80,.80-.25*navN-.15*powerN));
            if(entry.has("good_sense_of_direction"))laneSeconds*=.92;
            if(entry.has("gap_finder"))laneSeconds*=.90;
            if(entry.has("lane_cadence"))laneSeconds*=.88;
            if(entry.has("passing_spring")&&(entry.blockedTicks()>0||entry.changingLane()))laneSeconds*=.80;
            if(entry.has("rainwash")&&weather==TurtleWeather.LIGHT_RAIN&&entry.previousSurface()!=sample.surface())laneSeconds*=.84;
            if(entry.companion()==TurtleCompanion.FOX&&entry.progress()/course.length()>=.75)laneSeconds*=weather==TurtleWeather.FOREST_MIST?.52:.65;
            if(entry.companion()==TurtleCompanion.BEE)laneSeconds*=weather==TurtleWeather.CLEAR?.89:.91;
            if(entry.companion()==TurtleCompanion.FROG&&entry.blockedTicks()>0)laneSeconds*=.82;
            if(entry.wakePressured())laneSeconds*=1.35;
            if(entry.has("passing_vision")&&entry.strategy()==TurtleStrategy.FOLLOW&&rankOf(entry)>=3&&rankOf(entry)<=6)laneSeconds*=.78;
            if(entry.has("final_route")&&entry.strategy()==TurtleStrategy.CLOSER&&entry.progress()/course.length()>=.70&&rankOf(entry)>=4)laneSeconds*=.70;
            if(entry.has("reclaim_sense")&&entry.strategy()==TurtleStrategy.FRONT&&rankOf(entry)>=2&&rankOf(entry)<=4)laneSeconds*=.76;
            if(entry.has("outer_lane_flow")&&entry.blockedTicks()>0)laneSeconds*=.82;
            if(entry.has("wake_follower")&&entry.strategy()==TurtleStrategy.FOLLOW&&drafting)laneSeconds*=.82;
            entry.tickLane(laneSeconds);
            entry.tickTransient();
            entry.setWasCornering(sample.curvature()>.15);
            entry.setPreviousSurface(sample.surface());
            if(entry.progress()>=course.length()) {
                double fraction=(course.length()-oldProgress)/Math.max(1.0e-8,entry.progress()-oldProgress);
                entry.setProgress(course.length());entry.finish(tick,fraction,++finishCount);
                events.add(new RaceEvent(tick,"FINISH",entry.entryId(),null,"fraction="+fraction));
            }
        }
        cachedStandingsTick=Long.MIN_VALUE;
        List<RaceEntry> afterOrder=standings();
        for(RaceEntry entry:entries){int before=beforeOrder.indexOf(entry),after=afterOrder.indexOf(entry);if(before>=0&&after>=0&&after<before)entry.recordOvertakes(before-after);}
        RaceEntry currentLeader=entries.stream().noneMatch(RaceEntry::finished)?entries.stream().max(Comparator.comparingDouble(RaceEntry::progress)).orElse(null):null;
        if(currentLeader!=null){if(leaderId!=null&&!leaderId.equals(currentLeader.entryId())&&tick-lastLeaderChangeTick>=30){events.add(new RaceEvent(tick,"LEAD_CHANGE",currentLeader.entryId(),leaderId,"progress="+currentLeader.progress()));lastLeaderChangeTick=tick;}leaderId=currentLeader.entryId();}
        if(entries.stream().allMatch(RaceEntry::finished)&&allFinishedTick<0)allFinishedTick=tick;
        complete=allFinishedTick>=0&&tick-allFinishedTick>=40||tick>=3_000;
        if(tick>=3_000)events.add(new RaceEvent(tick,"TIME_LIMIT",null,null,"150 seconds"));
    }

    private CornerTarget nextCorner(double progress,double horizon){
        TurtleCourse.Sample best=null;double bestCurvature=.18,bestDistance=0;
        for(double ahead=.75;ahead<=horizon;ahead+=.75){
            TurtleCourse.Sample candidate=course.sampleAt(Math.min(course.length(),progress+ahead));
            if(candidate.curvature()>bestCurvature){best=candidate;bestCurvature=candidate.curvature();bestDistance=ahead;}
            if(best!=null&&ahead-bestDistance>=2.25)break;
        }
        return best==null?null:new CornerTarget(best,bestDistance);
    }

    private void chooseRacingLine(RaceEntry entry,CornerTarget target,List<Occupancy> snapshot,Set<String> reserved){
        if(entry.targetLane()!=entry.lane()||entry.laneDecisionCooldown()>0||entry.blockedTicks()>0||entry.cornerLineCommitted())return;
        if(target==null)return;TurtleCourse.Sample corner=target.sample();
        double progressRatio=entry.progress()/course.length(),navigation=entry.n(TurtleStat.NAVIGATION),calm=entry.n(TurtleStat.CALM);
        int idealLane=insideLane(corner),lanesToIdeal=Math.abs(idealLane-entry.lane());
        // Time the merge to finish shortly before the bend. Far-outside turtles begin earlier, while
        // turtles already near the inside keep running straight instead of making a 45-degree start.
        double mergeWindow=4.5+lanesToIdeal*(1.65+(1-navigation)*.55)+entry.speed()*.65;
        if(target.distanceAhead()>mergeWindow){entry.setLaneDecisionCooldown(4);return;}
        double strategyAggression=switch(entry.strategy()){case FRONT->1.25;case STEADY->1.00;case FOLLOW->1.00;case CLOSER->progressRatio<.50?.55:1.60;};
        double riskWeight=switch(entry.strategy()){case FRONT->.65;case STEADY->1.08;case FOLLOW->1.00;case CLOSER->progressRatio<.50?1.40:.62;};
        int best=entry.lane();double bestScore=lineScore(entry,best,corner,snapshot,strategyAggression,riskWeight,navigation,calm);
        // Two-lane planning is now a compact smooth lateral move (~1.10 blocks), letting the outside
        // rows reach a useful line before the first bend instead of spending the whole bend merging.
        for(int candidate=Math.max(0,entry.lane()-2);candidate<=Math.min(7,entry.lane()+2);candidate++){
            double score=lineScore(entry,candidate,corner,snapshot,strategyAggression,riskWeight,navigation,calm);
            if(score>bestScore+.015){bestScore=score;best=candidate;}
        }
        if(best==entry.lane()){
            entry.setLaneDecisionCooldown(7+(int)Math.round((1-navigation)*6));
            if(target.distanceAhead()<2.5)entry.setCornerLineCommitted(true);
            return;
        }
        double offset=RaceEntry.laneCenterOffset(best);int arrivalBucket=(int)Math.floor((entry.progress()+Math.max(4,entry.speed()*2.2))*2);String reservation=best+":"+arrivalBucket;
        boolean reservedByOther=reserved.contains(reservation);
        boolean free=!reservedByOther&&snapshot.stream().noneMatch(o->o.entry!=entry&&Math.abs(o.offset-offset)<=SAME_LANE_TOLERANCE&&o.progress>entry.progress()-.35&&o.progress-entry.progress()<1.15);
        boolean inward=course.distanceScale(corner.distance(),offset)+.01<course.distanceScale(corner.distance(),entry.laneOffset());
        // Baby turtles are 0.36 blocks wide while lanes are 0.55 blocks apart. They can cross
        // beside a neighbour, then tuck behind it instead of being locked to the starting lane.
        boolean zipperMerge=!reservedByOther&&inward&&snapshot.stream().anyMatch(o->o.entry!=entry&&Math.abs(o.offset-offset)<=SAME_LANE_TOLERANCE&&Math.abs(o.progress-entry.progress())<.55);
        if(free||zipperMerge){reserved.add(reservation);entry.setTargetLane(best);if(entry.useGoldenGap()){entry.setSpeed(entry.speed()+.28);entry.startLaneBoost(70,.35);entry.startAccelerationBoost(70,1.22);entry.startSurge(70,1.12);events.add(new RaceEvent(tick,"PASSIVE",entry.entryId(),null,"golden_gap"));}entry.setLaneDecisionCooldown(2+(int)Math.round((1-navigation)*4));events.add(new RaceEvent(tick,"RACING_LINE",entry.entryId(),null,"lane="+best+",strategy="+entry.strategy().name().toLowerCase()+",merge="+(zipperMerge?"zipper":"clear")));}
        else {entry.markGoldenGapFailure();entry.setLaneDecisionCooldown(4+(int)Math.round((1-calm)*5));}
    }

    private double lineScore(RaceEntry entry,int lane,TurtleCourse.Sample corner,List<Occupancy> snapshot,double aggression,double riskWeight,double navigation,double calm){
        double offset=RaceEntry.laneCenterOffset(lane),scale=course.distanceScale(corner.distance(),offset);double distanceValue=(1.18-scale)*7.0*aggression;
        long nearby=snapshot.stream().filter(o->o.entry!=entry&&Math.abs(o.offset-offset)<.42&&o.progress>entry.progress()-.25&&o.progress-entry.progress()<1.6).count();
        double congestion=nearby*(.30+.28*(1-navigation)+.18*(1-calm))*riskWeight*(entry.has("gap_finder")?.88:1);
        if(entry.has("quiet_champion")&&entry.progress()/course.length()>=.70&&rankOf(entry)>=2)congestion*=.40;
        double changeCost=Math.abs(lane-entry.lane())*(.12-.05*navigation)*(entry.has("gap_finder")?.82:1);
        double drafting=entry.strategy()==TurtleStrategy.FOLLOW&&snapshot.stream().anyMatch(o->o.entry!=entry&&Math.abs(o.offset-offset)<.55&&o.progress>entry.progress()+.5&&o.progress-entry.progress()<2.1)?.16:0;
        return distanceValue-congestion-changeCost+drafting;
    }

    private int insideLane(TurtleCourse.Sample corner){int best=0;double scale=Double.MAX_VALUE;for(int lane=0;lane<8;lane++){double candidate=course.distanceScale(corner.distance(),RaceEntry.laneCenterOffset(lane));if(candidate<scale){scale=candidate;best=lane;}}return best;}

    private Occupancy findBlocker(RaceEntry entry,List<Occupancy> snapshot){
        double gap=.42+.18*(1-entry.n(TurtleStat.CALM));
        return snapshot.stream().filter(o->o.entry!=entry&&o.progress>entry.progress()&&o.progress-entry.progress()<=gap
                        &&Math.abs(o.offset-entry.laneOffset())<=SAME_LANE_TOLERANCE&&o.speed<entry.speed()+.05)
                .min(Comparator.comparingDouble(o->o.progress)).orElse(null);
    }

    private void chooseTrafficMove(RaceEntry entry,Occupancy blocker,CornerTarget corner,List<Occupancy> snapshot,Set<String> reserved){
        if(entry.targetLane()!=entry.lane())return;
        int decisionTicks=4+(int)Math.round((1-entry.n(TurtleStat.CALM))*3);
        if(entry.blockedTicks()<decisionTicks)return;
        boolean aggressive=entry.strategy()==TurtleStrategy.FRONT
                ||entry.strategy()==TurtleStrategy.CLOSER&&entry.progress()/course.length()>=.50;
        double earlyHopRequirement=entry.has("hop_landing")?.54:entry.has("strong_flippers")?.58:.62;
        if(aggressive&&entry.n(TurtleStat.POWER)>=earlyHopRequirement&&tryHop(entry,blocker,snapshot,reserved))return;
        int inward=corner==null?0:Integer.signum(insideLane(corner.sample())-entry.lane());
        int preferred=inward!=0?inward:entry.has("outer_lane_flow")?(entry.lane()<4?-1:1):entry.strategy()==TurtleStrategy.FRONT?1:-1;
        int[] directions={preferred,-preferred};
        for(int direction:directions){int lane=entry.lane()+direction;if(lane<0||lane>7)continue;
            double offset=RaceEntry.laneCenterOffset(lane);
            boolean free=snapshot.stream().noneMatch(o->o.entry!=entry&&Math.abs(o.offset-offset)<=SAME_LANE_TOLERANCE&&Math.abs(o.progress-entry.progress())<.75);
            if(free){entry.setTargetLane(lane);events.add(new RaceEvent(tick,"LANE_CHANGE",entry.entryId(),null,"lane="+lane));return;}}
        entry.markGoldenGapFailure();
        int retryTicks=entry.companion()==TurtleCompanion.FROG?(weather==TurtleWeather.LIGHT_RAIN?17:19):24;
        if(entry.blockedTicks()<retryTicks)return;
        double requirement=entry.has("hop_landing")?.43:entry.has("strong_flippers")?.46:.48;
        if(entry.companion()==TurtleCompanion.GOAT)requirement*=.90;
        if(entry.n(TurtleStat.POWER)<requirement)return;
        tryHop(entry,blocker,snapshot,reserved);
    }

    private boolean tryHop(RaceEntry entry,Occupancy blocker,List<Occupancy> snapshot,Set<String> reserved){
        if(!entry.canHop())return false;
        double landingGap=Math.max(.75,.42+.18*(1-blocker.entry.n(TurtleStat.CALM))+.15);
        double landing=blocker.progress+landingGap;
        String key=entry.lane()+":"+(int)Math.floor(landing*4);
        boolean occupied=snapshot.stream().anyMatch(o->Math.abs(o.offset-entry.laneOffset())<=SAME_LANE_TOLERANCE&&Math.abs(o.progress-landing)<.50);
        if(!occupied&&reserved.add(key)){entry.takeStamina(entry.companion()==TurtleCompanion.GOAT?0:.008);entry.startHopTo(landing);if(entry.companion()==TurtleCompanion.GOAT){entry.setSpeed(entry.speed()+.18);entry.recoverStamina(.015);entry.startAccelerationBoost(50,1.25);entry.startSurge(50,1.12);}hopCount++;events.add(new RaceEvent(tick,"HOP",entry.entryId(),blocker.entry.entryId(),"landing="+landing));return true;}
        if(entry.companion()==TurtleCompanion.BLUE_AXOLOTL&&entry.useCompanionCongestion()){
            // The rare blue axolotl stores energy after a failed landing.  It is released once
            // stamina runs low, avoiding the old forced surge that caused exhaustion and delayed
            // the next natural hop.
            events.add(new RaceEvent(tick,"COMPANION",entry.entryId(),blocker.entry.entryId(),"blue_axolotl"));
        }
        return false;
    }

    private void applyActive(RaceEntry e,TurtleCourse.Sample s,Occupancy blocker,CornerTarget corner,List<Occupancy> snapshot){
        if(e.activeUsed()||e.breathing())return; boolean trigger=false; double progress=e.progress()/course.length();double allowance=e.companion()==TurtleCompanion.PARROT?(weather==TurtleWeather.SEA_BREEZE?.06:.04):0;
        ActiveSkill skill=e.activeSkill();
        boolean recoverySkill=switch(skill){case SAND_SPRINT,PUDDLE_SURF,DEEP_BREATH,SURFACE_CHAIN->true;default->false;};
        if(e.stamina()<=1.0e-8&&!recoverySkill)return;
        trigger=switch(skill){
            case SHELLBREAK_START->tick<=40;
            case SAND_SPRINT->s.surface()==TurtleSurface.SAND&&e.previousSurface()!=TurtleSurface.SAND;
            case FLOW_RHYTHM->progress>=.20-allowance&&progress<=.55+allowance&&s.curvature()<.05;
            case MUD_BREAKER->s.surface()==TurtleSurface.MUD&&e.speed()<3.2;
            case CORAL_CORNER->s.curvature()>.20;
            case SHELL_HOP->blocker!=null&&e.blockedTicks()>=4;
            case UNTURNED_HEART->e.interfered();
            case FALSE_FOOTPRINTS->progress>=.30-allowance&&progress<=.70+allowance;
            case PUDDLE_SURF->s.surface()==TurtleSurface.PUDDLE&&e.previousSurface()!=TurtleSurface.PUDDLE;
            case DEEP_BREATH->progress>=.30-allowance&&e.stamina()<=.45;
            case THOUSAND_YEAR_STEP->progress>=.85-allowance&&rankOf(e)>=4&&e.canSpendStamina(.05);
            case HOMEWARD_WAVE->progress>=.78-allowance&&leaderGap(e)>=4;
            case SUNLIT_STRIDE->weather==TurtleWeather.CLEAR&&progress>=.20-allowance&&progress<=.45+allowance&&s.curvature()<.05;
            case TAILWIND_SAIL->progress>=.08-allowance&&s.curvature()<.05;
            case RAINSTEP->weather==TurtleWeather.LIGHT_RAIN&&progress>=.20-allowance&&progress<=.78+allowance;
            case BREAKAWAY_BOUND->progress>=.20-allowance&&progress<=.78+allowance&&(blocker!=null||rankOf(e)>=5);
            case LEAD_GUARD->progress>=.73-allowance&&rankOf(e)<=6;
            case RESERVE_RELEASE->progress>=.70-allowance&&e.stamina()>=.22;
            case WAKE_CUT->progress>=.25-allowance&&progress<=.78+allowance&&rankOf(e)>=3&&rankOf(e)<=6&&drafting(e,snapshot);
            case FINAL_GAP->progress>=.80-allowance&&rankOf(e)>=5&&(blocker!=null||leaderGap(e)>=2.0);
            case CORNER_CLAIM->corner!=null&&corner.distanceAhead()<=10.0;
            case SURFACE_CHAIN->e.surfaceTransitions()>=1&&e.previousSurface()!=null&&e.previousSurface()!=s.surface();
            case LIMIT_SPRINT->progress>=.72-allowance&&rankOf(e)>=4&&e.stamina()>=.32&&e.canSpendStamina(.08);
            case SURGING_SPRAY->progress>=.35-allowance&&progress<=.78+allowance&&rankOf(e)<=4&&trailingGap(e)<=5.0&&e.canSpendStamina(.04);
            case RECKLESS_PASS->progress>=.20-allowance&&progress<=.88+allowance&&blocker!=null&&e.stamina()>=.25;
        };
        if(!trigger)return;e.useActive();RaceEntry target=null;
        switch(skill){
            case SHELLBREAK_START->{e.clearStartDelay();e.addSpeedImpulse(.50);activeAcceleration(e,140,1.42);activeSurge(e,140,1.20);}
            case SAND_SPRINT->{e.recoverStamina(.025);e.addSpeedImpulse(.15);activeAcceleration(e,70,1.18);activeSurge(e,70,1.12);}
            case FLOW_RHYTHM->{e.addSpeedImpulse(.20);activeLane(e,80,.70);activeAcceleration(e,80,1.20);activeSurge(e,80,1.14);}
            case MUD_BREAKER->{activeAcceleration(e,70,1.18);activeSurge(e,70,1.09);}
            case CORAL_CORNER->{e.setLaneDecisionCooldown(0);e.addSpeedImpulse(.25);activeLane(e,70,.55);activeAcceleration(e,70,1.27);activeSurge(e,70,1.18);}
            case PUDDLE_SURF->{e.recoverStamina(weather==TurtleWeather.LIGHT_RAIN?.08:.07);e.addSpeedImpulse(.18);activeAcceleration(e,60,1.18);activeSurge(e,60,1.12);}
            case DEEP_BREATH->{e.recoverStamina(.08);e.addSpeedImpulse(.12);activeAcceleration(e,70,1.14);activeSurge(e,70,1.10);}
            case SHELL_HOP->{if(blocker!=null){e.startHopTo(blocker.progress+Math.max(.85,.42+.18*(1-blocker.entry.n(TurtleStat.CALM))+.25));hopCount++;}activeAcceleration(e,45,1.12);activeSurge(e,45,1.08);}
            case UNTURNED_HEART->{e.clearInterference();e.grantInterferenceImmunity(100);e.addSpeedImpulse(.12);activeSurge(e,70,1.05);}
            case FALSE_FOOTPRINTS->{target=entries.stream().filter(v->v!=e&&!v.finished()&&v.progress()>e.progress()&&v.progress()-e.progress()<=5.0).min(Comparator.comparingDouble(v->v.progress()-e.progress())).orElse(null);if(target!=null){target.interfere(70,weather==TurtleWeather.FOREST_MIST);if(target.has("shell_reflection")){e.interfere(18,weather==TurtleWeather.FOREST_MIST);events.add(new RaceEvent(tick,"PASSIVE",target.entryId(),e.entryId(),"shell_reflection"));}}e.addSpeedImpulse(.40);activeLane(e,60,.52);activeAcceleration(e,60,1.35);activeSurge(e,60,1.22);}
            case THOUSAND_YEAR_STEP->{double boost=.26+Math.min(.03,e.stamina()*.06);e.takeStamina(.05);e.addSpeedImpulse(.48);activeAcceleration(e,85,1.34);activeSurge(e,85,1+boost);}
            case HOMEWARD_WAVE->{double boost=.20+Math.min(.08,leaderGap(e)*.008);e.addSpeedImpulse(.35);activeAcceleration(e,85,1.25);activeSurge(e,85,1+boost);}
            case SUNLIT_STRIDE->{e.addSpeedImpulse(.28);activeAcceleration(e,75,1.22);activeSurge(e,75,1.16);}
            case TAILWIND_SAIL->{boolean wind=weather==TurtleWeather.SEA_BREEZE;e.addSpeedImpulse(.25);activeAcceleration(e,70,wind?1.26:1.22);activeSurge(e,70,wind?1.17:1.14);}
            case RAINSTEP->{e.addSpeedImpulse(.22);activeLane(e,70,.65);activeAcceleration(e,70,1.24);activeSurge(e,70,weather==TurtleWeather.LIGHT_RAIN?1.16:1.12);}
            case BREAKAWAY_BOUND->{chooseOpenAdjacentLane(e,corner,snapshot);e.addSpeedImpulse(.25);activeLane(e,70,.52);activeAcceleration(e,70,1.28);activeSurge(e,70,1.17);}
            case LEAD_GUARD->{e.addSpeedImpulse(.35);activeAcceleration(e,70,1.25);activeSurge(e,70,1.16);}
            case RESERVE_RELEASE->{e.takeStamina(.03);e.addSpeedImpulse(.22);activeAcceleration(e,70,1.28);activeSurge(e,70,1.20);}
            case WAKE_CUT->{chooseOpenAdjacentLane(e,corner,snapshot);e.addSpeedImpulse(.38);activeLane(e,70,.48);activeAcceleration(e,70,1.35);activeSurge(e,70,1.22);}
            case FINAL_GAP->{chooseOpenAdjacentLane(e,corner,snapshot);e.addSpeedImpulse(.55);activeLane(e,80,.40);activeAcceleration(e,80,1.50);activeSurge(e,80,1.32);}
            case CORNER_CLAIM->{if(corner!=null&&e.targetLane()==e.lane())e.setTargetLane(insideLane(corner.sample()));e.addSpeedImpulse(.18);activeLane(e,60,.55);activeAcceleration(e,60,1.22);activeSurge(e,60,1.14);}
            case SURFACE_CHAIN->{e.recoverStamina(.03);e.addSpeedImpulse(.18);activeAcceleration(e,60,1.20);activeSurge(e,60,1.13);}
            case LIMIT_SPRINT->{e.takeStamina(.08);e.addSpeedImpulse(.60);activeAcceleration(e,90,1.55);activeSurge(e,90,1.33);e.scheduleOverdrivePenalty(activeDuration(90)+20);}
            case SURGING_SPRAY->{List<RaceEntry> pressured=entries.stream().filter(v->v!=e&&!v.finished()&&v.progress()<e.progress()&&e.progress()-v.progress()<=5.0).sorted(Comparator.comparingDouble(v->e.progress()-v.progress())).limit(2).toList();for(RaceEntry victim:pressured){victim.applyWakePressure(70);if(victim.has("shell_reflection")){e.applyWakePressure(18);events.add(new RaceEvent(tick,"PASSIVE",victim.entryId(),e.entryId(),"shell_reflection"));}}target=pressured.isEmpty()?null:pressured.getFirst();e.addSpeedImpulse(.48);activeAcceleration(e,50,1.40);activeSurge(e,50,1.28);}
            case RECKLESS_PASS->{chooseOpenAdjacentLane(e,corner,snapshot);if(blocker!=null&&e.targetLane()==e.lane()&&e.canHop()){e.startHopTo(blocker.progress+1.0);hopCount++;}e.takeStamina(.07);e.addSpeedImpulse(.50);activeLane(e,80,.42);activeAcceleration(e,80,1.48);activeSurge(e,80,1.30);}
        }
        String targetId=target!=null?target.entryId():blocker==null?null:blocker.entry.entryId();events.add(new RaceEvent(tick,"ACTIVE",e.entryId(),targetId,skill.id()));
    }

    private static int activeDuration(int ticks){return (int)Math.ceil(ticks*1.12);}
    private static void activeSurge(RaceEntry entry,int ticks,double multiplier){entry.startSurge(activeDuration(ticks),1+(multiplier-1)*1.35);}
    private static void activeAcceleration(RaceEntry entry,int ticks,double multiplier){entry.startAccelerationBoost(activeDuration(ticks),1+(multiplier-1)*1.30);}
    private static void activeLane(RaceEntry entry,int ticks,double multiplier){entry.startLaneBoost(activeDuration(ticks),Math.max(.35,1-(1-multiplier)*1.25));}

    private void chooseOpenAdjacentLane(RaceEntry entry,CornerTarget corner,List<Occupancy> snapshot){
        int inward=corner==null?0:Integer.signum(insideLane(corner.sample())-entry.lane());
        int preferred=inward==0?(entry.lane()<4?1:-1):inward;
        for(int direction:new int[]{preferred,-preferred}){int lane=entry.lane()+direction;if(lane<0||lane>7)continue;double offset=RaceEntry.laneCenterOffset(lane);boolean free=snapshot.stream().noneMatch(o->o.entry!=entry&&Math.abs(o.offset-offset)<=SAME_LANE_TOLERANCE&&Math.abs(o.progress-entry.progress())<.75);if(free){entry.setTargetLane(lane);entry.setLaneDecisionCooldown(0);return;}}
    }

    private void updateBreathing(RaceEntry e,List<Occupancy> snapshot){
        double p=e.progress()/course.length();
        if(e.breathing()){
            if(e.breathingRecovery()>=e.breathingRecoveryLimit()-1.0e-8){
                e.stopBreathing();
                events.add(new RaceEvent(tick,"BREATH_END",e.entryId(),null,"stamina="+e.stamina()));
            }
            return;
        }
        if(!e.canStartBreathing())return;
        boolean hardEmergency=e.stamina()<=.01;
        boolean emergency=e.stamina()<=.08;
        boolean enter=switch(e.strategy()){
            case FRONT->emergency;
            case FOLLOW->e.stamina()<=.04;
            case STEADY->{boolean safe=rankOf(e)>=4||trailingGap(e)>1.0;yield emergency||p<.78&&e.stamina()<=.13&&safe;}
            // A closer may reset before the launch point. After 60%, only a near-empty gauge can
            // interrupt its committed finishing drive.
            // A closer protects the launch before 60%, but must still pay for an under-built gauge:
            // after committing, 6% is late enough to hurt without hiding the risk until the line.
            case CLOSER->p<.50?e.stamina()<=.08:e.stamina()<=.02;
        };
        if(enter||hardEmergency){e.startBreathing();events.add(new RaceEvent(tick,"BREATH_START",e.entryId(),null,"stamina="+e.stamina()));}
    }

    private double surfaceMultiplier(RaceEntry e,TurtleSurface s){if(course.neutralPhysics())return 1;double loss=switch(s){case SAND->.96;case MUD->.94;case PUDDLE->.95;};
        if(s==TurtleSurface.SAND&&e.has("sand_friend"))loss=1-(1-loss)*.25;if(s==TurtleSurface.PUDDLE&&e.has("puddle_swimmer"))loss=Math.max(loss,.985);
        if(s==TurtleSurface.PUDDLE&&e.companion()==TurtleCompanion.DOLPHIN){loss=1-(1-loss)*(weather==TurtleWeather.LIGHT_RAIN?.50:.60);if(e.previousSurface()!=TurtleSurface.PUDDLE&&e.useCompanionSurface())e.recoverStamina(.025);}
        if(s==TurtleSurface.MUD&&e.has("mud_grip"))loss=1-(1-loss)*.25;
        if(e.previousSurface()!=null&&e.previousSurface()!=s&&e.has("changing_surface")){loss=1;e.recoverStamina(.004);}
        if(e.previousSurface()!=null&&e.previousSurface()!=s&&(e.has("adaptive_shell")||e.has("rainwash")&&weather==TurtleWeather.LIGHT_RAIN))loss=Math.min(1,loss+.025);
        if(e.previousSurface()!=null&&e.previousSurface()!=s&&e.has("rainwash")&&weather==TurtleWeather.LIGHT_RAIN)
            e.recoverStamina(.008);
        if(e.previousSurface()!=null&&e.previousSurface()!=s&&e.has("even_cadence"))loss=Math.min(1,loss+(1-loss)*.30);
        if(e.has("adaptive_shell")&&e.previousSurface()!=null&&e.previousSurface()!=s&&e.useFourSeasonShell()){e.recoverStamina(.06);loss=1;}
        if(e.activeEffectRunning()&&((e.activeSkill()==ActiveSkill.SAND_SPRINT&&s==TurtleSurface.SAND)||(e.activeSkill()==ActiveSkill.MUD_BREAKER&&s==TurtleSurface.MUD)||(e.activeSkill()==ActiveSkill.PUDDLE_SURF&&s==TurtleSurface.PUDDLE)))loss=1;
        if(e.companion()==TurtleCompanion.SNIFFER&&e.previousSurface()!=null&&e.previousSurface()!=s&&e.useCompanionSurface()){e.startSurge(60,1.12);e.recoverStamina(.03);return 1;}return loss;}
    private double surfaceCost(RaceEntry e,TurtleSurface s){if(course.neutralPhysics())return 1;double value=switch(s){case SAND->1.03;case MUD->1.05;case PUDDLE->1.02;};if(s==TurtleSurface.PUDDLE&&e.has("puddle_swimmer"))value*=.92;if(s==TurtleSurface.PUDDLE&&e.companion()==TurtleCompanion.DOLPHIN)value*=.94;if(s==TurtleSurface.SAND&&e.has("wind_reader")&&weather==TurtleWeather.SEA_BREEZE)value*=.94;return value;}
    private double strategyThrottle(RaceEntry e,List<Occupancy> snapshot){
        double p=e.progress()/course.length(),leader=entries.stream().mapToDouble(RaceEntry::progress).max().orElse(e.progress());
        int rank=rankOf(e);double gap=Math.max(0,leader-e.progress()),throttle;
        if(e.breathing()||e.breathingThrottle()<1)return e.breathingThrottle();
        throttle=switch(e.strategy()){
            // Hold the lead at the lowest useful output; the clean-air/pace-control stamina cost is
            // accounted for separately so a front runner cannot gain speed and reserve at once.
            case FRONT->e.activeEffectRunning()?1.0:p>.82?(e.has("leaders_ease")?.970:.940):rank==1
                    ?(trailingGap(e)>1.40?.90:trailingGap(e)>.65?.93:.97)
                    :gap>.90?Math.min(.995,.945+gap*.014):.94;
            // Even pacing with a small finish release. It does not seek a particular rank.
            case STEADY->p>.78?1.0:.985;
            // Match the leading pack instead of contesting the lead at full output. The saved gauge
            // is released in the final 16%; these are target-pace decisions, never raw-stat bonuses.
            case FOLLOW->p>=.84?1.0:rank<=2?.932:rank<=4&&drafting(e,snapshot)?.963:.987;
            // Deliberately accept the rear early, but begin closing before the last third. Waiting
            // until 65% left too little physical distance for even a superior finishing build.
            case CLOSER->p<.30?(gap>4.0?.975:.960):p<.48?.990
                    :e.stamina()>.06+(1-p)*.78?1.005:1.0;
        };
        // Shared pacing guard follows a course-progress reserve curve. It reacts to the actual gauge,
        // not directly to the selected strategy or stamina stat, and only makes small throttle edits.
        double plannedReserve=.08+(1-p)*.84;
        if(e.stamina()<plannedReserve-.08)throttle-=Math.min(.08,(plannedReserve-.08-e.stamina())*.28);
        else if(p>.65&&e.stamina()>plannedReserve+.10)throttle+=Math.min(.03,(e.stamina()-plannedReserve-.10)*.10);
        return Math.max(.78,Math.min(1.04,throttle));
    }
    private double strategyStaminaCost(RaceEntry e){
        if(e.strategy()!=TurtleStrategy.FRONT)return 1;
        // The active surge already pays exponential speed exertion. Applying the clean-air pace
        // premium as well made every front-specific active a double stamina penalty.
        if(e.activeEffectRunning())return 1;
        double p=e.progress()/course.length();int rank=rankOf(e);
        if(p>=.72)return 1;
        double instinct=e.has("front_instinct")?.94:1;
        if(rank<=2)return 1.18*instinct;
        if(rank<=4)return 1.08*instinct;
        return 1;
    }
    private double passiveSpeed(RaceEntry e,TurtleCourse.Sample s){
        double m=1,p=e.progress()/course.length();
        if(e.has("light_steps"))m*=1.010;
        if(e.has("straight_focus")&&s.curvature()<.05)m*=1.024;
        if(e.has("corner_cushion")&&s.curvature()>.05)m*=1.022;
        if(e.has("lane_cadence")){m*=1.007;if(e.changingLane())m*=1.022;}
        if(e.has("passing_spring")&&(e.blockedTicks()>0||e.changingLane()))m*=1.020;
        if(e.has("clear_sky_shell")&&weather==TurtleWeather.CLEAR)m*=1.019;
        if(e.has("rain_cadence")&&weather==TurtleWeather.LIGHT_RAIN)m*=1.019;
        if(e.has("weather_crown")&&weather!=TurtleWeather.CLEAR)m*=1.030;
        if(e.has("mist_compass")&&weather==TurtleWeather.FOREST_MIST)m*=1.019;
        if(e.has("wind_reader")&&weather==TurtleWeather.SEA_BREEZE&&s.curvature()<.05)m*=1.022;
        if(e.has("front_instinct")&&e.strategy()==TurtleStrategy.FRONT&&p<.35&&rankOf(e)<=3)m*=1.015;
        if(e.has("final_savings")&&e.strategy()==TurtleStrategy.CLOSER&&p>.70)m*=1.020;
        if(e.has("reserve_control")&&e.strategy()==TurtleStrategy.STEADY&&p>.70&&e.stamina()>=.10)m*=1.025;
        if(e.has("passing_vision")&&e.strategy()==TurtleStrategy.FOLLOW&&p>=.25&&p<=.80&&rankOf(e)>=3&&rankOf(e)<=6)m*=1.016;
        if(e.has("wake_follower")&&e.strategy()==TurtleStrategy.FOLLOW&&draftingNow(e))m*=1.014;
        if(e.has("final_route")&&e.strategy()==TurtleStrategy.CLOSER&&p>.70&&rankOf(e)>=4)m*=1.014;
        if(e.has("ancient_patience"))m*=p<.45?.994:p>=.72?1.045:1;
        if(e.has("comeback_star")&&p>.72&&rankOf(e)>=4)m*=1.025;
        if(e.has("corner_exit")&&e.cornerExitActive())m*=1.032;
        if(e.has("quiet_champion")&&p>=.70&&rankOf(e)>=2)m*=1.032;
        if(e.has("chasing_spring")&&e.strategy()==TurtleStrategy.CLOSER&&p>.75&&rankOf(e)>=4)m*=1.018;
        return Math.min(1.10,m);
    }
    private double passiveCost(RaceEntry e){
        double m=1,p=e.progress()/course.length();
        if(e.has("long_breath"))m*=.96;
        if(e.has("steady_breath"))m*=.96;
        if(e.has("early_rhythm")&&p<=.25)m*=.95;
        if(e.has("mist_breathing")&&weather==TurtleWeather.FOREST_MIST)m*=.94;
        if(e.has("long_course_pace")&&course.length()>=170)m*=.93;
        if(e.has("leaders_ease")&&e.strategy()==TurtleStrategy.FRONT&&p<.70&&rankOf(e)<=2)m*=.84;
        if(e.has("steady_cruise")&&e.strategy()==TurtleStrategy.STEADY&&p<.70)m*=.94;
        if(e.has("final_savings")&&e.strategy()==TurtleStrategy.CLOSER&&p<.70)m*=.94;
        if(e.has("hard_shell")&&e.interfered())m*=.85;
        return Math.max(.80,m);
    }
    private double passiveAcceleration(RaceEntry e,TurtleCourse.Sample s){
        double m=1,p=e.progress()/course.length();
        if(e.has("strong_flippers"))m*=1.050;
        if(e.has("starting_focus")&&tick<=50)m*=1.070;
        if(e.has("passing_spring")&&(e.blockedTicks()>0||e.changingLane()))m*=1.15;
        if(e.has("short_course_focus")&&course.length()<170)m*=1.050;
        if(e.has("corner_exit")&&e.cornerExitActive())m*=1.040;
        if(e.has("front_instinct")&&e.strategy()==TurtleStrategy.FRONT&&p<.35&&rankOf(e)<=3)m*=1.070;
        if(e.has("final_route")&&e.strategy()==TurtleStrategy.CLOSER&&p>.70&&rankOf(e)>=4)m*=1.050;
        if(e.has("comeback_star")&&p>.72&&rankOf(e)>=4)m*=1.050;
        if(e.has("reclaim_sense")&&e.strategy()==TurtleStrategy.FRONT&&rankOf(e)>=2&&rankOf(e)<=4)m*=1.080;
        if(e.has("chasing_spring")&&e.strategy()==TurtleStrategy.CLOSER&&p>.75&&rankOf(e)>=4)m*=1.080;
        return Math.min(1.20,m);
    }
    private double weatherSpeed(TurtleSurface s){if(weather==TurtleWeather.SEA_BREEZE&&s!=TurtleSurface.MUD)return 1.01;return 1;}
    private int rankOf(RaceEntry e){return 1+(int)entries.stream().filter(o->o.progress()>e.progress()).count();}
    private double leaderGap(RaceEntry e){return entries.stream().mapToDouble(RaceEntry::progress).max().orElse(e.progress())-e.progress();}
    private double trailingGap(RaceEntry e){return entries.stream().filter(v->v!=e&&!v.finished()&&v.progress()<e.progress()).mapToDouble(v->e.progress()-v.progress()).min().orElse(Double.POSITIVE_INFINITY);}
    private boolean drafting(RaceEntry e,List<Occupancy> snapshot){return snapshot.stream().anyMatch(o->o.entry!=e&&o.progress>e.progress()&&o.progress-e.progress()<1.8&&Math.abs(o.offset-e.laneOffset())<.85);}
    private boolean draftingNow(RaceEntry e){return entries.stream().anyMatch(o->o!=e&&!o.finished()&&o.progress()>e.progress()&&o.progress()-e.progress()<1.8&&Math.abs(o.laneOffset()-e.laneOffset())<.85);}
    private double paceVariation(RaceEntry e){long zone=(long)Math.floor(e.progress()/6.0);long mixed=mix(seed,e.entryId().hashCode()*37L+zone*101L);double unit=((mixed>>>11)&0xFFFF)/(double)0xFFFF*2-1;long form=mix(seed^0x51A7E5L,e.entryId().hashCode());double formUnit=((form>>>13)&0xFFFF)/(double)0xFFFF*2-1;double calm=1-.45*e.n(TurtleStat.CALM);double variation=e.has("even_cadence")?.65:1;return 1+(formUnit*.025*calm+unit*.018*calm)*variation;}
    private static long mix(long a,long b){long z=a^Long.rotateLeft(b,21)^0x9E3779B97F4A7C15L;z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);}

    public List<RaceEntry> standings() {
        if (cachedStandingsTick != tick) {
            cachedStandings = entries.stream()
                    .sorted(Comparator.comparing(RaceEntry::finished).reversed()
                            .thenComparingLong(entry -> entry.finished() ? entry.finishTick() : Long.MAX_VALUE)
                            .thenComparingDouble(RaceEntry::finishFraction)
                            .thenComparing(Comparator.comparingDouble(RaceEntry::progress).reversed()))
                    .toList();
            cachedStandingsTick = tick;
        }
        return cachedStandings;
    }
    /** Fixes unfinished time-trial AI order after the ten-second post-player presentation window. */
    public void forceFinishRemaining() {
        for (RaceEntry entry : standings().stream().filter(value -> !value.finished()).toList()) {
            entry.setProgress(course.length());
            entry.finish(tick, 1.0, ++finishCount);
            events.add(new RaceEvent(tick, "FINISH", entry.entryId(), null, "timeout_order"));
        }
        complete = true;
        cachedStandingsTick = Long.MIN_VALUE;
    }

    public TurtleCourse course() { return course; }
    public TurtleWeather weather() { return weather; }
    public long seed() { return seed; }
    public long currentTick() { return tick; }
    public boolean complete() { return complete; }
    public List<RaceEntry> entries() { return entriesView; }
    public List<RaceEvent> events() { return eventsView; }
    static double staminaExertion(double speed){return Math.exp(1.60*(speed-2.69));}
    private static double smooth(double value){double t=Math.max(0,Math.min(1,value));return t*t*(3-2*t);}
    public int hopCount(){return hopCount;}
}
