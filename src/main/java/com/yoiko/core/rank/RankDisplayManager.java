package com.yoiko.core.rank;

import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.network.RankNameplatePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class RankDisplayManager {
    private RankDisplayManager() {
    }

    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        Component prefix = RankManager.prefixFor(player);
        if (!prefix.getString().isBlank()) {
            event.setCanceled(true);
            Component message = prefix.copy()
                    .append(player.getDisplayName())
                    .append(Component.literal(": "))
                    .append(event.getMessage());
            player.server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Component prefix = RankManager.tabPrefixFor(player);
        if (prefix.getString().isBlank()) {
            event.setDisplayName(null);
            return;
        }
        event.setDisplayName(prefix.copy().append(Component.literal(player.getGameProfile().getName())));
    }

    public static void refresh(ServerPlayer player) {
        player.refreshTabListName();
        sync(player);
    }

    public static void refreshAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payloadFor(player));
    }

    public static void syncTo(ServerPlayer target, ServerPlayer viewer) {
        PacketDistributor.sendToPlayer(viewer, payloadFor(target));
    }

    public static void clear(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, blankPayload(player));
    }

    private static RankNameplatePayload payloadFor(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        RankData rank = RankManager.get(data.activeRank);
        if (rank == null || rank.iconTexture().isBlank() || rank.nameplateIcon().isBlank()) {
            return blankPayload(player);
        }
        Integer color = rank.color().getColor();
        return new RankNameplatePayload(player.getUUID(), rank.id(), rank.iconTexture(), color == null ? 0xFFFFFF : color);
    }

    private static RankNameplatePayload blankPayload(ServerPlayer player) {
        return new RankNameplatePayload(player.getUUID(), "", "", 0xFFFFFF);
    }
}
