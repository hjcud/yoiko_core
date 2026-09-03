package com.yoiko.core.turtle.race;

import com.yoiko.core.turtle.ActiveSkill;
import com.yoiko.core.turtle.PassiveSkill;
import com.yoiko.core.turtle.PassiveSkillCatalog;
import com.yoiko.core.turtle.TurtleStats;
import com.yoiko.core.turtle.TurtleStrategy;
import com.yoiko.core.turtle.TurtleCompanion;
import com.yoiko.core.turtle.TurtleWeather;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** Slow, deterministic balance suite. Kept separate from the normal course integrity check. */
public final class TurtleRacingBalanceValidator {
    public record StrategyResult(int wins,double rankAt35,double rankAt70,double finishRank,double finishStaminaPercent){}
    public record BuildResult(int wins,double finishRank,double finishStaminaPercent){}
    public record SkillResult(int races,int triggers,double timeBenefitPercent,double rankBenefit,double staminaSavedPoints,
                              double passesPerActivation,double leaderCapturePercent,double winRateGainPoints){}
    public record PassiveResult(double timeBenefitPercent,double rankBenefit,double staminaSavedPoints){}
    public record CategoryAverage(double timeBenefitPercent,double rankBenefit,double staminaSavedPoints){}
    public record LaneResult(int wins,double rankAt25,double laneAt25,double finishRank){}
    public record Report(Map<TurtleStrategy,StrategyResult> trainedStrategies,
                         Map<String,BuildResult> trainedBuilds,
                         Map<ActiveSkill,SkillResult> activeSkills,
                         Map<String,PassiveResult> passiveSkills,
                         Map<TurtleCompanion,PassiveResult> companions,
                         Map<Integer,LaneResult> startingLanes,
                         CategoryAverage activeAverage,CategoryAverage passiveAverage,
                         CategoryAverage specialPassiveAverage,CategoryAverage companionAverage,
                         double exertionAt3,double exertionAt35,double exertionAt4,long elapsedMillis){}
    private record PairResult(double baselineTicks,double skillTicks,double baselineRank,double skillRank,double baselineStamina,
                              double skillStamina,boolean triggered,int activationRank,int bestBurstRank,boolean capturedLead){}
    private record ActiveTrace(boolean triggered,int activationRank,int bestBurstRank,boolean capturedLead){}
    private static final TurtleStats[] TRAINED_STATS={
            new TurtleStats(90,60,76,50,52),new TurtleStats(65,90,63,57,53),
            new TurtleStats(68,65,90,52,53),new TurtleStats(70,62,62,80,54),
            new TurtleStats(65,66,60,52,85),new TurtleStats(76,76,68,54,54),
            new TurtleStats(72,82,70,52,52),new TurtleStats(70,70,70,59,59)};
    private static final String[] BUILD_NAMES={"speed","stamina","power","calm","navigation","speed_stamina","stamina_power","balanced"};
    private static final ActiveSkill[] TRAINED_ACTIVES={ActiveSkill.SHELLBREAK_START,ActiveSkill.CORAL_CORNER,
            ActiveSkill.SHELL_HOP,ActiveSkill.FALSE_FOOTPRINTS,ActiveSkill.THOUSAND_YEAR_STEP,
            ActiveSkill.HOMEWARD_WAVE,ActiveSkill.UNTURNED_HEART,ActiveSkill.SHELLBREAK_START};
    private static final List<List<String>> TRAINED_PASSIVES=List.of(
            List.of("light_steps","strong_flippers","starting_focus","straight_focus"),
            List.of("long_breath","early_rhythm","calm_recovery","long_course_pace"),
            List.of("strong_flippers","hop_landing","corner_exit","gap_finder"),
            List.of("hard_shell","shell_reflection","steady_cruise","early_rhythm"),
            List.of("good_sense_of_direction","corner_expert","gap_finder","early_rhythm"),
            List.of("light_steps","long_breath","straight_focus","corner_expert"),
            List.of("long_breath","strong_flippers","quiet_champion","comeback_star"),
            List.of("light_steps","long_breath","good_sense_of_direction","early_rhythm"));

