package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Opens the four-strategy selector for the unified rare turtle egg. */
public record OpenTurtleStrategyTicketPayload(UUID sessionId) implements CustomPacketPayload {
    public static final Type<OpenTurtleStrategyTicketPayload> TYPE =
            new Type<>(YoikoServerCore.id("open_turtle_strategy_ticket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTurtleStrategyTicketPayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenTurtleStrategyTicketPayload::write, OpenTurtleStrategyTicketPayload::new);

    private OpenTurtleStrategyTicketPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
