package com.yoiko.core.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import com.yoiko.core.config.YoikoClientConfig;

public class YoikoSpriteButton extends Button {
    public enum Sprite {
        CLOSE(10),
        CONFIRM(11),
        PAGE_LEFT(12),
        PAGE_RIGHT(13);

        private final int column;

        Sprite(int column) {
            this.column = column;
        }
    }

    private static final int SIZE = 18;
    private final Sprite sprite;

    private YoikoSpriteButton(int x, int y, Component message, Sprite sprite, OnPress onPress) {
        super(x, y, SIZE, SIZE, message, onPress, DEFAULT_NARRATION);
        this.sprite = sprite;
    }

    public static YoikoSpriteButton create(int x, int y, Component message, Sprite sprite, OnPress onPress) {
        return new YoikoSpriteButton(x, y, message, sprite, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = this.active && this.isHoveredOrFocused();
        YoikoScreenStyle.renderSpriteButton(graphics, getX(), getY(), SIZE, sprite.column, highlighted, this.active);
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        if (YoikoClientConfig.UI_SOUNDS.get()) {
            soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.35F, 0.16F));
        }
    }
}