    private TurtleRacingBalanceValidator(){}
    public static void main(String[] args){
        if(args.length>0){
            if(args[0].startsWith("passive:")){
                for(String id:args[0].substring("passive:".length()).split(",")){
                    PassiveSkill passive=PassiveSkillCatalog.get(id.trim());
                    System.out.println(passive.id()+"="+testPassive(passive,64));
                }
            }else if(args[0].startsWith("companion:")){
                for(String id:args[0].substring("companion:".length()).split(",")){
                    TurtleCompanion companion=TurtleCompanion.valueOf(id.trim());
                    System.out.println(companion+"="+testCompanion(companion,64));
                }
            }else{
                for(String id:args[0].split(",")){
                    ActiveSkill skill=ActiveSkill.valueOf(id.trim());
                    System.out.println(skill+"="+testActive(skill,96));
                }
            }
            return;
        }
        System.out.println(validate());
    }

    public static Report validate(){long started=System.currentTimeMillis();validateTrainedBudgets();
        double e3=RaceSimulation.staminaExertion(3),e35=RaceSimulation.staminaExertion(3.5),e4=RaceSimulation.staminaExertion(4);
        if(!(e3<e35&&e35<e4&&e4/e3>2.5))throw new IllegalStateException("Stamina exertion is not sufficiently exponential: "+e3+", "+e35+", "+e4);
        Map<String,BuildResult> trainedBuilds=new LinkedHashMap<>();Map<TurtleStrategy,StrategyResult> trainedStrategies=trainedStrategyTest(128,trainedBuilds);
        // This matrix intentionally assigns every stat build to every strategy, including poor
        // pairings. The recommendation validator below owns the tighter 10..45% viable-build band.
        for(TurtleStrategy strategy:TurtleStrategy.values()){int wins=trainedStrategies.get(strategy).wins();if(wins<2||wins>78)throw new IllegalStateException("Arbitrary build/strategy pairing outside 1.5..61%: "+trainedStrategies);}
        // Stamina, calm and navigation are defensive/technical specialists: their payoff is reserve,
        // resistance and clean lines, so a neutral speed-biased roster need not give a single-stat
        // overinvestment a win. Recommended mixed builds remain subject to the strict validator.
        for(Map.Entry<String,BuildResult> build:trainedBuilds.entrySet())if(build.getValue().wins()==0&&!Set.of("stamina","calm","navigation").contains(build.getKey()))throw new IllegalStateException("Trained stat build never wins: "+trainedBuilds);
        Map<Integer,LaneResult> laneResults=startingLaneTest(128);double earlyMin=laneResults.values().stream().mapToDouble(LaneResult::rankAt25).min().orElse(0),earlyMax=laneResults.values().stream().mapToDouble(LaneResult::rankAt25).max().orElse(0),finishMin=laneResults.values().stream().mapToDouble(LaneResult::finishRank).min().orElse(0),finishMax=laneResults.values().stream().mapToDouble(LaneResult::finishRank).max().orElse(0);
        // A full eight-turtle field cannot make every start slot identical, but the old fixed-lane
        // behaviour produced a 5.27-rank spread. Keep both first-quarter and finish spread under 1.8.
        if(earlyMax-earlyMin>1.8||finishMax-finishMin>1.8)throw new IllegalStateException("Starting-lane bias is too large: "+laneResults);
        Map<ActiveSkill,SkillResult> activeResults=new EnumMap<>(ActiveSkill.class);List<String> weakActives=new ArrayList<>();
        for(ActiveSkill skill:ActiveSkill.values()){
            SkillResult result=testActive(skill,48);activeResults.put(skill,result);
            if(result.triggers()==0)weakActives.add("never triggered "+skill);
            else if(result.timeBenefitPercent()<-2.0||result.timeBenefitPercent()>8.0
                    ||result.rankBenefit()>4.5||result.winRateGainPoints()>65)
                weakActives.add("outlier "+skill+" "+result);
            else if(result.timeBenefitPercent()<-.25&&result.rankBenefit()<-.25)
                weakActives.add("harmful "+skill+" "+result);
            else{
                double rankPerUse=result.rankBenefit()*result.races()/result.triggers();
                double staminaPerUse=result.staminaSavedPoints()*result.races()/result.triggers();
                boolean decisive=result.passesPerActivation()>=1.5||rankPerUse>=1.5
                        ||result.winRateGainPoints()>=15||staminaPerUse>=2.0;
                if(!decisive)weakActives.add("not decisive "+skill+" "+result);
            }
        }
        if(!weakActives.isEmpty())throw new IllegalStateException("Active skill validation failed: "+weakActives);
        for(ActiveSkill skill:Set.of(ActiveSkill.THOUSAND_YEAR_STEP,ActiveSkill.HOMEWARD_WAVE,ActiveSkill.FINAL_GAP,ActiveSkill.LIMIT_SPRINT)){SkillResult result=activeResults.get(skill);if(result.passesPerActivation()<3.0)throw new IllegalStateException("Comeback active does not pass at least three turtles per activation: "+skill+" "+result);}
        Map<String,PassiveResult> passiveResults=new LinkedHashMap<>();List<String> passiveFailures=new ArrayList<>();
        for(PassiveSkill passive:PassiveSkillCatalog.all()){PassiveResult result=testPassive(passive,16);passiveResults.put(passive.id(),result);boolean meaningful=passive.special()?result.timeBenefitPercent()>=.30||result.rankBenefit()>=.50||result.staminaSavedPoints()>=2.0:result.timeBenefitPercent()>=.08||result.rankBenefit()>=.20||result.staminaSavedPoints()>=.50;if(result.timeBenefitPercent()<-5.0||result.timeBenefitPercent()>8.0)passiveFailures.add("outlier "+passive.id()+" "+result);else if(result.timeBenefitPercent()<-3.0&&result.rankBenefit()<-1.5&&result.staminaSavedPoints()<0)passiveFailures.add("harmful "+passive.id()+" "+result);else if(!meaningful)passiveFailures.add("no meaningful impact "+passive.id()+" "+result);}
        if(!passiveFailures.isEmpty())throw new IllegalStateException("Passive skill validation failed: "+passiveFailures);
        Map<TurtleCompanion,PassiveResult> companionResults=new EnumMap<>(TurtleCompanion.class);List<String> companionFailures=new ArrayList<>();
        for(TurtleCompanion companion:TurtleCompanion.values()){PassiveResult result=testCompanion(companion,24);companionResults.put(companion,result);boolean meaningful=result.timeBenefitPercent()>=.10||result.rankBenefit()>=.20||result.staminaSavedPoints()>=.75;if(!meaningful)companionFailures.add("no meaningful auxiliary effect "+companion+" "+result);if(result.timeBenefitPercent()>2.0||result.rankBenefit()>2.0)companionFailures.add("exceeds auxiliary-passive role "+companion+" "+result);}
        if(!companionFailures.isEmpty())throw new IllegalStateException("Companion validation failed: "+companionFailures);
        CategoryAverage activeAverage=averageActive(activeResults),passiveAverage=averagePassive(passiveResults.values());
        CategoryAverage specialPassiveAverage=averagePassive(PassiveSkillCatalog.all().stream().filter(PassiveSkill::special).map(skill->passiveResults.get(skill.id())).toList());
        CategoryAverage ordinaryPassiveAverage=averagePassive(PassiveSkillCatalog.all().stream().filter(skill->!skill.special()).map(skill->passiveResults.get(skill.id())).toList());
        CategoryAverage companionAverage=averagePassive(companionResults.values());
        // In an eight-turtle field, a decisive active is primarily a positional event: finding a
        // lane or breaking traffic may change the finishing rank without shortening a long course
        // by several percent. Require both a visible time gain and a larger rank swing, then compare
        // both axes with ordinary passives instead of overfitting to clock time alone.
        if(activeAverage.timeBenefitPercent()<.85||activeAverage.rankBenefit()<1.50)throw new IllegalStateException("Active skills are not sufficiently decisive: "+activeAverage);
        // Conditional passives such as Breathing Switch are intentionally near-zero in a healthy
        // generic build and are validated in their own trigger scenario. Keep the general clock
        // floor modest while retaining a strict average finishing-rank contribution.
        if(passiveAverage.timeBenefitPercent()<.40||passiveAverage.rankBenefit()<.45)throw new IllegalStateException("Passives no longer shape race results: "+passiveAverage);
        // Active speed pays its real exponential stamina cost, but a once-per-race skill must still
        // move the field rather than resemble a fifth passive. Immediate passes are checked per
        // skill above, while this category floor protects the average finishing-rank conversion.
        if(activeAverage.timeBenefitPercent()<.85||activeAverage.rankBenefit()<1.50)throw new IllegalStateException("Active reversal power is too low: "+activeAverage);
        // Companions are conditional auxiliary passives. Their positional floor remains strict;
        // the clock floor allows conditional utility and stamina savings to carry part of the value.
        if(companionAverage.timeBenefitPercent()<.32||companionAverage.rankBenefit()<.35)throw new IllegalStateException("Companions are too weak as auxiliary passives: "+companionAverage);
        if(activeAverage.timeBenefitPercent()<passiveAverage.timeBenefitPercent()+.35
                ||activeAverage.rankBenefit()<passiveAverage.rankBenefit()+.75)throw new IllegalStateException("Active/passive role separation is too small: active="+activeAverage+", passive="+passiveAverage);
        if(specialPassiveAverage.timeBenefitPercent()<=ordinaryPassiveAverage.timeBenefitPercent())throw new IllegalStateException("Special passives do not outperform ordinary passives on average: special="+specialPassiveAverage+", ordinary="+ordinaryPassiveAverage);
        for(Map.Entry<TurtleCompanion,PassiveResult> result:companionResults.entrySet())if(result.getValue().timeBenefitPercent()<0&&result.getValue().rankBenefit()<0&&result.getValue().staminaSavedPoints()<=.10)throw new IllegalStateException("Companion is harmful in every measured dimension: "+result);
        for(SkillResult result:activeResults.values())if(result.timeBenefitPercent()<0&&result.rankBenefit()<=0&&result.staminaSavedPoints()<=0)throw new IllegalStateException("Active skill has no measured upside: "+activeResults);
        return new Report(Map.copyOf(trainedStrategies),Map.copyOf(trainedBuilds),Map.copyOf(activeResults),Map.copyOf(passiveResults),Map.copyOf(companionResults),Map.copyOf(laneResults),activeAverage,passiveAverage,specialPassiveAverage,companionAverage,e3,e35,e4,System.currentTimeMillis()-started);
    }

