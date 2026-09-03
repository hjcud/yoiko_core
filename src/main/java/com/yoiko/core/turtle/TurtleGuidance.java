package com.yoiko.core.turtle;

import java.util.List;

/** Shared, read-only UI guidance. It never changes training or race values. */
public final class TurtleGuidance {
    private static final int MAX_TRAINING = 24;
    private static final double[][] STRATEGY_TARGET_RATIOS = {
            // speed, stamina, power, calm, navigation
            {.255,.200,.225,.140,.180}, // FRONT
            {.230,.220,.200,.180,.170}, // STEADY
            {.215,.210,.215,.175,.185}, // FOLLOW
            {.240,.195,.235,.140,.190}  // CLOSER
    };
    private static final double[][] RECOVERY_REDISTRIBUTION = {
            {.45,0,.35,.00,.20},
            {.25,0,.25,.25,.25},
            {.35,0,.35,.00,.30},
            {.40,0,.40,.00,.20}
    };
    private static final double[][] STRATEGY_IMPORTANCE = {
            {1.25,1.00,1.15,.80,.95},
            {1.10,1.10,.90,1.10,.85},
            {1.15,1.05,1.15,.82,1.08},
            {1.22,.82,1.25,.78,1.05}
    };

    private TurtleGuidance() { }

    public static String recommendedTraining(TurtleData turtle){
        if(turtle.trainingCount()>=MAX_TRAINING)return "COMPLETE";
        TurtleTrainingType best=recommendedTraining(turtle.stats(),turtle.rarity(),turtle.strategy(),
                turtle.trainingCount(),turtle.activeSkill(),turtle.passives().subList(0,turtle.activePassiveSlots()),
                turtle.companion());
        return best==null?"NONE":best.name();
    }

    /** Applies the same recommendation repeatedly without mutating the turtle, for the Shift UI preview. */
    public static TurtleStats projectedFinalStats(TurtleData turtle){
        TurtleStats projected=turtle.stats().copy();
        List<String> activePassives=turtle.passives().subList(0,turtle.activePassiveSlots());
        for(int session=turtle.trainingCount();session<MAX_TRAINING;session++){
            TurtleTrainingType next=recommendedTraining(projected,turtle.rarity(),turtle.strategy(),session,
                    turtle.activeSkill(),activePassives,turtle.companion());
            if(next==null)break;
            projected.add(next.primary(),2);
            projected.add(next.secondary(),1);
        }
        return projected;
    }

    /** Final stat destination used by both the planner and deterministic balance tests. */
    public static TurtleStats targetStats(TurtleRarity rarity,TurtleStrategy strategy,ActiveSkill active,
                                          List<String> passives,TurtleCompanion companion){
        double[] ratios=STRATEGY_TARGET_RATIOS[strategy.ordinal()];
        double[] target=new double[ratios.length];
        for(int i=0;i<target.length;i++)target[i]=rarity.finalBudget()*ratios[i];
        double recoveryCredit=recoveryCredit(active,passives,companion);
        // A closer intentionally exceeds ordinary pace through the final half. Recovery effects do
        // not replace endurance point-for-point there; doing so produced fast recommendations that
        // reached the line on 3~4% stamina and dominated generated-build fields.
        if(strategy==TurtleStrategy.CLOSER&&recoveryCredit>0)recoveryCredit*=.33;
        target[TurtleStat.STAMINA.ordinal()]-=recoveryCredit;
        double[] redistribution=RECOVERY_REDISTRIBUTION[strategy.ordinal()];
        for(int i=0;i<target.length;i++)target[i]+=recoveryCredit*redistribution[i];
        int[] rounded=largestRemainder(target,rarity.finalBudget(),rarity.finalStatCap());
        return new TurtleStats(rounded[0],rounded[1],rounded[2],rounded[3],rounded[4]);
    }

    static TurtleTrainingType recommendedTraining(TurtleStats stats,TurtleRarity rarity,TurtleStrategy strategy,
                                                   int trainingCount,ActiveSkill active,List<String> passives,
                                                   TurtleCompanion companion){
        int remaining=MAX_TRAINING-trainingCount;
        if(remaining<=0)return null;
        TurtleStats target=targetStats(rarity,strategy,active,passives,companion);
        int[] bestCounts=null;double bestError=Double.POSITIVE_INFINITY;
        // A complete 24-session plan is small enough to search exactly (at most 20,475 mixes).
        // This avoids a greedy recommendation repeatedly overtraining stamina because it happens to
        // be low at the current step while ignoring the secondary stats supplied by later sessions.
        for(int speed=0;speed<=remaining;speed++)for(int endurance=0;endurance<=remaining-speed;endurance++)
            for(int power=0;power<=remaining-speed-endurance;power++)
                for(int dodge=0;dodge<=remaining-speed-endurance-power;dodge++){
                    int course=remaining-speed-endurance-power-dodge;
                    int[] counts={speed,endurance,power,dodge,course};
                    int[] projected=project(stats,counts);
                    boolean valid=true;for(int value:projected)if(value>rarity.finalStatCap()){valid=false;break;}
                    if(!valid)continue;
                    double error=fitError(projected,target,strategy);
                    if(error<bestError-1.0e-9){bestError=error;bestCounts=counts;}
                }
        if(bestCounts==null)return immediateFallback(stats,rarity,strategy,target);
        TurtleTrainingType result=null;double urgency=Double.NEGATIVE_INFINITY;
        for(TurtleTrainingType type:TurtleTrainingType.values()){
            if(bestCounts[type.ordinal()]<=0||!canApply(stats,rarity,type))continue;
            double value=bestCounts[type.ordinal()]/(double)remaining*2.0
                    +immediateImprovement(stats,target,strategy,type);
            if(value>urgency){urgency=value;result=type;}
        }
        return result==null?immediateFallback(stats,rarity,strategy,target):result;
    }

