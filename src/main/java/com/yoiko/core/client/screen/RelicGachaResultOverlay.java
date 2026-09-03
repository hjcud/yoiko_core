package com.yoiko.core.client.screen;

import com.yoiko.core.network.RelicGachaResultPayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Non-interactive relic appraisal card that leaves normal world input available. */
public final class RelicGachaResultOverlay {
    public static final RelicGachaResultOverlay INSTANCE = new RelicGachaResultOverlay();

    private static final int CARD_WIDTH = 248;
    private static final int CARD_HEIGHT = 130;
    private static final int DISPLAY_TICKS = 80;
    private static final int SLIDE_TICKS = 9;

    private RelicGachaResultPayload result;
    private int age;

    private RelicGachaResultOverlay() {
    }

    public void show(RelicGachaResultPayload result) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        this.result = result;
        this.age = 0;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        if (result == null || minecraft.isPaused()) {
            return;
        }
        age++;
        if (age >= DISPLAY_TICKS) {
            clear();
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (result == null || minecraft.options.hideGui || minecraft.player == null
                || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        render(event.getGuiGraphics(), minecraft.font);
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private void render(GuiGraphics graphics, Font font) {
        Layout target = layout(graphics.guiWidth(), graphics.guiHeight());
        float slideIn = eased(Math.min(1.0F, age / (float) SLIDE_TICKS));
        int remaining = DISPLAY_TICKS - age;
        float slideOut = remaining >= SLIDE_TICKS
                ? 0.0F
                : eased(1.0F - Math.max(0, remaining) / (float) SLIDE_TICKS);
        int x = target.x() + Math.round(((1.0F - slideIn) + slideOut) * 30.0F);
        int y = target.y();
        RelicResultCardRenderer.render(
                graphics, x, y, target.width(), target.height(), result.rarity(), age);

        int centerX = x + target.width() / 2;
        int innerX = x + 16;
        int innerWidth = target.width() - 32;
        if (age >= 5) {
            drawCentered(graphics, font,
                    Component.translatable("yoiko_core.relic.gacha_result.title"),
                    centerX, y + 12, 0xFFD9B878);
            graphics.fill(x + 12, y + 27, x + target.width() - 12, y + 28,
                    (rarityColor() & 0x00FFFFFF) | 0x50000000);
        }
        if (age >= 10) {
            Component name = Component.literal("+" + result.level() + " ").append(displayName());
            drawClamped(graphics, font, name, x + 43, y + 39,
                    innerWidth - 32, rarityColor());
            Component rarity = Component.translatable(
                    "yoiko_core.rarity." + result.rarity().name().toLowerCase(Locale.ROOT));
            drawClamped(graphics, font, rarity, x + 43, y + 51,
                    innerWidth - 32, 0xFF9A8F87);
        }
        if (age >= 15) {
            YoikoScreenStyle.renderRelicIcon(graphics, x + 18, y + 39, 16, result.rarity().name());
        }
        if (age >= 17) {
            drawEffect(graphics, font, result.primaryEffect(), result.primaryValue(),
                    innerX, y + 78, innerWidth, 0xFF67E5E8);
        }
        if (age >= 21) {
            if (result.secondaryEffect().isBlank()) {
                drawClamped(graphics, font,
                        Component.literal("◇ ").append(
                                Component.translatable("yoiko_core.ui.relic.secondary_effect_empty")),
                        innerX, y + 98, innerWidth, 0xFF777777);
            } else {
                drawEffect(graphics, font, result.secondaryEffect(), result.secondaryValue(),
                        innerX, y + 98, innerWidth, 0xFFC79CFF);
            }
        }
    }

    private void drawEffect(GuiGraphics graphics, Font font, String effect, double value,
                            int x, int y, int maxWidth, int color) {
        Component line = Component.literal("◆ ")
                .append(Component.translatable("yoiko_core.effect_short." + effect))
                .append(Component.literal("  +" + YoikoRelicValueFormatter.format(effect, value)));
        drawClamped(graphics, font, line, x, y, maxWidth, color);
    }

    private Component displayName() {
        return result.displayName().startsWith("yoiko_core.")
                ? Component.translatable(result.displayName())
                : Component.literal(result.displayName());
    }

    private int rarityColor() {
        return RelicUiText.rarityColor(result.rarity().name());
    }

    private static void drawClamped(GuiGraphics graphics, Font font, Component text,
                                    int x, int y, int maxWidth, int color) {
        String value = text.getString();
        if (font.width(value) > maxWidth) {
            value = font.plainSubstrByWidth(value,
                    Math.max(1, maxWidth - font.width("…"))) + "…";
        }
        graphics.drawString(font, value, x, y, color, false);
    }

    private static void drawCentered(GuiGraphics graphics, Font font, Component text,
                                     int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private static Layout layout(int screenWidth, int screenHeight) {
        int cardWidth = Math.min(CARD_WIDTH, Math.max(160, screenWidth / 2 - 10));
        cardWidth = Math.min(cardWidth, Math.max(1, screenWidth - 12));
        int cardHeight = Math.min(CARD_HEIGHT, Math.max(1, screenHeight - 12));
        return new Layout(screenWidth - cardWidth - 6,
                Math.max(6, (screenHeight - cardHeight) / 2), cardWidth, cardHeight);
    }

    private static float eased(float progress) {
        return 1.0F - (float) Math.pow(1.0F - progress, 3.0D);
    }

    private void clear() {
        result = null;
        age = 0;
    }

    private record Layout(int x, int y, int width, int height) {
    }
}
