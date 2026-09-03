package com.yoiko.core.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.network.OpenRelicPayload;
import com.yoiko.core.registry.YoikoItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Stateless renderer for the relic upgrade workbench and its material controls. */
final class RelicWorkbenchPanelRenderer {
    static final int RIGHT_X = YoikoMenuLayout.SPLIT_RIGHT_X;
    static final int RIGHT_WIDTH = YoikoMenuLayout.SPLIT_RIGHT_WIDTH;
    static final int WORKBENCH_CENTER_X = RIGHT_X + 84;
    static final int UPGRADE_TARGET_SIZE = 18;
    static final int UPGRADE_TARGET_CENTER_X_OFFSET = -1;
    static final int UPGRADE_TARGET_X = RIGHT_X + 76;
    static final int UPGRADE_TARGET_Y = 49;
    static final int UPGRADE_BUTTON_CENTER_Y = 104;
    static final int MATERIAL_CENTER_Y = 128;
    static final int PROTECTION_CENTER_X = RIGHT_X + 46;
    static final int UPGRADE_BUTTON_CENTER_X = WORKBENCH_CENTER_X;
    static final int UPGRADE_CRYSTAL_CENTER_X = RIGHT_X + 122;
    static final int WORKBENCH_SLOT_BORDER_SIZE = 23;
    static final int UPGRADE_BUTTON_HIT_WIDTH = 40;
    static final int UPGRADE_BUTTON_HIT_HEIGHT = 18;
    static final int PROTECTION_SLOT_FRAME_TICKS = 3;
    static final int PROTECTION_ANIMATION_DURATION = 18;

    private static final ResourceLocation UPGRADE_CRYSTAL_ICON =
            YoikoServerCore.id("textures/item/relic/consumables/relic_upgrade_crystal.png");
    private static final ResourceLocation SCRAP_ICON =
            YoikoServerCore.id("textures/item/relic/consumables/relic_scrap.png");
    private static final ResourceLocation SLOT_VALID =
            YoikoServerCore.id("textures/gui/relic/workbench/slot_valid.png");
    private static final ResourceLocation SLOT_INVALID =
            YoikoServerCore.id("textures/gui/relic/workbench/slot_invalid.png");
    private static final ResourceLocation[] PROTECTION_INVALID_FRAMES = {
            YoikoServerCore.id("textures/gui/relic/workbench/slot_invalid_0.png"),
            YoikoServerCore.id("textures/gui/relic/workbench/slot_invalid_1.png"),
            YoikoServerCore.id("textures/gui/relic/workbench/slot_invalid_2.png"),
            YoikoServerCore.id("textures/gui/relic/workbench/slot_invalid_3.png"),
            YoikoServerCore.id("textures/gui/relic/workbench/slot_invalid_4.png"),
            YoikoServerCore.id("textures/gui/relic/workbench/slot_invalid_5.png")
    };
    private static final int IDENTITY_Y = 19;
    private static final int UPGRADE_LABEL_COLOR = 0xFF4F8A3A;
    private static final int DISABLED_UPGRADE_LABEL_COLOR = 0xFF91A07E;
    private static final int TARGET_ICON_SIZE = 16;
    private static final int TARGET_ICON_X_OFFSET = -1;
    private static final int TARGET_ICON_Y_OFFSET = 0;
    private static final int UPGRADE_LABEL_X_OFFSET = 1;
    private static final int MATERIAL_ICON_SIZE = TARGET_ICON_SIZE;
    private static final int MATERIAL_ICON_X_OFFSET = 1;
    private static final float UNAVAILABLE_MATERIAL_BRIGHTNESS = 0.42F;
    private static final int UPGRADE_DETAILS_Y = 174;
    private static final int UPGRADE_EFFECT_Y = UPGRADE_DETAILS_Y + 14;
    private static final int DETAIL_NAME_X = RIGHT_X + 8;
    private static final int DETAIL_NAME_WIDTH = 84;
    private static final int DETAIL_NAME_VALUE_GAP = 4;
    private static final int DETAIL_CURRENT_RIGHT_X = RIGHT_X + 112;
    private static final int DETAIL_ARROW_CENTER_X = RIGHT_X + 120;
    private static final int DETAIL_NEXT_RIGHT_X = RIGHT_X + 168;
    private static final int OUTCOME_FIRST_ROW_Y = 211;
    private static final int OUTCOME_SECOND_ROW_Y = 227;
    private static final int SUCCESS_CENTER_OFFSET_X = -10;
    private static final int SUCCESS_VALUE_Y_OFFSET = -1;
    private static final int FAILURE_ROW_Y_OFFSET = -1;
    private static final int FAILURE_SECOND_ROW_Y_OFFSET = -1;
    private static final int FAILURE_LABEL_X = 13;
    private static final int FAILURE_MARKER_RIGHT_PADDING = 7;
    private static final int OUTCOME_PANEL_GAP = 6;

