package com.yoiko.core.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.client.YoikoClientKeys;
import com.yoiko.core.network.MenuActionPayload;
import com.yoiko.core.network.OpenRelicPayload;
import com.yoiko.core.storage.YoikoStorageMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

public class YoikoStorageScreen extends AbstractContainerScreen<YoikoStorageMenu> {
    private static final ResourceLocation SORT_ICON =
            YoikoServerCore.id("textures/gui/storage/sort.png");
    private static final int SORT_BUTTON_SIZE = 12;
    private static final int SORT_BUTTON_GAP = 3;
    private static final int PANEL_TEXTURE_OVERFLOW = 25;
    private static final int RIGHT_PANEL_X = YoikoMenuLayout.SPLIT_RIGHT_PANEL_X;
    private static final int PROFILE_CONTENT_SHIFT_X = -10;
    private static final int PROFILE_FACE_X = RIGHT_PANEL_X + 55 - PANEL_TEXTURE_OVERFLOW + PROFILE_CONTENT_SHIFT_X;
    private static final int PROFILE_FACE_Y = 71 - PANEL_TEXTURE_OVERFLOW;
    private static final int PROFILE_FACE_SIZE = 30;
    private static final int PROFILE_TEXT_CENTER_X = RIGHT_PANEL_X + 144 - PANEL_TEXTURE_OVERFLOW + PROFILE_CONTENT_SHIFT_X;
    private static final int PROFILE_RANK_Y = 74 - PANEL_TEXTURE_OVERFLOW - 4;
    private static final int PROFILE_NAME_Y = 99 - PANEL_TEXTURE_OVERFLOW - 4;
    private static final int RELIC_ICON_X = RIGHT_PANEL_X + 47 - PANEL_TEXTURE_OVERFLOW + PROFILE_CONTENT_SHIFT_X;
    private static final int RELIC_START_Y = 127 - PANEL_TEXTURE_OVERFLOW;
    private static final int RELIC_TEXT_X = RIGHT_PANEL_X + 68 - PANEL_TEXTURE_OVERFLOW + PROFILE_CONTENT_SHIFT_X;
    private static final int RELIC_TEXT_RIGHT = RIGHT_PANEL_X + 194 - PANEL_TEXTURE_OVERFLOW + PROFILE_CONTENT_SHIFT_X;
    private static final int RELIC_ROW_STEP = 19;
    private static final int RELIC_ICON_SIZE = 16;
    private static final int COSMETIC_START_X = RIGHT_PANEL_X + 46 - PANEL_TEXTURE_OVERFLOW + PROFILE_CONTENT_SHIFT_X;
    private static final int COSMETIC_Y = 236 - PANEL_TEXTURE_OVERFLOW;
    private static final int COSMETIC_SLOT_STEP = 21;
    private static final int STATUS_VALUE_X = RIGHT_PANEL_X + 162 - PANEL_TEXTURE_OVERFLOW + PROFILE_CONTENT_SHIFT_X;
    private static final int STORAGE_VALUE_Y = 236 - PANEL_TEXTURE_OVERFLOW;
    private static final int MAIL_VALUE_Y = 247 - PANEL_TEXTURE_OVERFLOW;
    private static final int PROFILE_BODY_COLOR = 0xFF63452F;
    private static final int PROFILE_NAME_COLOR = 0xFF60452F;
    private static final int PROFILE_VALUE_COLOR = 0xFFE69A00;
    private static final int DETAIL_Y = YoikoMenuLayout.CONTENT_Y;
    private static final int PROFILE_PAPER_X = YoikoMenuLayout.PROFILE_PAPER_X;
    private static final int PROFILE_PAPER_Y = YoikoMenuLayout.PROFILE_PAPER_Y;
    private static final int PROFILE_PAPER_WIDTH = YoikoMenuLayout.PROFILE_PAPER_WIDTH;
    private static final int PROFILE_RELIC_X = PROFILE_PAPER_X + 9;
    private static final int PROFILE_RELIC_NAME_X = PROFILE_RELIC_X + 14;
    private static final int PROFILE_RELIC_NAME_WIDTH = 66;
    private static final int PROFILE_RELIC_START_Y = PROFILE_PAPER_Y + 44;
    private static final int PROFILE_RELIC_ROW_STEP = 11;
    private static final int PROFILE_STATS_X = PROFILE_PAPER_X + 102;
    private static final int PROFILE_STATS_RIGHT_X = PROFILE_PAPER_X + PROFILE_PAPER_WIDTH - 9;
    private static final float PROFILE_HEADER_NAME_SCALE = 1.0F;
    private static final int PROFILE_HEADER_TITLE_Y = DETAIL_Y + 1;
    private static final int PROFILE_HEADER_NAME_Y = DETAIL_Y + 13;
    private static final int MODE_BOOKMARK_X = YoikoMenuLayout.SPLIT_RIGHT_PANEL_X + 165;
    private static final int MODE_BOOKMARK_TOP_Y = 84;
    private static final int MODE_BOOKMARK_BOTTOM_Y = 106;
    private static final int MODE_BOOKMARK_WIDTH = 18;
    private static final int MODE_BOOKMARK_HEIGHT = 19;
    // storage.png includes 25 px of overflow around the logical 192x260 panel.
    private static final int STORAGE_TEXTURE_OVERFLOW = 25;
    private static final int PAGE_PREV_BUTTON_X = 160 - STORAGE_TEXTURE_OVERFLOW;
    private static final int PAGE_NEXT_BUTTON_X = 192 - STORAGE_TEXTURE_OVERFLOW;
    private static final int PAGE_BUTTON_Y = 170 - STORAGE_TEXTURE_OVERFLOW;
    private static final int PAGE_TEXT_CENTER_X = (PAGE_PREV_BUTTON_X + 18 + PAGE_NEXT_BUTTON_X) / 2;
    private static final int CONTROL_TEXT_Y = PAGE_BUTTON_Y + 5;
    private static final int SLOT_TEXT_RIGHT_X = PAGE_PREV_BUTTON_X - 4;
    private static final int PLAYER_INVENTORY_LABEL_Y = 151;
    private static final int SORT_BUTTON_Y = PLAYER_INVENTORY_LABEL_Y - 2;
    private final List<YoikoIconButton> iconButtons = new ArrayList<>();
    private YoikoSpriteButton pagePrevButton;
    private YoikoSpriteButton pageNextButton;
    private Button sortButton;
    private Button profileButton;
    private Button storageBookmarkButton;
    private Button cosmeticBookmarkButton;
    private static boolean lockKeyHandled;
    private static boolean sortKeyHandled;

