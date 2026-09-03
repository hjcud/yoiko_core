package com.yoiko.core.storage;

import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.registry.YoikoMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class YoikoStorageMenu extends AbstractContainerMenu {
    public static final int STORAGE_COLUMNS = 9;
    public static final int STORAGE_ROWS = 6;
    public static final int STORAGE_SLOT_COUNT = PlayerYoikoData.YOIKO_STORAGE_PAGE_SIZE;
    public static final int TOTAL_SLOT_COUNT = STORAGE_SLOT_COUNT + 36;
    public static final int SLOT_STEP = 19;
    public static final int STORAGE_SLOT_X = 12;
    public static final int STORAGE_SLOT_Y = 27;
    public static final int PLAYER_INVENTORY_SLOT_X = 12;
    public static final int PLAYER_INVENTORY_SLOT_Y = 168;
    public static final int HOTBAR_SLOT_Y = 232;

    private final YoikoStorageContainer storage;
    private final int activeSlots;
    private final int visibleActiveSlots;
    private final int page;
    private final int totalPages;
    private final StatusSummary status;

    public YoikoStorageMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, createClientInit(playerInventory, buffer));
    }

    public YoikoStorageMenu(int containerId, Inventory playerInventory, YoikoStorageContainer storage) {
        this(containerId, playerInventory, storage, StatusSummary.empty());
    }

    public YoikoStorageMenu(int containerId, Inventory playerInventory, YoikoStorageContainer storage, StatusSummary status) {
        super(YoikoMenus.YOIKO_STORAGE.get(), containerId);
        checkContainerSize(storage, STORAGE_SLOT_COUNT);
        this.storage = storage;
        this.activeSlots = storage.activeSlots();
        this.visibleActiveSlots = storage.visibleActiveSlots();
        this.page = storage.page();
        this.totalPages = storage.totalPages();
        this.status = status == null ? StatusSummary.empty() : status;
        storage.startOpen(playerInventory.player);

        for (int row = 0; row < STORAGE_ROWS; row++) {
            for (int column = 0; column < STORAGE_COLUMNS; column++) {
                int index = column + row * STORAGE_COLUMNS;
                int x = STORAGE_SLOT_X + column * SLOT_STEP;
                int y = STORAGE_SLOT_Y + row * SLOT_STEP;
                addSlot(index < visibleActiveSlots ? new StorageSlot(storage, index, x, y) : new LockedSlot(storage, index, x, y));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, PLAYER_INVENTORY_SLOT_X + column * SLOT_STEP, PLAYER_INVENTORY_SLOT_Y + row * SLOT_STEP));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INVENTORY_SLOT_X + column * SLOT_STEP, HOTBAR_SLOT_Y));
        }
    }

    private YoikoStorageMenu(int containerId, Inventory playerInventory, ClientInit init) {
        this(containerId, playerInventory, init.storage(), init.status());
    }

    public int getActiveSlots() {
        return activeSlots;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getVisibleActiveSlots() {
        return visibleActiveSlots;
    }

    public StatusSummary getStatus() {
        return status;
    }

    public boolean isStorageSlot(int menuIndex) {
        return menuIndex >= 0 && menuIndex < STORAGE_SLOT_COUNT;
    }

    public boolean isStorageSlotLocked(int menuIndex) {
        return isStorageSlot(menuIndex) && menuIndex >= visibleActiveSlots;
    }

    public boolean isStorageSlotUserLocked(int menuIndex) {
        return isStorageSlot(menuIndex) && storage.isUserLocked(menuIndex);
    }

    public boolean toggleStorageSlotLock(int menuIndex) {
        return storage.toggleUserLock(menuIndex);
    }

    public boolean sortCurrentPage() {
        boolean sorted = storage.sortUnlocked();
        if (sorted) {
            broadcastChanges();
        }
        return sorted;
    }

    @Override
    public boolean stillValid(Player player) {
        return storage.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        if (index < STORAGE_SLOT_COUNT && isStorageSlotUserLocked(index)) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        moved = stack.copy();
        if (index < STORAGE_SLOT_COUNT) {
            if (!moveItemStackTo(stack, STORAGE_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, visibleActiveSlots, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        storage.saveToData();
        storage.stopOpen(player);
    }

    private static ClientInit createClientInit(Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        int activeSlots = buffer == null ? 0 : buffer.readVarInt();
        int page = buffer == null ? 0 : buffer.readVarInt();
        StatusSummary status = buffer == null ? StatusSummary.empty() : StatusSummary.read(buffer);
        long lockedMask = buffer == null ? 0L : buffer.readLong();
        return new ClientInit(new YoikoStorageContainer(playerInventory.player.getUUID(), activeSlots, page, lockedMask), status);
    }

    private record ClientInit(YoikoStorageContainer storage, StatusSummary status) {
    }

    public record StatusSummary(
            int mailCount,
            String activeRankId,
            String activeRankName,
            int rankColor,
            int equippedRelicCount,
            int maxRelicSlots,
            List<RelicSummaryEntry> equippedRelics,
            String headCosmeticId,
            String chestCosmeticId,
            String feetCosmeticId,
            String feetCosmeticRarity,
            String feetCosmeticCategory
    ) {
        static StatusSummary empty() {
            return new StatusSummary(0, "", "", 0xFFFFFF, 0, PlayerYoikoData.RELIC_EQUIP_SLOT_COUNT,
                    List.of(), "", "", "", "", "NONE");
        }

        static StatusSummary read(RegistryFriendlyByteBuf buffer) {
            return new StatusSummary(
                    buffer.readVarInt(),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    readRelicEntries(buffer),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(32),
                    buffer.readUtf(32)
            );
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(mailCount);
            buffer.writeUtf(activeRankId, 128);
            buffer.writeUtf(activeRankName, 128);
            buffer.writeInt(rankColor);
            buffer.writeVarInt(equippedRelicCount);
            buffer.writeVarInt(maxRelicSlots);
            buffer.writeVarInt(equippedRelics.size());
            for (RelicSummaryEntry entry : equippedRelics) {
                entry.write(buffer);
            }
            buffer.writeUtf(headCosmeticId, 128);
            buffer.writeUtf(chestCosmeticId, 128);
            buffer.writeUtf(feetCosmeticId, 128);
            buffer.writeUtf(feetCosmeticRarity, 32);
            buffer.writeUtf(feetCosmeticCategory, 32);
        }

        private static List<RelicSummaryEntry> readRelicEntries(RegistryFriendlyByteBuf buffer) {
            int encodedSize = Math.max(0, buffer.readVarInt());
            List<RelicSummaryEntry> entries = new ArrayList<>(Math.min(5, encodedSize));
            for (int i = 0; i < encodedSize; i++) {
                RelicSummaryEntry entry = RelicSummaryEntry.read(buffer);
                if (entries.size() < 5) {
                    entries.add(entry);
                }
            }
            return List.copyOf(entries);
        }
    }

    public record RelicSummaryEntry(
            String nameKey,
            String effect,
            String secondaryEffect,
            String rarity,
            int level,
            int effectiveLevel,
            double value,
            double secondaryValue,
            int scrapValue,
            boolean primaryEffectSuppressed,
            boolean secondaryEffectSuppressed
    ) {
        static RelicSummaryEntry read(RegistryFriendlyByteBuf buffer) {
            return new RelicSummaryEntry(
                    buffer.readUtf(160),
                    buffer.readUtf(160),
                    buffer.readUtf(160),
                    buffer.readUtf(32),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            );
        }

        void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(nameKey, 160);
            buffer.writeUtf(effect, 160);
            buffer.writeUtf(secondaryEffect, 160);
            buffer.writeUtf(rarity, 32);
            buffer.writeVarInt(level);
            buffer.writeVarInt(effectiveLevel);
            buffer.writeDouble(value);
            buffer.writeDouble(secondaryValue);
            buffer.writeVarInt(scrapValue);
            buffer.writeBoolean(primaryEffectSuppressed);
            buffer.writeBoolean(secondaryEffectSuppressed);
        }
    }

    private static class StorageSlot extends Slot {
        StorageSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !((YoikoStorageContainer) container).isUserLocked(getContainerSlot());
        }

        @Override
        public boolean mayPickup(Player player) {
            return !((YoikoStorageContainer) container).isUserLocked(getContainerSlot());
        }
    }

    private static final class LockedSlot extends Slot {
        LockedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
