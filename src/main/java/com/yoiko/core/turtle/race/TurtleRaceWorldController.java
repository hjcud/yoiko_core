package com.yoiko.core.turtle.race;

import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.network.TurtleRaceHudPayload;
import com.yoiko.core.turtle.TurtleSurface;
import com.yoiko.core.turtle.TurtleWeather;
import com.yoiko.core.turtle.arena.TurtleArenaManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Turtle;
import com.yoiko.core.registry.YoikoEntities;
import com.yoiko.core.turtle.entity.RaceTurtleEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

public final class TurtleRaceWorldController {
    public static final int START_ANNOUNCEMENT_INTERVAL_TICKS=40;
    public static final int START_COUNTDOWN_TICK=START_ANNOUNCEMENT_INTERVAL_TICKS*3;
    public static final int COUNTDOWN_STEP_TICKS=20;
    public static final int RACE_START_TICK=START_COUNTDOWN_TICK+COUNTDOWN_STEP_TICKS*3;
    private static final ResourceLocation RAIN_WEATHER_EFFECT=YoikoServerCore.id("turtle_weather_rain");
    private static final ResourceLocation BREEZE_WEATHER_EFFECT=YoikoServerCore.id("turtle_weather_breeze");
    private static final ResourceLocation MIST_WEATHER_EFFECT=YoikoServerCore.id("turtle_weather_mist");
    private static final double WEATHER_VIEW_DISTANCE_SQR=96*96;
    private final ServerLevel level;
    private final RaceSimulation simulation;
    private final double baseY;
    private final BlockPos center;
    private final long hudSessionId;
    private final List<TurtleRaceHudPayload.SurfaceSegment> hudSurfaces;
    private final Map<String, UUID> entities = new HashMap<>();
    private final Map<String, Boolean> hoppingLastTick = new HashMap<>();
    private final Set<UUID> hudViewers = new HashSet<>();
    private long lastHudSyncTick=Long.MIN_VALUE;
    private long releaseStartedTick=-1;
    private long lastFinishLineBurst=-100;
    private long lastFinishBell=-100;

    public TurtleRaceWorldController(ServerLevel level, RaceSimulation simulation, BlockPos center) {
        this.level=level;this.simulation=simulation;this.center=center.immutable();this.baseY=center.getY();
        this.hudSessionId=(level.getGameTime()<<20)^simulation.seed()^center.asLong();
        this.hudSurfaces=buildHudSurfaces(simulation.course());
    }

    public void spawn() {
        discard();
        for(int i=0;i<simulation.entries().size();i++){
            RaceEntry entry=simulation.entries().get(i);
            RaceTurtleEntity turtle=YoikoEntities.RACE_TURTLE.get().create(level);
            if(turtle==null)throw new IllegalStateException("Could not create race turtle entity");
            turtle.setAppearance(entry.appearance());
            turtle.setBodyAppearance(entry.bodyAppearance());
            turtle.setOwnerId(entry.ownerId());
            turtle.setPersistenceRequired();
            var displayName=Component.literal("["+(i+1)+"] ").withStyle(laneColor(i));
            if(entry.ai())displayName.append(Component.translatable("yoiko_core.turtle.ui.ai_prefix").withStyle(ChatFormatting.GRAY));
            turtle.setCustomName(displayName.append(entry.displayName().copy().withStyle(ChatFormatting.WHITE)));
            turtle.setCustomNameVisible(true);
            TurtleCourse.Sample sample=simulation.course().sampleAt(0);TurtleCourse.TrackPoint point=simulation.course().startGridPoint(-TurtleCourse.START_WAIT_DISTANCE,entry.laneOffset());
            turtle.moveTo(point.x(),surfaceY(sample),point.z(),yaw(point),0);
            faceCourseDirection(turtle,yaw(point));
            level.addFreshEntity(turtle);
            entities.put(entry.entryId(),turtle.getUUID());
        }
        syncRaceHud(true);
    }

