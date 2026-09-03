package com.yoiko.core.event;

import com.yoiko.core.YoikoServerCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Persists the event selected for the current Yoiko daily period. */
public final class DailyServerEventSavedData extends SavedData {
    private static final String DATA_NAME = YoikoServerCore.MODID + "_daily_events";
    private static final int DATA_VERSION = 1;

    private String periodDate = "";
    private String eventId = "";
    private boolean forced;

    public static DailyServerEventSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DailyServerEventSavedData::new, DailyServerEventSavedData::load,
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_NAME
        );
    }

    public String periodDate() {
        return periodDate;
    }

    public String eventId() {
        return eventId;
    }

    public boolean forced() {
        return forced;
    }

    public void select(String periodDate, String eventId, boolean forced) {
        this.periodDate = periodDate == null ? "" : periodDate;
        this.eventId = eventId == null ? "" : eventId;
        this.forced = forced;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putString("periodDate", periodDate);
        tag.putString("eventId", eventId);
        tag.putBoolean("forced", forced);
        return tag;
    }

    private static DailyServerEventSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DailyServerEventSavedData data = new DailyServerEventSavedData();
        if (tag.getInt("dataVersion") != DATA_VERSION) {
            return data;
        }
        data.periodDate = tag.getString("periodDate");
        data.eventId = tag.getString("eventId");
        data.forced = tag.getBoolean("forced");
        return data;
    }
}