    private static int[] project(TurtleStats stats,int[] counts){
        int[] result=stats.copyValues();
        TurtleTrainingType[] types=TurtleTrainingType.values();
        for(int i=0;i<counts.length;i++){
            result[types[i].primary().ordinal()]+=counts[i]*2;
            result[types[i].secondary().ordinal()]+=counts[i];
        }
        return result;
    }

    private static double fitError(int[] values,TurtleStats target,TurtleStrategy strategy){
        double result=0;double[] importance=STRATEGY_IMPORTANCE[strategy.ordinal()];
        for(TurtleStat stat:TurtleStat.values()){
            double difference=(values[stat.ordinal()]-target.get(stat))/10.0;
            // Overshooting a target is undesirable, but less damaging than leaving a defining stat
            // short when the turtle's hatch distribution makes the exact destination impossible.
            double direction=difference>0?.72:1.0;
            result+=difference*difference*importance[stat.ordinal()]*direction;
        }
        return result;
    }

    private static double immediateImprovement(TurtleStats stats,TurtleStats target,TurtleStrategy strategy,
                                               TurtleTrainingType type){
        int[] before=stats.copyValues(),after=stats.copyValues();
        after[type.primary().ordinal()]+=2;after[type.secondary().ordinal()]++;
        return fitError(before,target,strategy)-fitError(after,target,strategy);
    }

    private static TurtleTrainingType immediateFallback(TurtleStats stats,TurtleRarity rarity,
                                                        TurtleStrategy strategy,TurtleStats target){
        TurtleTrainingType best=null;double bestScore=Double.NEGATIVE_INFINITY;
        for(TurtleTrainingType type:TurtleTrainingType.values()){
            if(!canApply(stats,rarity,type))continue;
            double score=immediateImprovement(stats,target,strategy,type);
            if(score>bestScore){bestScore=score;best=type;}
        }
        return best;
    }

    private static boolean canApply(TurtleStats stats,TurtleRarity rarity,TurtleTrainingType type){
        return stats.get(type.primary())+2<=rarity.finalStatCap()
                &&stats.get(type.secondary())+1<=rarity.finalStatCap();
    }

    private static double recoveryCredit(ActiveSkill active,List<String> passives,TurtleCompanion companion){
        double value=switch(active){
            case DEEP_BREATH->5;case PUDDLE_SURF->3;case SURFACE_CHAIN->2;case SAND_SPRINT->1;default->0;
        };
        value+=switch(active){case LIMIT_SPRINT->-4;case SURGING_SPRAY->-2;case RECKLESS_PASS->-3;default->0;};
        for(String passive:passives)value+=switch(passive){
            case "long_breath"->3;case "steady_breath","leaders_ease","steady_cruise","final_savings",
                    "draft_posture","mist_breathing","long_course_pace"->2;
            case "calm_recovery","adaptive_shell","breathing_switch"->3;case "early_rhythm"->1;default->0;
        };
        if(companion!=null)value+=switch(companion){case AXOLOTL->3;case DOLPHIN,ARMADILLO->1;default->0;};
        return Math.max(-8,Math.min(12,value));
    }

    private static int[] largestRemainder(double[] values,int total,int cap){
        int[] result=new int[values.length];double[] remainder=new double[values.length];int used=0;
        for(int i=0;i<values.length;i++){result[i]=Math.min(cap,(int)Math.floor(values[i]));remainder[i]=values[i]-result[i];used+=result[i];}
        while(used<total){int best=-1;double largest=-1;for(int i=0;i<result.length;i++)if(result[i]<cap&&remainder[i]>largest){largest=remainder[i];best=i;}if(best<0)throw new IllegalStateException("Unable to distribute turtle guidance target");result[best]++;remainder[best]=-1;used++;}
        while(used>total){int best=-1;double smallest=Double.MAX_VALUE;for(int i=0;i<result.length;i++)if(result[i]>0&&remainder[i]<smallest){smallest=remainder[i];best=i;}result[best]--;used--;}
        return result;
    }
}
