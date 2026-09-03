package com.yoiko.core.client.turtle;

import com.yoiko.core.network.TurtleTimeTrialGhostPayload;
import com.yoiko.core.registry.YoikoEntities;
import com.yoiko.core.turtle.entity.TurtleGhostEntity;
import com.yoiko.core.turtle.race.TurtleCourse;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class TurtleTimeTrialGhostClient {
    /** Matches the server's 7.5 second time-trial start sequence. */
    private static final int COUNTDOWN_TICKS=150;
    public static final TurtleTimeTrialGhostClient INSTANCE=new TurtleTimeTrialGhostClient();
    private TurtleGhostEntity entity;private TurtleTimeTrialGhostPayload payload;private TurtleCourse course;private int ticks;
    public void start(TurtleTimeTrialGhostPayload value){clear();if(value.samples().isEmpty())return;Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;payload=value;course=TurtleCourse.preset(new BlockPos(value.centerX(),value.baseY(),value.centerZ()),value.presetIndex());entity=new TurtleGhostEntity(YoikoEntities.TURTLE_GHOST.get(),mc.level);entity.setAge(-24_000);entity.setGlowingTag(true);entity.setCustomName(net.minecraft.network.chat.Component.translatable("yoiko_core.turtle.time_trial.personal_best_ghost"));entity.setCustomNameVisible(true);mc.level.addEntity(entity);ticks=-COUNTDOWN_TICKS;moveToSample(0.0);}
    @SubscribeEvent public void tick(ClientTickEvent.Post event){if(entity==null||payload==null||Minecraft.getInstance().isPaused())return;if(++ticks<0)return;double samplePosition=ticks/4.0;int a=(int)Math.floor(samplePosition);if(a>=payload.samples().size()-1){clear();return;}moveToSample(samplePosition);}
    private void moveToSample(double samplePosition){int a=Math.max(0,(int)Math.floor(samplePosition));int b=Math.min(a+1,payload.samples().size()-1);double t=Math.max(0.0,samplePosition-a);var sa=payload.samples().get(a);var sb=payload.samples().get(b);double progress=(sa.progressU16()+(sb.progressU16()-sa.progressU16())*t)/65_535.0*course.length();double lane=(sa.laneOffset()+(sb.laneOffset()-sa.laneOffset())*t)/127.0*2.5;double hop=(sa.hopHeightU8()+(sb.hopHeightU8()-sa.hopHeightU8())*t)/255.0*.42;TurtleCourse.Sample sample=course.sampleAt(progress);double x=sample.x()+sample.normalX()*lane,z=sample.z()+sample.normalZ()*lane;float yaw=(float)(Math.toDegrees(Math.atan2(sample.tangentZ(),sample.tangentX()))-90);entity.moveTo(x,payload.baseY()+sample.surface().visualYOffset()+hop,z,yaw,0);}
    public void clear(){if(entity!=null)entity.discard();entity=null;payload=null;course=null;ticks=0;}
}
