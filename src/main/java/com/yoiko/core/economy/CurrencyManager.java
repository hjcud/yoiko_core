package com.yoiko.core.economy;

import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import net.minecraft.server.level.ServerPlayer;

public final class CurrencyManager {
    public static final long MAX_BALANCE = 9_000_000_000_000_000L;

    private CurrencyManager() {
    }

    public static long balance(ServerPlayer player, CurrencyType type) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        return balance(data, type);
    }

    public static long balance(PlayerYoikoData data, CurrencyType type) {
        return type == CurrencyType.GEM ? data.gems : data.gold;
    }

    public static long add(ServerPlayer player, CurrencyType type, long amount) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        long added = add(data, type, amount);
        if (added > 0L) {
            savedData.markDirty(player);
            YoikoAdvancementManager.recordCurrencyBalances(player);
        }
        return added;
    }

    public static long add(PlayerYoikoData data, CurrencyType type, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long before = balance(data, type);
        long after = clamp(saturatedAdd(before, amount));
        set(data, type, after);
        return after - before;
    }

    public static boolean take(ServerPlayer player, CurrencyType type, long amount) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        if (!take(data, type, amount)) {
            return false;
        }
        savedData.markDirty(player);
        return true;
    }

    public static boolean take(PlayerYoikoData data, CurrencyType type, long amount) {
        if (amount < 0L || balance(data, type) < amount) {
            return false;
        }
        set(data, type, balance(data, type) - amount);
        return true;
    }

    public static void set(ServerPlayer player, CurrencyType type, long amount) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        set(savedData.getOrCreate(player), type, amount);
        savedData.markDirty(player);
    }

    public static void set(PlayerYoikoData data, CurrencyType type, long amount) {
        long safe = clamp(amount);
        if (type == CurrencyType.GEM) {
            data.gems = safe;
        } else {
            data.gold = safe;
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long clamp(long amount) {
        return Math.max(0L, Math.min(MAX_BALANCE, amount));
    }
}
