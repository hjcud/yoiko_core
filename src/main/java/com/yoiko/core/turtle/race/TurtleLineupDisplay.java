package com.yoiko.core.turtle.race;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.decoration.ArmorStand;
import com.yoiko.core.registry.YoikoEntities;
import com.yoiko.core.turtle.entity.RaceTurtleEntity;

/** Lightweight world preview used during registration and betting. */
public final class TurtleLineupDisplay {
    public record Preview(Component name,boolean ai,String appearance,String bodyAppearance,UUID ownerId){ }
    private final ServerLevel level;
    private final TurtleCourse course;
    private final double baseY;
    private final List<UUID> turtles=new ArrayList<>();
    private UUID labelId;

    public TurtleLineupDisplay(ServerLevel level,TurtleCourse course,double baseY){this.level=level;this.course=course;this.baseY=baseY;}

    public void show(List<Preview> previews,Component status){
        discardTurtles();
        TurtleCourse.Sample sample=course.sampleAt(0);
        for(int lane=0;lane<Math.min(8,previews.size());lane++){
            Preview preview=previews.get(lane);RaceTurtleEntity turtle=YoikoEntities.RACE_TURTLE.get().create(level);if(turtle==null)continue;
            turtle.setAppearance(preview.appearance());turtle.setBodyAppearance(preview.bodyAppearance());turtle.setOwnerId(preview.ownerId());
            var displayName=Component.literal("["+(lane+1)+"] ").withStyle(laneColor(lane));
            if(preview.ai())displayName.append(Component.translatable("yoiko_core.turtle.ui.ai_prefix").withStyle(ChatFormatting.GRAY));
            turtle.setCustomName(displayName.append(preview.name().copy().withStyle(ChatFormatting.WHITE)));turtle.setCustomNameVisible(true);
            double offset=RaceEntry.laneCenterOffset(lane);TurtleCourse.TrackPoint point=course.startGridPoint(-TurtleCourse.START_WAIT_DISTANCE,offset);turtle.moveTo(point.x(),baseY+sample.surface().visualYOffset(),point.z(),yaw(point),0);faceCourseDirection(turtle,yaw(point));
            level.addFreshEntity(turtle);turtles.add(turtle.getUUID());
        }
        updateStatus(status);
    }

    public void updateStatus(Component status){
        ArmorStand label=label();
        if(label==null){label=EntityType.ARMOR_STAND.create(level);if(label==null)return;net.minecraft.nbt.CompoundTag tag=label.saveWithoutId(new net.minecraft.nbt.CompoundTag());tag.putBoolean("Invisible",true);tag.putBoolean("Marker",true);tag.putBoolean("NoGravity",true);label.load(tag);label.setInvulnerable(true);label.setCustomNameVisible(true);TurtleCourse.Sample sample=course.sampleAt(0);label.moveTo(sample.x(),baseY+1.65,sample.z(),0,0);level.addFreshEntity(label);labelId=label.getUUID();}
        label.setCustomName(status);
    }

    public void discard(){discardTurtles();ArmorStand label=label();if(label!=null)label.discard();labelId=null;}
    private void discardTurtles(){for(UUID id:turtles){Entity entity=level.getEntity(id);if(entity!=null)entity.discard();}turtles.clear();}
    private ArmorStand label(){if(labelId==null)return null;Entity entity=level.getEntity(labelId);return entity instanceof ArmorStand stand?stand:null;}
    private static float yaw(TurtleCourse.Sample sample){return (float)(Math.toDegrees(Math.atan2(sample.tangentZ(),sample.tangentX()))-90);}
    private static float yaw(TurtleCourse.TrackPoint point){return (float)(Math.toDegrees(Math.atan2(point.tangentZ(),point.tangentX()))-90);}
    private static void faceCourseDirection(RaceTurtleEntity turtle,float yaw){turtle.setYRot(yaw);turtle.setYHeadRot(yaw);turtle.yBodyRot=yaw;turtle.yRotO=yaw;turtle.yHeadRotO=yaw;turtle.yBodyRotO=yaw;}
    private static ChatFormatting laneColor(int lane){return switch(lane){case 0->ChatFormatting.RED;case 1->ChatFormatting.GOLD;case 2->ChatFormatting.YELLOW;case 3->ChatFormatting.GREEN;case 4->ChatFormatting.AQUA;case 5->ChatFormatting.BLUE;case 6->ChatFormatting.LIGHT_PURPLE;default->ChatFormatting.WHITE;};}
}
