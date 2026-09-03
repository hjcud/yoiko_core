package com.yoiko.core.data;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Global Yoiko data. Player state is stored separately by {@link PlayerYoikoSavedData}. */
public final class ServerYoikoSavedData extends SavedData {
    public static final String DATA_NAME = YoikoServerCore.MODID + "_global";
    private static final int DATA_VERSION = 1;
    private static final int MAX_GACHA_LOGS = 50;

    private final ListTag gachaLogs = new ListTag();
    private transient MinecraftServer server;

    public static ServerYoikoSavedData get(MinecraftServer server) {
        ServerYoikoSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ServerYoikoSavedData::new, ServerYoikoSavedData::load,
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_NAME
        );
        data.server = server;
        return data;
    }

    public PlayerYoikoData getOrCreate(ServerPlayer player) {
        PlayerYoikoSavedData saved = PlayerYoikoSavedData.get(
                player.server, player.getUUID(), player.getGameProfile().getName());
        PlayerYoikoData data = saved.data();
        if (data.firstLoginAt <= 0L) {
            data.firstLoginAt = System.currentTimeMillis();
            saved.setDirty();
        }
        String currentName = player.getGameProfile().getName();
        if (!currentName.equals(data.name)) {
            data.name = currentName;
            saved.setDirty();
        }
        ServerYoikoEconomySavedData.get(player.server).ensureAccount(data);
        return data;
    }

    public PlayerYoikoData get(UUID uuid) {
        ensureServer();
        return PlayerYoikoSavedData.get(server, uuid, "").data();
    }

    public void markDirty(ServerPlayer player) {
        PlayerYoikoSavedData saved = PlayerYoikoSavedData.get(
                player.server, player.getUUID(), player.getGameProfile().getName());
        saved.setDirty();
        ServerYoikoEconomySavedData.get(player.server).syncBalances(saved.data());
    }

    public void markDirty(UUID playerUuid) {
        ensureServer();
        PlayerYoikoSavedData saved = PlayerYoikoSavedData.get(server, playerUuid, "");
        saved.setDirty();
        ServerYoikoEconomySavedData.get(server).syncBalances(saved.data());
    }

    public void markMailboxDirty(ServerPlayer player) {
        PlayerYoikoSavedData saved = PlayerYoikoSavedData.get(
                player.server, player.getUUID(), player.getGameProfile().getName());
        saved.setDirty();
        ServerYoikoEconomySavedData.get(player.server).syncMailbox(saved.data());
    }

    public void markMailboxDirty(UUID playerUuid) {
        ensureServer();
        PlayerYoikoSavedData saved = PlayerYoikoSavedData.get(server, playerUuid, "");
        saved.setDirty();
        ServerYoikoEconomySavedData.get(server).syncMailbox(saved.data());
    }

    public void addGachaLog(ServerPlayer player, String species, String rarity, boolean shiny,
                            int level, boolean pityForced) {
        CompoundTag log = new CompoundTag();
        log.putLong("time", System.currentTimeMillis());
        log.putUUID("playerUuid", player.getUUID());
        log.putString("playerName", player.getGameProfile().getName());
        log.putString("species", species);
        log.putString("rarity", rarity);
        log.putBoolean("shiny", shiny);
        log.putInt("level", level);
        log.putBoolean("pityForced", pityForced);
        gachaLogs.add(0, log);
        while (gachaLogs.size() > MAX_GACHA_LOGS) {
            gachaLogs.remove(gachaLogs.size() - 1);
        }
        super.setDirty();
    }

    public ListTag gachaLogs() {
        return gachaLogs;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("gachaLogs", gachaLogs.copy());
        return tag;
    }

    private static ServerYoikoSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerYoikoSavedData data = new ServerYoikoSavedData();
        if (tag.getInt("dataVersion") != DATA_VERSION) {
            return data;
        }
        ListTag logs = tag.getList("gachaLogs", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(logs.size(), MAX_GACHA_LOGS); index++) {
            data.gachaLogs.add(logs.getCompound(index).copy());
        }
        return data;
    }

    private void ensureServer() {
        if (server == null) {
            throw new IllegalStateException("Yoiko saved data is not attached to a server");
        }
    }
}
