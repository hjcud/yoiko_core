package com.yoiko.core.turtle.race;

import com.yoiko.core.turtle.ActiveSkill;
import com.yoiko.core.turtle.TurtleCompanion;
import com.yoiko.core.turtle.TurtleGuidance;
import com.yoiko.core.turtle.TurtleLeague;
import com.yoiko.core.turtle.TurtleRarity;
import com.yoiko.core.turtle.TurtleStats;
import com.yoiko.core.turtle.TurtleStrategy;
import com.yoiko.core.turtle.TurtleWeather;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Deterministic balance matrix for bare strategy profiles and fully specialized builds. */
public final class TurtleStrategySpecializationValidator {
    public record Loadout(TurtleStats stats,ActiveSkill active,List<String> passives,TurtleCompanion companion){}
    public record SoloResult(int races,int wins,double winRate,double averageRank,double staminaPercent,
                             double activeRate,int comebackWins){}
    public record StrategyResult(int wins,double winRate,double averageRank,double rankAt35,double rankAt70,
                                 double staminaPercent,double breathingRacePercent){}
    public record FieldResult(int races,Map<TurtleStrategy,StrategyResult> strategies){}
    public record CombinationResult(int races,double baselineRank,double activeRank,double passivesRank,
                                    double companionRank,double fullRank,double activeGain,double passivesGain,
                                    double companionGain,double fullGain,double interactionResidual){}
    public record Report(Map<TurtleStrategy,Loadout> loadouts,Map<TurtleStrategy,SoloResult> soloVsOpenAi,
                         FieldResult bareStrategies,FieldResult specializedStrategies,
                         Map<TurtleStrategy,CombinationResult> combinations,long elapsedMillis){}

    private static final Map<TurtleStrategy,Loadout> LOADOUTS=Map.of(
            TurtleStrategy.FRONT,loadout(TurtleStrategy.FRONT,ActiveSkill.LEAD_GUARD,
                    List.of("leaders_ease","starting_focus","strong_flippers","golden_gap"),TurtleCompanion.RABBIT),
            TurtleStrategy.STEADY,loadout(TurtleStrategy.STEADY,ActiveSkill.RESERVE_RELEASE,
                    List.of("reserve_control","long_breath","calm_recovery","adaptive_shell"),TurtleCompanion.AXOLOTL),
            TurtleStrategy.FOLLOW,loadout(TurtleStrategy.FOLLOW,ActiveSkill.WAKE_CUT,
                    List.of("passing_vision","gap_finder","corner_expert","quiet_champion"),TurtleCompanion.BEE),
            TurtleStrategy.CLOSER,loadout(TurtleStrategy.CLOSER,ActiveSkill.FINAL_GAP,
                    List.of("final_route","comeback_star","light_steps","long_breath"),TurtleCompanion.PARROT));

    private TurtleStrategySpecializationValidator(){}

    private static Loadout loadout(TurtleStrategy strategy,ActiveSkill active,List<String> passives,TurtleCompanion companion){
        return new Loadout(TurtleGuidance.targetStats(TurtleRarity.RARE,strategy,active,passives,companion),active,passives,companion);
    }

    private static Loadout bareLoadout(TurtleStrategy strategy){
        return new Loadout(TurtleGuidance.targetStats(TurtleRarity.RARE,strategy,ActiveSkill.UNTURNED_HEART,List.of(),null),
                ActiveSkill.UNTURNED_HEART,List.of(),null);
    }

    public static void main(String[] args){
        int solo=args.length>0?Integer.parseInt(args[0]):512;
        int head=args.length>1?Integer.parseInt(args[1]):1_024;
        System.out.println(validate(solo,head));
    }

    public static Report validate(){return validate(512,1_024);}

    public static Report validate(int soloRaces,int headRaces){
        long started=System.currentTimeMillis();
        LOADOUTS.forEach((strategy,loadout)->{
            if(loadout.stats().total()!=TurtleRarity.RARE.finalBudget())
                throw new IllegalStateException(strategy+" budget="+loadout.stats().total());
        });
        Map<TurtleStrategy,SoloResult> solo=new EnumMap<>(TurtleStrategy.class);
        for(TurtleStrategy strategy:TurtleStrategy.values())solo.put(strategy,runSolo(strategy,soloRaces));
        FieldResult bare=runHeadToHead(headRaces,false);
        FieldResult specialized=runHeadToHead(headRaces,true);
        Map<TurtleStrategy,CombinationResult> combinations=new EnumMap<>(TurtleStrategy.class);
        int combinationRaces=Math.max(48,Math.min(128,soloRaces/2));
        for(TurtleStrategy strategy:TurtleStrategy.values())
            combinations.put(strategy,runCombination(strategy,combinationRaces));
        validateField("bare",bare);
        validateField("specialized",specialized);
        validateCombinations(combinations);
        return new Report(Map.copyOf(LOADOUTS),Map.copyOf(solo),bare,specialized,Map.copyOf(combinations),
                System.currentTimeMillis()-started);
    }