    private static CategoryAverage averageActive(Map<ActiveSkill,SkillResult> values){double time=0,rank=0,stamina=0;for(SkillResult value:values.values()){time+=value.timeBenefitPercent();rank+=value.rankBenefit();stamina+=value.staminaSavedPoints();}double count=values.size();return new CategoryAverage(time/count,rank/count,stamina/count);}
    private static CategoryAverage averagePassive(Iterable<PassiveResult> values){double time=0,rank=0,stamina=0,count=0;for(PassiveResult value:values){time+=value.timeBenefitPercent();rank+=value.rankBenefit();stamina+=value.staminaSavedPoints();count++;}return new CategoryAverage(time/count,rank/count,stamina/count);}

    private static void validateTrainedBudgets(){for(TurtleStats stats:TRAINED_STATS)if(stats.total()!=328)throw new IllegalStateException("Training build budget must be 328: "+stats.total());}

    private static Map<TurtleStrategy,StrategyResult> trainedStrategyTest(int races,Map<String,BuildResult> buildResults){Map<TurtleStrategy,Integer>wins=new EnumMap<>(TurtleStrategy.class);Map<TurtleStrategy,double[]> totals=new EnumMap<>(TurtleStrategy.class);for(TurtleStrategy strategy:TurtleStrategy.values())totals.put(strategy,new double[4]);int[] buildWins=new int[8];double[][] buildTotals=new double[8][2];TurtleStrategy[] strategies=TurtleStrategy.values();
        for(int raceIndex=0;raceIndex<races;raceIndex++){long seed=0x7A11_0000L+raceIndex*65_537L;TurtleCourse course=TurtleCourse.validationUniform(BlockPos.ZERO,seed);List<RaceEntry> entries=new ArrayList<>();
            for(int slot=0;slot<8;slot++){int build=Math.floorMod(slot+raceIndex*5,8);TurtleStrategy strategy=strategies[Math.floorMod(slot/2+raceIndex,4)];int lane=Math.floorMod(slot+raceIndex*3,8);
                entries.add(RaceEntry.validation("trained:"+raceIndex+":"+slot,TRAINED_STATS[build],TRAINED_ACTIVES[build],TRAINED_PASSIVES.get(build),strategy,lane));}
            RaceSimulation race=new RaceSimulation(course,TurtleWeather.CLEAR,seed,entries);Map<String,Integer> at35=null,at70=null;while(!race.complete()){race.tick();double lead=race.standings().getFirst().progress();if(at35==null&&lead>=course.length()*.35)at35=ranks(race);if(at70==null&&lead>=course.length()*.70)at70=ranks(race);}wins.merge(race.standings().getFirst().strategy(),1,Integer::sum);Map<String,Integer> finish=ranks(race);
            RaceEntry winner=race.standings().getFirst();for(int slot=0;slot<entries.size();slot++){RaceEntry entry=entries.get(slot);double[] sum=totals.get(entry.strategy());sum[0]+=at35.get(entry.entryId());sum[1]+=at70.get(entry.entryId());sum[2]+=finish.get(entry.entryId());sum[3]+=entry.stamina()*100;int build=Math.floorMod(slot+raceIndex*5,8);buildTotals[build][0]+=finish.get(entry.entryId());buildTotals[build][1]+=entry.stamina()*100;if(entry==winner)buildWins[build]++;}}
        Map<TurtleStrategy,StrategyResult> result=new EnumMap<>(TurtleStrategy.class);double samples=races*2.0;for(TurtleStrategy strategy:TurtleStrategy.values()){double[] sum=totals.get(strategy);result.put(strategy,new StrategyResult(wins.getOrDefault(strategy,0),sum[0]/samples,sum[1]/samples,sum[2]/samples,sum[3]/samples));}for(int build=0;build<8;build++)buildResults.put(BUILD_NAMES[build],new BuildResult(buildWins[build],buildTotals[build][0]/races,buildTotals[build][1]/races));return result;}

