package com.yoiko.core.cosmetic;

import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.network.CosmeticEquipmentSyncPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CosmeticEquipmentDisplayManager {
    private CosmeticEquipmentDisplayManager() {
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

    public static void clear(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new CosmeticEquipmentSyncPayload(
                        player.getUUID(),
                        CosmeticEquipmentSyncPayload.ModelEntry.EMPTY,
                        CosmeticEquipmentSyncPayload.ModelEntry.EMPTY
                )
        );
    }

    private static CosmeticEquipmentSyncPayload payloadFor(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        return new CosmeticEquipmentSyncPayload(
                player.getUUID(),
                entry(data, CosmeticEquipSlot.HEAD, CosmeticType.HEAD),
                entry(data, CosmeticEquipSlot.CHEST, CosmeticType.CHEST)
        );
    }

    private static CosmeticEquipmentSyncPayload.ModelEntry entry(
            PlayerYoikoData data, CosmeticEquipSlot slot, CosmeticType type) {
        String cosmeticId = data.equippedCosmetics.getOrDefault(slot, "");
        CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
        return cosmetic != null && cosmetic.type() == type
                ? CosmeticEquipmentSyncPayload.ModelEntry.from(cosmetic)
                : CosmeticEquipmentSyncPayload.ModelEntry.EMPTY;
    }
}
