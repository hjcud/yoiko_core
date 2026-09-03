package com.yoiko.core.economy;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public record MarketplaceSaleRecord(
        UUID id,
        ItemStack item,
        String buyerName,
        long totalPrice,
        long fee,
        long proceeds,
        long soldAt
) {
    public MarketplaceSaleRecord {
        id = id == null ? UUID.randomUUID() : id;
        item = item == null ? ItemStack.EMPTY : item.copy();
        buyerName = buyerName == null ? "" : buyerName;
        totalPrice = Math.max(0L, totalPrice);
        fee = Math.max(0L, fee);
        proceeds = Math.max(0L, proceeds);
    }

    @Override
    public ItemStack item() {
        return item.copy();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("buyerName", buyerName);
        tag.putLong("totalPrice", totalPrice);
        tag.putLong("fee", fee);
        tag.putLong("proceeds", proceeds);
        tag.putLong("soldAt", soldAt);
        if (item.saveOptional(registries) instanceof CompoundTag itemTag && !itemTag.isEmpty()) {
            tag.put("item", itemTag);
        }
        return tag;
    }

    public static MarketplaceSaleRecord load(CompoundTag tag, HolderLookup.Provider registries) {
        return new MarketplaceSaleRecord(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                ItemStack.parseOptional(registries, tag.getCompound("item")),
                tag.getString("buyerName"),
                tag.getLong("totalPrice"),
                tag.getLong("fee"),
                tag.getLong("proceeds"),
                tag.getLong("soldAt")
        );
    }
}
