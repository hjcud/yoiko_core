package com.yoiko.core.cosmetic;

import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.network.ParticleCosmeticSyncPayload;
import com.yoiko.core.network.ParticleCosmeticCatalogPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ParticleCosmeticDisplayManager {
    private ParticleCosmeticDisplayManager() {
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payloadFor(player));
    }

    public static void syncTo(ServerPlayer target, ServerPlayer viewer) {
        PacketDistributor.sendToPlayer(viewer, payloadFor(target));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static void syncCatalog(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, catalogPayload());
    }

    public static void syncCatalogToAll(MinecraftServer server) {
        ParticleCosmeticCatalogPayload payload = catalogPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void clear(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new ParticleCosmeticSyncPayload(
                        player.getUUID(), List.of(), CosmeticManager.particleCatalogRevision()
                )
        );
    }

    private static ParticleCosmeticSyncPayload payloadFor(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        List<String> cosmeticIds = new ArrayList<>(CosmeticEquipSlot.values().length);
        for (CosmeticEquipSlot slot : CosmeticEquipSlot.values()) {
            String cosmeticId = data.equippedCosmetics.getOrDefault(slot, "");
            CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
            if (cosmetic != null && cosmetic.type() == CosmeticType.PARTICLE
                    && CosmeticEquipSlot.forCosmetic(cosmetic) == slot) {
                cosmeticIds.add(cosmeticId);
            }
        }
        return new ParticleCosmeticSyncPayload(
                player.getUUID(),
                cosmeticIds,
                CosmeticManager.particleCatalogRevision()
        );
    }

    private static ParticleCosmeticCatalogPayload catalogPayload() {
        return new ParticleCosmeticCatalogPayload(
                CosmeticManager.particleCatalogRevision(),
                CosmeticManager.particleCatalog().stream()
                        .map(ParticleCosmeticCatalogPayload.Entry::new)
                        .toList()
        );
    }
}