    public YoikoStorageScreen(YoikoStorageMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = YoikoMenuLayout.SPLIT_PANEL_WIDTH;
        this.imageHeight = YoikoMenuLayout.TAB_PANEL_HEIGHT;
        this.titleLabelX = 12;
        this.titleLabelY = YoikoMenuLayout.HEADER_Y;
        this.inventoryLabelX = 12;
        this.inventoryLabelY = PLAYER_INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        if (!YoikoClientKeys.STORAGE_SLOT_LOCK.isDown()) lockKeyHandled = false;
        if (!YoikoClientKeys.STORAGE_SORT.isDown()) sortKeyHandled = false;
        YoikoCosmeticIconRenderer.refreshProfileSlotTextures();
        iconButtons.clear();
        pagePrevButton = addPageButton(YoikoSpriteButton.Sprite.PAGE_LEFT, pagePrevButtonX(), "storage_page_prev", menu.getPage() > 0);
        pageNextButton = addPageButton(YoikoSpriteButton.Sprite.PAGE_RIGHT, pageNextButtonX(), "storage_page_next", menu.getPage() + 1 < menu.getTotalPages());
        sortButton = this.addRenderableWidget(new SortButton(
                sortButtonX(),
                topPos + SORT_BUTTON_Y,
                YoikoClientText.tr("yoiko_core.ui.storage.sort_hint",
                        YoikoClientKeys.STORAGE_SORT.getTranslatedKeyMessage()),
                ignored -> send("storage_sort_page")
        ));
        profileButton = this.addRenderableWidget(YoikoButton.create(
                leftPos + PROFILE_PAPER_X + PROFILE_PAPER_WIDTH - 27,
                topPos + PROFILE_PAPER_Y + 6,
                18,
                18,
                YoikoClientText.tr("yoiko_core.ui.profile.open"),
                ignored -> send("profile_open")
        ).withCosmeticStyle().withEyeIcon().withoutChrome());
        storageBookmarkButton = this.addRenderableWidget(new YoikoPaperModeButton(
                leftPos + MODE_BOOKMARK_X,
                topPos + MODE_BOOKMARK_TOP_Y,
                MODE_BOOKMARK_WIDTH,
                MODE_BOOKMARK_HEIGHT,
                YoikoClientText.tr("yoiko_core.ui.storage.mode_storage"),
                true,
                ignored -> {
                }
        ));
        storageBookmarkButton.active = false;
        cosmeticBookmarkButton = this.addRenderableWidget(new YoikoPaperModeButton(
                leftPos + MODE_BOOKMARK_X,
                topPos + MODE_BOOKMARK_BOTTOM_Y,
                MODE_BOOKMARK_WIDTH,
                MODE_BOOKMARK_HEIGHT,
                YoikoClientText.tr("yoiko_core.ui.storage.mode_cosmetic"),
                false,
                ignored -> {
                    if (!menu.getCarried().isEmpty()) return;
                    YoikoMousePosition.remember();
                    send("cosmetic_open");
                }
        ));
        YoikoNavigationTabs.add(iconButtons, leftPos, topPos, "storage", this::send, button -> this.addRenderableWidget(button));
        YoikoMousePosition.restoreIfRemembered();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean cursorEmpty = menu.getCarried().isEmpty();
        if (pagePrevButton != null) pagePrevButton.active = cursorEmpty && menu.getPage() > 0;
        if (pageNextButton != null) pageNextButton.active = cursorEmpty && menu.getPage() + 1 < menu.getTotalPages();
        if (profileButton != null) profileButton.active = cursorEmpty;
        if (storageBookmarkButton != null) storageBookmarkButton.active = false;
        if (cosmeticBookmarkButton != null) cosmeticBookmarkButton.active = cursorEmpty;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderPlayerPreview(graphics, mouseX, mouseY);
        ClientServerRequestState.render(graphics, this.font, "storage", this.width / 2, this.height - 30);
        renderStorageTooltip(graphics, mouseX, mouseY);
        renderRelicTooltip(graphics, mouseX, mouseY);
        renderIconTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YoikoScreenStyle.renderBackdrop(graphics, this.width, this.height);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        YoikoScreenStyle.renderCosmeticRightPanel(graphics, leftPos + YoikoMenuLayout.SPLIT_RIGHT_PANEL_X, topPos);
        YoikoScreenStyle.renderStorageLeftPanel(graphics, leftPos, topPos);
        renderLockedSlotOverlays(graphics);
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
        Component pageText = pageText();
        Component slotText = slotText();
        int pageX = pageTextX();
        int slotX = slotTextX();
        graphics.drawString(this.font, slotText, slotX, CONTROL_TEXT_Y, 0xFF946222, false);
        graphics.drawString(this.font, pageText, pageX, CONTROL_TEXT_Y, PROFILE_NAME_COLOR, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF5F3C18, false);
        renderStatusPanel(graphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D && isOverStorageGrid(mouseX, mouseY)) {
            if (!menu.getCarried().isEmpty()) {
                return true;
            }
            boolean previous = scrollY > 0.0D;
            boolean canChangePage = previous
                    ? menu.getPage() > 0
                    : menu.getPage() + 1 < menu.getTotalPages();
            if (canChangePage) {
                YoikoMousePosition.remember();
                send(previous ? "storage_page_prev" : "storage_page_next");
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (YoikoClientKeys.STORAGE_SLOT_LOCK.matches(keyCode, scanCode)) {
            if (!lockKeyHandled) {
                lockKeyHandled = true;
                if (menu.getCarried().isEmpty()) {
                    int hoveredIndex = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
                    if (menu.isStorageSlot(hoveredIndex) && hoveredIndex < menu.getVisibleActiveSlots()) {
                        send("storage_toggle_lock|" + hoveredIndex);
                    }
                }
            }
            return true;
        }
        if (YoikoClientKeys.STORAGE_SORT.matches(keyCode, scanCode)) {
            if (!sortKeyHandled) {
                sortKeyHandled = true;
                if (menu.getCarried().isEmpty()) send("storage_sort_page");
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean handled = false;
        if (YoikoClientKeys.STORAGE_SLOT_LOCK.matches(keyCode, scanCode)) {
            lockKeyHandled = false;
            handled = true;
        }
        if (YoikoClientKeys.STORAGE_SORT.matches(keyCode, scanCode)) {
            sortKeyHandled = false;
            handled = true;
        }
        return handled || super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void renderStorageTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int hoveredMenuIndex = this.hoveredSlot == null ? -1 : menu.slots.indexOf(this.hoveredSlot);
        if (!menu.isStorageSlot(hoveredMenuIndex)
                || !menu.getCarried().isEmpty()) {
            this.renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (this.hoveredSlot.hasItem()) {
            lines.addAll(Screen.getTooltipFromItem(this.minecraft, this.hoveredSlot.getItem()));
        }
        String key = YoikoClientKeys.STORAGE_SLOT_LOCK.getTranslatedKeyMessage().getString();
        String hintKey = menu.isStorageSlotUserLocked(hoveredMenuIndex)
                ? "yoiko_core.ui.storage.unlock_hint"
                : "yoiko_core.ui.storage.lock_hint";
        lines.add(YoikoClientText.tr(hintKey, key).withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private void renderStatusPanel(GuiGraphics graphics) {
        YoikoStorageMenu.StatusSummary status = menu.getStatus();
        renderEquippedProfile(graphics, status);
        renderProfilePaper(graphics, status);
    }

    private void renderEquippedProfile(GuiGraphics graphics, YoikoStorageMenu.StatusSummary status) {
        renderProfileHeader(graphics, status);

        renderCosmeticIcon(graphics, YoikoMenuLayout.PROFILE_SLOT_LEFT_X, YoikoMenuLayout.PROFILE_SLOT_TOP_Y,
                "HEAD", status.headCosmeticId(), status.rankColor(), "");
        renderCosmeticIcon(graphics, YoikoMenuLayout.PROFILE_SLOT_LEFT_X, YoikoMenuLayout.PROFILE_SLOT_BOTTOM_Y,
                "CHEST", status.chestCosmeticId(), status.rankColor(), "");
        renderCosmeticIcon(graphics, YoikoMenuLayout.PROFILE_SLOT_RIGHT_X, YoikoMenuLayout.PROFILE_SLOT_TOP_Y,
                "FEET", status.feetCosmeticId(), status.rankColor(),
                status.feetCosmeticRarity(), status.feetCosmeticCategory());
        renderCosmeticIcon(graphics, YoikoMenuLayout.PROFILE_SLOT_RIGHT_X, YoikoMenuLayout.PROFILE_SLOT_BOTTOM_Y,
                "RANK", status.activeRankId(), status.rankColor(), "");
    }

    private void renderProfileHeader(GuiGraphics graphics, YoikoStorageMenu.StatusSummary status) {
        int centerX = YoikoMenuLayout.PROFILE_CENTER_X;
        Component rank = localizedRankName(status);
        graphics.drawCenteredString(this.font,
                Component.literal(abbreviated(rank.getString(), 112, 1.0F)),
                centerX, PROFILE_HEADER_TITLE_Y, 0xFF946222);

        String playerName = minecraft.player == null ? "-" : minecraft.player.getGameProfile().getName();
        Component abbreviatedName = Component.literal(
                abbreviated(playerName, 112, PROFILE_HEADER_NAME_SCALE));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, PROFILE_HEADER_NAME_Y, 0.0F);
        graphics.pose().scale(PROFILE_HEADER_NAME_SCALE, PROFILE_HEADER_NAME_SCALE, 1.0F);
        graphics.drawCenteredString(this.font, abbreviatedName, 0, 0, PROFILE_NAME_COLOR);
        graphics.pose().popPose();
    }

    /**
     * Entity rendering consumes screen coordinates, unlike the labels rendered by
     * AbstractContainerScreen in its translated local coordinate system.
     */
    private void renderPlayerPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        LivingEntity entity = Minecraft.getInstance().player;
        if (entity == null) {
            return;
        }
        int x = leftPos + YoikoMenuLayout.PROFILE_PLAYER_X;
        int y = topPos + YoikoMenuLayout.PROFILE_PLAYER_Y;
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                x + 3, y + 3,
                x + YoikoMenuLayout.PROFILE_PLAYER_WIDTH - 3,
                y + YoikoMenuLayout.PROFILE_PLAYER_HEIGHT - 3,
                27, 0.0625F, mouseX, mouseY, entity);

    }

    private void renderProfilePaper(GuiGraphics graphics, YoikoStorageMenu.StatusSummary status) {
        int cardX = PROFILE_PAPER_X;
        int cardY = PROFILE_PAPER_Y;
        int cardWidth = PROFILE_PAPER_WIDTH;
        graphics.drawCenteredString(this.font, YoikoClientText.tr("yoiko_core.ui.storage.profile_summary"),
                cardX + cardWidth / 2, cardY + 12, 0xFF5C5A3B);
        YoikoScreenStyle.renderCosmeticDivider(graphics, cardX + 8, cardY + 24, cardWidth - 16);

        Component equippedRelics = YoikoClientText.tr("yoiko_core.ui.storage.equipped_relic");
        Component equippedRelicCount = Component.literal(
                status.equippedRelicCount() + "/" + status.maxRelicSlots());
        int relicLabelWidth = PROFILE_RELIC_NAME_X + PROFILE_RELIC_NAME_WIDTH
                - this.font.width(equippedRelicCount) - 4 - PROFILE_RELIC_X;
        graphics.drawString(this.font,
                Component.literal(abbreviated(equippedRelics.getString(), relicLabelWidth, 1.0F)),
                PROFILE_RELIC_X, cardY + 29,
                PROFILE_NAME_COLOR, false);
        graphics.drawString(this.font, equippedRelicCount,
                PROFILE_RELIC_NAME_X + PROFILE_RELIC_NAME_WIDTH - this.font.width(equippedRelicCount), cardY + 29,
                PROFILE_VALUE_COLOR, false);

        graphics.fill(cardX + 96, cardY + 29, cardX + 97, cardY + 96, 0x49C9AA72);
        graphics.drawString(this.font, YoikoClientText.tr("yoiko_core.ui.storage.profile_stats"),
                PROFILE_STATS_X, cardY + 29, PROFILE_NAME_COLOR, false);

        List<YoikoStorageMenu.RelicSummaryEntry> relics = status.equippedRelics();
        for (int index = 0; index < Math.min(5, relics.size()); index++) {
            YoikoStorageMenu.RelicSummaryEntry relic = relics.get(index);
            int y = PROFILE_RELIC_START_Y + index * PROFILE_RELIC_ROW_STEP;
            YoikoScreenStyle.renderRelicIcon(graphics, PROFILE_RELIC_X, y - 1, 10, relic.rarity());
            String name = Component.translatable(relic.nameKey()).getString();
            graphics.drawString(this.font,
                    Component.literal(abbreviated(name, PROFILE_RELIC_NAME_WIDTH, 1.0F)),
                    PROFILE_RELIC_NAME_X, y,
                    rarityColor(relic.rarity()), false);
        }
        if (relics.isEmpty()) {
            graphics.drawString(this.font, YoikoClientText.tr("yoiko_core.ui.storage.no_relics"),
                    PROFILE_RELIC_X, PROFILE_RELIC_START_Y + 11, 0xFF8F8068, false);
        }

        drawProfileStat(graphics, YoikoClientText.tr("yoiko_core.ui.storage.stat_slots"),
                Integer.toString(menu.getActiveSlots()), cardY + 44);
        drawProfileStat(graphics, YoikoClientText.tr("yoiko_core.ui.storage.stat_mail"),
                Integer.toString(status.mailCount()), cardY + 61);
        drawProfileStat(graphics, YoikoClientText.tr("yoiko_core.ui.storage.stat_cosmetics"),
                equippedCosmeticCount(status) + "/3", cardY + 78);
    }

    private void drawProfileStat(GuiGraphics graphics, Component label, String value, int y) {
        int valueWidth = this.font.width(value);
        int labelWidth = Math.max(1, PROFILE_STATS_RIGHT_X - valueWidth - 3 - PROFILE_STATS_X);
        graphics.drawString(this.font, Component.literal(abbreviated(label.getString(), labelWidth, 1.0F)),
                PROFILE_STATS_X, y, PROFILE_BODY_COLOR, false);
        graphics.drawString(this.font, Component.literal(value),
                PROFILE_STATS_RIGHT_X - valueWidth, y, PROFILE_VALUE_COLOR, false);
    }

    private static int equippedCosmeticCount(YoikoStorageMenu.StatusSummary status) {
        int count = 0;
        if (!status.headCosmeticId().isBlank()) count++;
        if (!status.chestCosmeticId().isBlank()) count++;
        if (!status.feetCosmeticId().isBlank()) count++;
        return count;
    }

    private void renderRelicTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        if (localX < PROFILE_RELIC_X || localX >= PROFILE_RELIC_NAME_X + PROFILE_RELIC_NAME_WIDTH
                || localY < PROFILE_RELIC_START_Y - 1) {
            return;
        }

        int index = (localY - (PROFILE_RELIC_START_Y - 1)) / PROFILE_RELIC_ROW_STEP;
        List<YoikoStorageMenu.RelicSummaryEntry> relics = menu.getStatus().equippedRelics();
        if (index < 0 || index >= Math.min(5, relics.size())) {
            return;
        }
        int rowY = PROFILE_RELIC_START_Y + index * PROFILE_RELIC_ROW_STEP;
        if (localY >= rowY + 9) {
            return;
        }

        YoikoStorageMenu.RelicSummaryEntry relic = relics.get(index);
        OpenRelicPayload.Entry tooltipEntry = new OpenRelicPayload.Entry(
                "", "", relic.nameKey(), relic.effect(), relic.secondaryEffect(), relic.rarity(),
                relic.level(), relic.effectiveLevel(), relic.value(), relic.value(),
                relic.secondaryValue(), relic.secondaryValue(), 0.0D, 0.0D, 0.0D, 0.0D,
                index, relic.scrapValue(), false, false, false, false, -1,
                relic.primaryEffectSuppressed(), relic.secondaryEffectSuppressed()
        );
        RelicTooltipRenderer.render(graphics, this.font, this.width, tooltipEntry, mouseX, mouseY);
    }

    private void renderProfileCard(GuiGraphics graphics, YoikoStorageMenu.StatusSummary status) {
        renderPlayerFace(graphics, PROFILE_FACE_X, PROFILE_FACE_Y, PROFILE_FACE_SIZE);
        String playerName = minecraft.player == null ? "-" : minecraft.player.getGameProfile().getName();
        Component rankName = localizedRankName(status);
        graphics.drawCenteredString(this.font, Component.literal(abbreviated(rankName.getString(), 112, 1.0F)), PROFILE_TEXT_CENTER_X, PROFILE_RANK_Y, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(abbreviated(playerName, 112, 1.0F)), PROFILE_TEXT_CENTER_X, PROFILE_NAME_Y, PROFILE_NAME_COLOR);
    }

    private static Component localizedRankName(YoikoStorageMenu.StatusSummary status) {
        if (status.activeRankId().isBlank()) {
            return Component.translatable("yoiko_core.ui.profile.default_rank");
        }
        if (status.activeRankName().startsWith("yoiko_core.")) {
            return Component.translatable(status.activeRankName());
        }
        return Component.literal(status.activeRankName().isBlank()
                ? status.activeRankId()
                : status.activeRankName());
    }

    private void renderRelicRows(GuiGraphics graphics, YoikoStorageMenu.StatusSummary status) {
        List<YoikoStorageMenu.RelicSummaryEntry> relics = status.equippedRelics();
        for (int i = 0; i < relics.size() && i < 5; i++) {
            YoikoStorageMenu.RelicSummaryEntry relic = relics.get(i);
            int y = RELIC_START_Y + i * RELIC_ROW_STEP;
            YoikoScreenStyle.renderRelicIcon(graphics, RELIC_ICON_X, y, RELIC_ICON_SIZE, relic.rarity());

            String name = Component.translatable(relic.nameKey()).getString();
            graphics.drawString(this.font, Component.literal(abbreviated(name, RELIC_TEXT_RIGHT - RELIC_TEXT_X, 1.0F)), RELIC_TEXT_X, y, rarityColor(relic.rarity()), false);

            String magnitude = effectMagnitude(relic);
            graphics.drawString(this.font, Component.literal(magnitude), RELIC_TEXT_X, y + 10, PROFILE_VALUE_COLOR, false);
        }
    }

    private void renderCosmeticSlots(GuiGraphics graphics, YoikoStorageMenu.StatusSummary status) {
        renderCosmeticIcon(graphics, COSMETIC_START_X, COSMETIC_Y, "HEAD", status.headCosmeticId(), status.rankColor(), "");
        renderCosmeticIcon(graphics, COSMETIC_START_X + COSMETIC_SLOT_STEP, COSMETIC_Y, "CHEST", status.chestCosmeticId(), status.rankColor(), "");
        renderCosmeticIcon(graphics, COSMETIC_START_X + COSMETIC_SLOT_STEP * 2, COSMETIC_Y, "FEET",
                status.feetCosmeticId(), status.rankColor(), status.feetCosmeticRarity(), status.feetCosmeticCategory());
        renderCosmeticIcon(graphics, COSMETIC_START_X + COSMETIC_SLOT_STEP * 3, COSMETIC_Y, "RANK", status.activeRankId(), status.rankColor(), "");
    }

    private void renderCosmeticIcon(GuiGraphics graphics, int x, int y, String type, String id, int rankColor, String rarity) {
        renderCosmeticIcon(graphics, x, y, type, id, rankColor, rarity, "NONE");
    }

    private void renderCosmeticIcon(GuiGraphics graphics, int x, int y, String type, String id, int rankColor,
                                    String rarity, String particleCategory) {
        boolean equipped = id != null && !id.isBlank();
        if (!equipped) {
            YoikoCosmeticIconRenderer.renderProfileSlotIcon(graphics, x, y, type);
            return;
        }

        com.yoiko.core.network.CosmeticMenuCatalogPayload.Entry catalogEntry =
                com.yoiko.core.client.cosmetic.ClientCosmeticMenuCatalog.entry(id);
        String entryType = catalogEntry == null ? type : catalogEntry.type();
        String entryRarity = catalogEntry == null ? rarity : catalogEntry.rarity();
        String entryParticleCategory = catalogEntry == null ? particleCategory : catalogEntry.particleCategory();
        int iconSize = "PARTICLE".equals(entryType) ? 16 : 14;
        int iconOffset = (18 - iconSize) / 2;
        YoikoCosmeticIconRenderer.renderEntryIcon(graphics, x + iconOffset, y + iconOffset, iconSize,
                entryType, id, true, rankColor, entryRarity, entryParticleCategory);
    }

    private void renderStorageValues(GuiGraphics graphics, YoikoStorageMenu.StatusSummary status) {
        Component slots = YoikoClientText.tr("yoiko_core.ui.storage.slots", menu.getActiveSlots());
        Component mail = YoikoClientText.tr("yoiko_core.ui.count.items", status.mailCount());
        graphics.drawString(this.font, slots, STATUS_VALUE_X, STORAGE_VALUE_Y, PROFILE_VALUE_COLOR, false);
        graphics.drawString(this.font, mail, STATUS_VALUE_X, MAIL_VALUE_Y, PROFILE_VALUE_COLOR, false);
    }

    private void renderPlayerFace(GuiGraphics graphics, int x, int y, int size) {
        if (minecraft.player == null) {
            return;
        }
        PlayerFaceRenderer.draw(graphics, minecraft.player.getSkin().texture(), x, y, size);
    }

    private static String effectMagnitude(YoikoStorageMenu.RelicSummaryEntry relic) {
        return "+" + YoikoRelicValueFormatter.format(relic.effect(), relic.value());
    }

    private static int rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity.toUpperCase(java.util.Locale.ROOT)) {
            case "UNCOMMON" -> 0xFF4A9B55;
            case "RARE" -> 0xFF3A72B8;
            case "EPIC" -> 0xFF9A4FB2;
            case "LEGENDARY" -> 0xFFD28A16;
            case "MYSTIC", "UNIQUE" -> 0xFFC94E4E;
            case "RADIANT" -> 0xFF62C6D8;
            default -> PROFILE_BODY_COLOR;
        };
    }

    private String abbreviated(String text, int width, float scale) {
        int scaledWidth = Math.max(1, (int) Math.floor(width / scale));
        if (this.font.width(text) <= scaledWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = this.font.width(suffix);
        String candidate = text;
        while (!candidate.isEmpty() && this.font.width(candidate) + suffixWidth > scaledWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.isEmpty() ? suffix : candidate + suffix;
    }

    private void renderLockedSlotOverlays(GuiGraphics graphics) {
        for (int i = 0; i < YoikoStorageMenu.STORAGE_SLOT_COUNT; i++) {
            Slot slot = menu.slots.get(i);
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            if (menu.isStorageSlotLocked(i)) {
                YoikoScreenStyle.renderStorageCompactLockedSlot(graphics, x, y);
            } else if (menu.isStorageSlotUserLocked(i)) {
                renderUserLockBadge(graphics, x + 12, y + 1);
            }
        }
    }

    private static void renderUserLockBadge(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 1, y, x + 5, y + 1, 0xFFFFD76A);
        graphics.fill(x, y + 1, x + 1, y + 4, 0xFFFFD76A);
        graphics.fill(x + 5, y + 1, x + 6, y + 4, 0xFFFFD76A);
        graphics.fill(x, y + 3, x + 6, y + 8, 0xFF5B4024);
        graphics.fill(x + 2, y + 5, x + 4, y + 7, 0xFFFFD76A);
    }

    private YoikoSpriteButton addPageButton(YoikoSpriteButton.Sprite sprite, int localX, String action, boolean active) {
        YoikoSpriteButton button = YoikoSpriteButton.create(leftPos + localX, topPos + PAGE_BUTTON_Y, Component.literal(action), sprite, ignored -> {
            if (!menu.getCarried().isEmpty()) return;
            YoikoMousePosition.remember();
            send(action);
        });
        button.active = active;
        this.addRenderableWidget(button);
        return button;
    }

    private Component pageText() {
        return Component.literal((menu.getPage() + 1) + "/" + menu.getTotalPages());
    }

    private Component slotText() {
        return YoikoClientText.tr("yoiko_core.ui.storage.slots", menu.getActiveSlots()).withStyle(ChatFormatting.GOLD);
    }

    private int pageNextButtonX() {
        return PAGE_NEXT_BUTTON_X;
    }

    private int pageTextX() {
        return PAGE_TEXT_CENTER_X - this.font.width(pageText()) / 2;
    }

    private int pagePrevButtonX() {
        return PAGE_PREV_BUTTON_X;
    }

    private int slotTextX() {
        return SLOT_TEXT_RIGHT_X - this.font.width(slotText());
    }

    private int sortButtonX() {
        return leftPos + this.inventoryLabelX + this.font.width(this.playerInventoryTitle) + SORT_BUTTON_GAP;
    }

    private boolean isOverStorageGrid(double mouseX, double mouseY) {
        int x = leftPos + YoikoStorageMenu.STORAGE_SLOT_X;
        int y = topPos + YoikoStorageMenu.STORAGE_SLOT_Y;
        return mouseX >= x && mouseX < x + YoikoStorageMenu.STORAGE_COLUMNS * YoikoStorageMenu.SLOT_STEP
                && mouseY >= y && mouseY < y + YoikoStorageMenu.STORAGE_ROWS * YoikoStorageMenu.SLOT_STEP;
    }

    private void send(String action) {
        if (!ClientServerRequestState.begin(this, action)) {
            return;
        }
        PacketDistributor.sendToServer(ClientMenuSession.action(action));
    }

    private void renderIconTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sortButton != null && sortButton.isHovered()) {
            graphics.renderTooltip(this.font, sortButton.getMessage(), mouseX, mouseY);
            return;
        }
        if (profileButton != null && profileButton.isHovered()) {
            graphics.renderTooltip(this.font, profileButton.getMessage(), mouseX, mouseY);
            return;
        }
        if (storageBookmarkButton != null && storageBookmarkButton.isHovered()) {
            graphics.renderTooltip(this.font, storageBookmarkButton.getMessage(), mouseX, mouseY);
            return;
        }
        if (cosmeticBookmarkButton != null && cosmeticBookmarkButton.isHovered()) {
            graphics.renderTooltip(this.font, cosmeticBookmarkButton.getMessage(), mouseX, mouseY);
            return;
        }
        for (YoikoIconButton button : iconButtons) {
            if (button.isHovered()) {
                graphics.renderTooltip(this.font, button.getMessage(), mouseX, mouseY);
                return;
            }
        }
    }

    private static final class SortButton extends Button {
        private SortButton(int x, int y, Component message, OnPress onPress) {
            super(x, y, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            float brightness = !this.active ? 0.45F : this.isHoveredOrFocused() ? 1.0F : 0.72F;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(brightness, brightness, brightness, 1.0F);
            graphics.blit(SORT_ICON, getX(), getY(), SORT_BUTTON_SIZE, SORT_BUTTON_SIZE,
                    0.0F, 0.0F, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

}