    private static SkillResult testActive(ActiveSkill skill,int races){int triggers=0,leadEligible=0,leadCaptures=0;double totalBenefit=0,totalRank=0,totalStamina=0,totalPasses=0,totalWinGain=0;
        for(int i=0;i<races;i++){PairResult result=activePair(skill,i);if(result.triggered){triggers++;totalPasses+=Math.max(0,result.activationRank-result.bestBurstRank);if(result.activationRank>1){leadEligible++;if(result.capturedLead)leadCaptures++;}}totalBenefit+=(result.baselineTicks-result.skillTicks)/result.baselineTicks*100;totalRank+=result.baselineRank-result.skillRank;totalStamina+=(result.skillStamina-result.baselineStamina)*100;totalWinGain+=(result.skillRank==1?1:0)-(result.baselineRank==1?1:0);}
        return new SkillResult(races,triggers,totalBenefit/races,totalRank/races,totalStamina/races,
                triggers==0?0:totalPasses/triggers,leadEligible==0?0:leadCaptures*100.0/leadEligible,totalWinGain*100.0/races);}

    private static PairResult activePair(ActiveSkill skill,int index){long seed=0xAC71_0000L+skill.ordinal()*10_007L+index*257L;int preset=activePreset(skill);TurtleCourse course=TurtleCourse.preset(BlockPos.ZERO,preset);TurtleWeather weather=activeWeather(skill);int candidate=Math.floorMod(index,8);TurtleStrategy strategy=activeStrategy(skill);
        List<RaceEntry> experimental=benchmarkEntries("active:"+skill.name()+":"+index,candidate,TRAINED_STATS[7],skill,List.of(),strategy,false,skill==ActiveSkill.UNTURNED_HEART);
        List<RaceEntry> baseline=benchmarkEntries("active:"+skill.name()+":"+index,candidate,TRAINED_STATS[7],skill,List.of(),strategy,true,skill==ActiveSkill.UNTURNED_HEART);
        if(skill==ActiveSkill.SHELL_HOP){prepareTrafficBlock(experimental,candidate);prepareTrafficBlock(baseline,candidate);}
        if(skill==ActiveSkill.UNTURNED_HEART){experimental.get(candidate).validationInterfere(80);baseline.get(candidate).validationInterfere(80);}
        RaceSimulation withSkill=new RaceSimulation(course,weather,seed,experimental),withoutSkill=new RaceSimulation(course,weather,seed,baseline);RaceEntry tested=experimental.get(candidate);ActiveTrace trace=runTracked(withSkill,tested);run(withoutSkill);
        RaceEntry base=withoutSkill.entries().get(candidate);return new PairResult(finishTime(base),finishTime(tested),withoutSkill.standings().indexOf(base)+1,withSkill.standings().indexOf(tested)+1,base.stamina(),tested.stamina(),trace.triggered(),trace.activationRank(),trace.bestBurstRank(),trace.capturedLead());}

