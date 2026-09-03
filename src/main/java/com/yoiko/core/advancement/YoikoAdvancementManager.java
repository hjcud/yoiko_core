package com.yoiko.core.advancement;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.economy.CurrencyManager;
import com.yoiko.core.economy.CurrencyType;
import com.yoiko.core.economy.EconomyManager;
import com.yoiko.core.gacha.GachaResult;
import com.yoiko.core.gacha.GachaRarity;
import com.yoiko.core.mail.MailboxManager;
import com.yoiko.core.rank.RankManager;
import com.yoiko.core.relic.RelicAppraisalCategory;
import com.yoiko.core.relic.RelicManager;
import com.yoiko.core.relic.RelicRarity;
import com.yoiko.core.treasure.TreasureRabbitVariant;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative progress, one-time reward delivery, and login synchronization for the Yoiko tab. */
public final class YoikoAdvancementManager {
    private static final String PATH = "yoiko/";
    private static final int STANDARD_PROGRESS_SEGMENTS = 10;

    private YoikoAdvancementManager() {
    }

    public static void onLogin(ServerPlayer player) {
        grant(player, "root");
        ServerYoikoSavedData saved = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = saved.getOrCreate(player);
        boolean changed = false;
        changed |= atLeast(data, "pokemon_gacha", data.gachaStats.totalRolls);
        changed |= atLeast(data, "shiny_gacha", data.gachaStats.shinyRolls);
        changed |= atLeast(data, "legendary_gacha", data.gachaStats.legendaryRolls);
        changed |= atLeast(data, "market_sales", data.marketplaceSales.size());
        changed |= atLeast(data, "daily_streak", data.dailyStreak);
        if (!data.ownedRelics.isEmpty()) {
            changed |= atLeast(data, "relic_unsealed", 1);
            grant(player, "first_relic");
        }
        int equipped = (int) data.equippedRelics.stream().filter(value -> !value.isBlank()).count();
        if (equipped >= PlayerYoikoData.RELIC_EQUIP_SLOT_COUNT) {
            grant(player, "full_loadout");
        }
        for (PlayerYoikoData.RelicInstance relic : data.ownedRelics) {
            grantRarity(player, relic.rarity);
            if (!relic.secondaryEffect.isBlank()) {
                changed |= atLeast(data, "secondary_effect", 1);
                grant(player, "secondary_effect");
            }
            if (relic.level > 0) {
                changed |= atLeast(data, "upgrade_once", 1);
                grant(player, "upgrade_once");
            }
            recordUpgradeMilestones(player, relic.level);
        }
        syncDailyBonusProgress(player, data);
        syncCosmeticProgress(player, data);
        recordCurrencyBalances(player);
        evaluateCounters(player, data);
        if (changed) {
            saved.markDirty(player);
        }
    }

    public static void recordRelicAppraisal(ServerPlayer player, RelicManager.PendingRoll roll,
                                             PlayerYoikoData.RelicInstance instance) {
        PlayerYoikoData data = increment(player, "relic_unsealed", 1);
        evaluateRelicCount(player, count(data, "relic_unsealed"));
        grantRarity(player, instance.rarity);
        if (!instance.secondaryEffect.isBlank()) {
            set(player, "secondary_effect", 1);
            grant(player, "secondary_effect");
        }
        if (roll.categoryFocused() && roll.category() != RelicAppraisalCategory.ALL) {
            set(player, "focused_appraisal", 1);
            grant(player, "focused_appraisal");
            set(player, "focus_" + roll.category().id(), 1);
            syncCategoryScholar(player, data);
        }
    }

    public static void recordDismantle(ServerPlayer player, int amount) {
        PlayerYoikoData data = increment(player, "relic_dismantled", Math.max(0, amount));
        syncProgress(player, "dismantle_10", count(data, "relic_dismantled"), 10, 10);
    }

    public static void recordUpgrade(ServerPlayer player, int newLevel) {
        set(player, "upgrade_once", 1);
        grant(player, "upgrade_once");
        recordUpgradeMilestones(player, newLevel);
    }

    public static void recordEquip(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        long equipped = data.equippedRelics.stream().filter(value -> !value.isBlank()).count();
        if (equipped >= PlayerYoikoData.RELIC_EQUIP_SLOT_COUNT) {
            grant(player, "full_loadout");
        }
    }