    private RelicWorkbenchPanelRenderer() {
    }

    static ItemStack render(GuiGraphics graphics, Font font, int left, int top,
                            int mouseX, int mouseY, State state) {
        OpenRelicPayload.Entry entry = state.entry();
        int targetX = left + UPGRADE_TARGET_X;
        int targetY = top + UPGRADE_TARGET_Y;
        int targetCenterX = targetX + UPGRADE_TARGET_SIZE / 2 + UPGRADE_TARGET_CENTER_X_OFFSET;
        int targetCenterY = targetY + UPGRADE_TARGET_SIZE / 2;
        if (entry != null) {
            int iconOffset = (UPGRADE_TARGET_SIZE - TARGET_ICON_SIZE) / 2;
            YoikoScreenStyle.renderRelicIcon(graphics,
                    targetX + iconOffset + TARGET_ICON_X_OFFSET,
                    targetY + iconOffset + TARGET_ICON_Y_OFFSET,
                    TARGET_ICON_SIZE, entry.rarity());
            RelicScreenRenderSupport.drawLevelBadge(
                    graphics, font, targetX, targetY, UPGRADE_TARGET_SIZE, entry.level(), entry.level());
        }
        renderSlotBorder(graphics, targetCenterX, targetCenterY, validTarget(entry), SLOT_INVALID);

        if (entry == null) {
            RelicScreenRenderSupport.drawCentered(graphics, font,
                    YoikoClientText.tr("yoiko_core.ui.relic.no_selection").withStyle(ChatFormatting.GRAY),
                    left + WORKBENCH_CENTER_X, top + IDENTITY_Y, 0xFF9A8268);
        } else {
            int rarityColor = RelicUiText.identityRarityColor(entry.rarity());
            String rarity = RelicUiText.rarityLabel(entry.rarity());
            int nameWidth = RIGHT_WIDTH - 20 - font.width(rarity + " · ");
            String name = abbreviated(font, YoikoClientText.dataText(entry.displayName()), nameWidth);
            drawCenteredSegments(graphics, font, left + WORKBENCH_CENTER_X, top + IDENTITY_Y,
                    new TextSegment(rarity, rarityColor), new TextSegment(" · ", 0xFF7B6855),
                    new TextSegment(name, rarityColor));
        }
        renderDetails(graphics, font, left, top, entry);
        renderProbabilities(graphics, font, left, top, entry,
                state.useProtection() && state.protectionUsable());
        return renderControls(graphics, font, left, top, mouseX, mouseY, state);
    }

    private static void renderDetails(GuiGraphics graphics, Font font, int left, int top,
                                      OpenRelicPayload.Entry entry) {
        renderEffectRow(graphics, font, left, top + UPGRADE_DETAILS_Y, entry, false);
        renderEffectRow(graphics, font, left, top + UPGRADE_EFFECT_Y, entry, true);
    }

    private static void renderEffectRow(GuiGraphics graphics, Font font, int left, int y,
                                        OpenRelicPayload.Entry entry, boolean secondary) {
        String effect = entry == null ? "" : secondary ? entry.secondaryEffect() : entry.effect();
        boolean emptySecondary = secondary && effect.isBlank();
        double current = entry == null ? 0.0D : secondary ? entry.secondaryValue() : entry.value();
        double next = entry == null ? 0.0D : secondary ? entry.secondaryNextValue() : entry.nextValue();
        boolean upgradeable = entry != null
                && (secondary ? entry.secondaryUpgradeable() : entry.primaryUpgradeable());
        String currentValue = entry == null || emptySecondary ? "-"
                : YoikoRelicValueFormatter.format(effect, current);
        String nextValue = entry == null || emptySecondary ? "-" : !upgradeable
                ? YoikoClientText.text("yoiko_core.ui.relic.effect_fixed")
                : entry.level() >= 10 ? "MAX" : YoikoRelicValueFormatter.format(effect, next);
        String rawEffectName = entry == null ? "-" : emptySecondary
                ? YoikoClientText.text("yoiko_core.ui.relic.secondary_effect_empty")
                : RelicUiText.effectSummary(effect);
        int currentValueLeft = DETAIL_CURRENT_RIGHT_X - font.width(currentValue);
        int availableNameWidth = Math.max(1, Math.min(
                DETAIL_NAME_WIDTH,
                currentValueLeft - DETAIL_NAME_X - DETAIL_NAME_VALUE_GAP
        ));
        String effectName = abbreviated(font, rawEffectName, availableNameWidth);
        int baseColor = entry == null || emptySecondary ? 0xFF9A8268 : 0xFF4E443A;
        graphics.drawString(font, Component.literal(effectName), left + DETAIL_NAME_X, y, baseColor, false);
        RelicScreenRenderSupport.drawRightAligned(graphics, font, Component.literal(currentValue),
                left + DETAIL_CURRENT_RIGHT_X, y,
                entry == null || emptySecondary ? 0xFF9A8268 : 0xFF8F7A63);
        RelicScreenRenderSupport.drawCentered(graphics, font, Component.literal("→"),
                left + DETAIL_ARROW_CENTER_X, y, baseColor);
        RelicScreenRenderSupport.drawRightAligned(graphics, font, Component.literal(nextValue),
                left + DETAIL_NEXT_RIGHT_X, y,
                entry == null || emptySecondary ? 0xFF9A8268 : 0xFF329BC4);
    }

