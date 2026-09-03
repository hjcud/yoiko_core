package com.yoiko.core.treasure;

import com.yoiko.core.YoikoServerCore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent per-player exploration rolls. No wall-clock spawn cooldown is stored. */
public final class TreasureRabbitSavedData extends SavedData {
    private static final String DATA_NAME = YoikoServerCore.MODID + "_treasure_rabbits";
    private static final int DATA_VERSION = 2;
    private static final int MAX_PLAYERS_ON_LOAD = 100_000;
    private final Map<UUID, ExplorationState> players = new LinkedHashMap<>();

    public static TreasureRabbitSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TreasureRabbitSavedData::new, TreasureRabbitSavedData::load,
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_NAME
        );
    }

    public ExplorationState state(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new ExplorationState());
    }

    public void markChanged() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, ExplorationState> entry : players.entrySet()) {
            CompoundTag player = new CompoundTag();
            player.putUUID("uuid", entry.getKey());
            player.putDouble("credit", Math.max(0.0D, Math.min(1.0D, entry.getValue().credit)));
            player.putInt("misses", Math.max(0, entry.getValue().misses));
            list.add(player);
        }
        tag.put("players", list);
        return tag;
    }

    private static TreasureRabbitSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TreasureRabbitSavedData data = new TreasureRabbitSavedData();
        int dataVersion = tag.getInt("dataVersion");
        if (dataVersion < 1 || dataVersion > DATA_VERSION) {
            return data;
        }
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(list.size(), MAX_PLAYERS_ON_LOAD); index++) {
            CompoundTag player = list.getCompound(index);
            if (!player.hasUUID("uuid")) {
                continue;
            }
            ExplorationState state = new ExplorationState();
            state.credit = Math.max(0.0D, Math.min(1.0D, player.getDouble("credit")));
            state.misses = Math.max(0, player.getInt("misses"));
            data.players.put(player.getUUID("uuid"), state);
        }
        return data;
    }

    public static final class ExplorationState {
        private double credit;
        private int misses;
        private transient boolean sampled;
        private transient double lastX;
        private transient double lastZ;
        private transient String lastDimension = "";

        double credit() {
            return credit;
        }

        void setCredit(double value) {
            credit = Math.max(0.0D, Math.min(1.0D, value));
        }

        int misses() {
            return misses;
        }

        void incrementMisses() {
            misses = Math.min(Integer.MAX_VALUE, misses + 1);
        }

        void resetRolls() {
            credit = 0.0D;
            misses = 0;
        }

        boolean sampled() {
            return sampled;
        }

        boolean sameDimension(String dimension) {
            return lastDimension.equals(dimension);
        }

        double distanceTo(double x, double z) {
            double dx = x - lastX;
            double dz = z - lastZ;
            return Math.sqrt(dx * dx + dz * dz);
        }

        void sample(double x, double z, String dimension) {
            sampled = true;
            lastX = x;
            lastZ = z;
            lastDimension = dimension;
        }
    }
}
