package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

public record MenuActionPayload(String action, UUID sessionId, long nonce) implements CustomPacketPayload {
    private static final int MAX_ACTION_LENGTH = 256;
    public static final Type<MenuActionPayload> TYPE = new Type<>(YoikoServerCore.id("menu_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuActionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MenuActionPayload::write, MenuActionPayload::new);

    private MenuActionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(MAX_ACTION_LENGTH), buffer.readUUID(), buffer.readVarLong());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(action, MAX_ACTION_LENGTH);
        buffer.writeUUID(sessionId == null ? new UUID(0L, 0L) : sessionId);
        buffer.writeVarLong(Math.max(0L, nonce));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