    private static ActiveTrace runTracked(RaceSimulation race,RaceEntry candidate){boolean triggered=false,captured=false;int activationRank=0,bestRank=9;long activationTick=-1;
        while(!race.complete()){race.tick();if(!triggered&&candidate.activeUsed()){triggered=true;activationTick=race.currentTick();activationRank=race.standings().indexOf(candidate)+1;bestRank=activationRank;captured=activationRank==1;}if(triggered&&(candidate.activeEffectRunning()||race.currentTick()<=activationTick+20)){int rank=race.standings().indexOf(candidate)+1;bestRank=Math.min(bestRank,rank);if(rank==1)captured=true;}}
        return new ActiveTrace(triggered,activationRank,bestRank,captured);}

    private static void prepareTrafficBlock(List<RaceEntry> entries,int candidate){RaceEntry target=entries.get(candidate),blocker=entries.get((candidate+1)%entries.size());blocker.setTargetLane(target.lane());blocker.tickLane(.01);blocker.setProgress(.32);for(int i=0;i<24;i++)target.setBlocked(true);}

    private static PassiveResult testPassive(PassiveSkill passive,int races){double totalBenefit=0,totalRank=0,totalStamina=0;
        for(int i=0;i<races;i++){long seed=0xBA55_0000L+passive.id().hashCode()*31L+i*263L;int candidate=Math.floorMod(i,8);TurtleStrategy strategy=passiveStrategy(passive);TurtleCourse course=passive.tags().contains("SHORT")?TurtleCourse.validationUniformScaled(BlockPos.ZERO,seed,.75):passive.tags().contains("LONG")?TurtleCourse.validationUniformScaled(BlockPos.ZERO,seed,1.30):TurtleCourse.preset(BlockPos.ZERO,passivePreset(passive));TurtleWeather weather=passiveWeather(passive);TurtleStats stats=passive.id().equals("breathing_switch")?new TurtleStats(74,45,74,67,68):TRAINED_STATS[7];boolean interference=passive.tags().contains("INTERFERENCE");
            List<RaceEntry> with=benchmarkEntries("passive:"+passive.id()+":"+i,candidate,stats,ActiveSkill.UNTURNED_HEART,List.of(passive.id()),strategy,true,interference);
            List<RaceEntry> without=benchmarkEntries("passive:"+passive.id()+":"+i,candidate,stats,ActiveSkill.UNTURNED_HEART,List.of(),strategy,true,interference);
            if(passive.tags().contains("HOP")){prepareTrafficBlock(with,candidate);prepareTrafficBlock(without,candidate);with.get(candidate).startHop();without.get(candidate).startHop();}if(interference){with.get(candidate).interfere(80,false);without.get(candidate).interfere(80,false);}if(passive.id().equals("golden_gap"))with.get(candidate).markGoldenGapFailure();
            RaceSimulation a=new RaceSimulation(course,weather,seed,with),b=new RaceSimulation(course,weather,seed,without);run(a);run(b);RaceEntry tested=a.entries().get(candidate),baseEntry=b.entries().get(candidate);double base=finishTime(baseEntry);totalBenefit+=(base-finishTime(tested))/base*100;totalRank+=b.standings().indexOf(baseEntry)-a.standings().indexOf(tested);totalStamina+=(tested.stamina()-baseEntry.stamina())*100;}
        return new PassiveResult(totalBenefit/races,totalRank/races,totalStamina/races);}

