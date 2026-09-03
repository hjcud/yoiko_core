package com.yoiko.core.relic;

import com.yoiko.core.network.RelicGachaEndPayload;
import com.yoiko.core.network.RelicGachaStartPayload;
import com.yoiko.core.registry.YoikoItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RelicGachaAnimationManager {
    private static final int BASE_BRUSH_TICKS = 22;
    private static final int EXTRA_RARITY_STAGE_TICKS = 16;
    private static final double FORWARD_DISTANCE = 1.6D;
    private static final Map<UUID, PendingRoll> PENDING = new HashMap<>();

    private RelicGachaAnimationManager() {
    }

    public static boolean start(ServerPlayer player, ItemStack ticketStack,
                                RelicAppraisalCategory appraisalCategory) {
        if (PENDING.containsKey(player.getUUID())) {
            return false;
        }

        RelicManager.PendingRoll roll = RelicManager.beginTicketRoll(player, ticketStack, appraisalCategory);
        if (roll == null) {
            return false;
        }
        return startPreparedRoll(player, roll);
    }

    public static boolean startCrown(ServerPlayer player, ItemStack ticketStack) {
        if (PENDING.containsKey(player.getUUID())) {
            return false;
        }
        RelicManager.PendingRoll roll = RelicManager.beginCrownTicketRoll(player, ticketStack);
        return roll != null && startPreparedRoll(player, roll);
    }

    private static boolean startPreparedRoll(ServerPlayer player, RelicManager.PendingRoll roll) {
        RelicManager.CommittedRoll committed = RelicManager.commitTicketRoll(player, roll);
        if (committed == null) {
            return false;
        }
        GroundTarget target = findGroundTarget(player);
        UUID sessionId = UUID.randomUUID();
        PendingRoll pending = new PendingRoll(sessionId, committed, player.serverLevel().dimension());
        PENDING.put(player.getUUID(), pending);
        applyTicketCooldown(player, revealTick(committed.rarity()));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new RelicGachaStartPayload(
                sessionId,
                player.getUUID(),
                target.x(), target.groundY(), target.z(),
                target.forwardX(), target.forwardZ(), target.sideX(), target.sideZ(),
                player.getYRot(),
                committed.rarity()
        ));
        playSound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.70F, 0.82F);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.yoiko_core.relic_gacha.opening").withStyle(ChatFormatting.GRAY));
        return true;
    }

    public static void tick(ServerPlayer player) {
        PendingRoll pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        if (!player.isAlive() || !player.serverLevel().dimension().equals(pending.dimension)) {
            cancel(player);
            return;
        }

        pending.ticks++;
        int revealTick = revealTick(pending.committed.rarity());
        RelicRarity cue = anticipationCue(pending.ticks, revealTick);
        if (cue != null) {
            playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.78F, switch (cue) {
                case RADIANT -> 1.82F;
                case MYSTIC -> 1.62F;
                case LEGENDARY -> 1.42F;
                default -> 1.22F;
            });
        }
        if (!pending.resultPresented && pending.ticks >= revealTick) {
            playRevealSound(player, pending.committed.rarity());
            RelicManager.revealCommittedTicketRoll(player, pending.committed);
            pending.resultPresented = true;
            // The reward is complete at reveal. The client lets the remaining world effect finish
            // on its own, while the server immediately permits the next appraisal.
            PENDING.remove(player.getUUID(), pending);
        }
    }

    public static void cancel(ServerPlayer player) {
        PendingRoll pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new RelicGachaEndPayload(pending.sessionId));
        if (!pending.resultPresented) {
            RelicManager.announceCommittedTicketRoll(player, pending.committed);
            pending.resultPresented = true;
        }
    }

    private static GroundTarget findGroundTarget(ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double x = player.getX() + forwardX * FORWARD_DISTANCE;
        double z = player.getZ() + forwardZ * FORWARD_DISTANCE;
        Vec3 rayStart = new Vec3(x, player.getY() + 2.5D, z);
        Vec3 rayEnd = new Vec3(x, player.getY() - 5.0D, z);
        BlockHitResult hit = player.level().clip(new ClipContext(
                rayStart, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double groundY = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : Math.floor(player.getY());
        return new GroundTarget(x, groundY, z, forwardX, forwardZ, forwardZ, -forwardX);
    }

    private static int revealTick(RelicRarity rarity) {
        return switch (rarity) {
            case EPIC -> BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS;
            case LEGENDARY -> BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 2;
            case MYSTIC -> BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 3;
            case RADIANT -> BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 4;
            default -> BASE_BRUSH_TICKS;
        };
    }

    private static RelicRarity anticipationCue(int tick, int revealTick) {
        if (tick == BASE_BRUSH_TICKS && revealTick > tick) {
            return RelicRarity.EPIC;
        }
        if (tick == BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS && revealTick > tick) {
            return RelicRarity.LEGENDARY;
        }
        if (tick == BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 2 && revealTick > tick) {
            return RelicRarity.MYSTIC;
        }
        if (tick == BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 3 && revealTick > tick) {
            return RelicRarity.RADIANT;
        }
        return null;
    }

    private static void playRevealSound(ServerPlayer player, RelicRarity rarity) {
        switch (rarity) {
            case RADIANT -> playSound(player, SoundEvents.TOTEM_USE, 0.95F, 1.42F);
            case MYSTIC -> playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.92F, 1.16F);
            case LEGENDARY -> playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.88F, 1.04F);
            case EPIC -> playSound(player, SoundEvents.PLAYER_LEVELUP, 0.84F, 1.22F);
            case RARE -> playSound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.84F, 1.42F);
            default -> playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.80F, 1.28F);
        }
    }

    private static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void applyTicketCooldown(ServerPlayer player, int ticks) {
        player.getCooldowns().addCooldown(YoikoItems.RELIC_GACHA_TICKET.get(), ticks);
        player.getCooldowns().addCooldown(YoikoItems.RELIC_GACHA_TICKET_COMBAT.get(), ticks);
        player.getCooldowns().addCooldown(YoikoItems.RELIC_GACHA_TICKET_DEFENSE.get(), ticks);
        player.getCooldowns().addCooldown(YoikoItems.RELIC_GACHA_TICKET_POKEMON.get(), ticks);
        player.getCooldowns().addCooldown(YoikoItems.RELIC_GACHA_TICKET_EXPLORATION.get(), ticks);
        player.getCooldowns().addCooldown(YoikoItems.CROWN_SEALED_RELIC.get(), ticks);
    }

    private record GroundTarget(
            double x, double groundY, double z,
            double forwardX, double forwardZ, double sideX, double sideZ
    ) {
    }

    private static final class PendingRoll {
        private final UUID sessionId;
        private final RelicManager.CommittedRoll committed;
        private final ResourceKey<Level> dimension;
        private int ticks;
        private boolean resultPresented;

        private PendingRoll(UUID sessionId, RelicManager.CommittedRoll committed,
                            ResourceKey<Level> dimension) {
            this.sessionId = sessionId;
            this.committed = committed;
            this.dimension = dimension;
        }
    }
}
