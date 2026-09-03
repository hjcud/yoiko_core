package com.yoiko.core.client.screen;

import com.yoiko.core.network.MenuActionPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yoiko.core.config.YoikoClientConfig;
import com.yoiko.core.network.OpenRelicPayload;
import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.yoiko.core.client.screen.RelicStoragePanelRenderer.*;
import static com.yoiko.core.client.screen.RelicWorkbenchPanelRenderer.*;

public class YoikoRelicScreen extends Screen {
    private static final ResourceLocation MAILBOX_ACTION_ICONS = YoikoServerCore.id("textures/gui/mailbox/mail_row.png");
    private static final int MAILBOX_ACTION_TEXTURE_WIDTH = 170;
    private static final int MAILBOX_ACTION_TEXTURE_HEIGHT = 45;
    private static final int DISMANTLE_ICON_SOURCE_X = 11;
    private static final int DISMANTLE_ICON_SOURCE_Y = 27;
    private static final int DISMANTLE_ICON_SIZE = 11;
    private static final int MAGIC_CIRCLE_PAGE_X = 54;
    private static final int MAGIC_CIRCLE_PAGE_Y = 66;
    private static final ResourceLocation RELIC_FRAME_RUBY =
            YoikoServerCore.id("textures/gui/relic/equipment/level_frame_negative.png");
    private static final ResourceLocation RELIC_FRAME_DIAMOND =
            YoikoServerCore.id("textures/gui/relic/equipment/level_frame_positive.png");
    private static final ResourceLocation[] RELIC_FRAME_RADIANT = {
            YoikoServerCore.id("textures/gui/relic/animations/radiant_modifier/frame_00.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_modifier/frame_01.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_modifier/frame_02.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_modifier/frame_03.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_modifier/frame_04.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_modifier/frame_05.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_modifier/frame_06.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_modifier/frame_07.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_modifier/frame_08.png"),
            YoikoServerCore.id("textures/gui/relic/animations/radiant_modifier/frame_09.png")
    };
    private static final ResourceLocation RELIC_STAR_RUBY =
            YoikoServerCore.id("textures/gui/relic/equipment/connection_negative.png");
    private static final ResourceLocation RELIC_STAR_DIAMOND =
            YoikoServerCore.id("textures/gui/relic/equipment/connection_positive.png");
    private static final int LEFT_X = YoikoMenuLayout.LEFT_X;
    private static final int LEFT_WIDTH = YoikoMenuLayout.LEFT_WIDTH;
    private static final int EQUIP_TITLE_Y = 10;
    private static final int EQUIP_SLOT_COUNT = 5;
    private static final int EQUIP_SLOT_SIZE = 18;
    private static final int EQUIP_SLOT_GAP = 15;
    private static final int EQUIP_SLOT_STEP = EQUIP_SLOT_SIZE + EQUIP_SLOT_GAP;
    private static final int EQUIP_SLOT_ROW_WIDTH = EQUIP_SLOT_COUNT * EQUIP_SLOT_SIZE + (EQUIP_SLOT_COUNT - 1) * EQUIP_SLOT_GAP;
    private static final int EQUIP_SLOT_X = LEFT_X + (LEFT_WIDTH - EQUIP_SLOT_ROW_WIDTH) / 2;
    private static final int EQUIP_SLOT_Y = 28;
    private static final int PRESET_BUTTON_X = LEFT_X + 8;
    private static final int PRESET_BUTTON_Y = 7;
    private static final int PRESET_BUTTON_WIDTH = 21;
    private static final int PRESET_BUTTON_HEIGHT = 14;
    private static final int PRESET_BUTTON_STEP = 23;
    private static final int EQUIP_TITLE_CENTER_X = LEFT_X + 132;
    /** The fifth socket is baked two pixels to the right of the regular 33px sequence. */
    private static final int LAST_EQUIP_SLOT_TEXTURE_OFFSET_X = 2;
    private static final int EFFECT_FRAME_SIZE = 22;
    private static final int EFFECT_FRAME_OFFSET = (EFFECT_FRAME_SIZE - EQUIP_SLOT_SIZE) / 2;
    private static final long RADIANT_FRAME_DURATION_MS = 100L;
    private static final int EFFECT_STAR_WIDTH = 5;
    private static final int EFFECT_STAR_HEIGHT = 7;
    private static final int LOWERED_CONNECTION_STAR_COUNT = 3;
    private static final int EFFECT_TITLE_Y = 62;
    private static final int EFFECT_LIST_Y = 74;
    private static final int EFFECT_PAGE_BUTTON_SIZE = 11;
    private static final int EFFECT_PAGE_PREV_X = LEFT_X + 127;
    private static final int EFFECT_PAGE_NEXT_X = LEFT_X + 158;
    private static final int EFFECT_PAGE_BUTTON_Y = EFFECT_TITLE_Y - 2;
    private static final int EFFECT_PAGE_TEXT_CENTER_X = LEFT_X + 148;
    private static final int STORAGE_TITLE_Y = 133;
    private static final int LEFT_RELIC_ICON_SIZE = 16;
    private static final int LEFT_RELIC_ICON_OFFSET = (SLOT_SIZE - LEFT_RELIC_ICON_SIZE) / 2;
    private static final int STORAGE_PAGE_PREV_X = 135;
    private static final int STORAGE_PAGE_NEXT_X = 167;
    private static final int STORAGE_PAGE_BUTTON_Y = STORAGE_TITLE_Y - 5;
    private static final int STORAGE_PAGE_TEXT_CENTER_X = 160;
    private static final int BATCH_STATUS_RIGHT_X = STORAGE_PAGE_PREV_X - 4;
    private static final int ROLL_ANIMATION_TICKS = 42;
    private static final int UPGRADE_CHARGE_TICKS = 34;
    private static final int UPGRADE_RESULT_TICKS = 24;
    private static final long CTRL_DOUBLE_CLICK_WINDOW_MS = 350L;
    private record EquippedEffectLine(
            int equippedSlot,
            String effect,
            double value,
            boolean suppressed
    ) {
    }

    private OpenRelicPayload payload;
    private final List<YoikoIconButton> iconButtons = new ArrayList<>();
    private YoikoFocusGrid gridFocus;
    private YoikoFocusGrid equipmentFocus;
    private BatchDismantleButton batchDismantleButton;
    private YoikoSpriteButton storagePagePrevButton;
    private YoikoSpriteButton storagePageNextButton;
    private EffectPageButton effectPagePrevButton;
    private EffectPageButton effectPageNextButton;
    private final List<PresetTabButton> presetButtons = new ArrayList<>();
    private int rollAnimationTicks;
    private int protectionSlotAnimationTicks;
    private int upgradeChargeTicks;
    private int upgradeResultTicks;
    private boolean awaitingUpgradeResult;
    private boolean upgradeResultSuccess;
    private boolean upgradeResultGreatSuccess;
    private String pendingUpgradeUuid="";
    private int pendingUpgradeLevel;
    private String localMessage = "";
    private String draggingUuid = "";
    private String pressedInventoryUuid = "";
    private String upgradeTargetUuid = "";
    private String lastCtrlClickUuid = "";
    private boolean useScrapProtection;
    private boolean draggingFromEquipped;
    private int pressedInventoryIconX;
    private int pressedInventoryIconY;
    private long lastCtrlClickTimeMs;
    private boolean batchDismantleMode;
    private boolean batchDismantleArmed;
    private int storagePage;
    private int effectPage;
    private final Set<String> batchDismantleSelection = new LinkedHashSet<>();
    private String localSelectedUuid = "";

