package com.yoiko.core.treasure;

import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.yoiko.core.event.BreakingNewsEventManager;
import com.yoiko.core.event.IncidentCategory;
import com.yoiko.core.network.TreasureRabbitSearchZonePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

public final class TreasureRabbitEntity extends Rabbit {
    /** A vanilla rabbit is 0.5 blocks tall; 7x makes the Crown Rabbit a 3.5-block-tall boss. */
    public static final float CROWN_SCALE = 7.0F;
    private static final EntityDataAccessor<String> TREASURE_VARIANT = SynchedEntityData.defineId(
            TreasureRabbitEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> COUNTED_HITS = SynchedEntityData.defineId(
            TreasureRabbitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REQUIRED_HITS = SynchedEntityData.defineId(
            TreasureRabbitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BURROW_TICKS = SynchedEntityData.defineId(
            TreasureRabbitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MIRROR_DECOY = SynchedEntityData.defineId(
            TreasureRabbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CONTRIBUTOR_COUNT = SynchedEntityData.defineId(
            TreasureRabbitEntity.class, EntityDataSerializers.INT);
    private static final int HIT_COOLDOWN_TICKS = 8;
    private static final int MIRROR_SHUFFLE_PROTECTION_TICKS = 15;
    private static final int BURROW_SCRATCH_TICKS = 8;
    private static final int BURROW_DURATION_TICKS = 28;
    private static final int PLAYER_SENSE_RANGE = 24;
    private static final int SEARCH_ZONE_RADIUS = 48;
    private static final int SEARCH_ZONE_REFRESH_TICKS = 100;
    /** Vanilla Rabbit.RabbitAvoidEntityGoal uses 2.2 for both of these modifiers. */
    private static final double VANILLA_FLEE_SPEED_MODIFIER = 2.2D;
    /** Vanilla rabbits use 0.6 for WaterAvoidingRandomStrollGoal. */
    private static final double VANILLA_STROLL_SPEED_MODIFIER = 0.6D;
    private static final int CLUE_SOUND_RANGE = 32;
    private static final int ORPHAN_RANGE = 96;
    private static final int STUCK_CHECK_TICKS = 40;
    private static final double STUCK_MIN_PROGRESS_SQR = 0.36D;
    private static final Vector3f GOLD = rgb(0xFFD739);
    private static final Vector3f PALE_GOLD = rgb(0xFFF3A1);
    private static final int[] RADIANT_COLORS = {
            0x54E6E0, 0xFFE36A, 0xFF76B3, 0xA87BFF, 0x5FA8FF
    };

    private final Map<UUID, Long> lastCountedHitTicks = new HashMap<>();
    private final Map<UUID, Integer> contributorHits = new HashMap<>();
    @Nullable
    private final ServerBossEvent crownBossBar;
    @Nullable
    private UUID encounterTargetId;
    private boolean caught;
    private boolean naturalEncounter;
    private boolean startled;
    private boolean mirrorDecoysSpawned;
    private int mirrorShuffleProtectionTicks;
    @Nullable
    private UUID mirrorOwnerId;
    private long expiresAtGameTime;
    private long breakingNewsRunId;
    private boolean breakingNewsDormant;
    private int noNearbyPlayerTicks;
    private int hitBurstTicks;
    private boolean hasStuckSample;
    private double stuckSampleX;
    private double stuckSampleZ;
    private int stuckSampleTicks;
    private int stuckRecoveryAttempts;
    private int searchZoneCenterX;
    private int searchZoneCenterZ;
    private boolean searchZoneCenterSet;
    private boolean searchZoneAnnounced;

    public TreasureRabbitEntity(EntityType<? extends Rabbit> type, Level level) {
        super(type, level);
        crownBossBar = level.isClientSide() ? null : new ServerBossEvent(
                Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_20);
        if (crownBossBar != null) {
            crownBossBar.setVisible(false);
        }
        setVariant(Rabbit.Variant.GOLD);
        setPersistenceRequired();
        setCustomNameVisible(false);
        refreshName();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, PLAYER_SENSE_RANGE,
                VANILLA_FLEE_SPEED_MODIFIER, VANILLA_FLEE_SPEED_MODIFIER));
        goalSelector.addGoal(5,
                new WaterAvoidingRandomStrollGoal(this, VANILLA_STROLL_SPEED_MODIFIER));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TREASURE_VARIANT, TreasureRabbitVariant.GOLDEN.id());
        builder.define(COUNTED_HITS, 0);
        builder.define(REQUIRED_HITS, 3);
        builder.define(BURROW_TICKS, 0);
        builder.define(MIRROR_DECOY, false);
        builder.define(CONTRIBUTOR_COUNT, 0);
    }

    public TreasureRabbitVariant treasureVariant() {
        return TreasureRabbitVariant.fromString(entityData.get(TREASURE_VARIANT));
    }

    public void setTreasureVariant(TreasureRabbitVariant variant) {
        entityData.set(TREASURE_VARIANT, variant.id());
        configureRequiredHits(defaultRequiredHits());
        refreshDimensions();
        refreshName();
        updateCrownBossBar();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (TREASURE_VARIANT.equals(accessor)) {
            // The server refreshes dimensions in setTreasureVariant, but the client receives the
            // variant through synced entity data. Refresh here as well so targeting and F3+B use
            // the same Crown Rabbit hitbox as the server.
            refreshDimensions();
            refreshName();
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions dimensions = super.getDefaultDimensions(pose);
        return treasureVariant() == TreasureRabbitVariant.CROWN
                ? dimensions.scale(CROWN_SCALE) : dimensions;
    }

    public boolean isMirrorDecoy() {
        return entityData.get(MIRROR_DECOY);
    }

    void setMirrorDecoy(boolean value) {
        entityData.set(MIRROR_DECOY, value);
        if (value) {
            configureRequiredHits(1);
        }
    }

    void setMirrorOwnerId(@Nullable UUID ownerId) {
        mirrorOwnerId = ownerId;
    }

    @Nullable
    UUID mirrorOwnerId() {
        return mirrorOwnerId;
    }

    void beginMirrorShuffleProtection() {
        mirrorShuffleProtectionTicks = MIRROR_SHUFFLE_PROTECTION_TICKS;
    }

    public int contributorCount() {
        return entityData.get(CONTRIBUTOR_COUNT);
    }

    public Map<UUID, Integer> contributorHits() {
        return Map.copyOf(contributorHits);
    }

    void setExpiresAfterTicks(int ticks) {
        expiresAtGameTime = level().getGameTime() + Math.max(20, ticks);
    }

    public int countedHits() {
        return entityData.get(COUNTED_HITS);
    }

    public int requiredHits() {
        return Math.max(1, entityData.get(REQUIRED_HITS));
    }

    void configureRequiredHits(int required) {
        entityData.set(REQUIRED_HITS, Mth.clamp(required, 1, 2_000));
        updateCrownBossBar();
    }

    private int defaultRequiredHits() {
        return treasureVariant() == TreasureRabbitVariant.CROWN
                ? YoikoCommonConfig.TREASURE_RABBIT_CROWN_HITS_REQUIRED.get()
                : YoikoCommonConfig.TREASURE_RABBIT_HITS_REQUIRED.get();
    }

    public boolean isBurrowing() {
        return entityData.get(BURROW_TICKS) > 0;
    }

    public float burrowProgress(float partialTick) {
        int ticks = entityData.get(BURROW_TICKS);
        return ticks <= 0 ? 0.0F : Mth.clamp((ticks + partialTick) / BURROW_DURATION_TICKS, 0.0F, 1.0F);
    }

    public float burrowScratchProgress(float partialTick) {
        int ticks = entityData.get(BURROW_TICKS);
        return ticks <= 0 ? 0.0F : Mth.clamp((ticks + partialTick) / BURROW_SCRATCH_TICKS, 0.0F, 1.0F);
    }

    public float burrowSinkProgress(float partialTick) {
        int ticks = entityData.get(BURROW_TICKS);
        return ticks <= BURROW_SCRATCH_TICKS ? 0.0F : Mth.clamp(
                (ticks + partialTick - BURROW_SCRATCH_TICKS)
                        / (BURROW_DURATION_TICKS - BURROW_SCRATCH_TICKS), 0.0F, 1.0F);
    }

    public float burrowAnimationTicks(float partialTick) {
        return entityData.get(BURROW_TICKS) + partialTick;
    }

    public boolean isFatigued() {
        return !isBurrowing() && countedHits() >= 2;
    }

    public void setNaturalEncounter(boolean value) {
        if (!value) {
            clearSearchZone();
        } else if (!searchZoneCenterSet) {
            setSearchZoneCenter(blockPosition());
        }
        naturalEncounter = value;
    }

    public boolean isNaturalEncounter() {
        return naturalEncounter;
    }

    /** Natural exploration rabbits are personal incidents; breaking-news swarms are server incidents. */
    @Nullable
    public IncidentCategory incidentCategory() {
        if (breakingNewsRunId > 0L) {
            return IncidentCategory.SERVER;
        }
        return naturalEncounter ? IncidentCategory.PERSONAL : null;
    }

    public void setEncounterTarget(@Nullable UUID targetId) {
        if (!Objects.equals(encounterTargetId, targetId)) {
            clearSearchZone();
        }
        encounterTargetId = targetId;
    }

    @Nullable
    public UUID encounterTargetId() {
        return encounterTargetId;
    }

    public void setBreakingNewsRunId(long runId) {
        if (runId > 0L) {
            clearSearchZone();
        }
        breakingNewsRunId = Math.max(0L, runId);
    }

    public long breakingNewsRunId() {
        return breakingNewsRunId;
    }

    public void setBreakingNewsDormant(boolean dormant) {
        breakingNewsDormant = dormant && breakingNewsRunId > 0L;
        if (breakingNewsDormant) {
            setNoAi(true);
            getNavigation().stop();
            expiresAtGameTime = 0L;
            noNearbyPlayerTicks = 0;
        }
        updateCrownBossBar();
    }

    public void activateBreakingNewsEncounter() {
        if (!breakingNewsDormant) return;
        breakingNewsDormant = false;
        expiresAtGameTime = 0L;
        noNearbyPlayerTicks = 0;
        if (!caught && !isBurrowing()) setNoAi(false);
        updateCrownBossBar();
    }

    public void endBreakingNewsEncounter() {
        if (!caught && breakingNewsRunId > 0L) {
            beginEscape("breaking_news_ended");
        }
    }

    /** Ends a personal incident through the same visible burrow sequence as a natural escape. */
    public boolean endPersonalEncounter(String reason) {
        if (caught || incidentCategory() != IncidentCategory.PERSONAL) {
            return false;
        }
        beginEscape(reason);
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || caught || breakingNewsDormant
                || !(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        if (isMirrorDecoy()) {
            shatterMirrorDecoy(player);
            return true;
        }
        if (mirrorShuffleProtectionTicks > 0) {
            return false;
        }
        long now = level().getGameTime();
        Long lastHit = lastCountedHitTicks.get(player.getUUID());
        if (lastHit != null && now - lastHit < HIT_COOLDOWN_TICKS) {
            return false;
        }
        lastCountedHitTicks.put(player.getUUID(), now);
        if (lastCountedHitTicks.size() > 32) {
            lastCountedHitTicks.entrySet().removeIf(entry -> now - entry.getValue() > 200L);
        }
        boolean firstCountedHit = countedHits() == 0;
        markDiscovered(player);
        if (firstCountedHit && treasureVariant() == TreasureRabbitVariant.MIRROR && !mirrorDecoysSpawned) {
            mirrorDecoysSpawned = true;
            TreasureRabbitManager.spawnMirrorDecoys(this);
        }
        noNearbyPlayerTicks = 0;
        contributorHits.merge(player.getUUID(), 1, Integer::sum);
        entityData.set(CONTRIBUTOR_COUNT, contributorHits.size());
        int hits = countedHits() + 1;
        entityData.set(COUNTED_HITS, hits);
        updateCrownBossBar();
        applyFatigue(hits);
        int required = requiredHits();
        TreasureRabbitStatistics.hit(this, player, hits, required);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.broadcastEntityEvent(this, (byte) 2);
            double hitHeight = treasureVariant() == TreasureRabbitVariant.CROWN
                    ? getY() + getBbHeight() * 0.55D : getY() + 0.45D;
            double hitSpread = treasureVariant() == TreasureRabbitVariant.CROWN ? 0.82D : 0.28D;
            serverLevel.sendParticles(ParticleTypes.CRIT, getX(), hitHeight, getZ(),
                    treasureVariant() == TreasureRabbitVariant.CROWN ? 18 : 9,
                    hitSpread, hitSpread, hitSpread, 0.08D);
            applyHitFeedback(serverLevel, player, hits, required);
        }
        float hitPitch = treasureVariant() == TreasureRabbitVariant.CROWN
                ? Mth.lerp(Mth.clamp((float) hits / required, 0.0F, 1.0F), 0.78F, 1.18F)
                : 1.15F + hits * 0.08F;
        playSound(SoundEvents.RABBIT_HURT, 0.75F, hitPitch);

        if (treasureVariant() != TreasureRabbitVariant.CROWN) {
            player.displayClientMessage(Component.translatable(
                    "yoiko_core.message.treasure_rabbit.hit", hits, required), true);
        }
        if (hits >= required) {
            beginBurrow(player);
        }
        return true;
    }

    private void shatterMirrorDecoy(ServerPlayer player) {
        if (!shatterMirrorDecoy(true)) {
            return;
        }
        player.displayClientMessage(Component.translatable(
                "yoiko_core.message.treasure_rabbit.mirror_decoy"), true);
        YoikoAdvancementManager.recordMirrorDecoy(player);
    }

    /** Breaks a linked copy when its real Mirror Rabbit is caught or escapes. */
    boolean shatterAfterMirrorOwnerEnds() {
        return shatterMirrorDecoy(false);
    }

    private boolean shatterMirrorDecoy(boolean playBreakSound) {
        if (caught || !isMirrorDecoy()) {
            return false;
        }
        caught = true;
        getNavigation().stop();
        setNoAi(true);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLASH, getX(), getY() + 0.45D, getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.45D, getZ(),
                    18, 0.35D, 0.35D, 0.35D, 0.06D);
            if (playBreakSound) {
                serverLevel.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK,
                        SoundSource.NEUTRAL, 0.9F, 1.45F);
            }
        }
        discard();
        return true;
    }

