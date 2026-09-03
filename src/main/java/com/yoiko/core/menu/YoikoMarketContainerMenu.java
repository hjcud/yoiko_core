package com.yoiko.core.menu;

import com.yoiko.core.economy.EconomyManager;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.registry.YoikoMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class YoikoMarketContainerMenu extends AbstractContainerMenu {
    public static final int REGISTRATION_SLOT_X = 18;
    public static final int REGISTRATION_SLOT_Y = 57;
    public static final int PLAYER_INVENTORY_SLOT_X = 12;
    public static final int PLAYER_INVENTORY_SLOT_Y = 168;
    public static final int HOTBAR_SLOT_Y = 232;
    public static final int SLOT_STEP = 19;
    public static final int REGISTRATION_SLOT_INDEX = 0;
    public static final int PLAYER_SLOT_START = 1;
    public static final int TOTAL_SLOT_COUNT = 37;

    private final SimpleContainer registration = new SimpleContainer(1);
    private boolean registrationActive;

    public YoikoMarketContainerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf ignored) {
        this(containerId, inventory);
    }

    public YoikoMarketContainerMenu(int containerId, Inventory inventory) {
        super(YoikoMenus.YOIKO_MARKET.get(), containerId);
        registration.startOpen(inventory.player);
        addSlot(new RegistrationSlot(registration, REGISTRATION_SLOT_INDEX, REGISTRATION_SLOT_X, REGISTRATION_SLOT_Y));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new MarketPlayerSlot(
                        inventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_SLOT_X + column * SLOT_STEP,
                        PLAYER_INVENTORY_SLOT_Y + row * SLOT_STEP
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new MarketPlayerSlot(
                    inventory,
                    column,
                    PLAYER_INVENTORY_SLOT_X + column * SLOT_STEP,
                    HOTBAR_SLOT_Y
            ));
        }
    }

    public ItemStack registrationStack() {
        return registration.getItem(REGISTRATION_SLOT_INDEX);
    }

    public void setRegistrationActive(Player player, boolean active) {
        if (registrationActive == active) {
            return;
        }
        registrationActive = active;
        if (!active && !player.level().isClientSide) {
            returnRegistrationItem(player);
        }
        broadcastChanges();
    }

    public boolean registerItem(Player player, long totalPrice) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || !registrationActive || registrationStack().isEmpty()) {
            return false;
        }
        ItemStack listed = registrationStack().copy();
        if (!EconomyManager.listItem(serverPlayer, listed, totalPrice)) {
            return false;
        }
        registration.setItem(REGISTRATION_SLOT_INDEX, ItemStack.EMPTY);
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (index == REGISTRATION_SLOT_INDEX) {
            if (!moveItemStackTo(source, PLAYER_SLOT_START, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!registrationActive || !EconomyManager.isMarketable(source)
                || !moveItemStackTo(source, REGISTRATION_SLOT_INDEX, REGISTRATION_SLOT_INDEX + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            returnRegistrationItem(player);
        }
        registration.stopOpen(player);
    }

    private void returnRegistrationItem(Player player) {
        ItemStack stack = registration.removeItemNoUpdate(REGISTRATION_SLOT_INDEX);
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private final class RegistrationSlot extends Slot {
        private RegistrationSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return registrationActive && EconomyManager.isMarketable(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return registrationActive;
        }

        @Override
        public boolean isActive() {
            return registrationActive;
        }
    }

    private static final class MarketPlayerSlot extends Slot {
        private MarketPlayerSlot(Inventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return true;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return true;
        }
    }
}
