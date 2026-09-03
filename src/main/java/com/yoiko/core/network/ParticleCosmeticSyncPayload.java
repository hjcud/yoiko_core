package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ParticleCosmeticSyncPayload(UUID playerUuid, List<String> cosmeticIds, int catalogRevision)
        implements CustomPacketPayload {
    public static final Type<ParticleCosmeticSyncPayload> TYPE =
            new Type<>(YoikoServerCore.id("particle_cosmetic_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ParticleCosmeticSyncPayload> STREAM_CODEC =
            CustomPacketPayload.codec(ParticleCosmeticSyncPayload::write, ParticleCosmeticSyncPayload::new);

    private ParticleCosmeticSyncPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), readIds(buffer), buffer.readVarInt());
    }

    public ParticleCosmeticSyncPayload {
        cosmeticIds = cosmeticIds == null ? List.of() : List.copyOf(cosmeticIds);
        if (cosmeticIds.size() > 3) {
            throw new IllegalArgumentException("At most three particle cosmetics may be synchronized");
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(playerUuid);
        buffer.writeVarInt(cosmeticIds.size());
        for (String cosmeticId : cosmeticIds) {
            buffer.writeUtf(cosmeticId, 128);
        }
        buffer.writeVarInt(catalogRevision);
    }

    private static List<String> readIds(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 3) {
            throw new IllegalArgumentException("Invalid synchronized particle cosmetic count: " + size);
        }
        List<String> ids = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            ids.add(buffer.readUtf(128));
        }
        return ids;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
