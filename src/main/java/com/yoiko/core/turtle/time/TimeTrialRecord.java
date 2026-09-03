package com.yoiko.core.turtle.time;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record TimeTrialRecord(String key,long finishMillis,long achievedAt,List<GhostSample> samples) {
    public record GhostSample(int progressU16,byte laneOffset,short hopHeightU8){
        public GhostSample { progressU16=Math.max(0,Math.min(65_535,progressU16));hopHeightU8=(short)Math.max(0,Math.min(255,hopHeightU8)); }
        CompoundTag save(){CompoundTag t=new CompoundTag();t.putInt("p",progressU16);t.putByte("l",laneOffset);t.putShort("h",hopHeightU8);return t;}
        static GhostSample load(CompoundTag t){return new GhostSample(t.getInt("p"),t.getByte("l"),t.getShort("h"));}
    }
    public TimeTrialRecord { samples=List.copyOf(samples.subList(0,Math.min(750,samples.size()))); }
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("key",key);t.putLong("finishMillis",finishMillis);t.putLong("achievedAt",achievedAt);ListTag list=new ListTag();samples.forEach(v->list.add(v.save()));t.put("samples",list);return t;}
    public static TimeTrialRecord load(CompoundTag t){List<GhostSample> values=new ArrayList<>();ListTag list=t.getList("samples",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(750,list.size());i++)values.add(GhostSample.load(list.getCompound(i)));return new TimeTrialRecord(t.getString("key"),t.getLong("finishMillis"),t.getLong("achievedAt"),values);}
}