    public static void recordMirrorDecoy(ServerPlayer player) {
        set(player, "mirror_decoy", 1);
        grant(player, "mirror_decoy");
    }

    public static void recordRabbitCatch(ServerPlayer finisher, TreasureRabbitVariant variant,
                                         int contributors) {
        PlayerYoikoData data = increment(finisher, "rabbit_caught", 1);
        grant(finisher, "first_rabbit");
        syncRabbitProgress(finisher, data);
        switch (variant) {
            case RADIANT -> {
                set(finisher, "radiant_rabbit", 1);
                grant(finisher, "radiant_rabbit");
            }
            case MIRROR -> {
                set(finisher, "mirror_rabbit", 1);
                grant(finisher, "mirror_rabbit");
            }
            case CROWN -> {
                set(finisher, "crown_rabbit", 1);
                grant(finisher, "crown_rabbit");
                if (contributors >= 3) {
                    set(finisher, "crown_party", 1);
                    grant(finisher, "crown_party");
                }
            }
            default -> { }
        }
    }

    public static void recordCrownParticipant(ServerPlayer player, int contributors) {
        PlayerYoikoData data = increment(player, "rabbit_caught", 1);
        grant(player, "first_rabbit");
        syncRabbitProgress(player, data);
        set(player, "crown_participant", 1);
        grant(player, "crown_participant");
        if (contributors >= 3) {
            set(player, "crown_party", 1);
            grant(player, "crown_party");
        }
    }

    /** Grants the Crown Breaker achievement/title only to the player whose counted hit finishes the boss. */
    public static void recordCrownFinisher(ServerPlayer player, int contributors) {
        set(player, "crown_rabbit", 1);
        grant(player, "crown_rabbit");
        if (contributors >= 3) {
            set(player, "crown_party", 1);
            grant(player, "crown_party");
        }
    }

    public static void recordCrownParticipant(MinecraftServer server, java.util.UUID playerId,
                                              int contributors) {
        ServerYoikoSavedData saved = ServerYoikoSavedData.get(server);
        PlayerYoikoData data = saved.get(playerId);
        if (data == null) {
            return;
        }
        data.achievementCounters.merge("rabbit_caught", 1, Integer::sum);
        data.achievementCounters.put("crown_participant", 1);
        if (contributors >= 3) {
            data.achievementCounters.put("crown_party", 1);
        }
        saved.markDirty(playerId);
    }

    public static void recordPokemonGacha(ServerPlayer player, GachaResult result) {
        PlayerYoikoData data = increment(player, "pokemon_gacha", 1);
        int rolls = count(data, "pokemon_gacha");
        if (rolls >= 1) grant(player, "pokemon_gacha_first");
        syncGachaProgress(player, data);
        if (result.shiny()) {
            set(player, "shiny_gacha", 1);
            grant(player, "shiny_gacha");
        }
        if (result.rarity() == GachaRarity.LEGENDARY) {
            set(player, "legendary_gacha", 1);
            grant(player, "legendary_gacha");
        }
        if (result.pityForced()) {
            set(player, "pity_gacha", 1);
            grant(player, "pity_gacha");
        }
    }

    public static void recordDailyStreak(ServerPlayer player, int streak) {
        set(player, "daily_streak", streak);
        syncProgress(player, "daily_streak_7", streak, 7, 7);
        syncProgress(player, "daily_streak_30", streak, 30, STANDARD_PROGRESS_SEGMENTS);
    }

    public static void recordDailyBonusOpen(ServerPlayer player) {
        PlayerYoikoData data = increment(player, "daily_bonus_opened", 1);
        syncDailyBonusProgress(player, data);
    }

    public static void recordCurrencyBalances(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        if (data.gold >= 2_000L) {
            set(player, "gold_10000", 1);
            grant(player, "gold_10000");
        }
        if (data.gold >= 200_000L) {
            set(player, "gold_1000000", 1);
            grant(player, "gold_1000000");
        }
        if (data.gems >= 10L) {
            set(player, "gems_100", 1);
            grant(player, "gems_100");
        }
        if (data.gems >= 100L) {
            set(player, "gems_1000", 1);
            grant(player, "gems_1000");
        }
    }

    public static void recordCosmeticOwnership(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        syncCosmeticProgress(player, data);
    }