    public YoikoRelicScreen(OpenRelicPayload payload) {
        super(YoikoClientText.tr("yoiko_core.screen.relic"));
        this.payload = payload;
        this.localSelectedUuid = payload.selectedUuid();
    }

    @Override
    protected void init() {
        rebuild();
        YoikoMousePosition.restoreIfRemembered();
    }

    public void update(OpenRelicPayload payload) {
        String previousSelection = localSelectedUuid;
        if(awaitingUpgradeResult&&!pendingUpgradeUuid.isBlank()){
            OpenRelicPayload.Entry result=payload.relics().stream().filter(entry->entry.uuid().equals(pendingUpgradeUuid)).findFirst().orElse(null);
            int gainedLevels=result==null?Integer.MIN_VALUE:result.level()-pendingUpgradeLevel;
            upgradeResultSuccess=gainedLevels>0;
            upgradeResultGreatSuccess=gainedLevels>=2;
            upgradeResultTicks=UPGRADE_RESULT_TICKS;
            awaitingUpgradeResult=false;
            pendingUpgradeUuid="";
        }
        this.payload = payload;
        this.localSelectedUuid = payload.relics().stream().anyMatch(entry -> entry.uuid().equals(previousSelection))
                ? previousSelection : payload.selectedUuid();
        this.localMessage = "";
        validateWorkbenchTargets();
        batchDismantleSelection.removeIf(uuid -> {
            OpenRelicPayload.Entry entry = entryByUuid(uuid);
            return entry == null || entry.locked() || entry.equippedSlot() >= 0;
        });
        rebuild();
    }

