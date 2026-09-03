package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GachaSelectionEndPayload(UUID sessionId) implements CustomPacketPayload {
    public static final Type<GachaSelectionEndPayload> TYPE =
            new Type<>(YoikoServerCore.id("gacha_selection_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GachaSelectionEndPayload> STREAM_CODEC =
            CustomPacketPayload.codec(GachaSelectionEndPayload::write, GachaSelectionEndPayload::new);

    private GachaSelectionEndPayload(RegistryFriendlyByteBuf buffer) {
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
