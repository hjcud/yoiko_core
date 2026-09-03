package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CosmeticMenuCatalogPayload(int revision, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<CosmeticMenuCatalogPayload> TYPE =
            new Type<>(YoikoServerCore.id("cosmetic_menu_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmeticMenuCatalogPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CosmeticMenuCatalogPayload::write, CosmeticMenuCatalogPayload::new);

    private CosmeticMenuCatalogPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), readEntries(buffer));
    }

    public CosmeticMenuCatalogPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(revision);
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUtf(entry.id(), 128);
            buffer.writeUtf(entry.displayName(), 128);
            buffer.writeUtf(entry.type(), 32);
            buffer.writeUtf(entry.particleCategory(), 32);
            buffer.writeUtf(entry.requiredRank(), 128);
            buffer.writeUtf(entry.acquisitionPath(), 160);
            buffer.writeUtf(entry.creator(), 128);
            buffer.writeUtf(entry.rarity(), 32);
            buffer.writeInt(entry.color());
            buffer.writeVarLong(entry.gemPrice());
            buffer.writeUtf(entry.modelId(), 128);
            buffer.writeUtf(entry.modelAnchor(), 32);
            buffer.writeInt(entry.modelPrimaryColor());
            buffer.writeInt(entry.modelAccentColor());
            buffer.writeVarInt(entry.sortPriority());
        }
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(
                    buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(32), buffer.readUtf(32),
                    buffer.readUtf(128), buffer.readUtf(160), buffer.readUtf(128), buffer.readUtf(32),
                    buffer.readInt(), buffer.readVarLong(), buffer.readUtf(128), buffer.readUtf(32),
                    buffer.readInt(), buffer.readInt(), buffer.readVarInt()));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(String id, String displayName, String type, String particleCategory,
                        String requiredRank, String acquisitionPath, String creator, String rarity,
                        int color, long gemPrice, String modelId, String modelAnchor,
                        int modelPrimaryColor, int modelAccentColor, int sortPriority) {
    }
}
