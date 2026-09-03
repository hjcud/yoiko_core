package com.yoiko.core.event;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.gacha.GachaPoolScanner;
import com.yoiko.core.gacha.GachaRarity;
import com.yoiko.core.mail.MailboxManager;
import com.yoiko.core.economy.RestedGoldManager;
import com.yoiko.core.network.BreakingNewsZonePayload;
import com.yoiko.core.newspaper.WeeklyNewspaperManager;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.treasure.TreasureRabbitEntity;
import com.yoiko.core.treasure.TreasureRabbitManager;
import com.yoiko.core.treasure.TreasureRabbitVariant;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative lifecycle for low-frequency incidents, separate from scheduled daily bonuses. */
public final class BreakingNewsEventManager {
    private static final int CONFIG_VERSION = 4;
    private static final int MAX_DURATION_MINUTES = 7 * 24 * 60;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get()
            .resolve(YoikoServerCore.MODID).resolve("breaking_news_events.json");

    private static boolean enabled = true;
    private static boolean announceOnLogin = true;
    private static AutomaticConfig automatic = new AutomaticConfig();
    private static FishingConfig fishing = new FishingConfig();
    private static RabbitSwarmConfig rabbits = new RabbitSwarmConfig();
    private static CaptureGoalConfig captureGoal = new CaptureGoalConfig();
    private static OutbreakConfig outbreak = new OutbreakConfig();
    private static long lastTick = Long.MIN_VALUE;
    private static long lastScheduledFishingAttemptAt = Long.MIN_VALUE;
    private static final Map<MinecraftServer, CaptureGoalBossBarState> CAPTURE_GOAL_BOSS_BARS =
            new WeakHashMap<>();

    private BreakingNewsEventManager() {
    }

    public static void init() {
        reload();
        FishingFestivalEventManager.init();
        WildPokemonIncidentBridge.init();
    }