    private static void validateField(String label,FieldResult field){
        // A 256-race smoke run has roughly a five-point 95% sampling band around a 25% share.
        // Release-size runs tighten this to 18..32%.
        double minimum=field.races()>=512?18:15,maximum=field.races()>=512?32:36;
        for(Map.Entry<TurtleStrategy,StrategyResult> entry:field.strategies().entrySet()){
            StrategyResult result=entry.getValue();
            if(result.winRate()<minimum||result.winRate()>maximum)
                throw new IllegalStateException(label+" strategy win rate outside "+minimum+".."+maximum+"%: "+field);
            if(result.averageRank()<3.30||result.averageRank()>5.70)
                throw new IllegalStateException(label+" strategy average rank outside 3.30..5.70: "+field);
        }
        StrategyResult front=field.strategies().get(TurtleStrategy.FRONT);
        StrategyResult closer=field.strategies().get(TurtleStrategy.CLOSER);
        if(front.rankAt35()>4.10)
            throw new IllegalStateException(label+" front runner no longer establishes an early position: "+field);
        if(closer.rankAt35()<5.50||closer.averageRank()>closer.rankAt35()-1.0)
            throw new IllegalStateException(label+" closer no longer shows a real late comeback: "+field);
    }

    private static void validateCombinations(Map<TurtleStrategy,CombinationResult> combinations){
        for(Map.Entry<TurtleStrategy,CombinationResult> entry:combinations.entrySet()){
            CombinationResult result=entry.getValue();
            if(result.fullGain()<2.0)
                throw new IllegalStateException("Specialized combination is not meaningful: "+entry);
            // Traffic resolution is non-linear: a stamina passive can keep a turtle close enough
            // for its active to find a lane.  Allow one additional rank of healthy role synergy,
            // while still rejecting combinations that manufacture more than 1.25 ranks.
            if(result.interactionResidual()>1.25||result.interactionResidual()<-2.50)
                throw new IllegalStateException("Specialized combination has excessive interaction: "+entry);
        }
    }

    private static SoloResult runSolo(TurtleStrategy strategy,int races){
        Loadout loadout=LOADOUTS.get(strategy);int wins=0,active=0,comebacks=0;double rank=0,stamina=0;
        for(int raceIndex=0;raceIndex<races;raceIndex++){
            long seed=0x5EEC_0000L+strategy.ordinal()*1_000_003L+raceIndex*65_537L;
            TurtleCourse course=TurtleCourse.generate(BlockPos.ZERO,seed,(raceIndex&1)==0);
            TurtleWeather weather=TurtleWeather.values()[Math.floorMod(raceIndex+strategy.ordinal(),TurtleWeather.values().length)];
            int candidateLane=Math.floorMod(raceIndex*3+strategy.ordinal(),8);
            List<RaceEntry> entries=new ArrayList<>();RaceEntry candidate=null;int aiIndex=0;
            for(int lane=0;lane<8;lane++){
                if(lane==candidateLane){candidate=specialist(strategy,loadout,"solo:"+raceIndex,lane);entries.add(candidate);}
                else{
                    AiTurtleProfile profile=AiTurtleCatalog.all().get(Math.floorMod(raceIndex*5+aiIndex++,AiTurtleCatalog.all().size()));
                    entries.add(RaceEntry.ai(profile,TurtleLeague.OPEN,seed^lane*997L,lane));
                }
            }
            RaceSimulation race=new RaceSimulation(course,weather,seed,entries);Map<String,Integer> at70=null;
            while(!race.complete()){
                race.tick();
                if(at70==null&&race.standings().getFirst().progress()>=course.length()*.70)at70=ranks(race);
            }
            int finish=race.standings().indexOf(candidate)+1;rank+=finish;stamina+=candidate.stamina()*100;
            if(finish==1){wins++;if(at70!=null&&at70.get(candidate.entryId())>=4)comebacks++;}
            String candidateId=candidate.entryId();
            if(race.events().stream().anyMatch(event->event.type().equals("ACTIVE")&&candidateId.equals(event.sourceEntryId())))active++;
        }
        return new SoloResult(races,wins,wins*100.0/races,rank/races,stamina/races,active*100.0/races,comebacks);
    }

