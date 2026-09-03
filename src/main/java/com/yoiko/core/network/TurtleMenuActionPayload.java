package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TurtleMenuActionPayload(
        String action,
        UUID turtleId,
        String value,
        long expectedRevision,
        UUID sessionId,
        long nonce
) implements CustomPacketPayload {
    public static final Type<TurtleMenuActionPayload> TYPE =
            new Type<>(YoikoServerCore.id("turtle_menu_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TurtleMenuActionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(TurtleMenuActionPayload::write, TurtleMenuActionPayload::new);

    private TurtleMenuActionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(32), buffer.readUUID(), buffer.readUtf(64), buffer.readLong(),
                buffer.readUUID(), buffer.readVarLong());
    }

    public TurtleMenuActionPayload {
        turtleId = turtleId == null ? new UUID(0L, 0L) : turtleId;
        value = value == null ? "" : value;
        sessionId = sessionId == null ? new UUID(0L, 0L) : sessionId;
        nonce = Math.max(0L, nonce);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(action, 32);
        buffer.writeUUID(turtleId);
        buffer.writeUtf(value, 64);
        buffer.writeLong(expectedRevision);
        buffer.writeUUID(sessionId);
        buffer.writeVarLong(nonce);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
