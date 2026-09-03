package com.yoiko.core.turtle.race;

import com.yoiko.core.turtle.TurtleLeague;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record TurtleWeeklyResult(String weekKey,TurtleLeague league,UUID playerId,int rank,long finishMillis,long createdAt){
    public int points(){return switch(rank){case 1->100;case 2->75;case 3->60;case 4->48;case 5->40;case 6->32;case 7->26;default->20;};}
    public CompoundTag save(){CompoundTag tag=new CompoundTag();tag.putString("weekKey",weekKey);tag.putString("league",league.name());tag.putUUID("playerId",playerId);tag.putInt("rank",rank);tag.putLong("finishMillis",finishMillis);tag.putLong("createdAt",createdAt);return tag;}
    public static TurtleWeeklyResult load(CompoundTag tag){return new TurtleWeeklyResult(tag.getString("weekKey"),TurtleLeague.valueOf(tag.getString("league")),tag.getUUID("playerId"),Math.max(1,Math.min(8,tag.getInt("rank"))),Math.max(0,tag.getLong("finishMillis")),tag.getLong("createdAt"));}
}