    private static void renderProbabilities(GuiGraphics graphics, Font font, int left, int top,
                                            OpenRelicPayload.Entry entry, boolean protectedAttempt) {
        boolean showChance = entry != null && entry.upgradeable() && entry.level() < 10;
        int startX = left + RIGHT_X + 6;
        int contentWidth = RIGHT_WIDTH - 12;
        Double successChance = showChance ? entry.greatSuccess() + entry.success() : null;
        Boolean canDowngrade = showChance ? !protectedAttempt && entry.downgrade() > 0.0D : null;
        Boolean canDestroy = showChance ? !protectedAttempt && entry.destroy() > 0.0D : null;
        int panelWidth = (contentWidth - OUTCOME_PANEL_GAP) / 2;
        int failureX = startX + panelWidth + OUTCOME_PANEL_GAP;
        int firstY = top + OUTCOME_FIRST_ROW_Y;
        int secondY = top + OUTCOME_SECOND_ROW_Y;

        int successCenter = startX + panelWidth / 2 + SUCCESS_CENTER_OFFSET_X;
        RelicScreenRenderSupport.drawCentered(graphics, font,
                YoikoClientText.tr("yoiko_core.ui.relic.success_chance_only"),
                successCenter, firstY, 0xFF5E554A);
        RelicScreenRenderSupport.drawCentered(graphics, font,
                Component.literal(successChance == null ? "--" : formatPercent(successChance)),
                successCenter, secondY + SUCCESS_VALUE_Y_OFFSET,
                successChance == null ? 0xFF9A8268 : 0xFF2D9292);

        renderPossibility(graphics, font, failureX, firstY + FAILURE_ROW_Y_OFFSET, panelWidth,
                "yoiko_core.ui.relic.destroy_possibility", canDestroy);
        renderPossibility(graphics, font, failureX,
                secondY + FAILURE_ROW_Y_OFFSET + FAILURE_SECOND_ROW_Y_OFFSET, panelWidth,
                "yoiko_core.ui.relic.downgrade_possibility", canDowngrade);
    }

    private static void renderPossibility(GuiGraphics graphics, Font font, int x, int y,
                                          int width, String key, Boolean active) {
        int resultColor = active == null ? 0xFF9A8268 : active ? 0xFFC84D37 : 0xFF329BC4;
        String marker = active == null ? "-" : active ? "O" : "X";
        String label = abbreviated(font, YoikoClientText.text(key),
                width - FAILURE_LABEL_X - FAILURE_MARKER_RIGHT_PADDING - 7);
        graphics.drawString(font, Component.literal(label), x + FAILURE_LABEL_X, y,
                active == null ? 0xFF9A8268 : 0xFF5E554A, false);
        graphics.drawString(font, Component.literal(marker),
                x + width - font.width(marker) - FAILURE_MARKER_RIGHT_PADDING, y, resultColor, false);
    }

    private static ItemStack renderControls(GuiGraphics graphics, Font font, int left, int top,
                                            int mouseX, int mouseY, State state) {
        int materialY = top + MATERIAL_CENTER_Y;
        int protectionX = left + PROTECTION_CENTER_X;
        int upgradeX = left + UPGRADE_BUTTON_CENTER_X;
        int crystalX = left + UPGRADE_CRYSTAL_CENTER_X;
        boolean hasScrap = state.scrapCount() >= state.protectionScrapCost();
        boolean selected = state.useProtection() && hasScrap;
        ItemStack scrap = new ItemStack(YoikoItems.RELIC_SCRAP.get());
        ItemStack crystal = new ItemStack(YoikoItems.RELIC_UPGRADE_CRYSTAL.get());
        ItemStack hovered = ItemStack.EMPTY;
        if (renderMaterial(graphics, font, protectionX, materialY, SCRAP_ICON, scrap,
                state.scrapCount(), hasScrap, selected, protectionInvalidFrame(state.protectionAnimationTicks()),
                mouseX, mouseY)) {
            hovered = scrap;
        }
        if (renderMaterial(graphics, font, crystalX, materialY, UPGRADE_CRYSTAL_ICON, crystal,
                state.upgradeCrystalCount(), state.upgradeCrystalCount() > 0,
                state.upgradeCrystalCount() > 0, SLOT_INVALID, mouseX, mouseY)) {
            hovered = crystal;
        }
        RelicScreenRenderSupport.drawCentered(graphics, font,
                YoikoClientText.tr("yoiko_core.ui.relic.upgrade"),
                upgradeX + UPGRADE_LABEL_X_OFFSET, top + UPGRADE_BUTTON_CENTER_Y - 4,
                state.upgradeAvailable() ? UPGRADE_LABEL_COLOR : DISABLED_UPGRADE_LABEL_COLOR);
        return hovered;
    }

