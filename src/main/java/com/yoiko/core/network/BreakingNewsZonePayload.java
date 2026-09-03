package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A transient map overlay. No incident gameplay state is trusted from the client. */
public record BreakingNewsZonePayload(boolean visible, long runId, String eventType,
                                      String dimensionId, int centerX, int centerZ,
                                      int radius, String targetSpecies)
        implements CustomPacketPayload {
    public static final Type<BreakingNewsZonePayload> TYPE =
            new Type<>(YoikoServerCore.id("breaking_news_zone"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreakingNewsZonePayload> STREAM_CODEC =
            CustomPacketPayload.codec(BreakingNewsZonePayload::write, BreakingNewsZonePayload::new);

    private BreakingNewsZonePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readVarLong(), buffer.readUtf(64), buffer.readUtf(128),
                buffer.readInt(), buffer.readInt(), buffer.readVarInt(), buffer.readUtf(128));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(visible);
        buffer.writeVarLong(Math.max(0L, runId));
        buffer.writeUtf(safe(eventType), 64);
        buffer.writeUtf(safe(dimensionId), 128);
        buffer.writeInt(centerX);
        buffer.writeInt(centerZ);
        buffer.writeVarInt(Math.max(0, radius));
        buffer.writeUtf(safe(targetSpecies), 128);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
