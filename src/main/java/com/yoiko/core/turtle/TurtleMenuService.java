package com.yoiko.core.turtle;

import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.economy.CurrencyManager;
import com.yoiko.core.economy.CurrencyType;
import com.yoiko.core.network.OpenTurtleMenuPayload;
import com.yoiko.core.network.TurtleRaceMenuStatusPayload;
import com.yoiko.core.network.TurtleMenuActionPayload;
import com.yoiko.core.reward.YoikoResetClock;
import com.yoiko.core.menu.MenuSessionManager;
import com.yoiko.core.mail.MailboxManager;
import com.yoiko.core.turtle.race.TurtleCompetitionData;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TurtleMenuService {
    private record PendingRelease(UUID turtleId, long revision, int expiresAt) { }
    private record PendingRerollCandidates(UUID turtleId,int slot,List<String> candidates,long revision) { }
    private static final UUID EMPTY_ID = new UUID(0L, 0L);
    private static final Map<UUID, PendingRelease> RELEASES = new HashMap<>();
    private static final Map<UUID, PendingRerollCandidates> REROLL_CANDIDATES = new HashMap<>();
    private static final Map<UUID, Integer> PAGES = new HashMap<>();
    private static final Set<UUID> APPEARANCE_CATALOG_SENT = new HashSet<>();
    private static final int PAGE_SIZE = 8;
    static final String INTRO_STRATEGY_EGG_REWARD = "intro:rare-strategy-egg:v4";
    private static final String INTRO_STRATEGY_EGG_MAIL = "turtle-racing-intro-strategy-egg";
    enum IntroStrategyEggDecision { NONE, MARK_DELIVERED, SEND_MAIL }

    private TurtleMenuService() { }

    public static void open(ServerPlayer player) { open(player, "INFO"); }

    public static void open(ServerPlayer player, String initialTab) {
        open(player,initialTab,true);
    }

    private static void open(ServerPlayer player,String initialTab,boolean establishSession) {
        if(establishSession)MenuSessionManager.open(player,"turtle");
        ServerYoikoSavedData yoikoSaved = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData yoikoData = yoikoSaved.getOrCreate(player);
        if (!PlayerYoikoData.MENU_TAB_TURTLE.equals(yoikoData.lastMenuTab)) {
            yoikoData.lastMenuTab = PlayerYoikoData.MENU_TAB_TURTLE;
            yoikoSaved.markDirty(player);
        }
        TurtleRacingSavedData saved = TurtleRacingSavedData.get(player.server);
        TurtlePlayerProgress progress = saved.getOrCreatePlayer(player.getUUID());
        TurtleGoldenShellAdvancement.sync(player,progress);
        TurtleAchievementManager.sync(player, progress);
        if(establishSession&&ensureIntroStrategyEggMail(player,progress))saved.markChanged();
        if(deliverPendingTicketItems(player,progress))saved.markChanged();
        progress.refreshTraining(YoikoResetClock.dailyPeriodKey(System.currentTimeMillis()));
        PendingRelease pending = RELEASES.get(player.getUUID());
        if (pending != null && pending.expiresAt() < player.server.getTickCount()) {
            RELEASES.remove(player.getUUID());
            pending = null;
        }

        TurtleRaceMenuStatusPayload raceStatus=buildRaceStatus(player,saved);

        final PendingRelease selectedPending = pending;
        var forecastCourse=TurtleRacingManager.get().arena().course();
        TurtleWeather forecastWeather=TurtleWeather.valueOf(raceStatus.forecastWeather());
        List<TurtleData> owned = saved.ownedBy(player.getUUID()).stream()
                .sorted(Comparator.comparing(TurtleData::favorite).reversed().thenComparingLong(TurtleData::acquiredAt)).toList();
        TurtlePlayerProgress.PendingPassiveReroll pendingReroll=progress.pendingPassiveReroll().orElse(null);
        TurtleData pendingRerollTurtle=pendingReroll==null?null:saved.turtle(pendingReroll.turtleId()).orElse(null);
        if(pendingReroll!=null&&(pendingRerollTurtle==null
                ||!pendingRerollTurtle.ownerId().equals(player.getUUID()))){
            throw new IllegalStateException("Pending passive reroll references an unowned turtle; reset development data");
        }
        PendingRerollCandidates pendingCandidates=REROLL_CANDIDATES.get(player.getUUID());
        if(pendingCandidates!=null){
            TurtleData candidateTurtle=saved.turtle(pendingCandidates.turtleId()).orElse(null);
            if(candidateTurtle==null||!candidateTurtle.ownerId().equals(player.getUUID())||candidateTurtle.revision()!=pendingCandidates.revision()){
                REROLL_CANDIDATES.remove(player.getUUID());pendingCandidates=null;
            }
        }
        int pages=Math.max(1,(owned.size()+PAGE_SIZE-1)/PAGE_SIZE);
        int page=Math.max(0,Math.min(PAGES.getOrDefault(player.getUUID(),0),pages-1));
        PAGES.put(player.getUUID(),page);
        var courseSummary=TurtleStrategyForecast.CourseFeatureSummary.from(forecastCourse,forecastWeather);
        List<OpenTurtleMenuPayload.Card> cards = owned.stream().skip((long)page*PAGE_SIZE).limit(PAGE_SIZE)
                .map(turtle -> new OpenTurtleMenuPayload.Card(turtle.id(), turtle.revision(), turtle.name(),
                        turtle.rarity().symbol(), turtle.stats().total(), turtle.league().name(), turtle.raceClass().name(), turtle.racePoints(),
                        turtle.trainingCount(), turtle.rerollCredits(), turtle.rerollsUsed(),
                        turtle.awakeningPoints(), turtle.locked(),
                        selectedPending != null && selectedPending.turtleId().equals(turtle.id()),
                        TurtleRacingManager.get().isRegisteredTurtle(turtle.id()),turtle.favorite(),
                        turtle.activeSkill().id(), turtle.passives(), turtle.strategy().name(),
                        turtle.companion() == null ? "NONE" : turtle.companion().name(),turtle.appearance(),turtle.bodyAppearance(),
                        TurtleStrategyForecast.all(turtle,courseSummary),
                        java.util.Arrays.stream(turtle.stats().copyValues()).boxed().toList(),
                        java.util.Arrays.stream(turtle.stats().copyValues()).mapToObj(value->TurtleStats.grade(value).name()).toList(),
                        java.util.Arrays.stream(turtle.baseStats().copyValues()).boxed().toList(),
                        java.util.Arrays.stream(TurtleGuidance.projectedFinalStats(turtle).copyValues()).boxed().toList(),
                        java.util.Arrays.stream(TurtleTrainingType.values()).filter(type->TurtleTrainingService.canTrain(turtle,type)).map(Enum::name).toList(),
                        TurtleGuidance.recommendedTraining(turtle),career(turtle),raceAnalysis(turtle.lastOfficialRace())))
                .toList();

        List<String> companions = progress.unlockedCompanions().stream()
                .sorted(Comparator.comparing(Enum::name)).map(Enum::name).toList();
        TurtleBannerSchedule.Banner banner = TurtleBannerSchedule.current();
        long gemBalance=CurrencyManager.balance(player,CurrencyType.GEM);
        boolean sendAppearanceCatalog=APPEARANCE_CATALOG_SENT.add(player.getUUID());
        List<OpenTurtleMenuPayload.BetCandidate> betCandidates=buildBetCandidates(saved,saved.competition().orElse(null));
        PacketDistributor.sendToPlayer(player, new OpenTurtleMenuPayload(saved.revision(),
                progress.availableTraining(), progress.shellMedals(), gemBalance, owned.size(), 40,
                progress.ticketCount(TurtleTicketType.STANDARD), progress.ticketCount(TurtleTicketType.PICKUP),
                progress.ticketCount(TurtleTicketType.RARE_STRATEGY_SELECT)
                        +progress.ticketCount(TurtleTicketType.RARE_FRONT)+progress.ticketCount(TurtleTicketType.RARE_STEADY)
                        +progress.ticketCount(TurtleTicketType.RARE_FOLLOW)+progress.ticketCount(TurtleTicketType.RARE_CLOSER),
                progress.ticketCount(TurtleTicketType.EPIC_GUARANTEED), countItem(player,com.yoiko.core.registry.YoikoItems.TURTLE_TRAINING_RESET_TICKET.get()),
                banner.id() + " · " + banner.activeSkill().id(), raceStatus.competition(), raceStatus.timeTrial(),
                normalizeTab(initialTab),raceStatus.forecastWeather(), raceStatus.forecastSurfaceMask(),
                page, pages, goldenShellWinMask(progress),
                pendingReroll==null?null:new OpenTurtleMenuPayload.RerollOffer(pendingReroll.turtleId(),pendingReroll.slot(),pendingReroll.currentPassive(),pendingReroll.candidatePassive(),pendingRerollTurtle.revision()),
                pendingCandidates==null?null:new OpenTurtleMenuPayload.RerollCandidates(pendingCandidates.turtleId(),pendingCandidates.slot(),pendingCandidates.candidates(),pendingCandidates.revision()),
                sendAppearanceCatalog?TurtleAppearanceCatalog.values().stream()
                        .map(value->new OpenTurtleMenuPayload.AppearanceDefinition(value.id(),value.color(),value.unlockKind().name(),value.unlockKey(),value.gemPrice()))
                        .toList():List.of(),
                TurtleAppearanceCatalog.values().stream()
                        .map(value->new OpenTurtleMenuPayload.AppearanceState(value.id(),TurtleAppearanceCatalog.isUnlocked(progress,value.id()),progress.isFavoriteAppearance(value.id()),appearanceProgress(progress,value,gemBalance),appearanceTarget(value)))
                        .toList(), companions,betCandidates,raceStatus.betPools(), cards));
    }

    private static void sendRaceStatus(ServerPlayer player){
        PacketDistributor.sendToPlayer(player,buildRaceStatus(player,TurtleRacingSavedData.get(player.server)));
    }

    private static OpenTurtleMenuPayload.Career career(TurtleData turtle){
        return new OpenTurtleMenuPayload.Career(turtle.raceRating(),turtle.officialStarts(),turtle.officialWins(),turtle.officialPodiums(),
                (int)Math.round(turtle.averageOfficialRank()*10),turtle.averageOfficialStamina(),
                turtle.recentOfficialRanks(),java.util.Arrays.stream(TurtleStrategy.values()).mapToInt(turtle::strategyStarts).boxed().toList(),
                java.util.Arrays.stream(TurtleStrategy.values()).mapToInt(turtle::strategyWins).boxed().toList());
    }

    private static OpenTurtleMenuPayload.RaceAnalysis raceAnalysis(TurtleData.LastRaceSummary value){
        return value==null?null:new OpenTurtleMenuPayload.RaceAnalysis(value.rank(),value.finished(),value.finishMillis(),value.staminaPercent(),value.overtakes(),
                value.laneChanges(),value.blockedTicks(),value.breaths(),value.activeUsed(),value.ratingChange());
    }

    private static TurtleRaceMenuStatusPayload buildRaceStatus(ServerPlayer player,TurtleRacingSavedData saved){
        TurtleCompetitionData competition=saved.competition().orElse(null);
        TurtleRacingManager.WeeklySummary weekly=TurtleRacingManager.get().weeklySummary(player.getUUID());
        NextOfficial next=nextOfficial();
        var timeTrial=TurtleRacingManager.get().timeTrial();
        TurtlePlayerProgress progress=saved.getOrCreatePlayer(player.getUUID());
        List<OpenTurtleMenuPayload.TimeTrialBest> bests=new ArrayList<>();
        for(TurtleRaceClass raceClass:TurtleRaceClass.values()){var record=progress.timeTrialRecord(timeTrial.currentRecordKey(raceClass));bests.add(new OpenTurtleMenuPayload.TimeTrialBest(raceClass.name(),record==null?0:record.finishMillis()));}
        boolean competitionRegistered=competition!=null&&competition.registrations().stream()
                .anyMatch(value->value.playerId().equals(player.getUUID()));
        boolean timeTrialEngaged=player.getUUID().equals(timeTrial.activePlayer())
                ||timeTrial.queuePosition(player.getUUID())>0;
        TurtleWeather forecastWeather=competition==null?TurtleWeather.CLEAR:competition.weather();
        var courseSummary=TurtleStrategyForecast.CourseFeatureSummary.from(
                TurtleRacingManager.get().arena().course(),forecastWeather);
        int forecastSurfaceMask=courseSummary.surfaces().stream()
                .mapToInt(surface->1<<surface.ordinal()).reduce(0,(leftMask,rightMask)->leftMask|rightMask);
        var competitionStatus=new OpenTurtleMenuPayload.CompetitionStatus(competition!=null,competition==null?"":competition.league().name(),
                competition==null?"":competition.phase().name(),competition==null?0:competition.registrations().size(),competition==null?0:competition.currentHeat(),competitionRegistered,
                weekly.finishes(),weekly.bestRank(),next.league().name(),next.startAt());
        var timeTrialStatus=new OpenTurtleMenuPayload.TimeTrialStatus(timeTrial.presetId(),timeTrial.state().name(),timeTrial.queuePosition(player.getUUID()),timeTrialEngaged,bests);
        return new TurtleRaceMenuStatusPayload(competitionStatus,timeTrialStatus,forecastWeather.name(),forecastSurfaceMask,
                buildBetPools(player,competition));
    }

    private record NextOfficial(TurtleLeague league,long startAt){}
    private static NextOfficial nextOfficial(){ZonedDateTime now=ZonedDateTime.now(YoikoResetClock.zone());for(int day=0;day<=7;day++){ZonedDateTime date=now.plusDays(day);ZonedDateTime start=date.with(LocalTime.of(20,0));if(start.isAfter(now))return new NextOfficial(TurtleLeague.scheduled(start.getDayOfWeek()),start.toInstant().toEpochMilli());}throw new IllegalStateException("No next turtle competition");}

    private static boolean deliverPendingTicketItems(ServerPlayer player,TurtlePlayerProgress progress){boolean delivered=false;
        for(TurtleTicketType type:TurtleTicketType.values()){
            int count=progress.ticketCount(type);while(count-->0){net.minecraft.world.item.ItemStack stack=new net.minecraft.world.item.ItemStack(com.yoiko.core.registry.YoikoItems.turtleTicket(type));com.yoiko.core.item.TurtleHatchTicketItem.bindTo(stack,player.getUUID());if(!player.getInventory().add(stack))player.drop(stack,false);progress.consumeTicket(type);delivered=true;}
        }
        return delivered;
    }

    private static boolean ensureIntroStrategyEggMail(ServerPlayer player,TurtlePlayerProgress progress){
        boolean pending=MailboxManager.hasPending(player,"reward",INTRO_STRATEGY_EGG_MAIL);
        IntroStrategyEggDecision decision=introStrategyEggDecision(progress,pending);
        if(decision==IntroStrategyEggDecision.NONE)return false;
        // A queued mail is marked before reopening the menu so it is never sent twice.
        if(decision==IntroStrategyEggDecision.MARK_DELIVERED){
            progress.claimReward(INTRO_STRATEGY_EGG_REWARD);
            return true;
        }
        net.minecraft.world.item.ItemStack egg=new net.minecraft.world.item.ItemStack(
                com.yoiko.core.registry.YoikoItems.TURTLE_RARE_STRATEGY_TICKET.get());
        com.yoiko.core.item.TurtleHatchTicketItem.bindTo(egg,player.getUUID());
        if(!MailboxManager.sendSystemMail(player,"reward",INTRO_STRATEGY_EGG_MAIL,
                "yoiko_core.mail.turtle_intro.title","yoiko_core.mail.turtle_intro.message",List.of(egg)))return false;
        progress.claimReward(INTRO_STRATEGY_EGG_REWARD);
        return true;
    }

    static IntroStrategyEggDecision introStrategyEggDecision(TurtlePlayerProgress progress,boolean pendingMail){
        if(progress.hasReward(INTRO_STRATEGY_EGG_REWARD))return IntroStrategyEggDecision.NONE;
        if(pendingMail)return IntroStrategyEggDecision.MARK_DELIVERED;
        return IntroStrategyEggDecision.SEND_MAIL;
    }

    public static void handle(ServerPlayer player, TurtleMenuActionPayload payload) {
        if("STATUS_REFRESH".equals(payload.action())){
            TurtleRacingSavedData saved=TurtleRacingSavedData.get(player.server);
            int knownSignature;
            try{knownSignature=Integer.parseInt(payload.value());}catch(NumberFormatException ignored){knownSignature=Integer.MIN_VALUE;}
            if(knownSignature!=betCandidateSignature(buildBetCandidates(saved,saved.competition().orElse(null)))){
                open(player,"RACE",false);
                return;
            }
            sendRaceStatus(player);
            return;
        }
        String reopenTab = tabForAction(payload.action());
        try {
            TurtleRacingSavedData saved = TurtleRacingSavedData.get(player.server);
            TurtleData turtle = payload.turtleId().equals(EMPTY_ID) ? null : saved.turtle(payload.turtleId()).orElse(null);
            if (turtle != null && !turtle.ownerId().equals(player.getUUID())) {
                throw TurtleLocalizedException.of("yoiko_core.turtle.error.not_owner");
            }
            switch (payload.action()) {
                case "TURTLE_PAGE" -> {
                    String[] fields=payload.value().split("\\|",2);
                    int requested=Integer.parseInt(fields[0]);
                    PAGES.put(player.getUUID(),Math.max(0,requested));
                    if(fields.length==2)reopenTab=normalizeTab(fields[1]);
                }
                case "HATCH_STANDARD","HATCH_PICKUP","HATCH_RARE","HATCH_EPIC","BUY_TICKET" -> throw TurtleLocalizedException.of("yoiko_core.turtle.error.use_ticket_item");
                case "TRAIN" -> {
                    require(turtle, payload.expectedRevision());
                    TurtleGrowthPolicy.requireCompetitionUnlocked(turtle);
                    TurtleTrainingService.train(player, turtle, TurtleTrainingType.valueOf(payload.value()),
                            payload.expectedRevision());
                }
                case "TRAINING_RESET" -> {
                    require(turtle, payload.expectedRevision());
                    TurtleGrowthPolicy.requireCompetitionUnlocked(turtle);
                    TurtleTrainingService.resetTraining(player,turtle,payload.expectedRevision());
                }
                case "REROLL" -> reroll(player, saved, turtle, payload);
                case "REROLL_CANDIDATES" -> previewRerollCandidates(player,turtle,payload);
                case "REROLL_CANDIDATES_CLOSE" -> REROLL_CANDIDATES.remove(player.getUUID());
                case "REROLL_KEEP" -> finishReroll(player,saved,turtle,payload,false);
                case "REROLL_APPLY" -> finishReroll(player,saved,turtle,payload,true);
                case "REGISTER" -> {
                    require(turtle, payload.expectedRevision());
                    TurtleRacingManager.get().register(player, turtle.id());
                }
                case "UNREGISTER" -> TurtleRacingManager.get().unregister(player);
                case "BET" -> placeBet(player, payload.value());
                case "CHEER" -> TurtleRacingManager.get().cheer(player, payload.value());
                case "LOCK" -> {
                    require(turtle, payload.expectedRevision());
                    turtle.setLocked(!turtle.locked());
                    saved.markChanged();
                }
                case "TURTLE_FAVORITE" -> {
                    require(turtle,payload.expectedRevision());
                    turtle.setFavorite(!turtle.favorite());
                    saved.markChanged();
                    reopenTab=normalizeTab(payload.value());
                }
                case "STRATEGY" -> {
                    require(turtle, payload.expectedRevision());
                    TurtleGrowthPolicy.requireCompetitionUnlocked(turtle);
                    turtle.setStrategy(TurtleStrategy.valueOf(payload.value()));
                    saved.markChanged();
                }
                case "COMPANION" -> setCompanion(player, saved, turtle, payload);
                case "APPEARANCE" -> setAppearance(player,saved,turtle,payload);
                case "APPEARANCE_UNLOCK" -> unlockAppearance(player,saved,payload.value());
                case "APPEARANCE_FAVORITE" -> toggleFavoriteAppearance(saved,player,payload.value());
                case "RELEASE_PREPARE" -> prepareRelease(player, saved, turtle, payload.expectedRevision());
                case "RELEASE_CONFIRM" -> confirmRelease(player, saved, turtle);
                case "MEDAL_AWAKEN" -> awakenWithMedals(player, saved, turtle, payload.expectedRevision());
                case "TIME_TRIAL" -> {
                    require(turtle, payload.expectedRevision());
                    TurtleRacingManager.get().timeTrial().request(player, turtle,
                            TurtleRaceClass.valueOf(payload.value()));
                }
                case "TIME_TRIAL_CANCEL" -> TurtleRacingManager.get().timeTrial().cancel(player.getUUID());
                default -> throw TurtleLocalizedException.of("yoiko_core.turtle.error.unknown_menu_action");
            }
        } catch (RuntimeException exception) {
            player.sendSystemMessage(TurtleLocalizedException.component(exception,"yoiko_core.turtle.error.request_failed"));
        }
        open(player,reopenTab,true);
    }

    private static void reroll(ServerPlayer player,TurtleRacingSavedData saved,TurtleData turtle,TurtleMenuActionPayload payload) {
        require(turtle, payload.expectedRevision());
        TurtleGrowthPolicy.requireCompetitionUnlocked(turtle);
        TurtlePlayerProgress progress=saved.getOrCreatePlayer(player.getUUID());
        if(progress.pendingPassiveReroll().isPresent())throw TurtleLocalizedException.of("yoiko_core.turtle.error.reroll_pending");
        int slot = Integer.parseInt(payload.value());
        List<String> candidates = TurtleTrainingService.rerollCandidates(player, turtle, slot);
        if (candidates.isEmpty()) throw TurtleLocalizedException.of("yoiko_core.turtle.error.reroll_unavailable");
        String chosen = candidates.get(player.getRandom().nextInt(candidates.size()));
        String current=turtle.passives().get(slot);
        REROLL_CANDIDATES.remove(player.getUUID());
        TurtleTrainingService.consumePassiveReroll(player,turtle,payload.expectedRevision());
        progress.setPendingPassiveReroll(new TurtlePlayerProgress.PendingPassiveReroll(turtle.id(),slot,current,chosen));
        saved.markChanged();
    }

    private static void previewRerollCandidates(ServerPlayer player,TurtleData turtle,TurtleMenuActionPayload payload){
        require(turtle,payload.expectedRevision());
        TurtleGrowthPolicy.requireCompetitionUnlocked(turtle);
        int slot=Integer.parseInt(payload.value());
        if(slot<0||slot>=4)throw TurtleLocalizedException.of("yoiko_core.turtle.error.invalid_passive");
        List<String> candidates=TurtleTrainingService.previewRerollCandidates(player,turtle,slot);
        REROLL_CANDIDATES.put(player.getUUID(),new PendingRerollCandidates(turtle.id(),slot,candidates,turtle.revision()));
    }

    private static void finishReroll(ServerPlayer player,TurtleRacingSavedData saved,TurtleData turtle,TurtleMenuActionPayload payload,boolean apply){
        TurtlePlayerProgress progress=saved.getOrCreatePlayer(player.getUUID());
        TurtlePlayerProgress.PendingPassiveReroll pending=progress.pendingPassiveReroll().orElse(null);
        if(pending==null||turtle==null||!pending.turtleId().equals(turtle.id()))throw TurtleLocalizedException.of("yoiko_core.turtle.error.reroll_missing");
        require(turtle,payload.expectedRevision());
        if(!turtle.passives().get(pending.slot()).equals(pending.currentPassive()))throw TurtleLocalizedException.of("yoiko_core.turtle.error.changed_reopen");
        if(apply){TurtleGrowthPolicy.requireCompetitionUnlocked(turtle);TurtleTrainingService.applyPreparedPassive(player,turtle,pending.slot(),pending.candidatePassive(),turtle.revision());}
        progress.clearPendingPassiveReroll();
        saved.markChanged();
        player.sendSystemMessage(Component.translatable(apply?"yoiko_core.turtle.reroll.applied":"yoiko_core.turtle.reroll.kept"));
    }

    private static void placeBet(ServerPlayer player, String value) {
        String[] fields = value.split("\\|", 3);
        if (fields.length != 3) throw TurtleLocalizedException.of("yoiko_core.turtle.error.invalid_bet_request");
        TurtleRacingManager.get().bet(player, Integer.parseInt(fields[0]), fields[1], Long.parseLong(fields[2]));
    }

    private static void setCompanion(ServerPlayer player, TurtleRacingSavedData saved, TurtleData turtle,
                                     TurtleMenuActionPayload payload) {
        require(turtle, payload.expectedRevision());
        TurtleGrowthPolicy.requireCompetitionUnlocked(turtle);
        if (payload.value().equals("NONE")) turtle.setCompanion(null);
        else {
            TurtleCompanion companion = TurtleCompanion.valueOf(payload.value());
            if (!saved.getOrCreatePlayer(player.getUUID()).unlockedCompanions().contains(companion)) {
                throw TurtleLocalizedException.of("yoiko_core.turtle.error.companion_locked");
            }
            turtle.setCompanion(companion);
        }
        saved.markChanged();
    }

    private static int countItem(ServerPlayer player,net.minecraft.world.item.Item item){
        int count=0;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
            net.minecraft.world.item.ItemStack stack=player.getInventory().getItem(slot);
            if(stack.is(item))count+=stack.getCount();
        }
        return count;
    }

    private static void setAppearance(ServerPlayer player,TurtleRacingSavedData saved,TurtleData turtle,TurtleMenuActionPayload payload){
        require(turtle,payload.expectedRevision());TurtlePlayerProgress progress=saved.getOrCreatePlayer(player.getUUID());
        if(!TurtleAppearanceCatalog.isUnlocked(progress,payload.value()))throw localized("yoiko_core.turtle.appearance.error.locked");
        turtle.setAppearance(payload.value());saved.markChanged();
    }

    private static int goldenShellWinMask(TurtlePlayerProgress progress){
        int mask=0;
        for(TurtleStrategy strategy:progress.goldenShellStrategyWins())mask|=1<<strategy.ordinal();
        return mask;
    }

    private static void unlockAppearance(ServerPlayer player,TurtleRacingSavedData saved,String id){
        TurtleAppearanceCatalog.Appearance appearance=TurtleAppearanceCatalog.get(id);
        if(appearance.unlockKind()!=TurtleAppearanceCatalog.UnlockKind.GEM)throw localized("yoiko_core.turtle.appearance.error.not_gem");
        TurtlePlayerProgress progress=saved.getOrCreatePlayer(player.getUUID());
        if(TurtleAppearanceCatalog.isUnlocked(progress,id))return;
        if(!CurrencyManager.take(player,CurrencyType.GEM,appearance.gemPrice()))throw localized("yoiko_core.turtle.appearance.error.gem_short");
        progress.unlockAppearance(id);saved.markChanged();
        ServerYoikoAuditSavedData.get(player.server).addOperational("ECONOMY","GEM_SPENT",player.getUUID(),player.getGameProfile().getName(),"source=turtle_appearance;appearance="+id+",amount="+appearance.gemPrice());
        player.sendSystemMessage(Component.translatable("yoiko_core.turtle.appearance.unlocked",Component.translatable("yoiko_core.turtle.appearance."+id)));
    }

    private static void toggleFavoriteAppearance(TurtleRacingSavedData saved,ServerPlayer player,String id){
        TurtleAppearanceCatalog.get(id);
        saved.getOrCreatePlayer(player.getUUID()).toggleFavoriteAppearance(id);
        saved.markChanged();
    }

    private static int appearanceTarget(TurtleAppearanceCatalog.Appearance appearance){
        if(appearance.unlockKind()==TurtleAppearanceCatalog.UnlockKind.FREE)return 0;
        if(appearance.unlockKind()==TurtleAppearanceCatalog.UnlockKind.GEM)return appearance.gemPrice();
        return switch(appearance.unlockKey()){
            case "official_finish_1","time_trial_finish"->1;
            case "official_finish_5","companion_5"->5;
            case "golden_shell_mastery"->4;
            default->1;
        };
    }

    private static int appearanceProgress(TurtlePlayerProgress progress,TurtleAppearanceCatalog.Appearance appearance,long gems){
        int target=appearanceTarget(appearance);
        if(TurtleAppearanceCatalog.isUnlocked(progress,appearance.id()))return target;
        long current=switch(appearance.unlockKey()){
            case "official_finish_1","official_finish_5"->progress.officialFinishes();
            case "time_trial_finish"->progress.timeTrialRecordCount();
            case "companion_5"->progress.unlockedCompanions().size();
            case "golden_shell_mastery"->progress.goldenShellStrategyWins().size();
            default->appearance.unlockKind()==TurtleAppearanceCatalog.UnlockKind.GEM?gems:0;
        };
        return (int)Math.max(0,Math.min(target,current));
    }

    public static void clearPlayer(UUID playerId) {
        RELEASES.remove(playerId);
        REROLL_CANDIDATES.remove(playerId);
        PAGES.remove(playerId);
        APPEARANCE_CATALOG_SENT.remove(playerId);
    }

    private static TurtleLocalizedException localized(String key){return TurtleLocalizedException.of(key);}

    private static void prepareRelease(ServerPlayer player, TurtleRacingSavedData saved, TurtleData turtle,
                                       long expectedRevision) {
        require(turtle, expectedRevision);
        verifyReleaseAllowed(player, saved, turtle);
        RELEASES.put(player.getUUID(), new PendingRelease(turtle.id(), turtle.revision(),
                player.server.getTickCount() + 600));
        player.sendSystemMessage(Component.translatable("yoiko_core.turtle.message.release_prepare",30,releaseMedals(turtle.rarity()),turtle.displayName()));
    }

    private static void confirmRelease(ServerPlayer player, TurtleRacingSavedData saved, TurtleData turtle) {
        if (turtle == null) throw TurtleLocalizedException.of("yoiko_core.turtle.error.select_turtle");
        PendingRelease pending = RELEASES.remove(player.getUUID());
        if (pending == null || pending.expiresAt() < player.server.getTickCount()
                || !pending.turtleId().equals(turtle.id()) || pending.revision() != turtle.revision()) {
            throw TurtleLocalizedException.of("yoiko_core.turtle.error.release_expired");
        }
        verifyReleaseAllowed(player, saved, turtle);
        TurtleData released = saved.releaseTurtle(player.getUUID(), turtle.id());
        int medals = releaseMedals(released.rarity());
        saved.getOrCreatePlayer(player.getUUID()).addShellMedals(medals);
        saved.markChanged();
        ServerYoikoAuditSavedData.get(player.server).addOperational("TURTLE", "TURTLE_RELEASED",
                player.getUUID(), player.getGameProfile().getName(),
                "id=" + released.id() + ",rarity=" + released.rarity() + ",medals=" + medals);
        player.sendSystemMessage(Component.translatable("yoiko_core.turtle.message.released",released.displayName(),medals));
    }

    private static void verifyReleaseAllowed(ServerPlayer player, TurtleRacingSavedData saved, TurtleData turtle) {
        if (turtle.locked()) throw TurtleLocalizedException.of("yoiko_core.turtle.error.release_locked");
        TurtleCompetitionData competition = saved.competition().orElse(null);
        if (competition != null && competition.registrations().stream()
                .anyMatch(value -> value.turtleId().equals(turtle.id()))) {
            throw TurtleLocalizedException.of("yoiko_core.turtle.error.release_registered");
        }
        if (TurtleRacingManager.get().timeTrial().isActiveTurtle(turtle.id())) {
            throw TurtleLocalizedException.of("yoiko_core.turtle.error.release_time_trial");
        }
    }

    private static void awakenWithMedals(ServerPlayer player, TurtleRacingSavedData saved, TurtleData turtle,
                                         long expectedRevision) {
        require(turtle, expectedRevision);
        TurtleGrowthPolicy.requireCompetitionUnlocked(turtle);
        TurtlePlayerProgress progress = saved.getOrCreatePlayer(player.getUUID());
        if (progress.officialFinishes() <= 0) throw TurtleLocalizedException.of("yoiko_core.turtle.error.awaken_need_finish");
        if (turtle.awakeningPoints() >= 30) throw TurtleLocalizedException.of("yoiko_core.turtle.error.awaken_max");
        String week = weeklyKey();
        if (progress.usedMedalAwakening(week)) throw TurtleLocalizedException.of("yoiko_core.turtle.error.awaken_weekly_used");
        if (!progress.takeShellMedals(100)) throw TurtleLocalizedException.of("yoiko_core.turtle.error.need_shell_medals",100);
        turtle.addAwakeningPoints(5);
        progress.markMedalAwakening(week);
        saved.markChanged();
    }

    private static List<OpenTurtleMenuPayload.BetCandidate> buildBetCandidates(TurtleRacingSavedData saved,TurtleCompetitionData competition) {
        if (competition == null || !competition.official() || competition.heats().size()!=1) return List.of();
        List<OpenTurtleMenuPayload.BetCandidate> result = new ArrayList<>();
        for (int heat = 0; heat < competition.heats().size(); heat++) {
            int heatIndex = heat;
            for (String entryId : TurtleRacingManager.get().bettingCandidates(heatIndex)) {
                TurtleData turtle=betTurtle(saved,entryId);
                com.yoiko.core.turtle.race.AiTurtleProfile ai=betAi(entryId);
                String raceClass=turtle==null?competition.league().maximumClass().name():turtle.raceClass().name();
                int rating=turtle==null?TurtleData.DEFAULT_RACE_RATING:turtle.raceRating();
                String strategy=turtle==null&&ai!=null?ai.strategy().name():turtle==null?"STEADY":turtle.strategy().name();
                String active=turtle==null&&ai!=null?ai.activeSkill().id():turtle==null?"":turtle.activeSkill().id();
                String form=turtle==null?"-":turtle.recentOfficialRanks().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("-"));
                TurtleCompetitionData.BetQuote quote=competition.betQuote(heatIndex,entryId);
                result.add(new OpenTurtleMenuPayload.BetCandidate(heatIndex,entryId,candidateLabel(saved,entryId),
                        raceClass,rating,strategy,active,form.isBlank()?"-":form,
                        quote==null?0:quote.winBasisPoints(),quote==null?0:quote.oddsMilli()));
            }
        }
        return result;
    }

    private static List<OpenTurtleMenuPayload.BetPool> buildBetPools(ServerPlayer player,TurtleCompetitionData competition){
        if(competition==null||!competition.official()||competition.heats().size()!=1)return List.of();
        record Key(int heat,String entryId){}
        Map<Key,Long> totals=new HashMap<>(),mine=new HashMap<>();
        for(TurtleCompetitionData.Bet bet:competition.bets()){Key key=new Key(bet.heatIndex(),bet.entryId());totals.merge(key,bet.amount(),Long::sum);if(bet.playerId().equals(player.getUUID()))mine.merge(key,bet.amount(),Long::sum);}
        List<OpenTurtleMenuPayload.BetPool> result=new ArrayList<>();
        for(int heat=0;heat<competition.heats().size();heat++)for(String entryId:TurtleRacingManager.get().bettingCandidates(heat)){
            Key key=new Key(heat,entryId);result.add(new OpenTurtleMenuPayload.BetPool(heat,entryId,totals.getOrDefault(key,0L),mine.getOrDefault(key,0L)));
        }
        return result;
    }

    private static int betCandidateSignature(List<OpenTurtleMenuPayload.BetCandidate> candidates){int hash=1;for(var candidate:candidates){hash=31*hash+candidate.heat();hash=31*hash+candidate.entryId().hashCode();hash=31*hash+candidate.winBasisPoints();hash=31*hash+candidate.oddsMilli();}return hash;}

    private static TurtleData betTurtle(TurtleRacingSavedData saved,String entryId){if(!entryId.startsWith("player:"))return null;try{return saved.turtle(UUID.fromString(entryId.substring(7))).orElse(null);}catch(IllegalArgumentException ignored){return null;}}
    private static com.yoiko.core.turtle.race.AiTurtleProfile betAi(String entryId){return entryId.startsWith("ai:")?com.yoiko.core.turtle.race.AiTurtleCatalog.get(entryId.substring(3)):null;}

    private static String candidateLabel(TurtleRacingSavedData saved, String entryId) {
        if (entryId.startsWith("ai:")) {
            return entryId.substring(3);
        }
        if (entryId.startsWith("player:")) {
            try {
                return saved.turtle(UUID.fromString(entryId.substring(7))).map(TurtleData::name).orElse(entryId);
            } catch (IllegalArgumentException ignored) { }
        }
        return entryId;
    }

    private static int releaseMedals(TurtleRarity rarity) {
        return switch (rarity) {
            case COMMON -> 20;
            case UNCOMMON -> 35;
            case RARE -> 60;
            case EPIC -> 120;
            case LEGENDARY -> 250;
        };
    }

    private static String weeklyKey() {
        LocalDate date = YoikoResetClock.dailyPeriodDate(System.currentTimeMillis());
        return "week:" + date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
    }

    private static void require(TurtleData turtle, long revision) {
        if (turtle == null) throw TurtleLocalizedException.of("yoiko_core.turtle.error.select_turtle");
        if (turtle.revision() != revision) throw TurtleLocalizedException.of("yoiko_core.turtle.error.changed_retry");
    }

    private static String normalizeTab(String tab) {
        return switch (tab == null ? "" : tab.toUpperCase(Locale.ROOT)) {
            case "TURTLES", "HATCH" -> "INFO";
            case "TRAINING", "GROWTH" -> "GROWTH";
            case "TIME_TRIAL" -> "RACE";
            case "INFO", "APPEARANCE", "RACE" -> tab.toUpperCase(Locale.ROOT);
            default -> "INFO";
        };
    }

    private static String tabForAction(String action) {
        if (action.startsWith("HATCH") || action.equals("BUY_TICKET")) return "HATCH";
        if (action.equals("TRAIN") || action.equals("TRAINING_RESET") || action.startsWith("REROLL")
                || action.equals("MEDAL_AWAKEN") || action.equals("STRATEGY") || action.equals("COMPANION")) return "GROWTH";
        if(action.equals("APPEARANCE")||action.equals("APPEARANCE_UNLOCK")||action.equals("APPEARANCE_FAVORITE"))return "APPEARANCE";
        if (action.equals("REGISTER") || action.equals("UNREGISTER") || action.equals("BET") || action.equals("CHEER")) return "RACE";
        if (action.startsWith("TIME_TRIAL")) return "RACE";
        return "INFO";
    }

}