    @Override
    public void tick() {
        protectionSlotAnimationTicks = (protectionSlotAnimationTicks + 1)
                % PROTECTION_ANIMATION_DURATION;
        if(upgradeChargeTicks>0){
            upgradeChargeTicks--;
            if(upgradeChargeTicks==0){awaitingUpgradeResult=true;sendUpgrade();}
        }
        if(upgradeResultTicks>0)upgradeResultTicks--;
        if (rollAnimationTicks > 0) {
            rollAnimationTicks--;
            if (rollAnimationTicks == 0) send("relic_roll");
        }
        if (batchDismantleButton != null) {
            batchDismantleButton.active = rollAnimationTicks <= 0 && !upgradeAnimationActive();
        }
        for (PresetTabButton button : presetButtons) {
            button.active = rollAnimationTicks <= 0 && !upgradeAnimationActive();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YoikoScreenStyle.renderBackdrop(graphics, this.width, this.height);
        int left = left();
        int top = top();

        YoikoScreenStyle.renderRelicRightPanel(graphics, left + YoikoMenuLayout.SPLIT_RIGHT_PANEL_X, top);
        YoikoScreenStyle.renderRelicLeftPanel(graphics, left, top);

        renderEquippedRelicPanel(graphics, left, top, mouseX, mouseY);
        renderRelicStorage(graphics, left, top, mouseX, mouseY);
        ItemStack hoveredStack = renderWorkbench(graphics, left, top, mouseX, mouseY);
        renderUpgradeMagicCircle(graphics,left,top,partialTick);
        renderRollAnimation(graphics, left, top, partialTick);

        YoikoScreenStyle.renderWidgets(this, graphics, mouseX, mouseY, partialTick);
        ClientServerRequestState.render(graphics, this.font, "relic", this.width / 2, this.height - 30);
        OpenRelicPayload.Entry hoveredRelic = draggingUuid.isBlank() ? hoveredRelicAt(mouseX, mouseY) : null;
        Component disabledReason = disabledControlReason(mouseX, mouseY);
        if (disabledReason != null) {
            graphics.renderTooltip(this.font, disabledReason, mouseX, mouseY);
        } else if (!hoveredStack.isEmpty()) {
            graphics.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
        } else if (batchDismantleButton != null && batchDismantleButton.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(this.font, batchDismantleLabel(), mouseX, mouseY);
        } else if (hoveredRelic != null) {
            renderRelicTooltip(graphics, hoveredRelic, mouseX, mouseY);
        } else {
            YoikoNavigationTabs.renderTooltip(graphics, this.font, iconButtons, mouseX, mouseY);
        }
        renderDraggedRelic(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            int equippedSlot = equippedSlotIndexAt((int) mouseX, (int) mouseY);
            String equippedUuid = equippedSlot >= 0 ? payload.equippedSlots().get(equippedSlot) : null;
            if (equippedUuid != null && !equippedUuid.isBlank()) {
                focusEquipmentSlot(equippedSlot);
                clearPressedInventory();
                resetCtrlDoubleClick();
                draggingUuid = "";
                draggingFromEquipped = false;
                send("relic_unequip_slot|" + equippedSlot);
                return true;
            }
            OpenRelicPayload.Entry entry = inventoryEntryAt((int) mouseX, (int) mouseY);
            if (entry != null) {
                focusGridEntry(storageSlotOf(entry));
                clearPressedInventory();
                draggingUuid = "";
                draggingFromEquipped = false;
                send("relic_toggle_lock|" + entry.uuid());
                return true;
            }
        }
        if (button == 0) {
            if (isProtectionToggle((int) mouseX, (int) mouseY)) {
                if (!upgradeAnimationActive()&&canToggleScrapProtection()) {
                    useScrapProtection = !useScrapProtection;
                    if (useScrapProtection && YoikoClientConfig.UI_SOUNDS.get()) {
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.1F, 0.45F));
                    }
                }
                return true;
            }
            if (isUpgradeButton((int) mouseX, (int) mouseY)) {
                if (!upgradeAnimationActive() && rollAnimationTicks <= 0 && canUpgrade(upgradeTarget())) {
                    beginUpgradeAnimation();
                }
                return true;
            }
            int equippedSlot = equippedSlotIndexAt((int) mouseX, (int) mouseY);
            if (equippedSlot >= 0) {
                focusEquipmentSlot(equippedSlot);
            }
            String equippedUuid = equippedSlot >= 0 ? payload.equippedSlots().get(equippedSlot) : null;
            if (equippedUuid != null && !equippedUuid.isBlank()) {
                if (Screen.hasShiftDown()) {
                    clearPressedInventory();
                    draggingUuid = "";
                    draggingFromEquipped = false;
                    send("relic_contract_target|" + equippedUuid);
                    return true;
                }
                clearPressedInventory();
                draggingUuid = equippedUuid;
                draggingFromEquipped = true;
                return true;
            }
            OpenRelicPayload.Entry entry = inventoryEntryAt((int) mouseX, (int) mouseY);
            if (entry != null) {
                focusGridEntry(storageSlotOf(entry));
                if (batchDismantleMode) {
                    if (!entry.locked() && entry.equippedSlot() < 0) {
                        if (!batchDismantleSelection.remove(entry.uuid())) {
                            batchDismantleSelection.add(entry.uuid());
                        }
                        batchDismantleArmed = false;
                        rebuild();
                    }
                    return true;
                }
                if (Screen.hasControlDown()) {
                    long now = Util.getMillis();
                    boolean doubleClick = entry.uuid().equals(lastCtrlClickUuid)
                            && now - lastCtrlClickTimeMs <= CTRL_DOUBLE_CLICK_WINDOW_MS;
                    lastCtrlClickUuid = entry.uuid();
                    lastCtrlClickTimeMs = now;
                    if (doubleClick) {
                        clearPressedInventory();
                        draggingUuid = "";
                        draggingFromEquipped = false;
                        lastCtrlClickUuid = "";
                        lastCtrlClickTimeMs = 0L;
                        dismantleImmediately(entry.uuid());
                        return true;
                    }
                } else {
                    resetCtrlDoubleClick();
                }
                prepareInventoryPress(entry, (int) mouseX, (int) mouseY);
                return true;
            }
            resetCtrlDoubleClick();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && !pressedInventoryUuid.isBlank()) {
            if (!isInsidePressedInventoryIcon(mouseX, mouseY)) {
                draggingUuid = pressedInventoryUuid;
                draggingFromEquipped = false;
                clearPressedInventory();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && !pressedInventoryUuid.isBlank()) {
            String uuid = pressedInventoryUuid;
            clearPressedInventory();
            selectStorageRelic(uuid);
            return true;
        }
        if (button == 0 && !draggingUuid.isBlank()) {
            String uuid = draggingUuid;
            boolean fromEquipped = draggingFromEquipped;
            draggingUuid = "";
            draggingFromEquipped = false;

            if (isUpgradeTargetSlot((int) mouseX, (int) mouseY)) {
                if (!fromEquipped) {
                    upgradeTargetUuid = uuid;
                    localSelectedUuid = uuid;
                    rebuild();
                }
                return true;
            }

            int targetSlot = equippedSlotIndexAt((int) mouseX, (int) mouseY);
            if (targetSlot >= 0) {
                String targetUuid = payload.equippedSlots().get(targetSlot);
                if (uuid.equals(targetUuid)) {
                    selectLocally(uuid);
                } else {
                    send("relic_equip_slot|" + uuid + "|" + targetSlot);
                }
                return true;
            }
            int storageSlot = storageSlotIndexAt((int) mouseX, (int) mouseY);
            if (storageSlot >= 0) {
                if (fromEquipped) {
                    send("relic_unequip_storage|" + uuid + "|" + storageSlot);
                } else {
                    OpenRelicPayload.Entry moved = entryByUuid(uuid);
                    if (moved != null && storageSlot != storageSlotOf(moved)) {
                        send("relic_move_storage|" + uuid + "|" + storageSlot);
                    } else {
                        selectStorageRelic(uuid);
                    }
                }
                return true;
            }
            if (fromEquipped && isInventoryArea((int) mouseX, (int) mouseY)) {
                send("relic_unequip|" + uuid);
                return true;
            }
            if (fromEquipped) {
                selectLocally(uuid);
            } else {
                selectStorageRelic(uuid);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean carryingRelic = !draggingUuid.isBlank() || !pressedInventoryUuid.isBlank();
        if (!carryingRelic && scrollY != 0.0D && isEffectPanel((int) mouseX, (int) mouseY)
                && effectPageCount() > 1) {
            changeEffectPage(scrollY < 0.0D ? 1 : -1);
            return true;
        }
        if (scrollY != 0.0D && (isInventoryArea((int) mouseX, (int) mouseY) || carryingRelic)) {
            changeStoragePage(scrollY < 0.0D ? 1 : -1);
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
        presetButtons.clear();
        int left = left();
        int top = top();
        YoikoNavigationTabs.add(iconButtons, left, top, "relic", this::send, button -> this.addRenderableWidget(button));
        boolean animating = rollAnimationTicks > 0||upgradeAnimationActive();
        for (int preset = 0; preset < 3; preset++) {
            PresetTabButton button = new PresetTabButton(
                    left + PRESET_BUTTON_X + preset * PRESET_BUTTON_STEP,
                    top + PRESET_BUTTON_Y,
                    preset
            );
            button.active = !animating;
            presetButtons.add(button);
            this.addRenderableWidget(button);
        }

        Component storageTitle = YoikoClientText.tr("yoiko_core.ui.relic.owned_relics");
        batchDismantleButton = new BatchDismantleButton(
                left + LEFT_X + 10 + this.font.width(storageTitle) + 3,
                top + STORAGE_TITLE_Y - 1
        );
        batchDismantleButton.active = !animating;
        this.addRenderableWidget(batchDismantleButton);
        storagePagePrevButton = addStoragePageButton(YoikoSpriteButton.Sprite.PAGE_LEFT,
                STORAGE_PAGE_PREV_X, -1, storagePage > 0);
        storagePageNextButton = addStoragePageButton(YoikoSpriteButton.Sprite.PAGE_RIGHT,
                STORAGE_PAGE_NEXT_X, 1, storagePage + 1 < STORAGE_PAGE_COUNT);
        int effectPages = effectPageCount();
        effectPage = Math.clamp(effectPage, 0, effectPages - 1);
        effectPagePrevButton = null;
        effectPageNextButton = null;
        if (effectPages > 1) {
            effectPagePrevButton = this.addRenderableWidget(new EffectPageButton(
                    left + EFFECT_PAGE_PREV_X,
                    top + EFFECT_PAGE_BUTTON_Y,
                    -1
            ));
            effectPageNextButton = this.addRenderableWidget(new EffectPageButton(
                    left + EFFECT_PAGE_NEXT_X,
                    top + EFFECT_PAGE_BUTTON_Y,
                    1
            ));
            updateEffectPageButtons();
        }
        gridFocus = this.addRenderableWidget(new YoikoFocusGrid(
                left + GRID_X, top + GRID_Y, GRID_COLUMNS, GRID_ROWS, SLOT_SIZE, SLOT_SIZE,
                SLOT_STEP, SLOT_STEP, () -> GRID_COLUMNS * GRID_ROWS, this::activateGridEntry).withoutFocusOutline());
        OpenRelicPayload.Entry selectedEntry = selected();
        int selectedStorageSlot = storageSlotOf(selectedEntry);
        int selectedPageIndex = storageSlotOnCurrentPage(selectedStorageSlot)
                ? selectedStorageSlot - storagePageStart()
                : 0;
        gridFocus.setFocusedIndex(restoreGridFocus ? previousGridIndex : selectedPageIndex);
        equipmentFocus = this.addRenderableWidget(new YoikoFocusGrid(
                left + EQUIP_SLOT_X, top + EQUIP_SLOT_Y, EQUIP_SLOT_COUNT, 1,
                EQUIP_SLOT_SIZE, EQUIP_SLOT_SIZE, EQUIP_SLOT_STEP, EQUIP_SLOT_SIZE,
                () -> Math.min(EQUIP_SLOT_COUNT, payload.equippedSlots().size()), this::activateEquipmentSlot).withoutFocusOutline());
        equipmentFocus.setFocusedIndex(previousEquipmentIndex);
        if (restoreGridFocus) {
            setFocused(gridFocus);
        } else if (restoreEquipmentFocus) {
            setFocused(equipmentFocus);
        }
    }

    private void activateGridEntry(int index) {
        OpenRelicPayload.Entry entry = storageEntryAtSlot(storagePageStart() + index);
        if (entry == null) {
            return;
        }
        if (batchDismantleMode) {
            if (!entry.locked() && entry.equippedSlot() < 0) {
                if (!batchDismantleSelection.remove(entry.uuid())) {
                    batchDismantleSelection.add(entry.uuid());
                }
                batchDismantleArmed = false;
                rebuild();
            }
            return;
        }
        selectStorageRelic(entry.uuid());
    }

    private void focusGridEntry(int index) {
        if (gridFocus != null && storageSlotOnCurrentPage(index)) {
            setFocused(gridFocus);
            gridFocus.setFocusedIndex(index - storagePageStart());
        }
    }

    private void changeStoragePage(int delta) {
        int nextPage = Math.max(0, Math.min(STORAGE_PAGE_COUNT - 1, storagePage + delta));
        if (nextPage == storagePage) {
            return;
        }
        storagePage = nextPage;
        updateStoragePageButtons();
        if (gridFocus != null && gridFocus.isFocused()) {
            gridFocus.setFocusedIndex(0);
        }
    }

    private YoikoSpriteButton addStoragePageButton(
            YoikoSpriteButton.Sprite sprite,
            int localX,
            int delta,
            boolean active
    ) {
        Component label = YoikoClientText.tr(delta < 0 ? "yoiko_core.ui.previous" : "yoiko_core.ui.next");
        YoikoSpriteButton button = YoikoSpriteButton.create(
                left() + localX,
                top() + STORAGE_PAGE_BUTTON_Y,
                label,
                sprite,
                ignored -> changeStoragePage(delta)
        );
        button.active = active;
        this.addRenderableWidget(button);
        return button;
    }

    private void updateStoragePageButtons() {
        if (storagePagePrevButton != null) {
            storagePagePrevButton.active = storagePage > 0;
        }
        if (storagePageNextButton != null) {
            storagePageNextButton.active = storagePage + 1 < STORAGE_PAGE_COUNT;
        }
    }

    private void changeEffectPage(int delta) {
        int nextPage = Math.clamp(effectPage + delta, 0, effectPageCount() - 1);
        if (nextPage == effectPage) {
            return;
        }
        effectPage = nextPage;
        updateEffectPageButtons();
    }

    private void updateEffectPageButtons() {
        if (effectPagePrevButton != null) {
            effectPagePrevButton.active = effectPage > 0;
        }
        if (effectPageNextButton != null) {
            effectPageNextButton.active = effectPage + 1 < effectPageCount();
        }
    }

    private void activateEquipmentSlot(int index) {
        if (index < 0 || index >= payload.equippedSlots().size()) {
            return;
        }
        String uuid = payload.equippedSlots().get(index);
        if (uuid != null && !uuid.isBlank()) {
            selectLocally(uuid);
        }
    }

    private void focusEquipmentSlot(int index) {
        if (equipmentFocus != null && index >= 0) {
            setFocused(equipmentFocus);
            equipmentFocus.setFocusedIndex(index);
        }
    }

    private void renderEquippedRelicPanel(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        drawCenteredNoShadow(
                graphics,
                YoikoClientText.tr("yoiko_core.ui.relic.equipped_relics"),
                left + EQUIP_TITLE_CENTER_X,
                top + EQUIP_TITLE_Y,
                0xFF8A5A32
        );
        EquippedEffectLine hoveredEffect = effectLineAt(mouseX, mouseY);
        int hoveredEffectSlot = hoveredEffect == null ? -1 : hoveredEffect.equippedSlot();
        int levelModifierSlot = levelModifierSlot();
        if (levelModifierSlot >= 0) {
            renderRelicLevelConnectionStars(graphics, left, top, levelModifierSlot);
        }
        for (int i = 0; i < payload.equippedSlots().size(); i++) {
            int x = equippedSlotX(left, i);
            int y = top + EQUIP_SLOT_Y;
            String uuid = payload.equippedSlots().get(i);
            OpenRelicPayload.Entry entry = entryByUuid(uuid);
            if (entry != null && !isDraggingSource(entry, true)) {
                if (i == levelModifierSlot) {
                    renderRadiantLevelModifierFrame(graphics, x, y);
                }
                YoikoScreenStyle.renderRelicIcon(
                        graphics,
                        x + LEFT_RELIC_ICON_OFFSET,
                        y + LEFT_RELIC_ICON_OFFSET,
                        LEFT_RELIC_ICON_SIZE,
                        entry.rarity()
                );
                int levelDelta = effectiveLevelDelta(entry);
                if (levelModifierSlot >= 0 && levelDelta != 0) {
                    renderRelicLevelFrame(graphics, x, y, levelDelta);
                }
                RelicScreenRenderSupport.drawLevelBadge(
                        graphics, this.font, x, y, EQUIP_SLOT_SIZE, entry.level(), entry.effectiveLevel());
                if (i == hoveredEffectSlot) {
                    renderEffectHoverGlow(graphics, x, y);
                }
            }
        }
        int effectPages = effectPageCount();
        Component effectTitle = YoikoClientText.tr(effectPages > 1
                ? effectPage == 0
                        ? "yoiko_core.ui.relic.effect_page.primary"
                        : "yoiko_core.ui.relic.effect_page.secondary"
                : "yoiko_core.ui.relic.total_effect");
        int effectTitleX = left + LEFT_X + 10;
        graphics.drawString(
                this.font,
                effectTitle,
                effectTitleX,
                top + EFFECT_TITLE_Y,
                0xFF9A693A,
                false
        );
        if (hasRadiantEquipped()) {
            Component radiantUsage = YoikoClientText.tr(
                    "yoiko_core.ui.relic.radiant_usage",
                    payload.specialEquippedCount(),
                    payload.specialMaxEquipped()
            );
            graphics.drawString(
                    this.font,
                    radiantUsage,
                    effectTitleX + this.font.width(effectTitle) + 6,
                    top + EFFECT_TITLE_Y,
                    0xFFC07058,
                    false
            );
        }
        if (effectPages > 1) {
            drawCenteredNoShadow(
                    graphics,
                    Component.literal((effectPage + 1) + "/" + effectPages),
                    left + EFFECT_PAGE_TEXT_CENTER_X,
                    top + EFFECT_TITLE_Y,
                    0xFF876747
            );
        }
        int lineY = top + EFFECT_LIST_Y;
        List<EquippedEffectLine> effects = effectLinesForPage();
        for (EquippedEffectLine effect : effects) {
            String value = "+" + YoikoRelicValueFormatter.format(effect.effect(), effect.value());
            int valueWidth = this.font.width(value);
            int labelWidth = Math.max(20, LEFT_WIDTH - 24 - valueWidth);
            String label = abbreviated(RelicUiText.effectSummary(effect.effect()), labelWidth, 1.0F);
            int color = effect.suppressed() ? 0xFF9A8F7D : 0xFF6E5338;
            int valueColor = effect.suppressed() ? 0xFFAAA092 : 0xFF4B7580;
            graphics.drawString(this.font, Component.literal(label),
                    left + LEFT_X + 10, lineY, color, false);
            drawRightAlignedNoShadow(graphics, Component.literal(value),
                    left + LEFT_X + LEFT_WIDTH - 10, lineY, valueColor);
            lineY += 10;
        }
        if (effects.isEmpty()) {
            graphics.drawString(this.font, YoikoClientText.tr("yoiko_core.ui.relic.no_effects"), left + LEFT_X + 10, lineY, 0xFF9A8268, false);
        }
    }

    private int effectPageCount() {
        for (String uuid : payload.equippedSlots()) {
            OpenRelicPayload.Entry entry = entryByUuid(uuid);
            if (entry != null && !entry.secondaryEffect().isBlank()) {
                return 2;
            }
        }
        return 1;
    }

    private List<EquippedEffectLine> effectLinesForPage() {
        List<EquippedEffectLine> effects = new ArrayList<>(EQUIP_SLOT_COUNT);
        int slotCount = Math.min(EQUIP_SLOT_COUNT, payload.equippedSlots().size());
        for (int slot = 0; slot < slotCount; slot++) {
            OpenRelicPayload.Entry entry = entryByUuid(payload.equippedSlots().get(slot));
            if (entry == null) {
                continue;
            }
            if (effectPage == 0) {
                if (!entry.effect().isBlank()) {
                    effects.add(new EquippedEffectLine(
                            slot, entry.effect(), entry.value(), entry.primaryEffectSuppressed()));
                }
            } else if (!entry.secondaryEffect().isBlank()) {
                effects.add(new EquippedEffectLine(
                        slot,
                        entry.secondaryEffect(),
                        entry.secondaryValue(),
                        entry.secondaryEffectSuppressed()
                ));
            }
        }
        return effects;
    }

    private EquippedEffectLine effectLineAt(int mouseX, int mouseY) {
        int localX = mouseX - left();
        int localY = mouseY - top();
        if (localX < LEFT_X + 8 || localX >= LEFT_X + LEFT_WIDTH - 8
                || localY < EFFECT_LIST_Y || localY >= EFFECT_LIST_Y + EQUIP_SLOT_COUNT * 10) {
            return null;
        }
        int line = (localY - EFFECT_LIST_Y) / 10;
        List<EquippedEffectLine> effects = effectLinesForPage();
        return line >= 0 && line < effects.size() ? effects.get(line) : null;
    }

    private boolean isEffectPanel(int mouseX, int mouseY) {
        int localX = mouseX - left();
        int localY = mouseY - top();
        return localX >= LEFT_X + 6 && localX < LEFT_X + LEFT_WIDTH - 6
                && localY >= EFFECT_TITLE_Y - 3 && localY < STORAGE_TITLE_Y - 4;
    }

    private boolean hasRadiantEquipped() {
        for (String uuid : payload.equippedSlots()) {
            OpenRelicPayload.Entry entry = entryByUuid(uuid);
            if (entry != null && "RADIANT".equalsIgnoreCase(entry.rarity())) {
                return true;
            }
        }
        return false;
    }

    private int levelModifierSlot() {
        int slotCount = Math.min(EQUIP_SLOT_COUNT, payload.equippedSlots().size());
        for (int i = 0; i < slotCount; i++) {
            OpenRelicPayload.Entry entry = entryByUuid(payload.equippedSlots().get(i));
            if (entry != null && isRelicLevelModifier(entry.effect())) {
                return i;
            }
        }
        return -1;
    }

    private void renderRelicLevelConnectionStars(GuiGraphics graphics, int left, int top, int sourceSlot) {
        int slotCount = Math.min(EQUIP_SLOT_COUNT, payload.equippedSlots().size());
        int y = top + EQUIP_SLOT_Y + (EQUIP_SLOT_SIZE - EFFECT_STAR_HEIGHT) / 2;
        for (int gap = 0; gap < slotCount - 1; gap++) {
            int levelDelta = outwardConnectionDelta(gap, sourceSlot, slotCount);
            if (levelDelta == 0) {
                continue;
            }
            int leftSlotRight = equippedSlotX(left, gap) + EQUIP_SLOT_SIZE;
            int rightSlotLeft = equippedSlotX(left, gap + 1);
            int x = leftSlotRight + (rightSlotLeft - leftSlotRight - EFFECT_STAR_WIDTH) / 2;
            ResourceLocation texture = levelDelta > 0 ? RELIC_STAR_DIAMOND : RELIC_STAR_RUBY;
            int adjustedY = y + (gap < LOWERED_CONNECTION_STAR_COUNT ? 1 : 0);
            blitNative(graphics, texture, x, adjustedY, EFFECT_STAR_WIDTH, EFFECT_STAR_HEIGHT);
        }
    }

    private int outwardConnectionDelta(int gap, int sourceSlot, int slotCount) {
        int first = gap < sourceSlot ? gap : gap + 1;
        int end = gap < sourceSlot ? -1 : slotCount;
        int step = gap < sourceSlot ? -1 : 1;
        for (int slot = first; slot != end; slot += step) {
            OpenRelicPayload.Entry entry = entryByUuid(payload.equippedSlots().get(slot));
            int levelDelta = effectiveLevelDelta(entry);
            if (levelDelta != 0) {
                return levelDelta;
            }
        }
        return 0;
    }

    private static void renderRelicLevelFrame(
            GuiGraphics graphics,
            int slotX,
            int slotY,
            int levelDelta
    ) {
        ResourceLocation texture = levelDelta > 0 ? RELIC_FRAME_DIAMOND : RELIC_FRAME_RUBY;
        blitNative(
                graphics,
                texture,
                slotX - EFFECT_FRAME_OFFSET,
                slotY - EFFECT_FRAME_OFFSET,
                EFFECT_FRAME_SIZE,
                EFFECT_FRAME_SIZE
        );
    }

    private static void renderEffectHoverGlow(GuiGraphics graphics, int slotX, int slotY) {
        float phase = (float) ((Math.sin(Util.getMillis() / 145.0D) + 1.0D) * 0.5D);
        float haloAlpha = 0.12F + phase * 0.10F;
        float coreAlpha = 0.76F + phase * 0.20F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            // The occupied-slot texture follows the relic tablet silhouette, so the glow does
            // not collapse into the rectangular focus outline used by ordinary widgets.
            RenderSystem.setShaderColor(1.90F, 1.0F, 1.05F, haloAlpha);
            YoikoScreenStyle.renderRelicEquippedSlot(graphics, slotX - 1, slotY);
            YoikoScreenStyle.renderRelicEquippedSlot(graphics, slotX + 1, slotY);
            YoikoScreenStyle.renderRelicEquippedSlot(graphics, slotX, slotY - 1);
            YoikoScreenStyle.renderRelicEquippedSlot(graphics, slotX, slotY + 1);
            RenderSystem.setShaderColor(1.90F, 1.0F, 1.05F, coreAlpha);
            YoikoScreenStyle.renderRelicEquippedSlot(graphics, slotX, slotY);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    private static void renderRadiantLevelModifierFrame(
            GuiGraphics graphics,
            int slotX,
            int slotY
    ) {
        int frame = (int) ((Util.getMillis() / RADIANT_FRAME_DURATION_MS) % RELIC_FRAME_RADIANT.length);
        blitNative(
                graphics,
                RELIC_FRAME_RADIANT[frame],
                slotX - EFFECT_FRAME_OFFSET,
                slotY - EFFECT_FRAME_OFFSET,
                EFFECT_FRAME_SIZE,
                EFFECT_FRAME_SIZE
        );
    }

    private static int effectiveLevelDelta(OpenRelicPayload.Entry entry) {
        return entry == null ? 0 : Integer.compare(entry.effectiveLevel(), entry.level());
    }

    private static boolean isRelicLevelModifier(String effect) {
        return switch (effect) {
            case "equipped_relic_level_bonus",
                    "weakest_relic_level_bonus",
                    "chromatic_contract_level_shift" -> true;
            default -> false;
        };
    }

    private static void blitNative(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height
    ) {
        graphics.blit(texture, x, y, width, height, 0.0F, 0.0F, width, height, width, height);
    }

    private void renderRelicStorage(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        RelicStoragePanelRenderer.render(
                graphics, this.font, left, top, mouseX, mouseY, storagePage,
                storageSlots(), batchDismantleMode, batchDismantleSelection,
                batchSelectedScrap(), batchDismantleArmed, draggingUuid, draggingFromEquipped,
                LEFT_X, STORAGE_TITLE_Y, STORAGE_PAGE_TEXT_CENTER_X,
                BATCH_STATUS_RIGHT_X
        );
    }

    private ItemStack renderWorkbench(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        OpenRelicPayload.Entry entry = workbenchDisplayEntry();
        boolean upgradeAvailable = !upgradeAnimationActive()
                && rollAnimationTicks <= 0
                && canUpgrade(upgradeTarget());
        return RelicWorkbenchPanelRenderer.render(
                graphics,
                this.font,
                left,
                top,
                mouseX,
                mouseY,
                new RelicWorkbenchPanelRenderer.State(
                        entry,
                        payload.upgradeCrystalCount(),
                        payload.scrapCount(),
                        payload.protectionScrapCost(),
                        useScrapProtection,
                        canUseScrapProtection(),
                        upgradeAvailable,
                        protectionSlotAnimationTicks
                )
        );
    }

    private void renderRollAnimation(GuiGraphics graphics, int left, int top, float partialTick) {
        if (rollAnimationTicks <= 0) {
            return;
        }
        int x = left + LEFT_X + 8;
        int y = top + GRID_Y - 6;
        int active = Math.floorMod((ROLL_ANIMATION_TICKS - rollAnimationTicks) / 2, GRID_COLUMNS * GRID_ROWS);
        for (int i = 0; i < GRID_COLUMNS * GRID_ROWS; i++) {
            if (i != active && i != Math.floorMod(active + 1, GRID_COLUMNS * GRID_ROWS)) {
                continue;
            }
            int slotX = left + GRID_X + (i % GRID_COLUMNS) * SLOT_STEP;
            int slotY = top + GRID_Y + (i / GRID_COLUMNS) * SLOT_STEP;
            renderOutline(graphics, slotX - 2, slotY - 2, SLOT_SIZE + 4, SLOT_SIZE + 4, i == active ? 0xFF4FEAFF : 0x88FFFFB8);
        }
        int pulse = (int) ((Math.sin((ROLL_ANIMATION_TICKS - rollAnimationTicks + partialTick) * 0.45D) + 1.0D) * 20.0D);
        graphics.fill(x, y, x + 170, y + 1, 0xFF1B2427);
        graphics.fill(x, y, x + Math.min(170, (ROLL_ANIMATION_TICKS - rollAnimationTicks) * 170 / ROLL_ANIMATION_TICKS), y + 1, 0xFF4FEAFF + (pulse << 16));
    }

    private void renderUpgradeMagicCircle(GuiGraphics graphics,int left,int top,float partialTick){
        if(upgradeChargeTicks<=0&&upgradeResultTicks<=0&&!awaitingUpgradeResult)return;
        int textureX=left+YoikoMenuLayout.SPLIT_RIGHT_PANEL_X-25+MAGIC_CIRCLE_PAGE_X;
        int textureY=top-25+MAGIC_CIRCLE_PAGE_Y;
        RelicMagicCircleRenderer.render(
                graphics,
                textureX,
                textureY,
                upgradeChargeTicks,
                upgradeResultTicks,
                awaitingUpgradeResult,
                upgradeResultGreatSuccess,
                upgradeResultSuccess,
                partialTick,
                UPGRADE_CHARGE_TICKS,
                UPGRADE_RESULT_TICKS
        );
    }

    private void renderDraggedRelic(GuiGraphics graphics, int mouseX, int mouseY) {
        if (draggingUuid.isBlank()) {
            return;
        }
        OpenRelicPayload.Entry entry = entryByUuid(draggingUuid);
        if (entry == null) {
            return;
        }
        int x = mouseX - 8;
        int y = mouseY - 8;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 410.0F);
        YoikoScreenStyle.renderRelicIcon(graphics, x, y, 16, entry.rarity());
        graphics.pose().popPose();
    }

    private void renderRelicTooltip(GuiGraphics graphics, OpenRelicPayload.Entry entry, int mouseX, int mouseY) {
        RelicTooltipRenderer.render(graphics, this.font, this.width, entry, mouseX, mouseY);
    }

    private boolean isDraggingSource(OpenRelicPayload.Entry entry, boolean equippedSlot) {
        if (entry == null || draggingUuid.isBlank() || !entry.uuid().equals(draggingUuid)) {
            return false;
        }
        return equippedSlot == draggingFromEquipped;
    }

    private void prepareInventoryPress(OpenRelicPayload.Entry entry, int mouseX, int mouseY) {
        int gridX = left() + GRID_X;
        int gridY = top() + GRID_Y;
        int column = (mouseX - gridX) / SLOT_STEP;
        int row = (mouseY - gridY) / SLOT_STEP;
        pressedInventoryUuid = entry.uuid();
        pressedInventoryIconX = gridX + column * SLOT_STEP + 1;
        pressedInventoryIconY = gridY + row * SLOT_STEP + 1;
    }

    private boolean isInsidePressedInventoryIcon(double mouseX, double mouseY) {
        return mouseX >= pressedInventoryIconX
                && mouseX < pressedInventoryIconX + 16
                && mouseY >= pressedInventoryIconY
                && mouseY < pressedInventoryIconY + 16;
    }

    private void clearPressedInventory() {
        pressedInventoryUuid = "";
        pressedInventoryIconX = 0;
        pressedInventoryIconY = 0;
    }

    private OpenRelicPayload.Entry selected() {
        for (OpenRelicPayload.Entry entry : payload.relics()) {
            if (entry.uuid().equals(localSelectedUuid)) {
                return entry;
            }
        }
        return payload.relics().isEmpty() ? null : payload.relics().get(0);
    }

    private void selectLocally(String uuid) {
        if (entryByUuid(uuid) == null) {
            return;
        }
        localSelectedUuid = uuid;
        localMessage = "";
        rebuild();
    }

    private void selectStorageRelic(String uuid) {
        OpenRelicPayload.Entry entry = entryByUuid(uuid);
        if (entry == null || entry.equippedSlot() >= 0) {
            return;
        }
        localSelectedUuid = uuid;
        upgradeTargetUuid = uuid;
        localMessage = "";
        rebuild();
    }

    private OpenRelicPayload.Entry entryByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        for (OpenRelicPayload.Entry entry : payload.relics()) {
            if (entry.uuid().equals(uuid)) {
                return entry;
            }
        }
        return null;
    }

    private OpenRelicPayload.Entry hoveredRelicAt(int mouseX, int mouseY) {
        if (isUpgradeTargetSlot(mouseX, mouseY)) {
            return workbenchDisplayEntry();
        }
        String equippedUuid = equippedUuidAt(mouseX, mouseY);
        OpenRelicPayload.Entry equipped = entryByUuid(equippedUuid);
        return equipped == null ? inventoryEntryAt(mouseX, mouseY) : equipped;
    }

    private String equippedUuidAt(int mouseX, int mouseY) {
        int index = equippedSlotIndexAt(mouseX, mouseY);
        return index < 0 ? null : payload.equippedSlots().get(index);
    }

    private int equippedSlotIndexAt(int mouseX, int mouseY) {
        int left = left();
        int top = top();
        for (int i = 0; i < payload.equippedSlots().size(); i++) {
            int x = equippedSlotX(left, i);
            int y = top + EQUIP_SLOT_Y;
            if (mouseX >= x && mouseX < x + EQUIP_SLOT_SIZE && mouseY >= y && mouseY < y + EQUIP_SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private static int equippedSlotX(int left, int index) {
        return left + EQUIP_SLOT_X + index * EQUIP_SLOT_STEP
                + (index == EQUIP_SLOT_COUNT - 1 ? LAST_EQUIP_SLOT_TEXTURE_OFFSET_X : 0);
    }

    private OpenRelicPayload.Entry inventoryEntryAt(int mouseX, int mouseY) {
        return storageEntryAtSlot(storageSlotIndexAt(mouseX, mouseY));
    }

    private int storageSlotIndexAt(int mouseX, int mouseY) {
        return RelicStoragePanelRenderer.slotIndexAt(
                mouseX, mouseY, left(), top(), storagePage);
    }

    private OpenRelicPayload.Entry storageEntryAtSlot(int slot) {
        return RelicStoragePanelRenderer.entryAt(storageSlots(), slot);
    }

    private int storageSlotOf(OpenRelicPayload.Entry target) {
        return RelicStoragePanelRenderer.slotOf(storageSlots(), target);
    }

    private List<OpenRelicPayload.Entry> storageSlots() {
        return RelicStoragePanelRenderer.slots(payload);
    }

    private int storagePageStart() {
        return RelicStoragePanelRenderer.pageStart(storagePage);
    }

    private boolean storageSlotOnCurrentPage(int slot) {
        return RelicStoragePanelRenderer.slotOnPage(slot, storagePage);
    }

    private boolean isUpgradeTargetSlot(int mouseX, int mouseY) {
        int x = left() + UPGRADE_TARGET_X;
        int y = top() + UPGRADE_TARGET_Y;
        return RelicWorkbenchPanelRenderer.insideSlot(
                mouseX,
                mouseY,
                x + UPGRADE_TARGET_SIZE / 2 + UPGRADE_TARGET_CENTER_X_OFFSET,
                y + UPGRADE_TARGET_SIZE / 2
        );
    }

    private boolean isProtectionToggle(int mouseX, int mouseY) {
        return RelicWorkbenchPanelRenderer.insideSlot(
                mouseX,
                mouseY,
                left() + PROTECTION_CENTER_X,
                top() + MATERIAL_CENTER_Y
        );
    }

    private boolean isUpgradeButton(int mouseX, int mouseY) {
        int centerX = left() + UPGRADE_BUTTON_CENTER_X;
        int centerY = top() + UPGRADE_BUTTON_CENTER_Y;
        return mouseX >= centerX - UPGRADE_BUTTON_HIT_WIDTH / 2
                && mouseX < centerX + UPGRADE_BUTTON_HIT_WIDTH / 2
                && mouseY >= centerY - UPGRADE_BUTTON_HIT_HEIGHT / 2
                && mouseY < centerY + UPGRADE_BUTTON_HIT_HEIGHT / 2;
    }

    private boolean isInventoryArea(int mouseX, int mouseY) {
        int left = left();
        int top = top();
        return mouseX >= left + LEFT_X
                && mouseX < left + LEFT_X + LEFT_WIDTH
                && mouseY >= top + GRID_Y
                && mouseY < top + GRID_Y + (GRID_ROWS - 1) * SLOT_STEP + SLOT_SIZE;
    }

    private boolean canUpgrade(OpenRelicPayload.Entry selected) {
        return selected != null && selected.upgradeable() && !selected.locked() && selected.equippedSlot() < 0
                && selected.level() < 10 && payload.upgradeCrystalCount() > 0;
    }

    private boolean canUseScrapProtection() {
        return canUpgrade(upgradeTarget()) && payload.scrapCount() >= payload.protectionScrapCost();
    }

    private boolean canToggleScrapProtection() {
        return payload.scrapCount() >= payload.protectionScrapCost();
    }

    private boolean upgradeAnimationActive(){
        return upgradeChargeTicks>0||awaitingUpgradeResult||upgradeResultTicks>0;
    }

    private void beginUpgradeAnimation(){
        OpenRelicPayload.Entry target=upgradeTarget();
        if(target==null||!canUpgrade(target)||upgradeAnimationActive())return;
        pendingUpgradeUuid=target.uuid();
        pendingUpgradeLevel=target.level();
        upgradeResultSuccess=false;
        upgradeResultGreatSuccess=false;
        upgradeChargeTicks=UPGRADE_CHARGE_TICKS;
        localMessage=YoikoClientText.text("yoiko_core.ui.relic.upgrade_charging");
        rebuild();
    }

    private Component disabledControlReason(int mouseX, int mouseY) {
        boolean upgradeHovered = isUpgradeButton(mouseX, mouseY);
        if (!upgradeHovered) {
            return null;
        }
        OpenRelicPayload.Entry target = entryByUuid(upgradeTargetUuid);
        if (target == null) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.select_upgrade_target");
        }
        if (!target.upgradeable()) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.not_upgradeable");
        }
        if (target.locked()) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.relic_locked");
        }
        if (target.equippedSlot() >= 0) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.unequip_first");
        }
        if (target.level() >= 10) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.max_level");
        }
        if (payload.upgradeCrystalCount() <= 0) {
            return YoikoClientText.tr("yoiko_core.ui.disabled.no_upgrade_crystal");
        }
        return null;
    }

