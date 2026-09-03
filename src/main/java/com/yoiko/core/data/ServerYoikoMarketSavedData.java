package com.yoiko.core.data;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.economy.MarketplaceListing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class ServerYoikoMarketSavedData extends SavedData {
    public static final String DATA_NAME = YoikoServerCore.MODID + "_market";
    public static final int DATA_VERSION = 1;
    private static final int MAX_LISTINGS_ON_LOAD = 100_000;
    private final List<MarketplaceListing> marketplaceListings = new ArrayList<>();
    private long revision = 1L;

    public static ServerYoikoMarketSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ServerYoikoMarketSavedData::new, ServerYoikoMarketSavedData::load,
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_NAME
        );
    }

    public List<MarketplaceListing> marketplaceListings() {
        return marketplaceListings;
    }

    public long revision() {
        return revision;
    }

    public void markChanged() {
        revision = revision == Long.MAX_VALUE ? 1L : revision + 1L;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        ListTag listings = new ListTag();
        for (MarketplaceListing listing : marketplaceListings) {
            if (!listing.item().isEmpty()) {
                listings.add(listing.save(registries));
            }
        }
        tag.put("marketplaceListings", listings);
        return tag;
    }

    private static ServerYoikoMarketSavedData load(CompoundTag raw, HolderLookup.Provider registries) {
        int sourceVersion = Math.max(0, raw.getInt("dataVersion"));
        if (sourceVersion != DATA_VERSION) {
            throw new IllegalStateException("Yoiko market data version " + sourceVersion
                    + " does not match " + DATA_VERSION + "; reset the data before starting the server.");
        }
        ServerYoikoMarketSavedData data = new ServerYoikoMarketSavedData();
        ListTag listings = raw.getList("marketplaceListings", Tag.TAG_COMPOUND);
        int loadCount = Math.min(listings.size(), MAX_LISTINGS_ON_LOAD);
        for (int i = 0; i < loadCount; i++) {
            MarketplaceListing listing = MarketplaceListing.load(listings.getCompound(i), registries);
            if (!listing.item().isEmpty()) {
                data.marketplaceListings.add(listing);
            }
        }
        if (listings.size() > loadCount) {
            YoikoServerCore.LOGGER.error("Yoiko market data contained {} listings; only the first {} were loaded.",
                    listings.size(), loadCount);
        }
        return data;
    }

}
