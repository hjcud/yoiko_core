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

/** Paired-seed comparison of innate stat distributions after following the same training policy. */
public final class TurtleArchetypeBalanceValidator {
    public record ArchetypeResult(int races,int wins,double winRate,double averageRank,double staminaPercent,
                                  double breathingRacePercent,TurtleStats averageHatch,TurtleStats averageFinish){}
    public record Report(int samplesPerArchetype,Map<TurtleStrategy,Map<TurtleArchetype,ArchetypeResult>> strategies,
                         long elapsedMillis){}

    private TurtleArchetypeBalanceValidator(){}

    public static void main(String[] args){
        int samples=args.length>0?Integer.parseInt(args[0]):24;
        System.out.println(validate(samples));
    }

    public static Report validate(int samples){
        long started=System.currentTimeMillis();
        Map<TurtleStrategy,Map<TurtleArchetype,ArchetypeResult>> strategies=new EnumMap<>(TurtleStrategy.class);
        for(TurtleStrategy strategy:TurtleStrategy.values()){
            Map<TurtleArchetype,ArchetypeResult> archetypes=new EnumMap<>(TurtleArchetype.class);
            for(TurtleArchetype archetype:TurtleArchetype.values())archetypes.put(archetype,run(strategy,archetype,samples));
            if(samples>=24){
                double best=archetypes.values().stream().mapToDouble(ArchetypeResult::averageRank).min().orElse(0);
                double worst=archetypes.values().stream().mapToDouble(ArchetypeResult::averageRank).max().orElse(0);
                if(worst-best>1.60)
                    throw new IllegalStateException("Innate archetype spread exceeds 1.60 ranks for "+strategy+": "+archetypes);
            }
            strategies.put(strategy,Map.copyOf(archetypes));
        }
        return new Report(samples,Map.copyOf(strategies),System.currentTimeMillis()-started);
    }

    private static ArchetypeResult run(TurtleStrategy strategy,TurtleArchetype archetype,int samples){
        int wins=0,breathing=0;double rank=0,stamina=0;long[] hatch=new long[5],finish=new long[5];
        for(int sample=0;sample<samples;sample++){
            long generationSeed=0xA2C4_0000L+strategy.ordinal()*65_537L+sample*257L;
            TurtleStats stats=TurtleGenerationService.generateStats(new SplittableRandom(generationSeed),TurtleRarity.RARE,archetype);
            add(hatch,stats);
            for(int session=0;session<24;session++){
                TurtleTrainingType type=TurtleGuidance.recommendedTraining(stats,TurtleRarity.RARE,strategy,session,
                        ActiveSkill.UNTURNED_HEART,List.of(),null);
                if(type==null)throw new IllegalStateException("Archetype training ended early: "+strategy+" "+archetype+" "+stats);
                stats.add(type.primary(),2);stats.add(type.secondary(),1);
            }
            add(finish,stats);
            long raceSeed=0xA2C4_7000L+strategy.ordinal()*1_000_003L+sample*104_729L;
            TurtleCourse course=TurtleCourse.generate(BlockPos.ZERO,raceSeed,(sample&1)==0);
            TurtleWeather weather=TurtleWeather.values()[sample%TurtleWeather.values().length];
            int candidateLane=Math.floorMod(sample*3+strategy.ordinal(),8);
            List<RaceEntry> entries=new ArrayList<>();
            RaceEntry candidate=RaceEntry.validation("archetype-candidate:"+strategy+":"+sample,stats,
                    ActiveSkill.UNTURNED_HEART,List.of(),strategy,candidateLane);
            entries.add(candidate);
            int opponentIndex=0;
            for(int lane=0;lane<8;lane++)if(lane!=candidateLane){
                TurtleStrategy opponentStrategy=TurtleStrategy.values()[Math.floorMod(opponentIndex+sample,4)];
                TurtleStats opponentStats=TurtleGuidance.targetStats(TurtleRarity.RARE,opponentStrategy,
                        ActiveSkill.UNTURNED_HEART,List.of(),null);
                entries.add(RaceEntry.validation("archetype-opponent:"+strategy+":"+sample+":"+opponentIndex,
                        opponentStats,ActiveSkill.UNTURNED_HEART,List.of(),opponentStrategy,lane));
                opponentIndex++;
            }
            RaceSimulation race=new RaceSimulation(course,weather,raceSeed,entries);
            while(!race.complete())race.tick();
            int finalRank=race.standings().indexOf(candidate)+1;
            if(finalRank==1)wins++;
            rank+=finalRank;stamina+=candidate.stamina()*100;
            if(candidate.breathingCount()>0)breathing++;
        }
        return new ArchetypeResult(samples,wins,wins*100.0/samples,rank/samples,stamina/samples,
                breathing*100.0/samples,average(hatch,samples),average(finish,samples));
    }

    private static void add(long[] totals,TurtleStats stats){
        for(TurtleStat stat:TurtleStat.values())totals[stat.ordinal()]+=stats.get(stat);
    }

    private static TurtleStats average(long[] totals,int count){
        int[] value=new int[totals.length];
        for(int i=0;i<value.length;i++)value[i]=(int)Math.round(totals[i]/(double)count);
        return new TurtleStats(value[0],value[1],value[2],value[3],value[4]);
    }
}
