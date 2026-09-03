package com.yoiko.core.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yoiko.core.network.OpenRelicPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Layout, hit testing, and rendering for the three-page relic storage grid. */
final class RelicStoragePanelRenderer {
    static final int SLOT_STEP = 19;
    static final int SLOT_SIZE = 18;
    static final int ICON_SIZE = 16;
    static final int ICON_OFFSET = (SLOT_SIZE - ICON_SIZE) / 2;
    static final int GRID_COLUMNS = 9;
    static final int GRID_ROWS = 5;
    static final int STORAGE_PAGE_SIZE = GRID_COLUMNS * GRID_ROWS;
    static final int STORAGE_PAGE_COUNT = 3;
    static final int STORAGE_SLOT_COUNT = STORAGE_PAGE_SIZE * STORAGE_PAGE_COUNT;
    static final int GRID_X = 11;
    static final int GRID_Y = 150;
    private static final float SELECTED_BRIGHTNESS = 0.42F;

    private RelicStoragePanelRenderer() {
    }

    static void render(GuiGraphics graphics, Font font, int left, int top,
                       int mouseX, int mouseY, int page,
                       List<OpenRelicPayload.Entry> slots,
                       boolean batchMode, Set<String> batchSelection,
                       int selectedScrap, boolean batchArmed,
                       String draggingUuid, boolean draggingFromEquipped,
                       int leftPanelX, int titleY,
                       int pageTextCenterX, int batchStatusRightX) {
        graphics.drawString(font, YoikoClientText.tr("yoiko_core.ui.relic.owned_relics"),
                left + leftPanelX + 10, top + titleY, 0xFF8A5A32, false);
        RelicScreenRenderSupport.drawCentered(graphics, font,
                Component.literal((page + 1) + "/" + STORAGE_PAGE_COUNT),
                left + pageTextCenterX, top + titleY, 0xFF8A6A46);
        if (batchMode) {
            String status = YoikoClientText.text("yoiko_core.ui.relic.batch_status_short",
                    batchSelection.size(), selectedScrap);
            RelicScreenRenderSupport.drawRightAligned(graphics, font, Component.literal(status),
                    left + batchStatusRightX, top + titleY,
                    batchArmed ? 0xFFC84D37 : 0xFF9E7446);
        }

        int pageStart = pageStart(page);
        boolean empty = true;
        for (int slot = pageStart; slot < pageStart + STORAGE_PAGE_SIZE; slot++) {
            if (slots.get(slot) != null) {
                empty = false;
                break;
            }
        }
        if (empty) {
            RelicScreenRenderSupport.drawCentered(graphics, font,
                    YoikoClientText.tr("yoiko_core.ui.empty.relic_storage"),
                    left + GRID_X + (GRID_COLUMNS * SLOT_STEP) / 2,
                    top + GRID_Y + (GRID_ROWS * SLOT_STEP) / 2, 0xFF9A8268);
        }

        for (int index = 0; index < STORAGE_PAGE_SIZE; index++) {
            int x = left + GRID_X + (index % GRID_COLUMNS) * SLOT_STEP;
            int y = top + GRID_Y + (index / GRID_COLUMNS) * SLOT_STEP;
            OpenRelicPayload.Entry entry = slots.get(pageStart + index);
            if (entry == null || (!draggingUuid.isBlank() && !draggingFromEquipped
                    && entry.uuid().equals(draggingUuid))) {
                continue;
            }
            boolean selected = batchSelection.contains(entry.uuid());
            if (selected) {
                RenderSystem.setShaderColor(SELECTED_BRIGHTNESS, SELECTED_BRIGHTNESS,
                        SELECTED_BRIGHTNESS, 1.0F);
            }
            try {
                YoikoScreenStyle.renderRelicIcon(graphics, x + ICON_OFFSET, y + ICON_OFFSET,
                        ICON_SIZE, entry.rarity());
            } finally {
                if (selected) {
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
            }
            RelicScreenRenderSupport.drawLevelBadge(
                    graphics, font, x, y, SLOT_SIZE, entry.level(), entry.level());
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                YoikoScreenStyle.renderSlotMask(graphics, x, y, SLOT_SIZE, SLOT_SIZE, 0xFFFFFF, 1);
            }
            if (entry.locked()) {
                YoikoScreenStyle.renderStorageCompactLockedSlot(graphics, x, y);
            }
            if (selected) {
                YoikoScreenStyle.renderCheckIcon(graphics, x, y, SLOT_SIZE);
            }
        }
    }

    static List<OpenRelicPayload.Entry> slots(OpenRelicPayload payload) {
        List<OpenRelicPayload.Entry> slots = new ArrayList<>(
                Collections.nCopies(STORAGE_SLOT_COUNT, null));
        List<OpenRelicPayload.Entry> pending = new ArrayList<>();
        for (OpenRelicPayload.Entry entry : payload.relics()) {
            if (entry.equippedSlot() >= 0) {
                continue;
            }
            int slot = entry.storageSlot();
            if (slot >= 0 && slot < STORAGE_SLOT_COUNT && slots.get(slot) == null) {
                slots.set(slot, entry);
            } else {
                pending.add(entry);
            }
        }
        int nextFree = 0;
        for (OpenRelicPayload.Entry entry : pending) {
            while (nextFree < STORAGE_SLOT_COUNT && slots.get(nextFree) != null) {
                nextFree++;
            }
            if (nextFree >= STORAGE_SLOT_COUNT) {
                break;
            }
            slots.set(nextFree++, entry);
        }
        return slots;
    }

    static int slotIndexAt(int mouseX, int mouseY, int left, int top, int page) {
        int localX = mouseX - (left + GRID_X);
        int localY = mouseY - (top + GRID_Y);
        if (localX < 0 || localY < 0) {
            return -1;
        }
        int column = localX / SLOT_STEP;
        int row = localY / SLOT_STEP;
        if (column >= GRID_COLUMNS || row >= GRID_ROWS
                || localX % SLOT_STEP >= SLOT_SIZE || localY % SLOT_STEP >= SLOT_SIZE) {
            return -1;
        }
        return pageStart(page) + row * GRID_COLUMNS + column;
    }

    static OpenRelicPayload.Entry entryAt(List<OpenRelicPayload.Entry> slots, int slot) {
        return slot < 0 || slot >= STORAGE_SLOT_COUNT ? null : slots.get(slot);
    }

    static int slotOf(List<OpenRelicPayload.Entry> slots, OpenRelicPayload.Entry target) {
        if (target == null) {
            return -1;
        }
        for (int slot = 0; slot < slots.size(); slot++) {
            OpenRelicPayload.Entry entry = slots.get(slot);
            if (entry != null && entry.uuid().equals(target.uuid())) {
                return slot;
            }
        }
        return -1;
    }

    static int pageStart(int page) {
        return page * STORAGE_PAGE_SIZE;
    }

    static boolean slotOnPage(int slot, int page) {
        return slot >= pageStart(page) && slot < pageStart(page) + STORAGE_PAGE_SIZE;
    }
}
