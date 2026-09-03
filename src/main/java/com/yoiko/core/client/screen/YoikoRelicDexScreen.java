package com.yoiko.core.client.screen;

import com.yoiko.core.network.MenuActionPayload;
import com.yoiko.core.network.OpenRelicDexPayload;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class YoikoRelicDexScreen extends Screen {
    private static final int PANEL_WIDTH = YoikoMenuLayout.PANEL_WIDTH;
    private static final int PANEL_HEIGHT = YoikoMenuLayout.PANEL_HEIGHT;

    private static final int LIST_X = YoikoMenuLayout.LEFT_X;
    private static final int LIST_Y = YoikoMenuLayout.CONTENT_Y;
    private static final int LIST_WIDTH = YoikoMenuLayout.LEFT_WIDTH;
    private static final int LIST_HEIGHT = 200;
    private static final int GRID_X = LIST_X + 7;
    private static final int GRID_Y = LIST_Y + 20;
    private static final int GRID_COLUMNS = 6;
    private static final int GRID_ROWS = 6;
    private static final int CARD_SIZE = 24;
    private static final int CARD_GAP = 3;

    private static final int DETAIL_X = YoikoMenuLayout.RIGHT_X;
    private static final int DETAIL_Y = YoikoMenuLayout.CONTENT_Y;
    private static final int DETAIL_WIDTH = YoikoMenuLayout.RIGHT_WIDTH;
    private static final int DETAIL_HEIGHT = 134;
    private static final int FILTER_Y = 146;
    private static final int FILTER_HEIGHT = 76;

    private OpenRelicDexPayload payload;
    private YoikoFocusGrid gridFocus;
    private EditBox searchInput;
    private String localSelectedId = "";

    public YoikoRelicDexScreen(OpenRelicDexPayload payload) {
        super(Component.translatable(payload.title()));
        this.payload = payload;
        this.localSelectedId = payload.selected().relicId();
    }

    @Override
    protected void init() {
        rebuild();
    }

    public void update(OpenRelicDexPayload payload) {
        String previousSelection = localSelectedId;
        this.payload = payload;
        this.localSelectedId = payload.entries().stream().anyMatch(entry -> entry.relicId().equals(previousSelection))
                ? previousSelection : payload.selected().relicId();
        rebuild();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YoikoScreenStyle.renderBackdrop(graphics, this.width, this.height);
        int left = left();
        int top = top();

        YoikoScreenStyle.renderPanel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.drawString(this.font, Component.translatable(payload.title()).withStyle(ChatFormatting.GOLD),
                left + YoikoMenuLayout.LEFT_X, top + YoikoMenuLayout.HEADER_Y, 0xFFFFD36A, false);

        renderPageText(graphics, left, top);
        renderList(graphics, left, top, mouseX, mouseY);
        renderDetails(graphics, left, top);
        renderFilterText(graphics, left, top);
        YoikoScreenStyle.renderWidgets(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        OpenRelicDexPayload.Entry entry = entryAt(mouseX, mouseY);
        if (entry != null) {
            focusGridEntry(payload.entries().indexOf(entry));
            localSelectedId = entry.relicId();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && searchInput != null && searchInput.isFocused()) {
            send(action("relic_dex_search", payload.rarity(), searchInput.getValue()));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void rebuild() {
        boolean restoreGridFocus = gridFocus != null && gridFocus.isFocused();
        int previousGridIndex = gridFocus == null ? 0 : gridFocus.focusedIndex();
        boolean restoreSearchFocus = searchInput != null && searchInput.isFocused();
        String searchValue = searchInput == null ? payload.searchQuery() : searchInput.getValue();
        clearWidgets();
        int left = left();
        int top = top();

        Component pageText = Component.literal((payload.page() + 1) + "/" + payload.totalPages());
        int nextX = left + LIST_X + LIST_WIDTH - 18;
        int textX = nextX - 4 - this.font.width(pageText);
        int prevX = textX - 22;

        YoikoSpriteButton previous = addPageButton(prevX, top + YoikoMenuLayout.HEADER_BUTTON_Y,
                YoikoSpriteButton.Sprite.PAGE_LEFT,
                action("relic_dex_page", payload.rarity(), Math.max(0, payload.page() - 1), localSelectedId, payload.searchQuery()));
        previous.active = payload.page() > 0;
        YoikoSpriteButton next = addPageButton(nextX, top + YoikoMenuLayout.HEADER_BUTTON_Y,
                YoikoSpriteButton.Sprite.PAGE_RIGHT,
                action("relic_dex_page", payload.rarity(), Math.min(payload.totalPages() - 1, payload.page() + 1), localSelectedId, payload.searchQuery()));
        next.active = payload.page() + 1 < payload.totalPages();

        int filterLeft = left + DETAIL_X + 8;
        int filterTop = top + FILTER_Y + 16;
        addRarityTab(filterLeft, filterTop, "COMMON", YoikoClientText.text("yoiko_core.rarity.common"));
        addRarityTab(filterLeft + 80, filterTop, "UNCOMMON", YoikoClientText.text("yoiko_core.rarity.uncommon"));
        addRarityTab(filterLeft, filterTop + 18, "RARE", YoikoClientText.text("yoiko_core.rarity.rare"));
        addRarityTab(filterLeft + 80, filterTop + 18, "EPIC", YoikoClientText.text("yoiko_core.rarity.epic"));
        addRarityTab(filterLeft, filterTop + 36, "LEGENDARY", YoikoClientText.text("yoiko_core.rarity.legendary"));
        addRarityTab(filterLeft + 80, filterTop + 36, "MYSTIC", YoikoClientText.text("yoiko_core.rarity.mystic"));
        addRarityTab(filterLeft + 40, filterTop + 54, "RADIANT", YoikoClientText.text("yoiko_core.rarity.radiant"));
        gridFocus = this.addRenderableWidget(new YoikoFocusGrid(
                left + GRID_X, top + GRID_Y, GRID_COLUMNS, GRID_ROWS, CARD_SIZE, CARD_SIZE,
                CARD_SIZE + CARD_GAP, CARD_SIZE + CARD_GAP,
                () -> payload.entries().size(), this::activateGridEntry));
        String selectedId = localSelectedId;
        int selectedIndex = -1;
        for (int index = 0; index < payload.entries().size(); index++) {
            if (payload.entries().get(index).relicId().equals(selectedId)) {
                selectedIndex = index;
                break;
            }
        }
        gridFocus.setFocusedIndex(restoreGridFocus ? previousGridIndex : Math.max(0, selectedIndex));
        searchInput = new EditBox(this.font, left + LIST_X + 58, top + LIST_Y + 4,
                LIST_WIDTH - 66, 14, YoikoClientText.tr("yoiko_core.ui.search"));
        searchInput.setMaxLength(64);
        searchInput.setValue(searchValue);
        searchInput.setHint(YoikoClientText.tr("yoiko_core.ui.search_hint"));
        this.addRenderableWidget(searchInput);
        if (restoreGridFocus) {
            setFocused(gridFocus);
        } else if (restoreSearchFocus) {
            setFocused(searchInput);
        }
    }

    private void activateGridEntry(int index) {
        if (index >= 0 && index < payload.entries().size()) {
            localSelectedId = payload.entries().get(index).relicId();
        }
    }

    private void focusGridEntry(int index) {
        if (gridFocus != null && index >= 0) {
            setFocused(gridFocus);
            gridFocus.setFocusedIndex(index);
        }
    }

    private void renderPageText(GuiGraphics graphics, int left, int top) {
        Component pageText = Component.literal((payload.page() + 1) + "/" + payload.totalPages()).withStyle(ChatFormatting.AQUA);
        int nextX = left + LIST_X + LIST_WIDTH - 18;
        int textX = nextX - 4 - this.font.width(pageText);
        graphics.drawString(this.font, pageText, textX, top + YoikoMenuLayout.HEADER_Y, 0xFFFFFFFF, false);
    }

    private void renderList(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        YoikoScreenStyle.renderSubPanel(graphics, left + LIST_X, top + LIST_Y, LIST_WIDTH, LIST_HEIGHT);
        graphics.drawString(this.font, YoikoClientText.tr("yoiko_core.dex.list").withStyle(ChatFormatting.AQUA),
                left + LIST_X + 8, top + LIST_Y + 7, 0xFFFFFFFF, false);
        for (int i = 0; i < GRID_COLUMNS * GRID_ROWS; i++) {
            int column = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            int x = left + GRID_X + column * (CARD_SIZE + CARD_GAP);
            int y = top + GRID_Y + row * (CARD_SIZE + CARD_GAP);
            OpenRelicDexPayload.Entry entry = i < payload.entries().size() ? payload.entries().get(i) : null;
            renderCard(graphics, entry, x, y, mouseX, mouseY);
        }
        if (payload.entries().isEmpty()) {
            graphics.drawCenteredString(this.font,
                    YoikoClientText.tr(payload.searchQuery().isBlank()
                            ? "yoiko_core.ui.empty.category" : "yoiko_core.ui.empty.search"),
                    left + LIST_X + LIST_WIDTH / 2,
                    top + LIST_Y + LIST_HEIGHT / 2,
                    0xFFB8B8B8);
        }
    }

    private void renderCard(GuiGraphics graphics, OpenRelicDexPayload.Entry entry, int x, int y, int mouseX, int mouseY) {
        boolean filled = entry != null && !entry.relicId().isBlank();
        boolean hovered = filled && mouseX >= x && mouseX < x + CARD_SIZE && mouseY >= y && mouseY < y + CARD_SIZE;
        boolean selected = filled && entry.relicId().equals(localSelectedId);
        int border = selected ? 0xFF58F2FF : hovered ? 0xFFEAD7B0 : rarityColor(entry == null ? payload.rarity() : entry.rarity());
        if (selected || hovered) {
            graphics.fill(x - 2, y - 2, x + CARD_SIZE + 2, y + CARD_SIZE + 2, border);
        }
        graphics.fill(x, y, x + CARD_SIZE, y + CARD_SIZE, filled ? 0xFF343A3B : 0xFF242829);
        graphics.fill(x + 1, y + 1, x + CARD_SIZE - 1, y + CARD_SIZE - 1, filled ? 0xFF8B9697 : 0xFF4A5557);
        graphics.fill(x + 3, y + 3, x + CARD_SIZE - 3, y + CARD_SIZE - 3, filled ? 0xFF404A4C : 0xFF303637);
        if (filled) {
            YoikoScreenStyle.renderRelicIcon(graphics, x + 4, y + 4, 16, entry.rarity());
        }
    }

    private void renderDetails(GuiGraphics graphics, int left, int top) {
        int x = left + DETAIL_X;
        int y = top + DETAIL_Y;
        YoikoScreenStyle.renderDetailFrame(graphics, x, y, DETAIL_WIDTH, DETAIL_HEIGHT);

        OpenRelicDexPayload.Entry entry = selectedEntry();
        graphics.drawString(this.font, YoikoClientText.tr("yoiko_core.ui.relic.detail").withStyle(ChatFormatting.GOLD),
                x + 8, y + 8, 0xFFFFD36A, false);
        if (entry.relicId().isBlank()) {
            graphics.drawString(this.font, YoikoClientText.tr("yoiko_core.ui.no_selection").withStyle(ChatFormatting.GRAY),
                    x + 12, y + 44, 0xFFD0D0D0, false);
            return;
        }

        YoikoScreenStyle.renderRelicIcon(graphics, x + 14, y + 28, 42, entry.rarity());
        Component name = YoikoClientText.data(entry.displayName()).copy().withStyle(ChatFormatting.GOLD);
        graphics.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(name.getString(), 92)).withStyle(ChatFormatting.GOLD),
                x + 62, y + 28, 0xFFFFD36A, false);
        graphics.drawString(this.font, Component.literal(rarityLabel(entry.rarity()) + " / " + formatValue(entry.value()))
                        .withStyle(ChatFormatting.AQUA),
                x + 62, y + 42, 0xFFD7FFFF, false);
        drawWrapped(graphics, Component.literal(effectText(entry.effect())).withStyle(ChatFormatting.GRAY),
                x + 62, y + 56, 92, 3, 0xFFD0D0D0);

        drawMetric(graphics, x + 12, y + 94, YoikoClientText.text("yoiko_core.dex.individual_rate"), entry.individualRate());
        drawMetric(graphics, x + 86, y + 94, YoikoClientText.text("yoiko_core.dex.rarity_rate"), entry.rarityRate());
        graphics.drawString(this.font, Component.literal("+ " + formatValue(entry.upgradeBonus())).withStyle(ChatFormatting.LIGHT_PURPLE),
                x + 12, y + 118, 0xFFFFB7FF, false);
    }

    private void renderFilterText(GuiGraphics graphics, int left, int top) {
        int x = left + DETAIL_X;
        int y = top + FILTER_Y;
        YoikoScreenStyle.renderDetailFrame(graphics, x, y, DETAIL_WIDTH, FILTER_HEIGHT);
        Component filterTitle;
        if ("all".equals(payload.appraisalCategory())) {
            filterTitle = YoikoClientText.tr("yoiko_core.dex.rarity_filter");
        } else {
            String key = "RADIANT".equals(payload.rarity())
                    ? "yoiko_core.dex.focus_filter_radiant" : "yoiko_core.dex.focus_filter";
            filterTitle = YoikoClientText.tr(
                    key,
                    YoikoClientText.tr("yoiko_core.relic.appraisal_category." + payload.appraisalCategory())
            );
        }
        graphics.drawCenteredString(this.font, filterTitle.copy().withStyle(ChatFormatting.AQUA),
                x + DETAIL_WIDTH / 2, y + 6, 0xFFFFFFFF);
    }

    private void drawMetric(GuiGraphics graphics, int x, int y, String label, String value) {
        graphics.drawString(this.font, Component.literal(label).withStyle(ChatFormatting.AQUA), x, y, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal(value).withStyle(ChatFormatting.WHITE), x, y + 11, 0xFFFFFFFF, false);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int maxLines, int color) {
        int lineY = y;
        int lines = 0;
        for (FormattedCharSequence sequence : this.font.split(text, width)) {
            if (lines >= maxLines) {
                break;
            }
            graphics.drawString(this.font, sequence, x, lineY, color, false);
            lineY += 10;
            lines++;
        }
        return lineY;
    }

    private OpenRelicDexPayload.Entry entryAt(double mouseX, double mouseY) {
        int left = left();
        int top = top();
        int localX = (int) mouseX - (left + GRID_X);
        int localY = (int) mouseY - (top + GRID_Y);
        if (localX < 0 || localY < 0) {
            return null;
        }
        int step = CARD_SIZE + CARD_GAP;
        int column = localX / step;
        int row = localY / step;
        if (column < 0 || column >= GRID_COLUMNS || row < 0 || row >= GRID_ROWS) {
            return null;
        }
        if (localX % step >= CARD_SIZE || localY % step >= CARD_SIZE) {
            return null;
        }
        int index = row * GRID_COLUMNS + column;
        return index < payload.entries().size() ? payload.entries().get(index) : null;
    }

    private void addRarityTab(int x, int y, String rarity, String label) {
        boolean active = payload.rarity().equals(rarity);
        this.addRenderableWidget(YoikoButton.create(
                x,
                y,
                74,
                16,
                Component.literal(active ? "> " + label : label),
                button -> send(action("relic_dex_rarity", rarity, payload.searchQuery()))
        ));
    }

    private OpenRelicDexPayload.Entry selectedEntry() {
        for (OpenRelicDexPayload.Entry entry : payload.entries()) {
            if (entry.relicId().equals(localSelectedId)) {
                return entry;
            }
        }
        return OpenRelicDexPayload.Entry.empty();
    }

    private YoikoSpriteButton addPageButton(int x, int y, YoikoSpriteButton.Sprite sprite, String action) {
        YoikoSpriteButton button = YoikoSpriteButton.create(x, y, Component.literal(action), sprite, ignored -> send(action));
        this.addRenderableWidget(button);
        return button;
    }

    private void send(String action) {
        PacketDistributor.sendToServer(ClientMenuSession.action(action));
    }

    private int left() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int top() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    private static String action(String command, Object... parts) {
        StringBuilder builder = new StringBuilder(command);
        for (Object part : parts) {
            builder.append('|').append(part);
        }
        return builder.toString();
    }

    private static String formatValue(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String effectText(String effect) {
        return switch (effect) {
            case "yoiko_bag_slots" -> YoikoClientText.text("yoiko_core.effect.yoiko_bag_slots");
            case "pokemon_friendship_gain_bonus" -> YoikoClientText.text("yoiko_core.effect.pokemon_friendship_gain_bonus");
            case "pokemon_exp_multiplier_bonus" -> YoikoClientText.text("yoiko_core.effect.pokemon_exp_multiplier_bonus");
            case "pokemon_catch_rate_bonus" -> YoikoClientText.text("yoiko_core.effect.pokemon_catch_rate_bonus");
            case "natural_shiny_chance_bonus" -> YoikoClientText.text("yoiko_core.effect.natural_shiny_chance_bonus");
            case "player_exp_multiplier_bonus" -> YoikoClientText.text("yoiko_core.effect.player_exp_multiplier_bonus");
            case "player_movement_speed_bonus" -> YoikoClientText.text("yoiko_core.effect.player_movement_speed_bonus");
            case "player_max_health_bonus" -> YoikoClientText.text("yoiko_core.effect.player_max_health_bonus");
            case "block_interaction_range_bonus" -> YoikoClientText.text("yoiko_core.effect.block_interaction_range_bonus");
            case "critical_strike_chance_bonus" -> YoikoClientText.text("yoiko_core.effect.critical_strike_chance_bonus");
            case "critical_strike_damage_bonus" -> YoikoClientText.text("yoiko_core.effect.critical_strike_damage_bonus");
            case "low_health_damage_bonus" -> YoikoClientText.text("yoiko_core.effect.low_health_damage_bonus");
            case "riposte_damage_bonus" -> YoikoClientText.text("yoiko_core.effect.riposte_damage_bonus");
            case "heavy_hit_damage_reduction" -> YoikoClientText.text("yoiko_core.effect.heavy_hit_damage_reduction");
            case "ranged_distance_damage_bonus" -> YoikoClientText.text("yoiko_core.effect.ranged_distance_damage_bonus");
            case "harmful_effect_duration_reduction" -> YoikoClientText.text("yoiko_core.effect.harmful_effect_duration_reduction");
            case "low_health_escape_speed_bonus" -> YoikoClientText.text("yoiko_core.effect.low_health_escape_speed_bonus");
            case "rear_attack_damage_bonus" -> YoikoClientText.text("yoiko_core.effect.rear_attack_damage_bonus");
            case "full_health_first_strike_bonus" -> YoikoClientText.text("yoiko_core.effect.full_health_first_strike_bonus");
            case "marked_attacker_damage_bonus" -> YoikoClientText.text("yoiko_core.effect.marked_attacker_damage_bonus");
            case "sprinting_knockback_bonus" -> YoikoClientText.text("yoiko_core.effect.sprinting_knockback_bonus");
            case "frost_slow_chance" -> YoikoClientText.text("yoiko_core.effect.frost_slow_chance");
            case "kill_attack_speed_bonus" -> YoikoClientText.text("yoiko_core.effect.kill_attack_speed_bonus");
            case "out_of_combat_damage_absorption" -> YoikoClientText.text("yoiko_core.effect.out_of_combat_damage_absorption");
            case "waystone_travel_discount" -> YoikoClientText.text("yoiko_core.effect.waystone_travel_discount");
            case "mega_shard_find_bonus" -> YoikoClientText.text("yoiko_core.effect.mega_shard_find_bonus");
            case "player_knockback_resistance_bonus" -> YoikoClientText.text("yoiko_core.effect.player_knockback_resistance_bonus");
            case "player_armor_toughness_bonus" -> YoikoClientText.text("yoiko_core.effect.player_armor_toughness_bonus");
            case "player_luck_bonus" -> YoikoClientText.text("yoiko_core.effect.player_luck_bonus");
            case "pokemon_common_spawn_weight_bonus" -> YoikoClientText.text("yoiko_core.effect.pokemon_common_spawn_weight_bonus");
            case "pokemon_rare_spawn_weight_bonus" -> YoikoClientText.text("yoiko_core.effect.pokemon_rare_spawn_weight_bonus");
            case "pokemon_ultra_rare_spawn_weight_bonus" -> YoikoClientText.text("yoiko_core.effect.pokemon_ultra_rare_spawn_weight_bonus");
            case "totemless_revive_chance" -> YoikoClientText.text("yoiko_core.effect.totemless_revive_chance");
            case "mounted_pokemon_speed_bonus" -> YoikoClientText.text("yoiko_core.effect.mounted_pokemon_speed_bonus");
            case "failed_pokeball_return_chance" -> YoikoClientText.text("yoiko_core.effect.failed_pokeball_return_chance");
            case "battle_victory_heal_percent" -> YoikoClientText.text("yoiko_core.effect.battle_victory_heal_percent");
            case "battle_victory_status_cure_chance" -> YoikoClientText.text("yoiko_core.effect.battle_victory_status_cure_chance");
            case "battle_victory_move_pp_restore_chance" -> YoikoClientText.text("yoiko_core.effect.battle_victory_move_pp_restore_chance");
            case "quality_food_grade_upgrade_chance" -> YoikoClientText.text("yoiko_core.effect.quality_food_grade_upgrade_chance");
            case "treasure_rabbit_spawn_chance_bonus" -> YoikoClientText.text("yoiko_core.effect.treasure_rabbit_spawn_chance_bonus");
            case "radiant_high_air_step_count" -> YoikoClientText.text("yoiko_core.effect.radiant_high_air_step_count");
            case "radiant_double_air_step_count" -> YoikoClientText.text("yoiko_core.effect.radiant_double_air_step_count");
            case "mercy_hp_floor" -> YoikoClientText.text("yoiko_core.effect.mercy_hp_floor");
            case "equipped_relic_level_bonus" -> YoikoClientText.text("yoiko_core.effect.equipped_relic_level_bonus");
            case "weakest_relic_level_bonus" -> YoikoClientText.text("yoiko_core.effect.weakest_relic_level_bonus");
            case "chromatic_contract_level_shift" -> YoikoClientText.text("yoiko_core.effect.chromatic_contract_level_shift");
            case "satiation_absorption_conversion" -> YoikoClientText.text("yoiko_core.effect.satiation_absorption_conversion");
            case "fire_cocoon_duration" -> YoikoClientText.text("yoiko_core.effect.fire_cocoon_duration");
            case "combo_strike_damage_bonus" -> YoikoClientText.text("yoiko_core.effect.combo_strike_damage_bonus");
            default -> effect.isBlank() ? YoikoClientText.text("yoiko_core.effect.none") : effect;
        };
    }

    private static int rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> 0xFF6BFF8D;
            case "RARE" -> 0xFF6FA8FF;
            case "EPIC" -> 0xFFFF73FF;
            case "LEGENDARY" -> 0xFFFFD36A;
            case "MYSTIC", "UNIQUE" -> 0xFFFF6D6D;
            case "RADIANT" -> 0xFF9DF2FF;
            default -> 0xFFD0D0D0;
        };
    }

    private static String rarityLabel(String rarity) {
        return switch (rarity) {
            case "UNCOMMON" -> YoikoClientText.text("yoiko_core.rarity.uncommon");
            case "RARE" -> YoikoClientText.text("yoiko_core.rarity.rare");
            case "EPIC" -> YoikoClientText.text("yoiko_core.rarity.epic");
            case "LEGENDARY" -> YoikoClientText.text("yoiko_core.rarity.legendary");
            case "MYSTIC", "UNIQUE" -> YoikoClientText.text("yoiko_core.rarity.mystic");
            case "RADIANT" -> YoikoClientText.text("yoiko_core.rarity.radiant");
            default -> YoikoClientText.text("yoiko_core.rarity.common");
        };
    }
}
