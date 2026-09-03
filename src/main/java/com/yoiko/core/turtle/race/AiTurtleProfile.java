package com.yoiko.core.turtle.race;

import com.yoiko.core.turtle.ActiveSkill;
import com.yoiko.core.turtle.TurtleLeague;
import com.yoiko.core.turtle.TurtleStat;
import com.yoiko.core.turtle.TurtleStats;
import com.yoiko.core.turtle.TurtleStrategy;
import java.util.List;

public record AiTurtleProfile(String id, TurtleStrategy strategy,
                              TurtleStats baseStats, double coefficient, ActiveSkill activeSkill,
                              List<String> passives, String appearance) {
    public TurtleStats statsFor(TurtleLeague league, long seed) {
        int low = switch (league) {
            case CORAL -> 272;
            case CURRENT -> 290;
            case ABYSS -> 306;
            case OPEN -> 320;
            case TRAINING_D -> 260;
        };
        int high = switch (league) {
            case CORAL -> 284;
            case CURRENT -> 301;
            case ABYSS -> 316;
            case OPEN -> 328;
            case TRAINING_D -> 267;
        };
        int target = low + Math.floorMod((int) (seed ^ id.hashCode()), high - low + 1);
        target = (int) Math.round(low + (target - low) * coefficient);
        if(id.equals("water_scale"))target=Math.max(low-18,target-18);
        int baseTotal = baseStats.total();
        int[] values = new int[5];
        int used = 0;
        for (TurtleStat stat : TurtleStat.values()) {
            values[stat.ordinal()] = (int) Math.floor(baseStats.get(stat) * (double) target / baseTotal);
            used += values[stat.ordinal()];
        }
        for (int i = 0; used < target; i++, used++) values[i % values.length]++;
        return new TurtleStats(values[0], values[1], values[2], values[3], values[4]);
    }

    public int activePassiveCount(TurtleLeague league) {
        return switch (league) {
            case CORAL -> 2;
            case CURRENT -> 3;
            case ABYSS, OPEN -> 4;
            case TRAINING_D -> 1;
        };
    }

    public TurtleStats statsForTimeTrial(TurtleLeague league) {
        int base = switch (league) { case TRAINING_D -> 260; case CORAL -> 277; case CURRENT -> 295; case ABYSS -> 311; case OPEN -> 324; };
        int target=base+(int)Math.round((coefficient-.98)*100);
        if(id.equals("water_scale"))target-=18;
        int[] values=new int[5];double[] fractions=new double[5];int used=0;
        for(TurtleStat stat:TurtleStat.values()){double raw=baseStats.get(stat)*(double)target/320.0;values[stat.ordinal()]=(int)Math.floor(raw);fractions[stat.ordinal()]=raw-values[stat.ordinal()];used+=values[stat.ordinal()];}
        while(used<target){int best=0;for(int i=1;i<5;i++)if(fractions[i]>fractions[best])best=i;values[best]++;fractions[best]=-1;used++;}
        return new TurtleStats(values[0],values[1],values[2],values[3],values[4]);
    }
}
