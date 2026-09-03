package com.yoiko.core.turtle.race;

import com.yoiko.core.registry.YoikoEntities;
import com.yoiko.core.turtle.ActiveSkill;
import com.yoiko.core.turtle.entity.RaceTurtleEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Operator preview: a sample turtle crosses the viewer from left to right while using a skill. */
public final class TurtleSkillEffectPreviewManager {
    private static final int PASS_TICKS=80;
    private static final int ACTIVATE_TICK=18;
    private static final int EFFECT_END_TICK=68;
    private static final double HALF_DISTANCE=4.5;
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();

    private TurtleSkillEffectPreviewManager(){ }

    public static void start(ServerPlayer viewer,ActiveSkill skill){start(viewer,List.of(skill));}
    public static void startAll(ServerPlayer viewer){start(viewer,List.of(ActiveSkill.values()));}

    public static boolean stop(UUID viewerId){
        Session removed=SESSIONS.remove(viewerId);
        if(removed==null)return false;
        removed.discard();
        return true;
    }

    public static void tick(MinecraftServer server){
        Iterator<Map.Entry<UUID,Session>> iterator=SESSIONS.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<UUID,Session> entry=iterator.next();
            ServerPlayer viewer=server.getPlayerList().getPlayer(entry.getKey());
            if(viewer==null||entry.getValue().tick(viewer)){
                entry.getValue().discard();
                iterator.remove();
            }
        }
    }

    private static void start(ServerPlayer viewer,List<ActiveSkill> skills){
        stop(viewer.getUUID());
        Vec3 forward=viewer.getLookAngle().multiply(1,0,1);
        if(forward.lengthSqr()<.001)forward=new Vec3(0,0,1);
        forward=forward.normalize();
        Vec3 right=new Vec3(-forward.z,0,forward.x);
        Vec3 center=viewer.position().add(forward.scale(4.5)).add(0,.08,0);
        Session session=new Session(viewer.serverLevel(),viewer.getUUID(),center,right,new ArrayList<>(skills));
        session.spawn();
        SESSIONS.put(viewer.getUUID(),session);
    }

    private static final class Session {
        private final ServerLevel level;
        private final UUID viewerId;
        private final Vec3 center;
        private final Vec3 right;
        private final List<ActiveSkill> skills;
        private UUID turtleId;
        private int skillIndex;
        private int tick;

        private Session(ServerLevel level,UUID viewerId,Vec3 center,Vec3 right,List<ActiveSkill> skills){
            this.level=level;this.viewerId=viewerId;this.center=center;this.right=right;this.skills=skills;
        }

        private void spawn(){
            RaceTurtleEntity turtle=YoikoEntities.RACE_TURTLE.get().create(level);
            if(turtle==null)throw new IllegalStateException("Could not create turtle effect preview entity");
            turtle.setAppearance("coral");
            turtle.setBodyAppearance("natural");
            turtle.setOwnerId(viewerId);
            turtle.setCustomNameVisible(true);
            level.addFreshEntity(turtle);
            turtleId=turtle.getUUID();
            resetPass(turtle);
        }

        private boolean tick(ServerPlayer viewer){
            if(viewer.serverLevel()!=level)return true;
            RaceTurtleEntity turtle=turtle();
            if(turtle==null)return true;
            ActiveSkill skill=skills.get(skillIndex);
            double progress=Math.max(0,Math.min(1,(tick-4)/72.0));
            double eased=progress*progress*(3-2*progress);
            Vec3 position=center.add(right.scale((eased*2-1)*HALF_DISTANCE));
            float yaw=(float)Math.toDegrees(Math.atan2(-right.x,right.z));
            turtle.moveTo(position.x,position.y,position.z,yaw,0);
            face(turtle,yaw);
            turtle.setDeltaMovement(0,0,0);
            turtle.setActiveSkillHighlighted(tick>=ACTIVATE_TICK&&tick<=EFFECT_END_TICK);
            if(tick==ACTIVATE_TICK){
                TurtleSkillEffectRenderer.emitActivation(turtle,skill,4,this::emit);
                level.playSound(null,turtle.blockPosition(),TurtleSkillEffectRenderer.sound(skill),SoundSource.PLAYERS,.75F,1.08F);
            }
            if(tick>ACTIVATE_TICK&&tick<=EFFECT_END_TICK)TurtleSkillEffectRenderer.emitTrail(turtle,skill,tick,this::emit);
            tick++;
            if(tick<PASS_TICKS)return false;
            skillIndex++;
            if(skillIndex>=skills.size())return true;
            resetPass(turtle);
            return false;
        }

        private void resetPass(RaceTurtleEntity turtle){
            tick=0;
            ActiveSkill skill=skills.get(skillIndex);
            turtle.setCustomName(Component.translatable("yoiko_core.turtle.command.effect_preview.entity_name",
                    Component.translatable("yoiko_core.turtle.skill.active."+skill.id()+".name")));
            Vec3 start=center.add(right.scale(-HALF_DISTANCE));
            float yaw=(float)Math.toDegrees(Math.atan2(-right.x,right.z));
            turtle.moveTo(start.x,start.y,start.z,yaw,0);
            face(turtle,yaw);
            turtle.setActiveSkillHighlighted(false);
        }

        private void emit(net.minecraft.core.particles.ParticleOptions particle,double x,double y,double z,int count,
                          double offsetX,double offsetY,double offsetZ,double speed,boolean essential){
            level.sendParticles(particle,x,y,z,count,offsetX,offsetY,offsetZ,speed);
        }

        private RaceTurtleEntity turtle(){
            return turtleId==null?null:level.getEntity(turtleId) instanceof RaceTurtleEntity turtle?turtle:null;
        }

        private void discard(){RaceTurtleEntity turtle=turtle();if(turtle!=null)turtle.discard();turtleId=null;}

        private static void face(RaceTurtleEntity turtle,float yaw){
            turtle.setYRot(yaw);turtle.setYHeadRot(yaw);turtle.yBodyRot=yaw;
            turtle.yRotO=yaw;turtle.yHeadRotO=yaw;turtle.yBodyRotO=yaw;
        }
    }
}
