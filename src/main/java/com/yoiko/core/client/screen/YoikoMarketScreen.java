package com.yoiko.core.client.screen;

import com.yoiko.core.menu.YoikoMarketContainerMenu;
import com.yoiko.core.network.MenuActionPayload;
import com.yoiko.core.network.OpenMarketPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class YoikoMarketScreen extends AbstractContainerScreen<YoikoMarketContainerMenu> {
    private static OpenMarketPayload pendingPayload = OpenMarketPayload.empty();
    private static final int LEFT_CONTENT_X = 8;
    private static final int LEFT_CONTENT_WIDTH = 176;
    private static final int DETAIL_Y = 24;
    private static final int DETAIL_HEIGHT = 126;
    private static final int INVENTORY_LABEL_Y = 155;

    private static final int RIGHT_CONTENT_X = YoikoMenuLayout.SPLIT_RIGHT_X;
    private static final int RIGHT_CONTENT_WIDTH = YoikoMenuLayout.SPLIT_RIGHT_WIDTH;
    private static final int SECTION_Y = 8;
    private static final int SECTION_WIDTH = 32;
    private static final int SECTION_GAP = 2;
    private static final int CONTROL_Y = 29;
    private static final int GRID_X = RIGHT_CONTENT_X + 12;
    private static final int GRID_Y = 49;
    private static final int GRID_COLUMNS = 8;
    private static final int GRID_ROWS = 8;
    private static final int GRID_STEP = 19;
    private static final int PAGE_Y = 204;
    private static final int ACTION_Y = 225;

    private static final int PRICE_INPUT_X = 78;
    private static final int PRICE_INPUT_Y = 84;
    private static final int PRICE_INPUT_WIDTH = 88;
    private static final long CANCEL_CONFIRM_MILLIS = 3_500L;
    private static final String[] SORTS = {"latest", "price_asc", "price_desc", "expiring"};
    private static final String[] CATEGORIES = {"all", "block", "tool", "consumable", "spawn_egg", "other"};

    private static final Section[] SECTIONS = {
            new Section("server_shop", "yoiko_core.ui.market.server_shop_short"),
            new Section("gem_shop", "yoiko_core.ui.market.gem_shop_short"),
            new Section("player_market", "yoiko_core.ui.market.player_market_short"),
            new Section("my_listings", "yoiko_core.ui.market.my_listings_short"),
            new Section("sales_history", "yoiko_core.ui.market.sales_history_short")
    };

    private OpenMarketPayload payload;
    private final List<YoikoIconButton> iconButtons = new ArrayList<>();
    private EditBox priceInput;
    private boolean ownListingDetail;
    private String editingListingId = "";
    private String pendingCancelId = "";
    private long pendingCancelUntil;
    private boolean registering;
    private UUID marketSessionId = new UUID(0L, 0L);
    private long marketNonce;
    private int automaticRefreshTicks;
    private YoikoFocusGrid gridFocus;
    private String localSelectedId = "";
    private EditBox searchInput;
    private boolean searchEditing;
    private String searchDraft = "";
    private boolean rebuildSearchControls;
    private YoikoButton mainActionButton;
    private YoikoButton secondaryActionButton;

    public YoikoMarketScreen(YoikoMarketContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.payload = pendingPayload;
        this.localSelectedId = this.payload.selectedId();
        this.searchDraft = this.payload.searchQuery();
        updateMarketSession(this.payload);
        this.imageWidth = YoikoMenuLayout.SPLIT_PANEL_WIDTH;
        this.imageHeight = YoikoMenuLayout.TAB_PANEL_HEIGHT;
        this.titleLabelX = LEFT_CONTENT_X;
        this.titleLabelY = 8;
        this.inventoryLabelX = YoikoMarketContainerMenu.PLAYER_INVENTORY_SLOT_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    public static void cachePendingPayload(OpenMarketPayload payload) {
        pendingPayload = payload == null ? OpenMarketPayload.empty() : payload;
    }

    @Override
    protected void init() {
        super.init();
        syncRegistrationSlot();
        buildMarketWidgets("");
        YoikoMousePosition.restoreIfRemembered();
    }

    public void update(OpenMarketPayload payload) {
        String previousPrice = priceInput == null ? "" : priceInput.getValue();
        String previousSearch = searchInput == null ? searchDraft : searchInput.getValue();
        String previousSelection = localSelectedId;
        this.payload = payload == null ? OpenMarketPayload.empty() : payload;
        this.localSelectedId = this.payload.entries().stream().anyMatch(entry -> entry.id().equals(previousSelection))
                ? previousSelection : this.payload.selectedId();
        this.searchDraft = searchEditing ? previousSearch : this.payload.searchQuery();
        automaticRefreshTicks = 0;
        updateMarketSession(this.payload);
        pendingPayload = this.payload;
        if (!"my_listings".equals(this.payload.section())) {
            ownListingDetail = false;
            editingListingId = "";
        }
        if (registering) {
            if ("yoiko_core.ui.market.listed_success".equals(this.payload.message())
                    && !this.payload.rememberListingPrice()) {
                previousPrice = "";
            }
            registering = false;
        }
        if (ownListingDetail && selected() == null) {
            ownListingDetail = false;
            editingListingId = "";
        }
        syncRegistrationSlot();
        buildMarketWidgets(previousPrice);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (mainActionButton != null) {
            mainActionButton.active = canRunSelectedAction(selected());
        }
        if (secondaryActionButton != null) {
            secondaryActionButton.active = canRunSecondaryAction(selected());
        }
        if (rebuildSearchControls) {
            rebuildSearchControls = false;
            buildMarketWidgets(priceInput == null ? "" : priceInput.getValue());
            if (searchInput != null) {
                setFocused(searchInput);
            }
        }
        if (("player_market".equals(payload.section()) || "my_listings".equals(payload.section()))
                && ++automaticRefreshTicks >= 100) {
            automaticRefreshTicks = 0;
            send("market_refresh_if_changed|" + payload.marketRevision() + "||" + localSelectedId);
        }
        if (!pendingCancelId.isBlank() && System.currentTimeMillis() > pendingCancelUntil) {
            pendingCancelId = "";
            pendingCancelUntil = 0L;
            buildMarketWidgets(priceInput == null ? "" : priceInput.getValue());
        }
        if (ownListingDetail && !menu.registrationStack().isEmpty()) {
            ownListingDetail = false;
            editingListingId = "";
            syncRegistrationSlot();
            buildMarketWidgets(priceInput == null ? "" : priceInput.getValue());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ClientServerRequestState.render(graphics, this.font, "market", this.width / 2, this.height - 30);
        OpenMarketPayload.Entry hovered = entryAt(mouseX, mouseY);
        Component disabledReason = disabledActionReason();
        if (mainActionButton != null && mainActionButton.isHoveredOrFocused()
                && !mainActionButton.active && disabledReason != null) {
            graphics.renderTooltip(this.font, disabledReason, mouseX, mouseY);
        } else if (secondaryActionButton != null && secondaryActionButton.isHoveredOrFocused()
                && !secondaryActionButton.active && disabledSecondaryActionReason() != null) {
            graphics.renderTooltip(this.font, disabledSecondaryActionReason(), mouseX, mouseY);
        } else if (hovered != null && !hovered.stack().isEmpty()) {
            graphics.renderTooltip(this.font, hovered.stack(), mouseX, mouseY);
        } else {
            this.renderTooltip(graphics, mouseX, mouseY);
            YoikoNavigationTabs.renderTooltip(graphics, this.font, iconButtons, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YoikoScreenStyle.renderBackdrop(graphics, width, height);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        YoikoScreenStyle.renderMarketLeftPanel(graphics, leftPos, topPos);
        YoikoScreenStyle.renderMarketRightPanel(
                graphics, leftPos + YoikoMenuLayout.SPLIT_RIGHT_PANEL_X, topPos
        );
        YoikoScreenStyle.renderSubPanel(
                graphics, leftPos + LEFT_CONTENT_X, topPos + DETAIL_Y, LEFT_CONTENT_WIDTH, DETAIL_HEIGHT
        );
        renderInventorySlotBackgrounds(graphics);
        renderMarketGridBackgrounds(graphics, mouseX, mouseY);
        if (isRegistrationPanel()) {
            YoikoScreenStyle.renderInventoryCompactSlot(
                    graphics,
                    leftPos + YoikoMarketContainerMenu.REGISTRATION_SLOT_X - 1,
                    topPos + YoikoMarketContainerMenu.REGISTRATION_SLOT_Y - 1
            );
        }
        renderActionAccent(graphics);
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (slot.isHighlightable()) {
            YoikoScreenStyle.renderSlotMask(graphics, slot.x - 1, slot.y - 1, 18, 18, 0xFFFFFF, 1);
            return;
        }
        super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        renderBalances(graphics);
        renderEntries(graphics);
        renderLeftPanel(graphics);
        String page = (payload.page() + 1) + "/" + payload.totalPages();
        graphics.drawString(
                this.font,
                Component.literal(page),
                RIGHT_CONTENT_X + (RIGHT_CONTENT_WIDTH - this.font.width(page)) / 2,
                PAGE_Y + 3,
                0xFF8A6A46,
                false
        );
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                0xFF5F3C18, false);
        if (!payload.message().isBlank()) {
            String message = abbreviated(YoikoClientText.dataText(payload.message()), LEFT_CONTENT_WIDTH - 16);
            graphics.drawString(this.font, Component.literal(message), LEFT_CONTENT_X + 8,
                    DETAIL_Y + DETAIL_HEIGHT - 12, 0xFF4A9A5B, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            OpenMarketPayload.Entry entry = entryAt((int) mouseX, (int) mouseY);
            if (entry != null) {
                focusGridEntry(payload.entries().indexOf(entry));
                selectLocally(entry);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D
                && mouseX >= leftPos + GRID_X
                && mouseX < leftPos + GRID_X + GRID_COLUMNS * GRID_STEP
                && mouseY >= topPos + GRID_Y
                && mouseY < topPos + GRID_Y + GRID_ROWS * GRID_STEP) {
            changePage(scrollY < 0.0D ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && searchInput != null && searchInput.isFocused()) {
            applySearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void buildMarketWidgets(String previousPrice) {
        boolean restoreGridFocus = gridFocus != null && gridFocus.isFocused();
        int previousGridIndex = gridFocus == null ? 0 : gridFocus.focusedIndex();
        clearWidgets();
        iconButtons.clear();
        YoikoNavigationTabs.add(iconButtons, leftPos, topPos, "market", this::send, this::addRenderableWidget);

        int sectionStart = RIGHT_CONTENT_X + 4;
        for (int i = 0; i < SECTIONS.length; i++) {
            Section section = SECTIONS[i];
            YoikoButton button = YoikoButton.create(
                    leftPos + sectionStart + i * (SECTION_WIDTH + SECTION_GAP),
                    topPos + SECTION_Y,
                    SECTION_WIDTH,
                    18,
                    YoikoClientText.tr(section.labelKey()),
                    ignored -> send("market_view|" + section.id() + "|0|")
            ).withoutTextShadow();
            button.active = !section.id().equals(payload.section());
            addRenderableWidget(button);
        }

        if (searchEditing) {
            searchInput = new EditBox(this.font, leftPos + RIGHT_CONTENT_X + 7, topPos + CONTROL_Y,
                    125, 16, YoikoClientText.tr("yoiko_core.ui.search"));
            searchInput.setMaxLength(64);
            searchInput.setValue(searchDraft);
            searchInput.setHint(YoikoClientText.tr("yoiko_core.ui.search_hint"));
            addRenderableWidget(searchInput);
            YoikoButton applySearch = YoikoButton.create(
                    leftPos + RIGHT_CONTENT_X + 135, topPos + CONTROL_Y, 15, 16,
                    Component.literal("✓"), ignored -> applySearch()).withoutTextShadow();
            addRenderableWidget(applySearch);
        } else {
            searchInput = null;
            YoikoButton sort = YoikoButton.create(
                    leftPos + RIGHT_CONTENT_X + 7, topPos + CONTROL_Y, 61, 16,
                    YoikoClientText.tr("yoiko_core.ui.market.sort." + payload.sort()),
                    ignored -> send("market_sort|" + nextValue(SORTS, payload.sort()))
            ).withoutTextShadow();
            addRenderableWidget(sort);

            YoikoButton category = YoikoButton.create(
                    leftPos + RIGHT_CONTENT_X + 71, topPos + CONTROL_Y, 61, 16,
                    YoikoClientText.tr("yoiko_core.ui.market.category." + payload.category()),
                    ignored -> send("market_category|" + nextValue(CATEGORIES, payload.category()))
            ).withoutTextShadow();
            addRenderableWidget(category);

            YoikoButton search = YoikoButton.create(
                    leftPos + RIGHT_CONTENT_X + 135, topPos + CONTROL_Y, 15, 16,
                    Component.literal(payload.searchQuery().isBlank() ? "S" : "S*"), ignored -> {
                        searchEditing = true;
                        searchDraft = payload.searchQuery();
                        rebuildSearchControls = true;
                    }).withoutTextShadow();
            addRenderableWidget(search);
        }

        YoikoButton refresh = YoikoButton.create(
                leftPos + RIGHT_CONTENT_X + 153,
                topPos + CONTROL_Y,
                16,
                16,
                Component.literal("R"),
                ignored -> send("market_refresh|||" + localSelectedId)
        ).withoutTextShadow();
        addRenderableWidget(refresh);

        addPageButtons();
        addMainActionButton();

        if (isRegistrationPanel()) {
            addRegistrationWidgets(previousPrice);
        } else if (isOwnListingDetail()) {
            addOwnListingEditWidgets(previousPrice);
        } else {
            priceInput = null;
        }
        if (restoreGridFocus && gridFocus != null) {
            gridFocus.setFocusedIndex(previousGridIndex);
            setFocused(gridFocus);
        }
    }

    private void addPageButtons() {
        YoikoButton previous = YoikoButton.create(
                leftPos + RIGHT_CONTENT_X, topPos + PAGE_Y, 18, 14, Component.literal("<"),
                ignored -> changePage(-1)
        ).withKind(YoikoButton.Kind.PREVIOUS);
        previous.active = payload.page() > 0;
        addRenderableWidget(previous);
        YoikoButton next = YoikoButton.create(
                leftPos + RIGHT_CONTENT_X + RIGHT_CONTENT_WIDTH - 18,
                topPos + PAGE_Y,
                18,
                14,
                Component.literal(">"),
                ignored -> changePage(1)
        ).withKind(YoikoButton.Kind.NEXT);
        next.active = payload.page() + 1 < payload.totalPages();
        addRenderableWidget(next);
    }

    private void addMainActionButton() {
        OpenMarketPayload.Entry selected = selected();
        secondaryActionButton = null;
        boolean serverTrade=selected!=null&&"SERVER_TRADE".equals(selected.action());
        int buttonWidth=serverTrade?(RIGHT_CONTENT_WIDTH-20)/2:RIGHT_CONTENT_WIDTH-16;
        mainActionButton = YoikoButton.create(
                leftPos + RIGHT_CONTENT_X + 8,
                topPos + ACTION_Y,
                buttonWidth,
                18,
                actionLabel(selected),
                ignored -> runSelectedAction()
        ).withoutTextShadow().withAccent(actionAccentColor(selected));
        mainActionButton.active = canRunSelectedAction(selected);
        addRenderableWidget(mainActionButton);
        if(serverTrade){
            secondaryActionButton=YoikoButton.create(
                    leftPos+RIGHT_CONTENT_X+12+buttonWidth,topPos+ACTION_Y,buttonWidth,18,
                    YoikoClientText.tr("yoiko_core.ui.market.sell_to_server"),ignored->runServerBuyback(selected)
            ).withoutTextShadow().withAccent(0xD69A2C);
            secondaryActionButton.active=canRunSecondaryAction(selected);
            addRenderableWidget(secondaryActionButton);
        }
        gridFocus = addRenderableWidget(new YoikoFocusGrid(
                leftPos + GRID_X, topPos + GRID_Y, GRID_COLUMNS, GRID_ROWS, 16, 16,
                GRID_STEP, GRID_STEP, () -> payload.entries().size(), this::activateGridEntry));
        gridFocus.setFocusedIndex(Math.max(0, payload.entries().indexOf(selected)));
    }

    private void activateGridEntry(int index) {
        if (index >= 0 && index < payload.entries().size()) {
            selectLocally(payload.entries().get(index));
        }
    }

    private void selectLocally(OpenMarketPayload.Entry entry) {
        localSelectedId = entry.id();
        if ("my_listings".equals(payload.section()) && menu.registrationStack().isEmpty()
                && "CANCEL_LISTING".equals(entry.action())) {
            ownListingDetail = true;
            editingListingId = "";
            syncRegistrationSlot();
        }
        pendingCancelId = "";
        buildMarketWidgets(priceInput == null ? "" : priceInput.getValue());
    }

    private void applySearch() {
        searchDraft = searchInput == null ? searchDraft : searchInput.getValue();
        searchEditing = false;
        send("market_search|" + searchDraft.replace('|', ' '));
    }

    private void focusGridEntry(int index) {
        if (gridFocus != null && index >= 0) {
            setFocused(gridFocus);
            gridFocus.setFocusedIndex(index);
        }
    }

    private void addRegistrationWidgets(String previousPrice) {
        priceInput = createPriceInput(previousPrice);
        addRenderableWidget(priceInput);

        YoikoButton remember = YoikoButton.create(
                leftPos + LEFT_CONTENT_X + 105,
                topPos + DETAIL_Y + 5,
                62,
                14,
                YoikoClientText.tr(payload.rememberListingPrice()
                        ? "yoiko_core.ui.market.remember_price_on"
                        : "yoiko_core.ui.market.remember_price_off"),
                ignored -> send("market_remember_price|" + !payload.rememberListingPrice())
        ).withoutTextShadow();
        addRenderableWidget(remember);

    }

    private void addOwnListingEditWidgets(String previousPrice) {
        OpenMarketPayload.Entry entry = selected();
        String initial = previousPrice;
        if (entry != null && !entry.id().equals(editingListingId)) {
            initial = Long.toString(entry.price());
            editingListingId = entry.id();
        } else if (entry != null && initial.isBlank()) {
            initial = Long.toString(entry.price());
        }
        priceInput = createPriceInput(initial);
        addRenderableWidget(priceInput);

        YoikoButton edit = YoikoButton.create(
                leftPos + LEFT_CONTENT_X + 91,
                topPos + DETAIL_Y + 108,
                76,
                16,
                YoikoClientText.tr("yoiko_core.ui.market.update_price"),
                ignored -> updateListingPrice()
        ).withoutTextShadow().withAccent(0x4B8FD9);
        addRenderableWidget(edit);

        YoikoButton newListing = YoikoButton.create(
                leftPos + LEFT_CONTENT_X + 9,
                topPos + DETAIL_Y + 108,
                78,
                16,
                YoikoClientText.tr("yoiko_core.ui.market.new_listing"),
                ignored -> {
                    ownListingDetail = false;
                    editingListingId = "";
                    syncRegistrationSlot();
                    buildMarketWidgets("");
                }
        ).withoutTextShadow().withAccent(0xD69A2C);
        addRenderableWidget(newListing);
    }

    private EditBox createPriceInput(String value) {
        EditBox input = new EditBox(
                this.font,
                leftPos + PRICE_INPUT_X,
                topPos + PRICE_INPUT_Y,
                PRICE_INPUT_WIDTH,
                16,
                YoikoClientText.tr("yoiko_core.ui.market.total_price")
        );
        input.setMaxLength(16);
        input.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        input.setHint(YoikoClientText.tr("yoiko_core.ui.market.price_hint"));
        if (value != null && !value.isBlank()) {
            input.setValue(value);
        }
        return input;
    }

    private void renderInventorySlotBackgrounds(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                YoikoScreenStyle.renderInventoryCompactSlot(
                        graphics,
                        leftPos + YoikoMarketContainerMenu.PLAYER_INVENTORY_SLOT_X - 1
                                + column * YoikoMarketContainerMenu.SLOT_STEP,
                        topPos + YoikoMarketContainerMenu.PLAYER_INVENTORY_SLOT_Y - 1
                                + row * YoikoMarketContainerMenu.SLOT_STEP
                );
            }
        }
        for (int column = 0; column < 9; column++) {
            YoikoScreenStyle.renderInventoryCompactSlot(
                    graphics,
                    leftPos + YoikoMarketContainerMenu.PLAYER_INVENTORY_SLOT_X - 1
                            + column * YoikoMarketContainerMenu.SLOT_STEP,
                    topPos + YoikoMarketContainerMenu.HOTBAR_SLOT_Y - 1
            );
        }
    }

    private void renderMarketGridBackgrounds(GuiGraphics graphics, int mouseX, int mouseY) {
        YoikoScreenStyle.renderSubPanel(
                graphics,
                leftPos + GRID_X - 4,
                topPos + GRID_Y - 3,
                GRID_COLUMNS * GRID_STEP + 7,
                GRID_ROWS * GRID_STEP + 6
        );
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                int index = row * GRID_COLUMNS + column;
                int x = leftPos + GRID_X + column * GRID_STEP;
                int y = topPos + GRID_Y + row * GRID_STEP;
                YoikoScreenStyle.renderInventoryCompactSlot(graphics, x - 1, y - 1);
                if (index >= payload.entries().size()) {
                    continue;
                }
                OpenMarketPayload.Entry entry = payload.entries().get(index);
                boolean selected = entry.id().equals(localSelectedId);
                boolean hovered = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
                if (selected) {
                    graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x554FEAFF);
                } else if (hovered) {
                    graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x33FFFFFF);
                }
            }
        }
    }

    private void renderBalances(GuiGraphics graphics) {
        YoikoCurrencyIconRenderer.render(graphics, "GOLD", LEFT_CONTENT_X, 8);
        graphics.drawString(this.font, Component.literal(String.format(Locale.ROOT, "%,d", payload.gold())),
                LEFT_CONTENT_X + 11, 8, 0xFFD28A16, false);
        String gems = String.format(Locale.ROOT, "%,d", payload.gems());
        int gemX = LEFT_CONTENT_X + LEFT_CONTENT_WIDTH - this.font.width(gems)
                - YoikoCurrencyIconRenderer.ICON_SIZE - YoikoCurrencyIconRenderer.TEXT_GAP;
        YoikoCurrencyIconRenderer.render(graphics, "GEM", gemX, 8);
        graphics.drawString(this.font, Component.literal(gems), gemX + 11, 8, 0xFF36BFD8, false);

        int color = payload.pendingDeliveries() >= payload.maxPendingDeliveries() ? 0xFFE05A45 : 0xFF9A8268;
        String deliveries = YoikoClientText.text(
                "yoiko_core.ui.market.pending_deliveries",
                payload.pendingDeliveries(),
                payload.maxPendingDeliveries()
        );
        graphics.drawString(this.font, Component.literal(deliveries), LEFT_CONTENT_X, 16, color, false);
        if ("server_shop".equals(payload.section())) {
            String cap = YoikoClientText.text("yoiko_core.ui.market.daily_gold_remaining",
                    payload.remainingDailyServerBuyGold(), payload.dailyServerBuyGoldLimit());
            graphics.drawString(this.font, Component.literal(cap), LEFT_CONTENT_X + 80, 16,
                    payload.remainingDailyServerBuyGold() > 0 ? 0xFF9A8268 : 0xFFE05A45, false);
        }
    }

    private void renderEntries(GuiGraphics graphics) {
        if (payload.entries().isEmpty()) {
            graphics.drawCenteredString(this.font,
                    YoikoClientText.tr(payload.searchQuery().isBlank()
                            ? "yoiko_core.ui.market.empty" : "yoiko_core.ui.empty.search"),
                    GRID_X + (GRID_COLUMNS * GRID_STEP) / 2,
                    GRID_Y + (GRID_ROWS * GRID_STEP) / 2,
                    0xFF9A8268);
        }
        for (int i = 0; i < payload.entries().size() && i < GRID_COLUMNS * GRID_ROWS; i++) {
            int column = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            int x = GRID_X + column * GRID_STEP;
            int y = GRID_Y + row * GRID_STEP;
            ItemStack stack = payload.entries().get(i).stack();
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(this.font, stack, x, y);
        }
    }

    private void renderLeftPanel(GuiGraphics graphics) {
        if (isRegistrationPanel()) {
            renderRegistrationPanel(graphics);
            return;
        }
        OpenMarketPayload.Entry entry = selected();
        if (entry == null) {
            graphics.drawString(this.font, YoikoClientText.tr(payload.searchQuery().isBlank()
                            ? "yoiko_core.ui.market.empty" : "yoiko_core.ui.empty.search"),
                    LEFT_CONTENT_X + 10, DETAIL_Y + 16, 0xFF9A8268, false);
            return;
        }

        ItemStack stack = entry.stack();
        YoikoScreenStyle.renderInventoryCompactSlot(graphics, LEFT_CONTENT_X + 11, DETAIL_Y + 12);
        graphics.renderItem(stack, LEFT_CONTENT_X + 12, DETAIL_Y + 13);
        graphics.renderItemDecorations(this.font, stack, LEFT_CONTENT_X + 12, DETAIL_Y + 13);
        drawWrapped(graphics, stack.getHoverName(), LEFT_CONTENT_X + 38, DETAIL_Y + 13,
                LEFT_CONTENT_WIDTH - 48, 2, 0xFF755539);
        if("SERVER_TRADE".equals(entry.action())){
            drawServerTradePrice(graphics,"yoiko_core.ui.market.shop_sale_price",entry.purchaseCount(),entry.purchasePrice(),
                    "yoiko_core.ui.market.shop_not_sold",DETAIL_Y+49,0xFF4A9A5B);
            drawServerTradePrice(graphics,"yoiko_core.ui.market.shop_buy_price",entry.buybackCount(),entry.buybackPrice(),
                    "yoiko_core.ui.market.shop_not_bought",DETAIL_Y+64,0xFFD28A16);
            drawWrapped(graphics,YoikoClientText.tr("yoiko_core.ui.market.server_trade_description"),LEFT_CONTENT_X+12,
                    DETAIL_Y+86,LEFT_CONTENT_WIDTH-24,3,0xFF8A7258);
            return;
        }
        drawMetaRow(graphics, "yoiko_core.ui.market.quantity", Integer.toString(stack.getCount()),
                DETAIL_Y + 46, 0xFF5C4937);
        String priceSuffix = "RELIC".equals(entry.currency()) ? " / " + payload.sealedRelics() : "";
        drawCurrencyMetaRow(graphics, "yoiko_core.ui.market.price", entry.price(), entry.currency(), priceSuffix,
                DETAIL_Y + 58, currencyColor(entry.currency()));
        if (entry.medianUnitPrice() > 0L && ("BUY_LISTING".equals(entry.action())
                || "CANCEL_LISTING".equals(entry.action()) || "RELIST_EXPIRED".equals(entry.action()))) {
            drawCurrencyMetaRow(graphics, "yoiko_core.ui.market.median_unit_price",
                    entry.medianUnitPrice(), "GOLD",
                    YoikoClientText.tr("yoiko_core.ui.market.median_with_samples",
                            "", entry.medianSampleCount()).getString(),
                    DETAIL_Y + 70, 0xFF4A879A);
        }

        if ("SALE_RECORD".equals(entry.action())) {
            drawMetaRow(graphics, "yoiko_core.ui.market.buyer", entry.seller(), DETAIL_Y + 70, 0xFF5C4937);
            drawCurrencyMetaRow(graphics, "yoiko_core.ui.market.fee", entry.fee(), "GOLD", "", DETAIL_Y + 82, 0xFFD28A16);
            drawCurrencyMetaRow(graphics, "yoiko_core.ui.market.net_proceeds", entry.proceeds(), "GOLD", "",
                    DETAIL_Y + 94, 0xFF4A9A5B);
            drawMetaRow(graphics, "yoiko_core.ui.market.sold_at", formatDate(entry.createdAt()),
                    DETAIL_Y + 106, 0xFF5C4937);
            return;
        }

        if (!entry.own() && !entry.seller().isBlank()) {
            drawMetaRow(graphics, "yoiko_core.ui.market.seller", entry.seller(),
                    DETAIL_Y + (entry.medianUnitPrice() > 0L ? 82 : 70), 0xFF5C4937);
        }
        if (entry.own()) {
            if (entry.remainingSeconds() > 0L) {
                drawMetaRow(graphics, "yoiko_core.ui.market.remaining", formatRemaining(entry.remainingSeconds()),
                        DETAIL_Y + (entry.medianUnitPrice() > 0L ? 82 : 70), 0xFF5C4937);
            }
            int feeY = entry.medianUnitPrice() > 0L ? 94 : 82;
            drawCurrencyMetaRow(graphics, "yoiko_core.ui.market.fee", entry.fee(), "GOLD", "", DETAIL_Y + feeY, 0xFFD28A16);
            drawCurrencyMetaRow(graphics, "yoiko_core.ui.market.net_proceeds", entry.proceeds(), "GOLD", "",
                    DETAIL_Y + feeY + 12, 0xFF4A9A5B);
        } else {
            if (entry.remainingSeconds() > 0L) {
                drawMetaRow(graphics, "yoiko_core.ui.market.remaining", formatRemaining(entry.remainingSeconds()),
                        DETAIL_Y + (entry.medianUnitPrice() > 0L ? 94 : 82), 0xFF5C4937);
            }
            String descriptionKey = switch (entry.action()) {
                case "SELL_GOLD" -> "yoiko_core.ui.market.server_buy_description";
                case "BUY_GEM" -> "yoiko_core.ui.market.gem_delivery_description";
                case "BUY_LISTING" -> "yoiko_core.ui.market.player_delivery_description";
                case "RELIC_EXCHANGE" -> "yoiko_core.ui.market.relic_exchange_description";
                default -> "yoiko_core.ui.market.server_delivery_description";
            };
            drawWrapped(graphics, YoikoClientText.tr(descriptionKey), LEFT_CONTENT_X + 12,
                    DETAIL_Y + (entry.medianUnitPrice() > 0L ? 108 : 99),
                    LEFT_CONTENT_WIDTH - 24, 2, 0xFF8A7258);
        }
    }

    private void renderRegistrationPanel(GuiGraphics graphics) {
        graphics.drawString(this.font, YoikoClientText.tr("yoiko_core.ui.market.registration_title"),
                LEFT_CONTENT_X + 10, DETAIL_Y + 9, 0xFF755539, false);
        ItemStack stack = menu.registrationStack();
        if (stack.isEmpty()) {
            drawWrapped(graphics, YoikoClientText.tr("yoiko_core.ui.market.registration_hint"),
                    LEFT_CONTENT_X + 42, DETAIL_Y + 30, LEFT_CONTENT_WIDTH - 54, 3, 0xFF9A8268);
        } else {
            drawWrapped(graphics, stack.getHoverName(), LEFT_CONTENT_X + 42, DETAIL_Y + 31,
                    LEFT_CONTENT_WIDTH - 54, 2, 0xFF755539);
            graphics.drawString(this.font,
                    YoikoClientText.tr("yoiko_core.ui.market.registration_count", stack.getCount()),
                    LEFT_CONTENT_X + 42, DETAIL_Y + 54, 0xFF8A6A46, false);
        }
        graphics.drawString(this.font, YoikoClientText.tr("yoiko_core.ui.market.total_price"),
                LEFT_CONTENT_X + 10, PRICE_INPUT_Y + 4, 0xFF8A6A46, false);

        long total = enteredPrice();
        int count = Math.max(1, stack.getCount());
        long unit = total / count;
        long fee = Math.max(0L, Math.round(total * payload.saleFeePercent() / 100.0D));
        long net = Math.max(0L, total - fee);
        drawCurrencyMetaRow(graphics, "yoiko_core.ui.market.unit_price", unit, "GOLD", "", DETAIL_Y + 78, 0xFFD28A16);
        drawCurrencyMetaRow(graphics, "yoiko_core.ui.market.fee", fee, "GOLD", "", DETAIL_Y + 89, 0xFFD28A16);
        drawCurrencyMetaRow(graphics, "yoiko_core.ui.market.net_proceeds", net, "GOLD", "", DETAIL_Y + 100, 0xFF4A9A5B);
        if (!stack.isEmpty() && total > 0L
                && (unit < payload.minReasonableUnitPrice() || unit > payload.maxReasonableUnitPrice())) {
            String warning = abbreviated(
                    YoikoClientText.tr("yoiko_core.ui.market.price_warning").getString(),
                    LEFT_CONTENT_WIDTH - 20
            );
            graphics.drawString(this.font, Component.literal(warning),
                    LEFT_CONTENT_X + 10, DETAIL_Y + 111, 0xFFE05A45, false);
        }
    }

    private void renderActionAccent(GuiGraphics graphics) {
        OpenMarketPayload.Entry entry = selected();
        if ((!isRegistrationPanel() && entry == null) || (entry != null && "SALE_RECORD".equals(entry.action()))) {
            return;
        }
        int color = isRegistrationPanel() ? 0xFFE0A72E : switch (entry.action()) {
                case "CANCEL_LISTING" -> 0xFFD94B45;
                case "SELL_GOLD" -> 0xFFE0A72E;
                default -> 0xFF4BAA68;
            };
        graphics.fill(
                leftPos + RIGHT_CONTENT_X + 10,
                topPos + ACTION_Y + 4,
                leftPos + RIGHT_CONTENT_X + 12,
                topPos + ACTION_Y + 14,
                color
        );
    }

    private int actionAccentColor(OpenMarketPayload.Entry entry) {
        if (isRegistrationPanel()) {
            return 0xD69A2C;
        }
        if (entry == null || "SALE_RECORD".equals(entry.action())) {
            return 0;
        }
        return switch (entry.action()) {
            case "CANCEL_LISTING" -> 0xD94B45;
            case "SELL_GOLD" -> 0xD69A2C;
            default -> 0x4BAA68;
        };
    }

    private void drawMetaRow(GuiGraphics graphics, String key, String value, int y, int valueColor) {
        graphics.drawString(this.font, YoikoClientText.tr(key), LEFT_CONTENT_X + 12, y, 0xFF8A6A46, false);
        graphics.drawString(this.font, Component.literal(value), LEFT_CONTENT_X + 88, y, valueColor, false);
    }

    private void drawCurrencyMetaRow(GuiGraphics graphics, String key, long amount, String currency,
                                     String suffix, int y, int valueColor) {
        graphics.drawString(this.font, YoikoClientText.tr(key), LEFT_CONTENT_X + 12, y, 0xFF8A6A46, false);
        int iconX = LEFT_CONTENT_X + 88;
        YoikoCurrencyIconRenderer.render(graphics, currency, iconX, y + 1);
        String value = String.format(Locale.ROOT, "%,d", amount) + suffix;
        graphics.drawString(this.font, Component.literal(value),
                iconX + YoikoCurrencyIconRenderer.ICON_SIZE + YoikoCurrencyIconRenderer.TEXT_GAP,
                y, valueColor, false);
    }

    private void drawServerTradePrice(GuiGraphics graphics,String key,int count,long price,String unavailableKey,
                                      int y,int valueColor){
        graphics.drawString(this.font,YoikoClientText.tr(key),LEFT_CONTENT_X+12,y,0xFF8A6A46,false);
        if(price<=0L||count<=0){
            graphics.drawString(this.font,YoikoClientText.tr(unavailableKey),LEFT_CONTENT_X+76,y,0xFF9A8268,false);
            return;
        }
        String quantity="×"+count;
        graphics.drawString(this.font,Component.literal(quantity),LEFT_CONTENT_X+76,y,0xFF5C4937,false);
        int iconX=LEFT_CONTENT_X+104;
        YoikoCurrencyIconRenderer.render(graphics,"GOLD",iconX,y+1);
        graphics.drawString(this.font,Component.literal(String.format(Locale.ROOT,"%,d",price)),
                iconX+YoikoCurrencyIconRenderer.ICON_SIZE+YoikoCurrencyIconRenderer.TEXT_GAP,y,valueColor,false);
    }

    private void runSelectedAction() {
        if (isRegistrationPanel()) {
            registerItem();
            return;
        }
        OpenMarketPayload.Entry entry = selected();
        if (entry == null) {
            return;
        }
        if("SERVER_TRADE".equals(entry.action())){
            if(!entry.purchaseOfferId().isBlank())send("market_buy_gold|"+entry.purchaseOfferId());
            return;
        }
        if ("CANCEL_LISTING".equals(entry.action())) {
            long now = System.currentTimeMillis();
            if (!entry.id().equals(pendingCancelId) || now > pendingCancelUntil) {
                pendingCancelId = entry.id();
                pendingCancelUntil = now + CANCEL_CONFIRM_MILLIS;
                buildMarketWidgets(priceInput == null ? "" : priceInput.getValue());
                return;
            }
            pendingCancelId = "";
            send("market_cancel_listing|" + entry.id());
            return;
        }
        String action = switch (entry.action()) {
            case "BUY_GOLD" -> "market_buy_gold";
            case "SELL_GOLD" -> "market_sell_gold";
            case "BUY_GEM" -> "market_buy_gem";
            case "RELIC_EXCHANGE" -> "market_exchange_relic";
            case "BUY_LISTING" -> "market_buy_listing";
            case "RELIST_EXPIRED" -> "market_relist_expired";
            default -> "";
        };
        if (!action.isBlank()) {
            send(action + "|" + entry.id());
        }
    }

    private boolean canRunSelectedAction(OpenMarketPayload.Entry entry) {
        if (isRegistrationPanel()) {
            return !menu.registrationStack().isEmpty() && enteredPrice() > 0L;
        }
        if (entry == null) {
            return false;
        }
        boolean deliveryFull = payload.pendingDeliveries() >= payload.maxPendingDeliveries();
        return switch (entry.action()) {
            case "SERVER_TRADE" -> !entry.purchaseOfferId().isBlank() && entry.purchasePrice()>0L
                    && !deliveryFull && payload.gold()>=entry.purchasePrice();
            case "BUY_GEM" -> !deliveryFull && payload.gems() >= entry.price();
            case "RELIC_EXCHANGE" -> !deliveryFull && payload.sealedRelics() >= entry.price();
            case "BUY_GOLD", "BUY_LISTING" -> !deliveryFull && payload.gold() >= entry.price();
            case "SELL_GOLD" -> payload.remainingDailyServerBuyGold() >= entry.price();
            case "CANCEL_LISTING", "RELIST_EXPIRED" -> true;
            default -> false;
        };
    }

    private boolean canRunSecondaryAction(OpenMarketPayload.Entry entry){
        return entry!=null&&"SERVER_TRADE".equals(entry.action())&&!entry.buybackOfferId().isBlank()
                && entry.buybackPrice()>0L&&payload.remainingDailyServerBuyGold()>=entry.buybackPrice();
    }

    private void runServerBuyback(OpenMarketPayload.Entry entry){
        if(entry!=null&&!entry.buybackOfferId().isBlank())send("market_sell_gold|"+entry.buybackOfferId());
    }

    private Component disabledActionReason() {
        if (mainActionButton == null || mainActionButton.active) {
            return null;
        }
        if (isRegistrationPanel()) {
            return menu.registrationStack().isEmpty()
                    ? YoikoClientText.tr("yoiko_core.ui.disabled.item_required")
                    : YoikoClientText.tr("yoiko_core.ui.disabled.valid_price_required");
        }
        OpenMarketPayload.Entry entry = selected();
        if (entry == null) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.select_item");
        }
        if ("SALE_RECORD".equals(entry.action())) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.history_only");
        }
        if("SERVER_TRADE".equals(entry.action())){
            if(entry.purchaseOfferId().isBlank())return YoikoClientText.tr("yoiko_core.ui.market.shop_not_sold");
            if(payload.pendingDeliveries()>=payload.maxPendingDeliveries())return YoikoClientText.tr("yoiko_core.ui.disabled.delivery_full");
            if(payload.gold()<entry.purchasePrice())return YoikoClientText.tr("yoiko_core.ui.disabled.not_enough_gold");
        }
        if (payload.pendingDeliveries() >= payload.maxPendingDeliveries()
                && ("BUY_GEM".equals(entry.action()) || "BUY_GOLD".equals(entry.action())
                || "BUY_LISTING".equals(entry.action()) || "RELIC_EXCHANGE".equals(entry.action()))) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.delivery_full");
        }
        if ("BUY_GEM".equals(entry.action()) && payload.gems() < entry.price()) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.not_enough_gems");
        }
        if ("RELIC_EXCHANGE".equals(entry.action()) && payload.sealedRelics() < entry.price()) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.not_enough_sealed_relics");
        }
        if (("BUY_GOLD".equals(entry.action()) || "BUY_LISTING".equals(entry.action()))
                && payload.gold() < entry.price()) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.not_enough_gold");
        }
        if ("SELL_GOLD".equals(entry.action())) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.daily_limit");
        }
        return YoikoClientText.tr("yoiko_core.ui.disabled.unavailable");
    }

    private Component disabledSecondaryActionReason(){
        OpenMarketPayload.Entry entry=selected();
        if(entry==null||!"SERVER_TRADE".equals(entry.action()))return null;
        if(entry.buybackOfferId().isBlank())return YoikoClientText.tr("yoiko_core.ui.market.shop_not_bought");
        if(payload.remainingDailyServerBuyGold()<entry.buybackPrice())return YoikoClientText.tr("yoiko_core.ui.disabled.daily_limit");
        return YoikoClientText.tr("yoiko_core.ui.disabled.unavailable");
    }

    private Component actionLabel(OpenMarketPayload.Entry entry) {
        if (isRegistrationPanel()) {
            return YoikoClientText.tr("yoiko_core.ui.market.register_item")
                    .copy().withStyle(ChatFormatting.GOLD);
        }
        if (entry == null) {
            return YoikoClientText.tr("yoiko_core.ui.market.select_item");
        }
        return switch (entry.action()) {
            case "SERVER_TRADE" -> YoikoClientText.tr("yoiko_core.ui.market.purchase")
                    .copy().withStyle(ChatFormatting.GREEN);
            case "SELL_GOLD" -> YoikoClientText.tr("yoiko_core.ui.market.sell_to_server")
                    .copy().withStyle(ChatFormatting.GOLD);
            case "CANCEL_LISTING" -> YoikoClientText.tr(
                    entry.id().equals(pendingCancelId)
                            ? "yoiko_core.ui.market.confirm_cancel"
                            : "yoiko_core.ui.market.cancel_listing"
            ).copy().withStyle(ChatFormatting.RED);
            case "RELIST_EXPIRED" -> YoikoClientText.tr("yoiko_core.ui.market.relist")
                    .copy().withStyle(ChatFormatting.GREEN);
            case "RELIC_EXCHANGE" -> YoikoClientText.tr("yoiko_core.ui.market.exchange")
                    .copy().withStyle(ChatFormatting.GREEN);
            case "SALE_RECORD" -> YoikoClientText.tr("yoiko_core.ui.market.sale_record");
            default -> YoikoClientText.tr("yoiko_core.ui.market.purchase")
                    .copy().withStyle(ChatFormatting.GREEN);
        };
    }

    private void registerItem() {
        if (priceInput == null || priceInput.getValue().isBlank() || menu.registrationStack().isEmpty()) {
            return;
        }
        long price = enteredPrice();
        if (price > 0L) {
            registering = true;
            send("market_register|" + price);
        }
    }

    private void updateListingPrice() {
        OpenMarketPayload.Entry entry = selected();
        long price = enteredPrice();
        if (entry != null && "CANCEL_LISTING".equals(entry.action()) && price > 0L) {
            send("market_update_price|" + entry.id() + "|" + price);
        }
    }

    private long enteredPrice() {
        if (priceInput == null || priceInput.getValue().isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(priceInput.getValue());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void changePage(int delta) {
        int page = Math.max(0, Math.min(payload.page() + delta, payload.totalPages() - 1));
        if (page != payload.page()) {
            send("market_view|" + payload.section() + "|" + page + "|");
        }
    }

    private OpenMarketPayload.Entry selected() {
        for (OpenMarketPayload.Entry entry : payload.entries()) {
            if (entry.id().equals(localSelectedId)) {
                return entry;
            }
        }
        return payload.entries().isEmpty() ? null : payload.entries().get(0);
    }

    private OpenMarketPayload.Entry entryAt(int mouseX, int mouseY) {
        int x = leftPos + GRID_X;
        int y = topPos + GRID_Y;
        if (mouseX < x || mouseX >= x + GRID_COLUMNS * GRID_STEP
                || mouseY < y || mouseY >= y + GRID_ROWS * GRID_STEP) {
            return null;
        }
        int column = (mouseX - x) / GRID_STEP;
        int row = (mouseY - y) / GRID_STEP;
        if ((mouseX - x) % GRID_STEP >= 16 || (mouseY - y) % GRID_STEP >= 16) {
            return null;
        }
        int index = row * GRID_COLUMNS + column;
        return index >= 0 && index < payload.entries().size() ? payload.entries().get(index) : null;
    }

    private boolean isRegistrationPanel() {
        return "my_listings".equals(payload.section()) && !ownListingDetail;
    }

    private boolean isOwnListingDetail() {
        return "my_listings".equals(payload.section()) && ownListingDetail;
    }

    private void syncRegistrationSlot() {
        if (minecraft != null && minecraft.player != null) {
            menu.setRegistrationActive(minecraft.player, isRegistrationPanel());
        }
    }

    private void send(String action) {
        if (!ClientServerRequestState.begin(this, action)) {
            return;
        }
        if (!action.startsWith("market_")) {
            PacketDistributor.sendToServer(ClientMenuSession.action(action));
            return;
        }
        PacketDistributor.sendToServer(new MenuActionPayload(action, marketSessionId, ++marketNonce));
    }

    private void updateMarketSession(OpenMarketPayload nextPayload) {
        UUID next = new UUID(0L, 0L);
        try {
            if (nextPayload != null && !nextPayload.sessionId().isBlank()) {
                next = UUID.fromString(nextPayload.sessionId());
            }
        } catch (IllegalArgumentException ignored) {
            // The server will reject the zero session rather than accepting malformed state.
        }
        if (!next.equals(marketSessionId)) {
            marketSessionId = next;
            marketNonce = 0L;
        }
    }

    private String abbreviated(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(1, width - this.font.width("..."))) + "...";
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int maxLines, int color) {
        int line = 0;
        for (FormattedCharSequence sequence : this.font.split(text, width)) {
            if (line >= maxLines) {
                break;
            }
            graphics.drawString(this.font, sequence, x, y + line * 10, color, false);
            line++;
        }
    }

    private static String nextValue(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private static int currencyColor(String currency) {
        return YoikoCurrencyIconRenderer.color(currency);
    }

    private static String formatRemaining(long seconds) {
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        if (days > 0L) {
            return YoikoClientText.text("yoiko_core.ui.duration.days_hours", days, hours);
        }
        long minutes = (seconds % 3_600L) / 60L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static String formatDate(long millis) {
        if (millis <= 0L) {
            return "-";
        }
        var date = java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
        return date.toString();
    }

    private record Section(String id, String labelKey) {
    }
}
