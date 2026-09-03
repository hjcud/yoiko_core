package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MenuActionResultPayload(
        String scope,
        String action,
        boolean success,
        String messageKey
) implements CustomPacketPayload {
    public static final Type<MenuActionResultPayload> TYPE = new Type<>(YoikoServerCore.id("menu_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuActionResultPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MenuActionResultPayload::write, MenuActionResultPayload::new);

    private MenuActionResultPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(16), buffer.readUtf(64), buffer.readBoolean(), buffer.readUtf(128));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(scope, 16);
        buffer.writeUtf(action, 64);
        buffer.writeBoolean(success);
        buffer.writeUtf(messageKey == null ? "" : messageKey, 128);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
