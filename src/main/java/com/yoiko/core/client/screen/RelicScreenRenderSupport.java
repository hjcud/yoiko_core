package com.yoiko.core.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Small drawing primitives shared by the separated relic screen panels. */
final class RelicScreenRenderSupport {
    private RelicScreenRenderSupport() {
    }

    static void drawCentered(GuiGraphics graphics, Font font, Component text,
                             int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static void drawRightAligned(GuiGraphics graphics, Font font, Component text,
                                 int rightX, int y, int color) {
        graphics.drawString(font, text, rightX - font.width(text), y, color, false);
    }

    static void drawLevelBadge(GuiGraphics graphics, Font font, int slotX, int slotY,
                               int slotSize, int level, int effectiveLevel) {
        int displayedLevel = effectiveLevel != level ? effectiveLevel : level;
        if (displayedLevel <= 0) {
            return;
        }
        String text = "+" + Math.min(10, displayedLevel);
        float scale = slotSize <= 18 ? 0.55F : 1.0F;
        int scaledWidth = Math.max(1, Math.round(font.width(text) * scale));
        int scaledHeight = Math.max(1, Math.round(8.0F * scale));
        int x = slotX + slotSize - scaledWidth - 1;
        int y = slotY + 1;
        graphics.fill(x - 1, y - 1, x + scaledWidth + 1, y + scaledHeight + 1, 0xB0101820);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 200.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, Component.literal(text), 0, 0,
                effectiveLevel != level ? 0xFFFF7FE7 : 0xFF50F3FF, false);
        graphics.pose().popPose();
    }
}