    private static boolean renderMaterial(GuiGraphics graphics, Font font, int centerX, int centerY,
                                          ResourceLocation texture, ItemStack stack, int count,
                                          boolean available, boolean selected,
                                          ResourceLocation invalidBorder, int mouseX, int mouseY) {
        int iconX = centerX - MATERIAL_ICON_SIZE / 2 + MATERIAL_ICON_X_OFFSET;
        int iconY = centerY - MATERIAL_ICON_SIZE / 2;
        if (!available) {
            RenderSystem.setShaderColor(UNAVAILABLE_MATERIAL_BRIGHTNESS,
                    UNAVAILABLE_MATERIAL_BRIGHTNESS, UNAVAILABLE_MATERIAL_BRIGHTNESS, 1.0F);
        }
        try {
            graphics.blit(texture, iconX, iconY, MATERIAL_ICON_SIZE, MATERIAL_ICON_SIZE,
                    0.0F, 0.0F, 16, 16, 16, 16);
        } finally {
            if (!available) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        renderSlotBorder(graphics, centerX, centerY, selected, invalidBorder);
        graphics.renderItemDecorations(font, stack, iconX, iconY,
                Integer.toString(Math.min(999, Math.max(0, count))));
        return insideSlot(mouseX, mouseY, centerX, centerY);
    }

    static boolean insideSlot(int x, int y, int centerX, int centerY) {
        int offset = WORKBENCH_SLOT_BORDER_SIZE / 2;
        return x >= centerX - offset && x < centerX - offset + WORKBENCH_SLOT_BORDER_SIZE
                && y >= centerY - offset && y < centerY - offset + WORKBENCH_SLOT_BORDER_SIZE;
    }

    private static void renderSlotBorder(GuiGraphics graphics, int centerX, int centerY,
                                         boolean selected, ResourceLocation invalidBorder) {
        ResourceLocation texture = selected ? SLOT_VALID : invalidBorder;
        int offset = WORKBENCH_SLOT_BORDER_SIZE / 2;
        graphics.blit(texture, centerX - offset, centerY - offset,
                WORKBENCH_SLOT_BORDER_SIZE, WORKBENCH_SLOT_BORDER_SIZE,
                0.0F, 0.0F, WORKBENCH_SLOT_BORDER_SIZE, WORKBENCH_SLOT_BORDER_SIZE,
                WORKBENCH_SLOT_BORDER_SIZE, WORKBENCH_SLOT_BORDER_SIZE);
    }

    private static ResourceLocation protectionInvalidFrame(int animationTicks) {
        int frame = Math.floorMod(animationTicks / PROTECTION_SLOT_FRAME_TICKS,
                PROTECTION_INVALID_FRAMES.length);
        return PROTECTION_INVALID_FRAMES[frame];
    }

    private static boolean validTarget(OpenRelicPayload.Entry entry) {
        return entry != null && entry.upgradeable() && !entry.locked()
                && entry.equippedSlot() < 0 && entry.level() < 10;
    }

    private static String abbreviated(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String suffix = "…";
        int suffixWidth = font.width(suffix);
        String candidate = text;
        while (!candidate.isEmpty() && font.width(candidate) + suffixWidth > width) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.isEmpty() ? suffix : candidate + suffix;
    }

    private static void drawCenteredSegments(GuiGraphics graphics, Font font,
                                             int centerX, int y, TextSegment... segments) {
        int width = 0;
        for (TextSegment segment : segments) {
            width += font.width(segment.text());
        }
        int x = centerX - width / 2;
        for (TextSegment segment : segments) {
            graphics.drawString(font, segment.text(), x, y, segment.color(), false);
            x += font.width(segment.text());
        }
    }

    private static String formatPercent(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", value);
    }

    record State(OpenRelicPayload.Entry entry, int upgradeCrystalCount, int scrapCount,
                 int protectionScrapCost, boolean useProtection, boolean protectionUsable,
                 boolean upgradeAvailable, int protectionAnimationTicks) {
    }

    private record TextSegment(String text, int color) {
    }
}
