package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RelicAirStepPayload(int sequence) implements CustomPacketPayload {
    public static final Type<RelicAirStepPayload> TYPE =
            new Type<>(YoikoServerCore.id("relic_air_step"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelicAirStepPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RelicAirStepPayload::write, RelicAirStepPayload::new);

    private RelicAirStepPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(sequence);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
