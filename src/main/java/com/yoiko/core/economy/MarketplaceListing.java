package com.yoiko.core.economy;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public record MarketplaceListing(
        UUID id,
        UUID sellerUuid,
        String sellerName,
        ItemStack item,
        long price,
        long createdAt,
        long expiresAt,
        boolean expiryWarningSent
) {
    public MarketplaceListing {
        sellerName = sellerName == null ? "" : sellerName;
        item = item == null ? ItemStack.EMPTY : item.copy();
        price = Math.max(1L, price);
    }

    @Override
    public ItemStack item() {
        return item.copy();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("sellerUuid", sellerUuid);
        tag.putString("sellerName", sellerName);
        tag.putLong("price", price);
        tag.putLong("createdAt", createdAt);
        tag.putLong("expiresAt", expiresAt);
        tag.putBoolean("expiryWarningSent", expiryWarningSent);
        if (item.saveOptional(registries) instanceof CompoundTag itemTag && !itemTag.isEmpty()) {
            tag.put("item", itemTag);
        }
        return tag;
    }

    public static MarketplaceListing load(CompoundTag tag, HolderLookup.Provider registries) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        UUID sellerUuid = tag.hasUUID("sellerUuid") ? tag.getUUID("sellerUuid") : new UUID(0L, 0L);
        ItemStack item = ItemStack.parseOptional(registries, tag.getCompound("item"));
        return new MarketplaceListing(
                id,
                sellerUuid,
                tag.getString("sellerName"),
                item,
                Math.max(1L, tag.getLong("price")),
                tag.getLong("createdAt"),
                tag.getLong("expiresAt"),
                tag.getBoolean("expiryWarningSent")
        );
    }
}