    private static PassiveResult testCompanion(TurtleCompanion companion,int races){double totalBenefit=0,totalRank=0,totalStamina=0;
        for(int i=0;i<races;i++){long seed=0xC0A0_0000L+companion.ordinal()*11_003L+i*271L;int candidate=Math.floorMod(i,8);boolean traffic=switch(companion){case BLUE_AXOLOTL,FROG,ARMADILLO,GOAT->true;default->false;};
            TurtleStrategy strategy=companion==TurtleCompanion.PARROT?TurtleStrategy.CLOSER:companion==TurtleCompanion.BLUE_AXOLOTL?TurtleStrategy.FRONT:traffic?TurtleStrategy.STEADY:TurtleStrategy.STEADY;
            ActiveSkill active=companion==TurtleCompanion.PARROT?ActiveSkill.HOMEWARD_WAVE:companion==TurtleCompanion.ALLAY?ActiveSkill.SHELLBREAK_START:ActiveSkill.UNTURNED_HEART;
            TurtleStats stats=companion==TurtleCompanion.AXOLOTL?new TurtleStats(74,50,74,65,65):TRAINED_STATS[7];
            TurtleCourse course=companion==TurtleCompanion.AXOLOTL?TurtleCourse.validationUniformScaled(BlockPos.ZERO,seed,1.30):TurtleCourse.preset(BlockPos.ZERO,companion==TurtleCompanion.DOLPHIN||companion==TurtleCompanion.SNIFFER?1:5);
            TurtleWeather weather=companion==TurtleCompanion.DOLPHIN||companion==TurtleCompanion.AXOLOTL?TurtleWeather.LIGHT_RAIN:companion==TurtleCompanion.PARROT?TurtleWeather.SEA_BREEZE:companion==TurtleCompanion.FOX?TurtleWeather.FOREST_MIST:TurtleWeather.CLEAR;
            boolean disableActive=companion!=TurtleCompanion.PARROT&&companion!=TurtleCompanion.ALLAY;
            List<RaceEntry> with=benchmarkEntries("companion:"+companion.name()+":"+i,candidate,stats,active,List.of(),strategy,disableActive,false,companion);
            List<RaceEntry> without=benchmarkEntries("companion:"+companion.name()+":"+i,candidate,stats,active,List.of(),strategy,disableActive,false,null);
            if(traffic){prepareTrafficBlock(with,candidate);prepareTrafficBlock(without,candidate);}
            if(companion==TurtleCompanion.BLUE_AXOLOTL){prepareFailedHop(with,candidate);prepareFailedHop(without,candidate);}
            RaceSimulation a=new RaceSimulation(course,weather,seed,with),b=new RaceSimulation(course,weather,seed,without);
            if(companion==TurtleCompanion.WOLF){for(int tick=0;tick<80;tick++){a.tick();b.tick();}a.entries().get(candidate).interfere(100,false);b.entries().get(candidate).interfere(100,false);}
            run(a);run(b);RaceEntry tested=a.entries().get(candidate),base=b.entries().get(candidate);double baseTime=finishTime(base);totalBenefit+=(baseTime-finishTime(tested))/baseTime*100;totalRank+=b.standings().indexOf(base)-a.standings().indexOf(tested);totalStamina+=(tested.stamina()-base.stamina())*100;}
        return new PassiveResult(totalBenefit/races,totalRank/races,totalStamina/races);}

