package com.yoiko.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.yoiko.core.turtle.TurtleData;
import com.yoiko.core.turtle.ActiveSkill;
import com.yoiko.core.turtle.TurtleBettingRules;
import com.yoiko.core.turtle.TurtleLeague;
import com.yoiko.core.turtle.TurtleGrowthPolicy;
import com.yoiko.core.turtle.TurtleLocalizedException;
import com.yoiko.core.turtle.TurtleManualCompetitionService;
import com.yoiko.core.turtle.TurtleMenuService;
import com.yoiko.core.turtle.TurtlePlayerProgress;
import com.yoiko.core.turtle.TurtleRaceClass;
import com.yoiko.core.turtle.TurtleRacingManager;
import com.yoiko.core.turtle.TurtleRacingSavedData;
import com.yoiko.core.turtle.TurtleStrategy;
import com.yoiko.core.turtle.TurtleTicketType;
import com.yoiko.core.turtle.TurtleTrainingService;
import com.yoiko.core.turtle.TurtleTrainingType;
import com.yoiko.core.turtle.TurtleCompanion;
import com.yoiko.core.turtle.TurtleWeather;
import com.yoiko.core.turtle.race.TurtleCompetitionData;
import com.yoiko.core.turtle.race.TurtleCourse;
import com.yoiko.core.turtle.race.TurtleSkillEffectPreviewManager;
import com.yoiko.core.turtle.arena.TurtleArenaTheme;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TurtleCommand {
    private TurtleCommand(){}

    static LiteralArgumentBuilder<CommandSourceStack> playerCommands(){
        return Commands.literal("turtle")
                .executes(c->menu(c.getSource()))
                .then(YoikoCommandHelp.turtleHelpCommand())
                .then(Commands.literal("menu").executes(c->menu(c.getSource())))
                .then(Commands.literal("status").executes(c->status(c.getSource())))
                .then(Commands.literal("intro-ticket")
                        .executes(c->claimIntroTicket(c.getSource())))
                .then(Commands.literal("list").executes(c->list(c.getSource(),1)).then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c->list(c.getSource(),IntegerArgumentType.getInteger(c,"page")))))
                .then(Commands.literal("train").then(Commands.argument("index",IntegerArgumentType.integer(1)).suggests(TurtleCommand::suggestTurtleIndexes).then(Commands.argument("type",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(java.util.Arrays.stream(TurtleTrainingType.values()).map(v->v.name().toLowerCase(Locale.ROOT)),b)).executes(c->train(c.getSource(),IntegerArgumentType.getInteger(c,"index"),StringArgumentType.getString(c,"type"))))))
                .then(Commands.literal("rename").then(Commands.argument("index",IntegerArgumentType.integer(1)).suggests(TurtleCommand::suggestTurtleIndexes).then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->rename(c.getSource(),IntegerArgumentType.getInteger(c,"index"),StringArgumentType.getString(c,"name"))))))
                .then(Commands.literal("lock").then(Commands.argument("index",IntegerArgumentType.integer(1)).suggests(TurtleCommand::suggestTurtleIndexes).then(Commands.argument("locked",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"on","off"},b)).executes(c->lock(c.getSource(),IntegerArgumentType.getInteger(c,"index"),StringArgumentType.getString(c,"locked"))))))
                .then(Commands.literal("strategy").then(Commands.argument("index",IntegerArgumentType.integer(1)).suggests(TurtleCommand::suggestTurtleIndexes).then(Commands.argument("strategy",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(java.util.Arrays.stream(TurtleStrategy.values()).map(v->v.name().toLowerCase(Locale.ROOT)),b)).executes(c->strategy(c.getSource(),IntegerArgumentType.getInteger(c,"index"),StringArgumentType.getString(c,"strategy"))))))
                .then(Commands.literal("companion").then(Commands.argument("index",IntegerArgumentType.integer(1)).suggests(TurtleCommand::suggestTurtleIndexes).then(Commands.argument("companion",StringArgumentType.word()).suggests(TurtleCommand::suggestUnlockedCompanions).executes(c->companion(c.getSource(),IntegerArgumentType.getInteger(c,"index"),StringArgumentType.getString(c,"companion"))))))
                .then(Commands.literal("register").then(Commands.argument("index",IntegerArgumentType.integer(1)).suggests(TurtleCommand::suggestTurtleIndexes).executes(c->register(c.getSource(),IntegerArgumentType.getInteger(c,"index")))))
                .then(Commands.literal("unregister").executes(c->unregister(c.getSource())))
                .then(Commands.literal("time-trial")
                        .then(Commands.literal("status").executes(c->timeTrialStatus(c.getSource())))
                        .then(Commands.literal("start").then(Commands.argument("index",IntegerArgumentType.integer(1)).suggests(TurtleCommand::suggestTurtleIndexes).then(Commands.argument("difficulty",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"d","c","b","a","s"},b)).executes(c->startTimeTrial(c.getSource(),IntegerArgumentType.getInteger(c,"index"),StringArgumentType.getString(c,"difficulty"))))))
                        .then(Commands.literal("cancel").executes(c->cancelTimeTrial(c.getSource()))))
                .then(Commands.literal("bet").then(Commands.argument("heat",IntegerArgumentType.integer(1,3)).suggests(TurtleCommand::suggestHeats).then(Commands.argument("candidate",StringArgumentType.word()).suggests(TurtleCommand::suggestBetCandidates).then(Commands.argument("amount",LongArgumentType.longArg(TurtleBettingRules.MIN_STAKE,TurtleBettingRules.MAX_STAKE_PER_RACE)).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"20","50","100","200"},b)).executes(c->bet(c.getSource(),IntegerArgumentType.getInteger(c,"heat"),StringArgumentType.getString(c,"candidate"),LongArgumentType.getLong(c,"amount")))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommands(){
        return Commands.literal("race")
                .then(Commands.literal("status").executes(c->adminStatus(c.getSource())))
                .then(Commands.literal("force-start").executes(c->forceStart(c.getSource())))
                .then(Commands.literal("force-stop")
                        .executes(c->forceStop(c.getSource(),"ADMIN_FORCE_STOP"))
                        .then(Commands.argument("reason",StringArgumentType.greedyString())
                                .executes(c->forceStop(c.getSource(),StringArgumentType.getString(c,"reason")))))
                .then(Commands.literal("replay").then(Commands.literal("last").executes(c->replayLast(c.getSource()))))
                .then(Commands.literal("effect-preview")
                        .then(Commands.literal("all").executes(c->previewAllEffects(c.getSource())))
                        .then(Commands.literal("stop").executes(c->stopEffectPreview(c.getSource())))
                        .then(Commands.argument("skill",StringArgumentType.word()).suggests(TurtleCommand::suggestActiveSkills)
                                .executes(c->previewEffect(c.getSource(),StringArgumentType.getString(c,"skill")))))
                .then(Commands.literal("course")
                        .then(Commands.literal("inspect").executes(c->inspectCourse(c.getSource()))
                                .then(Commands.literal("visual").executes(c->inspectCourseVisual(c.getSource())))
                                .then(Commands.literal("lanes").executes(c->inspectCourseLanes(c.getSource())))
                                .then(Commands.literal("racing-line").executes(c->inspectRacingLine(c.getSource())))
                                .then(Commands.literal("clear").executes(c->clearCourseVisual(c.getSource())))))
                .then(Commands.literal("training").then(Commands.literal("give")
                        .then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("count",IntegerArgumentType.integer(1,10_000))
                                .suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"1","6","12","24"},b)).executes(c->giveTraining(c.getSource(),EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"count")))))))
                .then(Commands.literal("awakening").then(Commands.literal("give")
                        .then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("index",IntegerArgumentType.integer(1)).then(Commands.argument("count",IntegerArgumentType.integer(1,30))
                                .executes(c->giveAwakening(c.getSource(),EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"index"),IntegerArgumentType.getInteger(c,"count"))))))))
                .then(Commands.literal("medal").then(Commands.literal("give")
                        .then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("count",IntegerArgumentType.integer(1,1_000_000))
                                .executes(c->giveMedals(c.getSource(),EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"count")))))))
                .then(Commands.literal("race-points").then(Commands.literal("give")
                        .then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("index",IntegerArgumentType.integer(1)).then(Commands.argument("count",IntegerArgumentType.integer(1,10_000))
                                .executes(c->giveRacePoints(c.getSource(),EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"index"),IntegerArgumentType.getInteger(c,"count"))))))))
                .then(Commands.literal("arena")
                        .then(Commands.literal("set-center").executes(c->setCenter(c.getSource())))
                        .then(Commands.literal("cleanup").executes(c->cleanup(c.getSource())))
                        .then(Commands.literal("preview").then(Commands.argument("seed",LongArgumentType.longArg()).then(Commands.argument("theme",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(TurtleArenaTheme.ids(),b)).executes(c->preview(c.getSource(),LongArgumentType.getLong(c,"seed"),StringArgumentType.getString(c,"theme")))))))
                .then(Commands.literal("ticket")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player",EntityArgument.player())
                                        .then(Commands.argument("type",StringArgumentType.word())
                                                .suggests((c,b)->SharedSuggestionProvider.suggest(java.util.Arrays.stream(TurtleTicketType.values()).map(v->v.name().toLowerCase(Locale.ROOT)),b))
                                                .then(Commands.argument("count",IntegerArgumentType.integer(1,100))
                                                        .suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"1","5","10"},b))
                                                        .executes(c->giveTicket(c.getSource(),EntityArgument.getPlayer(c,"player"),StringArgumentType.getString(c,"type"),IntegerArgumentType.getInteger(c,"count"))))))))
                .then(Commands.literal("manual")
                        .then(Commands.literal("start")
                                .then(Commands.argument("mode",StringArgumentType.word()).suggests(TurtleCommand::suggestManualModes)
                                        .then(Commands.argument("league",StringArgumentType.word()).suggests(TurtleCommand::suggestManualLeagues)
                                                .then(Commands.argument("minutes",IntegerArgumentType.integer(3,30)).suggests(TurtleCommand::suggestManualMinutes)
                                                        .then(Commands.argument("theme",StringArgumentType.word()).suggests(TurtleCommand::suggestManualThemes)
                                                                .executes(c->startManual(c.getSource(),StringArgumentType.getString(c,"mode"),StringArgumentType.getString(c,"league"),IntegerArgumentType.getInteger(c,"minutes"),StringArgumentType.getString(c,"theme"),null))
                                                                .then(Commands.argument("weather",StringArgumentType.word()).suggests(TurtleCommand::suggestManualWeather)
                                                                        .executes(c->startManual(c.getSource(),StringArgumentType.getString(c,"mode"),StringArgumentType.getString(c,"league"),IntegerArgumentType.getInteger(c,"minutes"),StringArgumentType.getString(c,"theme"),StringArgumentType.getString(c,"weather")))))))))
                        .then(Commands.literal("prepare")
                                .then(Commands.argument("mode",StringArgumentType.word()).suggests(TurtleCommand::suggestManualModes)
                                        .then(Commands.argument("league",StringArgumentType.word()).suggests(TurtleCommand::suggestManualLeagues)
                                                .then(Commands.argument("minutes",IntegerArgumentType.integer(3,30)).suggests(TurtleCommand::suggestManualMinutes)
                                                        .then(Commands.argument("theme",StringArgumentType.word()).suggests(TurtleCommand::suggestManualThemes)
                                                                .executes(c->prepareManual(c.getSource(),StringArgumentType.getString(c,"mode"),StringArgumentType.getString(c,"league"),IntegerArgumentType.getInteger(c,"minutes"),StringArgumentType.getString(c,"theme"),null))
                                                                .then(Commands.argument("weather",StringArgumentType.word()).suggests(TurtleCommand::suggestManualWeather)
                                                                        .executes(c->prepareManual(c.getSource(),StringArgumentType.getString(c,"mode"),StringArgumentType.getString(c,"league"),IntegerArgumentType.getInteger(c,"minutes"),StringArgumentType.getString(c,"theme"),StringArgumentType.getString(c,"weather")))))))))
                        .then(Commands.literal("confirm").then(Commands.argument("plan",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(TurtleManualCompetitionService.planIds(c.getSource().getEntity() instanceof ServerPlayer p?p.getUUID():null),b)).executes(c->confirmManual(c.getSource(),StringArgumentType.getString(c,"plan")))))
                        .then(Commands.literal("cancel").then(Commands.argument("reason",StringArgumentType.greedyString()).executes(c->cancel(c.getSource(),StringArgumentType.getString(c,"reason"))))));
    }

    private static CompletableFuture<Suggestions> suggestTurtleIndexes(CommandContext<CommandSourceStack> context,
                                                                         SuggestionsBuilder builder) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) return builder.buildFuture();
        List<TurtleData> turtles = TurtleRacingSavedData.get(player.server).ownedBy(player.getUUID()).stream()
                .sorted(java.util.Comparator.comparingLong(TurtleData::acquiredAt)).toList();
        for (int i = 0; i < turtles.size(); i++) {
            TurtleData turtle = turtles.get(i);
            builder.suggest(Integer.toString(i + 1), Component.translatable("yoiko_core.turtle.command.suggestion.turtle",
                    turtle.displayName(),turtle.raceClass(),turtle.racePoints()));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestUnlockedCompanions(CommandContext<CommandSourceStack> context,
                                                                              SuggestionsBuilder builder) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) return builder.buildFuture();
        return SharedSuggestionProvider.suggest(TurtleRacingSavedData.get(player.server).getOrCreatePlayer(player.getUUID())
                .unlockedCompanions().stream().map(value -> value.name().toLowerCase(Locale.ROOT)), builder);
    }

    private static CompletableFuture<Suggestions> suggestManualModes(CommandContext<CommandSourceStack> context,SuggestionsBuilder builder){return SharedSuggestionProvider.suggest(new String[]{"exhibition","official"},builder);}
    private static CompletableFuture<Suggestions> suggestManualLeagues(CommandContext<CommandSourceStack> context,SuggestionsBuilder builder){return SharedSuggestionProvider.suggest(new String[]{"coral","current","abyss","open"},builder);}
    private static CompletableFuture<Suggestions> suggestManualMinutes(CommandContext<CommandSourceStack> context,SuggestionsBuilder builder){return SharedSuggestionProvider.suggest(new String[]{"3","5","10","15","30"},builder);}
    private static CompletableFuture<Suggestions> suggestManualThemes(CommandContext<CommandSourceStack> context,SuggestionsBuilder builder){return SharedSuggestionProvider.suggest(TurtleArenaTheme.ids(),builder);}
    private static CompletableFuture<Suggestions> suggestManualWeather(CommandContext<CommandSourceStack> context,SuggestionsBuilder builder){return SharedSuggestionProvider.suggest(java.util.Arrays.stream(TurtleWeather.values()).map(v->v.name().toLowerCase(Locale.ROOT)),builder);}
    private static CompletableFuture<Suggestions> suggestActiveSkills(CommandContext<CommandSourceStack> context,SuggestionsBuilder builder){return SharedSuggestionProvider.suggest(java.util.Arrays.stream(ActiveSkill.values()).map(ActiveSkill::id),builder);}

    private static CompletableFuture<Suggestions> suggestHeats(CommandContext<CommandSourceStack> context,
                                                                SuggestionsBuilder builder) {
        TurtleCompetitionData competition = TurtleRacingManager.get().competition();
        int count = competition == null ? 0 : competition.heats().size();
        return SharedSuggestionProvider.suggest(java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(Integer::toString), builder);
    }

    private static CompletableFuture<Suggestions> suggestBetCandidates(CommandContext<CommandSourceStack> context,
                                                                         SuggestionsBuilder builder) {
        try {
            int heat = IntegerArgumentType.getInteger(context, "heat") - 1;
            return SharedSuggestionProvider.suggest(TurtleRacingManager.get().bettingCandidates(heat), builder);
        } catch (RuntimeException ignored) {
            return builder.buildFuture();
        }
    }

    private static ServerPlayer player(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException{return source.getPlayerOrException();}
    private static int menu(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{TurtleMenuService.open(player(source));return 1;}
    private static TurtleData turtle(ServerPlayer player,int index){List<TurtleData> values=TurtleRacingSavedData.get(player.server).ownedBy(player.getUUID()).stream().sorted(java.util.Comparator.comparingLong(TurtleData::acquiredAt)).toList();if(index<1||index>values.size())throw TurtleLocalizedException.of("yoiko_core.turtle.error.invalid_turtle_index");return values.get(index-1);}
    private static int status(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);TurtleRacingSavedData saved=TurtleRacingSavedData.get(p.server);TurtlePlayerProgress progress=saved.getOrCreatePlayer(p.getUUID());TurtleCompetitionData c=saved.competition().orElse(null);source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.status.resources",progress.turtleIds().size(),40,progress.availableTraining(),progress.shellMedals()),false);source.sendSuccess(()->c==null?Component.translatable("yoiko_core.turtle.command.status.no_competition"):Component.translatable("yoiko_core.turtle.command.status.competition",league(c.league()),phase(c.phase()),c.registrations().size()),false);if(c!=null&&!c.heats().isEmpty())for(int i=0;i<c.heats().size();i++){int heat=i;source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.status.heat",heat+1,String.join(", ",TurtleRacingManager.get().bettingCandidates(heat))),false);}return 1;}
    private static int list(CommandSourceStack source,int page)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);List<TurtleData> values=TurtleRacingSavedData.get(p.server).ownedBy(p.getUUID()).stream().sorted(java.util.Comparator.comparingLong(TurtleData::acquiredAt)).toList();int start=(page-1)*10;for(int i=start;i<Math.min(values.size(),start+10);i++){TurtleData t=values.get(i);int number=i+1;source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.list.entry",number,t.rarity().symbol(),t.displayName(),t.raceClass().name(),t.racePoints(),t.stats().total(),Component.translatable("yoiko_core.turtle.skill.active."+t.activeSkill().id()+".name"),t.locked()?Component.translatable("yoiko_core.turtle.command.list.locked"):Component.empty()),false);}return 1;}
    private static int train(CommandSourceStack source,int index,String typeId)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);try{TurtleData t=turtle(p,index);TurtleTrainingService.train(p,t,TurtleTrainingType.valueOf(typeId.toUpperCase(Locale.ROOT)),t.revision());source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.train.complete",t.displayName(),t.stats().total(),t.trainingCount(),24),false);return 1;}catch(RuntimeException e){return fail(source,e);}}
    private static int rename(CommandSourceStack source,int index,String name)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);try{TurtleData t=turtle(p,index);t.rename(name);TurtleRacingSavedData.get(p.server).markChanged();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.rename.complete",t.displayName()),false);return 1;}catch(RuntimeException e){return fail(source,e);}}
    private static int lock(CommandSourceStack source,int index,String value)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);TurtleData t=turtle(p,index);boolean locked=switch(value.toLowerCase(Locale.ROOT)){case "true","on","1"->true;case "false","off","0"->false;default->throw TurtleLocalizedException.of("yoiko_core.turtle.error.lock_value");};t.setLocked(locked);TurtleRacingSavedData.get(p.server).markChanged();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.lock.complete",t.displayName(),Component.translatable(locked?"yoiko_core.turtle.ui.locked":"yoiko_core.turtle.ui.unlocked")),false);return 1;}
    private static int strategy(CommandSourceStack source,int index,String value)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);TurtleData t=turtle(p,index);try{TurtleGrowthPolicy.requireCompetitionUnlocked(t);}catch(RuntimeException e){return fail(source,e);}t.setStrategy(TurtleStrategy.valueOf(value.toUpperCase(Locale.ROOT)));TurtleRacingSavedData.get(p.server).markChanged();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.strategy.complete",t.displayName(),strategy(t.strategy())),false);return 1;}
    private static int companion(CommandSourceStack source,int index,String value)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);TurtleCompanion companion=TurtleCompanion.valueOf(value.toUpperCase(Locale.ROOT));TurtleRacingSavedData saved=TurtleRacingSavedData.get(p.server);if(!saved.getOrCreatePlayer(p.getUUID()).unlockedCompanions().contains(companion)){source.sendFailure(Component.translatable("yoiko_core.turtle.error.companion_locked"));return 0;}TurtleData t=turtle(p,index);try{TurtleGrowthPolicy.requireCompetitionUnlocked(t);}catch(RuntimeException e){return fail(source,e);}t.setCompanion(companion);saved.markChanged();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.companion.complete",t.displayName(),companion(companion)),false);return 1;}
    private static int register(CommandSourceStack source,int index)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);try{TurtleData t=turtle(p,index);TurtleRacingManager.get().register(p,t.id());source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.register.complete",t.displayName()),false);return 1;}catch(RuntimeException e){return fail(source,e);}}
    private static int unregister(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);try{TurtleRacingManager.get().unregister(p);source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.unregister.complete"),false);return 1;}catch(RuntimeException e){return fail(source,e);}}
    private static int startTimeTrial(CommandSourceStack source,int index,String difficulty)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);try{TurtleRacingManager.get().timeTrial().request(p,turtle(p,index),TurtleRaceClass.valueOf(difficulty.toUpperCase(Locale.ROOT)));source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.time_trial.requested"),false);return 1;}catch(RuntimeException e){return fail(source,e);}}
    private static int cancelTimeTrial(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);TurtleRacingManager.get().timeTrial().cancel(p.getUUID());source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.time_trial.cancelled"),false);return 1;}
    private static int timeTrialStatus(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);var tt=TurtleRacingManager.get().timeTrial();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.time_trial.status",Component.translatable("yoiko_core.turtle.ui.state."+tt.state().name().toLowerCase(Locale.ROOT)),Component.translatable("yoiko_core.turtle.ui.preset."+tt.presetId()),tt.queuePosition(p.getUUID())),false);return 1;}
    private static int bet(CommandSourceStack source,int heat,String id,long amount)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);try{TurtleRacingManager.get().bet(p,heat-1,id,amount);source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.bet.complete",heat,id,amount),false);return 1;}catch(RuntimeException e){return fail(source,e);}}
    private static int setCenter(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);TurtleRacingManager.get().arena().configure(p.serverLevel(),p.blockPosition());source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.arena.center_set",p.blockPosition().toShortString(),72,53).withStyle(ChatFormatting.YELLOW),true);return 1;}
    private static int cleanup(CommandSourceStack source){TurtleRacingManager.get().arena().recoverCleanup();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.arena.cleanup"),true);return 1;}
    private static int preview(CommandSourceStack source,long seed,String theme)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);boolean beach=(seed&1L)==0L;TurtleCourse course=TurtleCourse.generate(p.blockPosition(),seed,beach,theme);source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.arena.preview",seed,Component.translatable(beach?"yoiko_core.turtle.command.surface_profile.beach":"yoiko_core.turtle.command.surface_profile.forest"),course.themeId(),String.format(Locale.ROOT,"%.1f",course.length()),course.samples().size()),false);return 1;}
    private static int giveTicket(CommandSourceStack source,ServerPlayer target,String type,int count){TurtleTicketType ticket=TurtleTicketType.valueOf(type.toUpperCase(Locale.ROOT));net.minecraft.world.item.ItemStack stack=new net.minecraft.world.item.ItemStack(com.yoiko.core.registry.YoikoItems.turtleTicket(ticket),count);com.yoiko.core.item.TurtleHatchTicketItem.bindTo(stack,target.getUUID());if(!target.getInventory().add(stack))target.drop(stack,false);source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.give.ticket",target.getDisplayName(),stack.getHoverName(),count),true);return count;}
    private static int giveTraining(CommandSourceStack source,ServerPlayer target,int count){TurtleRacingSavedData saved=TurtleRacingSavedData.get(target.server);int added=saved.getOrCreatePlayer(target.getUUID()).grantBonusTraining(count);saved.markChanged();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.give.training",target.getGameProfile().getName(),added),true);return added;}
    private static int giveAwakening(CommandSourceStack source,ServerPlayer target,int index,int count){TurtleRacingSavedData saved=TurtleRacingSavedData.get(target.server);TurtleData turtle=ownedTurtle(saved,target,index);int before=turtle.awakeningPoints();turtle.addAwakeningPoints(count);saved.markChanged();int added=turtle.awakeningPoints()-before;source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.give.awakening",target.getGameProfile().getName(),turtle.displayName(),added),true);return added;}
    private static int giveMedals(CommandSourceStack source,ServerPlayer target,int count){TurtleRacingSavedData saved=TurtleRacingSavedData.get(target.server);saved.getOrCreatePlayer(target.getUUID()).addShellMedals(count);saved.markChanged();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.give.medals",target.getGameProfile().getName(),count),true);return count;}
    private static int giveRacePoints(CommandSourceStack source,ServerPlayer target,int index,int count){TurtleRacingSavedData saved=TurtleRacingSavedData.get(target.server);TurtleData turtle=ownedTurtle(saved,target,index);TurtleRaceClass before=turtle.raceClass();turtle.addRacePoints(count);saved.markChanged();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.give.race_points",turtle.displayName(),count,before.name(),turtle.raceClass().name()),true);return count;}
    private static TurtleData ownedTurtle(TurtleRacingSavedData saved,ServerPlayer target,int index){List<TurtleData> turtles=saved.ownedBy(target.getUUID()).stream().sorted(java.util.Comparator.comparingLong(TurtleData::acquiredAt)).toList();if(index<1||index>turtles.size())throw TurtleLocalizedException.of("yoiko_core.turtle.error.invalid_turtle_index");return turtles.get(index-1);}
    private static int claimIntroTicket(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer p=player(source);
        TurtleRacingSavedData saved=TurtleRacingSavedData.get(p.server);
        TurtlePlayerProgress progress=saved.getOrCreatePlayer(p.getUUID());
        String currentKey="intro:rare-strategy-egg:v4";
        if(progress.hasReward(currentKey)||!progress.claimReward(currentKey)){
            source.sendFailure(Component.translatable("yoiko_core.turtle.error.intro_ticket_claimed"));
            return 0;
        }
        net.minecraft.world.item.ItemStack stack=new net.minecraft.world.item.ItemStack(
                com.yoiko.core.registry.YoikoItems.TURTLE_RARE_STRATEGY_TICKET.get());
        com.yoiko.core.item.TurtleHatchTicketItem.bindTo(stack,p.getUUID());
        if(!p.getInventory().add(stack))p.drop(stack,false);
        saved.markChanged();
        source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.intro_ticket.received",stack.getHoverName())
                .withStyle(ChatFormatting.AQUA),false);
        return 1;
    }
    private static int forceStart(CommandSourceStack source){try{TurtleRacingManager.get().forceStart();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.force_start"),true);return 1;}catch(RuntimeException e){return fail(source,e);}}
    private static int forceStop(CommandSourceStack source,String reason){
        try{
            var result=TurtleRacingManager.get().forceStopCompetition(reason);
            source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.force_stop",result.competitionId(),phase(result.previousPhase()),result.refundedBets(),result.refundedGold()).withStyle(ChatFormatting.YELLOW),true);
            return 1;
        }catch(RuntimeException e){return fail(source,e);}
    }
    private static int replayLast(CommandSourceStack source){try{var result=TurtleRacingManager.get().replayLast();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.replay.complete",result.seed(),result.ticks(),result.hops(),String.join(" > ",result.order())).withStyle(ChatFormatting.AQUA),false);return 1;}catch(RuntimeException e){return fail(source,e);}}
    private static int previewEffect(CommandSourceStack source,String skillId)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer viewer=player(source);
        try{
            ActiveSkill skill=activeSkill(skillId);
            TurtleSkillEffectPreviewManager.start(viewer,skill);
            source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.effect_preview.started",Component.translatable("yoiko_core.turtle.skill.active."+skill.id()+".name")).withStyle(ChatFormatting.AQUA),false);
            return 1;
        }catch(RuntimeException e){return fail(source,e);}
    }
    private static int previewAllEffects(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer viewer=player(source);
        try{
            TurtleSkillEffectPreviewManager.startAll(viewer);
            source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.effect_preview.all_started",ActiveSkill.values().length).withStyle(ChatFormatting.AQUA),false);
            return 1;
        }catch(RuntimeException e){return fail(source,e);}
    }
    private static int stopEffectPreview(CommandSourceStack source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer viewer=player(source);
        boolean stopped=TurtleSkillEffectPreviewManager.stop(viewer.getUUID());
        source.sendSuccess(()->Component.translatable(stopped?"yoiko_core.turtle.command.effect_preview.stopped":"yoiko_core.turtle.command.effect_preview.none"),false);
        return stopped?1:0;
    }
    private static int inspectCourse(CommandSourceStack source){
        var inspection=TurtleRacingManager.get().arena().inspectCurrent();
        if(inspection==null){source.sendFailure(Component.translatable("yoiko_core.turtle.command.inspect.no_course"));return 0;}
        ChatFormatting color=inspection.valid()?ChatFormatting.GREEN:ChatFormatting.RED;
        source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.inspect.course",Component.translatable(inspection.valid()?"yoiko_core.turtle.command.inspect.valid":"yoiko_core.turtle.command.inspect.invalid"),String.format(Locale.ROOT,"%.1f",inspection.length()),inspection.startSurface().name(),inspection.puddleSections(),String.format(Locale.ROOT,"%.1f",inspection.puddleBlocks()*.25),inspection.lineTiles()-inspection.invalidLineTiles(),inspection.lineTiles()).withStyle(color),false);
        source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.inspect.performance",inspection.queuedBlocks(),inspection.changedBlocks(),inspection.skippedBlocks(),inspection.buildTicks(),inspection.inspectionMillis(),inspection.repairAttempts(),inspection.displayEntities()).withStyle(ChatFormatting.GRAY),false);
        source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.inspect.geometry",inspection.cornerSamples(),6,String.format(Locale.ROOT,"%.1f",TurtleCourse.START_FINISH_SAFE_DISTANCE)).withStyle(ChatFormatting.DARK_AQUA),false);
        for(String problem:inspection.problems())source.sendFailure(Component.translatable("yoiko_core.turtle.command.inspect.problem",problem));
        return inspection.valid()?1:0;
    }
    private static int inspectCourseVisual(CommandSourceStack source){try{TurtleRacingManager.get().arena().showInspectionVisual();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.inspect.visual",20).withStyle(ChatFormatting.AQUA),false);return inspectCourse(source);}catch(RuntimeException e){return fail(source,e);}}
    private static int inspectCourseLanes(CommandSourceStack source){try{TurtleRacingManager.get().arena().showLaneVisual();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.inspect.lanes",20,8).withStyle(ChatFormatting.AQUA),false);return 1;}catch(RuntimeException e){return fail(source,e);}}
    private static int inspectRacingLine(CommandSourceStack source){try{TurtleRacingManager.get().showRacingLineVisual();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.inspect.racing_line",20).withStyle(ChatFormatting.AQUA),false);return 1;}catch(RuntimeException e){return fail(source,e);}}
    private static int clearCourseVisual(CommandSourceStack source){TurtleRacingManager.get().arena().clearInspectionVisual();TurtleRacingManager.get().arena().clearLaneVisual();source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.inspect.cleared"),false);return 1;}
    private static int prepareManual(CommandSourceStack source,String mode,String league,int minutes,String theme,String weather)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);try{TurtleManualCompetitionService.Plan plan=TurtleManualCompetitionService.prepare(p,manualLeague(league),manualOfficial(mode),minutes,theme,manualWeather(weather,theme),null);source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.manual.prepared",plan.id(),league(plan.league()),Component.translatable(plan.official()?"yoiko_core.turtle.command.manual.official":"yoiko_core.turtle.command.manual.exhibition"),plan.theme(),weather(plan.weather()),60,plan.id()).withStyle(ChatFormatting.YELLOW),true);return 1;}catch(RuntimeException e){source.sendFailure(Component.translatable("yoiko_core.turtle.command.manual.prepare_failed",TurtleLocalizedException.component(e,"yoiko_core.turtle.error.request_failed")));return 0;}}
    private static int startManual(CommandSourceStack source,String mode,String league,int minutes,String theme,String weather)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);try{TurtleManualCompetitionService.Plan plan=TurtleManualCompetitionService.start(p,manualLeague(league),manualOfficial(mode),minutes,theme,manualWeather(weather,theme),null);source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.manual.started_detail",league(plan.league()),Component.translatable(plan.official()?"yoiko_core.turtle.command.manual.official":"yoiko_core.turtle.command.manual.exhibition"),plan.theme(),weather(plan.weather()),plan.registrationMinutes()).withStyle(ChatFormatting.GREEN),true);return 1;}catch(RuntimeException e){source.sendFailure(Component.translatable("yoiko_core.turtle.command.manual.start_failed",TurtleLocalizedException.component(e,"yoiko_core.turtle.error.request_failed")));return 0;}}
    private static int confirmManual(CommandSourceStack source,String id)throws com.mojang.brigadier.exceptions.CommandSyntaxException{ServerPlayer p=player(source);try{TurtleManualCompetitionService.confirm(p,id);source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.manual.started"),true);return 1;}catch(RuntimeException e){source.sendFailure(Component.translatable("yoiko_core.turtle.command.manual.start_failed",TurtleLocalizedException.component(e,"yoiko_core.turtle.error.request_failed")));return 0;}}
    private static int cancel(CommandSourceStack source,String reason){TurtleCompetitionData c=TurtleRacingManager.get().competition();if(c==null){source.sendFailure(Component.translatable("yoiko_core.turtle.error.no_competition"));return 0;}TurtleRacingManager.get().cancelCompetition(c,reason);source.sendSuccess(()->Component.translatable("yoiko_core.turtle.command.manual.cancelled"),true);return 1;}
    private static int adminStatus(CommandSourceStack source){TurtleCompetitionData c=TurtleRacingManager.get().competition();source.sendSuccess(()->c==null?Component.translatable("yoiko_core.turtle.command.status.no_competition"):Component.translatable("yoiko_core.turtle.command.admin_status",c.id(),phase(c.phase()),c.currentHeat()+1,TurtleRacingManager.get().arena().phase().name(),String.format(Locale.ROOT,"%.1f",TurtleRacingManager.get().arena().progress()*100)),false);return 1;}
    private static int fail(CommandSourceStack source,RuntimeException exception){source.sendFailure(TurtleLocalizedException.component(exception,"yoiko_core.turtle.error.request_failed"));return 0;}
    private static Component league(TurtleLeague value){return Component.translatable("yoiko_core.turtle.ui.league."+value.name().toLowerCase(Locale.ROOT));}
    private static Component phase(com.yoiko.core.turtle.race.TurtleCompetitionPhase value){return Component.translatable("yoiko_core.turtle.ui.phase."+value.name().toLowerCase(Locale.ROOT));}
    private static Component strategy(TurtleStrategy value){return Component.translatable("yoiko_core.turtle.strategy."+value.name().toLowerCase(Locale.ROOT)+".name");}
    private static Component companion(TurtleCompanion value){return Component.translatable("yoiko_core.turtle.companion."+value.name().toLowerCase(Locale.ROOT)+".name");}
    private static boolean manualOfficial(String value){if(value.equalsIgnoreCase("official"))return true;if(value.equalsIgnoreCase("exhibition"))return false;throw TurtleLocalizedException.of("yoiko_core.turtle.error.manual_mode_invalid",value);}
    private static ActiveSkill activeSkill(String value){try{return ActiveSkill.byId(value.toLowerCase(Locale.ROOT));}catch(IllegalArgumentException e){throw TurtleLocalizedException.of("yoiko_core.turtle.error.unknown_active_skill",value);}}
    private static TurtleLeague manualLeague(String value){try{TurtleLeague league=TurtleLeague.valueOf(value.toUpperCase(Locale.ROOT));if(league==TurtleLeague.TRAINING_D)throw new IllegalArgumentException();return league;}catch(IllegalArgumentException e){throw TurtleLocalizedException.of("yoiko_core.turtle.error.manual_league_invalid",value);}}
    private static TurtleWeather manualWeather(String value,String theme){if(value==null)return TurtleManualCompetitionService.defaultWeather(theme);try{return TurtleWeather.valueOf(value.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){throw TurtleLocalizedException.of("yoiko_core.turtle.error.manual_weather_invalid",value);}}
    private static Component weather(TurtleWeather value){return Component.translatable("yoiko_core.turtle.effect.tag."+switch(value){case CLEAR->"clear";case SEA_BREEZE->"wind";case LIGHT_RAIN->"rain";case FOREST_MIST->"mist";});}
}