    public static void recordParticleGacha(ServerPlayer player) {
        set(player, "particle_gacha_first", 1);
        grant(player, "particle_gacha_first");
        recordCosmeticOwnership(player);
    }

    public static void recordMarketListing(ServerPlayer player) {
        set(player, "market_listing", 1);
        grant(player, "market_listing");
    }

    public static void recordMarketPurchase(ServerPlayer player) {
        set(player, "market_purchase", 1);
        grant(player, "market_purchase");
    }

    public static void recordMarketSale(ServerPlayer player) {
        set(player, "market_sale", 1);
        grant(player, "market_sale");
    }

    private static void evaluateCounters(ServerPlayer player, PlayerYoikoData data) {
        evaluateRelicCount(player, count(data, "relic_unsealed"));
        syncProgress(player, "dismantle_10", count(data, "relic_dismantled"), 10, 10);
        syncCategoryScholar(player, data);
        syncRabbitProgress(player, data);
        syncGachaProgress(player, data);
        if (count(data, "focused_appraisal") > 0) grant(player, "focused_appraisal");
        if (count(data, "secondary_effect") > 0) grant(player, "secondary_effect");
        if (count(data, "upgrade_once") > 0) grant(player, "upgrade_once");
        if (count(data, "legendary_relic") > 0) grant(player, "legendary_relic");
        if (count(data, "mystic_relic") > 0) grant(player, "mystic_relic");
        if (count(data, "radiant_relic") > 0) grant(player, "radiant_relic");
        if (count(data, "shiny_gacha") > 0) grant(player, "shiny_gacha");
        if (count(data, "legendary_gacha") > 0) grant(player, "legendary_gacha");
        if (count(data, "pity_gacha") > 0) grant(player, "pity_gacha");
        if (count(data, "radiant_rabbit") > 0) grant(player, "radiant_rabbit");
        if (count(data, "mirror_rabbit") > 0) grant(player, "mirror_rabbit");
        if (count(data, "mirror_decoy") > 0) grant(player, "mirror_decoy");
        if (count(data, "crown_rabbit") > 0) grant(player, "crown_rabbit");
        if (count(data, "market_listing") > 0) grant(player, "market_listing");
        if (count(data, "market_purchase") > 0) grant(player, "market_purchase");
        if (count(data, "market_sale") > 0 || count(data, "market_sales") > 0) grant(player, "market_sale");
        if (count(data, "crown_participant") > 0) {
            grant(player, "crown_participant");
        }
        if (count(data, "crown_party") > 0) grant(player, "crown_party");
        if (count(data, "particle_gacha_first") > 0) grant(player, "particle_gacha_first");
        recordDailyStreak(player, Math.max(data.dailyStreak, count(data, "daily_streak")));
    }

    private static void evaluateRelicCount(ServerPlayer player, int count) {
        if (count >= 1) grant(player, "first_relic");
        syncProgress(player, "appraiser_10", count, 10, 10);
        syncProgress(player, "appraiser_50", count, 50, STANDARD_PROGRESS_SEGMENTS);
        syncProgress(player, "appraiser_100", count, 100, STANDARD_PROGRESS_SEGMENTS);
        syncProgress(player, "appraiser_1000", count, 1_000, STANDARD_PROGRESS_SEGMENTS);
    }

    private static void grantRarity(ServerPlayer player, RelicRarity rarity) {
        switch (rarity) {
            case LEGENDARY -> {
                set(player, "legendary_relic", 1);
                grant(player, "legendary_relic");
            }
            case MYSTIC -> {
                set(player, "mystic_relic", 1);
                grant(player, "mystic_relic");
            }
            case RADIANT -> {
                set(player, "radiant_relic", 1);
                grant(player, "radiant_relic");
            }
            default -> { }
        }
    }

    private static void syncRabbitProgress(ServerPlayer player, PlayerYoikoData data) {
        int rabbits = count(data, "rabbit_caught");
        if (rabbits >= 1) grant(player, "first_rabbit");
        syncProgress(player, "rabbits_10", rabbits, 10, 10);
        syncProgress(player, "rabbits_50", rabbits, 50, STANDARD_PROGRESS_SEGMENTS);
    }

