package com.yoiko.core.data;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.economy.MarketplaceTransactionRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class ServerYoikoAuditSavedData extends SavedData {
    public static final String DATA_NAME = YoikoServerCore.MODID + "_audit";
    public static final int DATA_VERSION = 1;
    private static final int MAX_MARKET_RECORDS = 1_000;
    private static final int MAX_OPERATIONAL_RECORDS = 1_000;
    private final List<MarketplaceTransactionRecord> marketplaceTransactions = new ArrayList<>();
    private final List<OperationalAuditRecord> operationalRecords = new ArrayList<>();
    private long marketRevision = 1L;
    private transient MinecraftServer server;

    public static ServerYoikoAuditSavedData get(MinecraftServer server) {
        ServerYoikoAuditSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ServerYoikoAuditSavedData::new, ServerYoikoAuditSavedData::load,
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_NAME
        );
        data.server = server;
        return data;
    }

    public List<MarketplaceTransactionRecord> marketplaceTransactions() {
        return marketplaceTransactions;
    }

    public List<OperationalAuditRecord> operationalRecords() {
        return operationalRecords;
    }

    public long marketRevision() {
        return marketRevision;
    }

    public void addOperational(String category, String action, UUID playerUuid, String playerName, String detail) {
        OperationalAuditRecord record = new OperationalAuditRecord(
                UUID.randomUUID(), category, action, playerUuid, playerName, detail, System.currentTimeMillis());
        operationalRecords.add(0, record);
        if (server != null) {
            ServerYoikoEconomySavedData.get(server).recordOperational(record);
        }
        trim(operationalRecords, MAX_OPERATIONAL_RECORDS);
        setDirty();
    }

    public void addMarket(MarketplaceTransactionRecord record) {
        marketplaceTransactions.add(0, record);
        if (server != null) {
            ServerYoikoEconomySavedData.get(server).recordMarket(record);
        }
        trim(marketplaceTransactions, MAX_MARKET_RECORDS);
        marketRevision = marketRevision == Long.MAX_VALUE ? 1L : marketRevision + 1L;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        ListTag market = new ListTag();
        for (MarketplaceTransactionRecord transaction : marketplaceTransactions) {
            market.add(transaction.save(registries));
        }
        tag.put("marketplaceTransactions", market);
        ListTag operational = new ListTag();
        for (OperationalAuditRecord record : operationalRecords) {
            operational.add(record.save());
        }
        tag.put("operationalRecords", operational);
        return tag;
    }

    private static ServerYoikoAuditSavedData load(CompoundTag raw, HolderLookup.Provider registries) {
        int sourceVersion = Math.max(0, raw.getInt("dataVersion"));
        if (sourceVersion != DATA_VERSION) {
            throw new IllegalStateException("Yoiko audit data version " + sourceVersion
                    + " does not match " + DATA_VERSION + "; reset the data before starting the server.");
        }
        ServerYoikoAuditSavedData data = new ServerYoikoAuditSavedData();
        ListTag market = raw.getList("marketplaceTransactions", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(market.size(), MAX_MARKET_RECORDS); i++) {
            data.marketplaceTransactions.add(MarketplaceTransactionRecord.load(market.getCompound(i), registries));
        }
        ListTag operational = raw.getList("operationalRecords", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(operational.size(), MAX_OPERATIONAL_RECORDS); i++) {
            data.operationalRecords.add(OperationalAuditRecord.load(operational.getCompound(i)));
        }
        return data;
    }

    private static <T> void trim(List<T> values, int max) {
        while (values.size() > max) {
            values.remove(values.size() - 1);
        }
    }
}
