package com.yoiko.core.data;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** One SavedData root per player so unrelated players are not serialized together. */
public final class PlayerYoikoSavedData extends SavedData {
    public static final String DATA_PREFIX = YoikoServerCore.MODID + "_player_";
    private static final int DATA_VERSION = 4;

    private final UUID expectedUuid;
    private final PlayerYoikoData playerData;

    private PlayerYoikoSavedData(UUID uuid, String name) {
        this.expectedUuid = uuid;
        this.playerData = new PlayerYoikoData(uuid, name == null ? "" : name);
    }

    private PlayerYoikoSavedData(UUID expectedUuid, PlayerYoikoData playerData) {
        this.expectedUuid = expectedUuid;
        this.playerData = playerData;
    }

    public static PlayerYoikoSavedData get(MinecraftServer server, UUID uuid, String name) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        () -> new PlayerYoikoSavedData(uuid, name),
                        (tag, registries) -> load(uuid, tag, registries),
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
                ),
                DATA_PREFIX + uuid
        );
    }

    public PlayerYoikoData data() {
        return playerData;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("player", playerData.save(registries));
        return tag;
    }

    private static PlayerYoikoSavedData load(UUID expectedUuid, CompoundTag tag,
                                             HolderLookup.Provider registries) {
        int version = Math.max(0, tag.getInt("dataVersion"));
        if (version != DATA_VERSION || !tag.contains("player", Tag.TAG_COMPOUND)) {
            YoikoServerCore.LOGGER.warn(
                    "Ignoring incompatible Yoiko player data for {} (version={}); starting fresh.",
                    expectedUuid, version
            );
            return new PlayerYoikoSavedData(expectedUuid, "");
        }
        PlayerYoikoData loaded = PlayerYoikoData.load(tag.getCompound("player"), registries);
        if (!expectedUuid.equals(loaded.uuid)) {
            YoikoServerCore.LOGGER.error(
                    "Ignoring Yoiko player data with mismatched UUID: file={}, payload={}.",
                    expectedUuid, loaded.uuid
            );
            return new PlayerYoikoSavedData(expectedUuid, "");
        }
        return new PlayerYoikoSavedData(expectedUuid, loaded);
    }
}
