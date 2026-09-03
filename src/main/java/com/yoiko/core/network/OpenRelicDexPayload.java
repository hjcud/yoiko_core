package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenRelicDexPayload(
        String title,
        String appraisalCategory,
        String rarity,
        int page,
        int totalPages,
        String searchQuery,
        List<Entry> entries,
        Entry selected
) implements CustomPacketPayload {
    public static final Type<OpenRelicDexPayload> TYPE = new Type<>(YoikoServerCore.id("open_relic_dex"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRelicDexPayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenRelicDexPayload::write, OpenRelicDexPayload::new);

    private OpenRelicDexPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUtf(128),
                buffer.readUtf(32),
                buffer.readUtf(32),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(64),
                readEntries(buffer),
                new Entry(buffer)
        );
    }

    public OpenRelicDexPayload {
        appraisalCategory = appraisalCategory == null || appraisalCategory.isBlank()
                ? "all" : appraisalCategory;
        entries = List.copyOf(entries);
        selected = selected == null ? Entry.empty() : selected;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(title, 128);
        buffer.writeUtf(appraisalCategory, 32);
        buffer.writeUtf(rarity, 32);
        buffer.writeVarInt(page);
        buffer.writeVarInt(totalPages);
        buffer.writeUtf(searchQuery, 64);
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            entry.write(buffer);
        }
        selected.write(buffer);
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            String relicId,
            String displayName,
            String effect,
            String rarity,
            String individualRate,
            String rarityRate,
            double value,
            double upgradeBonus
    ) {
        public static Entry empty() {
            return new Entry("", "", "", "COMMON", "-", "-", 0.0D, 0.0D);
        }

        private Entry(RegistryFriendlyByteBuf buffer) {
            this(
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(32),
                    buffer.readUtf(32),
                    buffer.readUtf(32),
                    buffer.readDouble(),
                    buffer.readDouble()
            );
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(relicId, 128);
            buffer.writeUtf(displayName, 128);
            buffer.writeUtf(effect, 128);
            buffer.writeUtf(rarity, 32);
            buffer.writeUtf(individualRate, 32);
            buffer.writeUtf(rarityRate, 32);
            buffer.writeDouble(value);
            buffer.writeDouble(upgradeBonus);
        }
    }
}
