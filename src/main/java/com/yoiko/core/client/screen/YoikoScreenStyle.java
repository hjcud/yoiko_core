package com.yoiko.core.client.screen;

import com.yoiko.core.config.YoikoClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yoiko.core.YoikoServerCore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;

final class YoikoScreenStyle {
    private static final ResourceLocation WIDGETS = YoikoServerCore.id("textures/gui/shared/widgets.png");
    private static final ResourceLocation STORAGE_LEFT_PANEL = YoikoServerCore.id("textures/gui/storage/panel_left.png");
    private static final ResourceLocation MAILBOX_LEFT_PANEL = YoikoServerCore.id("textures/gui/mailbox/panel_left.png");
    private static final ResourceLocation RELIC_LEFT_PANEL = YoikoServerCore.id("textures/gui/relic/panel_left.png");
    private static final ResourceLocation COSMETIC_LEFT_PANEL = YoikoServerCore.id("textures/gui/cosmetic/panel_left.png");
    private static final ResourceLocation MARKET_LEFT_PANEL = YoikoServerCore.id("textures/gui/market/panel_left.png");
    private static final ResourceLocation TURTLE_RACING_LEFT_PANEL = YoikoServerCore.id("textures/gui/turtle/panel_left_v2.png");
    private static final ResourceLocation STORAGE_RIGHT_PANEL = YoikoServerCore.id("textures/gui/storage/panel_right.png");
    private static final ResourceLocation MAILBOX_RIGHT_PANEL = YoikoServerCore.id("textures/gui/mailbox/panel_right.png");
    private static final ResourceLocation MAILBOX_NEWS_PANEL = YoikoServerCore.id("textures/gui/mailbox/panel_news.png");
    private static final ResourceLocation RELIC_RIGHT_PANEL = YoikoServerCore.id("textures/gui/relic/panel_right.png");
    private static final ResourceLocation COSMETIC_RIGHT_PANEL = YoikoServerCore.id("textures/gui/cosmetic/panel_right.png");
    private static final ResourceLocation MARKET_RIGHT_PANEL = YoikoServerCore.id("textures/gui/market/panel_right.png");
    private static final ResourceLocation TURTLE_RACING_RIGHT_PANEL = YoikoServerCore.id("textures/gui/turtle/panel_right_v2.png");
    private static final ResourceLocation TURTLE_RACING_DIALOG = YoikoServerCore.id("textures/gui/turtle/dialog.png");
    private static final ResourceLocation TURTLE_RACING_BUTTONS = YoikoServerCore.id("textures/gui/turtle/buttons.png");
    private static final ResourceLocation TURTLE_RACING_CARD = YoikoServerCore.id("textures/gui/turtle/card.png");
    private static final ResourceLocation RELIC_COMMON = YoikoServerCore.id("textures/item/relic/rarity/common.png");
    private static final ResourceLocation RELIC_UNCOMMON = YoikoServerCore.id("textures/item/relic/rarity/uncommon.png");
    private static final ResourceLocation RELIC_RARE = YoikoServerCore.id("textures/item/relic/rarity/rare.png");
    private static final ResourceLocation RELIC_EPIC = YoikoServerCore.id("textures/item/relic/rarity/epic.png");
    private static final ResourceLocation RELIC_LEGENDARY = YoikoServerCore.id("textures/item/relic/rarity/legendary.png");
    private static final ResourceLocation RELIC_MYSTIC = YoikoServerCore.id("textures/item/relic/rarity/mystic.png");
    private static final ResourceLocation[] RELIC_RADIANT_FRAMES = {
            YoikoServerCore.id("textures/gui/relic/animations/radiant_icon/frame_00.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_icon/frame_01.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_icon/frame_02.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_icon/frame_03.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_icon/frame_04.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_icon/frame_05.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_icon/frame_06.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_icon/frame_07.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_icon/frame_08.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_icon/frame_09.png")
    };
    private static final int RADIANT_FRAME_SIZE = 16;
    private static final long RADIANT_FRAME_DURATION_MS = 100L;
    private static final ResourceLocation RELIC_EQUIP_SLOT = YoikoServerCore.id("textures/gui/relic/equipment/slot_empty.png");
    private static final ResourceLocation RELIC_EQUIPPED_SLOT = YoikoServerCore.id("textures/gui/relic/equipment/slot_occupied.png");
    private static final int ATLAS_WIDTH = 192;
    private static final int ATLAS_HEIGHT = 64;
    private static final int WORLD_DIM = 0x18000000;
    private static final int CELL = 18;
    private static final int PANEL_SOURCE_SIZE = CELL;
    private static final int PANEL_CORNER = 4;
    private static final int BUTTON_SOURCE_WIDTH = CELL;
    private static final int BUTTON_SOURCE_HEIGHT = CELL;
    private static final int BUTTON_CORNER = 4;
    private static final int SLOT_SOURCE_SIZE = CELL;
    private static final int TAB_PANEL_OVERFLOW = 25;
    private static final int TAB_PANEL_TEXTURE_WIDTH = YoikoMenuLayout.TAB_PANEL_WIDTH + TAB_PANEL_OVERFLOW * 2;
    private static final int TAB_PANEL_TEXTURE_HEIGHT = YoikoMenuLayout.TAB_PANEL_HEIGHT + TAB_PANEL_OVERFLOW * 2;

