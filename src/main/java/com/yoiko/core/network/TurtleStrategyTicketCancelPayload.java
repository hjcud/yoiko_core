package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Releases a strategy-selection session when its screen is closed without confirmation. */
public record TurtleStrategyTicketCancelPayload(UUID sessionId) implements CustomPacketPayload {
    public static final Type<TurtleStrategyTicketCancelPayload> TYPE =
            new Type<>(YoikoServerCore.id("turtle_strategy_ticket_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TurtleStrategyTicketCancelPayload> STREAM_CODEC =
            CustomPacketPayload.codec(TurtleStrategyTicketCancelPayload::write, TurtleStrategyTicketCancelPayload::new);

    private TurtleStrategyTicketCancelPayload(RegistryFriendlyByteBuf buffer) {
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
