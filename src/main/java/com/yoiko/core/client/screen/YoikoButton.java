package com.yoiko.core.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import com.yoiko.core.config.YoikoClientConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class YoikoButton extends Button {
    public enum Kind {
        NORMAL(-1), CANCEL(10), CONFIRM(11), PREVIOUS(12), NEXT(13);

        private final int spriteColumn;

        Kind(int spriteColumn) {
            this.spriteColumn = spriteColumn;
        }
    }

    private boolean textShadow = true;
    private int accentColor;
    private ResourceLocation icon;
    private boolean iconOnly;
    private boolean eyeIcon;
    private boolean parchmentStyle;
    private boolean cosmeticStyle;
    private boolean chromeLess;
    private Kind kind = Kind.NORMAL;

    private YoikoButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static YoikoButton create(int x, int y, int width, int height, Component message, OnPress onPress) {
        return new YoikoButton(x, y, width, height, message, onPress);
    }

    public YoikoButton withoutTextShadow() {
        this.textShadow = false;
        return this;
    }

    public YoikoButton withAccent(int color) {
        this.accentColor = color & 0x00FFFFFF;
        return this;
    }

    public YoikoButton withKind(Kind value) {
        this.kind = value == null ? Kind.NORMAL : value;
        return this;
    }

    public YoikoButton withParchmentStyle() {
        this.parchmentStyle = true;
        return this;
    }

    public YoikoButton withCosmeticStyle() {
        this.cosmeticStyle = true;
        return this;
    }

    public YoikoButton withoutChrome() {
        this.chromeLess = true;
        return this;
    }

    /** Adds a compact 16 px icon without changing the shared YOIKO button chrome. */
    public YoikoButton withIcon(ResourceLocation value, boolean only) {
        this.icon = value;
        this.iconOnly = only;
        return this;
    }

    /** Uses a code-drawn eye glyph so the preview control does not need a separate texture asset. */
    public YoikoButton withEyeIcon() {
        this.eyeIcon = true;
        this.iconOnly = true;
        return this;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        if (YoikoClientConfig.UI_SOUNDS.get()) {
            super.playDownSound(soundManager);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = this.active && this.isHoveredOrFocused();
        int spriteColumn = kind.spriteColumn;
        if (spriteColumn >= 0) {
            int size = Math.min(18, Math.min(getWidth(), getHeight()));
            int x = getX() + (getWidth() - size) / 2;
            int y = getY() + (getHeight() - size) / 2;
            YoikoScreenStyle.renderSpriteButton(graphics, x, y, size, spriteColumn, highlighted, this.active);
            return;
        }
        int text = cosmeticStyle
                ? this.active ? (highlighted ? 0xFF44523A : 0xFF5C5A3B) : 0xFF9A8F75
                : parchmentStyle
                        ? this.active ? (highlighted ? 0xFF3E6F68 : 0xFF5E4B35) : 0xFF9A8973
                        : this.active ? (highlighted ? 0xFFFFF1B8 : 0xFFEAD7B0) : 0xFF928B80;
        if (chromeLess) {
            if (highlighted) {
                graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1,
                        0x285F7C40);
                graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xAA93A05D);
            }
        } else if (cosmeticStyle) {
            YoikoScreenStyle.renderCosmeticButton(graphics, getX(), getY(), getWidth(), getHeight(), highlighted, this.active);
        } else if (parchmentStyle) {
            YoikoScreenStyle.renderTurtleButton(graphics, getX(), getY(), getWidth(), getHeight(), highlighted, this.active);
        } else {
            YoikoScreenStyle.renderButton(graphics, getX(), getY(), getWidth(), getHeight(), highlighted, this.active);
        }
        if (this.active && accentColor != 0) {
            graphics.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2,
                    0x28000000 | accentColor);
            graphics.fill(getX() + 2, getY() + 3, getX() + 4, getY() + getHeight() - 3,
                    0xFF000000 | accentColor);
        }
        if (eyeIcon) {
            renderEyeIcon(graphics);
            return;
        }
        if (icon != null) {
            int size = Math.min(16, Math.min(getWidth() - 4, getHeight() - 4));
            int iconX = iconOnly ? getX() + (getWidth() - size) / 2 : getX() + 4;
            int iconY = getY() + (getHeight() - size) / 2;
            graphics.blit(icon, iconX, iconY, size, size, 0.0F, 0.0F, 16, 16, 16, 16);
            if (iconOnly) return;

            var font = Minecraft.getInstance().font;
            int textLeft = iconX + size + 3;
            int textWidth = Math.max(0, getX() + getWidth() - 4 - textLeft);
            String label = font.plainSubstrByWidth(getMessage().getString(), textWidth);
            int textX = textLeft + Math.max(0, (textWidth - font.width(label)) / 2);
            int textY = getY() + (getHeight() - 8) / 2;
            graphics.drawString(font, label, textX, textY, text, textShadow);
            return;
        }
        if (textShadow) {
            renderString(graphics, Minecraft.getInstance().font, text);
            return;
        }
        var font = Minecraft.getInstance().font;
        int textX = getX() + (getWidth() - font.width(getMessage())) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        graphics.drawString(font, getMessage(), textX, textY, text, false);
    }

    private void renderEyeIcon(GuiGraphics graphics) {
        int x = getX() + (getWidth() - 16) / 2;
        int y = getY() + (getHeight() - 16) / 2;
        int outline = cosmeticStyle
                ? this.active ? 0xFF6B5A3B : 0xFF9A8F75
                : this.active ? 0xFF2B2620 : 0xFF766F65;
        int sclera = cosmeticStyle
                ? this.active ? 0xFFFFF8E4 : 0xFFE7E0C8
                : this.active ? 0xFFFFF1D1 : 0xFFAAA397;
        int iris = cosmeticStyle
                ? this.active && isHoveredOrFocused() ? 0xFF859956 : 0xFFA0AD66
                : this.active && isHoveredOrFocused() ? 0xFF7BF3FF : 0xFF63CFE0;
        graphics.fill(x + 3, y + 6, x + 13, y + 11, outline);
        graphics.fill(x + 5, y + 4, x + 11, y + 13, outline);
        graphics.fill(x + 2, y + 7, x + 4, y + 10, outline);
        graphics.fill(x + 12, y + 7, x + 14, y + 10, outline);
        graphics.fill(x + 4, y + 6, x + 12, y + 11, sclera);
        graphics.fill(x + 6, y + 5, x + 10, y + 12, sclera);
        graphics.fill(x + 7, y + 6, x + 10, y + 11, iris);
        graphics.fill(x + 8, y + 7, x + 10, y + 10, cosmeticStyle ? 0xFF4E4930 : 0xFF15191B);
    }

}