    private static final int TOP_ROW = 0;
    private static final int PANEL_BASE_U = 0;
    private static final int SIDE_TAB_HOVER_U = 18;
    private static final int SIDE_TAB_NORMAL_U = 36;
    private static final int BUTTON_U = 54;
    private static final int BUTTON_PRESSED_U = 72;
    private static final int PAGE_LEFT_U = 90;
    private static final int PAGE_RIGHT_U = 108;
    private static final int CANCEL_U = 126;
    private static final int CONFIRM_U = 144;
    private static final float DISABLED_PAGE_BRIGHTNESS = 0.5F;

    private static final int BRIGHT_ROW = 18;
    private static final int DARK_ROW = 36;
    private static final int LOCK_ROW = 18;
    private static final int ICON_ROW = 36;
    private static final int ICON_START_U = 36;
    private static final int UI_PANEL_U = 0;
    private static final int UI_SLOT_U = 18;
    private static final int LOCK_ICON_U = 36;
    private static final int LOCK_DIM_U = 54;
    private static final int FAVORITE_ICON_U = 72;
    private static final int FAVORITE_ICON_V = 18;

    private YoikoScreenStyle() {
    }

    static void renderBackdrop(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, WORLD_DIM);
    }

    static void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        renderNineSlice(graphics, x, y, width, height, PANEL_BASE_U, TOP_ROW, PANEL_SOURCE_SIZE, PANEL_SOURCE_SIZE, PANEL_CORNER);
    }

    static void renderStorageLeftPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, STORAGE_LEFT_PANEL, x, y);
    }

    static void renderStorageRightPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, STORAGE_RIGHT_PANEL, x, y);
    }

    static void renderMailboxLeftPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, MAILBOX_LEFT_PANEL, x, y);
    }

    static void renderMailboxRightPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, MAILBOX_RIGHT_PANEL, x, y);
    }

    static void renderMailboxNewsPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, MAILBOX_NEWS_PANEL, x, y);
    }

    static void renderRelicLeftPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, RELIC_LEFT_PANEL, x, y);
    }

    static void renderRelicRightPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, RELIC_RIGHT_PANEL, x, y);
    }

    static void renderCosmeticLeftPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, COSMETIC_LEFT_PANEL, x, y);
    }

    static void renderCosmeticRightPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, COSMETIC_RIGHT_PANEL, x, y);
    }

    static void renderMarketLeftPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, MARKET_LEFT_PANEL, x, y);
    }

    static void renderMarketRightPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, MARKET_RIGHT_PANEL, x, y);
    }

    static void renderTurtleRacingLeftPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, TURTLE_RACING_LEFT_PANEL, x, y);
    }

    static void renderTurtleRacingRightPanel(GuiGraphics graphics, int x, int y) {
        renderOverflowPanel(graphics, TURTLE_RACING_RIGHT_PANEL, x, y);
    }

    static void renderTurtleDialogPanel(GuiGraphics graphics, int x, int y) {
        blitTexture(graphics, TURTLE_RACING_DIALOG, x, y, 180, 211, 180, 211);
    }

    static void renderTurtleButton(GuiGraphics graphics, int x, int y, int width, int height,
                                   boolean hovered, boolean active) {
        renderNineSliceTexture(graphics, TURTLE_RACING_BUTTONS, x, y, width, height,
                hovered && active ? 18 : 0, 0, 18, 18, 4, 36, 18);
        if (!active) graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x36A18A70);
    }

    static void renderTurtleCard(GuiGraphics graphics, int x, int y, int width, int height) {
        renderNineSliceTexture(graphics, TURTLE_RACING_CARD, x, y, width, height,
                0, 0, 18, 18, 4, 18, 18);
    }

    static void renderCosmeticButton(GuiGraphics graphics, int x, int y, int width, int height,
                                     boolean hovered, boolean active) {
        int border = active ? (hovered ? 0xFF89945D : 0xFFC4A66C) : 0xFFC8B999;
        int fill = active ? (hovered ? 0xFFF0EBC5 : 0xFFF6E6BE) : 0xFFE9DFC6;
        graphics.fill(x + 1, y + 2, x + width + 1, y + height + 1, 0x24543A22);
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 4, 0x55FFFFFF);
        if (active && !hovered) {
            graphics.fill(x + 3, y + height - 4, x + width - 3, y + height - 3, 0x287C8350);
        }
        if (width >= 48) {
            renderCosmeticFlower(graphics, x + 7, y + height / 2, active ? 0xFFA0AD66 : 0xFFB8AF8F);
            renderCosmeticFlower(graphics, x + width - 8, y + height / 2, active ? 0xFFA0AD66 : 0xFFB8AF8F);
        }
    }

    static void renderPaperModeTab(GuiGraphics graphics, int x, int y, int width, int height,
                                   boolean hovered, boolean active) {
        int border = active ? (hovered ? 0xFF8D965C : 0xFFB99A63) : 0xFFB7AA8C;
        int paper = active ? (hovered ? 0xFFFFF0C8 : 0xFFF8E6BC) : 0xFFE8DDC2;
        graphics.fill(x + 2, y + 3, x + width + 2, y + height + 2, 0x30543A22);
        graphics.fill(x, y, x + width - 4, y + height, border);
        graphics.fill(x + 2, y + 2, x + width - 5, y + height - 2, paper);
        graphics.fill(x + width - 4, y + 4, x + width, y + height, border);
        graphics.fill(x + width - 5, y + 2, x + width - 1, y + 5, 0xFFFFF5D7);
        graphics.fill(x + 4, y + 3, x + width - 8, y + 4, 0x55FFFFFF);
        if (hovered && active) {
            graphics.fill(x + 2, y + height - 3, x + width - 5, y + height - 2, 0x56889455);
        }
    }

    static void renderCosmeticPaperCard(GuiGraphics graphics, int x, int y, int width, int height) {
        // A translucent inset keeps the illustrated parchment underneath visible instead of
        // covering it with a bright rectangular card. The clipped corners mimic a paper note.
        graphics.fill(x + 3, y + 3, x + width + 1, y + height + 2, 0x18543A22);
        graphics.fill(x + 2, y, x + width - 2, y + height, 0xB8C7A66E);
        graphics.fill(x, y + 2, x + width, y + height - 2, 0xB8C7A66E);
        graphics.fill(x + 3, y + 1, x + width - 3, y + height - 1, 0xC8F8EBC9);
        graphics.fill(x + 1, y + 3, x + width - 1, y + height - 3, 0xC8F8EBC9);
        graphics.renderOutline(x + 4, y + 4, width - 8, height - 8, 0x49C9AA72);
        graphics.fill(x + 5, y + 5, x + width - 5, y + 6, 0x38FFFFFF);
    }

    static void renderCosmeticRibbon(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x - 5, y + 3, x, y + height - 2, 0xFF929C62);
        graphics.fill(x - 4, y + 4, x, y + height - 3, 0xFFDDE4AE);
        graphics.fill(x + width, y + 3, x + width + 5, y + height - 2, 0xFF929C62);
        graphics.fill(x + width, y + 4, x + width + 4, y + height - 3, 0xFFDDE4AE);
        graphics.fill(x, y, x + width, y + height, 0xFF929C62);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFFE6EABD);
        renderCosmeticFlower(graphics, x + 8, y + height / 2, 0xFFA0AD66);
        renderCosmeticFlower(graphics, x + width - 9, y + height / 2, 0xFFA0AD66);
    }

    static void renderCosmeticPhotoFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 2, y + 3, x + width + 2, y + height + 3, 0x30543A22);
        graphics.fill(x, y, x + width, y + height, 0xFFC9AA6C);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFFFFF9EA);
        graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0xFFF3E2BC);
        graphics.fill(x + 5, y + 5, x + width - 5, y + 6, 0x55FFFFFF);
    }

    static void renderCosmeticEquipmentGlyph(GuiGraphics graphics, int x, int y, int size, String type) {
        int color = 0xFF7B7951;
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        switch (type) {
            case "HEAD" -> {
                graphics.renderOutline(centerX - 4, centerY - 5, 9, 9, color);
                graphics.fill(centerX - 2, centerY + 4, centerX + 3, centerY + 5, color);
            }
            case "CHEST" -> {
                graphics.fill(centerX - 5, centerY - 4, centerX + 6, centerY - 2, color);
                graphics.fill(centerX - 4, centerY - 2, centerX + 5, centerY + 5, color);
                graphics.fill(centerX - 2, centerY + 5, centerX + 3, centerY + 6, color);
                graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 4, 0xFFFFF8E4);
            }
            case "FEET" -> {
                renderCosmeticSparkle(graphics, centerX - 4, centerY + 2, color);
                renderCosmeticSparkle(graphics, centerX + 3, centerY - 3, color);
            }
            case "RANK" -> {
                graphics.renderOutline(centerX - 4, centerY - 5, 9, 8, color);
                graphics.renderOutline(centerX - 2, centerY - 3, 5, 4, color);
                graphics.fill(centerX - 3, centerY + 3, centerX - 1, centerY + 7, color);
                graphics.fill(centerX + 2, centerY + 3, centerX + 4, centerY + 7, color);
            }
            default -> renderCosmeticSparkle(graphics, centerX, centerY, color);
        }
    }

    static void renderCosmeticSlotHighlight(GuiGraphics graphics, int x, int y, int size) {
        graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0x335F7C40);
        graphics.renderOutline(x, y, size, size, 0xFF93A05D);
    }

    static void renderCosmeticDivider(GuiGraphics graphics, int x, int y, int width) {
        for (int offset = 0; offset < width; offset += 5) {
            graphics.fill(x + offset, y, x + Math.min(width, offset + 3), y + 1, 0xFFCCAE76);
        }
    }

    private static void renderCosmeticFlower(GuiGraphics graphics, int centerX, int centerY, int color) {
        graphics.fill(centerX - 1, centerY - 3, centerX + 2, centerY, color);
        graphics.fill(centerX - 1, centerY + 1, centerX + 2, centerY + 4, color);
        graphics.fill(centerX - 4, centerY - 1, centerX - 1, centerY + 2, color);
        graphics.fill(centerX + 2, centerY - 1, centerX + 5, centerY + 2, color);
        graphics.fill(centerX, centerY, centerX + 1, centerY + 1, 0xFFF6EAC8);
    }

    private static void renderCosmeticSparkle(GuiGraphics graphics, int centerX, int centerY, int color) {
        graphics.fill(centerX, centerY - 4, centerX + 1, centerY + 5, color);
        graphics.fill(centerX - 4, centerY, centerX + 5, centerY + 1, color);
        graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, color);
    }

    static void renderSubPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        renderNineSlice(graphics, x, y, width, height, UI_PANEL_U, DARK_ROW, PANEL_SOURCE_SIZE, PANEL_SOURCE_SIZE, PANEL_CORNER);
    }

    static void renderDetailFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        renderSubPanel(graphics, x, y, width, height);
    }

    static void renderButton(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered, boolean active) {
        int u = active && hovered ? BUTTON_PRESSED_U : BUTTON_U;
        renderNineSlice(graphics, x, y, width, height, u, TOP_ROW, BUTTON_SOURCE_WIDTH, BUTTON_SOURCE_HEIGHT, BUTTON_CORNER);
        if (!active) {
            graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x66000000);
        }
    }

    static void renderRightTabButton(GuiGraphics graphics, int x, int y, int width, int height, boolean highlighted) {
        int u = highlighted ? SIDE_TAB_HOVER_U : SIDE_TAB_NORMAL_U;
        renderNineSlice(graphics, x, y, width, height, u, TOP_ROW, CELL, CELL, BUTTON_CORNER);
    }

    static void renderSpriteButton(GuiGraphics graphics, int x, int y, int size, int column, boolean hovered, boolean active) {
        int u = spriteU(column);
        boolean dimmedPageButton = !active && isPageSprite(column);
        if (dimmedPageButton) {
            RenderSystem.setShaderColor(DISABLED_PAGE_BRIGHTNESS, DISABLED_PAGE_BRIGHTNESS, DISABLED_PAGE_BRIGHTNESS, 1.0F);
        }
        try {
            blit(graphics, x, y, size, size, u, TOP_ROW, CELL, CELL);
        } finally {
            if (dimmedPageButton) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        if (active && hovered) {
            if (isPageSprite(column)) {
                // Match inventory-slot hover feedback without adding a separate underline.
                renderSlotMask(graphics, x, y, size, size, 0xFFFFFF, 1);
            } else {
                graphics.fill(x + 3, y + size - 3, x + size - 3, y + size - 2, 0xFFFFD36A);
            }
        }
        if (!active && !isPageSprite(column)) {
            graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0x88000000);
        }
    }

    static void renderSlot(GuiGraphics graphics, int x, int y) {
        blit(graphics, x, y, 24, 24, UI_SLOT_U, DARK_ROW, SLOT_SOURCE_SIZE, SLOT_SOURCE_SIZE);
    }

    static void renderRelicEquipSlot(GuiGraphics graphics, int x, int y) {
        blitTexture(graphics, RELIC_EQUIP_SLOT, x + 1, y + 1, 16, 16, 16, 16);
    }

    static void renderRelicEquippedSlot(GuiGraphics graphics, int x, int y) {
        blitTexture(graphics, RELIC_EQUIPPED_SLOT, x, y, 18, 18, 18, 18);
    }

    static void renderLockedSlot(GuiGraphics graphics, int x, int y) {
        // The appearance widgets render their own locked-state dim and unlock-kind icon.
    }

    static void renderInventoryCompactSlot(GuiGraphics graphics, int x, int y) {
        blit(graphics, x, y, 18, 18, UI_SLOT_U, BRIGHT_ROW, SLOT_SOURCE_SIZE, SLOT_SOURCE_SIZE);
    }

    static void renderStorageCompactLockedSlot(GuiGraphics graphics, int x, int y) {
        renderSlotMask(graphics, x, y, 18, 18, 0x000000, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            blit(graphics, x, y, 18, 18, LOCK_ICON_U, LOCK_ROW, SLOT_SOURCE_SIZE, SLOT_SOURCE_SIZE);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    static void renderCosmeticCompactLockedSlot(GuiGraphics graphics, int x, int y) {
        renderSlotMask(graphics, x, y, 18, 18, 0x000000, 4);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            blit(graphics, x, y, 18, 18, LOCK_ICON_U, LOCK_ROW, SLOT_SOURCE_SIZE, SLOT_SOURCE_SIZE);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    /**
     * Tints a slot through the rounded 18 px alpha mask stored at (54, 18) in the widget atlas.
     * Repeating the mask increases opacity without introducing a square fill around its corners.
     */
    static void renderSlotMask(GuiGraphics graphics, int x, int y, int width, int height, int rgb, int layers) {
        if (width <= 0 || height <= 0 || layers <= 0) {
            return;
        }
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(red, green, blue, 1.0F);
        try {
            for (int layer = 0; layer < layers; layer++) {
                blit(graphics, x, y, width, height, LOCK_DIM_U, LOCK_ROW, SLOT_SOURCE_SIZE, SLOT_SOURCE_SIZE);
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    static void renderMenuIcon(GuiGraphics graphics, int iconIndex, int x, int y) {
        int column = Math.max(0, iconIndex);
        blit(graphics, x, y, 18, 18, ICON_START_U + cellX(column), ICON_ROW, CELL, CELL);
    }

    static void renderCheckIcon(GuiGraphics graphics, int x, int y) {
        renderCheckIcon(graphics, x, y, 18);
    }

    static void renderCheckIcon(GuiGraphics graphics, int x, int y, int size) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 260.0F);
        blit(graphics, x, y, size, size, CONFIRM_U, TOP_ROW, CELL, CELL);
        graphics.pose().popPose();
    }

    static void renderFavoriteIcon(GuiGraphics graphics, int x, int y, int size) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 261.0F);
        blit(graphics, x, y, size, size, FAVORITE_ICON_U, FAVORITE_ICON_V, CELL, CELL);
        graphics.pose().popPose();
    }

    static void renderRelicIcon(GuiGraphics graphics, int x, int y, int size, String rarity) {
        String normalizedRarity = rarity == null ? "" : rarity.toUpperCase(java.util.Locale.ROOT);
        if ("RADIANT".equals(normalizedRarity)) {
            int frame = (int) ((Util.getMillis() / RADIANT_FRAME_DURATION_MS) % RELIC_RADIANT_FRAMES.length);
            blitTexture(
                    graphics,
                    RELIC_RADIANT_FRAMES[frame],
                    x,
                    y,
                    size,
                    size,
                    RADIANT_FRAME_SIZE,
                    RADIANT_FRAME_SIZE
            );
            return;
        }
        ResourceLocation texture = switch (normalizedRarity) {
            case "UNCOMMON" -> RELIC_UNCOMMON;
            case "RARE" -> RELIC_RARE;
            case "EPIC" -> RELIC_EPIC;
            case "LEGENDARY" -> RELIC_LEGENDARY;
            case "MYSTIC", "UNIQUE" -> RELIC_MYSTIC;
            default -> RELIC_COMMON;
        };
        blitTexture(graphics, texture, x, y, size, size, 16, 16);
    }

    static void renderWidgets(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : screen.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
            if (YoikoClientConfig.DEBUG_LAYOUT_OVERLAY.get() && renderable instanceof AbstractWidget widget) {
                int color = widget.isMouseOver(mouseX, mouseY) ? 0xFFFFD45A : 0xFF45D9FF;
                graphics.renderOutline(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), color);
            }
        }
    }

    private static void renderNineSlice(GuiGraphics graphics, int x, int y, int width, int height, int u, int v, int sourceWidth, int sourceHeight, int corner) {
        int centerSourceWidth = sourceWidth - corner * 2;
        int centerSourceHeight = sourceHeight - corner * 2;
        int centerWidth = Math.max(0, width - corner * 2);
        int centerHeight = Math.max(0, height - corner * 2);

        blit(graphics, x, y, corner, corner, u, v, corner, corner);
        blit(graphics, x + width - corner, y, corner, corner, u + sourceWidth - corner, v, corner, corner);
        blit(graphics, x, y + height - corner, corner, corner, u, v + sourceHeight - corner, corner, corner);
        blit(graphics, x + width - corner, y + height - corner, corner, corner, u + sourceWidth - corner, v + sourceHeight - corner, corner, corner);

        blit(graphics, x + corner, y, centerWidth, corner, u + corner, v, centerSourceWidth, corner);
        blit(graphics, x + corner, y + height - corner, centerWidth, corner, u + corner, v + sourceHeight - corner, centerSourceWidth, corner);
        blit(graphics, x, y + corner, corner, centerHeight, u, v + corner, corner, centerSourceHeight);
        blit(graphics, x + width - corner, y + corner, corner, centerHeight, u + sourceWidth - corner, v + corner, corner, centerSourceHeight);
        blit(graphics, x + corner, y + corner, centerWidth, centerHeight, u + corner, v + corner, centerSourceWidth, centerSourceHeight);
    }

    private static void renderNineSliceTexture(GuiGraphics graphics, ResourceLocation texture,
                                               int x, int y, int width, int height,
                                               int u, int v, int sourceWidth, int sourceHeight, int corner,
                                               int textureWidth, int textureHeight) {
        int centerSourceWidth = sourceWidth - corner * 2;
        int centerSourceHeight = sourceHeight - corner * 2;
        int centerWidth = Math.max(0, width - corner * 2);
        int centerHeight = Math.max(0, height - corner * 2);
        blitRegion(graphics, texture, x, y, corner, corner, u, v, corner, corner, textureWidth, textureHeight);
        blitRegion(graphics, texture, x + width - corner, y, corner, corner, u + sourceWidth - corner, v, corner, corner, textureWidth, textureHeight);
        blitRegion(graphics, texture, x, y + height - corner, corner, corner, u, v + sourceHeight - corner, corner, corner, textureWidth, textureHeight);
        blitRegion(graphics, texture, x + width - corner, y + height - corner, corner, corner,
                u + sourceWidth - corner, v + sourceHeight - corner, corner, corner, textureWidth, textureHeight);
        blitRegion(graphics, texture, x + corner, y, centerWidth, corner, u + corner, v, centerSourceWidth, corner, textureWidth, textureHeight);
        blitRegion(graphics, texture, x + corner, y + height - corner, centerWidth, corner,
                u + corner, v + sourceHeight - corner, centerSourceWidth, corner, textureWidth, textureHeight);
        blitRegion(graphics, texture, x, y + corner, corner, centerHeight, u, v + corner, corner, centerSourceHeight, textureWidth, textureHeight);
        blitRegion(graphics, texture, x + width - corner, y + corner, corner, centerHeight,
                u + sourceWidth - corner, v + corner, corner, centerSourceHeight, textureWidth, textureHeight);
        blitRegion(graphics, texture, x + corner, y + corner, centerWidth, centerHeight,
                u + corner, v + corner, centerSourceWidth, centerSourceHeight, textureWidth, textureHeight);
    }

    private static int spriteU(int column) {
        return switch (column) {
            case 10 -> CANCEL_U;
            case 11 -> CONFIRM_U;
            case 12 -> PAGE_LEFT_U;
            case 13 -> PAGE_RIGHT_U;
            default -> cellX(column);
        };
    }

    private static boolean isPageSprite(int column) {
        return column == 12 || column == 13;
    }

    private static void blit(GuiGraphics graphics, int x, int y, int width, int height, int u, int v, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(WIDGETS, x, y, width, height, (float) u, (float) v, sourceWidth, sourceHeight, ATLAS_WIDTH, ATLAS_HEIGHT);
    }

    private static void blitTexture(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(texture, x, y, width, height, 0.0F, 0.0F, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
    }

    private static void blitRegion(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                                   int u, int v, int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) return;
        graphics.blit(texture, x, y, width, height, (float) u, (float) v,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }

    private static void renderOverflowPanel(GuiGraphics graphics, ResourceLocation texture, int x, int y) {
        graphics.blit(
                texture,
                x - TAB_PANEL_OVERFLOW,
                y - TAB_PANEL_OVERFLOW,
                TAB_PANEL_TEXTURE_WIDTH,
                TAB_PANEL_TEXTURE_HEIGHT,
                0.0F,
                0.0F,
                TAB_PANEL_TEXTURE_WIDTH,
                TAB_PANEL_TEXTURE_HEIGHT,
                TAB_PANEL_TEXTURE_WIDTH,
                TAB_PANEL_TEXTURE_HEIGHT
        );
    }

    private static int cellX(int column) {
        return column * CELL;
    }

}
