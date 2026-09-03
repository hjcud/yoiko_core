package com.yoiko.core.client.cosmetic;

import com.yoiko.core.config.YoikoClientConfig;
import com.yoiko.core.cosmetic.CosmeticData;
import com.yoiko.core.cosmetic.ParticleCategory;
import com.yoiko.core.cosmetic.ParticleTrailManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class ParticleCosmeticClientEvents {
    private static final int SELECTION_REFRESH_TICKS = 10;
    private static final double SELECTION_MOVE_REFRESH_SQR = 16.0D;
    private ClientLevel activeLevel;
    private final List<RenderablePlayer> selectedPlayers = new ArrayList<>();
    private final List<RenderablePlayer> candidateBuffer = new ArrayList<>();
    private final Set<UUID> allowedPlayers = new LinkedHashSet<>();
    private int lastSelectionTick = Integer.MIN_VALUE;
    private long lastEquipmentRevision = Long.MIN_VALUE;
    private Vec3 lastSelectionOrigin;

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientParticleCosmeticCache.clear();
        ClientParticleCosmeticCatalog.clear();
        ClientParticleRenderBudget.clear();
        ClientParticlePerformanceController.reset();
        ClientCosmeticEquipmentCache.clear();
        ClientCosmeticMenuCatalog.clear();
        ClientCosmeticPreviewState.clear();
        com.yoiko.core.client.ClientMailNotification.clear();
        com.yoiko.core.client.screen.ClientServerRequestState.clear();
        ParticleTrailManager.clear();
        selectedPlayers.clear();
        candidateBuffer.clear();
        allowedPlayers.clear();
        lastSelectionTick = Integer.MIN_VALUE;
        lastEquipmentRevision = Long.MIN_VALUE;
        lastSelectionOrigin = null;
        activeLevel = null;
    }

    @SubscribeEvent
    public void onRenderFrame(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null && minecraft.screen == null) {
            ClientParticlePerformanceController.recordFrame(System.nanoTime());
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ClientParticleRenderBudget.clear();
            ParticleTrailManager.clear();
            selectedPlayers.clear();
            candidateBuffer.clear();
            allowedPlayers.clear();
            lastSelectionOrigin = null;
            return;
        }
        if (activeLevel != minecraft.level) {
            ParticleTrailManager.clear();
            ClientParticleRenderBudget.clear();
            selectedPlayers.clear();
            candidateBuffer.clear();
            allowedPlayers.clear();
            lastSelectionTick = Integer.MIN_VALUE;
            lastEquipmentRevision = Long.MIN_VALUE;
            lastSelectionOrigin = null;
            activeLevel = minecraft.level;
        }

        ClientCosmeticPreviewState.tickTimedPreview(minecraft);
        renderEquippedParticles(minecraft);
        renderPreviewParticle(minecraft);
    }

    private void renderEquippedParticles(Minecraft minecraft) {
        int limit = ClientParticlePerformanceController.effectiveOtherPlayerLimit(
                YoikoClientConfig.MAX_PARTICLE_PLAYERS.get());
        int density = ClientParticlePerformanceController.effectiveDensity(YoikoParticleSettings.density());
        if (density <= 0) {
            ClientParticleRenderBudget.clear();
            ParticleTrailManager.clear();
            selectedPlayers.clear();
            candidateBuffer.clear();
            allowedPlayers.clear();
            lastSelectionOrigin = null;
            return;
        }

        long equipmentRevision = ClientParticleCosmeticCache.revision();
        if (lastEquipmentRevision != Long.MIN_VALUE && lastEquipmentRevision != equipmentRevision) {
            ParticleTrailManager.clear();
        }
        if (lastSelectionTick == Integer.MIN_VALUE
                || lastEquipmentRevision != equipmentRevision
                || lastSelectionOrigin == null
                || minecraft.player.position().distanceToSqr(lastSelectionOrigin) >= SELECTION_MOVE_REFRESH_SQR
                || minecraft.player.tickCount - lastSelectionTick >= SELECTION_REFRESH_TICKS) {
            refreshSelectedPlayers(minecraft, limit);
            lastSelectionTick = minecraft.player.tickCount;
            lastEquipmentRevision = equipmentRevision;
        }

        for (RenderablePlayer candidate : selectedPlayers) {
            if (candidate.player().isRemoved() || candidate.player().level() != minecraft.level) {
                continue;
            }
            for (CosmeticData cosmetic : candidate.cosmetics()) {
                if (candidate.player() == minecraft.player
                        && ClientCosmeticPreviewState.overridesParticleCategory(
                                cosmetic.particleCategory().name())) {
                    continue;
                }
                if (cosmetic.particleCategory() != ParticleCategory.WINGS) {
                    ParticleTrailManager.tick(candidate.player(), cosmetic, density, false);
                }
            }
        }
    }

    private void refreshSelectedPlayers(Minecraft minecraft, int otherPlayerLimit) {
        selectedPlayers.clear();
        allowedPlayers.clear();

        double maxDistance = YoikoClientConfig.PARTICLE_RENDER_DISTANCE.get();
        double maxDistanceSqr = maxDistance * maxDistance;
        RenderablePlayer self = renderable(minecraft.player, -1.0D);
        if (self != null) {
            selectedPlayers.add(self);
            allowedPlayers.add(self.player().getUUID());
        }
        if (!YoikoClientConfig.RENDER_OTHER_PLAYER_PARTICLES.get() || otherPlayerLimit <= 0) {
            commitSelection(minecraft);
            return;
        }

        candidateBuffer.clear();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player == minecraft.player) {
                continue;
            }
            double distanceSqr = minecraft.player.distanceToSqr(player);
            if (distanceSqr > maxDistanceSqr) {
                continue;
            }
            RenderablePlayer candidate = renderable(player, distanceSqr);
            if (candidate == null) {
                continue;
            }
            int insertAt = 0;
            while (insertAt < candidateBuffer.size()
                    && candidateBuffer.get(insertAt).distanceSqr() <= distanceSqr) {
                insertAt++;
            }
            if (insertAt < otherPlayerLimit) {
                candidateBuffer.add(insertAt, candidate);
                if (candidateBuffer.size() > otherPlayerLimit) {
                    candidateBuffer.remove(candidateBuffer.size() - 1);
                }
            }
        }
        selectedPlayers.addAll(candidateBuffer);
        for (RenderablePlayer candidate : candidateBuffer) {
            allowedPlayers.add(candidate.player().getUUID());
        }
        commitSelection(minecraft);
    }

    private void commitSelection(Minecraft minecraft) {
        ClientParticleRenderBudget.replace(allowedPlayers);
        ParticleTrailManager.retainStates(allowedPlayers);
        lastSelectionOrigin = minecraft.player.position();
    }

    private static RenderablePlayer renderable(AbstractClientPlayer player, double distanceSqr) {
        ClientParticleCosmeticCache.EquippedParticles equipped = ClientParticleCosmeticCache.entry(player.getUUID());
        if (equipped == null) {
            return null;
        }
        List<CosmeticData> cosmetics = new ArrayList<>(equipped.cosmeticIds().size());
        for (String cosmeticId : equipped.cosmeticIds()) {
            CosmeticData cosmetic = ClientParticleCosmeticCatalog.get(cosmeticId, equipped.catalogRevision());
            if (cosmetic != null) {
                cosmetics.add(cosmetic);
            }
        }
        return cosmetics.isEmpty() ? null : new RenderablePlayer(player, List.copyOf(cosmetics), distanceSqr);
    }

    private static void renderPreviewParticle(Minecraft minecraft) {
        if (ClientCosmeticPreviewState.particleId().isBlank()
                || "WINGS".equals(ClientCosmeticPreviewState.particleCategory())
                || YoikoParticleSettings.density() <= 0) {
            return;
        }
        CosmeticData cosmetic = ClientParticleCosmeticCatalog.get(
                ClientCosmeticPreviewState.particleId(), ClientParticleCosmeticCatalog.revision());
        if (cosmetic != null) {
            ParticleTrailManager.tick(minecraft.player, cosmetic,
                    ClientParticlePerformanceController.effectiveDensity(YoikoParticleSettings.density()), false);
        }
    }

    private record RenderablePlayer(AbstractClientPlayer player, List<CosmeticData> cosmetics, double distanceSqr) {
    }
}