    public void tick() {
        for(RaceEntry entry:simulation.entries()){
            Entity entity=entity(entry.entryId());if(entity==null)continue;
            double releaseProgress=releaseStartedTick<0?0:Math.max(0,Math.min(1,(simulation.currentTick()-releaseStartedTick-entry.initialStartDelayTicks())/8.0));
            double waitingOffset=-TurtleCourse.START_WAIT_DISTANCE*(1-releaseProgress);
            double visualProgress=entry.visualProgress()+waitingOffset;
            TurtleCourse.Sample sample=simulation.course().sampleForWorld(Math.max(0,visualProgress));TurtleCourse.TrackPoint point=simulation.course().startGridPoint(visualProgress,entry.laneOffset());
            entity.moveTo(point.x(),surfaceY(sample)+entry.hopYOffset(),point.z(),yaw(point),0);
            if(entity instanceof RaceTurtleEntity turtle){faceCourseDirection(turtle,yaw(point));turtle.setActiveSkillHighlighted(entry.activeEffectRunning());}
            entity.setDeltaMovement(0,0,0);
            emitHopFx(entry,entity);
            emitStatusTrail(entry,entity);
        }
        syncRaceHud(false);
    }

    public void beginRace(){releaseStartedTick=simulation.currentTick();}

    private void emitHopFx(RaceEntry entry,Entity entity){
        boolean hopping=entry.hopping(),wasHopping=hoppingLastTick.getOrDefault(entry.entryId(),false);
        if(hopping&&!wasHopping){
            sendFx(ParticleTypes.CLOUD,entity.getX(),entity.getY()+.05,entity.getZ(),4,.11,.025,.11,.018,true);
            sendFx(ParticleTypes.CRIT,entity.getX(),entity.getY()+.16,entity.getZ(),3,.08,.06,.08,.025,true);
        }
        if(hopping&&simulation.currentTick()%2==0)
            sendFx(ParticleTypes.CRIT,entity.getX(),entity.getY()+.10,entity.getZ(),1,.04,.04,.04,.005,false);
        if(!hopping&&wasHopping){
            TurtleCourse.Sample landing=simulation.course().sampleAt(entry.progress());
            ParticleOptions impact=landing.surface()==com.yoiko.core.turtle.TurtleSurface.PUDDLE?ParticleTypes.SPLASH:ParticleTypes.POOF;
            sendFx(impact,entity.getX(),entity.getY()+.04,entity.getZ(),5,.12,.035,.12,.025,true);
            level.playSound(null,entity.blockPosition(),SoundEvents.TURTLE_SHAMBLE_BABY,SoundSource.PLAYERS,.35f,1.25f);
        }
        hoppingLastTick.put(entry.entryId(),hopping);
    }

    private void emitStatusTrail(RaceEntry entry,Entity entity){
        long tick=simulation.currentTick();
        if(entry.activeEffectRunning())TurtleSkillEffectRenderer.emitTrail(entity,entry.activeSkill(),tick,this::sendFx);
        if(entry.interfered()&&tick%5==0)sendFx(ParticleTypes.WITCH,entity.getX(),entity.getY()+.24,entity.getZ(),1,.06,.05,.06,.003,false);
        if(entry.accelerationPenaltyActive()&&tick%6==0)sendFx(ParticleTypes.LARGE_SMOKE,entity.getX(),entity.getY()+.10,entity.getZ(),1,.04,.02,.04,.001,false);
        if(entry.breathing()&&tick%4==0){sendFx(ParticleTypes.CLOUD,entity.getX(),entity.getY()+.12,entity.getZ(),1,.07,.04,.07,.004,false);if(tick%8==0)sendFx(ParticleTypes.BUBBLE_POP,entity.getX(),entity.getY()+.18,entity.getZ(),1,.04,.03,.04,.002,false);}
        if(!entry.interfered()&&!entry.activeEffectRunning()&&entry.passiveBoosted()&&tick%8==0)
            sendFx(ParticleTypes.HAPPY_VILLAGER,entity.getX(),entity.getY()+.10,entity.getZ(),1,.05,.025,.05,0,false);
        if(!entry.interfered()&&!entry.activeEffectRunning()&&entry.passiveSlowed()&&tick%8==0)
            sendFx(ParticleTypes.ASH,entity.getX(),entity.getY()+.14,entity.getZ(),1,.05,.03,.05,.001,false);
    }

