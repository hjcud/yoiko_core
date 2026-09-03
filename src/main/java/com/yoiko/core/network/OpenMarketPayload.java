package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record OpenMarketPayload(
        long gold,
        long gems,
        int sealedRelics,
        String sessionId,
        long marketRevision,
        String section,
        int page,
        int totalPages,
        String selectedId,
        String message,
        String sort,
        String category,
        String searchQuery,
        int pendingDeliveries,
        int maxPendingDeliveries,
        double saleFeePercent,
        long minReasonableUnitPrice,
        long maxReasonableUnitPrice,
        long dailyServerBuyGoldLimit,
        long remainingDailyServerBuyGold,
        boolean rememberListingPrice,
        List<Entry> entries
) implements CustomPacketPayload {
    public static final Type<OpenMarketPayload> TYPE = new Type<>(YoikoServerCore.id("open_market"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMarketPayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenMarketPayload::write, OpenMarketPayload::new);

    private OpenMarketPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readUtf(64),
                buffer.readVarLong(),
                buffer.readUtf(32),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(64),
                buffer.readUtf(256),
                buffer.readUtf(16),
                buffer.readUtf(16),
                buffer.readUtf(64),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readBoolean(),
                readEntries(buffer)
        );
    }

    public OpenMarketPayload {
        sealedRelics = Math.max(0, sealedRelics);
        entries = entries == null ? List.of() : entries.stream()
                .map(Entry::copy)
                .toList();
    }

    public static OpenMarketPayload empty() {
        return new OpenMarketPayload(
                0L, 0L, 0, "", 0L, "server_shop", 0, 1, "", "",
                "latest", "all", "", 0, 12, 5.0D, 1L, 20_000L, 0L, 0L, true, List.of()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(gold);
        buffer.writeVarLong(gems);
        buffer.writeVarInt(sealedRelics);
        buffer.writeUtf(sessionId, 64);
        buffer.writeVarLong(marketRevision);
        buffer.writeUtf(section, 32);
        buffer.writeVarInt(page);
        buffer.writeVarInt(totalPages);
        buffer.writeUtf(selectedId, 64);
        buffer.writeUtf(message, 256);
        buffer.writeUtf(sort, 16);
        buffer.writeUtf(category, 16);
        buffer.writeUtf(searchQuery, 64);
        buffer.writeVarInt(pendingDeliveries);
        buffer.writeVarInt(maxPendingDeliveries);
        buffer.writeDouble(saleFeePercent);
        buffer.writeVarLong(minReasonableUnitPrice);
        buffer.writeVarLong(maxReasonableUnitPrice);
        buffer.writeVarLong(dailyServerBuyGoldLimit);
        buffer.writeVarLong(remainingDailyServerBuyGold);
        buffer.writeBoolean(rememberListingPrice);
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUtf(entry.id(), 64);
            buffer.writeUtf(entry.action(), 32);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack());
            buffer.writeVarLong(entry.price());
            buffer.writeUtf(entry.currency(), 16);
            buffer.writeUtf(entry.seller(), 64);
            buffer.writeVarLong(entry.remainingSeconds());
            buffer.writeBoolean(entry.own());
            buffer.writeVarLong(entry.fee());
            buffer.writeVarLong(entry.proceeds());
            buffer.writeVarLong(entry.createdAt());
            buffer.writeUtf(entry.category(), 16);
            buffer.writeVarLong(entry.medianUnitPrice());
            buffer.writeVarInt(entry.medianSampleCount());
            buffer.writeUtf(entry.purchaseOfferId(), 64);
            buffer.writeVarInt(entry.purchaseCount());
            buffer.writeVarLong(entry.purchasePrice());
            buffer.writeUtf(entry.buybackOfferId(), 64);
            buffer.writeVarInt(entry.buybackCount());
            buffer.writeVarLong(entry.buybackPrice());
        }
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(
                    buffer.readUtf(64),
                    buffer.readUtf(32),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    buffer.readVarLong(),
                    buffer.readUtf(16),
                    buffer.readUtf(64),
                    buffer.readVarLong(),
                    buffer.readBoolean(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readUtf(16),
                    buffer.readVarLong(),
                    buffer.readVarInt(),
                    buffer.readUtf(64),
                    buffer.readVarInt(),
                    buffer.readVarLong(),
                    buffer.readUtf(64),
                    buffer.readVarInt(),
                    buffer.readVarLong()
            ));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            String id,
            String action,
            ItemStack stack,
            long price,
            String currency,
            String seller,
            long remainingSeconds,
            boolean own,
            long fee,
            long proceeds,
            long createdAt,
            String category,
            long medianUnitPrice,
            int medianSampleCount,
            String purchaseOfferId,
            int purchaseCount,
            long purchasePrice,
            String buybackOfferId,
            int buybackCount,
            long buybackPrice
    ) {
        public Entry {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            price = Math.max(0L, price);
            fee = Math.max(0L, fee);
            proceeds = Math.max(0L, proceeds);
            category = category == null ? "other" : category;
            medianUnitPrice = Math.max(0L, medianUnitPrice);
            medianSampleCount = Math.max(0, medianSampleCount);
            purchaseOfferId = purchaseOfferId == null ? "" : purchaseOfferId;
            purchaseCount = Math.max(0, purchaseCount);
            purchasePrice = Math.max(0L, purchasePrice);
            buybackOfferId = buybackOfferId == null ? "" : buybackOfferId;
            buybackCount = Math.max(0, buybackCount);
            buybackPrice = Math.max(0L, buybackPrice);
        }

        public Entry(String id,String action,ItemStack stack,long price,String currency,String seller,
                     long remainingSeconds,boolean own,long fee,long proceeds,long createdAt,String category,
                     long medianUnitPrice,int medianSampleCount) {
            this(id,action,stack,price,currency,seller,remainingSeconds,own,fee,proceeds,createdAt,category,
                    medianUnitPrice,medianSampleCount,"",0,0L,"",0,0L);
        }

        private Entry copy() {
            return new Entry(
                    id, action, stack, price, currency, seller, remainingSeconds,
                    own, fee, proceeds, createdAt, category, medianUnitPrice, medianSampleCount,
                    purchaseOfferId,purchaseCount,purchasePrice,buybackOfferId,buybackCount,buybackPrice
            );
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }
}
