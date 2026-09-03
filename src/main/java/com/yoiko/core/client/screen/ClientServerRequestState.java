package com.yoiko.core.client.screen;

import com.yoiko.core.network.MenuActionFeedback;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClientServerRequestState {
    private static final long TIMEOUT_MILLIS = 5_000L;
    private static final long TIMEOUT_NOTICE_MILLIS = 4_000L;
    private static final Map<String, Pending> PENDING = new HashMap<>();
    private static final Map<String, Long> TIMEOUT_NOTICES = new HashMap<>();

    private ClientServerRequestState() {
    }

    public static boolean begin(Screen screen, String action) {
        if (!MenuActionFeedback.isTracked(action)) {
            return true;
        }
        String scope = MenuActionFeedback.scope(action);
        updateTimeout(scope);
        if (PENDING.containsKey(scope)) {
            return false;
        }
        AbstractWidget control = triggeredControl(screen);
        if (control != null) {
            control.active = false;
        }
        PENDING.put(scope, new Pending(MenuActionFeedback.root(action), System.currentTimeMillis(), control));
        TIMEOUT_NOTICES.remove(scope);
        return true;
    }

    public static void complete(String scope) {
        Pending pending = PENDING.remove(scope);
        if (pending != null && pending.control() != null) {
            pending.control().active = true;
        }
        TIMEOUT_NOTICES.remove(scope);
    }

    public static void render(GuiGraphics graphics, Font font, String scope, int centerX, int y) {
        updateTimeout(scope);
        Pending pending = PENDING.get(scope);
        if (pending != null) {
            int dots = (int) ((System.currentTimeMillis() / 350L) % 4L);
            Component text = Component.translatable("yoiko_core.ui.request.processing")
                    .copy().append(".".repeat(dots));
            drawStatus(graphics, font, text, centerX, y, 0xE03B2B20, 0xFFFFD36A);
            return;
        }
        Long timedOutAt = TIMEOUT_NOTICES.get(scope);
        if (timedOutAt != null) {
            if (System.currentTimeMillis() - timedOutAt <= TIMEOUT_NOTICE_MILLIS) {
                drawStatus(graphics, font, Component.translatable("yoiko_core.ui.request.timed_out"),
                        centerX, y, 0xE04A2020, 0xFFFF8A80);
            } else {
                TIMEOUT_NOTICES.remove(scope);
            }
        }
    }

    public static void clear() {
        for (Pending pending : PENDING.values()) {
            if (pending.control() != null) {
                pending.control().active = true;
            }
        }
        PENDING.clear();
        TIMEOUT_NOTICES.clear();
    }

    private static void updateTimeout(String scope) {
        Pending pending = PENDING.get(scope);
        if (pending == null || System.currentTimeMillis() - pending.startedAt() < TIMEOUT_MILLIS) {
            return;
        }
        PENDING.remove(scope);
        if (pending.control() != null) {
            pending.control().active = true;
        }
        TIMEOUT_NOTICES.put(scope, System.currentTimeMillis());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("yoiko_core.message.request.timed_out"), false);
        }
    }

    private static AbstractWidget triggeredControl(Screen screen) {
        if (screen == null) {
            return null;
        }
        for (var listener : screen.children()) {
            if (listener instanceof AbstractWidget widget && widget.active && widget.isHoveredOrFocused()) {
                return widget;
            }
        }
        return null;
    }

    private static void drawStatus(GuiGraphics graphics, Font font, Component text,
                                   int centerX, int y, int background, int color) {
        int width = font.width(text) + 14;
        graphics.fill(centerX - width / 2, y - 3, centerX + width / 2, y + 11, background);
        graphics.drawCenteredString(font, text, centerX, y, color);
    }

    private record Pending(String action, long startedAt, AbstractWidget control) {
    }
}
