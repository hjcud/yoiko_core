package com.yoiko.core.menu;

import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.network.MenuBadgePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MenuBadgeManager {
    private MenuBadgeManager() {
    }

    public static void sync(ServerPlayer player) {
        sync(player, false);
    }

    public static void syncOnLogin(ServerPlayer player) {
        sync(player, true);
    }

    private static void sync(ServerPlayer player, boolean allowIntro) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        int unread = (int) data.mailbox.stream().filter(mail -> !mail.read).count();
        boolean showIntro = allowIntro && !data.menuHudIntroShown;
        if (showIntro) {
            data.menuHudIntroShown = true;
            ServerYoikoSavedData.get(player.server).markDirty(player);
        }
        PacketDistributor.sendToPlayer(player, new MenuBadgePayload(
                data.mailbox.size(), unread, PlayerYoikoData.MAX_MAILBOX_MAILS,
                data.unseenMarketSales, showIntro));
    }
}
