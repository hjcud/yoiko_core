package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenDexPayload(
        String title,
        String gachaType,
        String rarity,
        int page,
        int totalPages,
        List<String> entries,
        String selectedSpecies,
        String selectedSpeciesRate,
        String selectedRarityRate,
        String searchQuery,
        boolean admin
) implements CustomPacketPayload {
    public static final Type<OpenDexPayload> TYPE = new Type<>(YoikoServerCore.id("open_dex"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDexPayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenDexPayload::write, OpenDexPayload::new);

    private OpenDexPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUtf(128),
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readEntries(buffer),
                buffer.readUtf(256),
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readBoolean()
        );
    }

    public OpenDexPayload {
        entries = List.copyOf(entries);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(title, 128);
        buffer.writeUtf(gachaType, 64);
        buffer.writeUtf(rarity, 64);
        buffer.writeVarInt(page);
        buffer.writeVarInt(totalPages);
        buffer.writeVarInt(entries.size());
        for (String entry : entries) {
            buffer.writeUtf(entry, 256);
        }
        buffer.writeUtf(selectedSpecies, 256);
        buffer.writeUtf(selectedSpeciesRate, 64);
        buffer.writeUtf(selectedRarityRate, 64);
        buffer.writeUtf(searchQuery, 64);
        buffer.writeBoolean(admin);
    }

    private static List<String> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(buffer.readUtf(256));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
