package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenCosmeticPayload(String selectedId, String message, String category, int page, int totalPages,
                                  boolean favoritesOnly, long gems, List<EquippedSlot> equippedSlots,
                                  List<Entry> equippedEntries, List<Entry> cosmetics) implements CustomPacketPayload {
    public static final Type<OpenCosmeticPayload> TYPE = new Type<>(YoikoServerCore.id("open_cosmetic"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCosmeticPayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenCosmeticPayload::write, OpenCosmeticPayload::new);

    private OpenCosmeticPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(128), buffer.readUtf(256), buffer.readUtf(16), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readBoolean(), buffer.readVarLong(),
                readSlots(buffer), readEntries(buffer), readEntries(buffer));
    }

    public OpenCosmeticPayload {
        equippedSlots = List.copyOf(equippedSlots);
        equippedEntries = List.copyOf(equippedEntries);
        cosmetics = List.copyOf(cosmetics);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(selectedId, 128);
        buffer.writeUtf(message, 256);
        buffer.writeUtf(category, 16);
        buffer.writeVarInt(page);
        buffer.writeVarInt(totalPages);
        buffer.writeBoolean(favoritesOnly);
        buffer.writeVarLong(gems);
        buffer.writeVarInt(equippedSlots.size());
        for (EquippedSlot slot : equippedSlots) {
            slot.write(buffer);
        }
        writeEntries(buffer, equippedEntries);
        writeEntries(buffer, cosmetics);
    }

    private static void writeEntries(RegistryFriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUtf(entry.id(), 128);
            buffer.writeBoolean(entry.owned());
            buffer.writeBoolean(entry.equipped());
            buffer.writeBoolean(entry.favorite());
        }
    }

    private static List<EquippedSlot> readSlots(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<EquippedSlot> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(new EquippedSlot(buffer));
        }
        return slots;
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(Entry.state(
                    buffer.readUtf(128), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record EquippedSlot(String type, String cosmeticId) {
        private EquippedSlot(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUtf(32), buffer.readUtf(128));
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(type, 32);
            buffer.writeUtf(cosmeticId, 128);
        }
    }

    public record Entry(String id, String displayName, String type, String particleCategory, String requiredRank, String acquisitionPath,
                        String creator, String rarity, boolean owned, boolean equipped, boolean favorite,
                        int color, long gemPrice, String modelId, String modelAnchor,
                        int modelPrimaryColor, int modelAccentColor) {
        public static Entry state(String id, boolean owned, boolean equipped, boolean favorite) {
            return new Entry(id, "", "", "", "", "", "", "",
                    owned, equipped, favorite, 0xFFFFFF, 0L, "", "NONE",
                    0xFFFFFFFF, 0xFFFFFFFF);
        }
    }
}