    private void applyHitFeedback(ServerLevel level, ServerPlayer player, int hits, int required) {
        if (hits == 1 && hits < required) {
            hitBurstTicks = 12;
            if (getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(
                        treasureVariant() == TreasureRabbitVariant.RADIANT ? 0.48D : 0.45D);
            }
            level.sendParticles(new DustParticleOptions(PALE_GOLD, 1.05F),
                    getX(), getY() + 0.42D, getZ(), 14, 0.34D, 0.30D, 0.34D, 0.045D);
            level.playSound(null, blockPosition(), SoundEvents.RABBIT_JUMP,
                    SoundSource.NEUTRAL, 0.75F, 1.45F);
        } else if (hits < required
                && (treasureVariant() != TreasureRabbitVariant.CROWN || hits % 3 == 0)) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.55D, getZ(),
                    5, 0.22D, 0.16D, 0.22D, 0.015D);
            level.playSound(null, blockPosition(), SoundEvents.RABBIT_AMBIENT,
                    SoundSource.NEUTRAL, 0.45F, 0.72F);
        }
    }

    private void beginBurrow(ServerPlayer player) {
        caught = true;
        updateCrownBossBar();
        alignToGround();
        TreasureRabbitStatistics.caught(this, player);
        ServerPlayer target = encounterTarget(level() instanceof ServerLevel serverLevel ? serverLevel : null);
        if (treasureVariant() != TreasureRabbitVariant.CROWN
                && target != null && !target.getUUID().equals(player.getUUID())) {
            target.displayClientMessage(Component.translatable(
                    "yoiko_core.message.treasure_rabbit.caught_by_other",
                    player.getDisplayName()), true);
        }
        startBurrowing();
        // Emit currency icons and physical item rewards after the first dirt burst so the loot
        // visibly pops out of the hole as the rabbit starts to dig down.
        TreasureRabbitManager.rewardCaught(this, player);
    }

    private void beginEscape(String reason) {
        caught = true;
        updateCrownBossBar();
        alignToGround();
        BreakingNewsEventManager.recordRabbitEscaped(this);
        ServerPlayer target = encounterTarget(level() instanceof ServerLevel serverLevel ? serverLevel : null);
        TreasureRabbitStatistics.escaped(this, target, reason);
        if (target != null) {
            target.displayClientMessage(Component.translatable(
                    "yoiko_core.message.treasure_rabbit.escaped"), true);
        }
        startBurrowing();
    }

    @Nullable
    private ServerPlayer encounterTarget(@Nullable ServerLevel level) {
        return level == null || encounterTargetId == null
                ? null : level.getServer().getPlayerList().getPlayer(encounterTargetId);
    }

    private void startBurrowing() {
        if (treasureVariant() == TreasureRabbitVariant.MIRROR && !isMirrorDecoy()) {
            TreasureRabbitManager.shatterMirrorDecoys(this);
        }
        entityData.set(BURROW_TICKS, 1);
        getNavigation().stop();
        setNoAi(true);
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(groundParticle(),
                    getX(), getY() + 0.10D, getZ(), 30,
                    0.56D, 0.18D, 0.56D, 0.14D);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    getX(), getY() + 0.12D, getZ(), 7,
                    0.34D, 0.08D, 0.34D, 0.025D);
            serverLevel.playSound(null, blockPosition(), SoundEvents.ROOTED_DIRT_BREAK,
                    SoundSource.NEUTRAL, 1.0F, 0.72F);
            serverLevel.playSound(null, blockPosition(), SoundEvents.GRASS_BREAK,
                    SoundSource.NEUTRAL, 0.65F, 0.92F);
        }
    }

    private void alignToGround() {
        for (int depth = 0; depth <= 6; depth++) {
            BlockPos candidate = BlockPos.containing(getX(), getY() - 0.2D - depth, getZ());
            if (!level().getBlockState(candidate).isCollisionShapeFullBlock(level(), candidate)) {
                continue;
            }
            setPos(getX(), candidate.getY() + 1.0D, getZ());
            return;
        }
    }

    private void applyFatigue(int hits) {
        if (getAttribute(Attributes.MOVEMENT_SPEED) == null) {
            return;
        }
        int required = requiredHits();
        double progress = Mth.clamp((double) hits / required, 0.0D, 1.0D);
        double freshSpeed = switch (treasureVariant()) {
            case RADIANT -> 0.42D;
            case CROWN -> 0.24D;
            case MIRROR -> 0.44D;
            default -> 0.39D;
        };
        double tiredSpeed = treasureVariant() == TreasureRabbitVariant.CROWN ? 0.18D : 0.30D;
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Mth.lerp(progress, freshSpeed, tiredSpeed));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (mirrorShuffleProtectionTicks > 0) {
            mirrorShuffleProtectionTicks--;
        }
        // Reject vanilla boats and modded seats at the API boundary, then clean up a forced or
        // deserialized attachment defensively. Create seats also use Entity#startRiding.
        if (isPassenger()) {
            stopRiding();
        }
        if (isLeashed()) {
            dropLeash(true, false);
        }
        if (tickCount == 1) {
            configureRequiredHits(isMirrorDecoy() ? 1 : defaultRequiredHits());
            applyFatigue(countedHits());
            updateCrownBossBar();
        }
        if (hitBurstTicks > 0 && --hitBurstTicks == 0) {
            applyFatigue(countedHits());
        }
        if (isBurrowing()) {
            tickBurrow(serverLevel);
            return;
        }
        if (breakingNewsRunId > 0L
                && !BreakingNewsEventManager.isRabbitRunActive(
                        serverLevel.getServer(), breakingNewsRunId)) {
            beginEscape("breaking_news_inactive");
            return;
        }
        if (breakingNewsDormant) {
            if (BreakingNewsEventManager.isRabbitRunDormant(serverLevel.getServer(), breakingNewsRunId)) {
                if (tickCount % 40 == 0) emitAmbientParticles(serverLevel);
                return;
            }
            activateBreakingNewsEncounter();
        }
        // Ordinary exploration rabbits use their own immediate lifetime. A breaking-news swarm
        // is governed by the shared incident timer, which begins only after area discovery.
        if (breakingNewsRunId <= 0L && expiresAtGameTime <= 0L) {
            expiresAtGameTime = serverLevel.getGameTime()
                    + (treasureVariant() == TreasureRabbitVariant.CROWN
                    ? Math.max(12_000, YoikoCommonConfig.TREASURE_RABBIT_MAXIMUM_LIFETIME_TICKS.get())
                    : YoikoCommonConfig.TREASURE_RABBIT_MAXIMUM_LIFETIME_TICKS.get());
        }
        if (breakingNewsRunId <= 0L && serverLevel.getGameTime() >= expiresAtGameTime) {
            beginEscape("maximum_lifetime");
            return;
        }
        tickSearchZone(serverLevel);
        Player nearest = level().getNearestPlayer(
                getX(), getY(), getZ(), ORPHAN_RANGE, true);
        if (nearest != null) {
            noNearbyPlayerTicks = 0;
            if (!isMirrorDecoy() && !startled && distanceToSqr(nearest) <= PLAYER_SENSE_RANGE * PLAYER_SENSE_RANGE) {
                if (nearest instanceof ServerPlayer serverPlayer) {
                    markDiscovered(serverPlayer);
                }
                serverLevel.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 0.55D, getZ(),
                        7, 0.22D, 0.26D, 0.22D, 0.025D);
                level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.NEUTRAL, 0.55F, 1.35F);
            }
            emitClueSound(serverLevel, nearest);
            tickStuckRecovery(serverLevel, nearest);
        } else if (breakingNewsRunId > 0L) {
            noNearbyPlayerTicks = 0;
            resetStuckTracking(true);
        } else if (++noNearbyPlayerTicks
                >= YoikoCommonConfig.TREASURE_RABBIT_ESCAPE_NO_PLAYER_TICKS.get()) {
            beginEscape("no_nearby_player");
            return;
        } else {
            resetStuckTracking(true);
        }
        emitMovementTrail(serverLevel);
        emitAmbientParticles(serverLevel);
    }

    private void markDiscovered(ServerPlayer discoverer) {
        if (startled || isMirrorDecoy()) {
            return;
        }
        startled = true;
        TreasureRabbitStatistics.discovered(this, discoverer);
        discoverer.displayClientMessage(Component.translatable(
                "yoiko_core.message.treasure_rabbit.discovered"), true);
        ServerPlayer target = encounterTarget(
                level() instanceof ServerLevel serverLevel ? serverLevel : null);
        if (target != null && !target.getUUID().equals(discoverer.getUUID())) {
            target.displayClientMessage(Component.translatable(
                    "yoiko_core.message.treasure_rabbit.discovered_by_other",
                    discoverer.getDisplayName()), true);
        }
    }

    private void tickSearchZone(ServerLevel level) {
        if (!shouldShowSearchZone()) {
            clearSearchZone();
            return;
        }
        if (!searchZoneCenterSet) {
            setSearchZoneCenter(blockPosition());
        }
        ServerPlayer target = encounterTarget(level);
        if (target == null) {
            return;
        }
        if (target.serverLevel().dimension() != level.dimension()) {
            searchZoneAnnounced = false;
            return;
        }
        if (!searchZoneAnnounced || tickCount % SEARCH_ZONE_REFRESH_TICKS == 0) {
            PacketDistributor.sendToPlayer(target, searchZonePayload(true));
            searchZoneAnnounced = true;
        }
    }

    private boolean shouldShowSearchZone() {
        return naturalEncounter && breakingNewsRunId <= 0L && encounterTargetId != null
                && !isMirrorDecoy();
    }

    private void setSearchZoneCenter(BlockPos center) {
        searchZoneCenterX = center.getX();
        searchZoneCenterZ = center.getZ();
        searchZoneCenterSet = true;
    }

    private void clearSearchZone() {
        clearSearchZone(false);
    }

    private void clearSearchZone(boolean force) {
        if ((!searchZoneAnnounced && !force) || !searchZoneCenterSet || encounterTargetId == null) {
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            ServerPlayer target = encounterTarget(serverLevel);
            if (target != null) {
                PacketDistributor.sendToPlayer(target, searchZonePayload(false));
            }
        }
        searchZoneAnnounced = false;
    }

    private TreasureRabbitSearchZonePayload searchZonePayload(boolean visible) {
        return new TreasureRabbitSearchZonePayload(visible, getUUID(), treasureVariant().id(),
                level().dimension().location().toString(), searchZoneCenterX, searchZoneCenterZ,
                SEARCH_ZONE_RADIUS);
    }

    private void tickStuckRecovery(ServerLevel level, Player nearest) {
        if (distanceToSqr(nearest) > PLAYER_SENSE_RANGE * PLAYER_SENSE_RANGE) {
            resetStuckTracking(true);
            return;
        }
        if (!hasStuckSample) {
            hasStuckSample = true;
            stuckSampleX = getX();
            stuckSampleZ = getZ();
            stuckSampleTicks = 0;
            return;
        }
        if (++stuckSampleTicks < STUCK_CHECK_TICKS) {
            return;
        }

        double dx = getX() - stuckSampleX;
        double dz = getZ() - stuckSampleZ;
        stuckSampleX = getX();
        stuckSampleZ = getZ();
        stuckSampleTicks = 0;
        if (dx * dx + dz * dz >= STUCK_MIN_PROGRESS_SQR) {
            stuckRecoveryAttempts = 0;
            return;
        }

        stuckRecoveryAttempts++;
        int recoveryAttempt = stuckRecoveryAttempts;
        if (recoveryAttempt < 3) {
            // Let the vanilla avoid goal choose a fresh path on its next evaluation. Do not
            // inject velocity or a custom jump; RabbitMoveControl keeps the normal hop rhythm.
            getNavigation().stop();
            TreasureRabbitStatistics.recovered(this,
                    nearest instanceof ServerPlayer player ? player : null,
                    recoveryAttempt, "vanilla_repath", true);
            return;
        }

        Vec3 away = position().subtract(nearest.position()).multiply(1.0D, 0.0D, 1.0D);
        BlockPos landing = TreasureRabbitManager.findSafeRecoveryLanding(
                level, blockPosition(), away, getRandom());
        if (landing != null) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.35D, getZ(),
                    8, 0.22D, 0.20D, 0.22D, 0.03D);
            getNavigation().stop();
            moveTo(landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                    getYRot(), getXRot());
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.35D, getZ(),
                    8, 0.22D, 0.20D, 0.22D, 0.03D);
            resetStuckTracking(true);
            TreasureRabbitStatistics.recovered(this,
                    nearest instanceof ServerPlayer player ? player : null,
                    recoveryAttempt, "safe_relocation", false);
            return;
        }

        // No loaded, safe landing exists. Treat this as a genuine escape failure instead of
        // repeatedly applying non-vanilla leaps that can leave the rabbit oscillating in place.
        beginEscape("stuck_no_safe_landing");
    }

    private void resetStuckTracking(boolean resetAttempts) {
        hasStuckSample = false;
        stuckSampleTicks = 0;
        if (resetAttempts) {
            stuckRecoveryAttempts = 0;
        }
    }

    private void tickBurrow(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        int ticks = entityData.get(BURROW_TICKS) + 1;
        entityData.set(BURROW_TICKS, ticks);
        if (ticks <= BURROW_SCRATCH_TICKS) {
            emitScratchParticles(level);
        } else if ((ticks & 1) == 0) {
            double spread = 0.28D + 0.18D * burrowProgress(0.0F);
            level.sendParticles(groundParticle(),
                    getX(), getY() + 0.06D, getZ(), 5,
                    spread, 0.08D, spread, 0.055D);
        }
        if (ticks == 5 || ticks == 13 || ticks == 21) {
            level.playSound(null, blockPosition(), SoundEvents.GRASS_BREAK,
                    SoundSource.NEUTRAL, 0.55F, 1.02F - ticks * 0.012F);
        }
        if (ticks >= BURROW_DURATION_TICKS) {
            emitBurrowFinish(level);
            discard();
        }
    }

    private void emitScratchParticles(ServerLevel level) {
        double yaw = Math.toRadians(getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double backX = -forwardX;
        double backZ = -forwardZ;
        double originX = getX() - forwardX * 0.28D;
        double originZ = getZ() - forwardZ * 0.28D;
        for (int index = 0; index < 4; index++) {
            double side = (getRandom().nextDouble() - 0.5D) * 0.12D;
            double force = 0.07D + getRandom().nextDouble() * 0.07D;
            level.sendParticles(groundParticle(), originX, getY() + 0.08D, originZ, 0,
                    backX * force + forwardZ * side,
                    0.07D + getRandom().nextDouble() * 0.06D,
                    backZ * force - forwardX * side, 1.0D);
        }
    }

    private void emitBurrowFinish(ServerLevel level) {
        for (int index = 0; index < 16; index++) {
            double angle = Math.PI * 2.0D * index / 16.0D;
            double directionX = Math.cos(angle);
            double directionZ = Math.sin(angle);
            level.sendParticles(groundParticle(),
                    getX() + directionX * 0.18D, getY() + 0.04D, getZ() + directionZ * 0.18D, 0,
                    directionX * 0.10D, 0.065D, directionZ * 0.10D, 1.0D);
        }
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.05D, getZ(),
                8, 0.32D, 0.08D, 0.32D, 0.025D);
    }

    private BlockParticleOption groundParticle() {
        BlockState state = level().getBlockState(blockPosition().below());
        if (state.isAir()) {
            state = Blocks.DIRT.defaultBlockState();
        }
        return new BlockParticleOption(ParticleTypes.BLOCK, state);
    }

    private void emitMovementTrail(ServerLevel level) {
        if (tickCount % 5 != 0) {
            return;
        }
        double movementX = getX() - xo;
        double movementZ = getZ() - zo;
        if (movementX * movementX + movementZ * movementZ < 0.0025D) {
            return;
        }
        double trailX = getX() - movementX * 1.8D;
        double trailZ = getZ() - movementZ * 1.8D;
            if (treasureVariant() == TreasureRabbitVariant.RADIANT) {
                int phase = Math.floorMod(tickCount / 5, RADIANT_COLORS.length);
            level.sendParticles(new DustColorTransitionOptions(
                            rgb(RADIANT_COLORS[phase]), rgb(RADIANT_COLORS[(phase + 1) % RADIANT_COLORS.length]), 0.48F),
                    trailX, getY() + 0.07D, trailZ, 1, 0.06D, 0.02D, 0.06D, 0.0D);
        } else if (treasureVariant() == TreasureRabbitVariant.MIRROR) {
            level.sendParticles(new DustParticleOptions(rgb(0xA8F5FF), 0.55F),
                    trailX, getY() + 0.07D, trailZ, 2, 0.08D, 0.03D, 0.08D, 0.0D);
        } else {
            level.sendParticles(new DustParticleOptions(PALE_GOLD, 0.48F),
                    trailX, getY() + 0.07D, trailZ, 1, 0.06D, 0.02D, 0.06D, 0.0D);
        }
    }

    private void emitClueSound(ServerLevel level, Player nearest) {
        int interval = YoikoCommonConfig.TREASURE_RABBIT_CLUE_SOUND_INTERVAL_TICKS.get();
        if (distanceToSqr(nearest) > CLUE_SOUND_RANGE * CLUE_SOUND_RANGE
                || Math.floorMod(tickCount + getId(), interval) != 0) {
            return;
        }
        boolean crystal = treasureVariant() == TreasureRabbitVariant.RADIANT
                || treasureVariant() == TreasureRabbitVariant.MIRROR;
        level.playSound(null, blockPosition(), crystal
                        ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.NEUTRAL, crystal ? 0.18F : 0.12F,
                treasureVariant() == TreasureRabbitVariant.CROWN ? 0.75F : crystal ? 1.65F : 1.35F);
    }

    private void emitAmbientParticles(ServerLevel level) {
        int interval = switch (treasureVariant()) {
            case RADIANT -> 12;
            case MIRROR -> 10;
            case CROWN -> 18;
            default -> 28;
        };
        if (tickCount % interval != 0) {
            return;
        }
        if (treasureVariant() == TreasureRabbitVariant.RADIANT) {
            int phase = Math.floorMod(tickCount / interval, RADIANT_COLORS.length);
            Vector3f from = rgb(RADIANT_COLORS[phase]);
            Vector3f to = rgb(RADIANT_COLORS[(phase + 1) % RADIANT_COLORS.length]);
            level.sendParticles(new DustColorTransitionOptions(from, to, 0.9F),
                    getX(), getY() + 0.42D, getZ(), 3, 0.30D, 0.31D, 0.30D, 0.01D);
            if (phase == 0) {
                level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.5D, getZ(),
                        2, 0.22D, 0.25D, 0.22D, 0.01D);
            }
        } else if (treasureVariant() == TreasureRabbitVariant.MIRROR) {
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.48D, getZ(),
                    2, 0.34D, 0.34D, 0.34D, 0.006D);
            level.sendParticles(new DustParticleOptions(rgb(0xB7F7FF), 0.72F),
                    getX(), getY() + 0.42D, getZ(), 2, 0.28D, 0.30D, 0.28D, 0.004D);
        } else if (treasureVariant() == TreasureRabbitVariant.CROWN) {
            level.sendParticles(new DustParticleOptions(GOLD, 1.15F),
                    getX(), getY() + getBbHeight() * 0.62D, getZ(),
                    7, 1.05D, 1.0D, 1.05D, 0.012D);
        } else {
            level.sendParticles(new DustParticleOptions(GOLD, 0.85F),
                    getX(), getY() + 0.42D, getZ(), 2, 0.25D, 0.27D, 0.25D, 0.008D);
            if (tickCount % (interval * 3) == 0) {
                level.sendParticles(new DustParticleOptions(PALE_GOLD, 0.65F),
                        getX(), getY() + 0.58D, getZ(), 1, 0.17D, 0.18D, 0.17D, 0.0D);
            }
        }
    }

    private void refreshName() {
        setCustomName(Component.translatable("entity.yoiko_core.treasure_rabbit."
                + treasureVariant().id()));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("TreasureVariant", treasureVariant().id());
        tag.putInt("CountedHits", countedHits());
        tag.putBoolean("NaturalEncounter", naturalEncounter);
        tag.putBoolean("Startled", startled);
        tag.putBoolean("MirrorDecoy", isMirrorDecoy());
        tag.putBoolean("MirrorDecoysSpawned", mirrorDecoysSpawned);
        tag.putInt("MirrorShuffleProtectionTicks", mirrorShuffleProtectionTicks);
        if (mirrorOwnerId != null) {
            tag.putUUID("MirrorOwner", mirrorOwnerId);
        }
        tag.putBoolean("Caught", caught);
        tag.putInt("BurrowTicks", entityData.get(BURROW_TICKS));
        tag.putLong("ExpiresAtGameTime", expiresAtGameTime);
        tag.putLong("BreakingNewsRunId", breakingNewsRunId);
        tag.putBoolean("BreakingNewsDormant", breakingNewsDormant);
        tag.putBoolean("SearchZoneCenterSet", searchZoneCenterSet);
        if (searchZoneCenterSet) {
            tag.putInt("SearchZoneCenterX", searchZoneCenterX);
            tag.putInt("SearchZoneCenterZ", searchZoneCenterZ);
        }
        ListTag contributors = new ListTag();
        contributorHits.forEach((uuid, hits) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", uuid);
            entry.putInt("Hits", Math.max(1, hits));
            contributors.add(entry);
        });
        tag.put("Contributors", contributors);
        if (encounterTargetId != null) {
            tag.putUUID("EncounterTarget", encounterTargetId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTreasureVariant(TreasureRabbitVariant.fromString(tag.getString("TreasureVariant")));
        entityData.set(COUNTED_HITS, Math.max(0, tag.getInt("CountedHits")));
        naturalEncounter = tag.getBoolean("NaturalEncounter");
        startled = tag.getBoolean("Startled");
        setMirrorDecoy(tag.getBoolean("MirrorDecoy"));
        mirrorDecoysSpawned = tag.getBoolean("MirrorDecoysSpawned");
        mirrorShuffleProtectionTicks = Mth.clamp(
                tag.getInt("MirrorShuffleProtectionTicks"), 0, MIRROR_SHUFFLE_PROTECTION_TICKS);
        mirrorOwnerId = tag.hasUUID("MirrorOwner") ? tag.getUUID("MirrorOwner") : null;
        caught = tag.getBoolean("Caught");
        expiresAtGameTime = tag.contains("ExpiresAtGameTime", Tag.TAG_LONG)
                ? Math.max(0L, tag.getLong("ExpiresAtGameTime")) : 0L;
        breakingNewsRunId = tag.contains("BreakingNewsRunId", Tag.TAG_LONG)
                ? Math.max(0L, tag.getLong("BreakingNewsRunId")) : 0L;
        breakingNewsDormant = breakingNewsRunId > 0L && tag.getBoolean("BreakingNewsDormant");
        searchZoneCenterSet = tag.getBoolean("SearchZoneCenterSet");
        if (searchZoneCenterSet) {
            searchZoneCenterX = tag.getInt("SearchZoneCenterX");
            searchZoneCenterZ = tag.getInt("SearchZoneCenterZ");
        }
        encounterTargetId = tag.hasUUID("EncounterTarget") ? tag.getUUID("EncounterTarget") : null;
        contributorHits.clear();
        ListTag contributors = tag.getList("Contributors", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(64, contributors.size()); index++) {
            CompoundTag entry = contributors.getCompound(index);
            if (entry.hasUUID("Player")) {
                contributorHits.put(entry.getUUID("Player"), Math.max(1, entry.getInt("Hits")));
            }
        }
        entityData.set(CONTRIBUTOR_COUNT, contributorHits.size());
        int burrowTicks = Mth.clamp(tag.getInt("BurrowTicks"), 0, BURROW_DURATION_TICKS);
        entityData.set(BURROW_TICKS, burrowTicks);
        if (caught || burrowTicks > 0 || breakingNewsDormant) {
            setNoAi(true);
            setNoGravity(caught || burrowTicks > 0);
        }
        configureRequiredHits(isMirrorDecoy() ? 1 : defaultRequiredHits());
        applyFatigue(countedHits());
        updateCrownBossBar();
    }

    private void updateCrownBossBar() {
        if (crownBossBar == null) {
            return;
        }
        boolean visible = treasureVariant() == TreasureRabbitVariant.CROWN
                && !caught && !isBurrowing() && !breakingNewsDormant;
        crownBossBar.setVisible(visible);
        crownBossBar.setProgress(Mth.clamp(
                1.0F - (float) countedHits() / requiredHits(), 0.0F, 1.0F));
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (crownBossBar != null && treasureVariant() == TreasureRabbitVariant.CROWN) {
            crownBossBar.addPlayer(player);
            updateCrownBossBar();
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (crownBossBar != null) {
            crownBossBar.removePlayer(player);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public Rabbit getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal otherAnimal) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            clearSearchZone(true);
        }
        if (crownBossBar != null) {
            crownBossBar.removeAllPlayers();
        }
        super.remove(reason);
    }

    private static Vector3f rgb(int color) {
        return new Vector3f(((color >> 16) & 255) / 255.0F,
                ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F);
    }
}
