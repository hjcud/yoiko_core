package com.yoiko.core.economy;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public record MarketplaceTransactionRecord(
        UUID id, String action, UUID sellerUuid, String sellerName,
        UUID buyerUuid, String buyerName, ItemStack item,
        long totalPrice, long createdAt, boolean suspicious, String detail
) {
    public MarketplaceTransactionRecord {
        id = id == null ? UUID.randomUUID() : id;
        action = action == null ? "" : action;
        sellerName = sellerName == null ? "" : sellerName;
        buyerName = buyerName == null ? "" : buyerName;
        item = item == null ? ItemStack.EMPTY : item.copy();
        detail = detail == null ? "" : detail;
    }

    @Override
    public ItemStack item() {
        return item.copy();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("action", action);
        if (sellerUuid != null) tag.putUUID("sellerUuid", sellerUuid);
        tag.putString("sellerName", sellerName);
        if (buyerUuid != null) tag.putUUID("buyerUuid", buyerUuid);
        tag.putString("buyerName", buyerName);
        tag.putLong("totalPrice", totalPrice);
        tag.putLong("createdAt", createdAt);
        tag.putBoolean("suspicious", suspicious);
        tag.putString("detail", detail);
        if (item.saveOptional(registries) instanceof CompoundTag itemTag && !itemTag.isEmpty()) {
            tag.put("item", itemTag);
        }
        return tag;
    }

    public static MarketplaceTransactionRecord load(CompoundTag tag, HolderLookup.Provider registries) {
        return new MarketplaceTransactionRecord(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                tag.getString("action"),
                tag.hasUUID("sellerUuid") ? tag.getUUID("sellerUuid") : null,
                tag.getString("sellerName"),
                tag.hasUUID("buyerUuid") ? tag.getUUID("buyerUuid") : null,
                tag.getString("buyerName"),
                ItemStack.parseOptional(registries, tag.getCompound("item")),
                tag.getLong("totalPrice"),
                tag.getLong("createdAt"),
                tag.getBoolean("suspicious"),
                tag.getString("detail")
        );
    }
}