    private OpenRelicPayload.Entry upgradeTarget() {
        OpenRelicPayload.Entry entry = entryByUuid(upgradeTargetUuid);
        return entry != null && entry.equippedSlot() < 0 ? entry : null;
    }

    private OpenRelicPayload.Entry workbenchDisplayEntry() {
        return upgradeTarget();
    }

    private void validateWorkbenchTargets() {
        if (upgradeTarget() == null) {
            upgradeTargetUuid = "";
        }
        if (payload.scrapCount() < payload.protectionScrapCost()) {
            useScrapProtection = false;
        }
    }

    private void sendUpgrade() {
        OpenRelicPayload.Entry target = upgradeTarget();
        if (target != null && canUpgrade(target)) {
            if(!send("relic_upgrade|" + target.uuid() + "|" + (useScrapProtection && canUseScrapProtection()))){
                awaitingUpgradeResult=false;
                pendingUpgradeUuid="";
                upgradeResultSuccess=false;
                upgradeResultTicks=UPGRADE_RESULT_TICKS;
            }
        }else{
            awaitingUpgradeResult=false;
            pendingUpgradeUuid="";
            upgradeResultSuccess=false;
            upgradeResultTicks=UPGRADE_RESULT_TICKS;
        }
    }

    private void dismantleImmediately(String uuid) {
        OpenRelicPayload.Entry target = entryByUuid(uuid);
        if (target == null || target.locked() || target.equippedSlot() >= 0) {
            return;
        }
        if (uuid.equals(upgradeTargetUuid)) {
            upgradeTargetUuid = "";
        }
        send("relic_dismantle|" + uuid);
        rebuild();
    }

