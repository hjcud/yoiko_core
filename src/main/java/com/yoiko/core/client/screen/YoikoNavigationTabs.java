package com.yoiko.core.client.screen;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class YoikoNavigationTabs {
    private static final int TAB_X = -27;
    private static final int TAB_Y = 28;
    private static final int TAB_STEP = 32;

    private static final String[] IDS = {"storage", "mailbox", "relic", "market", "turtle"};
    private static final String[] ACTIONS = {"storage_open", "mailbox_open", "relic_open", "market_open", "turtle_open"};
    private static final String[] LABEL_KEYS = {
            "yoiko_core.nav.storage",
            "yoiko_core.nav.mailbox",
            "yoiko_core.nav.relic",
            "yoiko_core.nav.market",
            "yoiko_core.nav.turtle"
    };
    private static final int[] ICONS = {0, 1, 2, 3, -1};

    private YoikoNavigationTabs() {
    }

    static void add(List<YoikoIconButton> buttons, int left, int top, String activeId, Consumer<String> sender, Consumer<YoikoIconButton> widgetAdder) {
        for (int i = 0; i < IDS.length; i++) {
            int index = i;
            YoikoIconButton button = YoikoIconButton.navigation(left + TAB_X, top + TAB_Y + i * TAB_STEP,
                    ICONS[i], IDS[i], Component.translatable(LABEL_KEYS[i]), ignored -> {
                YoikoMousePosition.remember();
                sender.accept(ACTIONS[index]);
            });
            button.active = !IDS[i].equals(activeId);
            buttons.add(button);
            widgetAdder.accept(button);
        }
    }

    static boolean renderTooltip(GuiGraphics graphics, net.minecraft.client.gui.Font font, List<YoikoIconButton> buttons, int mouseX, int mouseY) {
        for (YoikoIconButton button : buttons) {
            if (button.isHovered()) {
                graphics.renderTooltip(font, button.getMessage(), mouseX, mouseY);
                return true;
            }
        }
        return false;
    }
}
