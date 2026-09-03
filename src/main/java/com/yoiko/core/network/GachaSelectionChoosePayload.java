package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GachaSelectionChoosePayload(UUID sessionId, int selectedIndex) implements CustomPacketPayload {
    public static final Type<GachaSelectionChoosePayload> TYPE =
            new Type<>(YoikoServerCore.id("gacha_selection_choose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GachaSelectionChoosePayload> STREAM_CODEC =
            CustomPacketPayload.codec(GachaSelectionChoosePayload::write, GachaSelectionChoosePayload::new);

    private GachaSelectionChoosePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeVarInt(selectedIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
