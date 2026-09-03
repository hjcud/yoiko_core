package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** One-time, server-validated choice for a unified rare strategy egg. */
public record TurtleStrategyTicketChoosePayload(UUID sessionId, String strategy) implements CustomPacketPayload {
    public static final Type<TurtleStrategyTicketChoosePayload> TYPE =
            new Type<>(YoikoServerCore.id("turtle_strategy_ticket_choose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TurtleStrategyTicketChoosePayload> STREAM_CODEC =
            CustomPacketPayload.codec(TurtleStrategyTicketChoosePayload::write, TurtleStrategyTicketChoosePayload::new);

    public TurtleStrategyTicketChoosePayload {
        strategy = strategy == null ? "" : strategy.toUpperCase(Locale.ROOT);
        if (strategy.length() > 16) throw new IllegalArgumentException("Strategy id is too long");
    }

    private TurtleStrategyTicketChoosePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(16));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeUtf(strategy, 16);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
