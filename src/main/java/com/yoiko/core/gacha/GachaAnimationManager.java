package com.yoiko.core.gacha;

import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.network.GachaSelectionChoosePayload;
import com.yoiko.core.network.GachaSelectionEndPayload;
import com.yoiko.core.network.GachaSelectionRevealPayload;
import com.yoiko.core.network.GachaSelectionStartPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GachaAnimationManager {
    private static final int BALL_COUNT = 3;
    private static final int LANDING_SETTLE_TICKS = 10;
    private static final int CLEANUP_GRACE_TICKS = 20;
    private static final int MINIMUM_REWARD_GRANT_DELAY_TICKS = 24;
    private static final Map<UUID, PendingSelection> PENDING = new HashMap<>();

    private GachaAnimationManager() {
    }

    public static boolean start(ServerPlayer player, GachaType type, ItemStack ticketStack) {
        if (ticketStack.isEmpty()) {
            return false;
        }
        if (PENDING.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.yoiko_core.gacha_already_opening")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }
        String configurationError = GachaManager.configurationError(type);
        if (!configurationError.isBlank()) {
            com.yoiko.core.YoikoServerCore.LOGGER.error(
                    "Rejected {} gacha for {} because the pool configuration is invalid: {}",
                    type, player.getGameProfile().getName(), configurationError);
            player.sendSystemMessage(Component.translatable("yoiko_core.message.gacha.pool_invalid")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        SelectionSettings settings = SelectionSettings.snapshot();
        List<GachaResult> candidates = rollCandidates(player, type);
        List<Vec3> positions = findBallPositions(player, settings);
        if (positions.size() != BALL_COUNT) {
            player.sendSystemMessage(Component.translatable("message.yoiko_core.gacha_no_space")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        UUID sessionId = UUID.randomUUID();
        boolean consumedTicket = !player.getAbilities().instabuild;
        ItemStack refundStack = ticketStack.copyWithCount(1);
        if (consumedTicket) {
            ticketStack.shrink(1);
        }

        PendingSelection pending = new PendingSelection(
                sessionId,
                candidates,
                positions,
                player.serverLevel().dimension(),
                player.position(),
                refundStack,
                consumedTicket,
                settings,
                collectViewers(player, settings)
        );
        PENDING.put(player.getUUID(), pending);
        sendToViewers(player, pending, createStartPayload(player, pending));
        player.sendSystemMessage(Component.translatable("message.yoiko_core.gacha_choose_ball")
                .withStyle(type.getColor()));
        return true;
    }

    public static void tick(ServerPlayer player) {
        PendingSelection pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }

        if (!player.isAlive()
                || !player.serverLevel().dimension().equals(pending.dimension)
                || player.position().distanceToSqr(pending.origin) > square(pending.settings.moveCancelDistance)) {
            cancel(player);
            return;
        }

        pending.ticks++;
        if (!pending.selected && pending.ticks >= pending.settings.timeoutTicks) {
            cancel(player);
            return;
        }

        if (pending.selected) {
            pending.ticksSinceSelection++;
            if (!pending.rewardGranted
                    && pending.ticksSinceSelection >= Math.max(
                            pending.settings.rewardGrantDelayTicks,
                            MINIMUM_REWARD_GRANT_DELAY_TICKS
                    )) {
                completeSelectedReward(player, pending);
            }
            if (pending.ticksSinceSelection >= pending.settings.revealTicks + CLEANUP_GRACE_TICKS) {
                finishCleanup(player, pending);
            }
        }
    }

    public static void choose(ServerPlayer player, GachaSelectionChoosePayload payload) {
        PendingSelection pending = PENDING.get(player.getUUID());
        if (pending == null || pending.selected || !pending.sessionId.equals(payload.sessionId())) {
            return;
        }
        int selectedIndex = payload.selectedIndex();
        if (selectedIndex < 0 || selectedIndex >= BALL_COUNT
                || !player.serverLevel().dimension().equals(pending.dimension)
                || pending.ticks < readyTick(pending)
                || pending.ticks >= pending.settings.timeoutTicks) {
            player.sendSystemMessage(Component.translatable("message.yoiko_core.gacha_invalid_selection")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        Vec3 ballPosition = pending.positions.get(selectedIndex);
        if (player.getEyePosition().distanceToSqr(ballPosition) > square(pending.settings.selectionDistance)) {
            player.sendSystemMessage(Component.translatable("message.yoiko_core.gacha_invalid_selection")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        pending.selected = true;
        pending.selectedIndex = selectedIndex;
        GachaResult result = pending.candidates.get(selectedIndex);
        sendToViewers(player, pending, new GachaSelectionRevealPayload(
                pending.sessionId,
                selectedIndex,
                result.species(),
                result.rarity().name(),
                result.shiny(),
                result.level(),
                pending.settings.revealTicks
        ));
    }

    public static void cancel(ServerPlayer player) {
        PendingSelection pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        sendToViewers(player, pending, new GachaSelectionEndPayload(pending.sessionId));
        if (pending.selected) {
            completeSelectedReward(player, pending);
        } else if (pending.consumedTicket) {
            returnTicket(player, pending.refundStack.copy());
            player.sendSystemMessage(Component.translatable("message.yoiko_core.gacha_ticket_returned")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void finishCleanup(ServerPlayer player, PendingSelection pending) {
        completeSelectedReward(player, pending);
        if (!PENDING.remove(player.getUUID(), pending)) {
            return;
        }
        sendToViewers(player, pending, new GachaSelectionEndPayload(pending.sessionId));
    }

    private static void completeSelectedReward(ServerPlayer player, PendingSelection pending) {
        if (!pending.selected || pending.rewardGranted
                || pending.selectedIndex < 0 || pending.selectedIndex >= pending.candidates.size()) {
            return;
        }
        // Mark first so repeated cleanup/logout/death paths cannot grant the fixed result twice.
        pending.rewardGranted = true;
        GachaManager.completeTicketRoll(player, pending.candidates.get(pending.selectedIndex));
    }

    private static List<GachaResult> rollCandidates(ServerPlayer player, GachaType type) {
        List<GachaResult> candidates = new ArrayList<>(BALL_COUNT);
        Set<String> species = new HashSet<>();
        for (int i = 0; i < BALL_COUNT; i++) {
            GachaResult result = GachaManager.roll(player, type);
            for (int retry = 0; retry < 8 && species.contains(result.species()); retry++) {
                result = GachaManager.roll(player, type);
            }
            candidates.add(result);
            species.add(result.species());
        }
        return candidates;
    }

    private static List<Vec3> findBallPositions(ServerPlayer player, SelectionSettings settings) {
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() < 1.0E-4D) {
            forward = Vec3.directionFromRotation(0.0F, player.getYRot()).multiply(1.0D, 0.0D, 1.0D);
        }
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double spacing = settings.ballSpacing;

        double initialDistance = unobstructedForwardDistance(player, forward, settings.forwardDistance);
        for (double forwardDistance = initialDistance; forwardDistance >= 1.5D; forwardDistance -= 0.35D) {
            List<Vec3> positions = new ArrayList<>(BALL_COUNT);
            double minY = Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            boolean valid = true;
            for (int i = 0; i < BALL_COUNT; i++) {
                double ballForwardDistance = forwardDistance
                        - (i == 1 ? 0.0D : settings.sideApproachOffset);
                Vec3 horizontal = player.position()
                        .add(forward.scale(ballForwardDistance))
                        .add(right.scale((i - 1) * spacing));
                Vec3 ground = findGround(player, horizontal);
                if (ground == null || !isSafeBallPosition(player, ground)) {
                    valid = false;
                    break;
                }
                positions.add(ground);
                minY = Math.min(minY, ground.y);
                maxY = Math.max(maxY, ground.y);
            }
            if (valid && maxY - minY <= 1.25D) {
                return positions;
            }
        }
        return List.of();
    }

    private static double unobstructedForwardDistance(ServerPlayer player, Vec3 forward, double configured) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(
                eye,
                eye.add(forward.scale(configured)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return configured;
        }
        return Math.max(1.5D, Math.min(configured, eye.distanceTo(hit.getLocation()) - 0.75D));
    }

    private static Vec3 findGround(ServerPlayer player, Vec3 horizontal) {
        ServerLevel level = player.serverLevel();
        double topY = Math.min(level.getMaxBuildHeight() - 1.0D, player.getY() + 2.5D);
        double bottomY = Math.max(level.getMinBuildHeight() + 1.0D, player.getY() - 6.0D);
        Vec3 top = new Vec3(horizontal.x, topY, horizontal.z);
        Vec3 bottom = new Vec3(horizontal.x, bottomY, horizontal.z);
        BlockHitResult hit = level.clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return new Vec3(horizontal.x, hit.getLocation().y + 0.28D, horizontal.z);
    }

    private static boolean isSafeBallPosition(ServerPlayer player, Vec3 position) {
        AABB bounds = AABB.ofSize(position.add(0.0D, 0.18D, 0.0D), 0.62D, 0.72D, 0.62D);
        if (!player.serverLevel().noCollision(bounds)) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 target = position.add(0.0D, 0.2D, 0.0D);
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(
                eye,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        return hit.getType() != HitResult.Type.BLOCK
                || eye.distanceToSqr(hit.getLocation()) + 1.0E-4D >= eye.distanceToSqr(target);
    }

    private static GachaSelectionStartPayload createStartPayload(ServerPlayer player, PendingSelection pending) {
        List<GachaSelectionStartPayload.BallEntry> balls = new ArrayList<>(BALL_COUNT);
        GachaRarity appearanceRarity = GachaRarity.COMMON;
        for (GachaResult candidate : pending.candidates) {
            if (candidate.rarity().ordinal() > appearanceRarity.ordinal()) {
                appearanceRarity = candidate.rarity();
            }
        }
        for (int i = 0; i < BALL_COUNT; i++) {
            Vec3 position = pending.positions.get(i);
            GachaResult result = pending.candidates.get(i);
            Vec3 toOwner = pending.origin.subtract(position);
            // Cobblemon's PokeBallRenderer treats the model's transformed +Z axis as its front.
            float facingYaw = (float) (Mth.atan2(toOwner.x, toOwner.z) * Mth.RAD_TO_DEG);
            balls.add(new GachaSelectionStartPayload.BallEntry(
                    position.x,
                    position.y,
                    position.z,
                    appearanceRarity,
                    result.shiny(),
                    facingYaw
            ));
        }
        return new GachaSelectionStartPayload(
                pending.sessionId,
                player.getUUID(),
                balls,
                pending.settings.fallTicks,
                pending.settings.staggerTicks,
                pending.settings.timeoutTicks,
                pending.settings.revealTicks,
                pending.settings.fallHeight,
                pending.settings.selectionDistance,
                pending.settings.particleDensity
        );
    }

    private static void sendToViewers(ServerPlayer owner, PendingSelection pending,
                                      net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        var server = owner.getServer();
        if (server == null) {
            return;
        }
        for (UUID viewerId : pending.viewerIds) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer != null) {
                PacketDistributor.sendToPlayer(viewer, payload);
            }
        }
    }

    private static Set<UUID> collectViewers(ServerPlayer owner, SelectionSettings settings) {
        Set<UUID> viewers = new HashSet<>();
        viewers.add(owner.getUUID());
        if (!settings.visibleToOthers) {
            return Set.copyOf(viewers);
        }
        double radiusSqr = square(settings.publicRadius);
        for (ServerPlayer viewer : owner.serverLevel().players()) {
            if (viewer.position().distanceToSqr(owner.position()) <= radiusSqr) {
                viewers.add(viewer.getUUID());
            }
        }
        return Set.copyOf(viewers);
    }

    private static void returnTicket(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static int readyTick(PendingSelection pending) {
        return pending.settings.fallTicks
                + pending.settings.staggerTicks * (BALL_COUNT - 1)
                + LANDING_SETTLE_TICKS;
    }

    private static double square(double value) {
        return value * value;
    }

    private static final class PendingSelection {
        private final UUID sessionId;
        private final List<GachaResult> candidates;
        private final List<Vec3> positions;
        private final ResourceKey<Level> dimension;
        private final Vec3 origin;
        private final ItemStack refundStack;
        private final boolean consumedTicket;
        private final SelectionSettings settings;
        private final Set<UUID> viewerIds;
        private int ticks;
        private boolean selected;
        private int selectedIndex = -1;
        private int ticksSinceSelection;
        private boolean rewardGranted;

        private PendingSelection(UUID sessionId, List<GachaResult> candidates, List<Vec3> positions,
                                 ResourceKey<Level> dimension, Vec3 origin, ItemStack refundStack,
                                 boolean consumedTicket, SelectionSettings settings, Set<UUID> viewerIds) {
            this.sessionId = sessionId;
            this.candidates = List.copyOf(candidates);
            this.positions = List.copyOf(positions);
            this.dimension = dimension;
            this.origin = origin;
            this.refundStack = refundStack;
            this.consumedTicket = consumedTicket;
            this.settings = settings;
            this.viewerIds = viewerIds;
        }
    }

    private record SelectionSettings(
            double forwardDistance,
            double ballSpacing,
            double sideApproachOffset,
            double fallHeight,
            double selectionDistance,
            double moveCancelDistance,
            double publicRadius,
            int fallTicks,
            int staggerTicks,
            int timeoutTicks,
            int revealTicks,
            int rewardGrantDelayTicks,
            int particleDensity,
            boolean visibleToOthers
    ) {
        private static SelectionSettings snapshot() {
            return new SelectionSettings(
                    YoikoCommonConfig.GACHA_SELECTION_FORWARD_DISTANCE.get(),
                    YoikoCommonConfig.GACHA_SELECTION_BALL_SPACING.get(),
                    YoikoCommonConfig.GACHA_SELECTION_SIDE_APPROACH_OFFSET.get(),
                    YoikoCommonConfig.GACHA_SELECTION_FALL_HEIGHT.get(),
                    YoikoCommonConfig.GACHA_SELECTION_DISTANCE.get(),
                    YoikoCommonConfig.GACHA_SELECTION_MOVE_CANCEL_DISTANCE.get(),
                    YoikoCommonConfig.GACHA_SELECTION_PUBLIC_RADIUS.get(),
                    YoikoCommonConfig.GACHA_SELECTION_FALL_TICKS.get(),
                    YoikoCommonConfig.GACHA_SELECTION_STAGGER_TICKS.get(),
                    YoikoCommonConfig.GACHA_SELECTION_TIMEOUT_TICKS.get(),
                    YoikoCommonConfig.GACHA_SELECTION_REVEAL_TICKS.get(),
                    YoikoCommonConfig.GACHA_SELECTION_REWARD_GRANT_DELAY_TICKS.get(),
                    YoikoCommonConfig.GACHA_SELECTION_PARTICLE_DENSITY.get(),
                    YoikoCommonConfig.GACHA_SELECTION_VISIBLE_TO_OTHERS.get()
            );
        }
    }
}