    private Component batchDismantleLabel() {
        if (!batchDismantleMode) {
            return YoikoClientText.tr("yoiko_core.ui.relic.batch_dismantle");
        }
        int scrap = batchSelectedScrap();
        if (batchDismantleSelection.isEmpty()) {
            return YoikoClientText.tr("yoiko_core.ui.relic.batch_cancel");
        }
        return batchDismantleArmed
                ? YoikoClientText.tr("yoiko_core.ui.relic.batch_confirm")
                : YoikoClientText.tr("yoiko_core.ui.relic.batch_summary",
                        batchDismantleSelection.size(), scrap);
    }

    private int batchSelectedScrap() {
        return batchDismantleSelection.stream()
                .map(this::entryByUuid)
                .filter(java.util.Objects::nonNull)
                .mapToInt(OpenRelicPayload.Entry::scrapValue)
                .sum();
    }

    private void handleBatchDismantleButton() {
        if (!batchDismantleMode) {
            batchDismantleMode = true;
            batchDismantleArmed = false;
            rebuild();
            return;
        }
        if (batchDismantleSelection.isEmpty()) {
            batchDismantleMode = false;
            batchDismantleArmed = false;
            rebuild();
            return;
        }
        if (!batchDismantleArmed) {
            batchDismantleArmed = true;
            rebuild();
            return;
        }
        if (!ClientServerRequestState.begin(this, "relic_batch_dismantle")) {
            return;
        }
        List<UUID> relicIds = batchDismantleSelection.stream().map(UUID::fromString).toList();
        PacketDistributor.sendToServer(ClientMenuSession.relicBatchDismantle(relicIds));
        batchDismantleSelection.clear();
        batchDismantleMode = false;
        batchDismantleArmed = false;
    }

