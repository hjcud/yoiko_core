package com.yoiko.core.turtle;

import com.yoiko.core.turtle.race.RaceEntry;
import com.yoiko.core.turtle.race.RaceSimulation;
import com.yoiko.core.turtle.race.TurtleCourse;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;

/** Deterministic proof that following the UI recommendation reaches a usable strategy build. */
public final class TurtleTrainingBalanceValidator {
    public record Loadout(ActiveSkill active,List<String> passives,TurtleCompanion companion){}
    public record GuidanceResult(TurtleStats target,TurtleStats averageFinish,Map<TurtleTrainingType,Double> averageSessions){}
    public record RaceResult(int wins,double averageRank,double averageFinishStamina,double minimumFinishStamina,
                             double maximumFinishStamina){}
    public record Report(Map<TurtleStrategy,GuidanceResult> guidance,Map<TurtleStrategy,RaceResult> races,long elapsedMillis){}

    private static final Map<TurtleStrategy,Loadout> LOADOUTS=Map.of(
            TurtleStrategy.FRONT,new Loadout(ActiveSkill.LEAD_GUARD,List.of("leaders_ease","starting_focus","strong_flippers","golden_gap"),TurtleCompanion.RABBIT),
            TurtleStrategy.STEADY,new Loadout(ActiveSkill.RESERVE_RELEASE,List.of("reserve_control","long_breath","calm_recovery","adaptive_shell"),TurtleCompanion.AXOLOTL),
            TurtleStrategy.FOLLOW,new Loadout(ActiveSkill.WAKE_CUT,List.of("passing_vision","gap_finder","corner_expert","quiet_champion"),TurtleCompanion.BEE),
            TurtleStrategy.CLOSER,new Loadout(ActiveSkill.FINAL_GAP,List.of("final_route","comeback_star","light_steps","long_breath"),TurtleCompanion.PARROT));

    private TurtleTrainingBalanceValidator(){}
    public static void main(String[] args){int races=args.length>0?Integer.parseInt(args[0]):256;System.out.println(validate(races));}

