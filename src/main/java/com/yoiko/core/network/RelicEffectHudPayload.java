package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-authoritative feedback for relic effects that actually triggered during play. */
public record RelicEffectHudPayload(
        String effectKey,
        DisplayKind displayKind,
        ValueKind valueKind,
        double value,
        int stateTicks,
        int displayTicks,
        int progress,
        int progressMaximum
) implements CustomPacketPayload {
    public static final Type<RelicEffectHudPayload> TYPE =
            new Type<>(YoikoServerCore.id("relic_effect_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelicEffectHudPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RelicEffectHudPayload::write, RelicEffectHudPayload::new);

    private RelicEffectHudPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUtf(96),
                displayKind(buffer.readVarInt()),
                valueKind(buffer.readVarInt()),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(effectKey, 96);
        buffer.writeVarInt(displayKind.ordinal());
        buffer.writeVarInt(valueKind.ordinal());
        buffer.writeDouble(value);
        buffer.writeVarInt(stateTicks);
        buffer.writeVarInt(displayTicks);
        buffer.writeVarInt(progress);
        buffer.writeVarInt(progressMaximum);
    }

    private static DisplayKind displayKind(int ordinal) {
        DisplayKind[] values = DisplayKind.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DisplayKind.PROC;
    }

    private static ValueKind valueKind(int ordinal) {
        ValueKind[] values = ValueKind.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ValueKind.NONE;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum DisplayKind {
        PROC,
        ACTIVE,
        CHARGED,
        PROGRESS,
        READY,
        COOLDOWN,
        MARKED
    }

    public enum ValueKind {
        NONE,
        DAMAGE_BONUS,
        DAMAGE_REDUCTION,
        MOVEMENT_SPEED,
        ATTACK_SPEED,
        DURATION_REDUCTION,
        KNOCKBACK,
        SLOWNESS,
        ABSORPTION,
        HEAL,
        PP_RESTORE
    }
}