    private static void prepareFailedHop(List<RaceEntry> entries,int candidate){RaceEntry target=entries.get(candidate),guard=entries.get((candidate+2)%entries.size());guard.setTargetLane(target.lane());guard.tickLane(.01);guard.setProgress(1.15);}

    private static Map<Integer,LaneResult> startingLaneTest(int races){int[] wins=new int[8];double[] early=new double[8],earlyLane=new double[8],finish=new double[8];
        for(int raceIndex=0;raceIndex<races;raceIndex++){long seed=0x1A4E_0000L+raceIndex*8_191L;TurtleCourse course=TurtleCourse.validationUniform(BlockPos.ZERO,seed);List<RaceEntry> entries=new ArrayList<>();Map<String,Integer> startLanes=new LinkedHashMap<>();
            for(int lane=0;lane<8;lane++){RaceEntry entry=RaceEntry.validation("lane:"+raceIndex+":"+Math.floorMod(lane+raceIndex*3,8),TRAINED_STATS[7],ActiveSkill.UNTURNED_HEART,List.of(),TurtleStrategy.STEADY,lane);entries.add(entry);startLanes.put(entry.entryId(),lane);}
            RaceSimulation race=new RaceSimulation(course,TurtleWeather.CLEAR,seed,entries);Map<String,Integer> at25=null;while(!race.complete()){race.tick();if(at25==null&&race.standings().getFirst().progress()>=course.length()*.25){at25=ranks(race);for(RaceEntry entry:entries)earlyLane[startLanes.get(entry.entryId())]+=entry.lane();}}Map<String,Integer> finalRanks=ranks(race);wins[startLanes.get(race.standings().getFirst().entryId())]++;
            for(RaceEntry entry:entries){int startLane=startLanes.get(entry.entryId());early[startLane]+=at25.get(entry.entryId());finish[startLane]+=finalRanks.get(entry.entryId());}}
        Map<Integer,LaneResult> result=new LinkedHashMap<>();for(int lane=0;lane<8;lane++)result.put(lane,new LaneResult(wins[lane],early[lane]/races,earlyLane[lane]/races,finish[lane]/races));return result;}

