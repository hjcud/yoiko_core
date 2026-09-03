package com.yoiko.core.storage;

import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import java.util.UUID;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class YoikoStorageContainer extends SimpleContainer {
    public static final int SIZE = PlayerYoikoData.YOIKO_STORAGE_PAGE_SIZE;

    private final UUID owner;
    private final ServerYoikoSavedData savedData;
    private final PlayerYoikoData data;
    private final int activeSlots;
    private final int page;
    private final int pageStart;
    private long lockedMask;
    private boolean loading;

    public YoikoStorageContainer(ServerPlayer player, ServerYoikoSavedData savedData, PlayerYoikoData data, int activeSlots, int page) {
        super(SIZE);
        this.owner = player.getUUID();
        this.savedData = savedData;
        this.data = data;
        this.activeSlots = PlayerYoikoData.clampYoikoStorageSlots(activeSlots);
        this.page = PlayerYoikoData.clampYoikoStoragePage(page, this.activeSlots);
        this.pageStart = this.page * SIZE;
        this.lockedMask = maskFor(data, this.pageStart);
        data.ensureYoikoStorageSize(this.activeSlots);
        this.loading = true;
        for (int i = 0; i < SIZE; i++) {
            int storageIndex = pageStart + i;
            if (storageIndex < this.activeSlots && storageIndex < data.yoikoStorage.size()) {
                super.setItem(i, data.yoikoStorage.get(storageIndex).copy());
            }
        }
        this.loading = false;
    }

    public YoikoStorageContainer(UUID owner, int activeSlots, int page, long lockedMask) {
        super(SIZE);
        this.owner = owner;
        this.savedData = null;
        this.data = null;
        this.activeSlots = PlayerYoikoData.clampYoikoStorageSlots(activeSlots);
        this.page = PlayerYoikoData.clampYoikoStoragePage(page, this.activeSlots);
        this.pageStart = this.page * SIZE;
        this.lockedMask = lockedMask;
    }

    public int activeSlots() {
        return activeSlots;
    }

    public int page() {
        return page;
    }

    public int pageStart() {
        return pageStart;
    }

    public int totalPages() {
        return PlayerYoikoData.yoikoStoragePageCount(activeSlots);
    }

    public int visibleActiveSlots() {
        return Math.max(0, Math.min(SIZE, activeSlots - pageStart));
    }

    public boolean isUserLocked(int localSlot) {
        return localSlot >= 0 && localSlot < SIZE && (lockedMask & (1L << localSlot)) != 0L;
    }

    public boolean toggleUserLock(int localSlot) {
        if (data == null || localSlot < 0 || localSlot >= visibleActiveSlots()) {
            return false;
        }
        int absoluteSlot = pageStart + localSlot;
        if (!data.lockedYoikoStorageSlots.remove(absoluteSlot)) {
            data.lockedYoikoStorageSlots.add(absoluteSlot);
        }
        lockedMask = maskFor(data, pageStart);
        savedData.markDirty(owner);
        return true;
    }

    public boolean sortUnlocked() {
        if (data == null) {
            return false;
        }
        List<ItemStack> before = new java.util.ArrayList<>(SIZE);
        for (int slot = 0; slot < SIZE; slot++) {
            before.add(getItem(slot).copy());
        }
        List<ItemStack> merged = new java.util.ArrayList<>();
        for (int slot = 0; slot < visibleActiveSlots(); slot++) {
            if (isUserLocked(slot)) {
                continue;
            }
            ItemStack source = getItem(slot);
            if (source.isEmpty()) {
                continue;
            }
            ItemStack remaining = source.copy();
            for (ItemStack target : merged) {
                if (!ItemStack.isSameItemSameComponents(target, remaining)) {
                    continue;
                }
                int moved = Math.min(remaining.getCount(), target.getMaxStackSize() - target.getCount());
                if (moved > 0) {
                    target.grow(moved);
                    remaining.shrink(moved);
                }
                if (remaining.isEmpty()) {
                    break;
                }
            }
            while (!remaining.isEmpty()) {
                int amount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack split = remaining.copyWithCount(amount);
                merged.add(split);
                remaining.shrink(amount);
            }
        }
        merged.sort(java.util.Comparator
                .comparing((ItemStack stack) -> net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString())
                .thenComparing(stack -> stack.getHoverName().getString())
                .thenComparing(java.util.Comparator.comparingInt(ItemStack::getCount).reversed()));
        int cursor = 0;
        this.loading = true;
        for (int slot = 0; slot < visibleActiveSlots(); slot++) {
            if (!isUserLocked(slot)) {
                super.setItem(slot, cursor < merged.size() ? merged.get(cursor++).copy() : ItemStack.EMPTY);
            }
        }
        this.loading = false;
        if (!sameItemTotals(before, snapshot())) {
            this.loading = true;
            for (int slot = 0; slot < SIZE; slot++) {
                super.setItem(slot, before.get(slot).copy());
            }
            this.loading = false;
            com.yoiko.core.YoikoServerCore.LOGGER.error(
                    "Storage sort invariant failed for player {} page {}; original contents restored.", owner, page);
            return false;
        }
        saveToData();
        return true;
    }

    private List<ItemStack> snapshot() {
        List<ItemStack> values = new java.util.ArrayList<>(SIZE);
        for (int slot = 0; slot < SIZE; slot++) {
            values.add(getItem(slot).copy());
        }
        return values;
    }

    private static boolean sameItemTotals(List<ItemStack> left, List<ItemStack> right) {
        List<ItemStack> unmatched = new java.util.ArrayList<>();
        for (ItemStack stack : right) {
            if (!stack.isEmpty()) {
                unmatched.add(stack.copy());
            }
        }
        for (ItemStack source : left) {
            if (source.isEmpty()) {
                continue;
            }
            int remaining = source.getCount();
            for (ItemStack target : unmatched) {
                if (remaining <= 0 || !ItemStack.isSameItemSameComponents(source, target)) {
                    continue;
                }
                int matched = Math.min(remaining, target.getCount());
                remaining -= matched;
                target.shrink(matched);
            }
            if (remaining > 0) {
                return false;
            }
        }
        return unmatched.stream().allMatch(ItemStack::isEmpty);
    }

    void saveToData() {
        if (savedData == null || data == null) {
            return;
        }
        data.ensureYoikoStorageSize(activeSlots);
        for (int i = 0; i < SIZE; i++) {
            int storageIndex = pageStart + i;
            if (storageIndex < activeSlots && storageIndex < data.yoikoStorage.size()) {
                data.yoikoStorage.set(storageIndex, getItem(i).copy());
            }
        }
        savedData.markDirty(owner);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (!loading) {
            saveToData();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return owner.equals(player.getUUID());
    }

    private static long maskFor(PlayerYoikoData data, int pageStart) {
        long mask = 0L;
        for (int local = 0; local < SIZE; local++) {
            if (data.lockedYoikoStorageSlots.contains(pageStart + local)) {
                mask |= 1L << local;
            }
        }
        return mask;
    }
}