    private static void syncGachaProgress(ServerPlayer player, PlayerYoikoData data) {
        int rolls = count(data, "pokemon_gacha");
        if (rolls >= 1) grant(player, "pokemon_gacha_first");
        syncProgress(player, "pokemon_gacha_10", rolls, 10, 10);
        syncProgress(player, "pokemon_gacha_100", rolls, 100, STANDARD_PROGRESS_SEGMENTS);
    }

    private static void syncDailyBonusProgress(ServerPlayer player, PlayerYoikoData data) {
        int opened = count(data, "daily_bonus_opened");
        if (opened >= 1) grant(player, "daily_bonus_first");
        syncProgress(player, "daily_bonus_30", opened, 30, STANDARD_PROGRESS_SEGMENTS);
    }

    private static void syncCosmeticProgress(ServerPlayer player, PlayerYoikoData data) {
        int owned = data.ownedCosmetics.size();
        if (owned >= 1) grant(player, "cosmetic_first");
        syncProgress(player, "cosmetic_10", owned, 10, 10);
    }

    private static void syncCategoryScholar(ServerPlayer player, PlayerYoikoData data) {
        for (RelicAppraisalCategory category : RelicAppraisalCategory.values()) {
            if (category != RelicAppraisalCategory.ALL && count(data, "focus_" + category.id()) > 0) {
                awardCriterion(player, PATH + "category_scholar", category.id(),
                        "category_scholar", titleKey("category_scholar"), false);
            }
        }
    }

    private static void syncProgress(ServerPlayer player, String id, int value, int target, int segments) {
        int safeSegments = Math.max(1, segments);
        int earned = (int) Math.min(safeSegments,
                (long) Math.max(0, value) * safeSegments / Math.max(1, target));
        for (int step = 1; step <= earned; step++) {
            awardCriterion(player, PATH + id, "progress_" + step,
                    id, titleKey(id), false);
        }
    }

    private static void recordUpgradeMilestones(ServerPlayer player, int level) {
        if (level >= 5) grant(player, "upgrade_five");
        if (level >= 10) grant(player, "upgrade_ten");
    }

    private static PlayerYoikoData increment(ServerPlayer player, String counter, int amount) {
        ServerYoikoSavedData saved = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = saved.getOrCreate(player);
        if (amount > 0) {
            data.achievementCounters.put(counter, (int) Math.min(Integer.MAX_VALUE,
                    (long) count(data, counter) + amount));
            saved.markDirty(player);
        }
        return data;
    }

    private static void set(ServerPlayer player, String counter, int value) {
        ServerYoikoSavedData saved = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = saved.getOrCreate(player);
        if (atLeast(data, counter, value)) {
            saved.markDirty(player);
        }
    }

    private static boolean atLeast(PlayerYoikoData data, String counter, int value) {
        int safe = Math.max(0, value);
        if (safe <= count(data, counter)) {
            return false;
        }
        data.achievementCounters.put(counter, safe);
        return true;
    }

    private static int count(PlayerYoikoData data, String counter) {
        return Math.max(0, data.achievementCounters.getOrDefault(counter, 0));
    }

    private static void grant(ServerPlayer player, String id) {
        awardCriterion(player, PATH + id, "complete", id, titleKey(id), "root".equals(id));
    }

    public static void awardExternalCriterion(ServerPlayer player, String advancementPath,
                                              String criterion, String rewardId, String titleTranslationKey) {
        awardCriterion(player, advancementPath, criterion, rewardId, titleTranslationKey, false);
    }

    public static void syncExternalProgress(ServerPlayer player, String advancementPath,
                                            String rewardId, String titleTranslationKey,
                                            int value, int target, int segments) {
        int safeSegments = Math.max(1, segments);
        int earned = (int) Math.min(safeSegments,
                (long) Math.max(0, value) * safeSegments / Math.max(1, target));
        for (int step = 1; step <= earned; step++) {
            awardCriterion(player, advancementPath, "progress_" + step,
                    rewardId, titleTranslationKey, false);
        }
    }