    public void emitWeather(TurtleWeather weather,long tick){
        if(weather==TurtleWeather.CLEAR)return;
        int refreshTicks=switch(weather){
            case LIGHT_RAIN->30;
            case FOREST_MIST->80;
            default->40;
        };
        if(tick==1||tick%refreshTicks==0)emitWeatherField(weather);
        // Snowstorm renders the atmosphere around each spectator. Only small ground accents remain server-side.
        if(weather==TurtleWeather.LIGHT_RAIN&&tick%16==0){
            TurtleCourse.Sample sample=simulation.course().sampleAt(Math.floorMod(tick*23,(long)Math.max(1,simulation.course().length())));
            sendFx(ParticleTypes.SPLASH,sample.x(),surfaceY(sample)+.04,sample.z(),4,1.5,.015,1.5,.025,false);
        }
    }

    /** Lower-frequency arena atmosphere used before the starting signal. */
    public void emitPreRaceWeather(TurtleWeather weather,long tick){
        emitPreRaceWeather(level,center,weather,tick);
    }

    public static void emitPreRaceWeather(ServerLevel level,BlockPos center,TurtleWeather weather,long tick){
        if(weather==TurtleWeather.CLEAR)return;
        int refreshTicks=switch(weather){
            case LIGHT_RAIN->40;
            case FOREST_MIST->100;
            default->60;
        };
        if(tick==1||tick%refreshTicks==0)emitWeatherField(level,center,weather);
    }

    private void emitWeatherField(TurtleWeather weather){
        emitWeatherField(level,center,weather);
    }

    private static void emitWeatherField(ServerLevel level,BlockPos center,TurtleWeather weather){
        ResourceLocation effect=switch(weather){
            case LIGHT_RAIN->RAIN_WEATHER_EFFECT;
            case SEA_BREEZE->BREEZE_WEATHER_EFFECT;
            case FOREST_MIST->MIST_WEATHER_EFFECT;
            default->null;
        };
        if(effect==null)return;
        double x=center.getX()+.5,y=center.getY()+.5,z=center.getZ()+.5;
        for(var player:level.players()){
            if(player.distanceToSqr(x,y,z)>WEATHER_VIEW_DISTANCE_SQR)continue;
            new SpawnSnowstormParticlePacket(effect,player.position()).sendToPlayer(player);
        }
    }

    public void emitActive(RaceEntry entry){
        Entity entity=entity(entry.entryId());if(entity==null)return;
        TurtleSkillEffectRenderer.emitActivation(entity,entry.activeSkill(),entry.rarity().ordinal(),this::sendFx);
        for(var player:level.players())if(player.distanceToSqr(entity)<=96*96)player.playNotifySound(TurtleSkillEffectRenderer.sound(entry.activeSkill()),SoundSource.PLAYERS,.75f,1.0f+entry.rarity().ordinal()*.07f);
    }

    public void emitBreathing(RaceEntry entry,boolean started){Entity entity=entity(entry.entryId());if(entity==null)return;ParticleOptions particle=started?ParticleTypes.CLOUD:ParticleTypes.HAPPY_VILLAGER;sendFx(particle,entity.getX(),entity.getY()+.18,entity.getZ(),started?6:4,.14,.07,.14,.016,true);level.playSound(null,entity.blockPosition(),started?SoundEvents.TURTLE_SHAMBLE_BABY:SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE,SoundSource.PLAYERS,.45f,started?.72f:1.35f);}

