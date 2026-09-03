package com.yoiko.core.client.screen;

import com.yoiko.core.gacha.PokemonNameFormatter;
import com.yoiko.core.network.MenuActionPayload;
import com.yoiko.core.network.OpenDexPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class YoikoDexScreen extends Screen {
    private static final int PANEL_WIDTH = YoikoMenuLayout.PANEL_WIDTH;
    private static final int PANEL_HEIGHT = YoikoMenuLayout.PANEL_HEIGHT;
    private static final int FLOATING_X = YoikoMenuLayout.RIGHT_X;
    private static final int FLOATING_WIDTH = YoikoMenuLayout.RIGHT_WIDTH;

    private static final int DETAIL_X = YoikoMenuLayout.LEFT_X;
    private static final int DETAIL_Y = YoikoMenuLayout.CONTENT_Y;
    private static final int DETAIL_WIDTH = YoikoMenuLayout.LEFT_WIDTH;
    private static final int DETAIL_HEIGHT = 90;
    private static final int PORTRAIT_X = DETAIL_X + 10;
    private static final int PORTRAIT_Y = DETAIL_Y + 25;
    private static final int PORTRAIT_WIDTH = 82;
    private static final int PORTRAIT_HEIGHT = 52;

    private static final int GRID_PANEL_X = YoikoMenuLayout.LEFT_X;
    private static final int GRID_PANEL_Y = 122;
    private static final int GRID_PANEL_WIDTH = YoikoMenuLayout.LEFT_WIDTH;
    private static final int GRID_PANEL_HEIGHT = 124;
    private static final int GRID_X = GRID_PANEL_X + 8;
    private static final int GRID_Y = GRID_PANEL_Y + 8;
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_ROWS = 3;
    private static final int CARD_WIDTH = 38;
    private static final int CARD_HEIGHT = 36;
    private static final int CARD_GAP = 4;

    private static final int FILTER_Y = YoikoMenuLayout.CONTENT_Y;
    private static final int FILTER_HEIGHT = 96;
    private static final int PAGE_PANEL_Y = 126;
    private static final int PAGE_PANEL_HEIGHT = 54;
    private static final int PAGE_CONTROLS_Y = PAGE_PANEL_Y + 20;

    private OpenDexPayload payload;
    private YoikoFocusGrid gridFocus;
    private EditBox searchInput;
    private String localSelectedSpecies = "";

    public YoikoDexScreen(OpenDexPayload payload) {
        super(Component.translatable(payload.title()));
        this.payload = payload;
        this.localSelectedSpecies = payload.selectedSpecies();
    }

    @Override
    protected void init() {
        rebuild();
    }

    public void update(OpenDexPayload payload) {
        String previousSelection = localSelectedSpecies;
        this.payload = payload;
        this.localSelectedSpecies = payload.entries().contains(previousSelection)
                ? previousSelection : payload.selectedSpecies();
        rebuild();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YoikoScreenStyle.renderBackdrop(graphics, this.width, this.height);
        int left = left();
        int top = top();

        YoikoScreenStyle.renderPanel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        YoikoScreenStyle.renderDetailFrame(graphics, left + FLOATING_X, top + FILTER_Y, FLOATING_WIDTH, FILTER_HEIGHT);
        YoikoScreenStyle.renderDetailFrame(graphics, left + FLOATING_X, top + PAGE_PANEL_Y, FLOATING_WIDTH, PAGE_PANEL_HEIGHT);

        graphics.drawString(this.font, Component.translatable(payload.title()), left + YoikoMenuLayout.LEFT_X, top + YoikoMenuLayout.HEADER_Y, 0xFFEAD7B0, false);
        renderDetails(graphics, left, top, partialTick);
        renderGrid(graphics, left, top, mouseX, mouseY, partialTick);
        renderPageText(graphics, left, top);
        renderFloatingText(graphics, left, top);
        YoikoScreenStyle.renderWidgets(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        String species = speciesAt(mouseX, mouseY);
        if (species != null) {
            focusGridEntry(payload.entries().indexOf(species));
            selectLocally(species);
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
            send(action("dex_search", payload.gachaType(), payload.rarity(), searchInput.getValue()));
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
        int x = left + FLOATING_X + 8;

        addRarityTab(x, top + FILTER_Y + 14, "common", YoikoClientText.text("yoiko_core.dex.rarity.common"));
        addRarityTab(x, top + FILTER_Y + 34, "sub_legendary", YoikoClientText.text("yoiko_core.dex.rarity.sub_legendary"));
        addRarityTab(x, top + FILTER_Y + 54, "mythical", YoikoClientText.text("yoiko_core.dex.rarity.mythical"));
        addRarityTab(x, top + FILTER_Y + 74, "legendary", YoikoClientText.text("yoiko_core.dex.rarity.legendary"));

        int pageCenter = left + FLOATING_X + FLOATING_WIDTH / 2;
        YoikoSpriteButton previous = addPageButton(pageCenter - 39, top + PAGE_CONTROLS_Y, YoikoSpriteButton.Sprite.PAGE_LEFT,
                action("dex_page", payload.gachaType(), payload.rarity(), Math.max(0, payload.page() - 1), localSelectedSpecies, payload.searchQuery()));
        previous.active = payload.page() > 0;
        YoikoSpriteButton next = addPageButton(pageCenter + 21, top + PAGE_CONTROLS_Y, YoikoSpriteButton.Sprite.PAGE_RIGHT,
                action("dex_page", payload.gachaType(), payload.rarity(), Math.min(payload.totalPages() - 1, payload.page() + 1), localSelectedSpecies, payload.searchQuery()));
        next.active = payload.page() + 1 < payload.totalPages();
        gridFocus = this.addRenderableWidget(new YoikoFocusGrid(
                left + GRID_X, top + GRID_Y, GRID_COLUMNS, GRID_ROWS, CARD_WIDTH, CARD_HEIGHT,
                CARD_WIDTH + CARD_GAP, CARD_HEIGHT + CARD_GAP,
                () -> payload.entries().size(), this::activateGridEntry));
        gridFocus.setFocusedIndex(restoreGridFocus
                ? previousGridIndex
                : Math.max(0, payload.entries().indexOf(localSelectedSpecies)));
        searchInput = new EditBox(this.font, left + GRID_PANEL_X + 67, top + GRID_PANEL_Y - 16,
                GRID_PANEL_WIDTH - 71, 14, YoikoClientText.tr("yoiko_core.ui.search"));
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
            selectLocally(payload.entries().get(index));
        }
    }

    private void selectLocally(String species) {
        localSelectedSpecies = species;
    }

    private void focusGridEntry(int index) {
        if (gridFocus != null && index >= 0) {
            setFocused(gridFocus);
            gridFocus.setFocusedIndex(index);
        }
    }

    private void renderDetails(GuiGraphics graphics, int left, int top, float partialTick) {
        int x = left + DETAIL_X;
        int y = top + DETAIL_Y;
        YoikoScreenStyle.renderSubPanel(graphics, x, y, DETAIL_WIDTH, DETAIL_HEIGHT);

        String selected = localSelectedSpecies;
        Component title = selected.isBlank()
                ? YoikoClientText.tr("yoiko_core.ui.no_selection")
                : PokemonNameFormatter.localizedName(selected);
        graphics.fill(left + DETAIL_X + 102, y + 10, left + DETAIL_X + DETAIL_WIDTH - 8, y + 27, 0xAAE6FFFF);
        String titleText = this.font.plainSubstrByWidth(title.getString(), 66);
        graphics.drawString(this.font, Component.literal(titleText).withStyle(ChatFormatting.DARK_GRAY), left + DETAIL_X + 108, y + 15, 0x222222, false);

        int portraitX = left + PORTRAIT_X;
        int portraitY = top + PORTRAIT_Y;
        graphics.fill(portraitX, portraitY, portraitX + PORTRAIT_WIDTH, portraitY + PORTRAIT_HEIGHT, 0x8834C8D4);
        graphics.fill(portraitX + 10, portraitY + PORTRAIT_HEIGHT - 18, portraitX + PORTRAIT_WIDTH - 10, portraitY + PORTRAIT_HEIGHT - 6, 0x99DDFBFF);
        YoikoPokemonModelRenderer.render(graphics, selected, portraitX + 8, portraitY + 4, PORTRAIT_WIDTH - 16, PORTRAIT_HEIGHT - 8, partialTick);

        int infoX = left + DETAIL_X + 108;
        int infoY = y + 36;
        drawRate(graphics, infoX, infoY, YoikoClientText.text("yoiko_core.dex.individual_rate"), payload.selectedSpeciesRate());
        drawRate(graphics, infoX, infoY + 25, YoikoClientText.text("yoiko_core.dex.rarity_rate"), payload.selectedRarityRate());
    }

    private void renderGrid(GuiGraphics graphics, int left, int top, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(this.font, YoikoClientText.tr("yoiko_core.dex.list").withStyle(ChatFormatting.AQUA), left + GRID_PANEL_X + 4, top + GRID_PANEL_Y - 11, 0xFFFFFF, false);
        YoikoScreenStyle.renderSubPanel(graphics, left + GRID_PANEL_X, top + GRID_PANEL_Y, GRID_PANEL_WIDTH, GRID_PANEL_HEIGHT);
        for (int i = 0; i < GRID_COLUMNS * GRID_ROWS; i++) {
            int column = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            int x = left + GRID_X + column * (CARD_WIDTH + CARD_GAP);
            int y = top + GRID_Y + row * (CARD_HEIGHT + CARD_GAP);
            String species = i < payload.entries().size() ? payload.entries().get(i) : "";
            renderCard(graphics, species, x, y, mouseX, mouseY, partialTick);
        }
        if (payload.entries().isEmpty()) {
            graphics.drawCenteredString(this.font,
                    YoikoClientText.tr(payload.searchQuery().isBlank()
                            ? "yoiko_core.ui.empty.category" : "yoiko_core.ui.empty.search"),
                    left + GRID_PANEL_X + GRID_PANEL_WIDTH / 2,
                    top + GRID_PANEL_Y + GRID_PANEL_HEIGHT / 2,
                    0xFFB8B8B8);
        }
    }

    private void renderPageText(GuiGraphics graphics, int left, int top) {
        int pageCenter = left + FLOATING_X + FLOATING_WIDTH / 2;
        graphics.drawCenteredString(
                this.font,
                Component.literal((payload.page() + 1) + "/" + payload.totalPages()).withStyle(ChatFormatting.AQUA),
                pageCenter,
                top + PAGE_CONTROLS_Y + 5,
                0xFFFFFF
        );
    }

    private void renderCard(GuiGraphics graphics, String species, int x, int y, int mouseX, int mouseY, float partialTick) {
        boolean filled = !species.isBlank();
        boolean hovered = filled && mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT;
        boolean selected = filled && species.equals(localSelectedSpecies);
        int border = selected ? 0xFF58F2FF : hovered ? 0xFFDFFBFF : rarityColor(payload.rarity());
        if (selected || hovered) {
            graphics.fill(x - 2, y - 2, x + CARD_WIDTH + 2, y + CARD_HEIGHT + 2, border);
        }
        renderCardFrame(graphics, x, y, filled);
        if (filled) {
            YoikoPokemonModelRenderer.render(graphics, species, x + 3, y + 3, CARD_WIDTH - 6, CARD_HEIGHT - 6, partialTick);
        }
    }

    private void renderCardFrame(GuiGraphics graphics, int x, int y, boolean filled) {
        int border = filled ? 0xFF8B9697 : 0xFF4A5557;
        int outer = filled ? 0xFF2F383A : 0xFF252B2C;
        int inner = filled ? 0xFF46575B : 0xFF333B3D;
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, border);
        graphics.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, outer);
        graphics.fill(x + 3, y + 3, x + CARD_WIDTH - 3, y + CARD_HEIGHT - 3, inner);
        graphics.fill(x + 4, y + 4, x + CARD_WIDTH - 4, y + 9, filled ? 0x803DD4E5 : 0x443F484A);
    }

    private void renderFloatingText(GuiGraphics graphics, int left, int top) {
        int x = left + FLOATING_X;
        graphics.drawCenteredString(this.font, YoikoClientText.tr("yoiko_core.dex.rarity_filter").withStyle(ChatFormatting.AQUA), x + FLOATING_WIDTH / 2, top + FILTER_Y + 5, 0xFFFFFF);
        graphics.drawCenteredString(this.font, YoikoClientText.tr("yoiko_core.ui.page.short").withStyle(ChatFormatting.GOLD), x + FLOATING_WIDTH / 2, top + PAGE_PANEL_Y + 8, 0xFFFFD36A);
    }

    private void drawRate(GuiGraphics graphics, int x, int y, String label, String value) {
        graphics.drawString(this.font, Component.literal(label).withStyle(ChatFormatting.AQUA), x, y, 0xFFFFFF, false);
        graphics.drawString(this.font, Component.literal(value).withStyle(ChatFormatting.WHITE), x, y + 12, 0xFFFFFF, false);
    }

    private String speciesAt(double mouseX, double mouseY) {
        int left = left();
        int top = top();
        int localX = (int) mouseX - (left + GRID_X);
        int localY = (int) mouseY - (top + GRID_Y);
        if (localX < 0 || localY < 0) {
            return null;
        }
        int stepX = CARD_WIDTH + CARD_GAP;
        int stepY = CARD_HEIGHT + CARD_GAP;
        int column = localX / stepX;
        int row = localY / stepY;
        if (column < 0 || column >= GRID_COLUMNS || row < 0 || row >= GRID_ROWS) {
            return null;
        }
        if (localX % stepX >= CARD_WIDTH || localY % stepY >= CARD_HEIGHT) {
            return null;
        }
        int index = row * GRID_COLUMNS + column;
        return index < payload.entries().size() ? payload.entries().get(index) : null;
    }

    private void addRarityTab(int x, int y, String rarity, String label) {
        boolean active = payload.rarity().equals(rarity);
        addAction(x, y, FLOATING_WIDTH, active ? "> " + label : label,
                action("dex_rarity", payload.gachaType(), rarity, payload.searchQuery()));
    }

    private void addAction(int x, int y, int width, String label, String action) {
        this.addRenderableWidget(YoikoButton.create(x, y, width, 18, Component.literal(label), button -> send(action)));
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

    private static int rarityColor(String rarity) {
        return switch (rarity) {
            case "sub_legendary" -> 0xFF5DDCFF;
            case "mythical" -> 0xFFFF7BEF;
            case "legendary" -> 0xFFFFC247;
            default -> 0xFFE8F2F2;
        };
    }

    private static String action(String command, Object... parts) {
        StringBuilder builder = new StringBuilder(command);
        for (Object part : parts) {
            builder.append('|').append(part);
        }
        return builder.toString();
    }
}
