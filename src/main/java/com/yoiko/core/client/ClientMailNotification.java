package com.yoiko.core.client;

import com.yoiko.core.config.YoikoClientConfig;
import com.yoiko.core.network.MenuBadgePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class ClientMailNotification {
    private static final SystemToast.SystemToastId MAIL_TOAST = new SystemToast.SystemToastId(4_500L);
    private static boolean initialized;
    private static int unreadMailCount;

    private ClientMailNotification() {
    }

    public static void update(MenuBadgePayload payload) {
        int nextUnread = Math.max(0, payload.unreadMailCount());
        if (!initialized) {
            initialized = true;
            unreadMailCount = nextUnread;
            return;
        }
        int added = nextUnread - unreadMailCount;
        unreadMailCount = nextUnread;
        if (added <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.addOrUpdate(
                minecraft.getToasts(), MAIL_TOAST,
                Component.translatable("yoiko_core.toast.mail.title"),
                Component.translatable("yoiko_core.toast.mail.message", added, nextUnread));
        if (YoikoClientConfig.UI_SOUNDS.get()) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F, 0.8F));
        }
    }

    public static void clear() {
        initialized = false;
        unreadMailCount = 0;
    }
}
