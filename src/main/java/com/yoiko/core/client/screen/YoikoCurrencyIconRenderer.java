package com.yoiko.core.client.screen;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.registry.YoikoItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Renders shared currency markers; item-backed currencies reuse their native item icon. */
public final class YoikoCurrencyIconRenderer {
    public static final int ICON_SIZE = 8;
    public static final int TEXT_GAP = 3;
    public static final int GOLD_COLOR = 0xFFD28A16;
    public static final int GEM_COLOR = 0xFF36BFD8;
    public static final int RELIC_COLOR = 0xFF8D7560;

    private static final ResourceLocation GOLD =
            YoikoServerCore.id("textures/gui/shared/currency/gold.png");
    private static final ResourceLocation GEM =
            YoikoServerCore.id("textures/gui/shared/currency/gem.png");

    private YoikoCurrencyIconRenderer() {
    }

    public static void render(GuiGraphics graphics, String currency, int x, int y) {
        if ("RELIC".equals(currency)) {
            graphics.renderItem(new ItemStack(YoikoItems.RELIC_GACHA_TICKET.get()), x - 4, y - 4);
            return;
        }
        ResourceLocation texture = "GEM".equals(currency) ? GEM : GOLD;
        graphics.blit(texture, x, y, ICON_SIZE, ICON_SIZE,
                0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    public static int color(String currency) {
        return switch (currency) {
            case "GEM" -> GEM_COLOR;
            case "RELIC" -> RELIC_COLOR;
            default -> GOLD_COLOR;
        };
    }
}
