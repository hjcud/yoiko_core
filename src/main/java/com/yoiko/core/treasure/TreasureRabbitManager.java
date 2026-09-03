package com.yoiko.core.treasure;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.cosmetic.CosmeticManager;
import com.yoiko.core.cosmetic.RabbitCrownStyle;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.economy.CurrencyManager;
import com.yoiko.core.economy.CurrencyType;
import com.yoiko.core.economy.EconomyManager;
import com.yoiko.core.event.BreakingNewsEventManager;
import com.yoiko.core.event.IncidentCategory;
import com.yoiko.core.mail.MailboxManager;
import com.yoiko.core.newspaper.WeeklyNewspaperManager;
import com.yoiko.core.registry.YoikoEntities;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.relic.RelicManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TreasureRabbitManager {
    private static final int GOLDEN_RELIC_CACHE_TICKETS = 3;
    private static final int GOLDEN_RELIC_CACHE_CRYSTALS = 1;
    private static final int MIRROR_RELIC_CACHE_CRYSTALS = 2;
    private static final int RADIANT_RELIC_CACHE_CRYSTALS = 3;
    private static final int CROWN_COOP_RELIC_CACHE_CRYSTALS = 4;
    private static final int CROWN_RELIC_SCRAP = 40;
    private static long lastExplorationCheckTick = Long.MIN_VALUE;
    private static boolean exclusionConfigValidated;

    private TreasureRabbitManager() {
    }

    public static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        int interval = YoikoCommonConfig.TREASURE_RABBIT_EXPLORATION_CHECK_TICKS.get();
        if (lastExplorationCheckTick != Long.MIN_VALUE
                && tick >= lastExplorationCheckTick
                && tick - lastExplorationCheckTick < interval) {
            return;
        }
        lastExplorationCheckTick = tick;
        validateExcludedZonesOnce();
        TreasureRabbitSavedData saved = TreasureRabbitSavedData.get(server);
        int eligiblePlayers = (int) server.getPlayerList().getPlayers().stream()
                .filter(TreasureRabbitManager::eligible)
                .count();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player, saved, eligiblePlayers);
        }
    }

    private static void tickPlayer(ServerPlayer player, TreasureRabbitSavedData saved, int eligiblePlayers) {
        TreasureRabbitSavedData.ExplorationState state = saved.state(player.getUUID());
        String dimension = player.level().dimension().location().toString();
        double x = player.getX();
        double z = player.getZ();
        if (!eligible(player) || !state.sampled() || !state.sameDimension(dimension)) {
            state.sample(x, z, dimension);
            return;
        }

        double distance = state.distanceTo(x, z);
        state.sample(x, z, dimension);
        int minimumDistance = YoikoCommonConfig.TREASURE_RABBIT_MIN_EXPLORATION_DISTANCE.get();
        int teleportCutoff = YoikoCommonConfig.TREASURE_RABBIT_TELEPORT_DISTANCE.get();
        if (distance > teleportCutoff || distance < 1.0D) {
            return;
        }
        state.setCredit(state.credit() + Math.min(1.0D, distance / minimumDistance));
        saved.markChanged();
        if (state.credit() < 1.0D) {
            return;
        }
        state.setCredit(state.credit() - 1.0D);
        state.incrementMisses();

        double baseChance = YoikoCommonConfig.TREASURE_RABBIT_EXPLORATION_ROLL_CHANCE.get();
        double pityMultiplier = 1.0D;
        if (state.misses() > 240) {
            pityMultiplier = Math.min(4.0D, 1.0D + (state.misses() - 240) / 120.0D);
        }
        double scaleStrength = YoikoCommonConfig.TREASURE_RABBIT_MULTIPLAYER_SCALE_STRENGTH.get();
        double multiplayerDivisor = 1.0D + scaleStrength * Math.max(0, eligiblePlayers - 1);
        double effectiveChance = applySpawnChanceBonus(
                player, baseChance * pityMultiplier / multiplayerDivisor);
        if (player.getRandom().nextDouble() >= effectiveChance) {
            return;
        }

        TreasureRabbitVariant variant = randomNaturalVariant(player);
        TreasureRabbitEntity rabbit = spawnNatural(player, variant);
        if (rabbit != null) {
            player.displayClientMessage(Component.translatable(
                    "yoiko_core.message.treasure_rabbit.spawned." + variant.id(),
                    IncidentCategory.PERSONAL.displayName()), true);
            TreasureRabbitStatistics.spawned(rabbit, player, eligiblePlayers, effectiveChance, state.misses());
            state.resetRolls();
            saved.markChanged();
        }
    }

    private static TreasureRabbitVariant randomNaturalVariant(ServerPlayer player) {
        double roll = player.getRandom().nextDouble();
        double crown = Mth.clamp(YoikoCommonConfig.TREASURE_RABBIT_CROWN_CHANCE.get(), 0.0D, 1.0D);
        double radiant = Mth.clamp(YoikoCommonConfig.TREASURE_RABBIT_RADIANT_CHANCE.get(), 0.0D, 1.0D);
        double mirror = Mth.clamp(YoikoCommonConfig.TREASURE_RABBIT_MIRROR_CHANCE.get(), 0.0D, 1.0D);
        if (roll < crown) {
            return TreasureRabbitVariant.CROWN;
        }
        if (roll < crown + radiant) {
            return TreasureRabbitVariant.RADIANT;
        }
        if (roll < crown + radiant + mirror) {
            return TreasureRabbitVariant.MIRROR;
        }
        return TreasureRabbitVariant.GOLDEN;
    }

    public static double applySpawnChanceBonus(ServerPlayer player, double chance) {
        double bonusPercent = Math.min(25.0D,
                Math.max(0.0D, RelicManager.effectBonus(player, "treasure_rabbit_spawn_chance_bonus")));
        return Math.min(1.0D, Math.max(0.0D, chance) * (1.0D + bonusPercent / 100.0D));
    }

    public static boolean eligible(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()
                || player.level().dimension() != Level.OVERWORLD) {
            return false;
        }
        BlockPos pos = player.blockPosition();
        return player.level().canSeeSky(pos.above()) && !player.isPassenger();
    }

    /**
     * Starts an operator-requested personal incident without consuming exploration credit or
     * changing the target player's natural-spawn pity state.
     */
    public static PersonalIncidentStartResult startPersonalIncident(
            ServerPlayer target, TreasureRabbitVariant variant) {
        if (!eligibleForForcedPersonalIncident(target)) {
            return PersonalIncidentStartResult.failed("ineligible_target");
        }
        if (countPersonalIncidents(target) > 0) {
            return PersonalIncidentStartResult.failed("already_active");
        }
        ServerLevel level = target.serverLevel();
        if (!level.getEntitiesOfClass(TreasureRabbitEntity.class,
                target.getBoundingBox().inflate(128.0D)).isEmpty()) {
            return PersonalIncidentStartResult.failed("nearby_rabbit");
        }
        BlockPos spawn = findNaturalSpawn(target, target.getRandom(), variant);
        if (spawn == null) {
            return PersonalIncidentStartResult.failed("no_spawn_site");
        }
        TreasureRabbitEntity rabbit = spawn(level, spawn, variant, true);
        if (rabbit == null) {
            return PersonalIncidentStartResult.failed("spawn_failed");
        }
        rabbit.setEncounterTarget(target.getUUID());
        target.displayClientMessage(Component.translatable(
                "yoiko_core.message.treasure_rabbit.spawned." + variant.id(),
                IncidentCategory.PERSONAL.displayName()), true);
        TreasureRabbitStatistics.spawnedByCommand(rabbit, target);
        return PersonalIncidentStartResult.started(rabbit);
    }

    /**
     * Operator-started incidents may target Creative players and players standing under a roof or
     * tree. The rabbit itself must still pass the ordinary safe outdoor-ground spawn search.
     */
    private static boolean eligibleForForcedPersonalIncident(ServerPlayer target) {
        return !target.isSpectator() && !target.isDeadOrDying()
                && target.level().dimension() == Level.OVERWORLD && !target.isPassenger();
    }

    /** Counts loaded personal incidents. A target of {@code null} counts all players. */
    public static int countPersonalIncidents(MinecraftServer server, @Nullable UUID targetId) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof TreasureRabbitEntity rabbit
                        && isPersonalIncidentFor(rabbit, targetId)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int countPersonalIncidents(ServerPlayer target) {
        return countPersonalIncidents(target.server, target.getUUID());
    }

    /** Starts the normal burrow-away sequence; search markers clear only when removal finishes. */
    public static int stopPersonalIncidents(ServerPlayer target) {
        int stopped = 0;
        for (ServerLevel level : target.server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof TreasureRabbitEntity rabbit
                        && isPersonalIncidentFor(rabbit, target.getUUID())
                        && rabbit.endPersonalEncounter("admin_stop")) {
                    stopped++;
                }
            }
        }
        return stopped;
    }

    private static boolean isPersonalIncidentFor(TreasureRabbitEntity rabbit, @Nullable UUID targetId) {
        return !rabbit.isRemoved()
                && rabbit.incidentCategory() == IncidentCategory.PERSONAL
                && (targetId == null || targetId.equals(rabbit.encounterTargetId()));
    }

    private static TreasureRabbitEntity spawnNatural(ServerPlayer player, TreasureRabbitVariant variant) {
        ServerLevel level = player.serverLevel();
        if (!level.getEntitiesOfClass(TreasureRabbitEntity.class,
                player.getBoundingBox().inflate(128.0D)).isEmpty()) {
            return null;
        }
        BlockPos spawn = findNaturalSpawn(player, player.getRandom(), variant);
        if (spawn == null) {
            return null;
        }
        TreasureRabbitEntity rabbit = spawn(level, spawn, variant, true);
        if (rabbit != null) {
            rabbit.setEncounterTarget(player.getUUID());
        }
        return rabbit;
    }

    public static TreasureRabbitEntity spawn(ServerLevel level, BlockPos pos,
                                              TreasureRabbitVariant variant, boolean naturalEncounter) {
        TreasureRabbitEntity rabbit = YoikoEntities.TREASURE_RABBIT.get().create(level);
        if (rabbit == null) {
            return null;
        }
        rabbit.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        rabbit.setTreasureVariant(variant);
        rabbit.setNaturalEncounter(naturalEncounter);
        rabbit.configureRequiredHits(variant == TreasureRabbitVariant.CROWN
                ? YoikoCommonConfig.TREASURE_RABBIT_CROWN_HITS_REQUIRED.get()
                : YoikoCommonConfig.TREASURE_RABBIT_HITS_REQUIRED.get());
        rabbit.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        rabbit.setTreasureVariant(variant);
        if (!level.addFreshEntity(rabbit)) {
            return null;
        }
        return rabbit;
    }

    /**
     * Shuffles the real Mirror Rabbit onto a safe nearby surface and leaves a decoy at its
     * original position. This makes following the pre-hit target unreliable without exposing
     * the real body through a different spawn animation.
     */
    static void spawnMirrorDecoys(TreasureRabbitEntity original) {
        if (!(original.level() instanceof ServerLevel level) || original.isMirrorDecoy()) {
            return;
        }
        int count = Mth.clamp(YoikoCommonConfig.TREASURE_RABBIT_MIRROR_DECOYS.get(), 3, 12);
        BlockPos origin = original.blockPosition();
        List<BlockPos> destinations = findMirrorShuffleDestinations(level, origin, count, original.getRandom());
        if (destinations.isEmpty()) {
            return;
        }

        BlockPos realDestination = destinations.remove(original.getRandom().nextInt(destinations.size()));
        level.sendParticles(ParticleTypes.POOF,
                original.getX(), original.getY() + 0.4D, original.getZ(),
                16, 0.34D, 0.34D, 0.34D, 0.025D);
        original.getNavigation().stop();
        original.setDeltaMovement(Vec3.ZERO);
        original.moveTo(realDestination.getX() + 0.5D, realDestination.getY(), realDestination.getZ() + 0.5D,
                original.getRandom().nextFloat() * 360.0F, 0.0F);
        original.beginMirrorShuffleProtection();

        List<BlockPos> decoyPositions = new ArrayList<>(count);
        decoyPositions.add(origin);
        decoyPositions.addAll(destinations);
        for (int index = 0; index < Math.min(count, decoyPositions.size()); index++) {
            BlockPos decoyPosition = decoyPositions.get(index);
            TreasureRabbitEntity decoy = spawn(level, decoyPosition, TreasureRabbitVariant.MIRROR, false);
            if (decoy == null) {
                continue;
            }
            decoy.setMirrorDecoy(true);
            decoy.setMirrorOwnerId(original.getUUID());
            decoy.setEncounterTarget(original.encounterTargetId());
            decoy.setExpiresAfterTicks(600);
            level.sendParticles(ParticleTypes.END_ROD, decoy.getX(), decoy.getY() + 0.4D, decoy.getZ(),
                    10, 0.28D, 0.30D, 0.28D, 0.04D);
        }
        level.sendParticles(ParticleTypes.END_ROD, original.getX(), original.getY() + 0.4D, original.getZ(),
                10, 0.28D, 0.30D, 0.28D, 0.04D);
        level.playSound(null, origin, SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.NEUTRAL, 1.0F, 1.55F);
    }

    private static List<BlockPos> findMirrorShuffleDestinations(ServerLevel level, BlockPos origin,
                                                                 int desiredCount, RandomSource random) {
        List<BlockPos> destinations = new ArrayList<>(desiredCount);
        int maximumAttempts = desiredCount * 16;
        for (int attempt = 0; attempt < maximumAttempts && destinations.size() < desiredCount; attempt++) {
            double angle = Math.PI * 2.0D * (attempt % desiredCount) / desiredCount
                    + random.nextDouble() * 0.55D;
            double distance = 3.0D + random.nextDouble() * 4.0D;
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos candidate = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, origin.getY(), z));
            if (!level.canSeeSky(candidate)
                    || !isSafeTerrain(level, candidate, 0, false)
                    || Math.abs(candidate.getY() - origin.getY()) > 3
                    || isNearAny(candidate, destinations, 2)) {
                continue;
            }
            destinations.add(candidate.immutable());
        }
        return destinations;
    }

    private static boolean isNearAny(BlockPos candidate, List<BlockPos> positions, int minimumDistance) {
        long minimumDistanceSquared = (long) minimumDistance * minimumDistance;
        for (BlockPos position : positions) {
            long dx = (long) candidate.getX() - position.getX();
            long dz = (long) candidate.getZ() - position.getZ();
            if (dx * dx + dz * dz < minimumDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    /** Shatters every loaded decoy linked to this real Mirror Rabbit. */
    static void shatterMirrorDecoys(TreasureRabbitEntity original) {
        if (!(original.level() instanceof ServerLevel level) || original.isMirrorDecoy()) {
            return;
        }
        List<TreasureRabbitEntity> linkedDecoys = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof TreasureRabbitEntity decoy
                    && decoy.isMirrorDecoy()
                    && original.getUUID().equals(decoy.mirrorOwnerId())) {
                linkedDecoys.add(decoy);
            }
        }
        int shattered = 0;
        for (TreasureRabbitEntity decoy : linkedDecoys) {
            if (decoy.shatterAfterMirrorOwnerEnds()) {
                shattered++;
            }
        }
        if (shattered > 0) {
            level.playSound(null, original.blockPosition(), SoundEvents.GLASS_BREAK,
                    SoundSource.NEUTRAL, 1.15F, 1.3F);
        }
    }

    /** Creates one fixed, server-selected swarm site independent of any particular player's position. */
    @Nullable
    public static SwarmSpawnResult spawnSwarm(MinecraftServer server,
                                              long breakingNewsRunId, int requestedCount,
                                              double radiantChance, int minimumWorldDistance,
                                              int maximumWorldDistance, int searchRadius) {
        ServerPlayer reporter = server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.isSpectator()).findAny().orElse(null);
        if (reporter == null) return null;
        ServerLevel level = server.overworld();
        RandomSource random = level.getRandom();
        int safeCount = Mth.clamp(requestedCount, 2, 24);
        double safeRadiantChance = Mth.clamp(radiantChance, 0.0D, 1.0D);
        int safeMinimumDistance = Mth.clamp(minimumWorldDistance, 256, 30_000_000);
        int safeMaximumDistance = Mth.clamp(maximumWorldDistance, safeMinimumDistance, 30_000_000);
        int safeSearchRadius = Mth.clamp(searchRadius, 64, 512);
        BlockPos center = findFixedSwarmCenter(level, random, safeMinimumDistance, safeMaximumDistance);
        if (center == null) return null;
        List<Long> forcedChunks = forceSwarmChunks(level, center);
        Set<BlockPos> used = new LinkedHashSet<>();
        List<UUID> entityIds = new ArrayList<>();
        for (int index = 0; index < safeCount; index++) {
            BlockPos spawn = index == 0 ? center : findSwarmMemberSpawn(level, center, used, random);
            if (spawn == null) continue;
            TreasureRabbitVariant variant = random.nextDouble() < safeRadiantChance
                    ? TreasureRabbitVariant.RADIANT : TreasureRabbitVariant.GOLDEN;
            TreasureRabbitEntity rabbit = spawn(level, spawn, variant, true);
            if (rabbit == null) continue;
            used.add(spawn.immutable());
            rabbit.setEncounterTarget(null);
            rabbit.setBreakingNewsRunId(breakingNewsRunId);
            rabbit.setBreakingNewsDormant(true);
            entityIds.add(rabbit.getUUID());
            TreasureRabbitStatistics.spawnedByBreakingNews(
                    rabbit, reporter, breakingNewsRunId, safeCount);
        }
        if (entityIds.size() < 2) {
            endSwarm(server, breakingNewsRunId, entityIds);
            releaseForcedChunks(level, forcedChunks);
            return null;
        }
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int offset = random.nextInt(Math.max(1, (int) (safeSearchRadius * 0.55D)));
        BlockPos zoneCenter = new BlockPos(
                center.getX() + Mth.floor(Math.cos(angle) * offset), center.getY(),
                center.getZ() + Mth.floor(Math.sin(angle) * offset));
        String biomeId = level.getBiome(center).unwrapKey()
                .map(key -> key.location().toString()).orElse("minecraft:plains");
        return new SwarmSpawnResult(center, zoneCenter, safeSearchRadius, biomeId,
                directionFromSpawn(level, zoneCenter), distanceFromSpawn(level, zoneCenter),
                List.copyOf(entityIds), List.copyOf(forcedChunks));
    }

    public static void endSwarm(MinecraftServer server, long runId, List<UUID> entityIds) {
        if (entityIds == null) {
            return;
        }
        for (UUID entityId : entityIds) {
            var entity = server.overworld().getEntity(entityId);
            if (entity instanceof TreasureRabbitEntity rabbit
                    && rabbit.breakingNewsRunId() == runId) {
                rabbit.endBreakingNewsEncounter();
            }
        }
    }

    public static void activateSwarm(MinecraftServer server, long runId, List<UUID> entityIds) {
        if (entityIds == null) return;
        for (UUID entityId : entityIds) {
            var entity = server.overworld().getEntity(entityId);
            if (entity instanceof TreasureRabbitEntity rabbit && rabbit.breakingNewsRunId() == runId) {
                rabbit.activateBreakingNewsEncounter();
            }
        }
    }

    @Nullable
    private static BlockPos findFixedSwarmCenter(ServerLevel level, RandomSource random,
                                                 int minimumDistance, int maximumDistance) {
        BlockPos origin = level.getSharedSpawnPos();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = minimumDistance + random.nextInt(maximumDistance - minimumDistance + 1);
            int rawX = origin.getX() + Mth.floor(Math.cos(angle) * radius);
            int rawZ = origin.getZ() + Mth.floor(Math.sin(angle) * radius);
            int x = (rawX & ~15) + 8;
            int z = (rawZ & ~15) + 8;
            level.getChunk(x >> 4, z >> 4);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, origin.getY(), z));
            if (!level.getWorldBorder().isWithinBounds(pos)
                    || !level.canSeeSky(pos)
                    || isExcludedSpawnArea(level, pos)
                    || !isSafeTerrain(level, pos, 2, true)) {
                continue;
            }
            return pos;
        }
        return null;
    }

    private static List<Long> forceSwarmChunks(ServerLevel level, BlockPos center) {
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        List<Long> newlyForced = new ArrayList<>();
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                int chunkX = centerChunkX + offsetX;
                int chunkZ = centerChunkZ + offsetZ;
                long key = ChunkPos.asLong(chunkX, chunkZ);
                level.getChunk(chunkX, chunkZ);
                if (!level.getForcedChunks().contains(key)) {
                    level.setChunkForced(chunkX, chunkZ, true);
                    newlyForced.add(key);
                }
            }
        }
        return newlyForced;
    }

    private static void releaseForcedChunks(ServerLevel level, List<Long> chunks) {
        for (long key : chunks) {
            ChunkPos pos = new ChunkPos(key);
            level.setChunkForced(pos.x, pos.z, false);
        }
    }

    @Nullable
    private static BlockPos findSwarmMemberSpawn(ServerLevel level, BlockPos center,
                                                  Set<BlockPos> used, RandomSource random) {
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = 3 + random.nextInt(10);
            int x = center.getX() + Mth.floor(Math.cos(angle) * radius);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * radius);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, center.getY(), z));
            if (!used.contains(pos) && level.canSeeSky(pos)
                    && !isExcludedSpawnArea(level, pos)
                    && isSafeTerrain(level, pos, 0, true)) {
                return pos;
            }
        }
        return null;
    }

    private static String directionFromSpawn(ServerLevel level, BlockPos pos) {
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = pos.getX() - spawn.getX();
        double dz = pos.getZ() - spawn.getZ();
        if (dx * dx + dz * dz < 128.0D * 128.0D) {
            return "center";
        }
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
        if (distance < 512.0D) {
            return "near";
        }
        if (distance < 2_048.0D) {
            return "middle";
        }
        return "far";
    }

    private static BlockPos findNaturalSpawn(ServerPlayer player, RandomSource random,
                                             TreasureRabbitVariant variant) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = 32 + random.nextInt(17);
            int x = center.getX() + Mth.floor(Math.cos(angle) * radius);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * radius);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, center.getY(), z));
            if (!level.getWorldBorder().isWithinBounds(pos)
                    || !level.canSeeSky(pos)
                    || isExcludedSpawnArea(level, pos)
                    || hasPlayerWithinDiscoveryRange(level, pos)
                    || !isSafeTerrain(level, pos, 2, true,
                    variant == TreasureRabbitVariant.CROWN ? 4 : 2)
                    || isClearlyVisibleFrom(player, pos)) {
                continue;
            }
            return pos;
        }
        return null;
    }

    private static boolean hasPlayerWithinDiscoveryRange(ServerLevel level, BlockPos pos) {
        int radius = 24;
        double radiusSquared = (double) radius * radius;
        Vec3 center = Vec3.atCenterOf(pos);
        AABB bounds = new AABB(pos).inflate(radius);
        return level.getEntitiesOfClass(ServerPlayer.class, bounds,
                        player -> player.isAlive() && !player.isCreative() && !player.isSpectator())
                .stream().anyMatch(player -> player.distanceToSqr(center) <= radiusSquared);
    }

    /** Returns a loaded, open landing spot biased away from the pursuing player. */
    static BlockPos findSafeRecoveryLanding(ServerLevel level, BlockPos origin, Vec3 away,
                                             RandomSource random) {
        Vec3 horizontal = away.multiply(1.0D, 0.0D, 1.0D);
        double baseAngle = horizontal.lengthSqr() > 1.0E-4D
                ? Math.atan2(horizontal.z, horizontal.x)
                : random.nextDouble() * Math.PI * 2.0D;
        double[] offsets = {
                0.0D, Math.PI / 6.0D, -Math.PI / 6.0D,
                Math.PI / 3.0D, -Math.PI / 3.0D,
                Math.PI / 2.0D, -Math.PI / 2.0D, Math.PI
        };
        int[] radii = {4, 6, 8};
        for (int radius : radii) {
            for (double offset : offsets) {
                double angle = baseAngle + offset;
                int x = origin.getX() + Mth.floor(Math.cos(angle) * radius);
                int z = origin.getZ() + Mth.floor(Math.sin(angle) * radius);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                BlockPos landing = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(x, origin.getY(), z));
                if (level.canSeeSky(landing) && isSafeTerrain(level, landing, 1, false)) {
                    return landing;
                }
            }
        }
        return null;
    }

    private static boolean isExcludedSpawnArea(ServerLevel level, BlockPos pos) {
        int worldSpawnRadius = YoikoCommonConfig.TREASURE_RABBIT_WORLD_SPAWN_EXCLUSION_RADIUS.get();
        if (worldSpawnRadius > 0 && withinHorizontalRadius(pos, level.getSharedSpawnPos(), worldSpawnRadius)) {
            return true;
        }
        for (String raw : YoikoCommonConfig.TREASURE_RABBIT_EXCLUDED_ZONES.get()) {
            int[] zone = parseExcludedZone(raw);
            if (zone == null) {
                continue;
            }
            if (withinHorizontalRadius(pos,
                    new BlockPos(zone[0], pos.getY(), zone[1]), zone[2])) {
                return true;
            }
        }
        return false;
    }

    private static void validateExcludedZonesOnce() {
        if (exclusionConfigValidated) {
            return;
        }
        exclusionConfigValidated = true;
        for (String raw : YoikoCommonConfig.TREASURE_RABBIT_EXCLUDED_ZONES.get()) {
            if (parseExcludedZone(raw) == null) {
                YoikoServerCore.LOGGER.warn(
                        "Ignoring invalid treasureRabbit.excludedZones entry '{}'; expected x,z,radius with a positive radius.",
                        raw);
            }
        }
    }

    private static int[] parseExcludedZone(String raw) {
        String[] parts = raw.split(",", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int z = Integer.parseInt(parts[1].trim());
            int radius = Integer.parseInt(parts[2].trim());
            return radius > 0 ? new int[]{x, z, radius} : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean withinHorizontalRadius(BlockPos first, BlockPos second, int radius) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static boolean isSafeTerrain(ServerLevel level, BlockPos center,
                                         int sampleRadius, boolean requireRabbitGround) {
        return isSafeTerrain(level, center, sampleRadius, requireRabbitGround, 2);
    }

    private static boolean isSafeTerrain(ServerLevel level, BlockPos center,
                                         int sampleRadius, boolean requireRabbitGround,
                                         int verticalClearance) {
        for (int offsetX = -sampleRadius; offsetX <= sampleRadius; offsetX++) {
            for (int offsetZ = -sampleRadius; offsetZ <= sampleRadius; offsetZ++) {
                int x = center.getX() + offsetX;
                int z = center.getZ() + offsetZ;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    return false;
                }
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(x, center.getY(), z));
                if (!level.getWorldBorder().isWithinBounds(surface)
                        || Math.abs(surface.getY() - center.getY()) > 1
                        || !level.getFluidState(surface.below()).isEmpty()) {
                    return false;
                }
                for (int height = 0; height < verticalClearance; height++) {
                    BlockPos clearancePos = surface.above(height);
                    if (!level.getBlockState(clearancePos).getCollisionShape(level, clearancePos).isEmpty()
                            || !level.getFluidState(clearancePos).isEmpty()) {
                        return false;
                    }
                }
                BlockPos groundPos = surface.below();
                BlockState ground = level.getBlockState(groundPos);
                if (ground.is(Blocks.POWDER_SNOW)
                        || ground.is(BlockTags.LEAVES)
                        || !ground.isCollisionShapeFullBlock(level, groundPos)
                        || (requireRabbitGround && offsetX == 0 && offsetZ == 0
                        && !ground.is(BlockTags.RABBITS_SPAWNABLE_ON))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isClearlyVisibleFrom(ServerPlayer player, BlockPos spawn) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = Vec3.atBottomCenterOf(spawn).add(0.0D, 0.45D, 0.0D);
        Vec3 direction = target.subtract(eye);
        if (direction.lengthSqr() < 1.0D
                || player.getLookAngle().dot(direction.normalize()) <= 0.25D) {
            return false;
        }
        HitResult hit = player.level().clip(new ClipContext(
                eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    public static void rewardCaught(TreasureRabbitEntity rabbit, ServerPlayer player) {
        BreakingNewsEventManager.recordRabbitCaught(rabbit, player);
        WeeklyNewspaperManager.recordTreasureRabbit(player, rabbit.treasureVariant());
        if (rabbit.treasureVariant() != TreasureRabbitVariant.CROWN) {
            YoikoAdvancementManager.recordRabbitCatch(
                    player, rabbit.treasureVariant(), rabbit.contributorCount());
        }
        switch (rabbit.treasureVariant()) {
            case RADIANT -> rewardRadiant(rabbit, player);
            // Mirror Rabbit capture rewards belong only to the player who caught the real body.
            case MIRROR -> rewardMirror(rabbit, player);
            case CROWN -> rewardCrown(rabbit, player);
            default -> rewardGolden(rabbit, player);
        }
        rabbit.level().playSound(null, rabbit.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.8F, switch (rabbit.treasureVariant()) {
                    case RADIANT -> 1.35F;
                    case MIRROR -> 1.55F;
                    case CROWN -> 0.85F;
                    default -> 1.05F;
                });
    }

    private static void rewardGolden(TreasureRabbitEntity rabbit, ServerPlayer player) {
        int minimum = YoikoCommonConfig.TREASURE_RABBIT_GOLD_MIN.get();
        int maximum = Math.max(minimum, YoikoCommonConfig.TREASURE_RABBIT_GOLD_MAX.get());
        long gold = minimum + player.getRandom().nextInt(maximum - minimum + 1);
        long added = CurrencyManager.add(player, CurrencyType.GOLD, gold);
        EconomyManager.recordEconomy(player, "TREASURE_RABBIT_GOLD", added, "variant=golden");
        emitCurrencyReward(rabbit, player, CurrencyType.GOLD, added);
        player.sendSystemMessage(Component.translatable("yoiko_core.message.treasure_rabbit.golden_caught", added)
                .withStyle(ChatFormatting.GOLD));

        if (player.getRandom().nextDouble() < YoikoCommonConfig.TREASURE_RABBIT_GOLD_SCRAP_CHANCE.get()) {
            int count = 8 + player.getRandom().nextInt(5);
            dropReward(rabbit, player, new ItemStack(YoikoItems.RELIC_SCRAP.get(), count));
        }
        if (player.getRandom().nextDouble() < YoikoCommonConfig.TREASURE_RABBIT_GOLD_RELIC_TICKET_CHANCE.get()) {
            dropReward(rabbit, player,
                    new ItemStack(YoikoItems.RELIC_GACHA_TICKET.get(), GOLDEN_RELIC_CACHE_TICKETS));
            dropReward(rabbit, player,
                    new ItemStack(YoikoItems.RELIC_UPGRADE_CRYSTAL.get(), GOLDEN_RELIC_CACHE_CRYSTALS));
            player.sendSystemMessage(Component.translatable("yoiko_core.message.treasure_rabbit.relic_cache",
                            GOLDEN_RELIC_CACHE_TICKETS, GOLDEN_RELIC_CACHE_CRYSTALS)
                    .withStyle(ChatFormatting.AQUA));
        }
        rollShinyTicket(rabbit, player, YoikoCommonConfig.TREASURE_RABBIT_GOLD_SHINY_TICKET_CHANCE.get());
    }

    private static void rewardRadiant(TreasureRabbitEntity rabbit, ServerPlayer player) {
        int minimum = YoikoCommonConfig.TREASURE_RABBIT_RADIANT_GEM_MIN.get();
        int maximum = Math.max(minimum, YoikoCommonConfig.TREASURE_RABBIT_RADIANT_GEM_MAX.get());
        long gems = minimum + player.getRandom().nextInt(maximum - minimum + 1);
        long added = CurrencyManager.add(player, CurrencyType.GEM, gems);
        EconomyManager.recordEconomy(player, "TREASURE_RABBIT_GEMS", added, "variant=radiant");
        emitCurrencyReward(rabbit, player, CurrencyType.GEM, added);
        player.sendSystemMessage(Component.translatable("yoiko_core.message.treasure_rabbit.radiant_caught", added)
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        rollShinyTicket(rabbit, player, YoikoCommonConfig.TREASURE_RABBIT_RADIANT_SHINY_TICKET_CHANCE.get());
        if (player.getRandom().nextDouble() < YoikoCommonConfig.TREASURE_RABBIT_RADIANT_RELIC_TICKET_CHANCE.get()) {
            ItemStack focusedTicket = randomFocusedRelicTicket(player.getRandom());
            dropReward(rabbit, player, focusedTicket);
            dropReward(rabbit, player,
                    new ItemStack(YoikoItems.RELIC_UPGRADE_CRYSTAL.get(), RADIANT_RELIC_CACHE_CRYSTALS));
            player.sendSystemMessage(Component.translatable("yoiko_core.message.treasure_rabbit.focused_relic_cache",
                            focusedTicket.getHoverName(), RADIANT_RELIC_CACHE_CRYSTALS)
                    .withStyle(ChatFormatting.AQUA));
        }
        if (player.getRandom().nextDouble() < YoikoCommonConfig.TREASURE_RABBIT_RADIANT_PARTICLE_TICKET_CHANCE.get()) {
            dropReward(rabbit, player, YoikoItems.PARTICLE_GACHA_TICKET.toStack());
            player.sendSystemMessage(Component.translatable("yoiko_core.message.treasure_rabbit.particle_ticket")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }

        player.server.getPlayerList().broadcastSystemMessage(Component.translatable(
                "yoiko_core.message.treasure_rabbit.radiant_broadcast", player.getDisplayName()), false);
    }

    private static void rewardMirror(TreasureRabbitEntity rabbit, ServerPlayer player) {
        int minimum = YoikoCommonConfig.TREASURE_RABBIT_MIRROR_GOLD_MIN.get();
        int maximum = Math.max(minimum, YoikoCommonConfig.TREASURE_RABBIT_MIRROR_GOLD_MAX.get());
        long gold = minimum + player.getRandom().nextInt(maximum - minimum + 1);
        long added = CurrencyManager.add(player, CurrencyType.GOLD, gold);
        EconomyManager.recordEconomy(player, "TREASURE_RABBIT_GOLD", added, "variant=mirror");
        emitCurrencyReward(rabbit, player, CurrencyType.GOLD, added);
        int gemMinimum = YoikoCommonConfig.TREASURE_RABBIT_MIRROR_GEM_MIN.get();
        int gemMaximum = Math.max(gemMinimum, YoikoCommonConfig.TREASURE_RABBIT_MIRROR_GEM_MAX.get());
        long gems = gemMinimum + player.getRandom().nextInt(gemMaximum - gemMinimum + 1);
        long addedGems = CurrencyManager.add(player, CurrencyType.GEM, gems);
        EconomyManager.recordEconomy(player, "TREASURE_RABBIT_GEMS", addedGems, "variant=mirror");
        emitCurrencyReward(rabbit, player, CurrencyType.GEM, addedGems);
        dropReward(rabbit, player,
                new ItemStack(YoikoItems.RELIC_SCRAP.get(), 12 + player.getRandom().nextInt(7)));
        dropReward(rabbit, player,
                new ItemStack(YoikoItems.RELIC_UPGRADE_CRYSTAL.get(), MIRROR_RELIC_CACHE_CRYSTALS));
        rollShinyTicket(rabbit, player, YoikoCommonConfig.TREASURE_RABBIT_MIRROR_SHINY_TICKET_CHANCE.get());
        player.sendSystemMessage(Component.translatable(
                "yoiko_core.message.treasure_rabbit.mirror_caught", added, addedGems)
                .withStyle(ChatFormatting.AQUA));
    }

    private static void rewardCrown(TreasureRabbitEntity rabbit, ServerPlayer completionPlayer) {
        Map<UUID, Integer> contributors = rabbit.contributorHits();
        grantCrownFinisherRewards(completionPlayer, contributors.size());
        int gold = YoikoCommonConfig.TREASURE_RABBIT_CROWN_PARTICIPANT_GOLD.get();
        int gems = YoikoCommonConfig.TREASURE_RABBIT_CROWN_PARTICIPANT_GEMS.get();
        boolean cooperationBonus = contributors.size()
                >= YoikoCommonConfig.TREASURE_RABBIT_CROWN_MIN_BONUS_CONTRIBUTORS.get();
        boolean sharedShinyReward = completionPlayer.getRandom().nextDouble()
                < YoikoCommonConfig.TREASURE_RABBIT_CROWN_SHINY_TICKET_CHANCE.get();
        ItemStack sharedFocusedTicket = cooperationBonus
                ? randomFocusedRelicTicket(completionPlayer.getRandom()) : ItemStack.EMPTY;
        int rewarded = 0;
        for (UUID participantId : contributors.keySet()) {
            ServerPlayer participant = completionPlayer.server.getPlayerList().getPlayer(participantId);
            if (participant == null) {
                ServerYoikoSavedData saved = ServerYoikoSavedData.get(completionPlayer.server);
                PlayerYoikoData offline = saved.get(participantId);
                if (offline == null) {
                    continue;
                }
                List<ItemStack> items = new ArrayList<>();
                items.add(YoikoItems.CROWN_SEALED_RELIC.toStack());
                items.add(new ItemStack(YoikoItems.RELIC_SCRAP.get(), CROWN_RELIC_SCRAP));
                if (sharedShinyReward) {
                    items.add(YoikoItems.SHINY_ALL_POKEMON_GACHA_TICKET.toStack());
                }
                if (cooperationBonus) {
                    items.add(sharedFocusedTicket.copy());
                    items.add(new ItemStack(YoikoItems.RELIC_UPGRADE_CRYSTAL.get(),
                            CROWN_COOP_RELIC_CACHE_CRYSTALS));
                }
                boolean mailed = MailboxManager.sendSystemMail(completionPlayer.server, participantId, "treasure_rabbit",
                        "crown:" + rabbit.getUUID(), "yoiko_core.mail.treasure_rabbit.title",
                        "yoiko_core.mail.treasure_rabbit.crown_offline|" + gold + "|" + gems,
                        items, gold, gems);
                if (!mailed) {
                    long createdGold = CurrencyManager.add(offline, CurrencyType.GOLD, gold);
                    long createdGems = CurrencyManager.add(offline, CurrencyType.GEM, gems);
                    if (createdGold > 0L || createdGems > 0L) {
                        saved.markDirty(participantId);
                    }
                    ServerYoikoAuditSavedData audit = ServerYoikoAuditSavedData.get(completionPlayer.server);
                    audit.addOperational("ECONOMY", "TREASURE_RABBIT_GOLD", participantId, offline.name,
                            "amount=" + createdGold + ";variant=crown;offline=true;mailed=false");
                    audit.addOperational("ECONOMY", "TREASURE_RABBIT_GEMS", participantId, offline.name,
                            "amount=" + createdGems + ";variant=crown;offline=true;mailed=false");
                }
                YoikoAdvancementManager.recordCrownParticipant(
                        completionPlayer.server, participantId, contributors.size());
                rewarded++;
                continue;
            }
            long addedGold = CurrencyManager.add(participant, CurrencyType.GOLD, gold);
            long addedGems = CurrencyManager.add(participant, CurrencyType.GEM, gems);
            EconomyManager.recordEconomy(participant, "TREASURE_RABBIT_GOLD", addedGold,
                    "variant=crown;contributors=" + contributors.size());
            EconomyManager.recordEconomy(participant, "TREASURE_RABBIT_GEMS", addedGems,
                    "variant=crown;contributors=" + contributors.size());
            emitCurrencyReward(rabbit, participant, CurrencyType.GOLD, addedGold);
            emitCurrencyReward(rabbit, participant, CurrencyType.GEM, addedGems);
            dropReward(rabbit, participant, YoikoItems.CROWN_SEALED_RELIC.toStack());
            dropReward(rabbit, participant,
                    new ItemStack(YoikoItems.RELIC_SCRAP.get(), CROWN_RELIC_SCRAP));
            participant.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.treasure_rabbit.crown_relic_reward")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            if (sharedShinyReward) {
                dropReward(rabbit, participant, YoikoItems.SHINY_ALL_POKEMON_GACHA_TICKET.toStack());
                participant.sendSystemMessage(Component.translatable(
                        "yoiko_core.message.treasure_rabbit.shiny_ticket").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (cooperationBonus) {
                dropReward(rabbit, participant, sharedFocusedTicket.copy());
                dropReward(rabbit, participant, new ItemStack(YoikoItems.RELIC_UPGRADE_CRYSTAL.get(),
                        CROWN_COOP_RELIC_CACHE_CRYSTALS));
                participant.sendSystemMessage(Component.translatable(
                                "yoiko_core.message.treasure_rabbit.focused_relic_cache",
                                sharedFocusedTicket.getHoverName(), CROWN_COOP_RELIC_CACHE_CRYSTALS)
                        .withStyle(ChatFormatting.AQUA));
            }
            participant.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.treasure_rabbit.crown_participant_reward",
                    addedGold, addedGems, contributors.size()).withStyle(ChatFormatting.GOLD));
            YoikoAdvancementManager.recordCrownParticipant(participant, contributors.size());
            rewarded++;
        }
        completionPlayer.server.getPlayerList().broadcastSystemMessage(Component.translatable(
                "yoiko_core.message.treasure_rabbit.crown_defeated", rewarded), false);
    }

    private static void grantCrownFinisherRewards(ServerPlayer finisher, int contributors) {
        PlayerYoikoData data = ServerYoikoSavedData.get(finisher.server).getOrCreate(finisher);
        if (!data.ownedCosmetics.contains(RabbitCrownStyle.COSMETIC_ID)
                && CosmeticManager.grant(finisher, RabbitCrownStyle.COSMETIC_ID, false)) {
            finisher.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.treasure_rabbit.crown_cosmetic_granted",
                    Component.translatable("yoiko_core.cosmetic.rabbit_crown.name"))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        YoikoAdvancementManager.recordCrownFinisher(finisher, contributors);
    }

    private static void rollShinyTicket(TreasureRabbitEntity rabbit, ServerPlayer player, double chance) {
        if (chance <= 0.0D || player.getRandom().nextDouble() >= Math.min(1.0D, chance)) {
            return;
        }
        dropReward(rabbit, player, YoikoItems.SHINY_ALL_POKEMON_GACHA_TICKET.toStack());
        player.sendSystemMessage(Component.translatable("yoiko_core.message.treasure_rabbit.shiny_ticket")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static ItemStack randomFocusedRelicTicket(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 0 -> YoikoItems.RELIC_GACHA_TICKET_COMBAT.toStack();
            case 1 -> YoikoItems.RELIC_GACHA_TICKET_DEFENSE.toStack();
            case 2 -> YoikoItems.RELIC_GACHA_TICKET_POKEMON.toStack();
            default -> YoikoItems.RELIC_GACHA_TICKET_EXPLORATION.toStack();
        };
    }

    private static void emitCurrencyReward(TreasureRabbitEntity rabbit, ServerPlayer player,
                                           CurrencyType currency, long amount) {
        if (amount <= 0L) {
            return;
        }
        RewardAnchor anchor = rewardAnchor(rabbit, player);
        ItemStack icon = new ItemStack(currency == CurrencyType.GEM
                ? YoikoItems.CURRENCY_GEM_PARTICLE.get()
                : YoikoItems.CURRENCY_GOLD_PARTICLE.get());
        ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, icon);
        Vec3 target = player.getEyePosition().subtract(0.0D, 0.25D, 0.0D);
        Vec3 direction = target.subtract(anchor.origin());
        if (direction.lengthSqr() <= 1.0E-4D) {
            direction = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            direction = direction.normalize();
        }
        int particleCount = Mth.clamp(6 + Mth.floor(Math.log1p(amount) * 1.55D), 9, 18);
        RandomSource random = anchor.level().getRandom();
        for (int index = 0; index < particleCount; index++) {
            double speed = 0.10D + random.nextDouble() * 0.08D;
            Vec3 velocity = direction.scale(speed).add(
                    random.nextGaussian() * 0.025D,
                    0.045D + random.nextDouble() * 0.035D,
                    random.nextGaussian() * 0.025D);
            anchor.level().sendParticles(particle,
                    anchor.origin().x, anchor.origin().y, anchor.origin().z, 0,
                    velocity.x, velocity.y, velocity.z, 1.0D);
        }
        anchor.level().sendParticles(ParticleTypes.END_ROD,
                anchor.origin().x, anchor.origin().y, anchor.origin().z,
                currency == CurrencyType.GEM ? 7 : 4,
                0.24D, 0.18D, 0.24D, 0.025D);
        anchor.level().playSound(null, BlockPos.containing(anchor.origin()),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                0.55F, currency == CurrencyType.GEM ? 1.65F : 1.30F);
    }

    private static void dropReward(TreasureRabbitEntity rabbit, ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        RewardAnchor anchor = rewardAnchor(rabbit, player);
        Vec3 towardPlayer = player.position().subtract(anchor.origin()).multiply(1.0D, 0.0D, 1.0D);
        if (towardPlayer.lengthSqr() <= 1.0E-4D) {
            double angle = anchor.level().getRandom().nextDouble() * Math.PI * 2.0D;
            towardPlayer = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        } else {
            towardPlayer = towardPlayer.normalize();
        }
        ItemEntity drop = new ItemEntity(anchor.level(),
                anchor.origin().x, anchor.origin().y, anchor.origin().z, stack.copy(),
                towardPlayer.x * 0.16D, 0.26D, towardPlayer.z * 0.16D);
        drop.setTarget(player.getUUID());
        drop.setThrower(player);
        drop.setPickUpDelay(8);
        drop.setExtendedLifetime();
        drop.setGlowingTag(true);
        if (!anchor.level().addFreshEntity(drop)) {
            deliverFallback(player, stack);
        }
    }

    private static RewardAnchor rewardAnchor(TreasureRabbitEntity rabbit, ServerPlayer player) {
        if (rabbit.level() instanceof ServerLevel rabbitLevel
                && player.serverLevel() == rabbitLevel
                && rabbit.distanceToSqr(player) <= 16_384.0D) {
            return new RewardAnchor(rabbitLevel,
                    rabbit.position().add(0.0D, Math.max(0.45D, rabbit.getBbHeight() * 0.3D), 0.0D));
        }
        return new RewardAnchor(player.serverLevel(), player.position().add(0.0D, 0.65D, 0.0D));
    }

    private static void deliverFallback(ServerPlayer player, ItemStack stack) {
        ItemStack remaining = stack.copy();
        if (player.getInventory().add(remaining)) {
            player.getInventory().setChanged();
            return;
        }
        if (!MailboxManager.sendSystemMail(player, "treasure_rabbit", UUID.randomUUID().toString(),
                "yoiko_core.mail.treasure_rabbit.title", "yoiko_core.mail.treasure_rabbit.message", List.of(remaining))) {
            player.drop(remaining, false);
        }
    }

    private record RewardAnchor(ServerLevel level, Vec3 origin) {
    }

    public record SwarmSpawnResult(BlockPos rabbitCenter, BlockPos zoneCenter, int zoneRadius,
                                   String biomeId, String directionId, String distanceId,
                                   List<UUID> entityIds, List<Long> forcedChunks) {
    }

    public record PersonalIncidentStartResult(
            boolean started, String reason, @Nullable TreasureRabbitEntity rabbit) {
        private static PersonalIncidentStartResult started(TreasureRabbitEntity rabbit) {
            return new PersonalIncidentStartResult(true, "", rabbit);
        }

        private static PersonalIncidentStartResult failed(String reason) {
            return new PersonalIncidentStartResult(false, reason, null);
        }
    }
}
