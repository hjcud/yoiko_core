package com.yoiko.core.data;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.economy.EconomyDailyAggregate;
import com.yoiko.core.economy.MarketplaceTransactionRecord;
import com.yoiko.core.reward.YoikoResetClock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Materialized read model for economy administration.
 * Player files remain authoritative; this compact database is updated on writes
 * so reports never need to load every player's full data or mailbox.
 */
public final class ServerYoikoEconomySavedData extends SavedData {
    public static final String DATA_NAME = YoikoServerCore.MODID + "_economy";
    public static final int DATA_VERSION = 1;
    public static final int MAX_HISTORY_DAYS = 180;

    private final NavigableMap<String, EconomyDailyAggregate> daily = new TreeMap<>();
    private final Map<UUID, AccountSnapshot> accounts = new LinkedHashMap<>();
    private String trackingStartedPeriod = "";

    public static ServerYoikoEconomySavedData get(MinecraftServer server) {
        ServerYoikoEconomySavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ServerYoikoEconomySavedData::new, ServerYoikoEconomySavedData::load,
                        DataFixTypes.SAVED_DATA_COMMAND_STORAGE),
                DATA_NAME
        );
        if (data.trackingStartedPeriod.isBlank()) {
            data.trackingStartedPeriod = YoikoResetClock.dailyPeriodDate(System.currentTimeMillis()).toString();
            data.setDirty();
        }
        return data;
    }

    public List<EconomyDailyAggregate> dailyAggregates() {
        return List.copyOf(daily.values());
    }

    public List<AccountSnapshot> accountSnapshots() {
        return List.copyOf(accounts.values());
    }

    public String trackingStartedPeriod() {
        return trackingStartedPeriod;
    }

    public void recordOperational(OperationalAuditRecord record) {
        if (record == null || !"ECONOMY".equals(record.category())) {
            return;
        }
        economyDay(record.createdAt()).recordOperational(record);
        trimDays();
        setDirty();
    }

    public void recordMarket(MarketplaceTransactionRecord record) {
        if (record == null) {
            return;
        }
        economyDay(record.createdAt()).recordMarket(record);
        trimDays();
        setDirty();
    }

    /** Registers a new account once, including its current pending-mail totals. */
    public void ensureAccount(PlayerYoikoData player) {
        if (player != null && !accounts.containsKey(player.uuid)) {
            accounts.put(player.uuid, AccountSnapshot.from(player));
            setDirty();
        }
    }

    /** Cheap write-through update for balance and rested-gold mutations. */
    public void syncBalances(PlayerYoikoData player) {
        if (player == null) {
            return;
        }
        AccountSnapshot before = accounts.get(player.uuid);
        AccountSnapshot after = before == null
                ? AccountSnapshot.from(player)
                : before.withBalances(player.gold, player.gems, player.restedGold,
                        player.restedGoldAccrualEpochDay);
        if (!after.equals(before)) {
            accounts.put(player.uuid, after);
            setDirty();
        }
    }

    /** Full single-account update used only when that player's mailbox changes. */
    public void syncMailbox(PlayerYoikoData player) {
        if (player == null) {
            return;
        }
        AccountSnapshot after = AccountSnapshot.from(player);
        if (!after.equals(accounts.get(player.uuid))) {
            accounts.put(player.uuid, after);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putString("trackingStartedPeriod", trackingStartedPeriod);
        ListTag dailyTags = new ListTag();
        for (EconomyDailyAggregate aggregate : daily.values()) {
            dailyTags.add(aggregate.save());
        }
        tag.put("daily", dailyTags);
        ListTag accountTags = new ListTag();
        for (AccountSnapshot account : accounts.values()) {
            accountTags.add(account.save());
        }
        tag.put("accounts", accountTags);
        return tag;
    }

    private static ServerYoikoEconomySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerYoikoEconomySavedData data = new ServerYoikoEconomySavedData();
        if (tag.getInt("dataVersion") != DATA_VERSION) {
            YoikoServerCore.LOGGER.warn("Ignoring incompatible Yoiko economy data version {}; starting fresh.",
                    tag.getInt("dataVersion"));
            return data;
        }
        data.trackingStartedPeriod = tag.getString("trackingStartedPeriod");
        ListTag dailyTags = tag.getList("daily", Tag.TAG_COMPOUND);
        for (int index = 0; index < dailyTags.size(); index++) {
            EconomyDailyAggregate aggregate = EconomyDailyAggregate.load(dailyTags.getCompound(index));
            if (!aggregate.period().isBlank()) {
                data.daily.put(aggregate.period(), aggregate);
            }
        }
        ListTag accountTags = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int index = 0; index < accountTags.size(); index++) {
            AccountSnapshot account = AccountSnapshot.load(accountTags.getCompound(index));
            if (account.uuid() != null) {
                data.accounts.put(account.uuid(), account);
            }
        }
        data.trimDays();
        return data;
    }

    private EconomyDailyAggregate economyDay(long createdAt) {
        String period = YoikoResetClock.dailyPeriodDate(createdAt).toString();
        return daily.computeIfAbsent(period, EconomyDailyAggregate::new);
    }

    private void trimDays() {
        while (daily.size() > MAX_HISTORY_DAYS) {
            daily.pollFirstEntry();
        }
    }

    private static long pendingGold(PlayerYoikoData player) {
        long total = 0L;
        for (PlayerYoikoData.MailEntry mail : allMail(player)) {
            total = saturatedAdd(total, mail.attachedGold);
        }
        return total;
    }

    private static long pendingGems(PlayerYoikoData player) {
        long total = 0L;
        for (PlayerYoikoData.MailEntry mail : allMail(player)) {
            total = saturatedAdd(total, mail.attachedGems);
        }
        return total;
    }

    private static List<PlayerYoikoData.MailEntry> allMail(PlayerYoikoData player) {
        List<PlayerYoikoData.MailEntry> mail = new ArrayList<>(
                player.mailbox.size() + player.mailboxOverflow.size());
        mail.addAll(player.mailbox);
        mail.addAll(player.mailboxOverflow);
        return mail;
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public record AccountSnapshot(UUID uuid, long gold, long gems, long restedGold,
                                  long restedGoldAccrualEpochDay,
                                  long pendingMailGold, long pendingMailGems) {
        public AccountSnapshot {
            gold = Math.max(0L, gold);
            gems = Math.max(0L, gems);
            restedGold = Math.max(0L, restedGold);
            pendingMailGold = Math.max(0L, pendingMailGold);
            pendingMailGems = Math.max(0L, pendingMailGems);
        }

        static AccountSnapshot from(PlayerYoikoData player) {
            return new AccountSnapshot(player.uuid, player.gold, player.gems, player.restedGold,
                    player.restedGoldAccrualEpochDay, pendingGold(player), pendingGems(player));
        }

        AccountSnapshot withBalances(long newGold, long newGems, long newRestedGold, long newAccrualEpochDay) {
            return new AccountSnapshot(uuid, newGold, newGems, newRestedGold, newAccrualEpochDay,
                    pendingMailGold, pendingMailGems);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (uuid != null) tag.putUUID("uuid", uuid);
            tag.putLong("gold", gold);
            tag.putLong("gems", gems);
            tag.putLong("restedGold", restedGold);
            tag.putLong("restedGoldAccrualEpochDay", restedGoldAccrualEpochDay);
            tag.putLong("pendingMailGold", pendingMailGold);
            tag.putLong("pendingMailGems", pendingMailGems);
            return tag;
        }

        static AccountSnapshot load(CompoundTag tag) {
            return new AccountSnapshot(tag.hasUUID("uuid") ? tag.getUUID("uuid") : null,
                    tag.getLong("gold"), tag.getLong("gems"), tag.getLong("restedGold"),
                    tag.getLong("restedGoldAccrualEpochDay"),
                    tag.getLong("pendingMailGold"), tag.getLong("pendingMailGems"));
        }
    }
}
