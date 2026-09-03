package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.cosmetic.CosmeticData;
import com.yoiko.core.cosmetic.CosmeticType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ParticleCosmeticCatalogPayload(int revision, List<Entry> entries)
        implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 4_096;
    public static final Type<ParticleCosmeticCatalogPayload> TYPE =
            new Type<>(YoikoServerCore.id("particle_cosmetic_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ParticleCosmeticCatalogPayload> STREAM_CODEC =
            CustomPacketPayload.codec(ParticleCosmeticCatalogPayload::write, ParticleCosmeticCatalogPayload::new);

    public ParticleCosmeticCatalogPayload {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many particle cosmetic catalog entries: " + entries.size());
        }
    }

    private ParticleCosmeticCatalogPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), readEntries(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(revision);
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            entry.write(buffer);
        }
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid particle cosmetic catalog size: " + size);
        }
        List<Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new Entry(buffer));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(String cosmeticId, String particleCategory, String rarity, String particle,
                        int intervalTicks, int count, double offsetY) {
        public Entry(CosmeticData cosmetic) {
            this(cosmetic.id(), cosmetic.particleCategory().name(), cosmetic.rarity().name(), cosmetic.particle(),
                    cosmetic.intervalTicks(), cosmetic.count(), cosmetic.offsetY());
            if (cosmetic.type() != CosmeticType.PARTICLE) {
                throw new IllegalArgumentException("Catalog entry is not a particle cosmetic: " + cosmetic.id());
            }
        }

        private Entry(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUtf(128), buffer.readUtf(32), buffer.readUtf(32), buffer.readUtf(128),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(cosmeticId, 128);
            buffer.writeUtf(particleCategory, 32);
            buffer.writeUtf(rarity, 32);
            buffer.writeUtf(particle, 128);
            buffer.writeVarInt(intervalTicks);
            buffer.writeVarInt(count);
            buffer.writeDouble(offsetY);
        }
    }
}