    /** Full within 32 blocks, half-density to 80, and essential-only to 144. */
    private void sendFx(ParticleOptions particle,double x,double y,double z,int count,double ox,double oy,double oz,double speed,boolean essential){
        for(var player:level.players()){
            double d=player.distanceToSqr(x,y,z);int adjusted=d<=32*32?count:d<=80*80?Math.max(1,count/2):essential&&d<=144*144?1:0;
            if(adjusted>0)level.sendParticles(player,particle,d>32*32,x,y,z,adjusted,ox,oy,oz,speed);
        }
    }

    public void emitFinish(RaceEntry entry,int rank){
        Entity entity=entity(entry.entryId());if(entity==null)return;
        ParticleOptions particle=rank==1?ParticleTypes.FIREWORK:rank==2?ParticleTypes.END_ROD:ParticleTypes.FLAME;
        level.sendParticles(particle,entity.getX(),entity.getY()+.35,entity.getZ(),rank<=3?16:6,.30,.28,.30,.055);
        if(rank==1||simulation.currentTick()-lastFinishLineBurst>=4){TurtleCourse.Sample line=simulation.course().sampleAt(0);for(int offset=-2;offset<=2;offset++)level.sendParticles(ParticleTypes.END_ROD,line.x()+line.normalX()*offset,baseY+.10,line.z()+line.normalZ()*offset,2,.10,.04,.10,.015);lastFinishLineBurst=simulation.currentTick();}
        if(rank==1){level.playSound(null,entity.blockPosition(),SoundEvents.PLAYER_LEVELUP,SoundSource.NEUTRAL,1.0f,1.15f);lastFinishBell=simulation.currentTick();}
        else if(simulation.currentTick()-lastFinishBell>=5){level.playSound(null,entity.blockPosition(),SoundEvents.NOTE_BLOCK_BELL.value(),SoundSource.NEUTRAL,.45f,1.35f-rank*.035f);lastFinishBell=simulation.currentTick();}
    }

