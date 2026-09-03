package com.yoiko.core.turtle.arena;

import com.mojang.math.Transformation;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.turtle.TurtleRacingSavedData;
import com.yoiko.core.turtle.TurtleSurface;
import com.yoiko.core.turtle.race.TurtleCourse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TurtleArenaManager {
    public enum Phase { IDLE, BUILDING, READY, CLEANING }
    public enum LineState { WAITING, RACING, FINAL_STRETCH, RESULT }
    private record Placement(BlockPos pos, BlockState state) { }
    private record TreePlacement(BlockPos pos, BlockState sapling) { }
    public record CourseInspection(boolean valid,double length,TurtleSurface startSurface,int puddleSections,
                                   int puddleBlocks,int lineTiles,int invalidLineTiles,List<String> problems,
                                   int repairAttempts,long queuedBlocks,long changedBlocks,long skippedBlocks,
                                   long buildTicks,long inspectionMillis,int displayEntities,int cornerSamples){ }
    /** Long rectangular footprint follows the loop instead of reserving an empty square. */
    private static final int WIDTH = 68;
    private static final int DEPTH = 53;
    private static final int ROAD_HALF_WIDTH = 2;
    private static final int ROAD_TILE_WIDTH = ROAD_HALF_WIDTH * 2 + 1;
    private static final int START_FINISH_DISPLAY_COUNT = ROAD_TILE_WIDTH * 2;
    private static final int BUILD_BUDGET = 1_000;
    private static final int CLEAN_BUDGET = 1_500;
    private static final int AIR_CLEAR_HEIGHT = 10;
    private static final int TREE_CAPTURE_RADIUS = 7;
    private static final int TREE_CAPTURE_BELOW = 5;
    private static final int TREE_CAPTURE_ABOVE = 20;
    private static final String START_FINISH_DISPLAY_TAG = "yoiko_turtle_start_finish";
    /** Shared presentation coordinates; keeps world entities aligned with generated facilities. */
    public static final int PODIUM_Z_OFFSET = 21;
    public static final int RESULT_ROW_Z_OFFSET = 23;
    static final int KIOSK_Z_OFFSET = 25;

    private final MinecraftServer server;
    private final Deque<Placement> queue = new ArrayDeque<>();
    private final Deque<TreePlacement> trees = new ArrayDeque<>();
    private final Map<BlockPos,BlockState> treeRestore = new HashMap<>();
    private Phase phase = Phase.IDLE;
    private TurtleCourse course;
    private long totalTasks;
    private long completedTasks;
    private ResourceKey<Level> activeDimension;
    private BlockPos activeCenter;
    private final List<UUID> startFinishDisplays=new ArrayList<>();
    private final List<BlockPos> pendingLineTiles=new ArrayList<>();
    private final Map<BlockPos,BlockState> pendingSafeRoad=new HashMap<>();
    private CourseInspection lastInspection;
    private LineState lineState=LineState.WAITING;
    private int inspectionRepairAttempts;
    private long changedBlocks;
    private long skippedBlocks;
    private long buildStartedTick;
    private long lastBuildTicks;
    private long lastInspectionMillis;
    private String failureReason;
    private long inspectionVisualUntil;
    private long laneVisualUntil;
    private long forcedWorkChunk=Long.MIN_VALUE;

    public TurtleArenaManager(MinecraftServer server) { this.server = server; }
    public Phase phase() { return phase; }
    public TurtleCourse course() { return course; }
    public double progress() { return totalTasks <= 0 ? (phase == Phase.READY ? 1 : 0) : completedTasks / (double) totalTasks; }
    public CourseInspection inspect(){return lastInspection;}
    public CourseInspection inspectCurrent(){ServerLevel level=activeDimension==null?null:server.getLevel(activeDimension);return phase!=Phase.READY||course==null||level==null?lastInspection:inspectBuiltCourse(level);}
    public String failureReason(){return failureReason;}
    public void showInspectionVisual(){if(course==null||activeDimension==null)throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.no_visual_course");inspectionVisualUntil=server.getTickCount()+400L;}
    public void clearInspectionVisual(){inspectionVisualUntil=0;}
    public void showLaneVisual(){if(course==null||activeDimension==null)throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.no_visual_course");laneVisualUntil=server.getTickCount()+400L;}
    public void clearLaneVisual(){laneVisualUntil=0;}

    public void setLineState(LineState state){
        if(lineState==state)return;lineState=state;
        if(phase==Phase.READY&&activeDimension!=null){ServerLevel level=server.getLevel(activeDimension);if(level!=null)updateStartFinishDisplays(level);}
    }

    public void configure(ServerLevel level, BlockPos center) {
        if (phase != Phase.IDLE) throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.arena_busy");
        TurtleRacingSavedData.get(server).setArena(level.dimension().location().toString(), center);
    }

    public TurtleCourse beginBuild(long seed, boolean beachTheme) {
        return beginBuild(seed,beachTheme,null);
    }

    public TurtleCourse beginBuild(long seed, boolean beachTheme,String themeId) {
        if (phase != Phase.IDLE) throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.arena_not_empty");
        TurtleRacingSavedData saved = TurtleRacingSavedData.get(server);
        activeCenter = saved.arenaCenter().orElseThrow(() -> com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.arena_center_first"));
        if (saved.arenaDimension().isBlank()) throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.arena_dimension_unset");
        activeDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(saved.arenaDimension()));
        ServerLevel level = server.getLevel(activeDimension);
        if (level == null) throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.configured_arena_dimension_missing");
        course = TurtleCourse.generate(activeCenter, seed, beachTheme,themeId);
        String selectedTheme=course.themeId();
        for(int attempt=0;attempt<3&&!inspectPlannedCourse(course,activeCenter.getY()).valid();attempt++){long replacement=course.seed()+0x9E37_79B9L;course=TurtleCourse.generate(activeCenter,replacement,beachTheme,selectedTheme);}
        beginBuildQueue(course);
        return course;
    }

    public TurtleCourse beginBuildPreset(int presetIndex) {
        if (phase != Phase.IDLE) throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.arena_not_empty");
        TurtleRacingSavedData saved = TurtleRacingSavedData.get(server);
        activeCenter = saved.arenaCenter().orElseThrow(() -> com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.arena_center_first"));
        if (saved.arenaDimension().isBlank()) throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.arena_dimension_unset");
        activeDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(saved.arenaDimension()));
        if (server.getLevel(activeDimension) == null) throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.configured_arena_dimension_missing");
        course=TurtleCourse.preset(activeCenter,presetIndex);
        beginBuildQueue(course);
        return course;
    }

    private void beginBuildQueue(TurtleCourse course) {
        queue.clear();trees.clear();treeRestore.clear();TurtleRacingSavedData.get(server).clearArenaTreeRestore();pendingSafeRoad.clear();pendingLineTiles.clear();completedTasks = 0;inspectionRepairAttempts=0;lineState=LineState.WAITING;
        changedBlocks=0;skippedBlocks=0;lastBuildTicks=0;lastInspectionMillis=0;failureReason=null;buildStartedTick=server.getTickCount();
        enqueueBuild(course, activeCenter);
        groupQueueByChunk();
        lastInspection=inspectPlannedCourse(course,activeCenter.getY());
        if(!lastInspection.valid()){
            failureReason="course_preflight_failed: "+String.join("; ",lastInspection.problems());
            queue.clear();enqueueBaseline(activeCenter);groupQueueByChunk();totalTasks=queue.size();phase=Phase.CLEANING;
            YoikoServerCore.LOGGER.error("{}",failureReason);return;
        }
        totalTasks = queue.size(); phase = Phase.BUILDING;
        YoikoServerCore.LOGGER.info("Turtle arena build queued: {} blocks, theme={}, seed={}", totalTasks,
                course.themeId(), course.seed());
    }

    public void beginCleanup() {
        if (phase == Phase.IDLE || phase == Phase.CLEANING) return;
        discardStartFinishDisplays(server.getLevel(activeDimension));
        queue.clear(); completedTasks = 0;
        TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);
        enqueueTreeRestore(saved);
        enqueueBaseline(activeCenter);
        groupQueueByChunk();
        totalTasks = queue.size(); phase = Phase.CLEANING;
    }

    public void recoverCleanup() {
        if (phase != Phase.IDLE) return;
        TurtleRacingSavedData saved = TurtleRacingSavedData.get(server);
        activeCenter = saved.arenaCenter().orElse(null);
        if (activeCenter == null || saved.arenaDimension().isBlank()) return;
        activeDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(saved.arenaDimension()));
        ServerLevel level=server.getLevel(activeDimension);if (level == null) return;
        discardStartFinishDisplays(level);
        queue.clear(); completedTasks=0; enqueueTreeRestore(saved); enqueueBaseline(activeCenter); groupQueueByChunk(); totalTasks=queue.size(); phase=Phase.CLEANING;
    }

    public void tick() {
        tickInspectionVisual();
        if (phase != Phase.BUILDING && phase != Phase.CLEANING) return;
        ServerLevel level = server.getLevel(activeDimension);
        if (level == null) { queue.clear(); phase = Phase.IDLE; return; }
        int budget = phase == Phase.BUILDING ? BUILD_BUDGET : CLEAN_BUDGET;
        int changes=0, inspected=0;
        long tickChunk=queue.isEmpty()?Long.MIN_VALUE:chunkKey(queue.peekFirst().pos());
        if(tickChunk!=Long.MIN_VALUE)forceWorkChunk(level,tickChunk);
        while(changes<budget && inspected<budget*4 && !queue.isEmpty()) {
            if(chunkKey(queue.peekFirst().pos())!=tickChunk)break;
            Placement placement=queue.removeFirst();
            inspected++;
            if(level.getBlockState(placement.pos()).equals(placement.state()))skippedBlocks++;
            else {level.setBlock(placement.pos(), placement.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);changedBlocks++;changes++;}
            completedTasks++;
        }
        if (!queue.isEmpty()) return;
        if (phase == Phase.BUILDING) {
            growVanillaTrees(level);long inspectStart=System.nanoTime();lastInspection=inspectBuiltCourse(level);lastInspectionMillis=(System.nanoTime()-inspectStart)/1_000_000L;
            if(!lastInspection.valid()&&inspectionRepairAttempts++<2){
                pendingSafeRoad.forEach((pos,state)->queue.add(new Placement(pos,state)));groupQueueByChunk();totalTasks+=queue.size();
                YoikoServerCore.LOGGER.warn("Turtle arena inspection repaired safe road (attempt {}): {}",inspectionRepairAttempts,String.join("; ",lastInspection.problems()));return;
            }
            if(!lastInspection.valid()){
                YoikoServerCore.LOGGER.error("Turtle arena final inspection failed: {}",String.join("; ",lastInspection.problems()));
                failureReason=String.join("; ",lastInspection.problems());
                queue.clear();enqueueBaseline(activeCenter);groupQueueByChunk();totalTasks=queue.size();completedTasks=0;phase=Phase.CLEANING;return;
            }
            lastBuildTicks=server.getTickCount()-buildStartedTick;spawnStartFinishDisplays(level);releaseWorkChunk(level);phase = Phase.READY;
            YoikoServerCore.LOGGER.info("Turtle arena ready: queued={}, changed={}, skipped={}, buildTicks={}, inspectMs={}, repairs={}, displays={}",totalTasks,changedBlocks,skippedBlocks,lastBuildTicks,lastInspectionMillis,inspectionRepairAttempts,startFinishDisplays.size());
        }
        else { TurtleRacingSavedData.get(server).clearArenaTreeRestore();treeRestore.clear();trees.clear();releaseWorkChunk(level);phase = Phase.IDLE; course = null; activeCenter = null; activeDimension = null; }
    }

    private void enqueueBuild(TurtleCourse course, BlockPos center) {
        int minX=center.getX()-WIDTH/2,maxX=minX+WIDTH-1,minZ=center.getZ()-DEPTH/2,maxZ=minZ+DEPTH-1;
        int baseY=center.getY();
        TurtleArenaTheme theme=TurtleArenaTheme.fromId(course.themeId());
        BlockState background=theme.background();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            queue.add(new Placement(new BlockPos(x,baseY-2,z),Blocks.STONE.defaultBlockState()));
            queue.add(new Placement(new BlockPos(x,baseY-1,z),background));
            for(int y=baseY;y<=baseY+AIR_CLEAR_HEIGHT;y++)queue.add(new Placement(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState()));
        }
        pendingLineTiles.clear();pendingSafeRoad.clear();Map<BlockPos,TurtleSurface> road=new HashMap<>(); Set<BlockPos> boundary=new HashSet<>(),startFinishRoad=new HashSet<>(),transitionRoad=new HashSet<>();
        for(TurtleCourse.Sample sample:course.samples()){
            for(int offset=-ROAD_HALF_WIDTH;offset<=ROAD_HALF_WIDTH;offset++){
                int x=(int)Math.floor(sample.x()+sample.normalX()*offset),z=(int)Math.floor(sample.z()+sample.normalZ()*offset);
                BlockPos roadPos=new BlockPos(x,baseY-1,z);road.put(roadPos,sample.surface());
                if(course.isStartFinishZone(sample.distance()))startFinishRoad.add(roadPos);
                if(sample.surface()!=TurtleSurface.PUDDLE&&(course.sampleAt(Math.max(0,sample.distance()-2.25)).surface()==TurtleSurface.PUDDLE
                        ||course.sampleAt(Math.min(course.length(),sample.distance()+2.25)).surface()==TurtleSurface.PUDDLE))transitionRoad.add(roadPos);
            }
        }
        boundary.addAll(deriveRoadBoundary(road.keySet(),baseY));
        road.forEach((pos,surface)->{
            if(startFinishRoad.contains(pos)){BlockState state=startFinishBlock(course.startFinishSurface());pendingSafeRoad.put(pos,state);queue.add(new Placement(pos,state));return;}
            if(surface==TurtleSurface.PUDDLE){queue.add(new Placement(pos.below(2),Blocks.STONE.defaultBlockState()));queue.add(new Placement(pos.below(),course.beachTheme()?Blocks.SAND.defaultBlockState():Blocks.CLAY.defaultBlockState()));queue.add(new Placement(pos,Blocks.WATER.defaultBlockState()));}
            else queue.add(new Placement(pos,transitionRoad.contains(pos)?transitionBlock(course.beachTheme(),pos,course.seed()):blockFor(surface,pos,course.seed())));
        });
        BlockState curb=theme.curb();
        boundary.forEach(pos->queue.add(new Placement(pos,curb)));
        TurtleCourse.Sample start=course.sampleAt(0);for(int offset=-ROAD_HALF_WIDTH;offset<=ROAD_HALF_WIDTH;offset++)pendingLineTiles.add(markerPos(start,offset,0,baseY-1));
        enqueueFacilities(center,course);
        enqueueCourseLandmarks(course,center,road,boundary);
        enqueueProceduralDecoration(center,road,boundary,course,course.seed());
    }

    /** A thin, non-colliding checker overlay keeps the continuous road material visible underneath. */
    private void spawnStartFinishDisplays(ServerLevel level){
        discardStartFinishDisplays(level);TurtleCourse.Sample start=course.sampleAt(0);
        for(int offset=-ROAD_HALF_WIDTH;offset<=ROAD_HALF_WIDTH;offset++){
            BlockPos pos=markerPos(start,offset,0,activeCenter.getY());spawnMarkerTile(level,pos.getX(),pos.getZ(),lineBlock(offset),.025F);
        }
        for(int offset=-ROAD_HALF_WIDTH;offset<=ROAD_HALF_WIDTH;offset++){
            BlockPos pos=markerPos(start,offset,-1,activeCenter.getY());spawnMarkerTile(level,pos.getX(),pos.getZ(),lineState==LineState.WAITING?((offset&1)==0?Blocks.LIGHT_BLUE_CONCRETE:Blocks.CYAN_CONCRETE):Blocks.AIR,.0125F);
        }
    }

    /** Rasterises a straight five-tile line without duplicate floor-rounded positions. */
    private static BlockPos markerPos(TurtleCourse.Sample start,int lateral,int forward,int y){int crossX=Math.abs(start.normalX())>=Math.abs(start.normalZ())?(start.normalX()>=0?1:-1):0,crossZ=crossX==0?(start.normalZ()>=0?1:-1):0;int forwardX=Math.abs(start.tangentX())>=Math.abs(start.tangentZ())?(start.tangentX()>=0?1:-1):0,forwardZ=forwardX==0?(start.tangentZ()>=0?1:-1):0;return new BlockPos((int)Math.floor(start.x())+crossX*lateral+forwardX*forward,y,(int)Math.floor(start.z())+crossZ*lateral+forwardZ*forward);}

    /** Keeps both marker rows stable and only swaps their rendered block state. */
    private void updateStartFinishDisplays(ServerLevel level){
        if(startFinishDisplays.size()!=START_FINISH_DISPLAY_COUNT){spawnStartFinishDisplays(level);return;}
        for(int i=0;i<START_FINISH_DISPLAY_COUNT;i++){
            Entity entity=level.getEntity(startFinishDisplays.get(i));
            if(!(entity instanceof Display.BlockDisplay display)){spawnStartFinishDisplays(level);return;}
            Block block=i<ROAD_TILE_WIDTH?lineBlock(i-ROAD_HALF_WIDTH):(lineState==LineState.WAITING?(((i-ROAD_TILE_WIDTH)&1)==0?Blocks.LIGHT_BLUE_CONCRETE:Blocks.CYAN_CONCRETE):Blocks.AIR);
            CompoundTag tag=display.saveWithoutId(new CompoundTag());
            tag.put("block_state",NbtUtils.writeBlockState(block.defaultBlockState()));
            display.load(tag);
        }
    }

    private void spawnMarkerTile(ServerLevel level,int x,int z,Block block,float thickness){
        Display.BlockDisplay display=new Display.BlockDisplay(EntityType.BLOCK_DISPLAY,level);CompoundTag tag=display.saveWithoutId(new CompoundTag());
        tag.put("block_state",NbtUtils.writeBlockState(block.defaultBlockState()));
        Transformation transform=new Transformation(new Vector3f(0,.0125F,0),new Quaternionf(),new Vector3f(1,thickness,1),new Quaternionf());
        Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE,transform).ifSuccess(value->tag.put("transformation",value));
        tag.putFloat("view_range",4F);tag.putFloat("shadow_radius",0);tag.putFloat("shadow_strength",0);tag.putFloat("width",1F);tag.putFloat("height",.05F);
        tag.put("brightness",Brightness.CODEC.encodeStart(NbtOps.INSTANCE,Brightness.FULL_BRIGHT).getOrThrow());display.load(tag);
        display.setNoGravity(true);display.setSilent(true);display.addTag(START_FINISH_DISPLAY_TAG);display.setPos(x,activeCenter.getY(),z);
        level.addFreshEntity(display);startFinishDisplays.add(display.getUUID());
    }

    private void discardStartFinishDisplays(ServerLevel level){
        if(level==null){startFinishDisplays.clear();return;}
        for(UUID id:startFinishDisplays){Entity entity=level.getEntity(id);if(entity!=null)entity.discard();}
        startFinishDisplays.clear();
        if(activeCenter==null)return;
        AABB arenaBox=new AABB(activeCenter).inflate(WIDTH,32,DEPTH);
        for(Entity entity:level.getEntities((Entity)null,arenaBox,e->e.getTags().contains(START_FINISH_DISPLAY_TAG)))entity.discard();
    }

    private void enqueueFacilities(BlockPos center,TurtleCourse course){
        int y=center.getY();int kioskZ=center.getZ()+KIOSK_Z_OFFSET;
        BlockState floor=TurtleArenaTheme.fromId(course.themeId()).facilityFloor();
        // The old centre counter shared blocks with first place, leaving its roof floating above
        // the winner. A deeper plaza now gives the counters and podium separate rows.
        for(int x=center.getX()-18;x<=center.getX()+18;x++)for(int z=center.getZ()+20;z<=center.getZ()+26;z++)
            queue.add(new Placement(new BlockPos(x,y-1,z),floor));
        int[] kiosks=centerOffsets(center.getX());
        for(int x:kiosks){
            // Keep the interactive lectern, but remove the oversized log, wool and lantern pillar.
            queue.add(new Placement(new BlockPos(x,y,kioskZ-1),Blocks.LECTERN.defaultBlockState()));
        }
        int podiumZ=center.getZ()+PODIUM_Z_OFFSET;for(int dx=-3;dx<=3;dx++)for(int dz=-1;dz<=1;dz++)queue.add(new Placement(new BlockPos(center.getX()+dx,y-1,podiumZ+dz),Blocks.QUARTZ_BRICKS.defaultBlockState()));
        for(int dx=-1;dx<=1;dx++){int height=dx==0?2:1;BlockState block=dx==0?Blocks.GOLD_BLOCK.defaultBlockState():dx<0?Blocks.IRON_BLOCK.defaultBlockState():Blocks.COPPER_BLOCK.defaultBlockState();for(int h=0;h<height;h++)queue.add(new Placement(new BlockPos(center.getX()+dx*2,y+h,podiumZ),block));}
    }

    private static int[] centerOffsets(int x){return new int[]{x-14,x,x+14};}

    private void enqueueProceduralDecoration(BlockPos center,Map<BlockPos,TurtleSurface> road,Set<BlockPos> boundary,TurtleCourse course,long seed){
        SplittableRandom random=new SplittableRandom(seed^0x54A7E11L);int y=center.getY();Set<BlockPos> forbidden=new HashSet<>(road.keySet());forbidden.addAll(boundary);
        // Vanilla-grown trees replace the old log pillars and flat leaf diamonds.
        for(int attempt=0,placed=0;attempt<80&&placed<10;attempt++){
            int x=center.getX()+random.nextInt(-31,32),z=center.getZ()+random.nextInt(-22,18);
            if(!decorationClear(forbidden,x,z,5)||Math.abs(z-(center.getZ()+24))<4)continue;
            BlockPos root=new BlockPos(x,y,z);queue.add(new Placement(root.below(),Blocks.DIRT.defaultBlockState()));
            BlockState sapling=TurtleArenaTheme.fromId(course.themeId()).sapling(random);queue.add(new Placement(root,sapling));trees.add(new TreePlacement(root,sapling));placed++;
        }
        // Irregular ground patches break up the single flat material without requiring custom structures.
        for(int patch=0;patch<18;patch++){
            int x=center.getX()+random.nextInt(-32,33),z=center.getZ()+random.nextInt(-22,19),radius=random.nextInt(1,3);
            if(!decorationClear(forbidden,x,z,radius+2))continue;
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(dx*dx+dz*dz<=radius*radius+random.nextInt(2)){
                BlockState ground=TurtleArenaTheme.fromId(course.themeId()).patch(random);
                queue.add(new Placement(new BlockPos(x+dx,y-1,z+dz),ground));
            }
            queue.add(new Placement(new BlockPos(x,y,z),TurtleArenaTheme.fromId(course.themeId()).accent(random)));
        }
    }

    private void enqueueCourseLandmarks(TurtleCourse course,BlockPos center,Map<BlockPos,TurtleSurface> road,Set<BlockPos> boundary){
        Set<BlockPos> forbidden=new HashSet<>(road.keySet());forbidden.addAll(boundary);int y=center.getY();
        double[] marks={course.length()*.22,course.length()*.50,course.length()*.78};
        BlockState[] caps={Blocks.LIME_WOOL.defaultBlockState(),Blocks.LIGHT_BLUE_WOOL.defaultBlockState(),Blocks.GOLD_BLOCK.defaultBlockState()};
        for(int i=0;i<marks.length;i++){
            TurtleCourse.Sample sample=course.sampleAt(marks[i]);boolean placed=false;
            for(int side:new int[]{1,-1}){int x=(int)Math.floor(sample.x()+sample.normalX()*side*5.2),z=(int)Math.floor(sample.z()+sample.normalZ()*side*5.2);if(!decorationClear(forbidden,x,z,1))continue;
                BlockPos root=new BlockPos(x,y,z);queue.add(new Placement(root.below(),TurtleArenaTheme.fromId(course.themeId()).landmarkBase()));queue.add(new Placement(root,Blocks.OAK_FENCE.defaultBlockState()));queue.add(new Placement(root.above(),caps[i]));queue.add(new Placement(root.above(2),Blocks.LANTERN.defaultBlockState()));placed=true;break;}
            if(!placed)YoikoServerCore.LOGGER.debug("Skipped turtle landmark {} because no clear vanilla placement was found",i+1);
        }
    }

    private static boolean decorationClear(Set<BlockPos> forbidden,int x,int z,int margin){return forbidden.stream().noneMatch(p->Math.abs(p.getX()-x)<=margin&&Math.abs(p.getZ()-z)<=margin);}
    private static long chunkKey(BlockPos pos){return net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4);}
    private void groupQueueByChunk(){List<Placement> ordered=new ArrayList<>(queue);ordered.sort(Comparator.comparingLong(v->chunkKey(v.pos())));queue.clear();queue.addAll(ordered);}
    private void forceWorkChunk(ServerLevel level,long key){if(forcedWorkChunk==key)return;releaseWorkChunk(level);level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(key),net.minecraft.world.level.ChunkPos.getZ(key),true);forcedWorkChunk=key;}
    private void releaseWorkChunk(ServerLevel level){if(forcedWorkChunk==Long.MIN_VALUE)return;level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(forcedWorkChunk),net.minecraft.world.level.ChunkPos.getZ(forcedWorkChunk),false);forcedWorkChunk=Long.MIN_VALUE;}
    private void growVanillaTrees(ServerLevel level){while(!trees.isEmpty()){TreePlacement tree=trees.removeFirst();Map<BlockPos,BlockState> before=captureTreeRegion(level,tree.pos());BlockState state=level.getBlockState(tree.pos());if(state.getBlock() instanceof SaplingBlock sapling){sapling.advanceTree(level,tree.pos(),state,RandomSource.create(course.seed()^tree.pos().asLong()));BlockState next=level.getBlockState(tree.pos());if(next.getBlock() instanceof SaplingBlock second)second.advanceTree(level,tree.pos(),next,RandomSource.create(~course.seed()^tree.pos().asLong()));}for(Map.Entry<BlockPos,BlockState> entry:before.entrySet())if(!level.getBlockState(entry.getKey()).equals(entry.getValue()))treeRestore.putIfAbsent(entry.getKey(),entry.getValue());}TurtleRacingSavedData.get(server).setArenaTreeRestore(treeRestore);}

    private static Map<BlockPos,BlockState> captureTreeRegion(ServerLevel level,BlockPos root){Map<BlockPos,BlockState> result=new HashMap<>();for(int x=root.getX()-TREE_CAPTURE_RADIUS;x<=root.getX()+TREE_CAPTURE_RADIUS;x++)for(int z=root.getZ()-TREE_CAPTURE_RADIUS;z<=root.getZ()+TREE_CAPTURE_RADIUS;z++)for(int y=root.getY()-TREE_CAPTURE_BELOW;y<=root.getY()+TREE_CAPTURE_ABOVE;y++){BlockPos pos=new BlockPos(x,y,z);result.put(pos,level.getBlockState(pos));}return result;}

    private void enqueueTreeRestore(TurtleRacingSavedData saved){Map<BlockPos,BlockState> persisted=saved.arenaTreeRestore();if(persisted.isEmpty())persisted=Map.copyOf(treeRestore);persisted.forEach((pos,state)->queue.add(new Placement(pos,state)));}

    /** Builds a one-block curb from the final road mask, avoiding sample-connection spikes at corners. */
    public static Set<BlockPos> deriveRoadBoundary(Set<BlockPos> road,int y){
        Set<BlockPos> output=new HashSet<>();
        int[][] directions={{1,0},{-1,0},{0,1},{0,-1}};
        for(BlockPos roadPos:road)for(int[] direction:directions){
            BlockPos neighbor=roadPos.offset(direction[0],0,direction[1]);
            if(!road.contains(neighbor))output.add(new BlockPos(neighbor.getX(),y,neighbor.getZ()));
        }
        return output;
    }

    private void enqueueBaseline(BlockPos center) {
        int minX=center.getX()-WIDTH/2,maxX=minX+WIDTH-1,minZ=center.getZ()-DEPTH/2,maxZ=minZ+DEPTH-1,baseY=center.getY();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            queue.add(new Placement(new BlockPos(x,baseY-2,z),Blocks.STONE.defaultBlockState()));
            queue.add(new Placement(new BlockPos(x,baseY-1,z),Blocks.SMOOTH_STONE.defaultBlockState()));
            for(int y=baseY;y<=baseY+AIR_CLEAR_HEIGHT;y++)queue.add(new Placement(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState()));
        }
    }

    private static BlockState blockFor(TurtleSurface surface,BlockPos pos,long seed){long hash=seed^pos.asLong()*0x9E3779B97F4A7C15L;int variant=Math.floorMod((int)(hash^(hash>>>32)),20);return switch(surface){case SAND->variant==0?Blocks.SANDSTONE.defaultBlockState():variant==1?Blocks.GRAVEL.defaultBlockState():Blocks.SAND.defaultBlockState();case MUD->variant<3?Blocks.PACKED_MUD.defaultBlockState():Blocks.MUD.defaultBlockState();case PUDDLE->Blocks.WATER.defaultBlockState();};}
    private static BlockState startFinishBlock(TurtleSurface surface){return switch(surface){
        case SAND->Blocks.SAND.defaultBlockState();case MUD->Blocks.MUD.defaultBlockState();case PUDDLE->throw new IllegalArgumentException("Start/finish surface cannot be water");
    };}

    private Block lineBlock(int offset){boolean alternate=(offset&1)!=0;return switch(lineState){
        case WAITING->alternate?Blocks.CYAN_CONCRETE:Blocks.WHITE_CONCRETE;
        case RACING->alternate?Blocks.BLACK_CONCRETE:Blocks.WHITE_CONCRETE;
        case FINAL_STRETCH,RESULT->alternate?Blocks.GOLD_BLOCK:Blocks.WHITE_CONCRETE;
    };}

    private static BlockState transitionBlock(boolean beach,BlockPos pos,long seed){
        int variant=Math.floorMod((int)(seed^pos.asLong()^(pos.asLong()>>>32)),8);
        if(beach)return variant<4?Blocks.SAND.defaultBlockState():variant<7?Blocks.CLAY.defaultBlockState():Blocks.PACKED_MUD.defaultBlockState();
        return variant<5?Blocks.MUD.defaultBlockState():Blocks.PACKED_MUD.defaultBlockState();
    }

    private CourseInspection inspectPlannedCourse(TurtleCourse inspected,int baseY){
        List<String> problems=new ArrayList<>();TurtleSurface seam=inspected.startFinishSurface();Set<BlockPos> line=new HashSet<>();
        TurtleCourse.Sample start=inspected.sampleAt(0);for(int offset=-ROAD_HALF_WIDTH;offset<=ROAD_HALF_WIDTH;offset++)line.add(markerPos(start,offset,0,baseY-1));
        if(line.size()!=ROAD_TILE_WIDTH)problems.add("start/finish display must occupy exactly "+ROAD_TILE_WIDTH+" unique blocks, actual="+line.size());
        for(double distance=0;distance<=TurtleCourse.START_FINISH_SAFE_DISTANCE;distance+=.25){if(inspected.sampleAt(distance).surface()!=seam||inspected.sampleAt(inspected.length()-distance).surface()!=seam)problems.add("start/finish surface mismatch at "+distance);}
        if(seam==TurtleSurface.PUDDLE)problems.add("start/finish surface is water");
        validatePuddles(inspected,problems);validateGeometry(inspected,problems);return inspection(problems,inspected,seam,line.size(),0);
    }

    private CourseInspection inspectBuiltCourse(ServerLevel level){
        List<String> problems=new ArrayList<>();TurtleSurface seam=course.startFinishSurface();int invalid=0;
        BlockState expected=startFinishBlock(seam);
        for(var safe:pendingSafeRoad.entrySet()){BlockState actual=level.getBlockState(safe.getKey());if(actual.getBlock()!=safe.getValue().getBlock()||!level.getFluidState(safe.getKey()).isEmpty())problems.add("unsafe seam block "+safe.getKey().toShortString()+" is "+actual.getBlock());}
        for(BlockPos pos:pendingLineTiles){BlockState actual=level.getBlockState(pos);if(actual.getBlock()!=expected.getBlock()){invalid++;problems.add("line tile "+pos.toShortString()+" is "+actual.getBlock()+", expected "+expected.getBlock());}}
        validatePuddles(course,problems);validateGeometry(course,problems);return inspection(problems,course,seam,pendingLineTiles.size(),invalid);
    }

    private CourseInspection inspection(List<String> problems,TurtleCourse value,TurtleSurface seam,int lineTiles,int invalid){return new CourseInspection(problems.isEmpty(),value.length(),seam,countPuddleSections(value),countPuddleSamples(value),lineTiles,invalid,List.copyOf(problems),inspectionRepairAttempts,totalTasks,changedBlocks,skippedBlocks,lastBuildTicks,lastInspectionMillis,startFinishDisplays.size(),(int)value.samples().stream().filter(s->s.curvature()>.15).count());}
    private static void validatePuddles(TurtleCourse value,List<String> problems){int samples=countPuddleSamples(value);double ratio=samples/(double)Math.max(1,value.samples().size());if(ratio<.40||ratio>.48)problems.add("water ratio must be 40-48%, actual="+String.format(java.util.Locale.ROOT,"%.1f%%",ratio*100));int sections=countPuddleSections(value);if(sections!=2)problems.add("course must contain exactly two swimming sections, actual="+sections);}
    private static void validateGeometry(TurtleCourse value,List<String> problems){
        if(value.length()<140||value.length()>195)problems.add("course length outside 140-195 blocks: "+String.format(java.util.Locale.ROOT,"%.1f",value.length()));
        int longestSurface=1,currentSurface=1;for(int i=1;i<value.samples().size();i++){if(value.samples().get(i).surface()==value.samples().get(i-1).surface())currentSurface++;else currentSurface=1;longestSurface=Math.max(longestSurface,currentSurface);}if(longestSurface*.25>value.length()*.62)problems.add("one surface occupies too much continuous track: "+String.format(java.util.Locale.ROOT,"%.1f",longestSurface*.25));
        double lastCornerEnd=-100;boolean corner=false;int groups=0;for(TurtleCourse.Sample sample:value.samples()){boolean curved=sample.curvature()>.15;if(curved&&!corner){if(sample.distance()-lastCornerEnd<3.0)problems.add("corners are closer than 3 blocks at "+String.format(java.util.Locale.ROOT,"%.1f",sample.distance()));corner=true;groups++;}if(!curved&&corner){lastCornerEnd=sample.distance();corner=false;}}if(groups<4)problems.add("course needs at least four distinct corners: "+groups);
        for(int lane=0;lane<8;lane++){double offset=com.yoiko.core.turtle.race.RaceEntry.laneCenterOffset(lane);TurtleCourse.TrackPoint previous=value.trackPoint(0,offset);for(double progress=.25;progress<=value.length();progress+=.25){TurtleCourse.TrackPoint point=value.trackPoint(progress,offset);double moved=Math.hypot(point.x()-previous.x(),point.z()-previous.z());if(moved<.02||moved>.45){problems.add("lane "+lane+" has invalid path step at "+String.format(java.util.Locale.ROOT,"%.1f",progress));break;}previous=point;}}
    }

    private void tickInspectionVisual(){
        if(course==null||activeDimension==null)return;boolean courseView=inspectionVisualUntil>server.getTickCount(),laneView=laneVisualUntil>server.getTickCount();if(!courseView&&!laneView)return;ServerLevel level=server.getLevel(activeDimension);if(level==null||server.getTickCount()%10!=0)return;
        if(courseView){for(int i=0;i<course.samples().size();i+=8){TurtleCourse.Sample s=course.samples().get(i);Vector3f color=s.surface()==TurtleSurface.PUDDLE?new Vector3f(.15F,.45F,1F):new Vector3f(.1F,1F,.75F);level.sendParticles(new DustParticleOptions(color,.7F),s.x(),activeCenter.getY()+.18,s.z(),1,0,0,0,0);}TurtleCourse.Sample line=course.sampleAt(0);var particle=lastInspection!=null&&lastInspection.valid()?ParticleTypes.HAPPY_VILLAGER:ParticleTypes.ANGRY_VILLAGER;for(int offset=-ROAD_HALF_WIDTH;offset<=ROAD_HALF_WIDTH;offset++)level.sendParticles(particle,line.x()+line.normalX()*offset,activeCenter.getY()+.25,line.z()+line.normalZ()*offset,1,.02,.02,.02,0);}
        if(laneView){Vector3f[] colors={new Vector3f(1,.18F,.18F),new Vector3f(1,.55F,.1F),new Vector3f(1,1,.15F),new Vector3f(.15F,1,.25F),new Vector3f(.1F,1,1),new Vector3f(.15F,.35F,1),new Vector3f(.75F,.15F,1),new Vector3f(1,1,1)};for(int lane=0;lane<8;lane++)for(double distance=0;distance<course.length();distance+=2){TurtleCourse.TrackPoint point=course.trackPoint(distance,com.yoiko.core.turtle.race.RaceEntry.laneCenterOffset(lane));level.sendParticles(new DustParticleOptions(colors[lane],.48F),point.x(),activeCenter.getY()+.28,point.z(),1,0,0,0,0);}}
    }

    private static int countPuddleSamples(TurtleCourse course){return (int)course.samples().stream().filter(sample->sample.surface()==TurtleSurface.PUDDLE).count();}
    private static int countPuddleSections(TurtleCourse course){int count=0;boolean inside=false;for(TurtleCourse.Sample sample:course.samples()){boolean puddle=sample.surface()==TurtleSurface.PUDDLE;if(puddle&&!inside)count++;inside=puddle;}return count;}
}
