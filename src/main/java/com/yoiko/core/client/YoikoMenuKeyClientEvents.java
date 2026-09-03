package com.yoiko.core.client;

import com.yoiko.core.client.screen.ClientMenuSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class YoikoMenuKeyClientEvents {
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (YoikoClientKeys.OPEN_MENU.consumeClick()) {
            if (minecraft.player == null || minecraft.level == null) {
                continue;
            }
            if (YoikoMenuScreenState.isYoikoMenu(minecraft.screen)) {
                minecraft.screen.onClose();
                continue;
            }
            if (minecraft.screen != null) {
                continue;
            }
            PacketDistributor.sendToServer(ClientMenuSession.action(
                    Screen.hasShiftDown() ? "menu_open_mailbox" : "menu_open"));
        }
        if (YoikoMenuScreenState.isYoikoMenu(minecraft.screen)) {
            while (minecraft.options.keyInventory.consumeClick()) {
                minecraft.screen.onClose();
            }
        }
    }
}
