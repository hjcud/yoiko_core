package com.yoiko.core.client.screen;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.client.cosmetic.ClientCosmeticPreviewState;
import com.yoiko.core.config.YoikoClientConfig;
import com.yoiko.core.cosmetic.ParticleTrailManager;
import com.yoiko.core.network.MenuActionPayload;
import com.yoiko.core.network.OpenCosmeticPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class YoikoCosmeticScreen extends Screen {
    private static final int LEFT_X = YoikoMenuLayout.LEFT_X;
    private static final int LEFT_WIDTH = YoikoMenuLayout.LEFT_WIDTH;
    private static final int PANEL_TEXTURE_OVERFLOW = 25;
    private static final int CATEGORY_BUTTON_X = 40 - PANEL_TEXTURE_OVERFLOW;
    private static final int CATEGORY_BUTTON_Y = 59 - PANEL_TEXTURE_OVERFLOW;
    private static final int CATEGORY_BUTTON_WIDTH = 40;
    private static final int CATEGORY_BUTTON_HEIGHT = 18;
    private static final int CATEGORY_BUTTON_GAP = 1;
    private static final int CATEGORY_TAB_TEXTURE_WIDTH = CATEGORY_BUTTON_WIDTH * 4;
    private static final ResourceLocation CATEGORY_TAB_NORMAL =
            YoikoServerCore.id("textures/gui/cosmetic/tabs/category.png");
    private static final ResourceLocation CATEGORY_TAB_HOVER =
            YoikoServerCore.id("textures/gui/cosmetic/tabs/category_hover.png");
    private static final int GRID_X = 11;
    private static final int GRID_SLOT_Y = 55;
    private static final int GRID_COLUMNS = 9;
    private static final int GRID_ROWS = 10;
    private static final int SLOT_STEP = 19;
    private static final int SLOT_SIZE = 18;
    private static final int EQUIPMENT_SLOT_SIZE = 18;
    private static final int PAGE_SIZE = GRID_COLUMNS * GRID_ROWS;
    private static final int DETAIL_PAPER_X = YoikoMenuLayout.PROFILE_PAPER_X;
    private static final int DETAIL_PAPER_Y = YoikoMenuLayout.PROFILE_PAPER_Y;
    private static final int DETAIL_PAPER_WIDTH = YoikoMenuLayout.PROFILE_PAPER_WIDTH;
    private static final int TOP_CONTROL_Y = 22;
    private static final int FAVORITES_BUTTON_X = YoikoMenuLayout.PROFILE_CENTER_X - 64;
    private static final int PAGE_PREV_BUTTON_X = YoikoMenuLayout.PROFILE_CENTER_X - 33;
    private static final int PAGE_TEXT_CENTER_X = YoikoMenuLayout.PROFILE_CENTER_X;
    private static final int PAGE_NEXT_BUTTON_X = YoikoMenuLayout.PROFILE_CENTER_X + 18;
    private static final int DETAIL_ACTION_Y = DETAIL_PAPER_Y + 82;
    private static final int MODE_BOOKMARK_X = YoikoMenuLayout.SPLIT_RIGHT_PANEL_X + 165;
    private static final int MODE_BOOKMARK_TOP_Y = 84;
    private static final int MODE_BOOKMARK_BOTTOM_Y = 106;
    private static final int MODE_BOOKMARK_WIDTH = 18;
    private static final int MODE_BOOKMARK_HEIGHT = 19;
    private static final int PREVIEW_DURATION_TICKS = 100;

    private static final EquipmentSlotView[] EQUIPMENT_LAYOUT = new EquipmentSlotView[] {
            new EquipmentSlotView("HEAD", YoikoMenuLayout.PROFILE_SLOT_LEFT_X, YoikoMenuLayout.PROFILE_SLOT_TOP_Y),
            new EquipmentSlotView("CHEST", YoikoMenuLayout.PROFILE_SLOT_LEFT_X, YoikoMenuLayout.PROFILE_SLOT_BOTTOM_Y),
            new EquipmentSlotView("FEET", YoikoMenuLayout.PROFILE_SLOT_RIGHT_X, YoikoMenuLayout.PROFILE_SLOT_TOP_Y),
            new EquipmentSlotView("RANK", YoikoMenuLayout.PROFILE_SLOT_RIGHT_X, YoikoMenuLayout.PROFILE_SLOT_BOTTOM_Y)
    };
    private static final int[] EQUIPMENT_FOCUS_ORDER = {0, 2, 1, 3};

    private OpenCosmeticPayload payload;
    private Category category = Category.HEAD;
    private int page;
    private final List<YoikoIconButton> iconButtons = new ArrayList<>();
    private YoikoFocusGrid gridFocus;
    private YoikoFocusGrid equipmentFocus;
    private String localSelectedId = "";
    private YoikoButton equipButton;
    private YoikoButton previewButton;
    private FavoriteFilterButton favoriteFilterButton;
    private YoikoSpriteButton pagePrevButton;
    private YoikoSpriteButton pageNextButton;
    private Button storageBookmarkButton;
    private Button cosmeticBookmarkButton;

    public YoikoCosmeticScreen(OpenCosmeticPayload payload) {
        super(YoikoClientText.tr("yoiko_core.screen.cosmetic"));
        this.payload = payload;
        this.localSelectedId = payload.selectedId();
        this.category = Category.fromType(payload.category());
        this.page = Math.max(0, payload.page());
    }

    @Override
    protected void init() {
        refreshCategoryTabTextures();
        rebuild();
        applyClientPreview(selected());
        YoikoMousePosition.restoreIfRemembered();
    }

    public void update(OpenCosmeticPayload payload) {
        String previousSelection = localSelectedId;
        this.payload = payload;
        this.category = Category.fromType(payload.category());
        this.page = Math.max(0, payload.page());
        this.localSelectedId = payload.cosmetics().stream().anyMatch(entry -> entry.id().equals(previousSelection))
                ? previousSelection : payload.selectedId();
        applyClientPreview(selected());
        rebuild();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YoikoScreenStyle.renderBackdrop(graphics, this.width, this.height);
        int left = left();
        int top = top();
        YoikoScreenStyle.renderCosmeticRightPanel(graphics, left + YoikoMenuLayout.SPLIT_RIGHT_PANEL_X, top);
        YoikoScreenStyle.renderCosmeticLeftPanel(graphics, left, top);
        renderInventory(graphics, left, top, mouseX, mouseY);
        renderDetail(graphics, left, top, mouseX, mouseY);
        if (!payload.message().isBlank()) {
            graphics.drawString(this.font, YoikoClientText.data(payload.message()).copy().withStyle(ChatFormatting.YELLOW), left + GRID_X, top + YoikoMenuLayout.TAB_PANEL_HEIGHT - 12, 0xFFEAD7B0, false);
        }
        YoikoScreenStyle.renderWidgets(this, graphics, mouseX, mouseY, partialTick);
        ClientServerRequestState.render(graphics, this.font, "cosmetic", this.width / 2, this.height - 30);

        OpenCosmeticPayload.EquippedSlot hoveredSlot = equippedAt(mouseX, mouseY);
        OpenCosmeticPayload.Entry equippedEntry = hoveredSlot == null ? null : entryById(hoveredSlot.cosmeticId());
        OpenCosmeticPayload.Entry hovered = equippedEntry == null ? entryAt(mouseX, mouseY) : equippedEntry;
        if (equipButton != null && equipButton.isHoveredOrFocused() && !equipButton.active) {
            graphics.renderTooltip(this.font, cosmeticDisabledReason(selected()), mouseX, mouseY);
        } else if (previewButton != null && previewButton.isHoveredOrFocused()) {
            graphics.renderTooltip(this.font,
                    YoikoClientText.tr("yoiko_core.ui.cosmetic.preview_hint", PREVIEW_DURATION_TICKS / 20),
                    mouseX, mouseY);
        } else if (favoriteFilterButton != null && favoriteFilterButton.isHoveredOrFocused()) {
            graphics.renderTooltip(this.font, favoriteFilterButton.getMessage(), mouseX, mouseY);
        } else if (pagePrevButton != null && pagePrevButton.isHoveredOrFocused()) {
            graphics.renderTooltip(this.font, pagePrevButton.getMessage(), mouseX, mouseY);
        } else if (pageNextButton != null && pageNextButton.isHoveredOrFocused()) {
            graphics.renderTooltip(this.font, pageNextButton.getMessage(), mouseX, mouseY);
        } else if (storageBookmarkButton != null && storageBookmarkButton.isHovered()) {
            graphics.renderTooltip(this.font, storageBookmarkButton.getMessage(), mouseX, mouseY);
        } else if (cosmeticBookmarkButton != null && cosmeticBookmarkButton.isHovered()) {
            graphics.renderTooltip(this.font, cosmeticBookmarkButton.getMessage(), mouseX, mouseY);
        } else if (hovered != null) {
            graphics.renderTooltip(this.font, YoikoClientText.data(hovered.displayName()), mouseX, mouseY);
        } else if (hoveredSlot != null) {
            graphics.renderTooltip(this.font, Component.literal(typeLabel(hoveredSlot.type())), mouseX, mouseY);
        } else {
            YoikoNavigationTabs.renderTooltip(graphics, this.font, iconButtons, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            OpenCosmeticPayload.Entry entry = entryAt((int) mouseX, (int) mouseY);
            if (entry != null) {
                send("cosmetic_favorite|" + entry.id());
                return true;
            }
        }
        if (button == 0) {
            OpenCosmeticPayload.EquippedSlot slot = equippedAt((int) mouseX, (int) mouseY);
            if (slot != null && !slot.cosmeticId().isBlank()) {
                focusEquipmentSlot(slot.type());
                category = categoryForEquipped(slot);
                send("cosmetic_select|" + slot.cosmeticId());
                return true;
            }
            OpenCosmeticPayload.Entry entry = entryAt((int) mouseX, (int) mouseY);
            if (entry != null) {
                focusGridEntry(visibleEntries().indexOf(entry));
                selectLocally(entry);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D && isOverGrid(mouseX, mouseY)) {
            changePage(scrollY < 0.0D ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuild() {
        boolean restoreGridFocus = gridFocus != null && gridFocus.isFocused();
        int previousGridIndex = gridFocus == null ? 0 : gridFocus.focusedIndex();
        boolean restoreEquipmentFocus = equipmentFocus != null && equipmentFocus.isFocused();
        int previousEquipmentIndex = equipmentFocus == null ? 0 : equipmentFocus.focusedIndex();
        clearWidgets();
        iconButtons.clear();
        int left = left();
        int top = top();
        YoikoNavigationTabs.add(iconButtons, left, top, "storage", this::send, button -> this.addRenderableWidget(button));
        storageBookmarkButton = this.addRenderableWidget(new YoikoPaperModeButton(
                left + MODE_BOOKMARK_X,
                top + MODE_BOOKMARK_TOP_Y,
                MODE_BOOKMARK_WIDTH,
                MODE_BOOKMARK_HEIGHT,
                YoikoClientText.tr("yoiko_core.ui.storage.mode_storage"),
                false,
                ignored -> {
                    YoikoMousePosition.remember();
                    send("storage_open");
                }
        ));
        cosmeticBookmarkButton = this.addRenderableWidget(new YoikoPaperModeButton(
                left + MODE_BOOKMARK_X,
                top + MODE_BOOKMARK_BOTTOM_Y,
                MODE_BOOKMARK_WIDTH,
                MODE_BOOKMARK_HEIGHT,
                YoikoClientText.tr("yoiko_core.ui.storage.mode_cosmetic"),
                true,
                ignored -> {
                }
        ));
        cosmeticBookmarkButton.active = false;
        addCategoryButtons(left, top);
        addPageButtons(left, top);
        favoriteFilterButton = new FavoriteFilterButton(
                left + FAVORITES_BUTTON_X,
                top + TOP_CONTROL_Y,
                YoikoClientText.tr(payload.favoritesOnly()
                        ? "yoiko_core.ui.cosmetic.show_all"
                        : "yoiko_core.ui.cosmetic.favorites_only"),
                payload.favoritesOnly()
        );
        this.addRenderableWidget(favoriteFilterButton);

        OpenCosmeticPayload.Entry selected = selected();
        previewButton = YoikoButton.create(
                left + DETAIL_PAPER_X + DETAIL_PAPER_WIDTH - 27,
                top + DETAIL_PAPER_Y + 5,
                18,
                18,
                YoikoClientText.tr("yoiko_core.ui.cosmetic.preview"),
                ignored -> startWorldPreview()
        ).withCosmeticStyle().withEyeIcon().withoutChrome();
        previewButton.active = selected != null && !isRank(selected);
        this.addRenderableWidget(previewButton);
        int buttonWidth = (DETAIL_PAPER_WIDTH - 24) / 2;
        equipButton = YoikoButton.create(left + DETAIL_PAPER_X + 8, top + DETAIL_ACTION_Y,
                buttonWidth, 18, equipLabel(selected), button -> sendPrimaryAction())
                .withCosmeticStyle().withoutTextShadow();
        equipButton.active = canUsePrimaryButton(selected);
        this.addRenderableWidget(equipButton);
        YoikoButton clear = YoikoButton.create(left + DETAIL_PAPER_X + 16 + buttonWidth, top + DETAIL_ACTION_Y,
                buttonWidth, 18, YoikoClientText.tr("yoiko_core.ui.cosmetic.clear_all"), button -> send("cosmetic_clear"))
                .withCosmeticStyle().withoutTextShadow();
        clear.active = category != Category.RANK;
        this.addRenderableWidget(clear);
        gridFocus = this.addRenderableWidget(new YoikoFocusGrid(
                left + GRID_X, top + GRID_SLOT_Y, GRID_COLUMNS, GRID_ROWS, SLOT_SIZE, SLOT_SIZE,
                SLOT_STEP, SLOT_STEP, () -> visibleEntries().size(), this::activateGridEntry).withoutFocusOutline());
        OpenCosmeticPayload.Entry selectedEntry = selected();
        gridFocus.setFocusedIndex(restoreGridFocus
                ? previousGridIndex
                : selectedEntry == null ? 0 : Math.max(0, visibleEntries().indexOf(selectedEntry)));
        int equipmentX = left + EQUIPMENT_LAYOUT[0].x();
        int equipmentY = top + EQUIPMENT_LAYOUT[0].y();
        equipmentFocus = this.addRenderableWidget(new YoikoFocusGrid(
                equipmentX, equipmentY, 2, 2, EQUIPMENT_SLOT_SIZE, EQUIPMENT_SLOT_SIZE,
                EQUIPMENT_LAYOUT[2].x() - EQUIPMENT_LAYOUT[0].x(),
                EQUIPMENT_LAYOUT[1].y() - EQUIPMENT_LAYOUT[0].y(),
                () -> EQUIPMENT_FOCUS_ORDER.length, this::activateEquipmentSlot).withoutFocusOutline());
        equipmentFocus.setFocusedIndex(previousEquipmentIndex);
        if (restoreGridFocus) {
            setFocused(gridFocus);
        } else if (restoreEquipmentFocus) {
            setFocused(equipmentFocus);
        }
    }

    private void activateGridEntry(int index) {
        List<OpenCosmeticPayload.Entry> entries = visibleEntries();
        if (index >= 0 && index < entries.size()) {
            OpenCosmeticPayload.Entry entry = entries.get(index);
            selectLocally(entry);
        }
    }

    private void selectLocally(OpenCosmeticPayload.Entry entry) {
        localSelectedId = entry.id();
        applyClientPreview(entry);
        rebuild();
    }

    private void focusGridEntry(int index) {
        if (gridFocus != null && index >= 0) {
            setFocused(gridFocus);
            gridFocus.setFocusedIndex(index);
        }
    }

    private void activateEquipmentSlot(int focusIndex) {
        if (focusIndex < 0 || focusIndex >= EQUIPMENT_FOCUS_ORDER.length) {
            return;
        }
        EquipmentSlotView view = EQUIPMENT_LAYOUT[EQUIPMENT_FOCUS_ORDER[focusIndex]];
        OpenCosmeticPayload.EquippedSlot slot = equippedSlotByType(view.type());
        if (slot == null || slot.cosmeticId().isBlank()) {
            return;
        }
        category = categoryForEquipped(slot);
        send("cosmetic_select|" + slot.cosmeticId());
    }

    private Category categoryForEquipped(OpenCosmeticPayload.EquippedSlot slot) {
        return Category.fromType(slot.type());
    }

    private void focusEquipmentSlot(String type) {
        if (equipmentFocus == null) {
            return;
        }
        for (int focusIndex = 0; focusIndex < EQUIPMENT_FOCUS_ORDER.length; focusIndex++) {
            if (EQUIPMENT_LAYOUT[EQUIPMENT_FOCUS_ORDER[focusIndex]].type().equals(type)) {
                setFocused(equipmentFocus);
                equipmentFocus.setFocusedIndex(focusIndex);
                return;
            }
        }
    }

    private void addCategoryButtons(int left, int top) {
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category option = categories[i];
            CosmeticCategoryButton button = new CosmeticCategoryButton(
                    left + CATEGORY_BUTTON_X + i * (CATEGORY_BUTTON_WIDTH + CATEGORY_BUTTON_GAP),
                    top + CATEGORY_BUTTON_Y,
                    option,
                    i
            );
            button.active = option != category;
            this.addRenderableWidget(button);
        }
    }

    private void addPageButtons(int left, int top) {
        int y = top + TOP_CONTROL_Y;
        pagePrevButton = YoikoSpriteButton.create(
                left + PAGE_PREV_BUTTON_X, y,
                YoikoClientText.tr("yoiko_core.ui.previous"),
                YoikoSpriteButton.Sprite.PAGE_LEFT,
                ignored -> changePage(-1));
        pagePrevButton.active = page > 0;
        this.addRenderableWidget(pagePrevButton);
        pageNextButton = YoikoSpriteButton.create(
                left + PAGE_NEXT_BUTTON_X, y,
                YoikoClientText.tr("yoiko_core.ui.next"),
                YoikoSpriteButton.Sprite.PAGE_RIGHT,
                ignored -> changePage(1));
        pageNextButton.active = page < pageCount() - 1;
        this.addRenderableWidget(pageNextButton);
    }

    private void renderInventory(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        String pageText = (page + 1) + "/" + pageCount();
        graphics.drawCenteredString(this.font, Component.literal(pageText),
                left + PAGE_TEXT_CENTER_X,
                top + TOP_CONTROL_Y + 5, 0xFF76522F);
        List<OpenCosmeticPayload.Entry> entries = visibleEntries();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    YoikoClientText.tr(payload.favoritesOnly()
                            ? "yoiko_core.ui.empty.favorites" : "yoiko_core.ui.empty.category"),
                    left + GRID_X + (GRID_COLUMNS * SLOT_STEP) / 2,
                    top + GRID_SLOT_Y + (GRID_ROWS * SLOT_STEP) / 2,
                    0xFFB8B8B8);
        }
        for (int i = 0; i < GRID_COLUMNS * GRID_ROWS; i++) {
            int x = left + GRID_X + (i % GRID_COLUMNS) * SLOT_STEP;
            int y = top + GRID_SLOT_Y + (i / GRID_COLUMNS) * SLOT_STEP;
            if (i >= entries.size()) {
                continue;
            }
            OpenCosmeticPayload.Entry entry = entries.get(i);
            drawEntryIcon(graphics, x + 1, y + 1, 16, entry);
            if (!entry.owned()) {
                YoikoScreenStyle.renderCosmeticCompactLockedSlot(graphics, x, y);
            }
            if (entry.equipped()) {
                graphics.drawString(this.font, Component.literal("E"), x + 11, y + 10, 0xFFFFD36A, false);
            }
            if (entry.favorite()) {
                YoikoScreenStyle.renderFavoriteIcon(graphics, x, y, SLOT_SIZE);
            }
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                YoikoScreenStyle.renderSlotMask(graphics, x, y, SLOT_SIZE, SLOT_SIZE, 0xEAD7B0, 1);
            }
        }
    }

    private void renderDetail(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        renderEquipped(graphics, left, top, mouseX, mouseY);

        int cardX = left + DETAIL_PAPER_X;
        int cardY = top + DETAIL_PAPER_Y;
        int cardWidth = DETAIL_PAPER_WIDTH;

        OpenCosmeticPayload.Entry entry = selected();
        if (entry == null) {
            graphics.drawCenteredString(this.font, YoikoClientText.tr("yoiko_core.ui.no_selection"),
                    cardX + cardWidth / 2, cardY + 54, 0xFF8F8068);
            return;
        }

        int iconX = cardX + 9;
        int iconY = cardY + 14;
        YoikoScreenStyle.renderCosmeticPhotoFrame(graphics, iconX, iconY, 42, 42);
        drawEntryIcon(graphics, iconX + 5, iconY + 5, 32, entry);

        int metaLabelX = cardX + 58;
        int metaRightX = cardX + cardWidth - 9;
        drawFitted(graphics, YoikoClientText.data(entry.displayName()), metaLabelX, cardY + 14,
                cardX + cardWidth - 31 - metaLabelX, entry.owned() ? 0xFFB06B24 : 0xFF8F8068);
        YoikoScreenStyle.renderCosmeticDivider(graphics, metaLabelX, cardY + 26,
                cardX + cardWidth - 9 - metaLabelX);

        drawMetaRow(graphics, YoikoClientText.tr("yoiko_core.ui.cosmetic.creator"),
                Component.literal(entry.creator().isBlank() ? "-" : entry.creator()),
                metaLabelX, metaRightX, cardY + 31, 0xFF60452F);
        Component rarity = entry.rarity().isBlank()
                ? Component.literal("-")
                : YoikoClientText.tr("yoiko_core.rarity."
                        + ("MYTHIC".equals(entry.rarity()) ? "mystic" : "legendary"));
        int rarityColor = entry.rarity().isBlank()
                ? 0xFF8F8068
                : "MYTHIC".equals(entry.rarity()) ? 0xFFD35D4E : 0xFFB87A2B;
        drawMetaRow(graphics, YoikoClientText.tr("yoiko_core.ui.cosmetic.rarity"), rarity,
                metaLabelX, metaRightX, cardY + 44, rarityColor);
        drawMetaRow(graphics, YoikoClientText.tr("yoiko_core.ui.acquisition_path"),
                YoikoClientText.data(entry.acquisitionPath()),
                metaLabelX, metaRightX, cardY + 57, 0xFF60452F);

        boolean showGemPrice = !entry.owned() && entry.gemPrice() > 0L;
        if (showGemPrice) {
            int priceX = iconX + 4;
            int priceY = cardY + 62;
            YoikoCurrencyIconRenderer.render(graphics, "GEM", priceX, priceY + 1);
            graphics.drawString(this.font, Component.literal(Long.toString(entry.gemPrice())),
                    priceX + YoikoCurrencyIconRenderer.ICON_SIZE + YoikoCurrencyIconRenderer.TEXT_GAP,
                    priceY, payload.gems() >= entry.gemPrice() ? 0xFF65784C : 0xFFD35D4E, false);
        }

    }

    private void renderEquipped(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        renderPlayerPreview(graphics,
                left + YoikoMenuLayout.PROFILE_PLAYER_X,
                top + YoikoMenuLayout.PROFILE_PLAYER_Y,
                YoikoMenuLayout.PROFILE_PLAYER_WIDTH,
                YoikoMenuLayout.PROFILE_PLAYER_HEIGHT,
                mouseX, mouseY);

        for (EquipmentSlotView view : EQUIPMENT_LAYOUT) {
            OpenCosmeticPayload.EquippedSlot slot = equippedSlotByType(view.type());
            OpenCosmeticPayload.Entry entry = slot == null ? null : entryById(slot.cosmeticId());
            int x = left + view.x();
            int y = top + view.y();
            if (entry != null) {
                drawEntryIcon(graphics, x + 1, y + 1, 16, entry);
                if (entry.favorite()) {
                    YoikoScreenStyle.renderFavoriteIcon(graphics, x + 10, y + 10, 8);
                }
            } else {
                YoikoScreenStyle.renderCosmeticEquipmentGlyph(graphics, x, y, EQUIPMENT_SLOT_SIZE, view.type());
            }
            if (mouseX >= x && mouseX < x + EQUIPMENT_SLOT_SIZE
                    && mouseY >= y && mouseY < y + EQUIPMENT_SLOT_SIZE) {
                YoikoScreenStyle.renderCosmeticSlotHighlight(graphics, x, y, EQUIPMENT_SLOT_SIZE);
            }
        }
    }

    private void renderPlayerPreview(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        LivingEntity entity = Minecraft.getInstance().player;
        if (entity == null) {
            return;
        }
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                x + 3, y + 3, x + width - 3, y + height - 3,
                27, 0.0625F, mouseX, mouseY, entity);
    }

    private void sendPrimaryAction() {
        OpenCosmeticPayload.Entry entry = selected();
        if (entry == null) {
            return;
        }
        if (!entry.owned()) {
            if (entry.gemPrice() > 0L) {
                send("cosmetic_exchange|" + entry.id());
            }
            return;
        }
        if (isRank(entry)) {
            send((entry.equipped() ? "cosmetic_unequip|" : "cosmetic_rank_equip|") + entry.id());
            return;
        }
        send((entry.equipped() ? "cosmetic_unequip|" : "cosmetic_equip|") + entry.id());
    }

    private void startWorldPreview() {
        OpenCosmeticPayload.Entry entry = selected();
        if (entry == null || isRank(entry)) {
            return;
        }
        ParticleTrailManager.clear();
        ClientCosmeticPreviewState.startTimed(
                entry.id(), entry.type(), entry.modelId(), entry.modelAnchor(),
                entry.modelPrimaryColor(), entry.modelAccentColor(), entry.particleCategory(),
                YoikoClientText.dataText(entry.displayName()), entry.color(), PREVIEW_DURATION_TICKS
        );
        Minecraft.getInstance().setScreen(null);
    }

    private static void applyClientPreview(OpenCosmeticPayload.Entry entry) {
        if (entry == null || entry.owned()) {
            ClientCosmeticPreviewState.clear();
            return;
        }
        ClientCosmeticPreviewState.set(entry.id(), entry.type(), entry.modelId(), entry.modelAnchor(),
                entry.modelPrimaryColor(), entry.modelAccentColor(), entry.particleCategory(),
                YoikoClientText.dataText(entry.displayName()), entry.color());
    }

    @Override
    public void removed() {
        if (!ClientCosmeticPreviewState.isTimed()) {
            ClientCosmeticPreviewState.clear();
        }
        super.removed();
    }

    private Component equipLabel(OpenCosmeticPayload.Entry entry) {
        if (entry != null && !entry.owned() && entry.gemPrice() > 0L) {
            return YoikoClientText.tr("yoiko_core.ui.cosmetic.exchange", entry.gemPrice());
        }
        if (entry == null || !entry.owned()) {
            return YoikoClientText.tr("yoiko_core.ui.cosmetic.equip");
        }
        return YoikoClientText.tr(entry.equipped() ? "yoiko_core.ui.cosmetic.unequip" : "yoiko_core.ui.cosmetic.equip");
    }

    private boolean canUsePrimaryButton(OpenCosmeticPayload.Entry entry) {
        if (entry == null) {
            return false;
        }
        if (!entry.owned()) {
            return entry.gemPrice() > 0L && payload.gems() >= entry.gemPrice();
        }
        return true;
    }

    private Component cosmeticDisabledReason(OpenCosmeticPayload.Entry entry) {
        if (entry == null) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.select_item");
        }
        if (!entry.owned()) {
            return entry.gemPrice() <= 0L
                    ? YoikoClientText.tr("yoiko_core.ui.disabled.exchange_unavailable")
                    : YoikoClientText.tr("yoiko_core.ui.disabled.not_enough_gems");
        }
        if (isRank(entry) && entry.equipped()) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.already_equipped");
        }
        return YoikoClientText.tr("yoiko_core.ui.disabled.unavailable");
    }

    private OpenCosmeticPayload.Entry selected() {
        List<OpenCosmeticPayload.Entry> entries = visibleEntries();
        String selectedId = localSelectedId;
        for (OpenCosmeticPayload.Entry entry : entries) {
            if (entry.id().equals(selectedId)) {
                return entry;
            }
        }
        return entries.isEmpty() ? null : entries.get(0);
    }

    private String selectedId() {
        OpenCosmeticPayload.Entry selected = selected();
        return selected == null ? "" : selected.id();
    }

    private List<OpenCosmeticPayload.Entry> visibleEntries() {
        return categoryEntries();
    }

    private List<OpenCosmeticPayload.Entry> categoryEntries() {
        List<OpenCosmeticPayload.Entry> entries = new ArrayList<>();
        for (OpenCosmeticPayload.Entry entry : payload.cosmetics()) {
            if (category.matches(entry)) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private int pageCount() {
        return Math.max(1, payload.totalPages());
    }

    private void changePage(int delta) {
        int next = Math.max(0, Math.min(page + delta, pageCount() - 1));
        if (next == page) {
            return;
        }
        page = next;
        sendView();
    }

    private void sendView() {
        send("cosmetic_view|" + category.name() + "|" + page);
    }

    private boolean isOverGrid(double mouseX, double mouseY) {
        int x = left() + GRID_X;
        int y = top() + GRID_SLOT_Y;
        return mouseX >= x && mouseX < x + GRID_COLUMNS * SLOT_STEP
                && mouseY >= y && mouseY < y + GRID_ROWS * SLOT_STEP;
    }

    private OpenCosmeticPayload.Entry entryById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (OpenCosmeticPayload.Entry entry : payload.cosmetics()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        for (OpenCosmeticPayload.Entry entry : payload.equippedEntries()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private OpenCosmeticPayload.EquippedSlot equippedSlotByType(String type) {
        for (OpenCosmeticPayload.EquippedSlot slot : payload.equippedSlots()) {
            if (slot.type().equals(type)) {
                return slot;
            }
        }
        return null;
    }

    private OpenCosmeticPayload.EquippedSlot equippedAt(int mouseX, int mouseY) {
        int left = left();
        int top = top();
        for (EquipmentSlotView view : EQUIPMENT_LAYOUT) {
            int x = left + view.x();
            int y = top + view.y();
            if (mouseX >= x && mouseX < x + EQUIPMENT_SLOT_SIZE
                    && mouseY >= y && mouseY < y + EQUIPMENT_SLOT_SIZE) {
                return equippedSlotByType(view.type());
            }
        }
        return null;
    }

    private OpenCosmeticPayload.Entry entryAt(int mouseX, int mouseY) {
        int left = left();
        int top = top();
        int gridX = left + GRID_X;
        int gridY = top + GRID_SLOT_Y;
        if (mouseX < gridX || mouseY < gridY) {
            return null;
        }
        int localX = mouseX - gridX;
        int localY = mouseY - gridY;
        int column = localX / SLOT_STEP;
        int row = localY / SLOT_STEP;
        if (column < 0 || column >= GRID_COLUMNS || row < 0 || row >= GRID_ROWS) {
            return null;
        }
        if (localX % SLOT_STEP >= SLOT_SIZE || localY % SLOT_STEP >= SLOT_SIZE) {
            return null;
        }
        int index = row * GRID_COLUMNS + column;
        List<OpenCosmeticPayload.Entry> entries = visibleEntries();
        return index < entries.size() ? entries.get(index) : null;
    }

    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        if (width <= 0) {
            return;
        }
        String fitted = this.font.plainSubstrByWidth(text.getString(), width);
        graphics.drawString(this.font, Component.literal(fitted), x, y, color, false);
    }

    private void drawMetaRow(GuiGraphics graphics, Component label, Component value,
                             int left, int right, int y, int valueColor) {
        int valueWidth = Math.min(this.font.width(value), Math.max(1, right - left));
        int valueX = right - valueWidth;
        int labelWidth = Math.max(1, valueX - left - 3);
        drawFitted(graphics, label, left, y, labelWidth, 0xFF596341);
        drawFitted(graphics, value, valueX, y, valueWidth, valueColor);
    }

    private void drawEntryIcon(GuiGraphics graphics, int x, int y, int size, OpenCosmeticPayload.Entry entry) {
        YoikoCosmeticIconRenderer.renderEntryIcon(graphics, x, y, size, entry.type(), entry.id(), entry.owned(),
                entry.color(), entry.rarity(), entry.particleCategory(), entry.modelId(),
                entry.modelPrimaryColor(), entry.modelAccentColor());
    }

    private static boolean isRank(OpenCosmeticPayload.Entry entry) {
        return entry != null && "RANK".equals(entry.type());
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "HEAD" -> YoikoClientText.text("yoiko_core.ui.cosmetic.head");
            case "CHEST" -> YoikoClientText.text("yoiko_core.ui.cosmetic.chest");
            case "PARTICLE" -> YoikoClientText.text("yoiko_core.ui.cosmetic.particle");
            case "FEET" -> YoikoClientText.text("yoiko_core.ui.cosmetic.feet");
            case "RANK" -> YoikoClientText.text("yoiko_core.ui.cosmetic.rank");
            default -> type;
        };
    }

    private static String equipSlotLabel(OpenCosmeticPayload.Entry entry) {
        if (entry == null || !"PARTICLE".equals(entry.type())) {
            return entry == null ? "" : typeLabel(entry.type());
        }
        return switch (entry.particleCategory()) {
            case "RING", "COMPANION" -> typeLabel("HEAD");
            case "WINGS", "AURA", "ORBIT" -> typeLabel("CHEST");
            case "TRAIL" -> typeLabel("FEET");
            default -> typeLabel("PARTICLE");
        };
    }

    private void send(String action) {
        if (!ClientServerRequestState.begin(this, action)) {
            return;
        }
        PacketDistributor.sendToServer(ClientMenuSession.action(action));
    }

    private static void refreshCategoryTabTextures() {
        var textureManager = Minecraft.getInstance().getTextureManager();
        textureManager.release(CATEGORY_TAB_NORMAL);
        textureManager.release(CATEGORY_TAB_HOVER);
    }

    private int left() {
        return YoikoMenuLayout.splitLeft(this.width);
    }

    private int top() {
        return YoikoMenuLayout.splitTop(this.height);
    }

    private record EquipmentSlotView(String type, int x, int y) {
    }

    private final class FavoriteFilterButton extends AbstractButton {
        private final boolean selected;

        private FavoriteFilterButton(int x, int y, Component message, boolean selected) {
            super(x, y, 18, 18, message);
            this.selected = selected;
        }

        @Override
        public void onPress() {
            send("cosmetic_favorites_only");
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            YoikoScreenStyle.renderCosmeticButton(graphics, getX(), getY(), width, height,
                    selected || isHoveredOrFocused(), active);
            YoikoScreenStyle.renderFavoriteIcon(graphics, getX() + 2, getY() + 2, 14);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            if (YoikoClientConfig.UI_SOUNDS.get()) {
                super.playDownSound(soundManager);
            }
        }
    }

    private final class CosmeticCategoryButton extends AbstractButton {
        private final Category option;
        private final int spriteIndex;

        private CosmeticCategoryButton(int x, int y, Category option, int spriteIndex) {
            super(x, y, CATEGORY_BUTTON_WIDTH, CATEGORY_BUTTON_HEIGHT, Component.literal(option.label()));
            this.option = option;
            this.spriteIndex = spriteIndex;
        }

        @Override
        public void onPress() {
            category = option;
            page = 0;
            sendView();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean highlighted = option == category || (this.active && this.isHoveredOrFocused());
            ResourceLocation texture = highlighted
                    ? CATEGORY_TAB_HOVER
                    : CATEGORY_TAB_NORMAL;
            graphics.blit(
                    texture,
                    getX(),
                    getY(),
                    CATEGORY_BUTTON_WIDTH,
                    CATEGORY_BUTTON_HEIGHT,
                    (float) (spriteIndex * CATEGORY_BUTTON_WIDTH),
                    0.0F,
                    CATEGORY_BUTTON_WIDTH,
                    CATEGORY_BUTTON_HEIGHT,
                    CATEGORY_TAB_TEXTURE_WIDTH,
                    CATEGORY_BUTTON_HEIGHT
            );
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            if (YoikoClientConfig.UI_SOUNDS.get()) {
                super.playDownSound(soundManager);
            }
        }
    }

    private enum Category {
        HEAD("yoiko_core.ui.cosmetic.head"),
        CHEST("yoiko_core.ui.cosmetic.chest"),
        FEET("yoiko_core.ui.cosmetic.feet"),
        RANK("yoiko_core.ui.cosmetic.rank");

        private final String labelKey;

        Category(String labelKey) {
            this.labelKey = labelKey;
        }

        String label() {
            return YoikoClientText.text(labelKey);
        }

        boolean matches(OpenCosmeticPayload.Entry entry) {
            String slot = equipSlot(entry);
            return switch (this) {
                case HEAD -> "HEAD".equals(slot);
                case CHEST -> "CHEST".equals(slot);
                case FEET -> "FEET".equals(slot);
                case RANK -> "RANK".equals(slot);
            };
        }

        private static String equipSlot(OpenCosmeticPayload.Entry entry) {
            if (entry == null || !"PARTICLE".equals(entry.type())) {
                return entry == null ? "" : entry.type();
            }
            return switch (entry.particleCategory()) {
                case "RING", "COMPANION" -> "HEAD";
                case "WINGS", "AURA", "ORBIT" -> "CHEST";
                case "TRAIL" -> "FEET";
                default -> "";
            };
        }

        static Category fromType(String type) {
            return switch (type) {
                case "HEAD" -> HEAD;
                case "CHEST" -> CHEST;
                case "FEET", "PARTICLE" -> FEET;
                case "RANK" -> RANK;
                default -> HEAD;
            };
        }
    }
}
