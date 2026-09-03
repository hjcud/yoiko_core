package com.yoiko.core.turtle;

import com.yoiko.core.turtle.race.TurtleCourse;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only guidance derived from the turtle's real loadout; it never changes race values. */
public final class TurtleStrategyForecast {
    private static final int MAX_CACHE = 512;
    private static final Map<CacheKey,List<String>> CACHE = new LinkedHashMap<>(128,.75F,true){
        @Override protected boolean removeEldestEntry(Map.Entry<CacheKey,List<String>> eldest){return size()>MAX_CACHE;}
    };
    private record CacheKey(UUID turtleId,long revision,long courseSignature) { }

    public record CourseFeatureSummary(EnumSet<TurtleSurface> surfaces,TurtleWeather weather,long signature){
        public CourseFeatureSummary{surfaces=surfaces.clone();}
        public static CourseFeatureSummary from(TurtleCourse course,TurtleWeather weather){
            EnumSet<TurtleSurface> surfaces=EnumSet.noneOf(TurtleSurface.class);
            long signature=weather.ordinal()+1L;
            if(course!=null){
                course.samples().forEach(sample->surfaces.add(sample.surface()));
                int mask=0;for(TurtleSurface surface:surfaces)mask|=1<<surface.ordinal();
                signature=31L*signature+course.seed();signature=31L*signature+course.themeId().hashCode();
                signature=31L*signature+Double.doubleToLongBits(Math.rint(course.length()*4.0)/4.0);signature=31L*signature+mask;
            }
            return new CourseFeatureSummary(surfaces,weather,signature);
        }
    }

    private TurtleStrategyForecast() { }

    public static List<String> all(TurtleData turtle,CourseFeatureSummary course){
        CacheKey key=new CacheKey(turtle.id(),turtle.revision(),course.signature());
        synchronized(CACHE){List<String> cached=CACHE.get(key);if(cached!=null)return cached;}
        List<String> result=Arrays.stream(TurtleStrategy.values()).map(strategy->describe(turtle,strategy,course)).toList();
        synchronized(CACHE){CACHE.put(key,result);}return result;
    }

    private static String describe(TurtleData turtle,TurtleStrategy strategy,CourseFeatureSummary course){
        double speed=turtle.stats().get(TurtleStat.SPEED)/100.0,power=turtle.stats().get(TurtleStat.POWER)/100.0;
        double stamina=turtle.stats().get(TurtleStat.STAMINA)/100.0,calm=turtle.stats().get(TurtleStat.CALM)/100.0,nav=turtle.stats().get(TurtleStat.NAVIGATION)/100.0;
        double early=.52*speed+.30*power+.18*calm+(strategy==TurtleStrategy.FRONT?.16:strategy==TurtleStrategy.CLOSER?-.14:0);
        double reserve=.72*stamina-.30*Math.pow(speed,1.55)+(strategy==TurtleStrategy.FRONT?-.04:strategy==TurtleStrategy.FOLLOW||strategy==TurtleStrategy.CLOSER?.07:.03);
        double finish=.38*speed+.34*power+.28*stamina+(strategy==TurtleStrategy.CLOSER?.14:strategy==TurtleStrategy.FOLLOW?.08:0)+lateSkill(turtle);
        double corner=.55*nav+.45*calm+(strategy==TurtleStrategy.FRONT?.06:strategy==TurtleStrategy.CLOSER?.04:0);
        return level(early)+"|"+level(reserve)+"|"+level(finish)+"|"+level(corner)+"|"+courseFit(turtle,course);
    }

    private static double lateSkill(TurtleData turtle){return switch(turtle.activeSkill()){case LIMIT_SPRINT->.13;case THOUSAND_YEAR_STEP,HOMEWARD_WAVE,FINAL_GAP->.10;default->turtle.passives().stream().anyMatch(id->id.equals("final_savings")||id.equals("comeback_star")||id.equals("ancient_patience"))?.05:0;};}
    private static String courseFit(TurtleData turtle,CourseFeatureSummary course){
        if(course.surfaces().isEmpty())return "UNSET";int matches=0;
        for(String tag:turtle.activeSkill().tags())if(matches(tag,course))matches++;
        for(String id:turtle.passives().subList(0,turtle.activePassiveSlots())){PassiveSkill passive=PassiveSkillCatalog.get(id);for(String tag:passive.tags())if(matches(tag,course)){matches++;break;}}
        return matches>=3?"VERY_GOOD":matches>=1?"GOOD":"NORMAL";
    }
    private static boolean matches(String tag,CourseFeatureSummary course){return switch(tag){
        case "SAND"->course.surfaces().contains(TurtleSurface.SAND);
        case "PUDDLE"->course.surfaces().contains(TurtleSurface.PUDDLE);case "MUD"->course.surfaces().contains(TurtleSurface.MUD);
        case "CLEAR"->course.weather()==TurtleWeather.CLEAR;case "RAIN"->course.weather()==TurtleWeather.LIGHT_RAIN;
        case "WIND"->course.weather()==TurtleWeather.SEA_BREEZE;case "MIST"->course.weather()==TurtleWeather.FOREST_MIST;
        default->false;};}
    private static String level(double value){return value>=.76?"HIGH":value>=.56?"NORMAL":value>=.38?"LOW":"VERY_LOW";}
}
