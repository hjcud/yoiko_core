package com.yoiko.core.client.event;

import com.yoiko.core.YoikoServerCore;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Maintains the optional, player-private Xaero Treasure Rabbit highlighter. */
public final class TreasureRabbitMapClientEvents {
    private boolean unavailable;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            ClientTreasureRabbitSearchZones.clear();
            return;
        }
        if (unavailable || !ModList.get().isLoaded("xaeroworldmap")) {
            return;
        }
        try {
            Class<?> integration = Class.forName("com.yoiko.core.client.event.XaeroTreasureRabbitOverlay");
            integration.getMethod("ensureRegistered").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            unavailable = true;
            YoikoServerCore.LOGGER.warn("Xaero Treasure Rabbit overlay was disabled for this session.", exception);
        }
    }
}
