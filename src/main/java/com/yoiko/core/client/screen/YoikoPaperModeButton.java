package com.yoiko.core.client.screen;

import com.yoiko.core.config.YoikoClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/** Hit target and subtle state marker for a bookmark painted into the panel texture. */
final class YoikoPaperModeButton extends Button {
    private final boolean selected;

    YoikoPaperModeButton(int x, int y, int width, int height, Component message,
                         boolean selected, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int markerX = getX() + 4;
        int markerY = getY() + getHeight() / 2 - 1;
        if (selected) {
            graphics.fill(markerX, markerY, markerX + 3, markerY + 3, 0xA85D6B3C);
        } else if (active && isHoveredOrFocused()) {
            graphics.fill(markerX, markerY, getX() + getWidth() - 3, markerY + 2, 0x80FFF4C8);
        }
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        if (YoikoClientConfig.UI_SOUNDS.get()) super.playDownSound(soundManager);
    }
}