    public static void reload() {
        ensureFile();
        boolean previousEnabled = enabled;
        boolean previousAnnounce = announceOnLogin;
        AutomaticConfig previousAutomatic = automatic;
        FishingConfig previousFishing = fishing;
        RabbitSwarmConfig previousRabbits = rabbits;
        CaptureGoalConfig previousCapture = captureGoal;
        OutbreakConfig previousOutbreak = outbreak;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            Config config = GSON.fromJson(reader, Config.class);
            if (config == null || config.schemaVersion != CONFIG_VERSION) {
                config = new Config();
                writeConfig(config);
            }
            loadValidated(config);
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.warn(
                    "Failed to load breaking-news event config; keeping the last known-good values.", exception);
            enabled = previousEnabled;
            announceOnLogin = previousAnnounce;
            automatic = previousAutomatic;
            fishing = previousFishing;
            rabbits = previousRabbits;
            captureGoal = previousCapture;
            outbreak = previousOutbreak;
        }
    }

    public static boolean enabled() { return enabled; }

    public static int defaultDurationMinutes(Type type) {
        return switch (type) {
            case TREASURE_RABBIT_SWARM -> rabbits.durationMinutes;
            case POKEMON_CAPTURE_GOAL -> captureGoal.durationMinutes;
            case MASS_OUTBREAK -> outbreak.durationMinutes;
            default -> fishing.durationMinutes;
        };
    }

    public static double feebasChance() { return fishing.feebasChance; }
    public static double patternedMagikarpBonusShinyChance() { return fishing.patternedMagikarpBonusShinyChance; }

    public static boolean isActive(MinecraftServer server, Type type) {
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(server);
        return enabled && saved.isActive(System.currentTimeMillis()) && type.id.equals(saved.type());
    }

    public static boolean isRabbitRunActive(MinecraftServer server, long runId) {
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(server);
        return enabled && runId > 0L && saved.runId() == runId
                && Type.TREASURE_RABBIT_SWARM.id.equals(saved.type())
                && saved.isActive(System.currentTimeMillis());
    }

    public static boolean isRabbitRunDormant(MinecraftServer server, long runId) {
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(server);
        return isRabbitRunActive(server, runId) && saved.waitingForActivation();
    }

    public static StartResult start(MinecraftServer server, Type type, int durationMinutes,
                                    @Nullable ServerPlayer ignoredPreferredAnchor) {
        if (!enabled) return new StartResult(false, "disabled", status(server, type));
        long now = System.currentTimeMillis();
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(server);
        if (saved.isActive(now)) return new StartResult(false, "already_active", status(server, type));

        int safeDuration = Math.max(1, Math.min(MAX_DURATION_MINUTES, durationMinutes));
        boolean waitForDiscovery = type == Type.TREASURE_RABBIT_SWARM;
        long runId = saved.begin(type.id, now, safeDuration * 60_000L, waitForDiscovery);

        switch (type) {
            case FISHING_FESTIVAL -> {
                WorldZone zone = selectFishingZone(server.overworld(), fishing.minimumWorldDistance,
                        fishing.maximumWorldDistance, fishing.radius);
                if (zone == null) {
                    saved.cancelActive();
                    return new StartResult(false, "no_spawn_site", status(server, type));
                }
                BlockPos center = new BlockPos(zone.centerX(), server.overworld().getSeaLevel(), zone.centerZ());
                String biomeId = server.overworld().getBiome(center).unwrapKey()
                        .map(key -> key.location().toString()).orElse("minecraft:river");
                saved.configureFishingFestival(server.overworld().dimension().location().toString(),
                        zone.centerX(), zone.centerZ(), zone.radius(), biomeId,
                        directionFromSpawn(server.overworld(), center),
                        distanceFromSpawn(server.overworld(), center));
            }
            case TREASURE_RABBIT_SWARM -> {
                TreasureRabbitManager.SwarmSpawnResult result = TreasureRabbitManager.spawnSwarm(
                        server, runId, rabbits.rabbitCount, rabbits.radiantChance,
                        rabbits.minimumWorldDistance, rabbits.maximumWorldDistance,
                        rabbits.searchRadius);
                if (result == null || result.entityIds().isEmpty()) {
                    saved.cancelActive();
                    return new StartResult(false, "no_spawn_site", status(server, type));
                }
                saved.configureRabbitSwarm(server.overworld().dimension().location().toString(),
                        result.zoneCenter().getX(), result.zoneCenter().getZ(), result.zoneRadius(),
                        result.rabbitCenter().getX(), result.rabbitCenter().getZ(), result.biomeId(),
                        result.directionId(), result.distanceId(), result.entityIds(), result.forcedChunks());
            }
            case POKEMON_CAPTURE_GOAL -> {
                String target = selectTargetSpecies(server, captureGoal.targetSpecies);
                if (target.isBlank()) {
                    saved.cancelActive();
                    return new StartResult(false, "no_target_species", status(server, type));
                }
                int online = Math.max(1, server.getPlayerCount());
                int goal = Math.min(captureGoal.maximumGoal,
                        captureGoal.baseGoal + Math.max(0, online - 1) * captureGoal.perAdditionalPlayer);
                saved.configureCaptureGoal(target, goal);
            }
            case MASS_OUTBREAK -> {
                String target = selectTargetSpecies(server, outbreak.targetSpecies);
                WorldZone zone = selectWorldZone(server.overworld(), outbreak.minimumWorldDistance,
                        outbreak.maximumWorldDistance, outbreak.radius);
                if (target.isBlank() || zone == null) {
                    saved.cancelActive();
                    return new StartResult(false, target.isBlank() ? "no_target_species" : "no_spawn_site", status(server, type));
                }
                saved.configureOutbreak(server.overworld().dimension().location().toString(),
                        zone.centerX(), zone.centerZ(), zone.radius(), target);
            }
        }

        Status status = status(server, type);
        syncCaptureGoalBossBar(server);
        announceStarted(server, status);
        syncZoneToAll(server);
        return new StartResult(true, "", status);
    }

    private static boolean stop(MinecraftServer server, Type requestedType, boolean announce) {
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(server);
        if (saved.type().isBlank() || !requestedType.id.equals(saved.type())) return false;
        Type type = Type.fromId(saved.type());
        if (type == Type.POKEMON_CAPTURE_GOAL) clearCaptureGoalBossBar(server);
        if (type == Type.POKEMON_CAPTURE_GOAL) rewardCaptureGoal(server, saved);
        if (type == Type.TREASURE_RABBIT_SWARM) {
            TreasureRabbitManager.endSwarm(server, saved.runId(), saved.eventEntities());
            releaseForcedChunks(server.overworld(), saved.forcedChunks());
        }
        List<UUID> participants = saved.participants();
        BreakingNewsSavedData.CompletedIncident completed = saved.finish(System.currentTimeMillis());
        if (completed == null) return false;
        syncZoneToAll(server);
        WeeklyNewspaperManager.recordBreakingNews(server, completed);
        sendResultCards(server, completed, participants);
        if (announce) announceEnded(server, completed);
        return true;
    }

    public static boolean stop(MinecraftServer server, boolean announce) {
        Type activeType = Type.fromId(BreakingNewsSavedData.get(server).type());
        return activeType != null && stop(server, activeType, announce);
    }

    private static void sendResultCards(MinecraftServer server,
                                        BreakingNewsSavedData.CompletedIncident incident,
                                        List<UUID> participants) {
        String message = switch (Type.fromId(incident.type())) {
            case FISHING_FESTIVAL -> "yoiko_core.mail.incident_result.fishing|" + incident.primaryCount()
                    + "|" + incident.secondaryCount() + "|" + incident.tertiaryCount();
            case TREASURE_RABBIT_SWARM -> "yoiko_core.mail.incident_result.rabbits|" + incident.primaryCount()
                    + "|" + incident.spawned() + "|" + incident.tertiaryCount();
            case POKEMON_CAPTURE_GOAL -> "yoiko_core.mail.incident_result.capture|" + incident.targetSpecies()
                    + "|" + incident.primaryCount() + "|" + incident.goal();
            case MASS_OUTBREAK -> "yoiko_core.mail.incident_result.outbreak|" + incident.targetSpecies()
                    + "|" + incident.primaryCount() + "|" + incident.tertiaryCount()
                    + "|" + incident.secondaryCount();
            default -> "yoiko_core.mail.incident_result.generic";
        };
        String claimKey = "incident_result:" + incident.type() + ":" + incident.startedAt();
        for (UUID participant : participants) {
            MailboxManager.sendSystemMail(server, participant, "incident_result", claimKey,
                    "yoiko_core.mail.incident_result.title", message, List.of());
        }
    }

    public static Status status(MinecraftServer server) {
        Type activeType = Type.fromId(BreakingNewsSavedData.get(server).type());
        return activeType == null ? inactiveStatus() : status(server, activeType);
    }

    public static Status status(MinecraftServer server, Type requestedType) {
        long now = System.currentTimeMillis();
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(server);
        boolean active = enabled && saved.isActive(now) && requestedType.id.equals(saved.type());
        Type type = active ? Type.fromId(saved.type()) : null;
        long remaining = active && !saved.waitingForActivation()
                ? Math.max(0L, (saved.endsAt() - now + 999L) / 1_000L) : 0L;
        return new Status(active, type, saved.runId(), remaining, saved.waitingForActivation(),
                saved.biomeId(), saved.directionId(), saved.distanceId(), saved.dimensionId(),
                saved.zoneCenterX(), saved.zoneCenterZ(), saved.zoneRadius(), saved.targetSpecies(),
                saved.goal(), saved.spawned(), saved.primaryCount(), saved.secondaryCount(),
                saved.tertiaryCount(), saved.rewardCount(), saved.participantCount());
    }

    private static Status inactiveStatus() {
        return new Status(false, null, 0L, 0L, false, "", "", "", "",
                0, 0, 0, "", 0, 0, 0, 0, 0, 0, 0);
    }

    public static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (lastTick != Long.MIN_VALUE && tick >= lastTick && tick - lastTick < 20L) return;
        lastTick = tick;
        long now = System.currentTimeMillis();
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(server);
        tickScheduledFishing(server, saved, now);
        syncCaptureGoalBossBar(server);
        if (!saved.type().isBlank()) {
            if (!enabled) {
                stop(server, true);
                return;
            }
            if (Type.TREASURE_RABBIT_SWARM.id.equals(saved.type()) && saved.waitingForActivation()) {
                ServerPlayer discoverer = firstPlayerInsideZone(server, saved);
                if (discoverer != null && saved.activate(now)) {
                    TreasureRabbitManager.activateSwarm(server, saved.runId(), saved.eventEntities());
                    server.getPlayerList().broadcastSystemMessage(Component.translatable(
                            "yoiko_core.breaking.rabbits.discovered", discoverer.getDisplayName(),
                            remainingMinutes(status(server))).withStyle(ChatFormatting.GOLD), false);
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME,
                                net.minecraft.sounds.SoundSource.MASTER, 0.7F, 1.25F);
                    }
                }
                return;
            }
            boolean completedSwarm = Type.TREASURE_RABBIT_SWARM.id.equals(saved.type())
                    && saved.spawned() > 0 && saved.primaryCount() + saved.secondaryCount() >= saved.spawned();
            boolean completedGoal = Type.POKEMON_CAPTURE_GOAL.id.equals(saved.type())
                    && saved.goal() > 0 && saved.primaryCount() >= saved.goal();
            if (!saved.isActive(now) || completedSwarm || completedGoal) stop(server, true);
            return;
        }
        tickAutomatic(server, saved, now);
    }

    public static void onPlayerLogin(ServerPlayer player) {
        syncZone(player);
        syncCaptureGoalBossBar(player.server);
        if (!announceOnLogin) return;
        for (Type type : List.of(Type.FISHING_FESTIVAL, Type.POKEMON_CAPTURE_GOAL,
                Type.TREASURE_RABBIT_SWARM, Type.MASS_OUTBREAK)) {
            Status status = status(player.server, type);
            if (!status.active()) continue;
            player.sendSystemMessage(loginMessage(status).copy().withStyle(ChatFormatting.GOLD));
            if (type == Type.TREASURE_RABBIT_SWARM || type == Type.MASS_OUTBREAK) break;
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        CaptureGoalBossBarState state = CAPTURE_GOAL_BOSS_BARS.get(player.server);
        if (state != null) state.bossBar.removePlayer(player);
    }

    public static void onServerStopped(MinecraftServer server) {
        clearCaptureGoalBossBar(server);
        lastTick = Long.MIN_VALUE;
        lastScheduledFishingAttemptAt = Long.MIN_VALUE;
    }

    private static Component loginMessage(Status status) {
        Component message = switch (status.type()) {
            case FISHING_FESTIVAL -> Component.translatable("yoiko_core.breaking.fishing.login",
                    biomeComponent(status.biomeId()), directionComponent(status.directionId()),
                    distanceComponent(status.distanceId()), remainingMinutes(status));
            case TREASURE_RABBIT_SWARM -> status.waitingForActivation()
                    ? Component.translatable("yoiko_core.breaking.rabbits.login.waiting",
                    biomeComponent(status.biomeId()), directionComponent(status.directionId()),
                    distanceComponent(status.distanceId()))
                    : Component.translatable("yoiko_core.breaking.rabbits.login",
                    biomeComponent(status.biomeId()), directionComponent(status.directionId()),
                    distanceComponent(status.distanceId()), remainingMinutes(status));
            case POKEMON_CAPTURE_GOAL -> Component.translatable("yoiko_core.breaking.capture.login",
                    speciesComponent(status.targetSpecies()), status.primaryCount(), status.goal(), remainingMinutes(status));
            case MASS_OUTBREAK -> Component.translatable("yoiko_core.breaking.outbreak.login",
                    speciesComponent(status.targetSpecies()), remainingMinutes(status));
            default -> Component.empty();
        };
        return message;
    }

    public static void recordFishingResult(ServerPlayer player, boolean feebas, boolean shinyPatterned) {
        BreakingNewsSavedData.get(player.server).recordFishing(
                player.getUUID(), feebas, shinyPatterned, System.currentTimeMillis());
    }

    public static boolean claimFishingReward(ServerPlayer player) {
        return BreakingNewsSavedData.get(player.server).claimFishingReward(
                player.getUUID(), System.currentTimeMillis());
    }

    public static boolean isFishingFestivalLocation(ServerLevel level, BlockPos position) {
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(level.getServer());
        long now = System.currentTimeMillis();
        return enabled && Type.FISHING_FESTIVAL.id.equals(saved.type()) && saved.isActive(now)
                && isInsideZone(level, position, saved)
                && isFishingBiome(level, position);
    }

    public static void recordRabbitCaught(TreasureRabbitEntity rabbit, ServerPlayer player) {
        if (rabbit.breakingNewsRunId() <= 0L) return;
        BreakingNewsSavedData.get(player.server).recordRabbitCaught(rabbit.breakingNewsRunId(),
                player.getUUID(), rabbit.treasureVariant() == TreasureRabbitVariant.RADIANT,
                System.currentTimeMillis());
    }

    public static void recordRabbitEscaped(TreasureRabbitEntity rabbit) {
        if (rabbit.breakingNewsRunId() <= 0L || !(rabbit.level() instanceof ServerLevel level)) return;
        BreakingNewsSavedData.get(level.getServer()).recordRabbitEscaped(
                rabbit.breakingNewsRunId(), System.currentTimeMillis());
    }

    public static void recordPokemonCaptured(ServerPlayer player, Pokemon pokemon) {
        if (player == null || pokemon == null || pokemon.getSpecies() == null) return;
        RestedGoldManager.awardActivity(player, RestedGoldManager.Activity.POKEMON_CAPTURE);
        long now = System.currentTimeMillis();
        String species = pokemon.getSpecies().getResourceIdentifier().toString();
        BreakingNewsSavedData captureSaved = BreakingNewsSavedData.get(player.server);
        if (Type.POKEMON_CAPTURE_GOAL.id.equals(captureSaved.type()) && captureSaved.isActive(now)
                && captureSaved.targetSpecies().equals(species)) {
            int previous = captureSaved.primaryCount();
            int current = captureSaved.recordCaptureGoal(
                    player.getUUID(), captureGoal.perPlayerContributionCap, now);
            if (current > previous) syncCaptureGoalBossBar(player.server);
            if (current > previous && (current == captureSaved.goal()
                    || current % captureGoal.progressBroadcastInterval == 0)) {
                player.server.getPlayerList().broadcastSystemMessage(Component.translatable(
                        "yoiko_core.breaking.capture.progress", speciesComponent(species),
                        current, captureSaved.goal())
                        .withStyle(ChatFormatting.AQUA), false);
            }
            if (current >= captureSaved.goal()) {
                stop(player.server, Type.POKEMON_CAPTURE_GOAL, true);
            }
        }
        BreakingNewsSavedData regionalSaved = captureSaved;
        if (Type.MASS_OUTBREAK.id.equals(regionalSaved.type()) && regionalSaved.isActive(now)
                && regionalSaved.targetSpecies().equals(species)
                && isInsideZone(player.serverLevel(), player.blockPosition(), regionalSaved)) {
            regionalSaved.recordOutbreakCapture(player.getUUID(), now);
        }
    }

    @Nullable
    public static OutbreakContext outbreakContext(ServerLevel level, BlockPos position) {
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(level.getServer());
        long now = System.currentTimeMillis();
        if (!enabled || !saved.isActive(now) || !Type.MASS_OUTBREAK.id.equals(saved.type())
                || !isInsideZone(level, position, saved)) return null;
        return new OutbreakContext(saved.runId(), saved.targetSpecies(),
                outbreak.targetReplacementChance, outbreak.bonusShinyChance);
    }

    public static void recordOutbreakSpawn(MinecraftServer server, long runId, boolean shiny) {
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(server);
        if (saved.runId() == runId) saved.recordOutbreakSpawn(shiny, System.currentTimeMillis());
    }

    public static long remainingMinutes(Status status) {
        return Math.max(0L, (status.remainingSeconds() + 59L) / 60L);
    }

    public static Component typeName(@Nullable Type type) {
        return Component.translatable(type == null
                ? "yoiko_core.breaking.type.none" : "yoiko_core.breaking.type." + type.id);
    }

    private static void tickScheduledFishing(MinecraftServer server, BreakingNewsSavedData saved, long now) {
        if (!enabled || !automatic.enabled) return;
        FishingScheduleWindow window = currentFishingScheduleWindow(now);
        if (window == null || window.key().equals(saved.lastFishingScheduleKey())
                || server.getPlayerCount() < automatic.minimumOnlinePlayers) return;
        if (lastScheduledFishingAttemptAt != Long.MIN_VALUE
                && now - lastScheduledFishingAttemptAt < 60_000L) return;
        lastScheduledFishingAttemptAt = now;
        if (saved.isActive(now)) {
            if (Type.FISHING_FESTIVAL.id.equals(saved.type())) {
                saved.markFishingScheduleStarted(window.key());
                return;
            }
            stop(server, true);
        }
        int remainingMinutes = Math.max(1, (int) Math.ceil(
                Duration.between(Instant.ofEpochMilli(now), window.endsAt().toInstant()).toMillis() / 60_000.0D));
        StartResult result = start(server, Type.FISHING_FESTIVAL, remainingMinutes, null);
        if (result.started()) saved.markFishingScheduleStarted(window.key());
    }

    @Nullable
    private static FishingScheduleWindow currentFishingScheduleWindow(long now) {
        ZoneId zone = scheduleZone();
        ZonedDateTime current = Instant.ofEpochMilli(now).atZone(zone);
        DayOfWeek scheduledDay = scheduleDay();
        if (current.getDayOfWeek() != scheduledDay) return null;
        ZonedDateTime startsAt = current.toLocalDate().atTime(fishing.startHour, fishing.startMinute).atZone(zone);
        ZonedDateTime endsAt = startsAt.plusMinutes(fishing.durationMinutes);
        if (current.isBefore(startsAt) || !current.isBefore(endsAt)) return null;
        return new FishingScheduleWindow(startsAt.toLocalDate().toString(), startsAt, endsAt);
    }

    private static void tickAutomatic(MinecraftServer server, BreakingNewsSavedData saved, long now) {
        if (!enabled || !automatic.enabled) return;
        long interval = Math.max(60_000L, automatic.checkIntervalMinutes * 60_000L);
        if (saved.nextAutomaticCheckAt() <= 0L) {
            saved.scheduleNextAutomaticCheck(now + interval);
            return;
        }
        if (now < saved.nextAutomaticCheckAt()) return;
        saved.scheduleNextAutomaticCheck(now + interval);
        if (server.getPlayerCount() < automatic.minimumOnlinePlayers || isFishingBlackout(now)) return;

        double captureRate = Math.max(0.0D, captureGoal.expectedStartsPer24OnlineHours);
        double rabbitRate = Math.max(0.0D, rabbits.expectedStartsPer24OnlineHours);
        double outbreakRate = Math.max(0.0D, outbreak.expectedStartsPer24OnlineHours);
        double totalRate = captureRate + rabbitRate + outbreakRate;
        if (totalRate <= 0.0D) return;
        double intervalMinutes = interval / 60_000.0D;
        double occurrenceChance = 1.0D - Math.exp(-totalRate * intervalMinutes / (24.0D * 60.0D));
        RandomSource random = server.overworld().getRandom();
        if (random.nextDouble() >= occurrenceChance) return;

        double selection = random.nextDouble() * totalRate;
        Type selected;
        if (selection < captureRate) {
            selected = Type.POKEMON_CAPTURE_GOAL;
        } else if (selection < captureRate + rabbitRate) {
            selected = Type.TREASURE_RABBIT_SWARM;
        } else {
            selected = Type.MASS_OUTBREAK;
        }
        if (selected == null) return;
        start(server, selected, defaultDurationMinutes(selected), null);
    }

    private static boolean isFishingBlackout(long now) {
        ZoneId zone = scheduleZone();
        ZonedDateTime current = Instant.ofEpochMilli(now).atZone(zone);
        for (int offset = -1; offset <= 1; offset++) {
            var date = current.toLocalDate().plusDays(offset);
            if (date.getDayOfWeek() != scheduleDay()) continue;
            ZonedDateTime startsAt = date.atTime(fishing.startHour, fishing.startMinute).atZone(zone);
            ZonedDateTime blackoutStarts = startsAt.minusMinutes(fishing.randomIncidentBlockMinutesBefore);
            ZonedDateTime blackoutEnds = startsAt.plusMinutes(fishing.durationMinutes)
                    .plusMinutes(fishing.randomIncidentBlockMinutesAfter);
            if (!current.isBefore(blackoutStarts) && current.isBefore(blackoutEnds)) return true;
        }
        return false;
    }

    private static void announceStarted(MinecraftServer server, Status status) {
        Component message = switch (status.type()) {
            case FISHING_FESTIVAL -> Component.translatable("yoiko_core.breaking.fishing.started",
                    biomeComponent(status.biomeId()), directionComponent(status.directionId()),
                    distanceComponent(status.distanceId()), remainingMinutes(status));
            case TREASURE_RABBIT_SWARM -> Component.translatable("yoiko_core.breaking.rabbits.started",
                    status.spawned(), biomeComponent(status.biomeId()), directionComponent(status.directionId()),
                    distanceComponent(status.distanceId()));
            case POKEMON_CAPTURE_GOAL -> Component.translatable("yoiko_core.breaking.capture.started",
                    speciesComponent(status.targetSpecies()), status.goal(), remainingMinutes(status));
            case MASS_OUTBREAK -> Component.translatable("yoiko_core.breaking.outbreak.started",
                    speciesComponent(status.targetSpecies()), remainingMinutes(status));
            default -> Component.empty();
        };
        server.getPlayerList().broadcastSystemMessage(message.copy().withStyle(ChatFormatting.GOLD), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    net.minecraft.sounds.SoundSource.MASTER, 0.7F, 1.1F);
        }
    }

    private static void announceEnded(MinecraftServer server, BreakingNewsSavedData.CompletedIncident incident) {
        Component message = switch (Type.fromId(incident.type())) {
            case FISHING_FESTIVAL -> Component.translatable("yoiko_core.breaking.fishing.ended",
                    incident.primaryCount(), incident.secondaryCount(), incident.tertiaryCount(), incident.participantCount());
            case TREASURE_RABBIT_SWARM -> Component.translatable("yoiko_core.breaking.rabbits.ended",
                    incident.primaryCount(), incident.spawned(), incident.tertiaryCount(), incident.participantCount());
            case POKEMON_CAPTURE_GOAL -> Component.translatable(
                    incident.primaryCount() >= incident.goal() ? "yoiko_core.breaking.capture.succeeded" : "yoiko_core.breaking.capture.failed",
                    speciesComponent(incident.targetSpecies()), incident.primaryCount(), incident.goal(), incident.participantCount());
            case MASS_OUTBREAK -> Component.translatable("yoiko_core.breaking.outbreak.ended",
                    speciesComponent(incident.targetSpecies()), incident.primaryCount(), incident.tertiaryCount(), incident.secondaryCount());
            default -> Component.translatable("yoiko_core.breaking.type.none");
        };
        server.getPlayerList().broadcastSystemMessage(message.copy().withStyle(ChatFormatting.GRAY), false);
    }

    private static void rewardCaptureGoal(MinecraftServer server, BreakingNewsSavedData saved) {
        boolean success = saved.goal() > 0 && saved.primaryCount() >= saved.goal();
        int best = saved.contributions().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int delivered = 0;
        for (Map.Entry<UUID, Integer> entry : saved.contributions().entrySet()) {
            List<ItemStack> items = new ArrayList<>();
            if (success) {
                items.add(YoikoItems.ALL_POKEMON_GACHA_TICKET.toStack());
                items.add(new ItemStack(YoikoItems.RELIC_SCRAP.get(), entry.getValue() == best ? 10 : 5));
            } else {
                items.add(new ItemStack(YoikoItems.RELIC_SCRAP.get(), 2));
            }
            boolean sent = MailboxManager.sendSystemMail(server, entry.getKey(), "breaking_news",
                    "capture_goal:" + saved.runId(), "yoiko_core.mail.breaking.capture.title",
                    success ? "yoiko_core.mail.breaking.capture.success" : "yoiko_core.mail.breaking.capture.failure",
                    items);
            if (sent) delivered++;
        }
        saved.addRewardCount(delivered);
    }

    private static void syncZoneToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) syncZone(player);
    }

    private static void syncCaptureGoalBossBar(MinecraftServer server) {
        Status status = status(server, Type.POKEMON_CAPTURE_GOAL);
        if (!status.active() || status.goal() <= 0) {
            clearCaptureGoalBossBar(server);
            return;
        }

        CaptureGoalBossBarState state = CAPTURE_GOAL_BOSS_BARS.get(server);
        if (state == null || state.runId != status.runId()) {
            if (state != null) state.bossBar.removeAllPlayers();
            state = new CaptureGoalBossBarState(status.runId());
            CAPTURE_GOAL_BOSS_BARS.put(server, state);
        }

        state.bossBar.setName(Component.translatable("yoiko_core.breaking.capture.bossbar",
                speciesComponent(status.targetSpecies()), status.primaryCount(), status.goal(),
                remainingClock(status.remainingSeconds())));
        state.bossBar.setProgress(Math.max(0.0F,
                Math.min(1.0F, (float) status.primaryCount() / (float) status.goal())));
        state.bossBar.setVisible(true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            state.bossBar.addPlayer(player);
        }
    }

    private static void clearCaptureGoalBossBar(MinecraftServer server) {
        CaptureGoalBossBarState state = CAPTURE_GOAL_BOSS_BARS.remove(server);
        if (state != null) state.bossBar.removeAllPlayers();
    }

    private static String remainingClock(long remainingSeconds) {
        long safeSeconds = Math.max(0L, remainingSeconds);
        return String.format(Locale.ROOT, "%d:%02d", safeSeconds / 60L, safeSeconds % 60L);
    }

    private static void syncZone(ServerPlayer player) {
        BreakingNewsSavedData saved = BreakingNewsSavedData.get(player.server);
        boolean visible = saved.isActive(System.currentTimeMillis()) && saved.zoneRadius() > 0
                && (Type.FISHING_FESTIVAL.id.equals(saved.type())
                || Type.TREASURE_RABBIT_SWARM.id.equals(saved.type())
                || Type.MASS_OUTBREAK.id.equals(saved.type()));
        PacketDistributor.sendToPlayer(player, new BreakingNewsZonePayload(visible,
                visible ? saved.runId() : 0L, visible ? saved.type() : "",
                visible ? saved.dimensionId() : "", visible ? saved.zoneCenterX() : 0,
                visible ? saved.zoneCenterZ() : 0, visible ? saved.zoneRadius() : 0,
                visible ? saved.targetSpecies() : ""));
    }

    @Nullable
    private static ServerPlayer firstPlayerInsideZone(MinecraftServer server, BreakingNewsSavedData saved) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && isInsideZone(player.serverLevel(), player.blockPosition(), saved)) return player;
        }
        return null;
    }

    private static boolean isInsideZone(ServerLevel level, BlockPos position, BreakingNewsSavedData saved) {
        if (!level.dimension().location().toString().equals(saved.dimensionId()) || saved.zoneRadius() <= 0) return false;
        long dx = (long) position.getX() - saved.zoneCenterX();
        long dz = (long) position.getZ() - saved.zoneCenterZ();
        return dx * dx + dz * dz <= (long) saved.zoneRadius() * saved.zoneRadius();
    }

    private static boolean isFishingBiome(ServerLevel level, BlockPos position) {
        var biome = level.getBiome(position);
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER);
    }

    @Nullable
    private static WorldZone selectFishingZone(ServerLevel level, int minimumDistance,
                                                int maximumDistance, int radius) {
        BlockPos spawn = level.getSharedSpawnPos();
        int safeMinimum = Math.max(256, minimumDistance);
        int safeMaximum = Math.max(safeMinimum, maximumDistance);
        int safeRadius = Math.max(64, radius);
        RandomSource random = level.getRandom();
        var biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        for (int attempt = 0; attempt < 24; attempt++) {
            var result = biomeSource.findBiomeHorizontal(spawn.getX(), level.getSeaLevel(), spawn.getZ(),
                    safeMaximum, holder -> holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_RIVER),
                    random, sampler);
            if (result == null) return null;
            BlockPos found = result.getFirst();
            int x = (found.getX() & ~15) + 8;
            int z = (found.getZ() & ~15) + 8;
            long dx = (long) x - spawn.getX();
            long dz = (long) z - spawn.getZ();
            if (dx * dx + dz * dz < (long) safeMinimum * safeMinimum) continue;
            BlockPos center = new BlockPos(x, level.getSeaLevel(), z);
            if (!level.getWorldBorder().isWithinBounds(center)
                    || !level.getWorldBorder().isWithinBounds(center.offset(safeRadius, 0, 0))
                    || !level.getWorldBorder().isWithinBounds(center.offset(-safeRadius, 0, 0))
                    || !level.getWorldBorder().isWithinBounds(center.offset(0, 0, safeRadius))
                    || !level.getWorldBorder().isWithinBounds(center.offset(0, 0, -safeRadius))) continue;
            level.getChunk(x >> 4, z >> 4);
            if (!isFishingBiome(level, center)) continue;
            return new WorldZone(x, z, safeRadius);
        }
        return null;
    }

    @Nullable
    private static WorldZone selectWorldZone(ServerLevel level, int minimumDistance, int maximumDistance, int radius) {
        BlockPos spawn = level.getSharedSpawnPos();
        int safeMinimum = Math.max(256, minimumDistance);
        int safeMaximum = Math.max(safeMinimum, maximumDistance);
        int safeRadius = Math.max(64, radius);
        for (int attempt = 0; attempt < 64; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            int distance = safeMinimum + level.getRandom().nextInt(safeMaximum - safeMinimum + 1);
            int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos requested = new BlockPos(x, spawn.getY(), z);
            if (!level.getWorldBorder().isWithinBounds(requested)
                    || !level.getWorldBorder().isWithinBounds(requested.offset(safeRadius, 0, 0))
                    || !level.getWorldBorder().isWithinBounds(requested.offset(-safeRadius, 0, 0))
                    || !level.getWorldBorder().isWithinBounds(requested.offset(0, 0, safeRadius))
                    || !level.getWorldBorder().isWithinBounds(requested.offset(0, 0, -safeRadius))) continue;
            level.getChunk(x >> 4, z >> 4);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, requested);
            BlockPos ground = surface.below();
            if (!level.getFluidState(surface).isEmpty() || !level.getFluidState(ground).isEmpty()
                    || !level.getBlockState(ground).isCollisionShapeFullBlock(level, ground)) continue;
            return new WorldZone(x, z, safeRadius);
        }
        return null;
    }

    private static String directionFromSpawn(ServerLevel level, BlockPos pos) {
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = pos.getX() - spawn.getX();
        double dz = pos.getZ() - spawn.getZ();
        if (dx * dx + dz * dz < 128.0D * 128.0D) return "center";
        double angle = Math.atan2(dz, dx);
        int sector = Math.floorMod((int) Math.round(angle / (Math.PI / 4.0D)), 8);
        return switch (sector) {
            case 0 -> "east";
            case 1 -> "southeast";
            case 2 -> "south";
            case 3 -> "southwest";
            case 4 -> "west";
            case 5 -> "northwest";
            case 6 -> "north";
            default -> "northeast";
        };
    }

    private static String distanceFromSpawn(ServerLevel level, BlockPos pos) {
        double distance = Math.sqrt(pos.distSqr(level.getSharedSpawnPos()));
        if (distance < 512.0D) return "near";
        if (distance < 2_048.0D) return "middle";
        return "far";
    }

    private static void releaseForcedChunks(ServerLevel level, List<Long> chunks) {
        for (long value : chunks) {
            ChunkPos pos = new ChunkPos(value);
            level.setChunkForced(pos.x, pos.z, false);
        }
    }

    private static String selectTargetSpecies(MinecraftServer server, List<String> configured) {
        Set<String> candidates = new LinkedHashSet<>();
        if (configured != null) {
            for (String raw : configured) {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                Species species = id == null ? null : PokemonSpecies.getByIdentifier(id);
                if (species != null && species.getImplemented()
                        && GachaPoolScanner.rarityOf(id.toString()) == GachaRarity.COMMON) {
                    candidates.add(id.toString());
                }
            }
        }
        if (candidates.isEmpty()) {
            for (String raw : GachaPoolScanner.list(GachaRarity.COMMON, 2_000)) {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id != null && PokemonSpecies.getByIdentifier(id) != null) candidates.add(id.toString());
            }
        }
        if (candidates.isEmpty()) return "";
        List<String> list = List.copyOf(candidates);
        return list.get(server.overworld().getRandom().nextInt(list.size()));
    }

    private static Component speciesComponent(String speciesId) {
        ResourceLocation id = ResourceLocation.tryParse(speciesId);
        Species species = id == null ? null : PokemonSpecies.getByIdentifier(id);
        return species == null ? Component.literal(speciesId.isBlank() ? "?" : speciesId) : species.getTranslatedName();
    }

    private static Component biomeComponent(String biomeId) {
        ResourceLocation id = ResourceLocation.tryParse(biomeId);
        return id == null ? Component.literal(biomeId.isBlank() ? "?" : biomeId)
                : Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
    }

    private static Component directionComponent(String id) {
        return Component.translatable("yoiko_core.breaking.clue.direction." + safeClueId(id));
    }

    private static Component distanceComponent(String id) {
        return Component.translatable("yoiko_core.breaking.clue.distance." + safeClueId(id));
    }

    private static String safeClueId(String id) {
        return id == null || id.isBlank() ? "unknown" : id.toLowerCase(Locale.ROOT);
    }

    private static ZoneId scheduleZone() {
        try {
            return ZoneId.of(fishing.timeZone);
        } catch (RuntimeException ignored) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    private static DayOfWeek scheduleDay() {
        try {
            return DayOfWeek.valueOf(fishing.dayOfWeek.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return DayOfWeek.SATURDAY;
        }
    }

    private static void loadValidated(Config config) {
        enabled = config.enabled;
        announceOnLogin = config.announceOnLogin;
        automatic = validated(config.automatic == null ? new AutomaticConfig() : config.automatic);
        fishing = validated(config.fishingFestival == null ? new FishingConfig() : config.fishingFestival);
        rabbits = validated(config.treasureRabbitSwarm == null ? new RabbitSwarmConfig() : config.treasureRabbitSwarm);
        captureGoal = validated(config.pokemonCaptureGoal == null ? new CaptureGoalConfig() : config.pokemonCaptureGoal);
        outbreak = validated(config.massOutbreak == null ? new OutbreakConfig() : config.massOutbreak);
    }

    private static AutomaticConfig validated(AutomaticConfig source) {
        AutomaticConfig value = new AutomaticConfig();
        value.enabled = source.enabled;
        value.checkIntervalMinutes = clamp(source.checkIntervalMinutes, 1, 24 * 60);
        value.minimumOnlinePlayers = clamp(source.minimumOnlinePlayers, 1, 1000);
        return value;
    }

    private static FishingConfig validated(FishingConfig source) {
        FishingConfig value = new FishingConfig();
        value.durationMinutes = clamp(source.durationMinutes, 1, MAX_DURATION_MINUTES);
        value.timeZone = validZoneId(source.timeZone, "Asia/Seoul");
        value.dayOfWeek = validDayOfWeek(source.dayOfWeek, "SATURDAY");
        value.startHour = clamp(source.startHour, 0, 23);
        value.startMinute = clamp(source.startMinute, 0, 59);
        value.randomIncidentBlockMinutesBefore = clamp(
                source.randomIncidentBlockMinutesBefore, 0, 24 * 60);
        value.randomIncidentBlockMinutesAfter = clamp(
                source.randomIncidentBlockMinutesAfter, 0, 24 * 60);
        value.minimumWorldDistance = clamp(source.minimumWorldDistance, 256, 30_000_000);
        value.maximumWorldDistance = clamp(source.maximumWorldDistance,
                value.minimumWorldDistance, 30_000_000);
        value.radius = clamp(source.radius, 64, 1_024);
        value.feebasChance = clampChance(source.feebasChance);
        value.patternedMagikarpBonusShinyChance = clampChance(source.patternedMagikarpBonusShinyChance);
        return value;
    }

    private static RabbitSwarmConfig validated(RabbitSwarmConfig source) {
        RabbitSwarmConfig value = new RabbitSwarmConfig();
        value.durationMinutes = clamp(source.durationMinutes, 1, MAX_DURATION_MINUTES);
        value.expectedStartsPer24OnlineHours = clampRate(source.expectedStartsPer24OnlineHours);
        value.rabbitCount = clamp(source.rabbitCount, 2, 24);
        value.radiantChance = clampChance(source.radiantChance);
        value.minimumWorldDistance = clamp(source.minimumWorldDistance, 256, 30_000_000);
        value.maximumWorldDistance = clamp(source.maximumWorldDistance, value.minimumWorldDistance, 30_000_000);
        value.searchRadius = clamp(source.searchRadius, 64, 512);
        return value;
    }

    private static CaptureGoalConfig validated(CaptureGoalConfig source) {
        CaptureGoalConfig value = new CaptureGoalConfig();
        value.durationMinutes = clamp(source.durationMinutes, 1, MAX_DURATION_MINUTES);
        value.expectedStartsPer24OnlineHours = clampRate(source.expectedStartsPer24OnlineHours);
        value.baseGoal = clamp(source.baseGoal, 1, 10_000);
        value.perAdditionalPlayer = clamp(source.perAdditionalPlayer, 0, 1_000);
        value.maximumGoal = clamp(source.maximumGoal, value.baseGoal, 100_000);
        value.perPlayerContributionCap = clamp(source.perPlayerContributionCap, 1, 10_000);
        value.progressBroadcastInterval = clamp(source.progressBroadcastInterval, 1, 1_000);
        value.targetSpecies = safeList(source.targetSpecies);
        return value;
    }

    private static OutbreakConfig validated(OutbreakConfig source) {
        OutbreakConfig value = new OutbreakConfig();
        value.durationMinutes = clamp(source.durationMinutes, 1, MAX_DURATION_MINUTES);
        value.expectedStartsPer24OnlineHours = clampRate(source.expectedStartsPer24OnlineHours);
        value.minimumWorldDistance = clamp(source.minimumWorldDistance, 256, 30_000_000);
        value.maximumWorldDistance = clamp(source.maximumWorldDistance, value.minimumWorldDistance, 30_000_000);
        value.radius = clamp(source.radius, 64, 1_024);
        value.targetReplacementChance = clampChance(source.targetReplacementChance);
        value.bonusShinyChance = clampChance(source.bonusShinyChance);
        value.targetSpecies = safeList(source.targetSpecies);
        return value;
    }

    private static List<String> safeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank()).limit(1_000).toList();
    }

    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    private static double clampChance(double value) { return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D; }
    private static double clampRate(double value) { return Double.isFinite(value) ? Math.max(0.0D, Math.min(100.0D, value)) : 0.0D; }

    private static String validZoneId(String value, String fallback) {
        try {
            return ZoneId.of(value == null ? "" : value).getId();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String validDayOfWeek(String value, String fallback) {
        try {
            return DayOfWeek.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT)).name();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static void ensureFile() {
        if (Files.exists(CONFIG_FILE)) return;
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            writeConfig(new Config());
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.warn("Failed to create breaking-news event config.", exception);
        }
    }

    private static void writeConfig(Config config) throws java.io.IOException {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) { GSON.toJson(config, writer); }
    }

    public enum Type {
        FISHING_FESTIVAL("fishing_festival"),
        TREASURE_RABBIT_SWARM("treasure_rabbit_swarm"),
        POKEMON_CAPTURE_GOAL("pokemon_capture_goal"),
        MASS_OUTBREAK("mass_outbreak");

        private final String id;
        Type(String id) { this.id = id; }
        public String id() { return id; }
        public IncidentCategory category() { return IncidentCategory.SERVER; }

        @Nullable
        public static Type fromId(String id) {
            for (Type value : values()) if (value.id.equalsIgnoreCase(id)) return value;
            return null;
        }
    }

    private static final class Config {
        private int schemaVersion = CONFIG_VERSION;
        private boolean enabled = true;
        private boolean announceOnLogin = true;
        private AutomaticConfig automatic = new AutomaticConfig();
        private FishingConfig fishingFestival = new FishingConfig();
        private RabbitSwarmConfig treasureRabbitSwarm = new RabbitSwarmConfig();
        private CaptureGoalConfig pokemonCaptureGoal = new CaptureGoalConfig();
        private OutbreakConfig massOutbreak = new OutbreakConfig();
    }

    private static final class AutomaticConfig {
        private boolean enabled = true;
        private int checkIntervalMinutes = 15;
        private int minimumOnlinePlayers = 1;
    }

    private static final class FishingConfig {
        private int durationMinutes = 120;
        private String timeZone = "Asia/Seoul";
        private String dayOfWeek = "SATURDAY";
        private int startHour = 20;
        private int startMinute = 0;
        private int randomIncidentBlockMinutesBefore = 60;
        private int randomIncidentBlockMinutesAfter = 60;
        private int minimumWorldDistance = 600;
        private int maximumWorldDistance = 3_000;
        private int radius = 192;
        private double feebasChance = 0.005D;
        private double patternedMagikarpBonusShinyChance = 0.05D;
    }

    private static final class RabbitSwarmConfig {
        private int durationMinutes = 5;
        private double expectedStartsPer24OnlineHours = 2.0D / 7.0D;
        private int rabbitCount = 6;
        private double radiantChance = 0.005D;
        private int minimumWorldDistance = 600;
        private int maximumWorldDistance = 3_000;
        private int searchRadius = 160;
    }

    private static final class CaptureGoalConfig {
        private int durationMinutes = 60;
        private double expectedStartsPer24OnlineHours = 1.0D;
        private int baseGoal = 20;
        private int perAdditionalPlayer = 10;
        private int maximumGoal = 100;
        private int perPlayerContributionCap = 15;
        private int progressBroadcastInterval = 5;
        private List<String> targetSpecies = List.of("cobblemon:pikachu", "cobblemon:eevee",
                "cobblemon:ralts", "cobblemon:shinx", "cobblemon:swablu", "cobblemon:litwick");
    }

    private static final class CaptureGoalBossBarState {
        private final long runId;
        private final ServerBossEvent bossBar;

        private CaptureGoalBossBarState(long runId) {
            this.runId = runId;
            this.bossBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    private static final class OutbreakConfig {
        private int durationMinutes = 30;
        private double expectedStartsPer24OnlineHours = 2.0D;
        private int minimumWorldDistance = 600;
        private int maximumWorldDistance = 3_000;
        private int radius = 256;
        private double targetReplacementChance = 0.75D;
        private double bonusShinyChance = 0.03D;
        private List<String> targetSpecies = List.of("cobblemon:pikachu", "cobblemon:eevee",
                "cobblemon:wooper", "cobblemon:ralts", "cobblemon:shinx", "cobblemon:swablu",
                "cobblemon:zorua", "cobblemon:minccino", "cobblemon:deerling", "cobblemon:litwick");
    }

    public record Status(boolean active, @Nullable Type type, long runId, long remainingSeconds,
                         boolean waitingForActivation, String biomeId, String directionId,
                         String distanceId, String dimensionId, int zoneCenterX, int zoneCenterZ,
                         int zoneRadius, String targetSpecies, int goal, int spawned,
                         int primaryCount, int secondaryCount, int tertiaryCount,
                         int rewardCount, int participantCount) {
        public IncidentCategory category() {
            return type == null ? IncidentCategory.SERVER : type.category();
        }
    }

    public record StartResult(boolean started, String reason, Status status) { }
    public record OutbreakContext(long runId, String targetSpecies,
                                  double replacementChance, double bonusShinyChance) { }
    private record WorldZone(int centerX, int centerZ, int radius) { }
    private record FishingScheduleWindow(String key, ZonedDateTime startsAt, ZonedDateTime endsAt) { }
}
