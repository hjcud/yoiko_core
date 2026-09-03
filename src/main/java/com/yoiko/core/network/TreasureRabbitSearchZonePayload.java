package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A player-private, approximate search area for one naturally spawned Treasure Rabbit. */
public record TreasureRabbitSearchZonePayload(boolean visible, UUID rabbitId, String variantId,
                                              String dimensionId, int centerX, int centerZ, int radius)
        implements CustomPacketPayload {
    public static final Type<TreasureRabbitSearchZonePayload> TYPE =
            new Type<>(YoikoServerCore.id("treasure_rabbit_search_zone"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TreasureRabbitSearchZonePayload> STREAM_CODEC =
            CustomPacketPayload.codec(TreasureRabbitSearchZonePayload::write,
                    TreasureRabbitSearchZonePayload::new);

    private TreasureRabbitSearchZonePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(128),
                buffer.readInt(), buffer.readInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(visible);
        buffer.writeUUID(rabbitId == null ? new UUID(0L, 0L) : rabbitId);
        buffer.writeUtf(safe(variantId), 32);
        buffer.writeUtf(safe(dimensionId), 128);
        buffer.writeInt(centerX);
        buffer.writeInt(centerZ);
        buffer.writeVarInt(Math.max(0, radius));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
