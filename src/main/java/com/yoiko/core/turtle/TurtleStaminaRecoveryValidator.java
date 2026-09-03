package com.yoiko.core.turtle;

import com.yoiko.core.turtle.race.RaceEntry;
import com.yoiko.core.turtle.race.RaceSimulation;
import com.yoiko.core.turtle.race.TurtleCourse;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Deterministic proof that low innate stamina with no stamina training visibly needs breath breaks. */
public final class TurtleStaminaRecoveryValidator {
    public record ProfileResult(int races,double breathingRacePercent,double averageBreaths,int maximumBreaths,
                                double averageRecoveredStamina,double averageFinishStamina,double averageRank){}
    public record PassiveResult(int races,double timeBenefitPercent,double rankBenefit,double finishStaminaDifference){}
    public record Report(Map<TurtleStrategy,ProfileResult> untrainedLowStamina,
                         Map<TurtleStrategy,ProfileResult> recommended,PassiveResult breathingSwitch,long elapsedMillis){}

    private static final TurtleStats LOW_UNTRAINED = new TurtleStats(88,28,84,60,68);
    private static final TurtleStats NEUTRAL_OPPONENT = new TurtleStats(70,70,70,59,59);

    private TurtleStaminaRecoveryValidator(){}

    public static void main(String[] args){int races=args.length>0?Integer.parseInt(args[0]):96;System.out.println(validate(races));}

    public static Report validate(int races){long started=System.currentTimeMillis();
        Map<TurtleStrategy,ProfileResult> low=new EnumMap<>(TurtleStrategy.class),recommended=new EnumMap<>(TurtleStrategy.class);
        for(TurtleStrategy strategy:TurtleStrategy.values()){
            ProfileResult weak=runProfile(strategy,LOW_UNTRAINED,races,0x10A5_0000L);low.put(strategy,weak);
            TurtleStats target=TurtleGuidance.targetStats(TurtleRarity.RARE,strategy,ActiveSkill.UNTURNED_HEART,List.of(),null);
            ProfileResult normal=runProfile(strategy,target,races,0x6EC0_0000L);recommended.put(strategy,normal);
            if(weak.breathingRacePercent()<55||weak.averageBreaths()<.65)
                throw new IllegalStateException("Low innate, untrained stamina does not catch breath often enough: "+strategy+" "+weak);
            if(weak.averageBreaths()>1.0||weak.maximumBreaths()>1)
                throw new IllegalStateException("The once-per-race breathing rule was violated: "+strategy+" "+weak);
            if(weak.breathingRacePercent()<normal.breathingRacePercent()+25)
                throw new IllegalStateException("Stamina training does not sufficiently reduce breathing: "+strategy+" low="+weak+" normal="+normal);
        }
        PassiveResult passive=runBreathingSwitch(races);
        if(passive.timeBenefitPercent()<.15&&passive.rankBenefit()<.15)
            throw new IllegalStateException("Breathing Switch is not impactful in its intended low-stamina case: "+passive);
        return new Report(Map.copyOf(low),Map.copyOf(recommended),passive,System.currentTimeMillis()-started);
    }

    private static ProfileResult runProfile(TurtleStrategy strategy,TurtleStats stats,int races,long baseSeed){
        int breathingRaces=0,totalBreaths=0,maxBreaths=0;double recovered=0,finishStamina=0,rank=0;
        for(int raceIndex=0;raceIndex<races;raceIndex++){
            long seed=baseSeed+strategy.ordinal()*10_007L+raceIndex*263L;
            TurtleCourse course=TurtleCourse.generate(BlockPos.ZERO,seed,(raceIndex&1)==0);
            int candidateLane=Math.floorMod(raceIndex*3,8);List<RaceEntry> entries=new ArrayList<>();
            RaceEntry candidate=RaceEntry.validation("stamina-profile:"+strategy+":"+raceIndex,stats,ActiveSkill.UNTURNED_HEART,List.of(),strategy,candidateLane);entries.add(candidate);
            for(int slot=1;slot<8;slot++){
                RaceEntry opponent=RaceEntry.validation("stamina-opponent:"+strategy+":"+raceIndex+":"+slot,NEUTRAL_OPPONENT,ActiveSkill.UNTURNED_HEART,List.of(),TurtleStrategy.values()[Math.floorMod(slot+raceIndex,4)],Math.floorMod(candidateLane+slot,8));entries.add(opponent);
            }
            RaceSimulation race=new RaceSimulation(course,TurtleWeather.CLEAR,seed,entries);while(!race.complete())race.tick();
            int breaths=candidate.breathingCount();if(breaths>0)breathingRaces++;totalBreaths+=breaths;maxBreaths=Math.max(maxBreaths,breaths);recovered+=candidate.breathingRecovery()*100;finishStamina+=candidate.stamina()*100;rank+=race.standings().indexOf(candidate)+1;
        }
        return new ProfileResult(races,breathingRaces*100.0/races,totalBreaths/(double)races,maxBreaths,recovered/races,finishStamina/races,rank/races);
    }

    private static PassiveResult runBreathingSwitch(int races){double time=0,rank=0,stamina=0;
        for(int raceIndex=0;raceIndex<races;raceIndex++){
            long seed=0xB2EA_0000L+raceIndex*277L;int lane=Math.floorMod(raceIndex*3,8);
            List<RaceEntry> with=field("breathing-passive:"+raceIndex,LOW_UNTRAINED,TurtleStrategy.STEADY,lane,List.of("breathing_switch"),raceIndex);
            List<RaceEntry> without=field("breathing-passive:"+raceIndex,LOW_UNTRAINED,TurtleStrategy.STEADY,lane,List.of(),raceIndex);
            RaceSimulation boosted=new RaceSimulation(TurtleCourse.generate(BlockPos.ZERO,seed,(raceIndex&1)==0),TurtleWeather.CLEAR,seed,with);
            RaceSimulation baseline=new RaceSimulation(TurtleCourse.generate(BlockPos.ZERO,seed,(raceIndex&1)==0),TurtleWeather.CLEAR,seed,without);
            while(!boosted.complete())boosted.tick();while(!baseline.complete())baseline.tick();RaceEntry a=with.getFirst(),b=without.getFirst();
            double baseTime=b.finishTick()+b.finishFraction(),boostedTime=a.finishTick()+a.finishFraction();time+=(baseTime-boostedTime)/baseTime*100;rank+=baseline.standings().indexOf(b)-boosted.standings().indexOf(a);stamina+=(a.stamina()-b.stamina())*100;
        }
        return new PassiveResult(races,time/races,rank/races,stamina/races);
    }

    private static List<RaceEntry> field(String id,TurtleStats stats,TurtleStrategy strategy,int candidateLane,List<String> passives,int raceIndex){List<RaceEntry> entries=new ArrayList<>();entries.add(RaceEntry.validation(id+":candidate",stats,ActiveSkill.UNTURNED_HEART,passives,strategy,candidateLane));for(int slot=1;slot<8;slot++)entries.add(RaceEntry.validation(id+":"+slot,NEUTRAL_OPPONENT,ActiveSkill.UNTURNED_HEART,List.of(),TurtleStrategy.values()[Math.floorMod(slot+raceIndex,4)],Math.floorMod(candidateLane+slot,8)));return entries;}
}
