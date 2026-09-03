package com.yoiko.core.turtle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import com.yoiko.core.turtle.arena.TurtleArenaTheme;
import net.minecraft.server.level.ServerPlayer;

public final class TurtleManualCompetitionService {
    public record Plan(String id,UUID operator,TurtleLeague league,boolean official,int registrationMinutes,long seed,boolean beach,String theme,TurtleWeather weather,long expiresAt){}
    private static final Map<String,Plan> PLANS=new HashMap<>();
    private TurtleManualCompetitionService(){}
    public static Plan prepare(ServerPlayer operator,TurtleLeague league,boolean official,int registrationMinutes,String theme,TurtleWeather requestedWeather,Long requestedSeed){
        Plan plan=createPlan(operator,league,official,registrationMinutes,theme,requestedWeather,requestedSeed);
        PLANS.values().removeIf(value->value.operator().equals(operator.getUUID()));PLANS.put(plan.id(),plan);return plan;
    }
    public static Plan start(ServerPlayer operator,TurtleLeague league,boolean official,int registrationMinutes,String theme,TurtleWeather requestedWeather,Long requestedSeed){
        Plan plan=createPlan(operator,league,official,registrationMinutes,theme,requestedWeather,requestedSeed);
        TurtleRacingManager.get().startManual(plan.league(),plan.official(),plan.registrationMinutes(),plan.seed(),plan.beach(),plan.theme(),plan.weather());
        return plan;
    }
    private static Plan createPlan(ServerPlayer operator,TurtleLeague league,boolean official,int registrationMinutes,String theme,TurtleWeather requestedWeather,Long requestedSeed){
        if(league==TurtleLeague.TRAINING_D)throw TurtleLocalizedException.of("yoiko_core.turtle.error.manual_training_league");
        TurtleRacingManager.get().validateManualStart(official);
        long seed=requestedSeed==null?operator.getRandom().nextLong():requestedSeed;
        String normalizedTheme=theme==null?"":theme.toLowerCase(java.util.Locale.ROOT);
        if(!TurtleArenaTheme.ids().contains(normalizedTheme))throw TurtleLocalizedException.of("yoiko_core.turtle.error.manual_theme_invalid",theme);
        TurtleArenaTheme arenaTheme=TurtleArenaTheme.fromId(normalizedTheme);
        boolean beach=arenaTheme==TurtleArenaTheme.BEACH_FESTIVAL||arenaTheme==TurtleArenaTheme.MANGROVE_BOARDWALK;
        TurtleWeather weather=requestedWeather==null?defaultWeather(beach):requestedWeather;
        String id=UUID.randomUUID().toString().substring(0,8);
        return new Plan(id,operator.getUUID(),league,official,Math.max(3,Math.min(30,registrationMinutes)),seed,beach,normalizedTheme,weather,System.currentTimeMillis()+60_000L);
    }
    public static TurtleWeather defaultWeather(String theme){String normalized=theme==null?"":theme.toLowerCase(java.util.Locale.ROOT);boolean beach=normalized.equals(TurtleArenaTheme.BEACH_FESTIVAL.id())||normalized.equals(TurtleArenaTheme.MANGROVE_BOARDWALK.id());return defaultWeather(beach);}
    private static TurtleWeather defaultWeather(boolean beach){return beach?TurtleWeather.SEA_BREEZE:TurtleWeather.FOREST_MIST;}
    public static Plan confirm(ServerPlayer operator,String id){Plan plan=PLANS.get(id);if(plan==null||System.currentTimeMillis()>plan.expiresAt())throw TurtleLocalizedException.of("yoiko_core.turtle.error.manual_plan_expired",60);if(!plan.operator().equals(operator.getUUID()))throw TurtleLocalizedException.of("yoiko_core.turtle.error.manual_plan_owner");TurtleRacingManager.get().startManual(plan.league(),plan.official(),plan.registrationMinutes(),plan.seed(),plan.beach(),plan.theme(),plan.weather());PLANS.remove(id);return plan;}
    public static Stream<String> planIds(UUID operator){long now=System.currentTimeMillis();PLANS.values().removeIf(plan->plan.expiresAt()<now);return PLANS.values().stream().filter(plan->operator==null||plan.operator().equals(operator)).map(Plan::id);}
}