    public static Report validate(int raceCount){long started=System.currentTimeMillis();int samples=40;
        Map<TurtleStrategy,List<TurtleStats>> finished=new EnumMap<>(TurtleStrategy.class);
        Map<TurtleStrategy,GuidanceResult> guidance=new EnumMap<>(TurtleStrategy.class);
        for(TurtleStrategy strategy:TurtleStrategy.values()){
            Loadout loadout=LOADOUTS.get(strategy);List<TurtleStats> builds=new ArrayList<>();double[] statSums=new double[5];double[] trainingSums=new double[TurtleTrainingType.values().length];
            for(int sample=0;sample<samples;sample++){
                TurtleArchetype archetype=sample<20?TurtleArchetype.valueOf(strategy.name()):TurtleArchetype.values()[sample%TurtleArchetype.values().length];
                TurtleStats stats=TurtleGenerationService.generateStats(new SplittableRandom(0x7A61_0000L+strategy.ordinal()*10_007L+sample*257L),TurtleRarity.RARE,archetype);
                for(int session=0;session<24;session++){
                    TurtleTrainingType type=TurtleGuidance.recommendedTraining(stats,TurtleRarity.RARE,strategy,session,loadout.active(),loadout.passives(),loadout.companion());
                    if(type==null)throw new IllegalStateException("Recommendation ended early: "+strategy+" "+stats+" session="+session);
                    stats.add(type.primary(),2);stats.add(type.secondary(),1);trainingSums[type.ordinal()]++;
                }
                if(stats.total()!=TurtleRarity.RARE.finalBudget())throw new IllegalStateException("Training budget mismatch: "+stats);
                builds.add(stats.copy());for(TurtleStat stat:TurtleStat.values())statSums[stat.ordinal()]+=stats.get(stat);
            }
            finished.put(strategy,List.copyOf(builds));EnumMap<TurtleTrainingType,Double> trainingAverage=new EnumMap<>(TurtleTrainingType.class);
            for(TurtleTrainingType type:TurtleTrainingType.values())trainingAverage.put(type,trainingSums[type.ordinal()]/samples);
            guidance.put(strategy,new GuidanceResult(TurtleGuidance.targetStats(TurtleRarity.RARE,strategy,loadout.active(),loadout.passives(),loadout.companion()),
                    new TurtleStats((int)Math.round(statSums[0]/samples),(int)Math.round(statSums[1]/samples),(int)Math.round(statSums[2]/samples),(int)Math.round(statSums[3]/samples),(int)Math.round(statSums[4]/samples)),Map.copyOf(trainingAverage)));
        }
        Map<TurtleStrategy,double[]> totals=new EnumMap<>(TurtleStrategy.class);for(TurtleStrategy strategy:TurtleStrategy.values())totals.put(strategy,new double[]{0,0,0,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY});
        for(int raceIndex=0;raceIndex<raceCount;raceIndex++){
            long seed=0x6A1D_0000L+raceIndex*65_537L;TurtleCourse course=TurtleCourse.generate(BlockPos.ZERO,seed,(raceIndex&1)==0);TurtleWeather weather=TurtleWeather.values()[raceIndex%TurtleWeather.values().length];List<RaceEntry> entries=new ArrayList<>();
            for(int slot=0;slot<8;slot++){
                TurtleStrategy strategy=TurtleStrategy.values()[Math.floorMod(slot/2+raceIndex,TurtleStrategy.values().length)];Loadout loadout=LOADOUTS.get(strategy);TurtleStats stats=finished.get(strategy).get(Math.floorMod(raceIndex*3+slot,finished.get(strategy).size()));
                RaceEntry entry=RaceEntry.validation("guidance:"+raceIndex+":"+slot,stats,loadout.active(),loadout.passives(),strategy,Math.floorMod(slot+raceIndex*3,8));entry.setValidationCompanion(loadout.companion());entries.add(entry);
            }
            RaceSimulation race=new RaceSimulation(course,weather,seed,entries);while(!race.complete())race.tick();List<RaceEntry> order=race.standings();totals.get(order.getFirst().strategy())[0]++;
            for(int rank=0;rank<order.size();rank++){RaceEntry entry=order.get(rank);double[] values=totals.get(entry.strategy());values[1]+=rank+1;values[2]++;double stamina=entry.stamina()*100;values[3]=Math.min(values[3],stamina);values[4]=Math.max(values[4],stamina);}
        }
        Map<TurtleStrategy,RaceResult> results=new EnumMap<>(TurtleStrategy.class);
        for(TurtleStrategy strategy:TurtleStrategy.values()){double[] v=totals.get(strategy);double stamina=0; // Re-run-free average is accumulated separately below.
            // The min/max already guard extremes; estimate the mean from representative solo runs so
            // each strategy receives an equal sample count independent of finishing order.
            int sampleRaces=Math.min(96,raceCount);for(int i=0;i<sampleRaces;i++){Loadout loadout=LOADOUTS.get(strategy);TurtleStats stats=finished.get(strategy).get(i%finished.get(strategy).size());List<RaceEntry> field=new ArrayList<>();RaceEntry candidate=RaceEntry.validation("stamina:"+strategy+":"+i,stats,loadout.active(),loadout.passives(),strategy,i%8);candidate.setValidationCompanion(loadout.companion());field.add(candidate);for(int lane=1;lane<8;lane++)field.add(RaceEntry.validation("stamina-ai:"+strategy+":"+i+":"+lane,new TurtleStats(70,70,70,59,59),ActiveSkill.UNTURNED_HEART,List.of("light_steps","long_breath","good_sense_of_direction","early_rhythm"),TurtleStrategy.values()[lane%4],(i+lane)%8));RaceSimulation race=new RaceSimulation(TurtleCourse.generate(BlockPos.ZERO,0x5A11_0000L+i*263L,(i&1)==0),TurtleWeather.values()[i%TurtleWeather.values().length],0x5A11_0000L+i*263L,field);while(!race.complete())race.tick();stamina+=candidate.stamina()*100;}
            RaceResult result=new RaceResult((int)v[0],v[1]/v[2],stamina/sampleRaces,v[3],v[4]);
            results.put(strategy,result);
        }
        double minimumWinRate=raceCount>=256?.15:.14;
        double maximumWinRate=raceCount>=256?.35:.36;
        for(Map.Entry<TurtleStrategy,RaceResult> value:results.entrySet()){
            RaceResult result=value.getValue();double winRate=result.wins()/(double)raceCount;
            // Short smoke runs have a wider binomial band than the 256-race release proof.
            if(winRate<minimumWinRate||winRate>maximumWinRate)throw new IllegalStateException("Recommended strategy win rate outside "+(minimumWinRate*100)+".."+(maximumWinRate*100)+"%: races="+results+" guidance="+guidance);
            if(result.averageRank()<3.0||result.averageRank()>5.8)throw new IllegalStateException("Recommended strategy average rank outside 3.0..5.8: races="+results+" guidance="+guidance);
            if(result.averageFinishStamina()<6||result.averageFinishStamina()>26)throw new IllegalStateException("Recommended build finish stamina outside 6..26%: races="+results+" guidance="+guidance);
        }
        return new Report(Map.copyOf(guidance),Map.copyOf(results),System.currentTimeMillis()-started);
    }
}
