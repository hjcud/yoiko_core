package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MenuSessionPayload(UUID sessionId, String scope) implements CustomPacketPayload {
    public static final Type<MenuSessionPayload> TYPE = new Type<>(YoikoServerCore.id("menu_session"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuSessionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MenuSessionPayload::write, MenuSessionPayload::new);

    private MenuSessionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(32));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeUtf(scope, 32);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
