package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenRelicPayload(
        int ticketCount,
        int upgradeCrystalCount,
        int maxOwned,
        int scrapCount,
        int protectionScrapCost,
        int specialEquippedCount,
        int specialMaxEquipped,
        String chromaticContractTargetUuid,
        int activePreset,
        List<Integer> presetFilledCounts,
        String selectedUuid,
        String message,
        List<String> equippedSlots,
        List<Entry> relics
) implements CustomPacketPayload {
    public static final Type<OpenRelicPayload> TYPE = new Type<>(YoikoServerCore.id("open_relic"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRelicPayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenRelicPayload::write, OpenRelicPayload::new);

    private OpenRelicPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(64),
                buffer.readVarInt(),
                readInts(buffer),
                buffer.readUtf(64),
                buffer.readUtf(256),
                readStrings(buffer),
                readEntries(buffer)
        );
    }

    public OpenRelicPayload {
        presetFilledCounts = List.copyOf(presetFilledCounts);
        equippedSlots = List.copyOf(equippedSlots);
        relics = List.copyOf(relics);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(ticketCount);
        buffer.writeVarInt(upgradeCrystalCount);
        buffer.writeVarInt(maxOwned);
        buffer.writeVarInt(scrapCount);
        buffer.writeVarInt(protectionScrapCost);
        buffer.writeVarInt(specialEquippedCount);
        buffer.writeVarInt(specialMaxEquipped);
        buffer.writeUtf(chromaticContractTargetUuid, 64);
        buffer.writeVarInt(activePreset);
        buffer.writeVarInt(presetFilledCounts.size());
        for (int count : presetFilledCounts) {
            buffer.writeVarInt(count);
        }
        buffer.writeUtf(selectedUuid, 64);
        buffer.writeUtf(message, 256);
        buffer.writeVarInt(equippedSlots.size());
        for (String uuid : equippedSlots) {
            buffer.writeUtf(uuid, 64);
        }
        buffer.writeVarInt(relics.size());
        for (Entry entry : relics) {
            entry.write(buffer);
        }
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buffer.readUtf(64));
        }
        return values;
    }

    private static List<Integer> readInts(RegistryFriendlyByteBuf buffer) {
        int encoded = Math.max(0, buffer.readVarInt());
        List<Integer> values = new ArrayList<>(Math.min(16, encoded));
        for (int i = 0; i < encoded; i++) {
            int value = buffer.readVarInt();
            if (values.size() < 16) values.add(value);
        }
        return values;
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            String uuid,
            String relicId,
            String displayName,
            String effect,
            String secondaryEffect,
            String rarity,
            int level,
            int effectiveLevel,
            double value,
            double nextValue,
            double secondaryValue,
            double secondaryNextValue,
            double greatSuccess,
            double success,
            double downgrade,
            double destroy,
            int equippedSlot,
            int scrapValue,
            boolean locked,
            boolean upgradeable,
            boolean primaryUpgradeable,
            boolean secondaryUpgradeable,
            int storageSlot,
            boolean primaryEffectSuppressed,
            boolean secondaryEffectSuppressed
    ) {
        private Entry(RegistryFriendlyByteBuf buffer) {
            this(
                    buffer.readUtf(64),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(32),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            );
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(uuid, 64);
            buffer.writeUtf(relicId, 128);
            buffer.writeUtf(displayName, 128);
            buffer.writeUtf(effect, 128);
            buffer.writeUtf(secondaryEffect, 128);
            buffer.writeUtf(rarity, 32);
            buffer.writeVarInt(level);
            buffer.writeVarInt(effectiveLevel);
            buffer.writeDouble(value);
            buffer.writeDouble(nextValue);
            buffer.writeDouble(secondaryValue);
            buffer.writeDouble(secondaryNextValue);
            buffer.writeDouble(greatSuccess);
            buffer.writeDouble(success);
            buffer.writeDouble(downgrade);
            buffer.writeDouble(destroy);
            buffer.writeVarInt(equippedSlot);
            buffer.writeVarInt(scrapValue);
            buffer.writeBoolean(locked);
            buffer.writeBoolean(upgradeable);
            buffer.writeBoolean(primaryUpgradeable);
            buffer.writeBoolean(secondaryUpgradeable);
            buffer.writeVarInt(storageSlot);
            buffer.writeBoolean(primaryEffectSuppressed);
            buffer.writeBoolean(secondaryEffectSuppressed);
        }
    }
}
