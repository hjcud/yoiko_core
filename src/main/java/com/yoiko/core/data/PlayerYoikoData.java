package com.yoiko.core.data;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.cosmetic.CosmeticEquipSlot;
import com.yoiko.core.economy.MarketplaceSaleRecord;
import com.yoiko.core.relic.RelicRarity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public class PlayerYoikoData {
    public static final int YOIKO_STORAGE_PAGE_SIZE = 54;
    public static final int MIN_YOIKO_STORAGE_SLOTS = 27;
    public static final int MAX_YOIKO_STORAGE_PAGES = 100;
    public static final int MAX_YOIKO_STORAGE_SLOTS = YOIKO_STORAGE_PAGE_SIZE * MAX_YOIKO_STORAGE_PAGES;
    public static final int MAX_MAILBOX_MAILS = 25;
    public static final int MAX_MAILBOX_OVERFLOW = 1_000;
    public static final int MAX_MARKETPLACE_SALES = 50;
    public static final int RELIC_EQUIP_SLOT_COUNT = 5;
    public static final int RELIC_PRESET_COUNT = 3;
    /** Three 9x5 pages in the relic storage screen. */
    public static final int MAX_OWNED_RELICS = 135;
    public static final int RELIC_PROTECTION_SCRAP_COST = 50;
    private static final String RELIC_UPGRADE_CRYSTALS_TAG = "relicUpgradeCrystals";
    public static final String MENU_TAB_STORAGE = "storage";
    public static final String MENU_TAB_MAILBOX = "mailbox";
    public static final String MENU_TAB_RELIC = "relic";
    public static final String MENU_TAB_COSMETIC = "cosmetic";
    public static final String MENU_TAB_MARKET = "market";
    public static final String MENU_TAB_TURTLE = "turtle";

    public UUID uuid;
    public String name = "";
    public Set<String> ownedRanks = new LinkedHashSet<>();
    public String activeRank = "";
    public Set<String> ownedCosmetics = new LinkedHashSet<>();
    public Set<String> favoriteCosmetics = new LinkedHashSet<>();
    public EnumMap<CosmeticEquipSlot, String> equippedCosmetics = new EnumMap<>(CosmeticEquipSlot.class);
    public boolean claimedFirstLoginReward;
    public long firstLoginAt;
    public long firstRewardClaimedAt;
    public long lastDailyRewardClaimAt;
    public int dailyStreak;
    public String dailyBonusKey = "";
    public String dailyBonusKeyGrantKey = "";
    public int dailyBonusKeys;
    public int dailyBonusOpenedCount;
    public int dailyBonusOpenedMask;
    public int dailyBonusLastOpened = -1;
    public NonNullList<ItemStack> dailyBonusBoxes = NonNullList.withSize(3, ItemStack.EMPTY);
    /** Logical reward amounts; box ItemStacks are display-only and always kept serializable. */
    public List<Integer> dailyBonusRewardCounts = new ArrayList<>();
    public List<String> dailyBonusRewardKeys = new ArrayList<>();
    public List<String> dailyBonusRewardRarities = new ArrayList<>();
    public List<RelicInstance> ownedRelics = new ArrayList<>();
    public List<String> equippedRelics = new ArrayList<>();
    /** Three persistent equipment sets. The active set always mirrors {@link #equippedRelics}. */
    public List<List<String>> relicPresets = new ArrayList<>();
    /** Per-preset target for the Chromatic Contract special relic. */
    public List<String> relicPresetContractTargets = new ArrayList<>();
    public int activeRelicPreset;
    public String radiantContractTargetUuid = "";
    public int relicScrap;
    public int relicUpgradeCrystals;
    public String relicMiningPeriodKey = "";
    public int relicMiningTicketsToday;
    public int relicMiningPityMisses;
    public long relicMiningEasyCreditEpochDay;
    public int relicMiningEasyCredits;
    public long restedGoldAccrualEpochDay;
    public long restedGold;
    public long lastParticleGachaTicketPurchaseAt;
    public long gold;
    public long gems;
    public GachaStats gachaStats = new GachaStats();
    public NonNullList<ItemStack> yoikoStorage = NonNullList.withSize(MIN_YOIKO_STORAGE_SLOTS, ItemStack.EMPTY);
    public Set<Integer> lockedYoikoStorageSlots = new LinkedHashSet<>();
    public int yoikoStoragePage;
    public String lastMenuTab = MENU_TAB_STORAGE;
    public String cosmeticCategory = "HEAD";
    public int cosmeticPage;
    public boolean cosmeticFavoritesOnly;
    public String marketSection = "server_shop";
    public int marketPage;
    public String marketSort = "latest";
    public String marketCategory = "all";
    public String marketSearch = "";
    public boolean marketRememberPrice = true;
    public int unseenMarketSales;
    public boolean menuHudIntroShown;
    /** Off by default: other players can only inspect this profile after explicit opt-in. */
    public boolean publicProfileEnabled;
    public String serverBuyGoldDate = "";
    public long serverBuyGoldToday;
    public List<MarketplaceSaleRecord> marketplaceSales = new ArrayList<>();
    public List<MailEntry> mailbox = new ArrayList<>();
    public List<MailEntry> mailboxOverflow = new ArrayList<>();
    /** Generic persistent counters used by the Yoiko advancement tab. */
    public Map<String, Integer> achievementCounters = new LinkedHashMap<>();

    public PlayerYoikoData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        for (CosmeticEquipSlot slot : CosmeticEquipSlot.values()) {
            equippedCosmetics.put(slot, "");
        }
        ensureEquippedRelicSlots();
        ensureRelicPresets();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", uuid);
        tag.putString("name", name);
        tag.put("ownedRanks", stringList(ownedRanks));
        tag.putString("activeRank", activeRank);
        tag.put("ownedCosmetics", stringList(ownedCosmetics));
        tag.put("favoriteCosmetics", stringList(favoriteCosmetics));

        CompoundTag equipped = new CompoundTag();
        for (CosmeticEquipSlot slot : CosmeticEquipSlot.values()) {
            equipped.putString(slot.name(), equippedCosmetics.getOrDefault(slot, ""));
        }
        tag.put("equippedCosmetics", equipped);

        tag.putBoolean("claimedFirstLoginReward", claimedFirstLoginReward);
        tag.putLong("firstLoginAt", firstLoginAt);
        tag.putLong("firstRewardClaimedAt", firstRewardClaimedAt);
        tag.putLong("lastDailyRewardClaimAt", lastDailyRewardClaimAt);
        tag.putInt("dailyStreak", dailyStreak);
        tag.putString("dailyBonusKey", dailyBonusKey);
        tag.putString("dailyBonusKeyGrantKey", dailyBonusKeyGrantKey);
        tag.putInt("dailyBonusKeys", dailyBonusKeys);
        tag.putInt("dailyBonusOpenedCount", dailyBonusOpenedCount);
        tag.putInt("dailyBonusOpenedMask", dailyBonusOpenedMask);
        tag.putInt("dailyBonusLastOpened", dailyBonusLastOpened);
        tag.put("dailyBonusBoxes", saveSizedItems(registries, dailyBonusBoxes));
        int[] dailyBonusCounts = new int[dailyBonusBoxes.size()];
        for (int i = 0; i < dailyBonusCounts.length; i++) {
            ItemStack preview = dailyBonusBoxes.get(i);
            int fallback = preview.isEmpty() ? 0 : Math.max(1, preview.getCount());
            dailyBonusCounts[i] = i < dailyBonusRewardCounts.size()
                    ? Math.max(0, dailyBonusRewardCounts.get(i))
                    : fallback;
        }
        tag.putIntArray("dailyBonusRewardCounts", dailyBonusCounts);
        tag.put("dailyBonusRewardKeys", stringList(dailyBonusRewardKeys));
        tag.put("dailyBonusRewardRarities", stringList(dailyBonusRewardRarities));

        ListTag relics = new ListTag();
        for (RelicInstance relic : ownedRelics) {
            relics.add(relic.save());
        }
        tag.put("ownedRelics", relics);
        captureActiveRelicPreset();
        tag.put("equippedRelics", equippedRelicList());
        tag.put("relicPresets", relicPresetList());
        tag.putInt("activeRelicPreset", activeRelicPreset);
        tag.putString("radiantContractTargetUuid", radiantContractTargetUuid);
        tag.putInt("relicScrap", relicScrap);
        tag.putInt(RELIC_UPGRADE_CRYSTALS_TAG, relicUpgradeCrystals);
        tag.putString("relicMiningPeriodKey", relicMiningPeriodKey);
        tag.putInt("relicMiningTicketsToday", Math.max(0, relicMiningTicketsToday));
        tag.putInt("relicMiningPityMisses", Math.max(0, relicMiningPityMisses));
        tag.putLong("relicMiningEasyCreditEpochDay", relicMiningEasyCreditEpochDay);
        tag.putInt("relicMiningEasyCredits", Math.max(0, relicMiningEasyCredits));
        tag.putLong("restedGoldAccrualEpochDay", restedGoldAccrualEpochDay);
        tag.putLong("restedGold", Math.max(0L, restedGold));
        tag.putLong("lastParticleGachaTicketPurchaseAt", Math.max(0L, lastParticleGachaTicketPurchaseAt));
        tag.putLong("gold", Math.max(0L, gold));
        tag.putLong("gems", Math.max(0L, gems));
        tag.put("gachaStats", gachaStats.save());

        tag.put("yoikoStorage", saveYoikoStorage(registries));
        tag.putIntArray("lockedYoikoStorageSlots", lockedYoikoStorageSlots.stream().mapToInt(Integer::intValue).toArray());
        tag.putInt("yoikoStoragePage", yoikoStoragePage);
        tag.putString("lastMenuTab", sanitizeMenuTab(lastMenuTab));
        tag.putString("cosmeticCategory", sanitizeCosmeticCategory(cosmeticCategory));
        tag.putInt("cosmeticPage", Math.max(0, cosmeticPage));
        tag.putBoolean("cosmeticFavoritesOnly", cosmeticFavoritesOnly);
        tag.putString("marketSection", sanitizeMarketSection(marketSection));
        tag.putInt("marketPage", Math.max(0, marketPage));
        tag.putString("marketSort", sanitizeMarketSort(marketSort));
        tag.putString("marketCategory", sanitizeMarketCategory(marketCategory));
        tag.putString("marketSearch", sanitizeSearch(marketSearch));
        tag.putBoolean("marketRememberPrice", marketRememberPrice);
        tag.putInt("unseenMarketSales", Math.max(0, unseenMarketSales));
        tag.putBoolean("menuHudIntroShown", menuHudIntroShown);
        tag.putBoolean("publicProfileEnabled", publicProfileEnabled);
        tag.putString("serverBuyGoldDate", serverBuyGoldDate);
        tag.putLong("serverBuyGoldToday", Math.max(0L, serverBuyGoldToday));
        ListTag sales = new ListTag();
        for (MarketplaceSaleRecord sale : marketplaceSales) {
            if (!sale.item().isEmpty()) {
                sales.add(sale.save(registries));
            }
        }
        tag.put("marketplaceSales", sales);

        ListTag mails = new ListTag();
        for (MailEntry mail : mailbox) {
            mails.add(mail.save(registries));
        }
        tag.put("mailbox", mails);
        ListTag overflowMails = new ListTag();
        for (MailEntry mail : mailboxOverflow) {
            overflowMails.add(mail.save(registries));
        }
        tag.put("mailboxOverflow", overflowMails);
        CompoundTag counters = new CompoundTag();
        achievementCounters.forEach((key, value) -> {
            if (key != null && key.matches("[a-z0-9_.-]{1,64}") && value != null && value > 0) {
                counters.putInt(key, value);
            }
        });
        tag.put("achievementCounters", counters);
        return tag;
    }

    public static PlayerYoikoData load(CompoundTag tag, HolderLookup.Provider registries) {
        UUID uuid = tag.hasUUID("uuid") ? tag.getUUID("uuid") : UUID.randomUUID();
        PlayerYoikoData data = new PlayerYoikoData(uuid, tag.getString("name"));
        data.ownedRanks = readStringSet(tag.getList("ownedRanks", Tag.TAG_STRING));
        data.activeRank = tag.getString("activeRank");
        data.ownedCosmetics = readStringSet(tag.getList("ownedCosmetics", Tag.TAG_STRING));
        data.favoriteCosmetics = readStringSet(tag.getList("favoriteCosmetics", Tag.TAG_STRING));

        CompoundTag equipped = tag.getCompound("equippedCosmetics");
        for (CosmeticEquipSlot slot : CosmeticEquipSlot.values()) {
            data.equippedCosmetics.put(slot, equipped.getString(slot.name()));
        }

        data.claimedFirstLoginReward = tag.getBoolean("claimedFirstLoginReward");
        data.firstLoginAt = tag.getLong("firstLoginAt");
        data.firstRewardClaimedAt = tag.getLong("firstRewardClaimedAt");
        data.lastDailyRewardClaimAt = tag.getLong("lastDailyRewardClaimAt");
        data.dailyStreak = tag.getInt("dailyStreak");
        data.dailyBonusKey = tag.getString("dailyBonusKey");
        data.dailyBonusKeyGrantKey = tag.getString("dailyBonusKeyGrantKey");
        data.dailyBonusKeys = Math.max(0, tag.getInt("dailyBonusKeys"));
        data.dailyBonusOpenedCount = Math.max(0, tag.getInt("dailyBonusOpenedCount"));
        data.dailyBonusOpenedMask = Math.max(0, tag.getInt("dailyBonusOpenedMask"));
        data.dailyBonusLastOpened = tag.contains("dailyBonusLastOpened", Tag.TAG_INT) ? tag.getInt("dailyBonusLastOpened") : -1;
        if (tag.contains("dailyBonusBoxes", Tag.TAG_COMPOUND)) {
            data.dailyBonusBoxes = loadSizedItems(tag.getCompound("dailyBonusBoxes"), registries, 3);
        }
        for (int count : tag.getIntArray("dailyBonusRewardCounts")) {
            data.dailyBonusRewardCounts.add(Math.max(0, count));
        }
        data.dailyBonusRewardKeys = readStringList(tag.getList("dailyBonusRewardKeys", Tag.TAG_STRING));
        data.dailyBonusRewardRarities = readStringList(tag.getList("dailyBonusRewardRarities", Tag.TAG_STRING));

        ListTag relics = tag.getList("ownedRelics", Tag.TAG_COMPOUND);
        int relicCount = Math.min(relics.size(), MAX_OWNED_RELICS);
        for (int i = 0; i < relicCount; i++) {
            CompoundTag relicTag = relics.getCompound(i);
            try {
                data.ownedRelics.add(RelicInstance.load(relicTag));
            } catch (IllegalArgumentException exception) {
                YoikoServerCore.LOGGER.error(
                        "Player {} relic record {} has invalid rarity '{}'; the record was skipped.",
                        data.uuid, i, relicTag.getString("rarity"));
            }
        }
        if (relics.size() > relicCount) {
            YoikoServerCore.LOGGER.error("Player {} had {} relic records; only {} were loaded",
                    data.uuid, relics.size(), relicCount);
        }
        data.equippedRelics = readStringList(tag.getList("equippedRelics", Tag.TAG_STRING));
        data.ensureEquippedRelicSlots();
        data.radiantContractTargetUuid = tag.getString("radiantContractTargetUuid");
        data.relicPresets.clear();
        data.relicPresetContractTargets.clear();
        if (tag.contains("relicPresets", Tag.TAG_LIST)) {
            ListTag presets = tag.getList("relicPresets", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(RELIC_PRESET_COUNT, presets.size()); i++) {
                CompoundTag preset = presets.getCompound(i);
                data.relicPresets.add(readStringList(preset.getList("slots", Tag.TAG_STRING)));
                data.relicPresetContractTargets.add(preset.getString("contractTarget"));
            }
        }
        data.activeRelicPreset = Math.clamp(tag.getInt("activeRelicPreset"), 0, RELIC_PRESET_COUNT - 1);
        data.ensureRelicPresets();
        data.pruneEquippedRelics();
        data.relicScrap = Math.max(0, tag.getInt("relicScrap"));
        data.relicUpgradeCrystals = Math.max(0, tag.getInt(RELIC_UPGRADE_CRYSTALS_TAG));
        data.relicMiningPeriodKey = tag.getString("relicMiningPeriodKey");
        data.relicMiningTicketsToday = Math.max(0, tag.getInt("relicMiningTicketsToday"));
        data.relicMiningPityMisses = Math.max(0, tag.getInt("relicMiningPityMisses"));
        data.relicMiningEasyCreditEpochDay = tag.getLong("relicMiningEasyCreditEpochDay");
        data.relicMiningEasyCredits = Math.max(0, tag.getInt("relicMiningEasyCredits"));
        data.restedGoldAccrualEpochDay = tag.getLong("restedGoldAccrualEpochDay");
        data.restedGold = Math.max(0L, tag.getLong("restedGold"));
        data.lastParticleGachaTicketPurchaseAt = Math.max(0L, tag.getLong("lastParticleGachaTicketPurchaseAt"));
        data.gold = Math.max(0L, tag.getLong("gold"));
        data.gems = Math.max(0L, tag.getLong("gems"));
        data.gachaStats = GachaStats.load(tag.getCompound("gachaStats"));
        if (tag.contains("yoikoStorage", Tag.TAG_COMPOUND)) {
            data.loadYoikoStorage(tag.getCompound("yoikoStorage"), registries);
        }
        for (int slot : tag.getIntArray("lockedYoikoStorageSlots")) {
            if (slot >= 0 && slot < MAX_YOIKO_STORAGE_SLOTS) {
                data.lockedYoikoStorageSlots.add(slot);
            }
        }
        data.yoikoStoragePage = Math.max(0, tag.getInt("yoikoStoragePage"));
        data.lastMenuTab = sanitizeMenuTab(tag.getString("lastMenuTab"));
        data.cosmeticCategory = sanitizeCosmeticCategory(tag.getString("cosmeticCategory"));
        data.cosmeticPage = Math.max(0, tag.getInt("cosmeticPage"));
        data.cosmeticFavoritesOnly = tag.getBoolean("cosmeticFavoritesOnly");
        data.marketSection = sanitizeMarketSection(tag.getString("marketSection"));
        data.marketPage = Math.max(0, tag.getInt("marketPage"));
        data.marketSort = sanitizeMarketSort(tag.getString("marketSort"));
        data.marketCategory = sanitizeMarketCategory(tag.getString("marketCategory"));
        data.marketSearch = sanitizeSearch(tag.getString("marketSearch"));
        data.marketRememberPrice = !tag.contains("marketRememberPrice", Tag.TAG_BYTE)
                || tag.getBoolean("marketRememberPrice");
        data.unseenMarketSales = Math.max(0, tag.getInt("unseenMarketSales"));
        data.menuHudIntroShown = tag.getBoolean("menuHudIntroShown");
        data.publicProfileEnabled = tag.getBoolean("publicProfileEnabled");
        data.serverBuyGoldDate = tag.getString("serverBuyGoldDate");
        data.serverBuyGoldToday = Math.max(0L, tag.getLong("serverBuyGoldToday"));
        ListTag sales = tag.getList("marketplaceSales", Tag.TAG_COMPOUND);
        int saleCount = Math.min(sales.size(), MAX_MARKETPLACE_SALES);
        for (int i = 0; i < saleCount; i++) {
            MarketplaceSaleRecord sale = MarketplaceSaleRecord.load(sales.getCompound(i), registries);
            if (!sale.item().isEmpty()) {
                data.marketplaceSales.add(sale);
            }
        }
        if (sales.size() > saleCount) {
            YoikoServerCore.LOGGER.error("Player {} had {} marketplace sale records; only {} were loaded",
                    data.uuid, sales.size(), saleCount);
        }
        ListTag mails = tag.getList("mailbox", Tag.TAG_COMPOUND);
        int mailLoadLimit = MAX_MAILBOX_MAILS;
        for (int i = 0; i < Math.min(mails.size(), mailLoadLimit); i++) {
            data.mailbox.add(MailEntry.load(mails.getCompound(i), registries));
        }
        if (mails.size() > mailLoadLimit) {
            YoikoServerCore.LOGGER.error("Player {} had {} mail records; only {} were loaded",
                    data.uuid, mails.size(), mailLoadLimit);
        }
        ListTag overflowMails = tag.getList("mailboxOverflow", Tag.TAG_COMPOUND);
        int overflowCount = Math.min(overflowMails.size(), MAX_MAILBOX_OVERFLOW);
        for (int i = 0; i < overflowCount; i++) {
            data.mailboxOverflow.add(MailEntry.load(overflowMails.getCompound(i), registries));
        }
        if (overflowMails.size() > overflowCount) {
            YoikoServerCore.LOGGER.error("Player {} had {} queued mail records; only {} were loaded",
                    data.uuid, overflowMails.size(), overflowCount);
        }
        CompoundTag counters = tag.getCompound("achievementCounters");
        for (String key : counters.getAllKeys()) {
            if (key.matches("[a-z0-9_.-]{1,64}")) {
                data.achievementCounters.put(key, Math.max(0, counters.getInt(key)));
            }
        }
        data.validateLoadedRanges();
        return data;
    }

    private void validateLoadedRanges() {
        dailyStreak = Math.max(0, dailyStreak);
        dailyBonusKeys = Math.max(0, Math.min(2, dailyBonusKeys));
        dailyBonusOpenedCount = Math.max(0, Math.min(3, dailyBonusOpenedCount));
        dailyBonusOpenedMask &= 0b111;
        dailyBonusLastOpened = Math.max(-1, Math.min(2, dailyBonusLastOpened));
        relicScrap = Math.max(0, relicScrap);
        relicMiningEasyCredits = Math.max(0, relicMiningEasyCredits);
        restedGold = Math.max(0L, restedGold);
        lastParticleGachaTicketPurchaseAt = Math.max(0L, lastParticleGachaTicketPurchaseAt);
        relicUpgradeCrystals = Math.max(0, relicUpgradeCrystals);
        relicMiningTicketsToday = Math.max(0, relicMiningTicketsToday);
        relicMiningPityMisses = Math.max(0, relicMiningPityMisses);
        activeRelicPreset = Math.clamp(activeRelicPreset, 0, RELIC_PRESET_COUNT - 1);
        ensureRelicPresets();
        unseenMarketSales = Math.max(0, unseenMarketSales);
        serverBuyGoldToday = Math.max(0L, serverBuyGoldToday);
        yoikoStoragePage = Math.max(0, Math.min(MAX_YOIKO_STORAGE_PAGES - 1, yoikoStoragePage));
        cosmeticPage = Math.max(0, cosmeticPage);
        marketPage = Math.max(0, marketPage);
        for (RelicInstance relic : ownedRelics) {
            relic.level = Math.max(0, Math.min(10, relic.level));
        }
        lockedYoikoStorageSlots.removeIf(slot -> slot < 0 || slot >= yoikoStorage.size());
    }

    public void ensureYoikoStorageSize(int slots) {
        int target = Math.max(MIN_YOIKO_STORAGE_SLOTS, clampYoikoStorageSlots(slots));
        if (yoikoStorage.size() >= target) {
            return;
        }
        NonNullList<ItemStack> expanded = NonNullList.withSize(target, ItemStack.EMPTY);
        for (int i = 0; i < yoikoStorage.size(); i++) {
            expanded.set(i, yoikoStorage.get(i));
        }
        yoikoStorage = expanded;
    }

    public static int clampYoikoStorageSlots(int slots) {
        return Math.max(0, Math.min(MAX_YOIKO_STORAGE_SLOTS, slots));
    }

    public static int yoikoStoragePageCount(int activeSlots) {
        int slots = clampYoikoStorageSlots(activeSlots);
        return Math.max(1, (slots + YOIKO_STORAGE_PAGE_SIZE - 1) / YOIKO_STORAGE_PAGE_SIZE);
    }

    public static int clampYoikoStoragePage(int page, int activeSlots) {
        return Math.max(0, Math.min(page, yoikoStoragePageCount(activeSlots) - 1));
    }

    public static String sanitizeMenuTab(String tab) {
        return switch (tab) {
            case MENU_TAB_MAILBOX, MENU_TAB_RELIC, MENU_TAB_COSMETIC, MENU_TAB_MARKET, MENU_TAB_TURTLE -> tab;
            default -> MENU_TAB_STORAGE;
        };
    }

    public static String sanitizeCosmeticCategory(String category) {
        return switch (category) {
            case "CHEST", "FEET", "RANK" -> category;
            case "PARTICLE" -> "FEET";
            default -> "HEAD";
        };
    }

    public static String sanitizeMarketSection(String section) {
        return switch (section) {
            case "gem_shop", "player_market", "my_listings", "sales_history" -> section;
            default -> "server_shop";
        };
    }

    public static String sanitizeMarketSort(String sort) {
        return switch (sort) {
            case "price_asc", "price_desc", "expiring" -> sort;
            default -> "latest";
        };
    }

    public static String sanitizeMarketCategory(String category) {
        return switch (category) {
            case "block", "tool", "consumable", "spawn_egg", "other" -> category;
            default -> "all";
        };
    }

    public static String sanitizeSearch(String query) {
        if (query == null) {
            return "";
        }
        String cleaned = query.replace('|', ' ').strip();
        return cleaned.length() <= 64 ? cleaned : cleaned.substring(0, 64);
    }

    public void ensureEquippedRelicSlots() {
        while (equippedRelics.size() < RELIC_EQUIP_SLOT_COUNT) {
            equippedRelics.add("");
        }
        while (equippedRelics.size() > RELIC_EQUIP_SLOT_COUNT) {
            equippedRelics.remove(equippedRelics.size() - 1);
        }
    }

    public void ensureRelicPresets() {
        ensureEquippedRelicSlots();
        activeRelicPreset = Math.clamp(activeRelicPreset, 0, RELIC_PRESET_COUNT - 1);
        while (relicPresets.size() < RELIC_PRESET_COUNT) {
            relicPresets.add(new ArrayList<>(equippedRelics));
        }
        while (relicPresets.size() > RELIC_PRESET_COUNT) {
            relicPresets.remove(relicPresets.size() - 1);
        }
        while (relicPresetContractTargets.size() < RELIC_PRESET_COUNT) {
            relicPresetContractTargets.add(radiantContractTargetUuid == null ? "" : radiantContractTargetUuid);
        }
        while (relicPresetContractTargets.size() > RELIC_PRESET_COUNT) {
            relicPresetContractTargets.remove(relicPresetContractTargets.size() - 1);
        }
        for (int i = 0; i < relicPresets.size(); i++) {
            List<String> preset = relicPresets.get(i);
            if (preset == null) {
                preset = new ArrayList<>(equippedRelics);
                relicPresets.set(i, preset);
            }
            normalizeRelicSlotList(preset);
        }
    }

    public void captureActiveRelicPreset() {
        ensureRelicPresets();
        relicPresets.set(activeRelicPreset, new ArrayList<>(equippedRelics));
        relicPresetContractTargets.set(activeRelicPreset,
                radiantContractTargetUuid == null ? "" : radiantContractTargetUuid);
    }

    public void pruneEquippedRelics() {
        ensureEquippedRelicSlots();
        Set<String> owned = new LinkedHashSet<>();
        for (RelicInstance relic : ownedRelics) {
            owned.add(relic.uuid.toString());
        }
        Set<String> used = new LinkedHashSet<>();
        for (int i = 0; i < equippedRelics.size(); i++) {
            String uuid = equippedRelics.get(i);
            if (uuid.isBlank() || !owned.contains(uuid) || used.contains(uuid)) {
                equippedRelics.set(i, "");
            } else {
                used.add(uuid);
            }
        }
        ensureRelicPresets();
        for (int presetIndex = 0; presetIndex < relicPresets.size(); presetIndex++) {
            List<String> preset = relicPresets.get(presetIndex);
            normalizeRelicSlotList(preset);
            Set<String> presetUsed = new LinkedHashSet<>();
            for (int i = 0; i < preset.size(); i++) {
                String uuid = preset.get(i);
                if (uuid.isBlank() || !owned.contains(uuid) || !presetUsed.add(uuid)) {
                    preset.set(i, "");
                }
            }
            String contractTarget = relicPresetContractTargets.get(presetIndex);
            if (contractTarget == null || !preset.contains(contractTarget)) {
                relicPresetContractTargets.set(presetIndex, "");
            }
        }
        captureActiveRelicPreset();
    }

    private static void normalizeRelicSlotList(List<String> slots) {
        while (slots.size() < RELIC_EQUIP_SLOT_COUNT) {
            slots.add("");
        }
        while (slots.size() > RELIC_EQUIP_SLOT_COUNT) {
            slots.remove(slots.size() - 1);
        }
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == null) {
                slots.set(i, "");
            }
        }
    }

    private CompoundTag saveYoikoStorage(HolderLookup.Provider registries) {
        CompoundTag storage = new CompoundTag();
        storage.putInt("Size", yoikoStorage.size());
        ListTag items = new ListTag();
        for (int i = 0; i < yoikoStorage.size(); i++) {
            ItemStack stack = yoikoStorage.get(i);
            if (!stack.isEmpty()) {
                CompoundTag item = new CompoundTag();
                item.putInt("Slot", i);
                items.add(stack.save(registries, item));
            }
        }
        storage.put("Items", items);
        return storage;
    }

    private static CompoundTag saveSizedItems(HolderLookup.Provider registries, List<ItemStack> stacks) {
        CompoundTag storage = new CompoundTag();
        storage.putInt("Size", stacks.size());
        ListTag items = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                CompoundTag item = new CompoundTag();
                item.putInt("Slot", i);
                ItemStack serializable = stack.copy();
                serializable.setCount(Math.max(1, Math.min(serializable.getCount(),
                        Math.min(99, serializable.getMaxStackSize()))));
                items.add(serializable.save(registries, item));
            }
        }
        storage.put("Items", items);
        return storage;
    }

    private static NonNullList<ItemStack> loadSizedItems(CompoundTag storage, HolderLookup.Provider registries, int fallbackSize) {
        NonNullList<ItemStack> stacks = NonNullList.withSize(fallbackSize, ItemStack.EMPTY);
        ListTag items = storage.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            int slot = item.getInt("Slot");
            if (slot >= 0 && slot < stacks.size()) {
                stacks.set(slot, ItemStack.parse(registries, item).orElse(ItemStack.EMPTY));
            }
        }
        return stacks;
    }

    private void loadYoikoStorage(CompoundTag storage, HolderLookup.Provider registries) {
        int size = storage.contains("Size", Tag.TAG_INT)
                ? storage.getInt("Size")
                : inferredYoikoStorageSize(storage.getList("Items", Tag.TAG_COMPOUND));
        yoikoStorage = NonNullList.withSize(Math.max(MIN_YOIKO_STORAGE_SLOTS, clampYoikoStorageSlots(size)), ItemStack.EMPTY);

        ListTag items = storage.getList("Items", Tag.TAG_COMPOUND);
        int itemRecordLimit = Math.min(items.size(), MAX_YOIKO_STORAGE_SLOTS);
        if (size < 0 || size > MAX_YOIKO_STORAGE_SLOTS || items.size() > itemRecordLimit) {
            YoikoServerCore.LOGGER.error(
                    "Player {} storage structure was clamped (declaredSize={}, itemRecords={}, maxSlots={})",
                    uuid, size, items.size(), MAX_YOIKO_STORAGE_SLOTS);
        }
        for (int i = 0; i < itemRecordLimit; i++) {
            CompoundTag item = items.getCompound(i);
            int slot = item.getInt("Slot");
            if (slot >= 0 && slot < yoikoStorage.size()) {
                yoikoStorage.set(slot, ItemStack.parse(registries, item).orElse(ItemStack.EMPTY));
            }
        }
    }

    private static int inferredYoikoStorageSize(ListTag items) {
        int highestSlot = -1;
        for (int i = 0; i < items.size(); i++) {
            highestSlot = Math.max(highestSlot, items.getCompound(i).getInt("Slot"));
        }
        return Math.max(MIN_YOIKO_STORAGE_SLOTS, highestSlot + 1);
    }

    private static ListTag stringList(Collection<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }

    private ListTag equippedRelicList() {
        ensureEquippedRelicSlots();
        ListTag list = new ListTag();
        for (String value : equippedRelics) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }

    private ListTag relicPresetList() {
        ensureRelicPresets();
        ListTag presets = new ListTag();
        for (int i = 0; i < relicPresets.size(); i++) {
            CompoundTag encoded = new CompoundTag();
            encoded.put("slots", stringList(relicPresets.get(i)));
            encoded.putString("contractTarget", relicPresetContractTargets.get(i));
            presets.add(encoded);
        }
        return presets;
    }

    private static LinkedHashSet<String> readStringSet(ListTag list) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            set.add(list.getString(i));
        }
        return set;
    }

    private static List<String> readStringList(ListTag list) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            values.add(list.getString(i));
        }
        return values;
    }

    public static class GachaStats {
        public int totalRolls;
        public int legendaryRolls;
        public int shinyRolls;
        public int rollsSinceMythical;
        public int rollsSinceLegendary;

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("totalRolls", totalRolls);
            tag.putInt("legendaryRolls", legendaryRolls);
            tag.putInt("shinyRolls", shinyRolls);
            tag.putInt("rollsSinceMythical", rollsSinceMythical);
            tag.putInt("rollsSinceLegendary", rollsSinceLegendary);
            return tag;
        }

        public static GachaStats load(CompoundTag tag) {
            GachaStats stats = new GachaStats();
            stats.totalRolls = tag.getInt("totalRolls");
            stats.legendaryRolls = tag.getInt("legendaryRolls");
            stats.shinyRolls = tag.getInt("shinyRolls");
            stats.rollsSinceMythical = tag.getInt("rollsSinceMythical");
            stats.rollsSinceLegendary = tag.getInt("rollsSinceLegendary");
            return stats;
        }
    }

    public static class RelicInstance {
        public UUID uuid = UUID.randomUUID();
        public String relicId;
        public RelicRarity rarity;
        public int level;
        public String secondaryEffect = "";
        public boolean secondaryEffectInitialized;
        public boolean crownRabbitRelic;
        public boolean locked;
        public int storageSlot = -1;

        public RelicInstance(String relicId, RelicRarity rarity) {
            this.relicId = relicId;
            this.rarity = rarity;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", uuid);
            tag.putString("relicId", relicId);
            tag.putString("rarity", rarity.name());
            tag.putInt("level", level);
            tag.putString("secondaryEffect", secondaryEffect == null ? "" : secondaryEffect);
            tag.putBoolean("crownRabbitRelic", crownRabbitRelic);
            tag.putBoolean("locked", locked);
            tag.putInt("storageSlot", storageSlot);
            return tag;
        }

        public static RelicInstance load(CompoundTag tag) {
            RelicInstance instance = new RelicInstance(tag.getString("relicId"), RelicRarity.fromString(tag.getString("rarity")));
            if (tag.hasUUID("uuid")) {
                instance.uuid = tag.getUUID("uuid");
            }
            instance.level = tag.getInt("level");
            instance.secondaryEffectInitialized = tag.contains("secondaryEffect", Tag.TAG_STRING);
            instance.secondaryEffect = instance.secondaryEffectInitialized
                    ? tag.getString("secondaryEffect")
                    : "";
            instance.crownRabbitRelic = tag.getBoolean("crownRabbitRelic");
            instance.locked = tag.getBoolean("locked");
            instance.storageSlot = tag.contains("storageSlot", Tag.TAG_INT)
                    ? tag.getInt("storageSlot")
                    : -1;
            return instance;
        }
    }

    public static class MailEntry {
        public UUID id = UUID.randomUUID();
        public String type = "message";
        public String claimKey = "";
        public String sender = "yoiko_core.mail.sender.system";
        public String title = "";
        public String message = "";
        public long createdAt = System.currentTimeMillis();
        public boolean read;
        public List<ItemStack> items = new ArrayList<>();
        public long attachedGold;
        public long attachedGems;
        public String sourceId = "";
        public long sourcePrice;
        public long expiresAt;
        public String rarity = "";

        public MailEntry() {
        }

        public MailEntry(String type, String claimKey, String sender, String title, String message, List<ItemStack> items) {
            this(type, claimKey, sender, title, message, items, 0L, 0L);
        }

        public MailEntry(String type, String claimKey, String sender, String title, String message,
                         List<ItemStack> items, long attachedGold, long attachedGems) {
            this.type = type;
            this.claimKey = claimKey;
            this.sender = sender;
            this.title = title;
            this.message = message;
            this.items = copyItems(items);
            this.attachedGold = Math.max(0L, attachedGold);
            this.attachedGems = Math.max(0L, attachedGems);
        }

        public MailEntry withSource(String sourceId, long sourcePrice, long expiresAt, String rarity) {
            this.sourceId = sourceId == null ? "" : sourceId;
            this.sourcePrice = Math.max(0L, sourcePrice);
            this.expiresAt = Math.max(0L, expiresAt);
            this.rarity = rarity == null ? "" : rarity;
            return this;
        }

        public CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("type", type);
            tag.putString("claimKey", claimKey);
            tag.putString("sender", sender);
            tag.putString("title", title);
            tag.putString("message", message);
            tag.putLong("createdAt", createdAt);
            tag.putBoolean("read", read);
            tag.putLong("attachedGold", Math.max(0L, attachedGold));
            tag.putLong("attachedGems", Math.max(0L, attachedGems));
            tag.putString("sourceId", sourceId);
            tag.putLong("sourcePrice", Math.max(0L, sourcePrice));
            tag.putLong("expiresAt", Math.max(0L, expiresAt));
            tag.putString("rarity", rarity);
            ListTag itemTags = new ListTag();
            for (ItemStack stack : items) {
                if (!stack.isEmpty() && stack.saveOptional(registries) instanceof CompoundTag itemTag && !itemTag.isEmpty()) {
                    itemTags.add(itemTag);
                }
            }
            tag.put("items", itemTags);
            return tag;
        }

        public static MailEntry load(CompoundTag tag, HolderLookup.Provider registries) {
            MailEntry mail = new MailEntry();
            if (tag.hasUUID("id")) {
                mail.id = tag.getUUID("id");
            }
            mail.type = tag.getString("type");
            mail.claimKey = tag.getString("claimKey");
            mail.sender = tag.getString("sender");
            mail.title = tag.getString("title");
            mail.message = tag.getString("message");
            mail.createdAt = tag.getLong("createdAt");
            mail.read = tag.getBoolean("read");
            mail.attachedGold = Math.max(0L, tag.getLong("attachedGold"));
            mail.attachedGems = Math.max(0L, tag.getLong("attachedGems"));
            mail.sourceId = tag.getString("sourceId");
            mail.sourcePrice = Math.max(0L, tag.getLong("sourcePrice"));
            mail.expiresAt = Math.max(0L, tag.getLong("expiresAt"));
            mail.rarity = tag.getString("rarity");
            if (mail.sourceId.isBlank() && "market_return".equals(mail.type) && mail.claimKey.contains("|")) {
                int separator = mail.claimKey.lastIndexOf('|');
                mail.sourceId = mail.claimKey.substring(0, separator);
                try {
                    mail.sourcePrice = Math.max(0L, Long.parseLong(mail.claimKey.substring(separator + 1)));
                } catch (NumberFormatException ignored) {
                    mail.sourcePrice = 0L;
                }
            }
            ListTag itemTags = tag.getList("items", Tag.TAG_COMPOUND);
            for (int i = 0; i < itemTags.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(registries, itemTags.getCompound(i));
                if (!stack.isEmpty()) {
                    mail.items.add(stack);
                }
            }
            return mail;
        }

        public List<ItemStack> copyItems() {
            return copyItems(items);
        }

        public boolean hasAttachments() {
            return !items.isEmpty() || attachedGold > 0L || attachedGems > 0L;
        }

        public int attachmentTypeCount() {
            return items.size() + (attachedGold > 0L ? 1 : 0) + (attachedGems > 0L ? 1 : 0);
        }

        private static List<ItemStack> copyItems(List<ItemStack> items) {
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    copies.add(stack.copy());
                }
            }
            return copies;
        }
    }
}