    private static FieldResult runHeadToHead(int races,boolean specialized){
        EnumMap<TurtleStrategy,double[]> totals=new EnumMap<>(TurtleStrategy.class);
        for(TurtleStrategy strategy:TurtleStrategy.values())totals.put(strategy,new double[7]);
        TurtleStrategy[] strategies=TurtleStrategy.values();
        long baseSeed=specialized?0xD1EC_7000L:0xBA5E_7000L;
        for(int raceIndex=0;raceIndex<races;raceIndex++){
            long seed=baseSeed+raceIndex*104_729L;
            TurtleCourse course=TurtleCourse.generate(BlockPos.ZERO,seed,(raceIndex&1)==0);
            TurtleWeather weather=TurtleWeather.values()[raceIndex%TurtleWeather.values().length];
            List<RaceEntry> entries=new ArrayList<>();
            for(int slot=0;slot<8;slot++){
                TurtleStrategy strategy=strategies[Math.floorMod(slot/2+raceIndex,strategies.length)];
                int lane=Math.floorMod(slot+raceIndex*3,8);
                Loadout loadout=specialized?LOADOUTS.get(strategy):bareLoadout(strategy);
                entries.add(specialist(strategy,loadout,(specialized?"specialized:":"bare:")+raceIndex+":"+slot,lane));
            }
            RaceSimulation race=new RaceSimulation(course,weather,seed,entries);
            Map<String,Integer> at35=null,at70=null;
            while(!race.complete()){
                race.tick();double leader=race.standings().getFirst().progress();
                if(at35==null&&leader>=course.length()*.35)at35=ranks(race);
                if(at70==null&&leader>=course.length()*.70)at70=ranks(race);
            }
            List<RaceEntry> order=race.standings();totals.get(order.getFirst().strategy())[0]++;
            for(int finish=0;finish<order.size();finish++){
                RaceEntry entry=order.get(finish);double[] values=totals.get(entry.strategy());
                values[1]+=finish+1;values[2]++;values[3]+=at35.get(entry.entryId());values[4]+=at70.get(entry.entryId());
                values[5]+=entry.stamina()*100;if(entry.breathingCount()>0)values[6]++;
            }
        }
        EnumMap<TurtleStrategy,StrategyResult> results=new EnumMap<>(TurtleStrategy.class);
        for(TurtleStrategy strategy:TurtleStrategy.values()){
            double[] value=totals.get(strategy);double samples=value[2];int wins=(int)value[0];
            results.put(strategy,new StrategyResult(wins,wins*100.0/races,value[1]/samples,value[3]/samples,
                    value[4]/samples,value[5]/samples,value[6]*100.0/samples));
        }
        return new FieldResult(races,Map.copyOf(results));
    }

    private static CombinationResult runCombination(TurtleStrategy strategy,int races){
        double[] ranks=new double[5];
        for(int raceIndex=0;raceIndex<races;raceIndex++){
            long seed=0xC04B_0000L+strategy.ordinal()*1_000_003L+raceIndex*65_537L;
            TurtleCourse course=TurtleCourse.generate(BlockPos.ZERO,seed,(raceIndex&1)==0);
            TurtleWeather weather=TurtleWeather.values()[Math.floorMod(raceIndex+strategy.ordinal(),TurtleWeather.values().length)];
            int candidateLane=Math.floorMod(raceIndex*3+strategy.ordinal(),8);
            for(int mode=0;mode<5;mode++){
                List<RaceEntry> entries=new ArrayList<>();int aiIndex=0;
                for(int lane=0;lane<8;lane++){
                    if(lane==candidateLane)entries.add(componentEntry(strategy,mode,"component:"+strategy+":"+raceIndex,lane));
                    else{
                        AiTurtleProfile profile=AiTurtleCatalog.all().get(Math.floorMod(raceIndex*5+aiIndex++,AiTurtleCatalog.all().size()));
                        entries.add(RaceEntry.ai(profile,TurtleLeague.OPEN,seed^lane*997L,lane));
                    }
                }
                RaceSimulation race=new RaceSimulation(course,weather,seed,entries);
                while(!race.complete())race.tick();
                RaceEntry candidate=entries.get(candidateLane);
                ranks[mode]+=race.standings().indexOf(candidate)+1;
            }
        }
        for(int i=0;i<ranks.length;i++)ranks[i]/=races;
        double activeGain=ranks[0]-ranks[1],passivesGain=ranks[0]-ranks[2],companionGain=ranks[0]-ranks[3];
        double fullGain=ranks[0]-ranks[4];
        return new CombinationResult(races,ranks[0],ranks[1],ranks[2],ranks[3],ranks[4],activeGain,
                passivesGain,companionGain,fullGain,fullGain-activeGain-passivesGain-companionGain);
    }

    /** Modes: 0 baseline, 1 active only, 2 passives only, 3 companion only, 4 full loadout. */
    private static RaceEntry componentEntry(TurtleStrategy strategy,int mode,String id,int lane){
        Loadout loadout=LOADOUTS.get(strategy);
        ActiveSkill active=mode==1||mode==4?loadout.active():ActiveSkill.UNTURNED_HEART;
        List<String> passives=mode==2||mode==4?loadout.passives():List.of();
        RaceEntry entry=RaceEntry.validation(id,loadout.stats(),active,passives,strategy,lane);
        if(mode==3||mode==4)entry.setValidationCompanion(loadout.companion());
        if(mode==0||mode==2||mode==3)entry.useActive();
        return entry;
    }

    private static RaceEntry specialist(TurtleStrategy strategy,Loadout loadout,String id,int lane){
        RaceEntry entry=RaceEntry.validation(id,loadout.stats(),loadout.active(),loadout.passives(),strategy,lane);
        entry.setValidationCompanion(loadout.companion());return entry;
    }

    private static Map<String,Integer> ranks(RaceSimulation race){
        Map<String,Integer> result=new LinkedHashMap<>();
        for(int i=0;i<race.standings().size();i++)result.put(race.standings().get(i).entryId(),i+1);
        return result;
    }
}