    private static void awardCriterion(ServerPlayer player, String advancementPath, String criterion,
                                       String rewardId, String titleTranslationKey, boolean suppressReward) {
        ResourceLocation advancementId = ResourceLocation.fromNamespaceAndPath(
                YoikoServerCore.MODID, advancementPath);
        AdvancementHolder holder = player.server.getAdvancements().get(advancementId);
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            grantRankReward(player, rewardId, true);
            return;
        }
        player.getAdvancements().award(holder, criterion);
        if (!player.getAdvancements().getOrStartProgress(holder).isDone() || suppressReward) {
            return;
        }
        AdvancementReward reward = reward(rewardId);
        grantRankReward(player, rewardId, true);
        String encodedTitle = "yoiko_core.mail.advancement.title_named|@" + titleTranslationKey;
        String encodedMessage = reward.rankId().isBlank()
                ? "yoiko_core.mail.advancement.message_named|@" + titleTranslationKey
                : "yoiko_core.mail.advancement.message_named_title|@" + titleTranslationKey
                        + "|@" + rankNameKey(reward.rankId());
        String claimKey = "advancement:" + rewardId;
        boolean alreadyPending = MailboxManager.hasPending(player, "advancement", claimKey);
        boolean mailed = MailboxManager.sendSystemMail(player, "advancement", claimKey,
                encodedTitle, encodedMessage, List.of(), 0L, reward.gems());
        boolean grantedDirectly = false;
        if (!mailed && !alreadyPending && reward.gems() > 0L) {
            long added = CurrencyManager.add(player, CurrencyType.GEM, reward.gems());
            grantedDirectly = true;
            EconomyManager.recordEconomy(player, "ADVANCEMENT_GEMS_CREATED", added,
                    "source=advancement;advancement=" + rewardId + ";delivery=direct");
        }
        player.sendSystemMessage(Component.translatable(mailed || alreadyPending || !grantedDirectly
                        ? "yoiko_core.message.advancement.reward_sent_named"
                        : "yoiko_core.message.advancement.reward_granted_direct_named",
                        Component.translatable(titleTranslationKey))
                .withStyle(ChatFormatting.GOLD));
    }

    private static String titleKey(String id) {
        return "advancement.yoiko_core.yoiko." + id;
    }

    private static AdvancementReward reward(String id) {
        return new AdvancementReward(gemReward(id), rankReward(id));
    }

    private static long gemReward(String id) {
        return switch (id) {
            case "appraiser_1000", "radiant_relic", "radiant_rabbit", "crown_rabbit",
                    "turtle_golden_shell_mastery" -> 5L;
            case "appraiser_100", "rabbits_50", "pokemon_gacha_100", "daily_streak_30",
                    "mystic_relic", "mirror_rabbit", "category_scholar", "legendary_gacha",
                    "shiny_gacha", "turtle_blue_axolotl_friend" -> 3L;
            case "appraiser_50", "daily_streak_7", "upgrade_ten", "daily_bonus_30",
                    "gold_1000000", "gems_1000", "cosmetic_10", "crown_party",
                    "crown_participant", "turtle_hatch_10", "turtle_race_10",
                    "turtle_race_win" -> 2L;
            case "legendary_relic", "appraiser_10", "rabbits_10", "pokemon_gacha_10",
                    "pity_gacha", "full_loadout", "upgrade_five", "focused_appraisal",
                    "secondary_effect", "daily_bonus_first", "gold_10000", "gems_100",
                    "cosmetic_first", "particle_gacha_first", "turtle_hatch_first",
                    "turtle_race_first", "turtle_time_trial_first" -> 1L;
            default -> 1L;
        };
    }

    private static String rankReward(String id) {
        return switch (id) {
            case "radiant_rabbit" -> "title_radiant_tracker";
            case "mirror_rabbit" -> "title_mirror_hunter";
            case "crown_rabbit" -> "title_crown_breaker";
            case "mystic_relic" -> "title_myth_awakener";
            case "radiant_relic" -> "title_radiant_heir";
            case "turtle_golden_shell_mastery" -> "title_golden_shell_king";
            case "turtle_blue_axolotl_friend" -> "title_blue_miracle_friend";
            case "appraiser_1000" -> "title_master_appraiser";
            default -> "";
        };
    }

    private static void grantRankReward(ServerPlayer player, String rewardId, boolean notify) {
        String rankId = rankReward(rewardId);
        if (rankId.isBlank()) {
            return;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        if (data.ownedRanks.contains(rankId) || !RankManager.grant(player, rankId)) {
            return;
        }
        if (notify) {
            player.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.advancement.title_unlocked",
                    Component.translatable(rankNameKey(rankId))).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static String rankNameKey(String rankId) {
        return "yoiko_core.rank." + rankId + ".name";
    }

    private record AdvancementReward(long gems, String rankId) { }
}
