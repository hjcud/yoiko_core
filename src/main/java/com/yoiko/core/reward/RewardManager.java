package com.yoiko.core.reward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.yoiko.core.cosmetic.CosmeticData;
import com.yoiko.core.cosmetic.CosmeticManager;
import com.yoiko.core.cosmetic.CosmeticRarity;
import com.yoiko.core.cosmetic.CosmeticType;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.economy.CurrencyManager;
import com.yoiko.core.economy.CurrencyType;
import com.yoiko.core.economy.EconomyManager;
import com.yoiko.core.economy.RestedGoldManager;
import com.yoiko.core.mail.MailboxManager;
import com.yoiko.core.relic.RelicManager;
import com.yoiko.core.turtle.TurtleRacingSavedData;
import com.yoiko.core.turtle.TurtleTicketType;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;

public final class RewardManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path REWARD_FILE = FMLPaths.CONFIGDIR.get().resolve(YoikoServerCore.MODID).resolve("rewards.json");
    private static final DateTimeFormatter RESET_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT);
    private static final Random RANDOM = new Random();
    private static final int DAILY_BOX_COUNT = 3;
    private static final int STREAK_PREVIEW_DAYS = 7;
    private static final String SUPPORTER_RANK_ID = "supporter";
    private static final String DAILY_BONUS_COSMETIC_PREFIX = "cosmetic:";
    private static final String DAILY_BONUS_TURTLE_PREFIX = "turtle:";
    private static final String DAILY_BONUS_RARITY_WEIGHTS_KEY = "dailyBonusRarityWeights";
    private static final String RELIC_SCRAP_ID = "yoiko_core:relic_scrap";

    private static List<DailyReward> dailyRewards = new ArrayList<>();
    private static List<ItemStack> firstLoginRewards = new ArrayList<>();
    private static List<DailyBonusReward> dailyBonusRewards = new ArrayList<>();
    private static EnumMap<DailyBonusRarity, Double> dailyBonusRarityWeights = defaultDailyBonusRarityWeights();

    private RewardManager() {
    }

    public static void init() {
        ensureFile();
        load();
    }

    public static void load() {
        ensureFile();
        try (Reader reader = Files.newBufferedReader(REWARD_FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            loadRewardConfig(ensureRewardConfigRoot(root));
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.error(
                    "Failed to load Yoiko reward config. The file was left unchanged; built-in defaults are active for this run.",
                    exception
            );
            loadRewardConfig(defaultRewardConfig());
        }
    }

    private static void loadRewardConfig(JsonObject root) {
        dailyBonusRarityWeights = dailyBonusRarityWeightsFromJson(root.get(DAILY_BONUS_RARITY_WEIGHTS_KEY));
        dailyRewards = new ArrayList<>();
        JsonArray daily = root.getAsJsonArray("dailyRewards");
        if (daily != null) {
            for (int i = 0; i < daily.size(); i++) {
                JsonObject entry = daily.get(i).getAsJsonObject();
                int day = entry.has("day") ? entry.get("day").getAsInt() : i + 1;
                dailyRewards.add(new DailyReward(day, RewardItemSerializer.listFromJson(entry.get("items"))));
            }
        }
        firstLoginRewards = RewardItemSerializer.listFromJson(root.get("firstLoginRewards"));
        dailyBonusRewards = dailyBonusRewardsFromJson(root.get("dailyBonusRewards"));
        appendRuntimeTurtleRewards(dailyBonusRewards);
        int supplementedCosmetics = appendRuntimeDailyBonusCosmeticRewards(dailyBonusRewards);
        if (supplementedCosmetics > 0) {
            YoikoServerCore.LOGGER.warn(
                    "Daily bonus config was missing {} eligible particle cosmetics; added them to the runtime reward pools.",
                    supplementedCosmetics
            );
        }
        if (dailyBonusRewards.isEmpty()) {
            throw new IllegalStateException("rewards.json dailyBonusRewards must contain at least one reward.");
        }
        logDailyBonusPoolSummary();
    }

    public static boolean claimDaily(ServerPlayer player) {
        boolean sent = enqueueDailyMailbox(player);
        if (sent) {
            MailboxManager.open(player);
            return true;
        }
        player.sendSystemMessage(Component.translatable("yoiko_core.message.reward.daily_already_sent").withStyle(ChatFormatting.YELLOW));
        MailboxManager.open(player);
        return false;
    }

    public static boolean claimFirst(ServerPlayer player) {
        boolean sent = enqueueFirstLoginMailbox(player);
        if (sent) {
            MailboxManager.open(player);
            return true;
        }
        player.sendSystemMessage(Component.translatable("yoiko_core.message.reward.first_already_sent").withStyle(ChatFormatting.YELLOW));
        MailboxManager.open(player);
        return false;
    }

    public static void enqueueLoginMailboxRewards(ServerPlayer player) {
        grantDailyBonusKeys(player, false);
        enqueueFirstLoginMailbox(player);
        enqueueDailyMailbox(player);
    }

    public static boolean enqueueDailyMailbox(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        if (!canClaimDaily(data)) {
            return false;
        }

        long now = System.currentTimeMillis();
        String claimKey = dailyClaimKey(now);
        if (MailboxManager.hasPending(player, "daily", claimKey)) {
            return false;
        }

        long previousClaimAt = data.lastDailyRewardClaimAt;
        int previousStreak = data.dailyStreak;
        updateStreak(data);
        DailyReward reward = rewardForStreak(data.dailyStreak);
        DailyMailItems mailItems = dailyMailItems(reward.items());
        long restedGold = RestedGoldManager.reserveCalendarClaim(player);
        data.lastDailyRewardClaimAt = now;
        ServerYoikoSavedData.get(player.server).markDirty(player);

        String message = "yoiko_core.mail.daily.message|" + data.dailyStreak;
        boolean sent = MailboxManager.sendSystemMail(player, "daily", claimKey,
                "yoiko_core.mail.daily.title|" + data.dailyStreak, message, mailItems.items(), restedGold, 0L);
        if (!sent) {
            RestedGoldManager.restoreCalendarClaim(player, restedGold);
            data.lastDailyRewardClaimAt = previousClaimAt;
            data.dailyStreak = previousStreak;
            ServerYoikoSavedData.get(player.server).markDirty(player);
        }
        if (sent) {
            YoikoAdvancementManager.recordDailyStreak(player, data.dailyStreak);
        }
        return sent;
    }

    public static boolean enqueueFirstLoginMailbox(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        if (data.claimedFirstLoginReward || MailboxManager.hasPending(player, "first_login", "first")) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean sent = MailboxManager.sendSystemMail(
                player,
                "first_login",
                "first",
                "yoiko_core.mail.first_login.title",
                "yoiko_core.mail.first_login.message",
                firstLoginRewards,
                200L,
                0L
        );
        if (sent) {
            data.claimedFirstLoginReward = true;
            data.firstRewardClaimedAt = now;
            ServerYoikoSavedData.get(player.server).markDirty(player);
        }
        return sent;
    }

    public static DailyBonusState dailyBonusState(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        ensureDailyBonusBoxes(player, data);
        grantDailyBonusKeys(player, false);
        return new DailyBonusState(
                copyItems(data.dailyBonusBoxes),
                dailyBonusCounts(data),
                dailyBonusRarities(data),
                dailyBonusCosmeticVisuals(data),
                dailyBonusChances(data),
                data.dailyBonusOpenedMask,
                Math.max(0, data.dailyBonusOpenedCount),
                dailyBonusOpenLimit(data),
                data.dailyBonusLastOpened,
                Math.max(0, data.dailyBonusKeys),
                dailyBonusOpenLimit(data),
                nextDailyBonusKeyRemainingText()
        );
    }

    public static boolean openDailyBonusBox(ServerPlayer player, int boxIndex) {
        if (boxIndex < 0 || boxIndex >= DAILY_BOX_COUNT) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.reward.daily_box.invalid").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        ensureDailyBonusBoxes(player, data);
        grantDailyBonusKeys(player, false);

        if (data.dailyBonusKeys <= 0) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.reward.daily_box.limit", nextDailyBonusKeyRemainingText()).withStyle(ChatFormatting.YELLOW));
            return false;
        }
        if ((data.dailyBonusOpenedMask & (1 << boxIndex)) != 0) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.reward.daily_box.opened").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        normalizeDailyBonusMetadata(data);
        ItemStack preview = data.dailyBonusBoxes.get(boxIndex).copy();
        if (preview.isEmpty()) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.reward.daily_box.empty").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        String rewardKey = dailyBonusRewardKey(data, boxIndex);
        int rewardCount = dailyBonusRewardCount(data, boxIndex);

        data.dailyBonusOpenedMask |= 1 << boxIndex;
        data.dailyBonusOpenedCount++;
        data.dailyBonusKeys = Math.max(0, data.dailyBonusKeys - 1);
        data.dailyBonusLastOpened = boxIndex;
        savedData.markDirty(player);
        DailyBonusGrantResult grant = grantDailyBonusReward(player, rewardKey, preview, rewardCount);
        if (grant.convertedToGems()) {
            player.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.reward.daily_box.duplicate_gems",
                    dailyBonusRewardName(rewardKey, preview),
                    grant.gems()
            ).withStyle(ChatFormatting.AQUA));
        } else {
            player.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.reward.daily_box.claimed",
                    dailyBonusRewardName(rewardKey, preview),
                    displayedDailyBonusRewardCount(rewardKey, rewardCount)
            ).withStyle(ChatFormatting.GREEN));
        }
        YoikoAdvancementManager.recordDailyBonusOpen(player);
        return true;
    }

    public static boolean isDailyBonusCosmetic(String cosmeticId) {
        if (cosmeticId == null || cosmeticId.isBlank()) {
            return false;
        }
        for (DailyBonusReward reward : dailyBonusRewards) {
            if (cosmeticId.equals(reward.cosmeticId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gem purchases must use the exact active daily-bonus pool as their source of truth.
     * Admin-only and rank-exclusive cosmetics stay unavailable even if a stale config entry exists.
     */
    public static boolean isGemExchangeEligibleCosmetic(String cosmeticId) {
        CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
        return cosmetic != null
                && !CosmeticManager.isAdminOnlyCosmetic(cosmeticId)
                && cosmetic.requiredRank().isBlank()
                && isDailyBonusCosmetic(cosmeticId);
    }

    public static List<ItemStack> streakPreview(ServerPlayer player) {
        List<ItemStack> preview = new ArrayList<>();
        for (int i = 0; i < STREAK_PREVIEW_DAYS; i++) {
            DailyReward reward = weeklyRewardForDay(i);
            preview.add(firstItem(reward.items()));
        }
        return preview;
    }

    public static void resetDaily(ServerPlayer player) {
        resetStreakReward(player);
        resetDailyBonusBoxes(player);
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        data.dailyBonusKeys = 0;
        data.dailyBonusKeyGrantKey = "";
        ServerYoikoSavedData.get(player.server).markDirty(player);
    }

    public static int grantDailyBonusKeys(ServerPlayer player, boolean force) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        String key = dailyBonusKey(System.currentTimeMillis());
        int maxKeys = dailyBonusOpenLimit(data);
        if (data.dailyBonusKeys > maxKeys) {
            data.dailyBonusKeys = maxKeys;
        }
        if (!force && key.equals(data.dailyBonusKeyGrantKey)) {
            savedData.markDirty(player);
            return 0;
        }
        int before = data.dailyBonusKeys;
        data.dailyBonusKeys = Math.min(maxKeys, data.dailyBonusKeys + maxKeys);
        data.dailyBonusKeyGrantKey = key;
        savedData.markDirty(player);
        return Math.max(0, data.dailyBonusKeys - before);
    }

    public static void resetDailyBonusBoxes(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        String key = dailyBonusKey(System.currentTimeMillis());
        rollDailyBonusBoxes(player, data, key, RANDOM);
    }

    public static void resetStreakReward(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        data.lastDailyRewardClaimAt = 0L;
        data.dailyStreak = 0;
        MailboxManager.removeType(player, "daily");
        ServerYoikoSavedData.get(player.server).markDirty(player);
    }

    public static void resetFirst(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        data.claimedFirstLoginReward = false;
        data.firstRewardClaimedAt = 0L;
        MailboxManager.removeType(player, "first_login");
        ServerYoikoSavedData.get(player.server).markDirty(player);
    }

    public static Component statusComponent(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        DailyBonusState bonus = dailyBonusState(player);
        Component dailyState = Component.translatable(canClaimDaily(data)
                ? "yoiko_core.command.reward.available"
                : "yoiko_core.command.reward.completed");
        Component firstState = Component.translatable(data.claimedFirstLoginReward
                ? "yoiko_core.command.reward.mailed_or_claimed"
                : "yoiko_core.command.reward.available");
        Component nextClaim = canClaimDaily(data)
                ? Component.translatable("yoiko_core.command.reward.now")
                : Component.literal(nextClaimText(data));
        return Component.translatable("yoiko_core.command.reward.status", dailyState, data.dailyStreak,
                nextClaim, bonus.keys(), bonus.maxKeys(), firstState, data.mailbox.size());
    }

    private static void ensureDailyBonusBoxes(ServerPlayer player, PlayerYoikoData data) {
        String key = dailyBonusKey(System.currentTimeMillis());
        if (key.equals(data.dailyBonusKey) && data.dailyBonusBoxes.size() == DAILY_BOX_COUNT && !hasEmptyDailyBonusBox(data)) {
            normalizeDailyBonusMetadata(data);
            if (!hasNonCosmeticHighRarityDailyBonus(data)) {
                data.dailyBonusOpenedCount = Integer.bitCount(data.dailyBonusOpenedMask);
                return;
            }
        }

        Random random = new Random(player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits() ^ key.hashCode());
        rollDailyBonusBoxes(player, data, key, random);
    }

    private static void rollDailyBonusBoxes(ServerPlayer player, PlayerYoikoData data, String key, Random random) {
        data.dailyBonusKey = key;
        data.dailyBonusOpenedCount = 0;
        data.dailyBonusOpenedMask = 0;
        data.dailyBonusLastOpened = -1;
        data.dailyBonusBoxes = NonNullList.withSize(DAILY_BOX_COUNT, ItemStack.EMPTY);
        data.dailyBonusRewardCounts = new ArrayList<>();
        data.dailyBonusRewardKeys = new ArrayList<>();
        data.dailyBonusRewardRarities = new ArrayList<>();

        Set<String> reservedCosmetics = new HashSet<>();
        for (int i = 0; i < DAILY_BOX_COUNT; i++) {
            DailyBonusReward reward = rollDailyBonusReward(data, reservedCosmetics, random);
            data.dailyBonusBoxes.set(i, reward.preview().copy());
            data.dailyBonusRewardCounts.add(reward.count());
            data.dailyBonusRewardKeys.add(reward.key());
            data.dailyBonusRewardRarities.add(reward.rarity().name());
            String cosmeticId = reward.cosmeticId();
            if (!cosmeticId.isBlank()) {
                reservedCosmetics.add(cosmeticId);
            }
        }
        ServerYoikoSavedData.get(player.server).markDirty(player);
    }

    private static boolean hasEmptyDailyBonusBox(PlayerYoikoData data) {
        for (ItemStack stack : data.dailyBonusBoxes) {
            if (stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonCosmeticHighRarityDailyBonus(PlayerYoikoData data) {
        for (int i = 0; i < DAILY_BOX_COUNT; i++) {
            String rewardKey = dailyBonusRewardKey(data, i);
            DailyBonusRarity rarity = DailyBonusRarity.fromString(data.dailyBonusRewardRarities.get(i));
            if (!rewardKey.startsWith(DAILY_BONUS_COSMETIC_PREFIX)
                    && (rarity == DailyBonusRarity.LEGENDARY || rarity == DailyBonusRarity.MYTHIC)) {
                return true;
            }
        }
        return false;
    }

    private static DailyBonusReward rollDailyBonusReward(PlayerYoikoData data, Set<String> reservedCosmetics, Random random) {
        List<DailyBonusReward> candidates = dailyBonusCandidates(data, reservedCosmetics);
        EnumMap<DailyBonusRarity, List<DailyBonusReward>> rewardsByRarity = rewardsByRarity(candidates);
        double totalRarityWeight = 0.0D;
        for (DailyBonusRarity rarity : DailyBonusRarity.values()) {
            if (!rewardsByRarity.get(rarity).isEmpty()) {
                totalRarityWeight += dailyBonusRarityWeights.get(rarity);
            }
        }

        double roll = random.nextDouble() * totalRarityWeight;
        DailyBonusRarity selectedRarity = DailyBonusRarity.COMMON;
        for (DailyBonusRarity rarity : DailyBonusRarity.values()) {
            if (rewardsByRarity.get(rarity).isEmpty()) {
                continue;
            }
            selectedRarity = rarity;
            roll -= dailyBonusRarityWeights.get(rarity);
            if (roll < 0.0D) {
                break;
            }
        }
        return weightedDailyBonusReward(rewardsByRarity.get(selectedRarity), random);
    }

    private static EnumMap<DailyBonusRarity, List<DailyBonusReward>> rewardsByRarity(
            List<DailyBonusReward> rewards
    ) {
        EnumMap<DailyBonusRarity, List<DailyBonusReward>> grouped = new EnumMap<>(DailyBonusRarity.class);
        for (DailyBonusRarity rarity : DailyBonusRarity.values()) {
            grouped.put(rarity, new ArrayList<>());
        }
        for (DailyBonusReward reward : rewards) {
            grouped.get(reward.rarity()).add(reward);
        }
        return grouped;
    }

    private static List<DailyBonusReward> dailyBonusCandidates(PlayerYoikoData data, Set<String> reservedCosmetics) {
        List<DailyBonusReward> candidates = new ArrayList<>();
        for (DailyBonusReward reward : dailyBonusRewards) {
            String cosmeticId = reward.cosmeticId();
            if (!cosmeticId.isBlank() && reservedCosmetics.contains(cosmeticId)) {
                continue;
            }
            candidates.add(reward);
        }
        if (candidates.isEmpty()) {
            for (DailyBonusReward reward : dailyBonusRewards) {
                if (reward.cosmeticId().isBlank()) {
                    candidates.add(reward);
                }
            }
        }
        if (candidates.isEmpty()) {
            candidates = dailyBonusRewards;
        }
        return candidates;
    }

    private static List<DailyBonusChance> dailyBonusChances(PlayerYoikoData data) {
        List<DailyBonusReward> candidates = dailyBonusCandidates(data, Set.of());
        EnumMap<DailyBonusRarity, List<DailyBonusReward>> rewardsByRarity = rewardsByRarity(candidates);
        double totalWeight = 0.0D;
        for (DailyBonusRarity rarity : DailyBonusRarity.values()) {
            if (!rewardsByRarity.get(rarity).isEmpty()) {
                totalWeight += dailyBonusRarityWeights.get(rarity);
            }
        }

        List<DailyBonusChance> chances = new ArrayList<>();
        for (DailyBonusRarity rarity : DailyBonusRarity.values()) {
            double weight = rewardsByRarity.get(rarity).isEmpty() ? 0.0D : dailyBonusRarityWeights.get(rarity);
            double percent = totalWeight <= 0.0D ? 0.0D : weight * 100.0D / totalWeight;
            chances.add(new DailyBonusChance(rarity.name(), percent, allDailyBonusCosmeticsOwned(data, rarity)));
        }
        return List.copyOf(chances);
    }

    private static boolean allDailyBonusCosmeticsOwned(PlayerYoikoData data, DailyBonusRarity rarity) {
        boolean hasCosmetics = false;
        for (DailyBonusReward reward : dailyBonusRewards) {
            String cosmeticId = reward.cosmeticId();
            if (cosmeticId.isBlank() || reward.rarity() != rarity) {
                continue;
            }
            hasCosmetics = true;
            if (!data.ownedCosmetics.contains(cosmeticId)) {
                return false;
            }
        }
        return hasCosmetics;
    }

    private static DailyBonusReward weightedDailyBonusReward(List<DailyBonusReward> rewards, Random random) {
        int totalWeight = 0;
        for (DailyBonusReward reward : rewards) {
            totalWeight += Math.max(1, reward.weight());
        }
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (DailyBonusReward reward : rewards) {
            roll -= Math.max(1, reward.weight());
            if (roll < 0) {
                return reward;
            }
        }
        return rewards.get(0);
    }

    private static void normalizeDailyBonusMetadata(PlayerYoikoData data) {
        while (data.dailyBonusRewardCounts.size() < DAILY_BOX_COUNT) {
            int index = data.dailyBonusRewardCounts.size();
            ItemStack preview = index < data.dailyBonusBoxes.size()
                    ? data.dailyBonusBoxes.get(index)
                    : ItemStack.EMPTY;
            data.dailyBonusRewardCounts.add(preview.isEmpty() ? 0 : Math.max(1, preview.getCount()));
        }
        while (data.dailyBonusRewardCounts.size() > DAILY_BOX_COUNT) {
            data.dailyBonusRewardCounts.remove(data.dailyBonusRewardCounts.size() - 1);
        }
        while (data.dailyBonusRewardKeys.size() < DAILY_BOX_COUNT) {
            data.dailyBonusRewardKeys.add("");
        }
        while (data.dailyBonusRewardKeys.size() > DAILY_BOX_COUNT) {
            data.dailyBonusRewardKeys.remove(data.dailyBonusRewardKeys.size() - 1);
        }
        while (data.dailyBonusRewardRarities.size() < DAILY_BOX_COUNT) {
            int index = data.dailyBonusRewardRarities.size();
            ItemStack stack = index < data.dailyBonusBoxes.size() ? data.dailyBonusBoxes.get(index) : ItemStack.EMPTY;
            data.dailyBonusRewardRarities.add(DailyBonusRarity.fromMinecraft(stack.getRarity()).name());
        }
        while (data.dailyBonusRewardRarities.size() > DAILY_BOX_COUNT) {
            data.dailyBonusRewardRarities.remove(data.dailyBonusRewardRarities.size() - 1);
        }
        for (int i = 0; i < DAILY_BOX_COUNT; i++) {
            String rewardKey = dailyBonusRewardKey(data, i);
            if (rewardKey.startsWith(DAILY_BONUS_COSMETIC_PREFIX)) {
                CosmeticData cosmetic = CosmeticManager.get(rewardKey.substring(DAILY_BONUS_COSMETIC_PREFIX.length()));
                data.dailyBonusRewardRarities.set(i, cosmeticDailyRarity(cosmetic).name());
            }
        }
    }

    private static List<Integer> dailyBonusCounts(PlayerYoikoData data) {
        normalizeDailyBonusMetadata(data);
        return List.copyOf(data.dailyBonusRewardCounts);
    }

    private static List<String> dailyBonusRarities(PlayerYoikoData data) {
        normalizeDailyBonusMetadata(data);
        return List.copyOf(data.dailyBonusRewardRarities);
    }

    private static List<DailyBonusCosmeticVisual> dailyBonusCosmeticVisuals(PlayerYoikoData data) {
        normalizeDailyBonusMetadata(data);
        List<DailyBonusCosmeticVisual> visuals = new ArrayList<>(DAILY_BOX_COUNT);
        for (int i = 0; i < DAILY_BOX_COUNT; i++) {
            String rewardKey = dailyBonusRewardKey(data, i);
            if (!rewardKey.startsWith(DAILY_BONUS_COSMETIC_PREFIX)) {
                visuals.add(DailyBonusCosmeticVisual.EMPTY);
                continue;
            }
            String cosmeticId = rewardKey.substring(DAILY_BONUS_COSMETIC_PREFIX.length());
            CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
            if (cosmetic == null) {
                visuals.add(DailyBonusCosmeticVisual.EMPTY);
                continue;
            }
            visuals.add(new DailyBonusCosmeticVisual(
                    cosmetic.id(),
                    cosmetic.type().name(),
                    cosmetic.particleCategory().name()
            ));
        }
        return List.copyOf(visuals);
    }

    private static String dailyBonusRewardKey(PlayerYoikoData data, int boxIndex) {
        return boxIndex >= 0 && boxIndex < data.dailyBonusRewardKeys.size() ? data.dailyBonusRewardKeys.get(boxIndex) : "";
    }

    private static int dailyBonusRewardCount(PlayerYoikoData data, int boxIndex) {
        return boxIndex >= 0 && boxIndex < data.dailyBonusRewardCounts.size()
                ? Math.max(1, data.dailyBonusRewardCounts.get(boxIndex))
                : 1;
    }

    private static DailyBonusGrantResult grantDailyBonusReward(
            ServerPlayer player,
            String rewardKey,
            ItemStack preview,
            int rewardCount
    ) {
        if (rewardKey.startsWith(DAILY_BONUS_TURTLE_PREFIX)) {
            String reward = rewardKey.substring(DAILY_BONUS_TURTLE_PREFIX.length());
            var saved = TurtleRacingSavedData.get(player.server);
            var progress = saved.getOrCreatePlayer(player.getUUID());
            switch (reward) {
                case "shell_medal" -> progress.addShellMedals(rewardCount);
                case "ticket_standard" -> giveTurtleTicket(player,TurtleTicketType.STANDARD);
                case "ticket_pickup" -> giveTurtleTicket(player,TurtleTicketType.PICKUP);
                case "ticket_rare_style" -> giveTurtleTicket(player,TurtleTicketType.RARE_STRATEGY_SELECT);
                case "ticket_epic_plus" -> giveTurtleTicket(player,TurtleTicketType.EPIC_GUARANTEED);
                default -> throw new IllegalStateException("Unknown turtle daily reward: " + reward);
            }
            saved.markChanged();
            return DailyBonusGrantResult.NONE;
        }
        if (rewardKey.startsWith(DAILY_BONUS_COSMETIC_PREFIX)) {
            String cosmeticId = rewardKey.substring(DAILY_BONUS_COSMETIC_PREFIX.length());
            PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
            CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
            if (data.ownedCosmetics.contains(cosmeticId) && cosmetic != null) {
                long gems = EconomyManager.duplicateGemValue(cosmetic.rarity().name());
                long added = CurrencyManager.add(player, CurrencyType.GEM, gems);
                EconomyManager.recordEconomy(player, "DUPLICATE_GEMS_CREATED", added,
                        "cosmetic=" + cosmeticId + ";rarity=" + cosmetic.rarity().name());
                return new DailyBonusGrantResult(true, added);
            }
            if (!CosmeticManager.grant(player, cosmeticId, false)) {
                YoikoServerCore.LOGGER.warn(
                        "Could not grant daily bonus cosmetic {} to {}; no item fallback was given.",
                        cosmeticId,
                        player.getGameProfile().getName()
                );
            }
            return DailyBonusGrantResult.NONE;
        }
        grant(player, RewardItemSerializer.split(preview, rewardCount));
        return DailyBonusGrantResult.NONE;
    }

    private static Component dailyBonusRewardName(String rewardKey, ItemStack preview) {
        if (rewardKey.startsWith(DAILY_BONUS_TURTLE_PREFIX)) return preview.getHoverName();
        if (rewardKey.startsWith(DAILY_BONUS_COSMETIC_PREFIX)) {
            String cosmeticId = rewardKey.substring(DAILY_BONUS_COSMETIC_PREFIX.length());
            CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
            if (cosmetic != null) {
                return localizedCosmeticName(cosmetic);
            }
        }
        return preview.getHoverName();
    }

    private static int displayedDailyBonusRewardCount(String rewardKey, int rewardCount) {
        return rewardKey.startsWith(DAILY_BONUS_COSMETIC_PREFIX) ? 1 : Math.max(1, rewardCount);
    }

    private static void appendRuntimeTurtleRewards(List<DailyBonusReward> rewards) {
        addTurtleReward(rewards,"shell_medal",Items.TURTLE_SCUTE,Component.translatable("yoiko_core.reward.turtle.shell_medal"),20,DailyBonusRarity.COMMON,100);
        addTurtleReward(rewards,"ticket_standard",com.yoiko.core.registry.YoikoItems.TURTLE_STANDARD_TICKET.get(),Component.translatable("yoiko_core.reward.turtle.ticket_standard"),1,DailyBonusRarity.UNCOMMON,60);
        addTurtleReward(rewards,"ticket_pickup",com.yoiko.core.registry.YoikoItems.TURTLE_PICKUP_TICKET.get(),Component.translatable("yoiko_core.reward.turtle.ticket_pickup"),1,DailyBonusRarity.RARE,20);
        addTurtleReward(rewards,"ticket_rare_style",com.yoiko.core.registry.YoikoItems.TURTLE_RARE_STRATEGY_TICKET.get(),Component.translatable("yoiko_core.reward.turtle.ticket_rare_style"),1,DailyBonusRarity.EPIC,3);
        addTurtleReward(rewards,"ticket_epic_plus",com.yoiko.core.registry.YoikoItems.TURTLE_EPIC_TICKET.get(),Component.translatable("yoiko_core.reward.turtle.ticket_epic_plus"),1,DailyBonusRarity.EPIC,1);
    }

    private static void addTurtleReward(List<DailyBonusReward> rewards,String id,Item item,Component name,int count,DailyBonusRarity rarity,int weight){String key=DAILY_BONUS_TURTLE_PREFIX+id;if(rewards.stream().anyMatch(value->value.key().equals(key)))return;ItemStack preview=new ItemStack(item);preview.set(DataComponents.CUSTOM_NAME,name);rewards.add(new DailyBonusReward(key,preview,count,rarity,weight));}
    private static void giveTurtleTicket(ServerPlayer player,TurtleTicketType type){ItemStack stack=new ItemStack(com.yoiko.core.registry.YoikoItems.turtleTicket(type));com.yoiko.core.item.TurtleHatchTicketItem.bindTo(stack,player.getUUID());if(!player.getInventory().add(stack))player.drop(stack,false);}

    private static int dailyBonusOpenLimit(PlayerYoikoData data) {
        return data.ownedRanks.contains(SUPPORTER_RANK_ID) ? 2 : 1;
    }

    private static String dailyBonusKey(long millis) {
        return YoikoResetClock.dailyPeriodKey(millis);
    }

    private static String nextDailyBonusKeyRemainingText() {
        ZonedDateTime now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone());
        ZonedDateTime next = YoikoResetClock.nextDailyReset(System.currentTimeMillis());
        long minutes = Math.max(0L, ChronoUnit.MINUTES.between(now, next));
        long hours = minutes / 60L;
        long remainingMinutes = minutes % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, remainingMinutes);
    }

    private static boolean canClaimDaily(PlayerYoikoData data) {
        if (data.lastDailyRewardClaimAt <= 0L) {
            return true;
        }
        String mode = YoikoCommonConfig.DAILY_RESET_MODE.get();
        if ("cooldown".equalsIgnoreCase(mode)) {
            long elapsed = System.currentTimeMillis() - data.lastDailyRewardClaimAt;
            return elapsed >= YoikoCommonConfig.DAILY_COOLDOWN_HOURS.get() * 60L * 60L * 1000L;
        }
        return periodIndex(System.currentTimeMillis(), mode) > periodIndex(data.lastDailyRewardClaimAt, mode);
    }

    private static void updateStreak(PlayerYoikoData data) {
        data.dailyStreak = data.dailyStreak == Integer.MAX_VALUE
                ? Integer.MAX_VALUE : Math.max(0, data.dailyStreak) + 1;
    }

    private static DailyReward rewardForStreak(int streak) {
        if (dailyRewards.isEmpty()) {
            return new DailyReward(1, List.of());
        }
        int dayIndex = Math.floorMod(streak - 1, STREAK_PREVIEW_DAYS);
        return weeklyRewardForDay(dayIndex);
    }

    private static DailyReward weeklyRewardForDay(int dayIndex) {
        if (dailyRewards.isEmpty()) {
            return new DailyReward(dayIndex + 1, List.of());
        }
        if (dailyRewards.size() <= STREAK_PREVIEW_DAYS) {
            return dailyRewards.get(Math.floorMod(dayIndex, dailyRewards.size()));
        }

        int groupSize = Math.max(1, (int) Math.ceil(dailyRewards.size() / (double) STREAK_PREVIEW_DAYS));
        int start = Math.min(dayIndex * groupSize, dailyRewards.size() - 1);
        int end = Math.min(start + groupSize, dailyRewards.size());
        long seed = weeklyPeriodStart(System.currentTimeMillis()).toEpochDay() * 31L + dayIndex;
        Random random = new Random(seed);
        return dailyRewards.get(start + random.nextInt(Math.max(1, end - start)));
    }

    private static void grant(ServerPlayer player, List<ItemStack> rewards) {
        for (ItemStack stack : rewards) {
            ItemStack copy = stack.copy();
            if (RelicManager.absorbInternalMaterial(player, copy)) {
                continue;
            }
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
        }
    }

    private static DailyMailItems dailyMailItems(List<ItemStack> rewards) {
        return new DailyMailItems(copyItems(rewards), false);
    }

    private static ItemStack firstItem(List<ItemStack> rewards) {
        for (ItemStack stack : rewards) {
            if (!stack.isEmpty()) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> copyItems(List<ItemStack> rewards) {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : rewards) {
            if (!stack.isEmpty()) {
                copies.add(stack.copy());
            }
        }
        return List.copyOf(copies);
    }

    private static ZoneId zone() {
        return YoikoResetClock.zone();
    }

    private static long periodIndex(long millis, String mode) {
        return "weekly".equalsIgnoreCase(mode) ? weeklyPeriodStart(millis).toEpochDay() : dailyPeriodDate(millis).toEpochDay();
    }

    private static String dailyClaimKey(long millis) {
        String mode = YoikoCommonConfig.DAILY_RESET_MODE.get();
        if ("cooldown".equalsIgnoreCase(mode)) {
            return "cooldown:" + millis;
        }
        return mode.toLowerCase(Locale.ROOT) + ":" + periodIndex(millis, mode);
    }

    private static long periodDistance(String mode, long fromMillis, long toMillis) {
        if ("weekly".equalsIgnoreCase(mode)) {
            return ChronoUnit.WEEKS.between(weeklyPeriodStart(fromMillis), weeklyPeriodStart(toMillis));
        }
        return dailyPeriodDate(toMillis).toEpochDay() - dailyPeriodDate(fromMillis).toEpochDay();
    }

    private static LocalDate dailyPeriodDate(long millis) {
        return YoikoResetClock.dailyPeriodDate(millis);
    }

    private static LocalDate weeklyPeriodStart(long millis) {
        ZonedDateTime time = Instant.ofEpochMilli(millis).atZone(zone());
        DayOfWeek resetDay = resetDay();
        LocalDate date = time.toLocalDate();
        int daysSinceResetDay = Math.floorMod(date.getDayOfWeek().getValue() - resetDay.getValue(), 7);
        LocalDate resetDate = date.minusDays(daysSinceResetDay);
        ZonedDateTime resetAt = resetDate.atTime(YoikoCommonConfig.DAILY_RESET_HOUR.get(), 0).atZone(zone());
        return time.isBefore(resetAt) ? resetDate.minusWeeks(1) : resetDate;
    }

    private static DayOfWeek resetDay() {
        try {
            return DayOfWeek.valueOf(YoikoCommonConfig.DAILY_RESET_WEEKDAY.get().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return DayOfWeek.MONDAY;
        }
    }

    private static String nextClaimText(PlayerYoikoData data) {
        if (canClaimDaily(data)) {
            return "now";
        }
        String mode = YoikoCommonConfig.DAILY_RESET_MODE.get();
        if ("cooldown".equalsIgnoreCase(mode)) {
            long cooldown = YoikoCommonConfig.DAILY_COOLDOWN_HOURS.get() * 60L * 60L * 1000L;
            return Instant.ofEpochMilli(data.lastDailyRewardClaimAt + cooldown).atZone(zone()).format(RESET_FORMAT);
        }
        if ("weekly".equalsIgnoreCase(mode)) {
            LocalDate next = weeklyPeriodStart(System.currentTimeMillis()).plusWeeks(1);
            return next.atTime(YoikoCommonConfig.DAILY_RESET_HOUR.get(), 0).atZone(zone()).format(RESET_FORMAT);
        }
        LocalDate next = dailyPeriodDate(System.currentTimeMillis()).plusDays(1);
        return next.atTime(YoikoCommonConfig.DAILY_RESET_HOUR.get(), 0).atZone(zone()).format(RESET_FORMAT);
    }

    private static void ensureFile() {
        try {
            Files.createDirectories(REWARD_FILE.getParent());
            if (Files.notExists(REWARD_FILE)) {
                writeDefaultFile();
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to create reward config file.", exception);
        }
    }

    private static JsonObject ensureRewardConfigRoot(JsonObject root) {
        JsonObject config = root == null ? new JsonObject() : root;
        boolean changed = root == null;

        JsonElement configuredRarityWeights = config.get(DAILY_BONUS_RARITY_WEIGHTS_KEY);
        if (!validDailyBonusRarityWeights(configuredRarityWeights)) {
            if (configuredRarityWeights != null) {
                YoikoServerCore.LOGGER.warn(
                        "rewards.json {} must contain positive, strictly descending weights from common to mythic; "
                                + "restoring defaults.",
                        DAILY_BONUS_RARITY_WEIGHTS_KEY
                );
            }
            config.add(DAILY_BONUS_RARITY_WEIGHTS_KEY, defaultDailyBonusRarityWeightsJson());
            changed = true;
        }
        if (!config.has("dailyRewards") || !config.get("dailyRewards").isJsonArray()) {
            config.add("dailyRewards", defaultDailyRewardsJson());
            changed = true;
        }
        if (!config.has("firstLoginRewards") || !config.get("firstLoginRewards").isJsonArray()) {
            config.add("firstLoginRewards", defaultFirstLoginRewardsJson());
            changed = true;
        }
        if (!config.has("dailyBonusRewards")
                || !config.get("dailyBonusRewards").isJsonArray()
                || config.getAsJsonArray("dailyBonusRewards").isEmpty()) {
            config.add("dailyBonusRewards", defaultDailyBonusRewardsJson());
            changed = true;
        } else {
            JsonArray rewards = config.getAsJsonArray("dailyBonusRewards");
            if (removeUnavailableDailyBonusCosmeticRewards(rewards)) {
                changed = true;
            }
            if (removeNonCosmeticHighRarityDailyBonusRewards(rewards)) {
                changed = true;
            }
            if (normalizeDailyBonusRewardRarities(rewards)) {
                changed = true;
            }
            if (appendDefaultDailyBonusCosmeticRewards(rewards)) {
                changed = true;
            }
        }
        if (changed) {
            writeRoot(config);
        }
        return config;
    }

    private static void writeDefaultFile() {
        writeRoot(defaultRewardConfig());
    }

    private static JsonObject defaultRewardConfig() {
        JsonObject root = new JsonObject();
        root.add(DAILY_BONUS_RARITY_WEIGHTS_KEY, defaultDailyBonusRarityWeightsJson());
        root.add("dailyRewards", defaultDailyRewardsJson());
        root.add("firstLoginRewards", defaultFirstLoginRewardsJson());
        root.add("dailyBonusRewards", defaultDailyBonusRewardsJson());
        return root;
    }

    private static EnumMap<DailyBonusRarity, Double> defaultDailyBonusRarityWeights() {
        EnumMap<DailyBonusRarity, Double> weights = new EnumMap<>(DailyBonusRarity.class);
        weights.put(DailyBonusRarity.COMMON, 55.0D);
        weights.put(DailyBonusRarity.UNCOMMON, 22.0D);
        weights.put(DailyBonusRarity.RARE, 12.0D);
        weights.put(DailyBonusRarity.EPIC, 6.0D);
        weights.put(DailyBonusRarity.LEGENDARY, 3.5D);
        weights.put(DailyBonusRarity.MYTHIC, 1.5D);
        return weights;
    }

    private static JsonObject defaultDailyBonusRarityWeightsJson() {
        JsonObject object = new JsonObject();
        for (var entry : defaultDailyBonusRarityWeights().entrySet()) {
            object.addProperty(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return object;
    }

    private static EnumMap<DailyBonusRarity, Double> dailyBonusRarityWeightsFromJson(JsonElement element) {
        if (!validDailyBonusRarityWeights(element)) {
            return defaultDailyBonusRarityWeights();
        }
        JsonObject object = element.getAsJsonObject();
        EnumMap<DailyBonusRarity, Double> weights = new EnumMap<>(DailyBonusRarity.class);
        for (DailyBonusRarity rarity : DailyBonusRarity.values()) {
            weights.put(rarity, object.get(rarity.name().toLowerCase(Locale.ROOT)).getAsDouble());
        }
        return weights;
    }

    private static boolean validDailyBonusRarityWeights(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        JsonObject object = element.getAsJsonObject();
        double previous = Double.POSITIVE_INFINITY;
        try {
            for (DailyBonusRarity rarity : DailyBonusRarity.values()) {
                String key = rarity.name().toLowerCase(Locale.ROOT);
                if (!object.has(key)) {
                    return false;
                }
                double weight = object.get(key).getAsDouble();
                if (!Double.isFinite(weight) || weight <= 0.0D || weight >= previous) {
                    return false;
                }
                previous = weight;
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static JsonArray defaultDailyRewardsJson() {
        JsonArray daily = new JsonArray();
        daily.add(reward(1, item("cobblemon:poke_ball", 10)));
        daily.add(reward(2, item("cobblemon:great_ball", 10)));
        daily.add(reward(3, item("cobblemon:ultra_ball", 5)));
        daily.add(reward(4, item("minecraft:experience_bottle", 16)));
        daily.add(reward(5, item("yoiko_core:all_pokemon_gacha_ticket", 1)));
        daily.add(reward(6, item("yoiko_core:relic_upgrade_crystal", 1)));
        daily.add(reward(7, item("yoiko_core:shiny_all_pokemon_gacha_ticket", 1), item("yoiko_core:relic_scrap", 25)));
        return daily;
    }

    private static JsonArray defaultFirstLoginRewardsJson() {
        JsonArray first = new JsonArray();
        first.add(item("cobblemon:poke_ball", 32));
        first.add(item("yoiko_core:all_pokemon_gacha_ticket", 3));
        first.add(item("yoiko_core:relic_gacha_ticket", 1));
        return first;
    }

    private static JsonArray defaultDailyBonusRewardsJson() {
        JsonArray bonus = new JsonArray();
        bonus.add(dailyItem("cobblemon:poke_ball", 8, DailyBonusRarity.COMMON, 2400));
        bonus.add(dailyItem("cobblemon:great_ball", 6, DailyBonusRarity.COMMON, 1900));
        bonus.add(dailyItem("cobblemon:ultra_ball", 3, DailyBonusRarity.COMMON, 1200));
        bonus.add(dailyItem("minecraft:experience_bottle", 8, DailyBonusRarity.COMMON, 1800));
        bonus.add(dailyItem("yoiko_core:all_pokemon_gacha_ticket", 1, DailyBonusRarity.UNCOMMON, 350));
        bonus.add(dailyItem("yoiko_core:relic_scrap", 10, DailyBonusRarity.UNCOMMON, 500));
        bonus.add(dailyItem("yoiko_core:relic_upgrade_crystal", 1, DailyBonusRarity.RARE, 120));
        bonus.add(dailyItem(RELIC_SCRAP_ID, PlayerYoikoData.RELIC_PROTECTION_SCRAP_COST,
                DailyBonusRarity.EPIC, 35));
        appendDefaultDailyBonusCosmeticRewards(bonus);
        return bonus;
    }

    private static List<DailyBonusReward> dailyBonusRewardsFromJson(JsonElement element) {
        List<DailyBonusReward> rewards = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return rewards;
        }
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonObject()) {
                continue;
            }
            DailyBonusReward reward = dailyBonusRewardFromJson(child.getAsJsonObject());
            if (reward != null) {
                rewards.add(reward);
            }
        }
        return rewards;
    }

    private static int appendRuntimeDailyBonusCosmeticRewards(List<DailyBonusReward> rewards) {
        Set<String> configuredCosmetics = new HashSet<>();
        for (DailyBonusReward reward : rewards) {
            if (!reward.cosmeticId().isBlank()) {
                configuredCosmetics.add(reward.cosmeticId());
            }
        }

        int added = 0;
        for (String cosmeticId : defaultDailyBonusParticleCosmeticIds()) {
            if (!configuredCosmetics.add(cosmeticId)) {
                continue;
            }
            CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
            if (cosmetic == null) {
                continue;
            }
            rewards.add(new DailyBonusReward(
                    DAILY_BONUS_COSMETIC_PREFIX + cosmeticId,
                    cosmeticPreviewStack(cosmetic),
                    1,
                    cosmeticDailyRarity(cosmetic),
                    1
            ));
            added++;
        }
        return added;
    }

    private static void logDailyBonusPoolSummary() {
        EnumMap<DailyBonusRarity, Integer> rewardCounts = new EnumMap<>(DailyBonusRarity.class);
        EnumMap<DailyBonusRarity, Integer> cosmeticCounts = new EnumMap<>(DailyBonusRarity.class);
        for (DailyBonusRarity rarity : DailyBonusRarity.values()) {
            rewardCounts.put(rarity, 0);
            cosmeticCounts.put(rarity, 0);
        }
        for (DailyBonusReward reward : dailyBonusRewards) {
            rewardCounts.merge(reward.rarity(), 1, Integer::sum);
            if (!reward.cosmeticId().isBlank()) {
                cosmeticCounts.merge(reward.rarity(), 1, Integer::sum);
            }
        }

        YoikoServerCore.LOGGER.info(
                "Loaded daily bonus pools with rarity-first rolls: counts={}, configured weights={}. "
                        + "Owned cosmetic duplicates convert to gems.",
                rewardCounts,
                dailyBonusRarityWeights
        );
        if (cosmeticCounts.get(DailyBonusRarity.LEGENDARY) == 0
                || cosmeticCounts.get(DailyBonusRarity.MYTHIC) == 0) {
            YoikoServerCore.LOGGER.warn(
                    "Daily bonus high-rarity pool is incomplete: legendary cosmetics={}, mythic cosmetics={}.",
                    cosmeticCounts.get(DailyBonusRarity.LEGENDARY),
                    cosmeticCounts.get(DailyBonusRarity.MYTHIC)
            );
        }
    }

    private static DailyBonusReward dailyBonusRewardFromJson(JsonObject object) {
        if (object.has("cosmetic")) {
            String cosmeticId = object.get("cosmetic").getAsString();
            CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
            if (cosmetic == null || CosmeticManager.isAdminOnlyCosmetic(cosmeticId)) {
                return null;
            }
            DailyBonusRarity rarity = cosmeticDailyRarity(cosmetic);
            int weight = integer(object, "weight", defaultDailyBonusWeight(rarity));
            return new DailyBonusReward(DAILY_BONUS_COSMETIC_PREFIX + cosmeticId,
                    cosmeticPreviewStack(cosmetic), 1, rarity, weight);
        }

        ItemStack stack = RewardItemSerializer.previewFromJson(object);
        if (stack.isEmpty()) {
            return null;
        }
        int count = RewardItemSerializer.configuredCount(object);
        DailyBonusRarity rarity = object.has("rarity")
                ? DailyBonusRarity.fromString(object.get("rarity").getAsString())
                : DailyBonusRarity.fromMinecraft(stack.getRarity());
        if (rarity == DailyBonusRarity.LEGENDARY || rarity == DailyBonusRarity.MYTHIC) {
            YoikoServerCore.LOGGER.warn(
                    "Daily bonus legendary and mythic pools only accept cosmetics; ignoring item reward {}.",
                    stack.getItem()
            );
            return null;
        }
        int weight = integer(object, "weight", defaultDailyBonusWeight(rarity));
        return new DailyBonusReward("", stack, count, rarity, weight);
    }

    private static ItemStack cosmeticPreviewStack(CosmeticData cosmetic) {
        ItemStack stack = new ItemStack(Items.FIREWORK_STAR);
        stack.set(DataComponents.CUSTOM_NAME,
                localizedCosmeticName(cosmetic).copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        return stack;
    }

    private static Component localizedCosmeticName(CosmeticData cosmetic) {
        if (defaultDailyBonusParticleCosmeticIds().contains(cosmetic.id())) return Component.translatable("yoiko_core.cosmetic." + cosmetic.id() + ".name");
        if (cosmetic.displayName().startsWith("yoiko_core.")) return Component.translatable(cosmetic.displayName());
        return Component.literal(cosmetic.displayName());
    }

    private static int defaultDailyBonusWeight(DailyBonusRarity rarity) {
        return switch (rarity) {
            case COMMON -> 1000;
            case UNCOMMON -> 300;
            case RARE -> 90;
            case EPIC -> 25;
            case LEGENDARY -> 5;
            case MYTHIC -> 1;
        };
    }

    private static boolean normalizeDailyBonusRewardRarities(JsonArray rewards) {
        boolean changed = false;
        for (JsonElement element : rewards) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject reward = element.getAsJsonObject();
            if (reward.has("cosmetic")) {
                CosmeticData cosmetic = CosmeticManager.get(string(reward, "cosmetic", ""));
                DailyBonusRarity expected = cosmeticDailyRarity(cosmetic);
                DailyBonusRarity configured = DailyBonusRarity.fromString(
                        string(reward, "rarity", DailyBonusRarity.COMMON.name())
                );
                if (!reward.has("rarity") || configured != expected) {
                    reward.addProperty("rarity", expected.name().toLowerCase(Locale.ROOT));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean removeNonCosmeticHighRarityDailyBonusRewards(JsonArray rewards) {
        boolean changed = false;
        for (int i = rewards.size() - 1; i >= 0; i--) {
            JsonElement element = rewards.get(i);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject reward = element.getAsJsonObject();
            if (reward.has("cosmetic")) {
                continue;
            }
            DailyBonusRarity rarity = DailyBonusRarity.fromString(
                    string(reward, "rarity", DailyBonusRarity.COMMON.name())
            );
            if (rarity == DailyBonusRarity.LEGENDARY || rarity == DailyBonusRarity.MYTHIC) {
                rewards.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean removeUnavailableDailyBonusCosmeticRewards(JsonArray rewards) {
        boolean changed = false;
        for (int i = rewards.size() - 1; i >= 0; i--) {
            JsonElement element = rewards.get(i);
            if (!element.isJsonObject() || !element.getAsJsonObject().has("cosmetic")) {
                continue;
            }
            String cosmeticId = string(element.getAsJsonObject(), "cosmetic", "");
            if (CosmeticManager.get(cosmeticId) == null || CosmeticManager.isAdminOnlyCosmetic(cosmeticId)) {
                rewards.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean appendDefaultDailyBonusCosmeticRewards(JsonArray rewards) {
        boolean changed = false;
        for (String cosmeticId : defaultDailyBonusParticleCosmeticIds()) {
            if (!hasCosmeticReward(rewards, cosmeticId)) {
                CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
                rewards.add(dailyCosmetic(cosmeticId, cosmeticDailyRarity(cosmetic), 1));
                changed = true;
            }
        }
        return changed;
    }

    private static List<String> defaultDailyBonusParticleCosmeticIds() {
        List<String> ids = new ArrayList<>();
        for (String cosmeticId : CosmeticManager.ids()) {
            CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
            if (cosmetic != null
                    && cosmetic.type() == CosmeticType.PARTICLE
                    && cosmetic.requiredRank().isBlank()
                    && !CosmeticManager.isAdminOnlyCosmetic(cosmeticId)) {
                ids.add(cosmeticId);
            }
        }
        return ids;
    }

    private static boolean hasCosmeticReward(JsonArray rewards, String cosmeticId) {
        for (JsonElement element : rewards) {
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("cosmetic") && cosmeticId.equals(string(object, "cosmetic", ""))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static DailyBonusRarity cosmeticDailyRarity(CosmeticData cosmetic) {
        return cosmetic != null && cosmetic.rarity() == CosmeticRarity.LEGENDARY
                ? DailyBonusRarity.LEGENDARY
                : DailyBonusRarity.MYTHIC;
    }

    private static void writeRoot(JsonObject root) {
        try {
            Files.createDirectories(REWARD_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(REWARD_FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to write default rewards.", exception);
        }
    }

    private static JsonObject reward(int day, JsonObject... items) {
        JsonObject reward = new JsonObject();
        reward.addProperty("day", day);
        JsonArray array = new JsonArray();
        for (JsonObject item : items) {
            array.add(item);
        }
        reward.add("items", array);
        return reward;
    }

    private static JsonObject item(String id, int count) {
        JsonObject item = new JsonObject();
        item.addProperty("item", id);
        item.addProperty("count", count);
        return item;
    }

    private static JsonObject dailyItem(String id, int count, DailyBonusRarity rarity, int weight) {
        JsonObject item = item(id, count);
        item.addProperty("rarity", rarity.name().toLowerCase(Locale.ROOT));
        item.addProperty("weight", weight);
        return item;
    }

    private static JsonObject dailyCosmetic(String id, DailyBonusRarity rarity, int weight) {
        JsonObject reward = new JsonObject();
        reward.addProperty("cosmetic", id);
        reward.addProperty("rarity", rarity.name().toLowerCase(Locale.ROOT));
        reward.addProperty("weight", weight);
        return reward;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
    }

    private record DailyReward(int day, List<ItemStack> items) {
    }

    private record DailyMailItems(List<ItemStack> items, boolean bonusApplied) {
    }

    private record DailyBonusReward(String key, ItemStack preview, int count, DailyBonusRarity rarity, int weight) {
        private DailyBonusReward {
            preview = preview.copy();
            preview.setCount(1);
            if (count < 1 || count > RewardItemSerializer.MAX_CONFIGURED_REWARD_COUNT) {
                throw new IllegalArgumentException("Invalid daily bonus reward count: " + count);
            }
            weight = Math.max(1, weight);
        }

        private String cosmeticId() {
            return key.startsWith(DAILY_BONUS_COSMETIC_PREFIX)
                    ? key.substring(DAILY_BONUS_COSMETIC_PREFIX.length())
                    : "";
        }

    }

    private record DailyBonusGrantResult(boolean convertedToGems, long gems) {
        private static final DailyBonusGrantResult NONE = new DailyBonusGrantResult(false, 0L);
    }

    public record DailyBonusState(
            List<ItemStack> boxes,
            List<Integer> counts,
            List<String> rarities,
            List<DailyBonusCosmeticVisual> cosmeticVisuals,
            List<DailyBonusChance> chances,
            int openedMask,
            int openedCount,
            int openLimit,
            int lastOpened,
            int keys,
            int maxKeys,
            String nextKeyText
    ) {
    }

    public record DailyBonusChance(String rarity, double percent, boolean allOwned) {
    }

    public record DailyBonusCosmeticVisual(String id, String type, String particleCategory) {
        private static final DailyBonusCosmeticVisual EMPTY = new DailyBonusCosmeticVisual("", "", "NONE");
    }
}
