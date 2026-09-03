package com.yoiko.core.client.screen;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.client.relic.RelicEffectPresentation;
import com.yoiko.core.network.OpenRelicPayload;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.relic.RelicEffectRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.fml.ModList;

/** Builds and renders relic tooltips independently of the storage/workbench screen. */
final class RelicTooltipRenderer {
    private static final int MIN_TEXT_WIDTH = 180;
    private static final int MAX_TEXT_WIDTH = 220;
    private static final int SCREEN_MARGIN = 12;
    private static final ResourceLocation MARK_FONT = YoikoServerCore.id("relic_tooltip_marks");
    private static final String ABSENT_EFFECT_MARK_GLYPH = "\uE201";
    private static final String DISMANTLE_MARK_GLYPH = "\uE206";

    private RelicTooltipRenderer() {
    }

    static void render(GuiGraphics graphics, Font font, int screenWidth,
                       OpenRelicPayload.Entry entry, int mouseX, int mouseY) {
        if (ModList.get().isLoaded("obscure_tooltips")) {
            graphics.renderTooltip(font, tooltipStack(entry), mouseX, mouseY);
            return;
        }

        List<FormattedCharSequence> lines = new ArrayList<>();
        int textWidth = textWidth(screenWidth, mouseX);
        String title = "+" + entry.level() + " " + YoikoClientText.dataText(entry.displayName());
        append(lines, font, Component.literal(title)
                .withColor(RelicUiText.rarityColor(entry.rarity()) & 0x00FFFFFF), textWidth);
        MutableComponent rarityLine = Component.literal(RelicUiText.rarityLabel(entry.rarity()))
                .withStyle(ChatFormatting.GRAY);
        boolean singleEffectRelic = isCommon(entry.rarity()) && entry.secondaryEffect().isBlank();
        if (singleEffectRelic) {
            rarityLine.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(YoikoClientText.tr("yoiko_core.ui.relic.tooltip.single_effect")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        append(lines, font, rarityLine, textWidth);
        lines.add(Component.empty().getVisualOrderText());
        append(lines, font, YoikoClientText.tr("yoiko_core.ui.relic.tooltip.when_equipped")
                .withColor(0xD9B878), textWidth);
        appendEffect(lines, font, entry.effect(), entry.value(), entry.primaryEffectSuppressed(), textWidth);
        if (!singleEffectRelic && entry.secondaryEffect().isBlank()) {
            append(lines, font, Component.empty()
                    .append(marker(ABSENT_EFFECT_MARK_GLYPH, 0x777777))
                    .append(Component.literal(" "))
                    .append(YoikoClientText.tr("yoiko_core.ui.relic.secondary_effect_empty")
                            .withStyle(ChatFormatting.GRAY)), textWidth);
        } else if (!entry.secondaryEffect().isBlank()) {
            appendEffect(lines, font, entry.secondaryEffect(), entry.secondaryValue(),
                    entry.secondaryEffectSuppressed(), textWidth);
        }
        lines.add(Component.empty().getVisualOrderText());
        append(lines, font, dismantleLine(entry.scrapValue()), textWidth);
        graphics.renderTooltip(font, lines, mouseX, mouseY);
    }

    private static ItemStack tooltipStack(OpenRelicPayload.Entry entry) {
        ItemStack stack = displayStack(entry.rarity());
        String title = "+" + entry.level() + " " + YoikoClientText.dataText(entry.displayName());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(style -> style
                .withColor(RelicUiText.rarityColor(entry.rarity()) & 0x00FFFFFF)
                .withItalic(false)));

        List<Component> lore = new ArrayList<>();
        boolean singleEffectRelic = isCommon(entry.rarity()) && entry.secondaryEffect().isBlank();
        lore.add(YoikoClientText.tr("yoiko_core.ui.relic.tooltip.when_equipped")
                .withStyle(style -> style.withColor(0xD9B878).withItalic(false)));
        lore.addAll(effectComponents(entry.effect(), entry.value(), entry.primaryEffectSuppressed()));
        if (!singleEffectRelic && entry.secondaryEffect().isBlank()) {
            lore.add(Component.empty()
                    .append(marker(ABSENT_EFFECT_MARK_GLYPH, 0x777777))
                    .append(Component.literal(" "))
                    .append(YoikoClientText.tr("yoiko_core.ui.relic.secondary_effect_empty")
                            .withStyle(style -> style.withColor(0x777777).withItalic(false))));
        } else if (!entry.secondaryEffect().isBlank()) {
            lore.addAll(effectComponents(entry.secondaryEffect(), entry.secondaryValue(),
                    entry.secondaryEffectSuppressed()));
        }
        lore.add(Component.empty());
        lore.add(dismantleLine(entry.scrapValue()));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack displayStack(String rarity) {
        return switch (rarity == null ? "" : rarity.toUpperCase(Locale.ROOT)) {
            case "RADIANT" -> YoikoItems.RELIC_DISPLAY_RADIANT.toStack();
            case "MYSTIC", "UNIQUE" -> YoikoItems.RELIC_DISPLAY_MYSTIC.toStack();
            case "LEGENDARY" -> YoikoItems.RELIC_DISPLAY_LEGENDARY.toStack();
            case "EPIC" -> YoikoItems.RELIC_DISPLAY_EPIC.toStack();
            case "RARE" -> YoikoItems.RELIC_DISPLAY_RARE.toStack();
            case "UNCOMMON" -> YoikoItems.RELIC_DISPLAY_UNCOMMON.toStack();
            default -> YoikoItems.RELIC_DISPLAY_COMMON.toStack();
        };
    }

    private static void appendEffect(List<FormattedCharSequence> lines, Font font, String effect,
                                     double value, boolean suppressed, int textWidth) {
        for (Component line : effectComponents(effect, value, suppressed)) {
            append(lines, font, line, textWidth);
        }
    }

    private static List<Component> effectComponents(String effect, double value, boolean suppressed) {
        List<Component> lines = new ArrayList<>();
        int markerColor = suppressed ? 0x666666 : RelicEffectPresentation.markerColor(effect);
        lines.add(Component.empty()
                .append(marker(RelicEffectPresentation.markerGlyph(effect), markerColor))
                .append(Component.literal(" "))
                .append(Component.literal(RelicUiText.effectSummary(effect))
                        .withStyle(style -> style
                                .withColor(suppressed ? 0x777777 : 0x67E5E8)
                                .withItalic(false))));
        if (suppressed) {
            lines.add(Component.literal("  ").append(
                    YoikoClientText.tr("yoiko_core.ui.relic.duplicate_effect_inactive")
                            .withStyle(style -> style.withColor(0x555555).withItalic(false))));
        }
        lines.add(Component.literal("  ").append(effectDescription(effect, value, suppressed)));
        int cooldownSeconds = cooldownSeconds(effect);
        if (cooldownSeconds > 0) {
            lines.add(Component.literal("  ").append(
                    YoikoClientText.tr("yoiko_core.ui.relic.tooltip.cooldown", cooldownSeconds)
                            .withStyle(style -> style.withColor(0x77706A).withItalic(false))));
        }
        return lines;
    }

    private static Component effectDescription(String effect, double value, boolean suppressed) {
        Component formattedValue = Component.literal(YoikoRelicValueFormatter.format(effect, value))
                .withStyle(style -> style
                        .withColor(suppressed ? 0x555555 : 0x55E5E8)
                        .withItalic(false));
        Component description = RelicEffectRegistry.contains(effect)
                ? YoikoClientText.tr("yoiko_core.effect_value." + effect, formattedValue)
                : Component.literal(RelicUiText.effectName(effect) + " ").append(formattedValue);
        return description.copy().withStyle(style -> style
                .withColor(suppressed ? 0x555555 : 0xB8B2AC)
                .withItalic(false));
    }

    private static Component dismantleLine(int scrapValue) {
        return Component.empty()
                .append(marker(DISMANTLE_MARK_GLYPH, 0xE3B84D))
                .append(Component.literal(" "))
                .append(YoikoClientText.tr("yoiko_core.ui.relic.tooltip.dismantle",
                                Component.literal(Integer.toString(scrapValue))
                                        .withStyle(style -> style.withColor(0xFFCC55).withItalic(false)))
                        .withStyle(style -> style.withColor(0xA8A19A).withItalic(false)));
    }

    private static MutableComponent marker(String glyph, int color) {
        return Component.literal(glyph).withStyle(style -> style
                .withFont(MARK_FONT).withColor(color).withItalic(false));
    }

    private static void append(List<FormattedCharSequence> lines, Font font,
                               Component text, int width) {
        lines.addAll(font.split(text, width));
    }

    private static int textWidth(int screenWidth, int mouseX) {
        int screenLimit = Math.max(1, screenWidth - SCREEN_MARGIN * 2);
        int widerSide = Math.max(mouseX - SCREEN_MARGIN, screenWidth - mouseX - SCREEN_MARGIN);
        int minimum = Math.min(MIN_TEXT_WIDTH, screenLimit);
        int available = Math.min(Math.max(1, widerSide), screenLimit);
        return Math.max(minimum, Math.min(MAX_TEXT_WIDTH, available));
    }

    private static boolean isCommon(String rarity) {
        return rarity != null && rarity.equalsIgnoreCase("COMMON");
    }

    private static int cooldownSeconds(String effect) {
        return switch (effect) {
            case "riposte_damage_bonus", "kill_attack_speed_bonus" -> 8;
            case "heavy_hit_damage_reduction", "low_health_escape_speed_bonus",
                    "out_of_combat_damage_absorption" -> 30;
            case "frost_slow_chance" -> 3;
            default -> 0;
        };
    }
}
