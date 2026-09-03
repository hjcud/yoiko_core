package com.yoiko.core.client.event;

import com.yoiko.core.YoikoServerCore;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** Keeps the optional Xaero highlighter attached when its per-world session changes. */
public final class BreakingNewsMapClientEvents {
    private boolean unavailable;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            ClientBreakingNewsZone.clear();
            return;
        }
        if (unavailable || !ModList.get().isLoaded("xaeroworldmap")) {
            return;
        }
        try {
            Class<?> integration = Class.forName("com.yoiko.core.client.event.XaeroBreakingNewsOverlay");
            integration.getMethod("ensureRegistered").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            unavailable = true;
            YoikoServerCore.LOGGER.warn("Xaero breaking-news overlay was disabled for this session.", exception);
        }
    }
}