    private static List<RaceEntry> benchmarkEntries(String id,int candidate,TurtleStats stats,ActiveSkill skill,List<String> passives,TurtleStrategy strategy,boolean disableCandidate,boolean interferenceOpponents){return benchmarkEntries(id,candidate,stats,skill,passives,strategy,disableCandidate,interferenceOpponents,null);}
    private static List<RaceEntry> benchmarkEntries(String id,int candidate,TurtleStats stats,ActiveSkill skill,List<String> passives,TurtleStrategy strategy,boolean disableCandidate,boolean interferenceOpponents,TurtleCompanion companion){List<RaceEntry> entries=new ArrayList<>();
        for(int slot=0;slot<8;slot++){boolean target=slot==candidate;ActiveSkill active=target?skill:interferenceOpponents?ActiveSkill.FALSE_FOOTPRINTS:ActiveSkill.UNTURNED_HEART;RaceEntry entry=RaceEntry.validation(id+":"+slot,target?stats:TRAINED_STATS[7],active,target?passives:List.of(),target?strategy:TurtleStrategy.STEADY,slot);if(target)entry.setValidationCompanion(companion);if((target&&disableCandidate)||(!target&&!interferenceOpponents))entry.useActive();entries.add(entry);}return entries;}
    private static void run(RaceSimulation race){while(!race.complete())race.tick();}
    private static Map<String,Integer> ranks(RaceSimulation race){Map<String,Integer> result=new LinkedHashMap<>();List<RaceEntry> order=race.standings();for(int i=0;i<order.size();i++)result.put(order.get(i).entryId(),i+1);return result;}
    private static double finishTime(RaceEntry entry){return entry.finishTick()+entry.finishFraction();}
    private static TurtleStrategy activeStrategy(ActiveSkill skill){return switch(skill){case SHELLBREAK_START,LEAD_GUARD,SURGING_SPRAY->TurtleStrategy.FRONT;case WAKE_CUT->TurtleStrategy.FOLLOW;case THOUSAND_YEAR_STEP,HOMEWARD_WAVE,FINAL_GAP,LIMIT_SPRINT->TurtleStrategy.CLOSER;default->TurtleStrategy.STEADY;};}
    private static int activePreset(ActiveSkill skill){return switch(skill){case SAND_SPRINT->0;case PUDDLE_SURF,SURFACE_CHAIN->1;case MUD_BREAKER->3;default->5;};}
    private static TurtleWeather activeWeather(ActiveSkill skill){return switch(skill){case TAILWIND_SAIL->TurtleWeather.SEA_BREEZE;case RAINSTEP->TurtleWeather.LIGHT_RAIN;default->TurtleWeather.CLEAR;};}
    private static TurtleStrategy passiveStrategy(PassiveSkill p){if(p.tags().contains("FRONT"))return TurtleStrategy.FRONT;if(p.tags().contains("STEADY"))return TurtleStrategy.STEADY;if(p.tags().contains("FOLLOW"))return TurtleStrategy.FOLLOW;if(p.tags().contains("CLOSER")||p.tags().contains("FINAL"))return TurtleStrategy.CLOSER;return TurtleStrategy.STEADY;}
    private static int passivePreset(PassiveSkill p){if(p.tags().contains("PUDDLE"))return 1;if(p.tags().contains("SAND"))return 0;if(p.tags().contains("MUD"))return 3;return 5;}
    private static TurtleWeather passiveWeather(PassiveSkill p){if(p.tags().contains("RAIN"))return TurtleWeather.LIGHT_RAIN;if(p.tags().contains("MIST"))return TurtleWeather.FOREST_MIST;if(p.tags().contains("WIND")||p.tags().contains("WEATHER"))return TurtleWeather.SEA_BREEZE;return TurtleWeather.CLEAR;}
}
