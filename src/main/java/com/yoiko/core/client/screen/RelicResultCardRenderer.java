package com.yoiko.core.client.screen;

import com.yoiko.core.relic.RelicRarity;
import net.minecraft.client.gui.GuiGraphics;

/** Draws a standalone result card with the same palettes as the relic tooltip styles. */
final class RelicResultCardRenderer {
    private RelicResultCardRenderer() {
    }

    static void render(GuiGraphics graphics, int x, int y, int width, int height,
                       RelicRarity rarity, float age) {
        Palette palette = palette(rarity);
        graphics.fillGradient(x + 1, y + 1, x + width - 1, y + height - 1,
                palette.backgroundTop(), palette.backgroundBottom());
        graphics.fillGradient(x, y, x + 1, y + height,
                palette.borderTop(), palette.borderBottom());
        graphics.fillGradient(x + width - 1, y, x + width, y + height,
                palette.borderTop(), palette.borderBottom());
        graphics.fill(x, y, x + width, y + 1, palette.borderTop());
        graphics.fill(x, y + height - 1, x + width, y + height, palette.borderBottom());

        int innerBorder = withAlpha(palette.borderTop(), 0x42);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 4, innerBorder);
        graphics.fill(x + 3, y + height - 4, x + width - 3, y + height - 3,
                withAlpha(palette.borderBottom(), 0x32));

        if (rarity.ordinal() >= RelicRarity.RARE.ordinal()) {
            renderShimmer(graphics, x, y, width, height, palette.accent(), age, rarity);
        }
    }

    private static void renderShimmer(GuiGraphics graphics, int x, int y, int width, int height,
                                      int accent, float age, RelicRarity rarity) {
        int travel = width + 36;
        int center = x - 18 + Math.floorMod((int) (age * 2.2F), travel);
        for (int offset = -7; offset <= 7; offset++) {
            int alpha = Math.max(0, 18 - Math.abs(offset) * 2);
            graphics.fill(center + offset, y + 2, center + offset + 1, y + height - 2,
                    withAlpha(accent, alpha));
        }
        if (rarity == RelicRarity.RADIANT) {
            int pulseAlpha = 18 + Math.round((float) ((Math.sin(age * 0.12F) + 1.0D) * 8.0D));
            graphics.fill(x + 2, y + height / 2, x + width - 2, y + height / 2 + 1,
                    withAlpha(accent, pulseAlpha));
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static Palette palette(RelicRarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> new Palette(0xF51B1813, 0xF5110F0C, 0xB058AD6B, 0x80336E42, 0xFF58AD6B);
            case RARE -> new Palette(0xF5191717, 0xF5100E10, 0xC04D91D8, 0x802B5D92, 0xFF4D91D8);
            case EPIC -> new Palette(0xF51C141C, 0xF5110B12, 0xC0A75DD6, 0x80653486, 0xFFA75DD6);
            case LEGENDARY -> new Palette(0xF51F1811, 0xF5130E09, 0xC0D2A842, 0x80826924, 0xFFD2A842);
            case MYSTIC -> new Palette(0xF5211416, 0xF5140B0D, 0xC0C24C5A, 0x80762B34, 0xFFC24C5A);
            case RADIANT -> new Palette(0xF5151C1E, 0xF50B1214, 0xD09BE9F0, 0x90609CA5, 0xFF9BE9F0);
            default -> new Palette(0xF51D1714, 0xF5120E0D, 0xB08F8174, 0x80605249, 0xFF8F8174);
        };
    }

    private record Palette(int backgroundTop, int backgroundBottom,
                           int borderTop, int borderBottom, int accent) {
    }
}
