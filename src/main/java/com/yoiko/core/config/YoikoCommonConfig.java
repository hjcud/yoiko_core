package com.yoiko.core.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class YoikoCommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_COBBLEMON_RELIC_EVENTS = BUILDER
            .comment("Log Cobblemon relic event values before/after Yoiko bonuses for gameplay QA.")
            .define("debug.cobblemonRelicEvents", false);

    public static final ModConfigSpec.IntValue MENU_REQUEST_LIMIT = BUILDER
            .comment("Maximum authenticated non-market menu actions accepted per request window.")
            .defineInRange("menu.security.requestLimit", 30, 4, 200);
    public static final ModConfigSpec.IntValue MENU_REQUEST_WINDOW_MS = BUILDER
            .comment("Rate-limit window in milliseconds for non-market menu actions.")
            .defineInRange("menu.security.requestWindowMs", 2_000, 250, 10_000);
    public static final ModConfigSpec.IntValue MENU_RATE_WARNING_COOLDOWN_MS = BUILDER
            .comment("Minimum interval between non-market menu rate-limit warnings.")
            .defineInRange("menu.security.warningCooldownMs", 3_000, 500, 30_000);

    public static final ModConfigSpec.IntValue MARKET_REQUEST_LIMIT = BUILDER
            .comment("Maximum authenticated market actions accepted per request window.")
            .defineInRange("market.requestLimit", 24, 4, 200);
    public static final ModConfigSpec.IntValue MARKET_REQUEST_WINDOW_MS = BUILDER
            .comment("Rate-limit window in milliseconds for market actions.")
            .defineInRange("market.requestWindowMs", 2_000, 250, 10_000);
    public static final ModConfigSpec.IntValue MARKET_RATE_WARNING_COOLDOWN_MS = BUILDER
            .comment("Minimum interval between market rate-limit warning messages.")
            .defineInRange("market.warningCooldownMs", 3_000, 500, 30_000);

    public static final ModConfigSpec.IntValue ALL_COMMON_WEIGHT = BUILDER.defineInRange("gacha.all.commonWeight", 9000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue ALL_SUB_LEGENDARY_WEIGHT = BUILDER.defineInRange("gacha.all.subLegendaryWeight", 700, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue ALL_MYTHICAL_WEIGHT = BUILDER.defineInRange("gacha.all.mythicalWeight", 200, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue ALL_LEGENDARY_WEIGHT = BUILDER.defineInRange("gacha.all.legendaryWeight", 100, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue ALL_SHINY_CHANCE = BUILDER.defineInRange("gacha.all.shinyChance", 0.01D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue LEGENDARY_SUB_LEGENDARY_WEIGHT = BUILDER.defineInRange("gacha.legendary.subLegendaryWeight", 7000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue LEGENDARY_MYTHICAL_WEIGHT = BUILDER.defineInRange("gacha.legendary.mythicalWeight", 2000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue LEGENDARY_LEGENDARY_WEIGHT = BUILDER.defineInRange("gacha.legendary.legendaryWeight", 1000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue LEGENDARY_SHINY_CHANCE = BUILDER.defineInRange("gacha.legendary.shinyChance", 0.03D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue SHINY_ALL_COMMON_WEIGHT = BUILDER.defineInRange("gacha.shinyAll.commonWeight", 9000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue SHINY_ALL_SUB_LEGENDARY_WEIGHT = BUILDER.defineInRange("gacha.shinyAll.subLegendaryWeight", 700, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue SHINY_ALL_MYTHICAL_WEIGHT = BUILDER.defineInRange("gacha.shinyAll.mythicalWeight", 200, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue SHINY_ALL_LEGENDARY_WEIGHT = BUILDER.defineInRange("gacha.shinyAll.legendaryWeight", 100, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue GACHA_PITY_ENABLED = BUILDER
            .comment("Enable player-specific gacha pity counters.")
            .define("gacha.pity.enabled", true);
    public static final ModConfigSpec.IntValue GACHA_MYTHICAL_PITY_COUNT = BUILDER
            .comment("Guarantee a mythical result after this many rolls without a mythical or legendary result.")
            .defineInRange("gacha.pity.mythicalPityCount", 50, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue GACHA_LEGENDARY_PITY_COUNT = BUILDER
            .comment("Guarantee a legendary result after this many rolls without a legendary result.")
            .defineInRange("gacha.pity.legendaryPityCount", 100, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.BooleanValue GACHA_PITY_RESET_ON_HIT = BUILDER
            .comment("Reset pity counters when the matching rarity appears naturally. Forced pity results always reset their matching counter.")
            .define("gacha.pity.resetOnHit", true);

    public static final ModConfigSpec.DoubleValue GACHA_SELECTION_FORWARD_DISTANCE = BUILDER
            .comment("Horizontal distance from the player to the center of the three gacha balls.")
            .defineInRange("gacha.selection.forwardDistance", 3.0D, 2.0D, 6.0D);
    public static final ModConfigSpec.DoubleValue GACHA_SELECTION_BALL_SPACING = BUILDER
            .comment("Horizontal spacing between the three gacha balls.")
            .defineInRange("gacha.selection.ballSpacing", 1.2D, 0.75D, 2.5D);
    public static final ModConfigSpec.DoubleValue GACHA_SELECTION_SIDE_APPROACH_OFFSET = BUILDER
            .comment("How much closer the left and right balls are placed than the center ball, forming a fan toward the player.")
            .defineInRange("gacha.selection.sideApproachOffset", 0.2D, 0.0D, 1.25D);
    public static final ModConfigSpec.DoubleValue GACHA_SELECTION_FALL_HEIGHT = BUILDER
            .comment("Visual height from which the gacha balls fall.")
            .defineInRange("gacha.selection.fallHeight", 4.5D, 2.0D, 12.0D);
    public static final ModConfigSpec.IntValue GACHA_SELECTION_FALL_TICKS = BUILDER
            .comment("Ticks used by each ball's fall and landing animation.")
            .defineInRange("gacha.selection.fallTicks", 24, 10, 60);
    public static final ModConfigSpec.IntValue GACHA_SELECTION_STAGGER_TICKS = BUILDER
            .comment("Delay in ticks between consecutive falling balls.")
            .defineInRange("gacha.selection.staggerTicks", 4, 0, 20);
    public static final ModConfigSpec.DoubleValue GACHA_SELECTION_DISTANCE = BUILDER
            .comment("Maximum server-validated eye distance for selecting a gacha ball.")
            .defineInRange("gacha.selection.selectionDistance", 6.0D, 2.0D, 10.0D);
    public static final ModConfigSpec.IntValue GACHA_SELECTION_TIMEOUT_TICKS = BUILDER
            .comment("Ticks allowed for choosing a ball after the animation starts. A timed-out ticket is returned.")
            .defineInRange("gacha.selection.timeoutTicks", 300, 100, 1200);
    public static final ModConfigSpec.IntValue GACHA_SELECTION_REVEAL_TICKS = BUILDER
            .comment("Ticks for showing the selected Pokemon model before cleanup.")
            .defineInRange("gacha.selection.revealTicks", 100, 40, 240);
    public static final ModConfigSpec.IntValue GACHA_SELECTION_REWARD_GRANT_DELAY_TICKS = BUILDER
            .comment("Ticks after ball selection before granting the fixed Pokemon result. Disconnecting after selection grants it immediately instead of refunding the ticket.")
            .defineInRange("gacha.selection.rewardGrantDelayTicks", 24, 12, 60);
    public static final ModConfigSpec.DoubleValue GACHA_SELECTION_MOVE_CANCEL_DISTANCE = BUILDER
            .comment("Cancel and return the ticket if the owner moves this far from the selection origin.")
            .defineInRange("gacha.selection.moveCancelDistance", 12.0D, 6.0D, 32.0D);
    public static final ModConfigSpec.IntValue GACHA_SELECTION_PARTICLE_DENSITY = BUILDER
            .comment("Shiny and reveal particle density. 0 disables optional selection particles.")
            .defineInRange("gacha.selection.particleDensity", 1, 0, 4);
    public static final ModConfigSpec.BooleanValue GACHA_SELECTION_VISIBLE_TO_OTHERS = BUILDER
            .comment("Show the client-only three-ball selection animation to nearby players.")
            .define("gacha.selection.visibleToOthers", false);
    public static final ModConfigSpec.DoubleValue GACHA_SELECTION_PUBLIC_RADIUS = BUILDER
            .comment("Viewer radius used when gacha.selection.visibleToOthers is enabled.")
            .defineInRange("gacha.selection.publicRadius", 24.0D, 8.0D, 64.0D);

    public static final ModConfigSpec.BooleanValue RELIC_GACHA_RARE_BROADCAST_ENABLED = BUILDER
            .comment("Broadcast rare relic appraisal results to the server.")
            .translation("yoiko_core.configuration.relicGacha.rareBroadcastEnabled")
            .define("relicGacha.announcement.enabled", true);
    public static final ModConfigSpec.ConfigValue<String> RELIC_GACHA_RARE_BROADCAST_MIN_RARITY = BUILDER
            .comment("Minimum relic rarity announced server-wide: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYSTIC, or RADIANT.")
            .translation("yoiko_core.configuration.relicGacha.rareBroadcastMinRarity")
            .define("relicGacha.announcement.minimumRarity", "LEGENDARY",
                    value -> value instanceof String rarity
                            && com.yoiko.core.relic.RelicRarity.parse(rarity).isPresent());
    public static final ModConfigSpec.BooleanValue RELIC_GACHA_PERSONAL_RESULT_MESSAGE = BUILDER
            .comment("Also show the detailed personal result message when the result is announced server-wide.")
            .translation("yoiko_core.configuration.relicGacha.personalResultMessage")
            .define("relicGacha.announcement.personalResultMessage", false);

    public static final ModConfigSpec.IntValue COMMON_LEVEL_MIN = BUILDER.defineInRange("gacha.rarityStats.common.levelMin", 5, 1, 100);
    public static final ModConfigSpec.IntValue COMMON_LEVEL_MAX = BUILDER.defineInRange("gacha.rarityStats.common.levelMax", 30, 1, 100);
    public static final ModConfigSpec.IntValue SUB_LEGENDARY_LEVEL_MIN = BUILDER.defineInRange("gacha.rarityStats.subLegendary.levelMin", 40, 1, 100);
    public static final ModConfigSpec.IntValue SUB_LEGENDARY_LEVEL_MAX = BUILDER.defineInRange("gacha.rarityStats.subLegendary.levelMax", 60, 1, 100);
    public static final ModConfigSpec.IntValue MYTHICAL_LEVEL_MIN = BUILDER.defineInRange("gacha.rarityStats.mythical.levelMin", 50, 1, 100);
    public static final ModConfigSpec.IntValue MYTHICAL_LEVEL_MAX = BUILDER.defineInRange("gacha.rarityStats.mythical.levelMax", 70, 1, 100);
    public static final ModConfigSpec.IntValue LEGENDARY_LEVEL_MIN = BUILDER.defineInRange("gacha.rarityStats.legendary.levelMin", 60, 1, 100);
    public static final ModConfigSpec.IntValue LEGENDARY_LEVEL_MAX = BUILDER.defineInRange("gacha.rarityStats.legendary.levelMax", 80, 1, 100);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DEFAULT_COMMON_POOL = BUILDER
            .comment("Fallback common Pokemon pool used before a Cobblemon registry scanner is wired in.")
            .defineListAllowEmpty("gacha.defaultCommonPool", List.of(
                    "bulbasaur", "charmander", "squirtle", "pikachu", "eevee", "riolu", "dratini"
            ), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<String> DAILY_RESET_MODE = BUILDER
            .comment("daily, weekly, or cooldown.")
            .define("dailyReward.resetMode", "daily");

    public static final ModConfigSpec.IntValue DAILY_RESET_HOUR = BUILDER.defineInRange("dailyReward.resetHour", 5, 0, 23);
    public static final ModConfigSpec.ConfigValue<String> DAILY_RESET_WEEKDAY = BUILDER
            .comment("Weekly reset day used when dailyReward.resetMode is weekly. Valid values: MONDAY..SUNDAY.")
            .define("dailyReward.resetWeekday", "MONDAY");
    public static final ModConfigSpec.IntValue DAILY_COOLDOWN_HOURS = BUILDER.defineInRange("dailyReward.cooldownHours", 24, 1, 168);
    public static final ModConfigSpec.ConfigValue<String> DAILY_TIMEZONE = BUILDER.define("dailyReward.timezone", "Asia/Seoul");

    public static final ModConfigSpec.IntValue RESTED_GOLD_PER_DAY = BUILDER
            .comment("Gold added to the rested reserve each daily period, including while the player is offline.")
            .translation("yoiko_core.configuration.restedGoldPerDay")
            .defineInRange("economy.restedGoldV2.perDay", 120, 0, 20_000);
    public static final ModConfigSpec.IntValue RESTED_GOLD_CAP = BUILDER
            .comment("Maximum rested gold stored for a player.")
            .translation("yoiko_core.configuration.restedGoldCap")
            .defineInRange("economy.restedGoldV2.cap", 840, 0, 200_000);
    public static final ModConfigSpec.IntValue RESTED_GOLD_CALENDAR_CLAIM = BUILDER
            .comment("Maximum rested gold attached to one daily calendar reward mail.")
            .translation("yoiko_core.configuration.restedGoldCalendarClaim")
            .defineInRange("economy.restedGoldV2.calendarClaim", 60, 0, 20_000);

    public static final ModConfigSpec.IntValue RELIC_MINING_EASY_CREDITS_PER_DAY = BUILDER
            .comment("Easy relic-ticket chances added per daily period, including while offline.")
            .translation("yoiko_core.configuration.relicMiningEasyCreditsPerDay")
            .defineInRange("relicMiningV2.easyCreditsPerDay", 2, 0, 20);
    public static final ModConfigSpec.IntValue RELIC_MINING_EASY_CREDIT_CAP = BUILDER
            .comment("Maximum banked easy relic-ticket chances.")
            .translation("yoiko_core.configuration.relicMiningEasyCreditCap")
            .defineInRange("relicMiningV2.easyCreditCap", 6, 0, 100);
    public static final ModConfigSpec.IntValue RELIC_MINING_DAILY_TICKET_CAP = BUILDER
            .comment("Hard maximum of relic tickets obtained from mining per daily period.")
            .translation("yoiko_core.configuration.relicMiningDailyTicketCap")
            .defineInRange("relicMiningV2.dailyTicketCap", 10, 0, 100);
    public static final ModConfigSpec.DoubleValue RELIC_MINING_EASY_CHANCE = BUILDER
            .comment("Per-block chance while the player has a banked easy credit.")
            .translation("yoiko_core.configuration.relicMiningEasyChance")
            .defineInRange("relicMiningV2.easyChance", 0.05D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue RELIC_MINING_MID_CHANCE = BUILDER
            .comment("Per-block chance for daily tickets 3-5 after easy credits are exhausted.")
            .translation("yoiko_core.configuration.relicMiningMidChance")
            .defineInRange("relicMiningV2.midChance", 0.005D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue RELIC_MINING_HARD_CHANCE = BUILDER
            .comment("Per-block chance for daily tickets 6-10 after easy credits are exhausted.")
            .translation("yoiko_core.configuration.relicMiningHardChance")
            .defineInRange("relicMiningV2.hardChance", 0.001D, 0.0D, 1.0D);
    public static final ModConfigSpec.IntValue RELIC_MINING_EASY_PITY_BLOCKS = BUILDER
            .comment("Guarantee a banked easy ticket after this many eligible misses. 0 disables pity.")
            .translation("yoiko_core.configuration.relicMiningEasyPityBlocks")
            .defineInRange("relicMiningV2.easyPityBlocks", 30, 0, 100_000);
    public static final ModConfigSpec.IntValue RELIC_MINING_MID_PITY_BLOCKS = BUILDER
            .comment("Guarantee daily tickets 3-5 after this many eligible misses. 0 disables pity.")
            .translation("yoiko_core.configuration.relicMiningMidPityBlocks")
            .defineInRange("relicMiningV2.midPityBlocks", 300, 0, 100_000);
    public static final ModConfigSpec.IntValue RELIC_MINING_HARD_PITY_BLOCKS = BUILDER
            .comment("Guarantee daily tickets 6-10 after this many eligible misses. 0 disables pity.")
            .translation("yoiko_core.configuration.relicMiningHardPityBlocks")
            .defineInRange("relicMiningV2.hardPityBlocks", 1_000, 0, 100_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_HITS_REQUIRED = BUILDER
            .comment("Successful player hits required to catch a treasure rabbit. Damage amount is ignored.")
            .defineInRange("treasureRabbit.hitsRequired", 3, 1, 100);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_CROWN_HITS_REQUIRED = BUILDER
            .comment("Shared successful hits required to catch a Crown Rabbit. Hits from every player are combined; reward value never scales with hit count.")
            .defineInRange("treasureRabbit.crownBossV2.hitsRequired", 180, 20, 2_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_EXPLORATION_CHECK_TICKS = BUILDER
            .comment("Ticks between exploration samples. 1200 is one minute; this is a probability sample, not a cooldown.")
            .defineInRange("treasureRabbit.explorationCheckTicks", 1_200, 200, 12_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_MIN_EXPLORATION_DISTANCE = BUILDER
            .comment("Horizontal blocks moved per sample for one full spawn roll.")
            .defineInRange("treasureRabbit.minExplorationDistance", 24, 4, 256);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_TELEPORT_DISTANCE = BUILDER
            .comment("Movement beyond this distance in one sample is treated as teleportation and earns no roll.")
            .defineInRange("treasureRabbit.teleportDistance", 256, 32, 4_096);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_EXPLORATION_ROLL_CHANCE = BUILDER
            .comment("Chance per full exploration roll. 1/210 plus exploration-miss soft pity averages about three active exploration hours.")
            .defineInRange("treasureRabbit.explorationRollChance", 1.0D / 210.0D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_MULTIPLAYER_SCALE_STRENGTH = BUILDER
            .comment("Reduces each player's roll chance as more eligible explorers are online without using a spawn timer. 0 disables scaling.")
            .defineInRange("treasureRabbit.multiplayerScaleStrength", 0.65D, 0.0D, 1.0D);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_ESCAPE_NO_PLAYER_TICKS = BUILDER
            .comment("Ticks without a player within tracking range before a treasure rabbit burrows away without a reward.")
            .defineInRange("treasureRabbit.escapeNoPlayerTicks", 200, 40, 2_400);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_MAXIMUM_LIFETIME_TICKS = BUILDER
            .comment("Maximum treasure-rabbit lifetime in loaded or unloaded world game ticks. 3600 is three minutes; expiry always burrows without a reward.")
            .defineInRange("treasureRabbit.maximumLifetimeTicks", 3_600, 200, 72_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_CLUE_SOUND_INTERVAL_TICKS = BUILDER
            .comment("Ticks between quiet nearby clue chimes. Set high to make treasure rabbits harder to track.")
            .defineInRange("treasureRabbit.clueSoundIntervalTicks", 100, 40, 600);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_WORLD_SPAWN_EXCLUSION_RADIUS = BUILDER
            .comment("Horizontal radius around the Overworld shared spawn where natural treasure rabbits cannot appear. 0 disables it.")
            .defineInRange("treasureRabbit.worldSpawnExclusionRadius", 96, 0, 2_048);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TREASURE_RABBIT_EXCLUDED_ZONES = BUILDER
            .comment("Additional Overworld no-spawn zones in x,z,radius format, for example: 120,300,64. Malformed entries are ignored.")
            .defineListAllowEmpty("treasureRabbit.excludedZones", List.of(), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_RADIANT_CHANCE = BUILDER
            .comment("Chance that a successful treasure-rabbit encounter is radiant instead of golden.")
            .defineInRange("treasureRabbit.variantChances.radiantChance", 0.06D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_MIRROR_CHANCE = BUILDER
            .comment("Chance that a successful encounter is a Mirror Rabbit. Checked after Crown and Radiant variants.")
            .defineInRange("treasureRabbit.variantChances.mirrorChance", 0.12D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_CROWN_CHANCE = BUILDER
            .comment("Chance that a successful encounter is a giant cooperative Crown Rabbit.")
            .defineInRange("treasureRabbit.variantChances.crownChance", 0.02D, 0.0D, 1.0D);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_MIRROR_DECOYS = BUILDER
            .comment("Mirror decoys created when the real Mirror Rabbit is first hit. Decoys never grant capture rewards.")
            .defineInRange("treasureRabbit.mirrorDecoys", 6, 3, 12);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_GOLD_MIN = BUILDER
            .comment("Golden Rabbit reward in the compact currency denomination.")
            .defineInRange("treasureRabbit.currencyRewardsV7.golden.goldMin", 200, 0, 2_000_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_GOLD_MAX = BUILDER
            .defineInRange("treasureRabbit.currencyRewardsV7.golden.goldMax", 300, 0, 2_000_000);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_GOLD_SCRAP_CHANCE = BUILDER
            .defineInRange("treasureRabbit.rewardBundlesV2.golden.relicScrapChance", 0.35D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_GOLD_RELIC_TICKET_CHANCE = BUILDER
            .comment("Chance to drop 3 unidentified sealed relics and 1 upgrade crystal. The V2 key avoids retaining the legacy single-ticket chance.")
            .defineInRange("treasureRabbit.rewardBundlesV2.golden.relicCacheChance", 0.12D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_GOLD_SHINY_TICKET_CHANCE = BUILDER
            .comment("Chance for a Golden Rabbit to drop one guaranteed-shiny all-Pokemon ticket.")
            .defineInRange("treasureRabbit.rewardBundlesV4.golden.shinyTicketChance", 0.05D, 0.0D, 1.0D);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_RADIANT_GEM_MIN = BUILDER
            .comment("Radiant Rabbit gems in the compact currency denomination.")
            .defineInRange("treasureRabbit.currencyRewardsV3.radiant.gemMin", 18, 0, 1_000_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_RADIANT_GEM_MAX = BUILDER
            .defineInRange("treasureRabbit.currencyRewardsV3.radiant.gemMax", 26, 0, 1_000_000);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_RADIANT_SHINY_TICKET_CHANCE = BUILDER
            .comment("Guaranteed by default. The V2 key avoids retaining the old 25% value in an existing config.")
            .defineInRange("treasureRabbit.rewardBundlesV2.radiant.shinyTicketChance", 1.00D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_RADIANT_RELIC_TICKET_CHANCE = BUILDER
            .comment("Chance to drop a random category-focused sealed relic and 3 upgrade crystals. Guaranteed by default for the rare Radiant variant.")
            .defineInRange("treasureRabbit.rewardBundlesV2.radiant.relicCacheChance", 1.00D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_RADIANT_PARTICLE_TICKET_CHANCE = BUILDER
            .comment("Very rare bonus roll after catching an already ultra-rare radiant rabbit.")
            .defineInRange("treasureRabbit.rewards.radiant.particleTicketChance", 0.05D, 0.0D, 1.0D);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_MIRROR_GOLD_MIN = BUILDER
            .defineInRange("treasureRabbit.currencyRewardsV7.mirror.goldMin", 600, 0, 2_000_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_MIRROR_GOLD_MAX = BUILDER
            .defineInRange("treasureRabbit.currencyRewardsV7.mirror.goldMax", 1_000, 0, 2_000_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_MIRROR_GEM_MIN = BUILDER
            .comment("Mirror Rabbit gem reward in addition to its gold reward.")
            .defineInRange("treasureRabbit.currencyRewardsV4.mirror.gemMin", 8, 0, 1_000_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_MIRROR_GEM_MAX = BUILDER
            .defineInRange("treasureRabbit.currencyRewardsV4.mirror.gemMax", 12, 0, 1_000_000);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_MIRROR_SHINY_TICKET_CHANCE = BUILDER
            .comment("Chance for a Mirror Rabbit to drop one guaranteed-shiny all-Pokemon ticket.")
            .defineInRange("treasureRabbit.rewardBundlesV4.mirror.shinyTicketChance", 0.25D, 0.0D, 1.0D);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_CROWN_PARTICIPANT_GOLD = BUILDER
            .comment("Equal fixed gold reward for every Crown Rabbit contributor, independent of hit count and final hit.")
            .defineInRange("treasureRabbit.currencyRewardsV7.crown.participantGold", 1_600, 0, 2_000_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_CROWN_PARTICIPANT_GEMS = BUILDER
            .comment("Equal fixed gem reward for every Crown Rabbit contributor. There is no finisher bonus.")
            .defineInRange("treasureRabbit.currencyRewardsV6.crown.participantGems", 15, 0, 1_000_000);
    public static final ModConfigSpec.DoubleValue TREASURE_RABBIT_CROWN_SHINY_TICKET_CHANCE = BUILDER
            .comment("One shared roll for the completed Crown Rabbit: either every contributor gets a guaranteed-shiny ticket or nobody does.")
            .defineInRange("treasureRabbit.rewardBundlesV4.crown.shinyTicketChance", 0.75D, 0.0D, 1.0D);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_CROWN_MIN_BONUS_CONTRIBUTORS = BUILDER
            .comment("Unique participants required for every participant to receive a focused Relic ticket and 4 upgrade crystals.")
            .defineInRange("treasureRabbit.rewards.crown.bonusContributors", 3, 2, 32);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_CROWN_RELIC_EPIC_WEIGHT = BUILDER
            .comment("Crown-sealed relic rarity weight. Crown relics never roll below Epic.")
            .defineInRange("treasureRabbit.crownRelicV1.rarityWeights.epic", 50, 0, 100_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_CROWN_RELIC_LEGENDARY_WEIGHT = BUILDER
            .defineInRange("treasureRabbit.crownRelicV1.rarityWeights.legendary", 30, 0, 100_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_CROWN_RELIC_MYSTIC_WEIGHT = BUILDER
            .defineInRange("treasureRabbit.crownRelicV1.rarityWeights.mystic", 15, 0, 100_000);
    public static final ModConfigSpec.IntValue TREASURE_RABBIT_CROWN_RELIC_RADIANT_WEIGHT = BUILDER
            .comment("Radiant Crown relics are intentionally valuable and cannot be upgraded.")
            .defineInRange("treasureRabbit.crownRelicV1.rarityWeights.radiant", 5, 0, 100_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private YoikoCommonConfig() {
    }
}