    /** Compact parchment-toned arrows used only inside the equipped-effect panel. */
    private final class EffectPageButton extends AbstractButton {
        private final int direction;

        private EffectPageButton(int x, int y, int direction) {
            super(
                    x,
                    y,
                    EFFECT_PAGE_BUTTON_SIZE,
                    EFFECT_PAGE_BUTTON_SIZE,
                    YoikoClientText.tr(direction < 0
                            ? "yoiko_core.ui.previous"
                            : "yoiko_core.ui.next")
            );
            this.direction = Integer.signum(direction);
        }

        @Override
        public void onPress() {
            if (active) {
                changeEffectPage(direction);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean highlighted = active && isHoveredOrFocused();
            int fillColor = highlighted ? 0x3D9A754E : 0x18FFF1C9;
            int outlineColor = active ? 0x72876645 : 0x3DAA9B83;
            int arrowColor = !active ? 0xFFB9AA91 : highlighted ? 0xFF503722 : 0xFF7B583A;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, fillColor);
            renderOutline(graphics, getX(), getY(), width, height, outlineColor);
            renderEffectPageChevron(graphics, getX() + width / 2, getY() + height / 2,
                    direction, arrowColor);
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

    private static void renderEffectPageChevron(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int direction,
        int color
    ) {
        for (int offset = -3; offset <= 3; offset++) {
            int x = centerX + direction * (2 - Math.abs(offset));
            graphics.fill(x, centerY + offset, x + 1, centerY + offset + 1, color);
        }
    }

    private final class BatchDismantleButton extends AbstractButton {
        private BatchDismantleButton(int x, int y) {
            super(x, y, DISMANTLE_ICON_SIZE, DISMANTLE_ICON_SIZE, batchDismantleLabel());
        }

        @Override
        public void onPress() {
            if (active) {
                handleBatchDismantleButton();
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int coreColor;
            int glowColor = 0;
            if (!active) {
                coreColor = 0xFF9B927C;
            } else if (batchDismantleArmed) {
                coreColor = 0xFFF0A391;
                glowColor = 0x62E85C50;
            } else if (batchDismantleMode) {
                coreColor = 0xFFF4E2B5;
                glowColor = 0x5258D878;
            } else {
                coreColor = isHoveredOrFocused() ? 0xFFFFF1C0 : 0xFFE9D4A0;
            }

            if (glowColor != 0) {
                blitDismantleIcon(graphics, getX() - 1, getY(), glowColor);
                blitDismantleIcon(graphics, getX() + 1, getY(), glowColor);
                blitDismantleIcon(graphics, getX(), getY() - 1, glowColor);
                blitDismantleIcon(graphics, getX(), getY() + 1, glowColor);
            }
            blitDismantleIcon(graphics, getX(), getY(), coreColor);
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

    private static void blitDismantleIcon(GuiGraphics graphics, int x, int y, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        RenderSystem.setShaderColor(red, green, blue, alpha);
        try {
            graphics.blit(
                    MAILBOX_ACTION_ICONS,
                    x,
                    y,
                    DISMANTLE_ICON_SIZE,
                    DISMANTLE_ICON_SIZE,
                    (float) DISMANTLE_ICON_SOURCE_X,
                    DISMANTLE_ICON_SOURCE_Y,
                    DISMANTLE_ICON_SIZE,
                    DISMANTLE_ICON_SIZE,
                    MAILBOX_ACTION_TEXTURE_WIDTH,
                    MAILBOX_ACTION_TEXTURE_HEIGHT
            );
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void resetCtrlDoubleClick() {
        lastCtrlClickUuid = "";
        lastCtrlClickTimeMs = 0L;
    }

    private boolean send(String action) {
        if (!ClientServerRequestState.begin(this, action)) {
            return false;
        }
        PacketDistributor.sendToServer(ClientMenuSession.action(action));
        return true;
    }

    private final class PresetTabButton extends AbstractButton {
        private final int presetIndex;

        private PresetTabButton(int x, int y, int presetIndex) {
            super(x, y, PRESET_BUTTON_WIDTH, PRESET_BUTTON_HEIGHT,
                    Component.literal(Integer.toString(presetIndex + 1)));
            this.presetIndex = presetIndex;
        }

        @Override
        public void onPress() {
            if (presetIndex != payload.activePreset()) {
                send("relic_preset|" + presetIndex);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = presetIndex == payload.activePreset();
            YoikoScreenStyle.renderButton(graphics, getX(), getY(), getWidth(), getHeight(),
                    isHoveredOrFocused() || selected, active);
            if (selected) {
                graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFC99A55);
            }
            graphics.drawCenteredString(font, getMessage(), getX() + getWidth() / 2,
                    getY() + 3, selected ? 0xFF6B4325 : 0xFF8A6A4C);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            int filled = presetIndex < payload.presetFilledCounts().size()
                    ? payload.presetFilledCounts().get(presetIndex) : 0;
            output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                    YoikoClientText.tr("yoiko_core.ui.relic.preset_narration", presetIndex + 1, filled));
        }
    }

    private int left() {
        return YoikoMenuLayout.splitLeft(this.width);
    }

    private int top() {
        return YoikoMenuLayout.splitTop(this.height);
    }

    private void drawScaledString(GuiGraphics graphics, Component text, int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 220.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawCenteredNoShadow(
            GuiGraphics graphics,
            Component text,
            int centerX,
            int y,
            int color
    ) {
        graphics.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private void drawRightAlignedNoShadow(
            GuiGraphics graphics,
            Component text,
            int rightX,
            int y,
            int color
    ) {
        graphics.drawString(this.font, text, rightX - this.font.width(text), y, color, false);
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

    private static void renderOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

}