    public void showPhotoFinish(RaceEntry first,RaceEntry second,long deltaMillis){TurtleCourse.Sample line=simulation.course().sampleAt(0);for(int offset=-4;offset<=4;offset++)level.sendParticles(ParticleTypes.FLASH,line.x()+line.normalX()*offset,baseY+.35,line.z()+line.normalZ()*offset,1,.02,.08,.02,0);showRaceMoment(Component.translatable("yoiko_core.turtle.moment.photo_finish").withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD),Component.translatable("yoiko_core.turtle.moment.photo_finish_subtitle",first.displayName(),second.displayName(),deltaMillis).withStyle(ChatFormatting.WHITE));}

    public void emitCheer(String entryId){Entity entity=entity(entryId);if(entity==null)return;level.sendParticles(ParticleTypes.HAPPY_VILLAGER,entity.getX(),entity.getY()+.35,entity.getZ(),5,.18,.12,.18,.018);level.playSound(null,entity.blockPosition(),SoundEvents.NOTE_BLOCK_BELL.value(),SoundSource.PLAYERS,.35f,1.45f);}

    /** Operator-only diagnostic request; particles themselves are visible in-world and expire immediately. */
    public void emitRacingLineDebug(){Vector3f[] colors={new Vector3f(1,.2F,.2F),new Vector3f(1,.7F,.1F),new Vector3f(1,1,.2F),new Vector3f(.2F,1,.3F),new Vector3f(.1F,1,1),new Vector3f(.2F,.4F,1),new Vector3f(.75F,.2F,1),new Vector3f(1,1,1)};for(RaceEntry entry:simulation.entries()){int lane=Math.max(0,Math.min(7,entry.targetLane()));for(int step=0;step<5;step++){double progress=Math.min(simulation.course().length(),entry.progress()+step*1.5);TurtleCourse.TrackPoint point=simulation.course().trackPoint(progress,RaceEntry.laneCenterOffset(lane));level.sendParticles(new DustParticleOptions(colors[lane],.65F),point.x(),baseY+.34,point.z(),1,0,0,0,0);}}}

    public void showPodium(List<RaceEntry> order){hideRaceHud();for(int rank=0;rank<Math.min(3,order.size());rank++){RaceEntry entry=order.get(rank);Entity entity=entity(entry.entryId());if(entity==null)continue;int offset=rank==0?0:rank==1?-2:2;double y=baseY+(rank==0?2:1);entity.moveTo(center.getX()+offset+.5,y,center.getZ()+TurtleArenaManager.PODIUM_Z_OFFSET+.5,180,0);entity.setCustomName(Component.translatable("yoiko_core.turtle.podium.rank",rank+1,entry.displayName()).withStyle(rank==0?ChatFormatting.GOLD:rank==1?ChatFormatting.WHITE:ChatFormatting.RED));entity.setCustomNameVisible(true);}for(int rank=3;rank<order.size();rank++){RaceEntry entry=order.get(rank);Entity entity=entity(entry.entryId());if(entity!=null){entity.moveTo(center.getX()+(rank-5)*2.0+.5,baseY,center.getZ()+TurtleArenaManager.RESULT_ROW_Z_OFFSET+.5,180,0);entity.setCustomName(Component.translatable("yoiko_core.turtle.podium.rank",rank+1,entry.displayName()).withStyle(ChatFormatting.GRAY));entity.setCustomNameVisible(true);}}}

    public void showCountdown(int number){
        Component title=number==0?Component.translatable("yoiko_core.turtle.countdown.go").withStyle(ChatFormatting.GREEN):Component.literal(Integer.toString(number)).withStyle(number==1?ChatFormatting.GOLD:ChatFormatting.YELLOW);
        var sound=number==0?SoundEvents.NOTE_BLOCK_BELL.value():SoundEvents.NOTE_BLOCK_HAT.value();float pitch=number==0?1.5f:1.0f+(3-number)*.15f;
        for(var player:level.players())if(player.distanceToSqr(center.getX()+.5,baseY+.5,center.getZ()+.5)<=96*96){player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0,16,4));player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("yoiko_core.turtle.countdown.subtitle")));player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));player.playNotifySound(sound,SoundSource.PLAYERS,1.0f,pitch);}
    }

    public void showStartAnnouncement(Component title,Component subtitle){
        for(var player:level.players())if(player.distanceToSqr(center.getX()+.5,baseY+.5,center.getZ()+.5)<=96*96){
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(2,START_ANNOUNCEMENT_INTERVAL_TICKS-6,4));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(),SoundSource.PLAYERS,.55f,1.05f);
        }
    }

    public void showRaceMoment(Component title,Component subtitle){
        for(var player:level.players())if(player.distanceToSqr(center.getX()+.5,baseY+.5,center.getZ()+.5)<=96*96){
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(4,28,8));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(),SoundSource.PLAYERS,.65f,1.25f);
        }
    }

    public void syncRaceHud(){syncRaceHud(false);}

    private void syncRaceHud(boolean force){
        long gameTick=level.getGameTime();
        if(!force&&(gameTick==lastHudSyncTick||Math.floorMod(gameTick,4)!=0))return;
        lastHudSyncTick=gameTick;
        double length=Math.max(1.0e-8,simulation.course().length());
        List<RaceEntry> standings=simulation.standings();
        Map<String,Integer> currentRanks=new HashMap<>();
        for(int rank=0;rank<standings.size();rank++)currentRanks.put(standings.get(rank).entryId(),rank+1);
        List<TurtleRaceHudPayload.Marker> markers=new ArrayList<>(simulation.entries().size());
        for(int i=0;i<simulation.entries().size();i++){
            RaceEntry entry=simulation.entries().get(i);
            int progress=(int)Math.round(Math.max(0,Math.min(1,entry.progress()/length))*65_535);
            double lanePosition=Math.max(0,Math.min(7,entry.laneOffset()/RaceEntry.LANE_SPACING+3.5));
            int lane=(int)Math.round(lanePosition/7.0*255);
            markers.add(new TurtleRaceHudPayload.Marker(i+1,progress,lane,currentRanks.getOrDefault(entry.entryId(),8),entry.finishRank(),
                    entry.activeEffectRunning()));
        }
        Set<UUID> current=new HashSet<>();
        for(var player:level.players()){
            if(player.distanceToSqr(center.getX()+.5,baseY+.5,center.getZ()+.5)>96*96)continue;
            current.add(player.getUUID());
            int own=0;
            for(int i=0;i<simulation.entries().size();i++)
                if(player.getUUID().equals(simulation.entries().get(i).ownerId())){own=i+1;break;}
            PacketDistributor.sendToPlayer(player,new TurtleRaceHudPayload(true,hudSessionId,own,markers,hudSurfaces));
        }
        for(UUID viewer:new HashSet<>(hudViewers))if(!current.contains(viewer)){
            var player=level.getServer().getPlayerList().getPlayer(viewer);
            if(player!=null)PacketDistributor.sendToPlayer(player,TurtleRaceHudPayload.hidden(hudSessionId));
        }
        hudViewers.clear();hudViewers.addAll(current);
    }

    public void hideRaceHud(){
        TurtleRaceHudPayload hidden=TurtleRaceHudPayload.hidden(hudSessionId);
        for(UUID viewer:new HashSet<>(hudViewers)){
            var player=level.getServer().getPlayerList().getPlayer(viewer);
            if(player!=null)PacketDistributor.sendToPlayer(player,hidden);
        }
        hudViewers.clear();
    }

    private static List<TurtleRaceHudPayload.SurfaceSegment> buildHudSurfaces(TurtleCourse course){
        List<TurtleRaceHudPayload.SurfaceSegment> result=new ArrayList<>();
        List<TurtleCourse.Sample> samples=course.samples();
        if(samples.isEmpty())return List.of();
        TurtleSurface current=samples.getFirst().surface();double start=0;double length=Math.max(1.0e-8,course.length());
        for(int i=1;i<samples.size();i++){
            TurtleCourse.Sample sample=samples.get(i);
            if(sample.surface()==current)continue;
            int from=(int)Math.round(start/length*65_535),to=(int)Math.round(sample.distance()/length*65_535);
            if(to>from)result.add(new TurtleRaceHudPayload.SurfaceSegment(from,to,current.ordinal()));
            start=sample.distance();current=sample.surface();
        }
        int from=(int)Math.round(start/length*65_535);
        if(from<65_535)result.add(new TurtleRaceHudPayload.SurfaceSegment(from,65_535,current.ordinal()));
        return List.copyOf(result);
    }

    public void discard(){hideRaceHud();for(UUID id:entities.values()){Entity entity=level.getEntity(id);if(entity!=null)entity.discard();}entities.clear();hoppingLastTick.clear();}
    private Entity entity(String id){UUID uuid=entities.get(id);return uuid==null?null:level.getEntity(uuid);}
    private double surfaceY(TurtleCourse.Sample sample){return baseY+sample.surface().visualYOffset();}
    private static float yaw(TurtleCourse.Sample sample){return (float)(Math.toDegrees(Math.atan2(sample.tangentZ(),sample.tangentX()))-90);}
    private static float yaw(TurtleCourse.TrackPoint point){return (float)(Math.toDegrees(Math.atan2(point.tangentZ(),point.tangentX()))-90);}
    private static void faceCourseDirection(RaceTurtleEntity turtle,float yaw){turtle.setYRot(yaw);turtle.setYHeadRot(yaw);turtle.yBodyRot=yaw;turtle.yRotO=yaw;turtle.yHeadRotO=yaw;turtle.yBodyRotO=yaw;}
    private static ChatFormatting laneColor(int lane){return switch(lane){case 0->ChatFormatting.RED;case 1->ChatFormatting.GOLD;case 2->ChatFormatting.YELLOW;case 3->ChatFormatting.GREEN;case 4->ChatFormatting.AQUA;case 5->ChatFormatting.BLUE;case 6->ChatFormatting.LIGHT_PURPLE;default->ChatFormatting.WHITE;};}
}
