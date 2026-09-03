package com.yoiko.core.turtle;

import com.yoiko.core.turtle.race.AiTurtleCatalog;
import com.yoiko.core.turtle.race.AiTurtleProfile;
import com.yoiko.core.turtle.race.RaceEntry;
import com.yoiko.core.turtle.race.RaceSimulation;
import com.yoiko.core.turtle.race.TurtleCourse;
import com.yoiko.core.turtle.arena.TurtleArenaManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class TurtleRacingValidator {
    public record Report(int generatedLoadouts,int courseSeeds,int simulatedRaces,long raceTicks,
                         long laneChanges,long rankChanges,int distinctWinners,int lateComebackWins,int tacticalWins,
                         long hops,int racesWithHops,
                         Map<TurtleStrategy,Integer> observedStrategyWins,int controlledStrategyRaces,
                         Map<TurtleStrategy,Integer> controlledStrategyWins,long elapsedMillis){}
    private TurtleRacingValidator(){}
    public static void main(String[] args){if(args.length>0&&args[0].equals("controlled")){System.out.println(validateStrategyBalance(256));return;}System.out.println(validate());}
    public static Report validate(){long started=System.currentTimeMillis();int generated=0;
        if(ActiveSkill.values().length!=25||PassiveSkillCatalog.all().size()!=50||TurtleCompanion.values().length!=13||AiTurtleCatalog.all().size()!=12||TurtleNameCatalog.ids().size()!=60)throw new IllegalStateException("Turtle catalog size invariant failed");
        validateGeneratedNames();
        validatePendingRerollPersistence();
        validateStrategyTicketFlow();
        validateProjectedTraining();
        validateBodyAppearances();
        AiTurtleCatalog.all().forEach(profile->TurtleAppearanceCatalog.get(profile.appearance()));
        long seed=1;for(TurtleRarity rarity:TurtleRarity.values())for(TurtleArchetype archetype:TurtleArchetype.values()){
            SplittableRandom random=new SplittableRandom(seed++);TurtleStats stats=TurtleGenerationService.generateStats(random,rarity,archetype);if(stats.total()!=rarity.hatchBudget())throw new IllegalStateException("Stat budget invariant failed");for(TurtleStat stat:TurtleStat.values())if(stats.get(stat)>rarity.hatchStatCap())throw new IllegalStateException("Stat cap invariant failed");ActiveSkill active=ActiveSkill.values()[(int)Math.floorMod(seed,ActiveSkill.values().length)];List<String> ids=PassiveGenerationService.generate(random,rarity,active,archetype);List<PassiveSkill> passives=ids.stream().map(PassiveSkillCatalog::get).toList();int specials=(int)passives.stream().filter(PassiveSkill::special).count();if(!PassiveLoadoutValidator.validComplete(passives,specials,rarity.minimumSynergies(),active,archetype))throw new IllegalStateException("Passive invariant failed");generated++;}
        int courses=32;for(long courseSeed=0;courseSeed<courses;courseSeed++){TurtleCourse course=TurtleCourse.generate(BlockPos.ZERO,courseSeed,(courseSeed&1)==0);validateCompactCourse(course);}
        for(int preset=0;preset<6;preset++){TurtleCourse course=TurtleCourse.preset(BlockPos.ZERO,preset);validateCompactCourse(course);
            if((preset==1||preset==2)&&course.samples().stream().noneMatch(sample->sample.surface()==TurtleSurface.PUDDLE))throw new IllegalStateException("Puddle preset lost its mid-course water section: "+preset);}
        int simulated=96;long totalTicks=0,laneChanges=0,rankChanges=0,hops=0;int racesWithHops=0,lateComebacks=0,tacticalWins=0;Set<String>winners=new HashSet<>();Map<String,Integer>winnerCounts=new java.util.TreeMap<>();Map<TurtleStrategy,Integer> observedStrategyWins=new EnumMap<>(TurtleStrategy.class);
        for(int raceIndex=0;raceIndex<simulated;raceIndex++){
            long raceSeed=9_876L+raceIndex*7919L;TurtleCourse course=TurtleCourse.generate(new BlockPos(0,80,0),12_345L+raceIndex,(raceIndex&1)==0);
            List<RaceEntry> entries=new ArrayList<>();for(int i=0;i<8;i++)entries.add(RaceEntry.ai(AiTurtleCatalog.all().get((i+raceIndex)%AiTurtleCatalog.all().size()),TurtleLeague.values()[1+raceIndex%4],raceSeed,i));
            RaceSimulation race=new RaceSimulation(course,TurtleWeather.values()[raceIndex%TurtleWeather.values().length],raceSeed,entries);
            List<String> previous=entries.stream().map(RaceEntry::entryId).toList();
            java.util.Map<String,Integer> rankAtSeventy=null;
            while(!race.complete()){race.tick();List<String> current=race.standings().stream().map(RaceEntry::entryId).toList();for(int i=0;i<current.size();i++)if(!current.get(i).equals(previous.get(i)))rankChanges++;previous=current;if(rankAtSeventy==null&&race.standings().getFirst().progress()>=course.length()*.70){rankAtSeventy=new java.util.HashMap<>();for(int i=0;i<race.standings().size();i++)rankAtSeventy.put(race.standings().get(i).entryId(),i+1);}}
            if(race.standings().stream().noneMatch(RaceEntry::finished))throw new IllegalStateException("No AI turtle completed validation race");
            RaceEntry winner=race.standings().getFirst();if(rankAtSeventy!=null&&rankAtSeventy.getOrDefault(winner.entryId(),1)>=4)lateComebacks++;if(winner.strategy()==TurtleStrategy.FOLLOW||winner.strategy()==TurtleStrategy.CLOSER)tacticalWins++;observedStrategyWins.merge(winner.strategy(),1,Integer::sum);
            totalTicks+=race.currentTick();laneChanges+=race.events().stream().filter(e->e.type().equals("RACING_LINE")||e.type().equals("LANE_CHANGE")).count();winners.add(winner.entryId());winnerCounts.merge(winner.entryId(),1,Integer::sum);
            hops+=race.hopCount();if(race.hopCount()>0)racesWithHops++;
        }
        if(laneChanges<simulated*4L)throw new IllegalStateException("Racing-line decisions are too rare: "+laneChanges);
        if(rankChanges<simulated*10L)throw new IllegalStateException("Race order is too static: "+rankChanges);
        if(winners.size()<8)throw new IllegalStateException("Winner diversity is too low: "+winners);
        if(lateComebacks<1)throw new IllegalStateException("Late comeback wins are too rare: "+lateComebacks);
        if(tacticalWins<16)throw new IllegalStateException("Follow/closer wins are too rare: "+tacticalWins);
        if(racesWithHops<simulated/6)throw new IllegalStateException("Hops are too hard to observe: "+hops+" in "+racesWithHops+"/"+simulated+" races");
        // A five-block course produces slightly denser traffic: allow fewer than 0.8 hops per
        // turtle while still rejecting constant leapfrogging.
        if(hops>simulated*6.4)throw new IllegalStateException("Hops are too frequent: "+hops+" in "+simulated+" races");
        int observedMinimum=(int)Math.ceil(simulated*.05);for(TurtleStrategy strategy:TurtleStrategy.values())if(observedStrategyWins.getOrDefault(strategy,0)<observedMinimum)throw new IllegalStateException(strategy+" is not viable with the real AI roster: "+observedStrategyWins+", winners="+winnerCounts);
        int observedMaximum=(int)Math.floor(simulated*.50);for(Map.Entry<TurtleStrategy,Integer> result:observedStrategyWins.entrySet())if(result.getValue()>observedMaximum)throw new IllegalStateException(result.getKey()+" dominates the real AI roster: "+observedStrategyWins+", winners="+winnerCounts);
        int controlled=96;Map<TurtleStrategy,Integer> controlledStrategyWins=validateStrategyBalance(controlled);
        // Equal balanced stats are intentionally a front-running profile. Tight cross-strategy
        // balance is validated with each strategy's recommended 24-session build instead.
        int minimum=1,maximum=(int)Math.floor(controlled*.90);
        for(TurtleStrategy strategy:TurtleStrategy.values()){int wins=controlledStrategyWins.getOrDefault(strategy,0);if(wins<minimum)throw new IllegalStateException(strategy+" controlled win rate is too low: "+wins+"/"+controlled+" "+controlledStrategyWins);if(wins>maximum)throw new IllegalStateException(strategy+" controlled win rate is too high: "+wins+"/"+controlled+" "+controlledStrategyWins);}
        return new Report(generated,courses,simulated,totalTicks,laneChanges,rankChanges,winners.size(),lateComebacks,tacticalWins,hops,racesWithHops,Map.copyOf(observedStrategyWins),controlled,Map.copyOf(controlledStrategyWins),System.currentTimeMillis()-started);
    }

    private static void validatePendingRerollPersistence() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID turtleId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TurtlePlayerProgress progress = new TurtlePlayerProgress(playerId);
        TurtlePlayerProgress.PendingPassiveReroll expected = new TurtlePlayerProgress.PendingPassiveReroll(
                turtleId, 2, "steady_rhythm", "final_surge");
        progress.setPendingPassiveReroll(expected);
        TurtlePlayerProgress restored = TurtlePlayerProgress.load(progress.save());
        if (!restored.pendingPassiveReroll().orElseThrow().equals(expected)) {
            throw new IllegalStateException("Pending passive reroll was not preserved by saved data");
        }
        if (!restored.clearPendingPassiveReroll() || restored.pendingPassiveReroll().isPresent()) {
            throw new IllegalStateException("Pending passive reroll could not be completed");
        }
    }

    private static void validateStrategyTicketFlow() {
        UUID playerId=UUID.fromString("00000000-0000-0000-0000-000000000011");
        TurtleStrategySelectionGuard guard=new TurtleStrategySelectionGuard();
        UUID cancelled=guard.open(playerId,net.minecraft.world.InteractionHand.MAIN_HAND,100);
        if(!guard.cancel(playerId,cancelled)||guard.claim(playerId,cancelled,10)!=null)
            throw new IllegalStateException("Cancelled strategy selection remained claimable");
        UUID oneShot=guard.open(playerId,net.minecraft.world.InteractionHand.OFF_HAND,100);
        TurtleStrategySelectionGuard.Pending claimed=guard.claim(playerId,oneShot,50);
        if(claimed==null||claimed.hand()!=net.minecraft.world.InteractionHand.OFF_HAND
                ||guard.claim(playerId,oneShot,50)!=null)
            throw new IllegalStateException("Strategy selection was not a hand-bound one-shot transaction");
        UUID expired=guard.open(playerId,net.minecraft.world.InteractionHand.MAIN_HAND,100);
        if(guard.claim(playerId,expired,101)!=null)
            throw new IllegalStateException("Expired strategy selection remained claimable");

        TurtlePlayerProgress fresh=new TurtlePlayerProgress(playerId);
        if(TurtleMenuService.introStrategyEggDecision(fresh,false)!=TurtleMenuService.IntroStrategyEggDecision.SEND_MAIL)
            throw new IllegalStateException("Fresh player would not receive the strategy egg mail");
        if(TurtleMenuService.introStrategyEggDecision(fresh,true)!=TurtleMenuService.IntroStrategyEggDecision.MARK_DELIVERED)
            throw new IllegalStateException("Queued intro mail would be duplicated");
        fresh.claimReward(TurtleMenuService.INTRO_STRATEGY_EGG_REWARD);
        if(TurtleMenuService.introStrategyEggDecision(fresh,false)!=TurtleMenuService.IntroStrategyEggDecision.NONE)
            throw new IllegalStateException("Delivered intro mail would be repeated");
    }

    private static void validateProjectedTraining() {
        EnumMap<TurtleSurface,TurtleAptitude> surfaces=new EnumMap<>(TurtleSurface.class);
        for(TurtleSurface surface:TurtleSurface.values())surfaces.put(surface,TurtleAptitude.B);
        EnumMap<TurtleStrategy,TurtleAptitude> strategies=new EnumMap<>(TurtleStrategy.class);
        for(TurtleStrategy strategy:TurtleStrategy.values())strategies.put(strategy,TurtleAptitude.B);
        for(TurtleStrategy strategy:TurtleStrategy.values()){
            TurtleStats stats=TurtleGenerationService.generateStats(new SplittableRandom(0x50524F4A454354L+strategy.ordinal()),
                    TurtleRarity.RARE,TurtleArchetype.valueOf(strategy.name()));
            TurtleData turtle=new TurtleData(UUID.randomUUID(),UUID.randomUUID(),TurtleNameCatalog.ids().getFirst(),
                    TurtleRarity.RARE,TurtleArchetype.valueOf(strategy.name()),stats.copy(),stats.copy(),
                    ActiveSkill.UNTURNED_HEART,List.of("light_steps","long_breath","good_sense_of_direction","early_rhythm"),
                    surfaces,strategies,TurtleAptitude.B,TurtleAptitude.B,TurtleAptitude.B,"natural",0L);
            turtle.setStrategy(strategy);
            TurtleStats projected=TurtleGuidance.projectedFinalStats(turtle);
            if(projected.total()!=TurtleRarity.RARE.finalBudget())
                throw new IllegalStateException("Projected Shift preview missed final budget: "+strategy+" "+projected);
            for(TurtleStat stat:TurtleStat.values())if(projected.get(stat)<stats.get(stat)
                    ||projected.get(stat)>TurtleRarity.RARE.finalStatCap())
                throw new IllegalStateException("Projected Shift preview violates stat bounds: "+strategy+" "+projected);
        }
    }

    private static void validateGeneratedNames() {
        Set<String> names=new HashSet<>();
        SplittableRandom random=new SplittableRandom(0x4E414D4553L);
        for(int i=0;i<40;i++){
            String name=TurtleNameCatalog.pick(random,names);
            if(!names.add(name))throw new IllegalStateException("Generated turtle name was duplicated: "+name);
            if(name.codePointCount(0,name.length())>TurtleData.MAX_NAME_CODE_POINTS)throw new IllegalStateException("Generated turtle name exceeds payload limit: "+name);
        }
    }

    private static void validateBodyAppearances(){
        Set<String> plausible=Set.of("natural","moss","sand","umber","slate","leucistic","melanistic","gold");
        List<TurtleBodyAppearanceCatalog.Appearance> values=TurtleBodyAppearanceCatalog.values();
        if(values.size()!=plausible.size()||!values.stream().map(TurtleBodyAppearanceCatalog.Appearance::id).collect(java.util.stream.Collectors.toSet()).equals(plausible))
            throw new IllegalStateException("Body appearance catalog contains a missing or fantasy color: "+values);
        int total=values.stream().mapToInt(TurtleBodyAppearanceCatalog.Appearance::weight).sum();
        int rareWeight=values.stream().filter(TurtleBodyAppearanceCatalog.Appearance::rare).mapToInt(TurtleBodyAppearanceCatalog.Appearance::weight).sum();
        if(total!=TurtleBodyAppearanceCatalog.TOTAL_WEIGHT||rareWeight!=TurtleBodyAppearanceCatalog.RARE_WEIGHT||!TurtleBodyAppearanceCatalog.isRare("gold"))
            throw new IllegalStateException("Body appearance probability invariant failed: "+total+"/"+rareWeight);
        for(var value:values){
            String path="/assets/yoiko_core/textures/entity/turtle/body/"+value.id()+".png";
            if(TurtleRacingValidator.class.getResource(path)==null)throw new IllegalStateException("Missing body texture: "+path);
        }
        SplittableRandom random=new SplittableRandom(0x425F434F4C4F5253L);int rare=0;Set<String> observed=new HashSet<>();
        int samples=200_000;
        for(int i=0;i<samples;i++){var value=TurtleBodyAppearanceCatalog.roll(random);observed.add(value.id());if(value.rare())rare++;}
        double ratio=rare/(double)samples;
        if(!observed.equals(plausible)||ratio<.0043||ratio>.0057)throw new IllegalStateException("Body appearance sample invariant failed: rare="+ratio+", observed="+observed);

        Set<String> expectedAiColors=Set.of("natural","moss","sand","umber","slate");
        Set<String> generatedAiColors=new HashSet<>();
        List<AiTurtleProfile> profiles=AiTurtleCatalog.all();
        for(int i=0;i<2_000;i++){
            AiTurtleProfile profile=profiles.get(i%profiles.size());
            RaceEntry entry=RaceEntry.ai(profile,TurtleLeague.OPEN,0x41495F524143454CL+i*104_729L,i%8);
            if(TurtleBodyAppearanceCatalog.isRare(entry.bodyAppearance()))throw new IllegalStateException("Race AI received a rare body color: "+entry.bodyAppearance());
            if(!entry.bodyAppearance().equals(AiTurtleCatalog.bodyAppearance(profile.id())))throw new IllegalStateException("AI mascot body color changed: "+profile.id());
            generatedAiColors.add(entry.bodyAppearance());
        }
        if(!generatedAiColors.equals(expectedAiColors))throw new IllegalStateException("AI mascot catalog does not cover the full common body pool: "+generatedAiColors);
    }

    private static Map<TurtleStrategy,Integer> validateStrategyBalance(int races){Map<TurtleStrategy,Integer>wins=new EnumMap<>(TurtleStrategy.class);TurtleStats equalStats=new TurtleStats(650,650,650,650,650);TurtleStrategy[] strategies=TurtleStrategy.values();for(int raceIndex=0;raceIndex<races;raceIndex++){long seed=0x51A7E000L+raceIndex*104_729L;TurtleCourse course=TurtleCourse.validationUniform(new BlockPos(0,80,0),seed^0xC0A5EL);List<RaceEntry> entries=new ArrayList<>();for(int slot=0;slot<8;slot++){TurtleStrategy strategy=strategies[(slot/2+raceIndex)%strategies.length];int lane=Math.floorMod(slot+raceIndex*3,8);entries.add(RaceEntry.validation(strategy.name().toLowerCase()+":"+raceIndex+":"+slot,equalStats,ActiveSkill.UNTURNED_HEART,List.of(),strategy,lane));}RaceSimulation race=new RaceSimulation(course,TurtleWeather.CLEAR,seed,entries);while(!race.complete())race.tick();wins.merge(race.standings().getFirst().strategy(),1,Integer::sum);}return wins;}
    private static void validateCompactCourse(TurtleCourse course){
        if(course.length()<140||course.length()>195)throw new IllegalStateException("Course length invariant failed: "+course.length());
        TurtleSurface seam=course.startFinishSurface();
        if(seam==TurtleSurface.PUDDLE)throw new IllegalStateException("Start/finish seam cannot be water");
        long puddleSamples=course.samples().stream().filter(sample->sample.surface()==TurtleSurface.PUDDLE).count();
        double waterRatio=puddleSamples/(double)Math.max(1,course.samples().size());
        if(waterRatio<.40||waterRatio>.48)throw new IllegalStateException("Water ratio invariant failed: "+waterRatio);
        for(double distance=0;distance<=TurtleCourse.START_FINISH_SAFE_DISTANCE;distance+=.25){
            TurtleSurface afterStart=course.sampleAt(distance).surface();
            TurtleSurface beforeFinish=course.sampleAt(course.length()-distance).surface();
            if(afterStart!=seam||beforeFinish!=seam)throw new IllegalStateException("Start/finish road is not continuous: distance="+distance+", start="+afterStart+", finish="+beforeFinish+", expected="+seam);
        }
        for(TurtleCourse.Sample sample:course.samples())if(Math.abs(sample.x()-.5)>29.1||Math.abs(sample.z()-.5)>21.1)
            throw new IllegalStateException("Course escaped compact arena: "+sample.x()+","+sample.z());
        TurtleCourse.Sample start=course.sampleAt(0);
        if(start.z()<course.samples().stream().mapToDouble(TurtleCourse.Sample::z).max().orElse(start.z())-.5||Math.abs(start.x()-.5)>1.0)
            throw new IllegalStateException("Start line is not centred on the podium-side straight: "+start.x()+","+start.z());
        boolean checkedCornerAdvantage=false;
        for(TurtleCourse.Sample sample:course.samples())if(sample.curvature()>.25){double inner=RaceEntry.laneCenterOffset(sample.turnSign()>0?7:0),outer=RaceEntry.laneCenterOffset(sample.turnSign()>0?0:7);double innerScale=course.distanceScale(sample.distance(),inner),outerScale=course.distanceScale(sample.distance(),outer);if(innerScale>=outerScale-.01)throw new IllegalStateException("Inner lane is not shorter at corner: "+innerScale+" >= "+outerScale);checkedCornerAdvantage=true;}
        if(!checkedCornerAdvantage)throw new IllegalStateException("Course has no measurable rounded corner");
        Set<BlockPos> road=new HashSet<>();
        for(TurtleCourse.Sample sample:course.samples())for(int offset=-2;offset<=2;offset++)road.add(trackBlock(sample,offset));
        for(TurtleCourse.Sample sample:course.samples())for(int lane:new int[]{0,7}){
            double offset=RaceEntry.laneCenterOffset(lane);
            BlockPos occupied=new BlockPos((int)Math.floor(sample.x()+sample.normalX()*offset),-1,
                    (int)Math.floor(sample.z()+sample.normalZ()*offset));
            if(!road.contains(occupied))throw new IllegalStateException("Outer lane escaped road mask: lane="+lane+", block="+occupied);
        }
        for(int lane=0;lane<8;lane++){
            double offset=RaceEntry.laneCenterOffset(lane);TurtleCourse.TrackPoint previous=course.trackPoint(0,offset);
            TurtleCourse.TrackPoint waiting=course.startGridPoint(-TurtleCourse.START_WAIT_DISTANCE,offset);
            double waitingDx=waiting.x()-previous.x(),waitingDz=waiting.z()-previous.z();
            double behind=waitingDx*previous.tangentX()+waitingDz*previous.tangentZ();
            if(behind>=-.75)throw new IllegalStateException("Start grid is not behind the start line: lane="+lane+", behind="+behind);
            for(double progress=.05;progress<=course.length();progress+=.05){
                TurtleCourse.TrackPoint current=course.trackPoint(progress,offset);double dx=current.x()-previous.x(),dz=current.z()-previous.z();
                double distance=Math.hypot(dx,dz),forward=dx*current.tangentX()+dz*current.tangentZ();
                if(distance<.002||distance>.18||forward<-.001){TurtleCourse.Sample sample=course.sampleAt(progress);throw new IllegalStateException("Lane path stalled, jumped, or reversed: lane="+lane+", progress="+progress+", distance="+distance+", forward="+forward+", curvature="+sample.curvature()+", turn="+sample.turnSign());}
                previous=current;
            }
        }
        Set<BlockPos> boundary=TurtleArenaManager.deriveRoadBoundary(road,0);
        for(BlockPos curb:boundary){
            boolean touchesRoad=road.contains(new BlockPos(curb.getX()+1,-1,curb.getZ()))
                    ||road.contains(new BlockPos(curb.getX()-1,-1,curb.getZ()))
                    ||road.contains(new BlockPos(curb.getX(),-1,curb.getZ()+1))
                    ||road.contains(new BlockPos(curb.getX(),-1,curb.getZ()-1));
            if(!touchesRoad)throw new IllegalStateException("Detached curb block: "+curb);
        }
    }
    private static BlockPos trackBlock(TurtleCourse.Sample sample,double offset){return new BlockPos(
            (int)Math.floor(sample.x()+sample.normalX()*offset),-1,
            (int)Math.floor(sample.z()+sample.normalZ()*offset));}
}
