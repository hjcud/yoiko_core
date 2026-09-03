package com.yoiko.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yoiko.core.cosmetic.CosmeticEquipmentDisplayManager;
import com.yoiko.core.cosmetic.CosmeticEquipSlot;
import com.yoiko.core.cosmetic.CosmeticManager;
import com.yoiko.core.cosmetic.ParticleCosmeticDisplayManager;
import com.yoiko.core.menu.YoikoCosmeticMenu;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.OperationalAuditRecord;
import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.economy.CurrencyManager;
import com.yoiko.core.economy.CurrencyType;
import com.yoiko.core.economy.EconomyManager;
import com.yoiko.core.economy.EconomyDashboardService;
import com.yoiko.core.economy.MarketplaceTransactionRecord;
import com.yoiko.core.event.DailyServerEventManager;
import com.yoiko.core.event.BreakingNewsEventManager;
import com.yoiko.core.event.IncidentCategory;
import com.yoiko.core.gacha.GachaManager;
import com.yoiko.core.gacha.GachaPoolScanner;
import com.yoiko.core.gacha.GachaRarity;
import com.yoiko.core.gacha.GachaType;
import com.yoiko.core.mail.MailboxManager;
import com.yoiko.core.menu.PlayerMenuManager;
import com.yoiko.core.menu.YoikoDexMenu;
import com.yoiko.core.newspaper.WeeklyNewspaperManager;
import com.yoiko.core.profile.PublicProfileService;
import com.yoiko.core.relic.RelicData;
import com.yoiko.core.relic.RelicManager;
import com.yoiko.core.relic.RelicRarity;
import com.yoiko.core.rank.RankDisplayManager;
import com.yoiko.core.rank.RankManager;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.reward.RewardManager;
import com.yoiko.core.reward.YoikoResetClock;
import com.yoiko.core.storage.YoikoStorageManager;
import com.yoiko.core.treasure.TreasureRabbitManager;
import com.yoiko.core.treasure.TreasureRabbitVariant;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class YoikoCommand {
    private YoikoCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("yoiko")
                .requires(source -> source.hasPermission(2))
                .executes(context -> YoikoCommandHelp.showAdminIndex(context.getSource()))
                .then(YoikoCommandHelp.adminHelpCommand())
                .then(Commands.literal("reload").executes(context -> reload(context.getSource())))
                .then(Commands.literal("newspaper")
                        .then(Commands.literal("status")
                                .executes(context -> newspaperStatus(context.getSource())))
                        .then(Commands.literal("preview")
                                .executes(context -> newspaperPreview(context.getSource())))
                        .then(Commands.literal("send")
                                .executes(context -> newspaperSend(context.getSource()))))
                .then(incidentCommands())
                .then(Commands.literal("event")
                        .then(Commands.literal("status")
                                .executes(context -> eventStatus(context.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument("event_id", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                DailyServerEventManager.ids(), builder))
                                        .executes(context -> eventSet(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "event_id")))))
                        .then(Commands.literal("clear")
                                .executes(context -> eventClear(context.getSource()))))
                .then(Commands.literal("breaking")
                        .then(Commands.literal("status")
                                .executes(context -> breakingNewsStatus(context.getSource())))
                        .then(Commands.literal("start")
                                .then(Commands.literal("fishing")
                                        .executes(context -> breakingNewsStart(
                                                context.getSource(), BreakingNewsEventManager.Type.FISHING_FESTIVAL,
                                                BreakingNewsEventManager.defaultDurationMinutes(
                                                        BreakingNewsEventManager.Type.FISHING_FESTIVAL)))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 10_080))
                                                .executes(context -> breakingNewsStart(
                                                        context.getSource(), BreakingNewsEventManager.Type.FISHING_FESTIVAL,
                                                        IntegerArgumentType.getInteger(context, "minutes")))))
                                .then(Commands.literal("rabbits")
                                        .executes(context -> breakingNewsStart(
                                                context.getSource(), BreakingNewsEventManager.Type.TREASURE_RABBIT_SWARM,
                                                BreakingNewsEventManager.defaultDurationMinutes(
                                                        BreakingNewsEventManager.Type.TREASURE_RABBIT_SWARM)))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 10_080))
                                                .executes(context -> breakingNewsStart(
                                                        context.getSource(), BreakingNewsEventManager.Type.TREASURE_RABBIT_SWARM,
                                                        IntegerArgumentType.getInteger(context, "minutes")))))
                                .then(Commands.literal("capture")
                                        .executes(context -> breakingNewsStart(
                                                context.getSource(), BreakingNewsEventManager.Type.POKEMON_CAPTURE_GOAL,
                                                BreakingNewsEventManager.defaultDurationMinutes(
                                                        BreakingNewsEventManager.Type.POKEMON_CAPTURE_GOAL)))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 10_080))
                                                .executes(context -> breakingNewsStart(
                                                        context.getSource(), BreakingNewsEventManager.Type.POKEMON_CAPTURE_GOAL,
                                                        IntegerArgumentType.getInteger(context, "minutes")))))
                                .then(Commands.literal("outbreak")
                                        .executes(context -> breakingNewsStart(
                                                context.getSource(), BreakingNewsEventManager.Type.MASS_OUTBREAK,
                                                BreakingNewsEventManager.defaultDurationMinutes(
                                                        BreakingNewsEventManager.Type.MASS_OUTBREAK)))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 10_080))
                                                .executes(context -> breakingNewsStart(
                                                        context.getSource(), BreakingNewsEventManager.Type.MASS_OUTBREAK,
                                                        IntegerArgumentType.getInteger(context, "minutes"))))))
                        .then(Commands.literal("stop")
                                .executes(context -> breakingNewsStop(context.getSource()))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("particle")
                                .then(Commands.literal("stats")
                                        .executes(context -> particleStats(context.getSource())))))
                .then(Commands.literal("data")
                        .then(Commands.literal("save").executes(context -> save(context.getSource()))))
                .then(Commands.literal("economy")
                        .then(Commands.literal("stats")
                                .executes(context -> economyStats(context.getSource())))
                        .then(Commands.literal("dashboard")
                                .executes(context -> economyDashboard(context.getSource(), 7))
                                .then(Commands.argument("days", IntegerArgumentType.integer(
                                                1, EconomyDashboardService.MAX_HISTORY_DAYS))
                                        .executes(context -> economyDashboard(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "days")))))
                        .then(Commands.literal("flow")
                                .executes(context -> economyFlow(context.getSource(), 7))
                                .then(Commands.argument("days", IntegerArgumentType.integer(
                                                1, EconomyDashboardService.MAX_HISTORY_DAYS))
                                        .executes(context -> economyFlow(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "days")))))
                        .then(Commands.literal("daily")
                                .executes(context -> economyDaily(context.getSource(), 7))
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 30))
                                        .executes(context -> economyDaily(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "days")))))
                        .then(Commands.literal("wealth")
                                .executes(context -> economyWealth(context.getSource()))))
                .then(Commands.literal("market")
                        .then(Commands.literal("audit")
                                .executes(context -> marketAudit(context.getSource(), 20))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> marketAudit(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "count"))))
                                .then(Commands.literal("suspicious")
                                        .executes(context -> marketAuditFiltered(context.getSource(), "", "", true, 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(context -> marketAuditFiltered(context.getSource(), "", "", true,
                                                        IntegerArgumentType.getInteger(context, "page")))))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> marketAuditFiltered(context.getSource(),
                                                        StringArgumentType.getString(context, "name"), "", false, 1))
                                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                        .executes(context -> marketAuditFiltered(context.getSource(),
                                                                StringArgumentType.getString(context, "name"), "", false,
                                                                IntegerArgumentType.getInteger(context, "page"))))))
                                .then(Commands.literal("item")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> marketAuditFiltered(context.getSource(), "",
                                                        StringArgumentType.getString(context, "id"), false, 1))
                                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                        .executes(context -> marketAuditFiltered(context.getSource(), "",
                                                                StringArgumentType.getString(context, "id"), false,
                                                                IntegerArgumentType.getInteger(context, "page"))))))))
                .then(Commands.literal("mailbox")
                        .then(Commands.literal("audit")
                                .executes(context -> mailboxAudit(context.getSource(), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> mailboxAudit(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "page")))))
                        .then(Commands.literal("queue")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> mailboxQueue(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("recover")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> mailboxRecover(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("open")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> mailboxOpen(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("send")
                                .then(Commands.literal("item")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
                                                                .executes(context -> mailboxSendItem(
                                                                        context.getSource(),
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        ItemArgument.getItem(context, "item"),
                                                                        IntegerArgumentType.getInteger(context, "count"),
                                                                        "yoiko_core.mail.admin.reward_message"))
                                                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                                                        .executes(context -> mailboxSendItem(
                                                                                context.getSource(),
                                                                                EntityArgument.getPlayer(context, "player"),
                                                                                ItemArgument.getItem(context, "item"),
                                                                                IntegerArgumentType.getInteger(context, "count"),
                                                                                StringArgumentType.getString(context, "message")))))))))
                                .then(Commands.literal("message")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                                        .executes(context -> mailboxSendMessage(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "message")))))))
                .then(Commands.literal("storage")
                        .then(Commands.literal("open")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> storageOpen(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("status")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> storageStatus(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("gacha")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(GachaType.ids(), builder))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
                                                        .executes(context -> giveGacha(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "type"),
                                                                IntegerArgumentType.getInteger(context, "count"))))))
                                .then(Commands.literal("all")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(GachaType.ids(), builder))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
                                                        .executes(context -> giveAllGacha(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "type"),
                                                                IntegerArgumentType.getInteger(context, "count")))))))
                        .then(Commands.literal("roll")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(GachaType.ids(), builder))
                                                .executes(context -> rollGacha(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "type"))))))
                        .then(Commands.literal("history")
                                .executes(context -> gachaHistory(context.getSource(), 5))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                        .executes(context -> gachaHistory(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                        .then(Commands.literal("pity")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> gachaPity(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("pool")
                                .then(Commands.literal("list")
                                        .then(Commands.argument("rarity", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"common", "sub_legendary", "mythical", "legendary"}, builder))
                                                .executes(context -> poolList(context.getSource(), StringArgumentType.getString(context, "rarity")))))
                                .then(Commands.literal("rescan").executes(context -> rescan(context.getSource())))
                                .then(Commands.literal("reload").executes(context -> poolReload(context.getSource())))))
                .then(Commands.literal("dex")
                        .then(Commands.literal("open")
                                .executes(context -> dexGui(context.getSource(), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> dexGui(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("summary").executes(context -> dexSummary(context.getSource())))
                        .then(Commands.literal("list")
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"common", "sub_legendary", "mythical", "legendary"}, builder))
                                        .executes(context -> dexList(context.getSource(), StringArgumentType.getString(context, "rarity"), 20))
                                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                                .executes(context -> dexList(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "rarity"),
                                                        IntegerArgumentType.getInteger(context, "limit"))))))
                        .then(Commands.literal("search")
                                .then(Commands.argument("query", StringArgumentType.word())
                                        .executes(context -> dexSearch(context.getSource(), StringArgumentType.getString(context, "query"), 10))
                                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                                .executes(context -> dexSearch(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "query"),
                                                        IntegerArgumentType.getInteger(context, "limit"))))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("species", StringArgumentType.word())
                                        .executes(context -> dexInfo(context.getSource(), StringArgumentType.getString(context, "species"))))))
                .then(Commands.literal("reward")
                        .then(Commands.literal("claim")
                                .then(Commands.literal("daily")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> claimDaily(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("first")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> claimFirst(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                        .then(Commands.literal("give")
                                .then(Commands.literal("keys")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> giveDailyKeys(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("boxes")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> resetDailyBoxes(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("streak")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> resetDailyStreak(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("first")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> resetFirst(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                        .then(Commands.literal("status")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> rewardStatus(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("rank")
                        .then(Commands.literal("grant")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("rank", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(RankManager.ids(), builder))
                                                .executes(context -> rankGrant(context.getSource(), EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "rank"))))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("rank", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(RankManager.ids(), builder))
                                                .executes(context -> rankRevoke(context.getSource(), EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "rank"))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("rank", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        Stream.concat(Stream.of("none"), RankManager.ids().stream()).toList(),
                                                        builder))
                                                .executes(context -> rankSet(context.getSource(), EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "rank"))))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> rankList(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("relic")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("random")
                                                .then(Commands.argument("rarity", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYSTIC", "RADIANT"}, builder))
                                                        .executes(context -> relicGiveRandom(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "rarity")))))
                                        .then(Commands.argument("relic_id", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(RelicManager.ids(), builder))
                                                .then(Commands.argument("rarity", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYSTIC", "RADIANT"}, builder))
                                                        .executes(context -> relicGive(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "relic_id"),
                                                                StringArgumentType.getString(context, "rarity"),
                                                                "random"))
                                                        .then(Commands.argument("secondary_effect", StringArgumentType.word())
                                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                        Stream.concat(
                                                                                Stream.of("auto", "none", "random"),
                                                                                RelicManager.secondaryEffectIds(
                                                                                        StringArgumentType.getString(context, "relic_id")
                                                                                ).stream()
                                                                        ).toList(),
                                                                        builder
                                                                ))
                                                                .executes(context -> relicGive(
                                                                        context.getSource(),
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        StringArgumentType.getString(context, "relic_id"),
                                                                        StringArgumentType.getString(context, "rarity"),
                                                                        StringArgumentType.getString(context, "secondary_effect"))))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("relic_uuid", StringArgumentType.word())
                                                .executes(context -> relicRemove(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "relic_uuid"))))))
                        .then(Commands.literal("upgrade")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("relic_uuid", StringArgumentType.word())
                                                .executes(context -> relicUpgrade(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "relic_uuid"),
                                                        false))
                                                .then(Commands.argument("use_protection", BoolArgumentType.bool())
                                                        .executes(context -> relicUpgrade(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "relic_uuid"),
                                                                BoolArgumentType.getBool(context, "use_protection")))))))
                        .then(Commands.literal("ticket")
                                .then(Commands.literal("give")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
                                                        .executes(context -> giveStack(context.getSource(), EntityArgument.getPlayer(context, "player"), YoikoItems.RELIC_GACHA_TICKET.toStack(IntegerArgumentType.getInteger(context, "count")), "item.yoiko_core.relic_gacha_ticket"))))))
                        .then(Commands.literal("crystal")
                                .then(Commands.literal("give")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
                                                        .executes(context -> giveRelicUpgradeCrystals(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "count")))))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> relicList(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("currency")
                        .then(Commands.literal("balance")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> currencyBalance(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"gold", "gem"}, builder))
                                                .then(Commands.argument("amount", LongArgumentType.longArg(1L, CurrencyManager.MAX_BALANCE))
                                                        .executes(context -> currencyChange(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "type"),
                                                                LongArgumentType.getLong(context, "amount"),
                                                                "give"))))))
                        .then(Commands.literal("take")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"gold", "gem"}, builder))
                                                .then(Commands.argument("amount", LongArgumentType.longArg(1L, CurrencyManager.MAX_BALANCE))
                                                        .executes(context -> currencyChange(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "type"),
                                                                LongArgumentType.getLong(context, "amount"),
                                                                "take"))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"gold", "gem"}, builder))
                                                .then(Commands.argument("amount", LongArgumentType.longArg(0L, CurrencyManager.MAX_BALANCE))
                                                        .executes(context -> currencyChange(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "type"),
                                                                LongArgumentType.getLong(context, "amount"),
                                                                "set")))))))
                .then(Commands.literal("cosmetic")
                        .then(Commands.literal("grant")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("cosmetic_id", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        Stream.concat(Stream.of("all"), CosmeticManager.ids().stream()).toList(),
                                                        builder))
                                                .executes(context -> cosmeticGrant(context.getSource(), EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "cosmetic_id"))))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("cosmetic_id", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(CosmeticManager.ids(), builder))
                                                .executes(context -> cosmeticRevoke(context.getSource(), EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "cosmetic_id"))))))
                        .then(Commands.literal("equip")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("cosmetic_id", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(CosmeticManager.ids(), builder))
                                                .executes(context -> cosmeticEquip(context.getSource(), EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "cosmetic_id"))))))
                        .then(Commands.literal("unequip")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"HEAD", "CHEST", "FEET"}, builder))
                                                .executes(context -> cosmeticUnequip(context.getSource(), EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "type"))))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> cosmeticList(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                .then(TurtleCommand.adminCommands())
                .then(TreasureRabbitCommand.adminCommands()));
        dispatcher.register(TurtleCommand.playerCommands());
        dispatcher.register(Commands.literal("yoikoprofile")
                .executes(context -> showPublicProfile(context.getSource(), context.getSource().getPlayerOrException()))
                .then(YoikoCommandHelp.profileHelpCommand())
                .then(Commands.literal("sharing")
                        .then(Commands.literal("on")
                                .executes(context -> setPublicProfileSharing(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setPublicProfileSharing(context.getSource(), false))))
                .then(Commands.literal("view")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> showPublicProfile(
                                        context.getSource(), EntityArgument.getPlayer(context, "player"))))));
    }

    private static int setPublicProfileSharing(CommandSourceStack source, boolean enabled)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PublicProfileService.setSharing(player, enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "yoiko_core.profile.sharing_enabled"
                : "yoiko_core.profile.sharing_disabled").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int showPublicProfile(CommandSourceStack source, ServerPlayer target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer viewer = source.getPlayerOrException();
        if (!PublicProfileService.canView(viewer, target)) {
            source.sendFailure(Component.translatable("yoiko_core.profile.private", target.getGameProfile().getName()));
            return 0;
        }
        PublicProfileService.lines(target).forEach(line -> source.sendSuccess(() -> line, false));
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> incidentCommands() {
        return Commands.literal("incident")
                .then(Commands.literal("status")
                        .executes(context -> incidentStatus(context.getSource())))
                .then(Commands.literal("personal")
                        .then(personalTreasureRabbitCommands()))
                .then(Commands.literal("server")
                        .then(serverDailyIncidentCommands())
                        .then(serverBreakingIncidentCommands()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> personalTreasureRabbitCommands() {
        return Commands.literal("treasure_rabbit")
                .then(Commands.literal("status")
                        .executes(context -> personalTreasureRabbitStatus(
                                context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> personalTreasureRabbitStatus(
                                        context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("start")
                        .then(personalTreasureRabbitVariantCommand(TreasureRabbitVariant.GOLDEN))
                        .then(personalTreasureRabbitVariantCommand(TreasureRabbitVariant.RADIANT))
                        .then(personalTreasureRabbitVariantCommand(TreasureRabbitVariant.MIRROR))
                        .then(personalTreasureRabbitVariantCommand(TreasureRabbitVariant.CROWN)))
                .then(Commands.literal("stop")
                        .executes(context -> personalTreasureRabbitStop(
                                context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> personalTreasureRabbitStop(
                                        context.getSource(), EntityArgument.getPlayer(context, "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> personalTreasureRabbitVariantCommand(
            TreasureRabbitVariant variant) {
        return Commands.literal(variant.id())
                .executes(context -> personalTreasureRabbitStart(
                        context.getSource(), context.getSource().getPlayerOrException(), variant))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> personalTreasureRabbitStart(
                                context.getSource(), EntityArgument.getPlayer(context, "player"), variant)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> serverDailyIncidentCommands() {
        return Commands.literal("daily")
                .then(Commands.literal("status")
                        .executes(context -> eventStatus(context.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("event_id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        DailyServerEventManager.ids(), builder))
                                .executes(context -> eventSet(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "event_id")))))
                .then(Commands.literal("clear")
                        .executes(context -> eventClear(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> serverBreakingIncidentCommands() {
        return Commands.literal("breaking")
                .then(Commands.literal("status")
                        .executes(context -> breakingNewsStatus(context.getSource())))
                .then(Commands.literal("start")
                        .then(serverBreakingIncidentTypeCommand(
                                "fishing", BreakingNewsEventManager.Type.FISHING_FESTIVAL))
                        .then(serverBreakingIncidentTypeCommand(
                                "rabbits", BreakingNewsEventManager.Type.TREASURE_RABBIT_SWARM))
                        .then(serverBreakingIncidentTypeCommand(
                                "capture", BreakingNewsEventManager.Type.POKEMON_CAPTURE_GOAL))
                        .then(serverBreakingIncidentTypeCommand(
                                "outbreak", BreakingNewsEventManager.Type.MASS_OUTBREAK)))
                .then(Commands.literal("stop")
                        .executes(context -> breakingNewsStop(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> serverBreakingIncidentTypeCommand(
            String literal, BreakingNewsEventManager.Type type) {
        return Commands.literal(literal)
                .executes(context -> breakingNewsStart(
                        context.getSource(), type,
                        BreakingNewsEventManager.defaultDurationMinutes(type)))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 10_080))
                        .executes(context -> breakingNewsStart(
                                context.getSource(), type,
                                IntegerArgumentType.getInteger(context, "minutes"))));
    }

    private static int reload(CommandSourceStack source) {
        GachaPoolScanner.load();
        RankManager.reload();
        CosmeticManager.reload();
        RelicManager.reload();
        EconomyManager.reload();
        RewardManager.load();
        DailyServerEventManager.reload();
        DailyServerEventManager.refreshActive(source.getServer());
        BreakingNewsEventManager.reload();
        WeeklyNewspaperManager.reload();
        RankDisplayManager.refreshAll(source.getServer());
        ParticleCosmeticDisplayManager.syncCatalogToAll(source.getServer());
        YoikoCosmeticMenu.syncCatalogToAll(source.getServer());
        ParticleCosmeticDisplayManager.syncAll(source.getServer());
        CosmeticEquipmentDisplayManager.syncAll(source.getServer());
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.reload.success").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int save(CommandSourceStack source) {
        source.getServer().overworld().getDataStorage().save();
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.save.success").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int economyStats(CommandSourceStack source) {
        EconomyDashboardService.Snapshot value = EconomyDashboardService.flowSnapshot(source.getServer(), 1);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.stats",
                value.periodTo(), value.goldCreated(), value.goldBurned(), value.gemsCreated(), value.gemsSpent())
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int economyDashboard(CommandSourceStack source, int days) {
        EconomyDashboardService.Snapshot value = EconomyDashboardService.snapshot(source.getServer(), days);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.header", value.days())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.period",
                value.periodFrom(), value.periodTo(), value.trackingSince(), value.activePlayers())
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.gold",
                value.goldCreated(), value.goldBurned(), value.goldNetFlow(),
                value.serverBuyGold(), value.rabbitGold(), value.restedGoldCreated(), value.welcomeGoldCreated(),
                value.goldShopSpent(),
                value.feesBurned(), value.turtleBetBurned(),value.turtleBetCreated(), value.adminGoldCreated(),
                value.adminGoldRemoved()).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.gems",
                value.gemsCreated(), value.gemsSpent(), value.gemNetFlow(), value.adminGemsCreated(),
                value.adminGemsRemoved()).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.market",
                value.tradeCount(), value.tradeVolume(), value.feesBurned(), value.suspiciousCount(),
                value.activeListings(), value.activeListingValue()).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.balances",
                value.knownPlayers(), value.totalGold(), value.goldP50(), value.goldP90(), value.goldP99(),
                value.totalGems(), value.gemP50(), value.gemP90(), value.gemP99(),
                value.topTenGoldShareBps() / 100.0D).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.liabilities",
                value.projectedRestedGold(), value.pendingMailGold(), value.pendingMailGems(),
                value.turtleEscrowGold())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.turnover",
                value.tradeVolume(), value.turtleBetStaked(), value.turtleBetPayout(), value.turtleBetRefund(),
                value.dailyAverageGoldCreated(), value.dailyAverageGoldBurned()).withStyle(ChatFormatting.GRAY), false);
        if (value.historyLimited()) {
            source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.truncated")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int economyFlow(CommandSourceStack source, int days) {
        EconomyDashboardService.Snapshot value = EconomyDashboardService.flowSnapshot(source.getServer(), days);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.flow.header",
                value.periodFrom(), value.periodTo()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        sendFlowGroup(source, "yoiko_core.command.economy.flow.gold_sources", value.goldSources(), ChatFormatting.YELLOW);
        sendFlowGroup(source, "yoiko_core.command.economy.flow.gold_sinks", value.goldSinks(), ChatFormatting.YELLOW);
        sendFlowGroup(source, "yoiko_core.command.economy.flow.gem_sources", value.gemSources(), ChatFormatting.AQUA);
        sendFlowGroup(source, "yoiko_core.command.economy.flow.gem_sinks", value.gemSinks(), ChatFormatting.AQUA);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.flow.details_header")
                .withStyle(ChatFormatting.GRAY), false);
        if (value.details().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.flow.none")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            value.details().stream().limit(10).forEach(detail -> source.sendSuccess(() -> Component.translatable(
                    "yoiko_core.command.economy.flow.detail", economyActionName(detail.action()), detail.detail(),
                    detail.amount(), detail.events()).withStyle(ChatFormatting.GRAY), false));
        }
        return 1;
    }

    private static void sendFlowGroup(CommandSourceStack source, String key,
                                      List<EconomyDashboardService.FlowSource> values,
                                      ChatFormatting color) {
        var summary = Component.empty();
        if (values.isEmpty()) {
            summary.append(Component.translatable("yoiko_core.command.economy.flow.none"));
        } else {
            for (int index = 0; index < Math.min(8, values.size()); index++) {
                EconomyDashboardService.FlowSource value = values.get(index);
                if (index > 0) summary.append(Component.literal(" · "));
                summary.append(economyActionName(value.action()))
                        .append(Component.literal(" " + value.amount() + " (" + value.events() + ")"));
            }
        }
        source.sendSuccess(() -> Component.translatable(key, summary).withStyle(color), false);
    }

    private static Component economyActionName(String action) {
        return Component.translatable("yoiko_core.economy.action."
                + action.toLowerCase(java.util.Locale.ROOT));
    }

    private static int economyDaily(CommandSourceStack source, int days) {
        EconomyDashboardService.Snapshot value = EconomyDashboardService.flowSnapshot(source.getServer(), days);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.daily.header",
                value.periodFrom(), value.periodTo()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (EconomyDashboardService.DailyFlow day : value.dailyFlows()) {
            source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.daily.entry",
                    day.period(), day.goldCreated(), day.goldBurned(), day.goldNetFlow(),
                    day.gemsCreated(), day.gemsSpent(), day.gemNetFlow(), day.activePlayers(),
                    day.marketVolume(), day.turtleBetStake()).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int economyWealth(CommandSourceStack source) {
        EconomyDashboardService.WealthSnapshot value = EconomyDashboardService.wealthSnapshot(source.getServer());
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.wealth.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.balances",
                value.knownPlayers(), value.totalGold(), value.goldP50(), value.goldP90(), value.goldP99(),
                value.totalGems(), value.gemP50(), value.gemP90(), value.gemP99(),
                value.topTenGoldShareBps() / 100.0D).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.economy.dashboard.liabilities",
                value.projectedRestedGold(), value.pendingMailGold(), value.pendingMailGems(),
                value.turtleEscrowGold())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    private static int newspaperStatus(CommandSourceStack source) {
        WeeklyNewspaperManager.Status status = WeeklyNewspaperManager.status(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.newspaper.status",
                status.weekKey(), status.knownPlayers(), status.radiantRelics(),
                status.shinyGacha(), status.goldenRabbits() + status.radiantRabbits()
                        + status.mirrorRabbits() + status.crownRabbits(),
                status.marketSales(), status.marketRevenue(),
                status.enabled(), status.autoPublish()), false);
        return 1;
    }

    private static int newspaperPreview(CommandSourceStack source) {
        WeeklyNewspaperManager.Issue issue = WeeklyNewspaperManager.preview(source.getServer());
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.newspaper.preview_header"), false);
        issue.previewLines().forEach(line -> source.sendSuccess(() -> line, false));
        return issue.previewLines().size();
    }

    private static int newspaperSend(CommandSourceStack source) {
        WeeklyNewspaperManager.PublicationResult result = WeeklyNewspaperManager.sendCurrent(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.newspaper.sent",
                result.weekKey(), result.delivered(), result.recipients()), true);
        return 1;
    }

    private static int incidentStatus(CommandSourceStack source) {
        int personalCount = TreasureRabbitManager.countPersonalIncidents(source.getServer(), null);
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.incident.status",
                IncidentCategory.PERSONAL.displayName(), personalCount,
                IncidentCategory.SERVER.displayName()), false);
        eventStatus(source);
        breakingNewsStatus(source);
        return 1;
    }

    private static int personalTreasureRabbitStatus(CommandSourceStack source, ServerPlayer target) {
        int count = TreasureRabbitManager.countPersonalIncidents(target);
        source.sendSuccess(() -> Component.translatable(
                count > 0
                        ? "yoiko_core.command.incident.personal.treasure_rabbit.status.active"
                        : "yoiko_core.command.incident.personal.treasure_rabbit.status.inactive",
                IncidentCategory.PERSONAL.displayName(), target.getDisplayName(), count), false);
        return 1;
    }

    private static int personalTreasureRabbitStart(
            CommandSourceStack source, ServerPlayer target, TreasureRabbitVariant variant) {
        TreasureRabbitManager.PersonalIncidentStartResult result =
                TreasureRabbitManager.startPersonalIncident(target, variant);
        if (!result.started() || result.rabbit() == null) {
            source.sendFailure(Component.translatable(
                    "yoiko_core.command.incident.personal.treasure_rabbit.start.failed." + result.reason(),
                    target.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.incident.personal.treasure_rabbit.started",
                IncidentCategory.PERSONAL.displayName(), result.rabbit().getDisplayName(),
                target.getDisplayName()), true);
        return 1;
    }

    private static int personalTreasureRabbitStop(CommandSourceStack source, ServerPlayer target) {
        int stopped = TreasureRabbitManager.stopPersonalIncidents(target);
        if (stopped <= 0) {
            source.sendFailure(Component.translatable(
                    "yoiko_core.command.incident.personal.treasure_rabbit.stop.inactive",
                    target.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.incident.personal.treasure_rabbit.stopped",
                IncidentCategory.PERSONAL.displayName(), target.getDisplayName(), stopped), true);
        return stopped;
    }

    private static int eventStatus(CommandSourceStack source) {
        DailyServerEventManager.EventStatus status = DailyServerEventManager.status(source.getServer());
        Component summary = DailyServerEventManager.summary(status);
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.event.status", status.category().displayName(),
                summary, status.periodDate(), status.id(),
                status.shinyMultiplier(), status.forced()), false);
        return 1;
    }

    private static int eventSet(CommandSourceStack source, String eventId) {
        if (!DailyServerEventManager.force(source.getServer(), eventId)) {
            source.sendFailure(Component.translatable("yoiko_core.command.event.unknown", eventId));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.event.set", eventId), true);
        return 1;
    }

    private static int eventClear(CommandSourceStack source) {
        DailyServerEventManager.EventStatus status = DailyServerEventManager.clearOverride(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.event.cleared", DailyServerEventManager.summary(status)), true);
        return 1;
    }

    private static int breakingNewsStatus(CommandSourceStack source) {
        BreakingNewsEventManager.Status status = BreakingNewsEventManager.status(source.getServer());
        if (!status.active()) {
            source.sendSuccess(() -> Component.translatable(
                    "yoiko_core.command.breaking.status.inactive"), false);
            return 1;
        }
        if (status.waitingForActivation()) {
            source.sendSuccess(() -> Component.translatable(
                    "yoiko_core.command.breaking.status.waiting",
                    status.category().displayName(),
                    BreakingNewsEventManager.typeName(status.type()), status.zoneCenterX(),
                    status.zoneCenterZ(), status.zoneRadius()), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "yoiko_core.command.breaking.status.active",
                    status.category().displayName(),
                    BreakingNewsEventManager.typeName(status.type()),
                    BreakingNewsEventManager.remainingMinutes(status),
                    status.primaryCount(), status.secondaryCount(), status.tertiaryCount(),
                    status.participantCount()), false);
        }
        return 1;
    }

    private static int breakingNewsStart(CommandSourceStack source,
                                         BreakingNewsEventManager.Type type, int minutes) {
        ServerPlayer preferred = source.getEntity() instanceof ServerPlayer player ? player : null;
        BreakingNewsEventManager.StartResult result = BreakingNewsEventManager.start(
                source.getServer(), type, minutes, preferred);
        if (!result.started()) {
            source.sendFailure(Component.translatable(
                    "yoiko_core.command.breaking.start.failed." + result.reason()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                result.status().waitingForActivation()
                        ? "yoiko_core.command.breaking.started.waiting" : "yoiko_core.command.breaking.started",
                BreakingNewsEventManager.typeName(type),
                BreakingNewsEventManager.remainingMinutes(result.status())), true);
        return 1;
    }

    private static int breakingNewsStop(CommandSourceStack source) {
        if (!BreakingNewsEventManager.stop(source.getServer(), true)) {
            source.sendFailure(Component.translatable("yoiko_core.command.breaking.not_active"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.breaking.stopped"), true);
        return 1;
    }

    private static int mailboxOpen(CommandSourceStack source, ServerPlayer player) {
        MailboxManager.open(player);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.mailbox.opened", player.getDisplayName()), true);
        return 1;
    }

    private static int mailboxSendItem(CommandSourceStack source, ServerPlayer player, ItemInput itemInput, int count, String message) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = itemInput.createItemStack(1, false);
        if (!MailboxManager.sendAdminMail(player, "yoiko_core.mail.admin.reward_title", message,
                splitStack(stack, count))) {
            source.sendFailure(Component.translatable("yoiko_core.command.mailbox.send_failed",
                    player.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.mailbox.item_sent", player.getDisplayName(), count), true);
        return count;
    }

    private static int mailboxSendMessage(CommandSourceStack source, ServerPlayer player, String message) {
        if (!MailboxManager.sendAdminMail(player, "yoiko_core.mail.admin.message_title", message, List.of())) {
            source.sendFailure(Component.translatable("yoiko_core.command.mailbox.send_failed",
                    player.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.mailbox.message_sent", player.getDisplayName()), true);
        return 1;
    }

    private static int storageOpen(CommandSourceStack source, ServerPlayer player) {
        boolean opened = YoikoStorageManager.open(player);
        source.sendSuccess(() -> Component.translatable(opened
                ? "yoiko_core.command.storage.opened"
                : "yoiko_core.command.storage.failed", player.getDisplayName()), true);
        return opened ? 1 : 0;
    }

    private static int storageStatus(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> YoikoStorageManager.statusComponent(player), false);
        return YoikoStorageManager.activeSlots(player);
    }

    private static int giveGacha(CommandSourceStack source, ServerPlayer player, String typeId, int count) {
        return giveStack(source, player, ticketFor(GachaType.fromString(typeId), count), "item.yoiko_core.all_pokemon_gacha_ticket");
    }

    private static int giveAllGacha(CommandSourceStack source, String typeId, int count) {
        Collection<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            giveItem(player, ticketFor(GachaType.fromString(typeId), count));
        }
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.gacha.given_all", count, players.size()), true);
        return players.size();
    }

    private static int rollGacha(CommandSourceStack source, ServerPlayer player, String typeId) {
        GachaType type = GachaType.fromString(typeId);
        String error = GachaManager.configurationError(type);
        if (!error.isBlank()) {
            source.sendFailure(Component.translatable("yoiko_core.command.gacha.invalid_pool", error));
            return 0;
        }
        GachaManager.completeTicketRoll(player, GachaManager.roll(player, type));
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.gacha.rolled", player.getDisplayName()), true);
        return 1;
    }

    private static int gachaHistory(CommandSourceStack source, int count) {
        List<Component> lines = GachaManager.describeRecentLogComponents(source.getServer(), count);
        for (Component line : lines) {
            source.sendSuccess(() -> line, false);
        }
        return lines.size();
    }

    private static int gachaPity(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> GachaManager.describePityComponent(player), false);
        return 1;
    }

    private static int rescan(CommandSourceStack source) {
        GachaPoolScanner.rescan();
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.gacha.rescanned"), true);
        return 1;
    }

    private static int poolList(CommandSourceStack source, String rarityId) {
        GachaRarity rarity = GachaRarity.fromString(rarityId);
        String joined = String.join(", ", GachaPoolScanner.getPool().get(rarity));
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.gacha.pool", rarity.getTranslatedName(), joined), false);
        return GachaPoolScanner.getPool().get(rarity).size();
    }

    private static int dexGui(CommandSourceStack source, ServerPlayer player) {
        YoikoDexMenu.open(player);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.dex.opened", player.getDisplayName()), true);
        return 1;
    }

    private static int currencyBalance(CommandSourceStack source, ServerPlayer player) {
        long gold = CurrencyManager.balance(player, CurrencyType.GOLD);
        long gems = CurrencyManager.balance(player, CurrencyType.GEM);
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.currency.balance",
                player.getDisplayName(),
                gold,
                gems
        ), false);
        return 1;
    }

    private static int currencyChange(CommandSourceStack source, ServerPlayer player, String rawType, long amount, String operation) {
        CurrencyType type = CurrencyType.fromString(rawType);
        if (type == null) {
            source.sendFailure(Component.translatable("yoiko_core.command.currency.invalid_type", rawType));
            return 0;
        }
        long before = CurrencyManager.balance(player, type);
        boolean success = switch (operation) {
            case "give" -> {
                long balance = CurrencyManager.balance(player, type);
                if (balance > CurrencyManager.MAX_BALANCE - amount) {
                    yield false;
                }
                yield CurrencyManager.add(player, type, amount) == amount;
            }
            case "take" -> CurrencyManager.take(player, type, amount);
            case "set" -> {
                CurrencyManager.set(player, type, amount);
                yield true;
            }
            default -> false;
        };
        if (!success) {
            source.sendFailure(Component.translatable(
                    "yoiko_core.command.currency.failed",
                    player.getDisplayName(),
                    type.name().toLowerCase(java.util.Locale.ROOT),
                    amount
            ));
            return 0;
        }
        long after = CurrencyManager.balance(player, type);
        long delta = after - before;
        if (delta != 0L) {
            String action = type == CurrencyType.GOLD
                    ? (delta > 0L ? "ADMIN_GOLD_CREATED" : "ADMIN_GOLD_REMOVED")
                    : (delta > 0L ? "ADMIN_GEMS_CREATED" : "ADMIN_GEMS_REMOVED");
            ServerYoikoAuditSavedData.get(source.getServer()).addOperational(
                    "ECONOMY", action, player.getUUID(), player.getGameProfile().getName(),
                    "amount=" + Math.abs(delta) + ";operation=" + operation + ";operator=" + source.getTextName());
        }
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.currency.changed",
                player.getDisplayName(),
                type.name().toLowerCase(java.util.Locale.ROOT),
                CurrencyManager.balance(player, type)
        ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int poolReload(CommandSourceStack source) {
        GachaPoolScanner.load();
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.gacha.reloaded"), true);
        return 1;
    }

    private static int dexSummary(CommandSourceStack source) {
        source.sendSuccess(GachaPoolScanner::describeSummaryComponent, false);
        return 1;
    }

    private static int dexList(CommandSourceStack source, String rarityId, int limit) {
        GachaRarity rarity = GachaRarity.fromString(rarityId);
        List<String> species = GachaPoolScanner.list(rarity, limit);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.dex.list", rarity.getTranslatedName(), species.size(), formatSpeciesList(species)), false);
        return species.size();
    }

    private static int dexSearch(CommandSourceStack source, String query, int limit) {
        List<String> species = GachaPoolScanner.search(query, limit);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.dex.search", species.size(), formatSpeciesList(species)), false);
        return species.size();
    }

    private static int dexInfo(CommandSourceStack source, String species) {
        source.sendSuccess(() -> GachaPoolScanner.describeSpeciesComponent(species), false);
        return 1;
    }

    private static String formatSpeciesList(List<String> species) {
        return species.isEmpty() ? "-" : String.join(", ", species);
    }

    private static int claimDaily(CommandSourceStack source, ServerPlayer player) {
        return RewardManager.claimDaily(player) ? 1 : 0;
    }

    private static int giveDailyKeys(CommandSourceStack source, ServerPlayer player) {
        int added = RewardManager.grantDailyBonusKeys(player, true);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.reward.keys_granted", player.getDisplayName(), added), true);
        return 1;
    }

    private static int resetDailyBoxes(CommandSourceStack source, ServerPlayer player) {
        RewardManager.resetDailyBonusBoxes(player);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.reward.boxes_reset", player.getDisplayName()), true);
        return 1;
    }

    private static int resetDailyStreak(CommandSourceStack source, ServerPlayer player) {
        RewardManager.resetStreakReward(player);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.reward.streak_reset", player.getDisplayName()), true);
        return 1;
    }

    private static int claimFirst(CommandSourceStack source, ServerPlayer player) {
        return RewardManager.claimFirst(player) ? 1 : 0;
    }

    private static int resetFirst(CommandSourceStack source, ServerPlayer player) {
        RewardManager.resetFirst(player);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.reward.first_reset", player.getDisplayName()), true);
        return 1;
    }

    private static int rewardStatus(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> RewardManager.statusComponent(player), false);
        return 1;
    }

    private static int rankGrant(CommandSourceStack source, ServerPlayer player, String rankId) {
        boolean success = RankManager.grant(player, rankId);
        source.sendSuccess(() -> Component.translatable(success
                ? "yoiko_core.command.rank.granted"
                : "yoiko_core.command.rank.unknown", rankId, player.getDisplayName()), true);
        return success ? 1 : 0;
    }

    private static int rankRevoke(CommandSourceStack source, ServerPlayer player, String rankId) {
        boolean success = RankManager.revoke(player, rankId);
        source.sendSuccess(() -> Component.translatable(success
                ? "yoiko_core.command.rank.revoked"
                : "yoiko_core.command.rank.not_owned", rankId, player.getDisplayName()), true);
        return success ? 1 : 0;
    }

    private static int rankSet(CommandSourceStack source, ServerPlayer player, String rankId) {
        if ("none".equalsIgnoreCase(rankId)) {
            return rankClear(source, player);
        }
        boolean success = RankManager.setActive(player, rankId);
        source.sendSuccess(() -> Component.translatable(success
                ? "yoiko_core.command.rank.activated"
                : "yoiko_core.command.rank.cannot_activate", rankId, player.getDisplayName()), true);
        return success ? 1 : 0;
    }

    private static int rankClear(CommandSourceStack source, ServerPlayer player) {
        boolean success = RankManager.unequip(player);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.rank.cleared", player.getDisplayName()), true);
        return success ? 1 : 0;
    }

    private static int rankList(CommandSourceStack source, ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.rank.list", player.getDisplayName(),
                String.join(", ", data.ownedRanks), data.activeRank), false);
        return data.ownedRanks.size();
    }

    private static int relicGive(
            CommandSourceStack source,
            ServerPlayer player,
            String relicId,
            String rarityId,
            String secondaryEffect
    ) {
        RelicRarity rarity = RelicRarity.parse(rarityId).orElse(null);
        if (rarity == null) {
            source.sendFailure(Component.translatable("yoiko_core.command.relic.invalid_rarity", rarityId));
            return 0;
        }
        if (RelicManager.get(relicId) == null) {
            source.sendFailure(Component.translatable(
                    "yoiko_core.command.relic.unknown", relicId));
            return 0;
        }
        if (!RelicManager.isValidSecondaryEffectOption(relicId, secondaryEffect)) {
            source.sendFailure(Component.translatable(
                    "yoiko_core.command.relic.invalid_secondary_effect", secondaryEffect));
            return 0;
        }
        boolean success = RelicManager.grant(player, relicId, rarity, secondaryEffect);
        source.sendSuccess(() -> Component.translatable(success
                ? "yoiko_core.command.relic.granted"
                : "yoiko_core.command.relic.unknown", relicId, player.getDisplayName()), true);
        return success ? 1 : 0;
    }

    private static int relicGiveRandom(
            CommandSourceStack source,
            ServerPlayer player,
            String rarityId
    ) {
        RelicRarity rarity = RelicRarity.parse(rarityId).orElse(null);
        if (rarity == null) {
            source.sendFailure(Component.translatable("yoiko_core.command.relic.invalid_rarity", rarityId));
            return 0;
        }
        String relicId = RelicManager.grantRandom(player, rarity);
        if (relicId.isBlank()) {
            source.sendFailure(Component.translatable(
                    "yoiko_core.command.relic.random_failed", rarityId, player.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.relic.random_granted", relicId, rarityId, player.getDisplayName()), true);
        return 1;
    }

    private static int relicRemove(CommandSourceStack source, ServerPlayer player, String relicUuid) {
        UUID uuid = parseUuid(source, relicUuid);
        if (uuid == null) {
            return 0;
        }
        boolean success = RelicManager.remove(player, uuid);
        source.sendSuccess(() -> Component.translatable(success
                ? "yoiko_core.command.relic.removed"
                : "yoiko_core.command.relic.not_found", relicUuid, player.getDisplayName()), true);
        return success ? 1 : 0;
    }

    private static int relicUpgrade(CommandSourceStack source, ServerPlayer player, String relicUuid, boolean useProtection) {
        UUID uuid = parseUuid(source, relicUuid);
        if (uuid == null) {
            return 0;
        }
        boolean success = RelicManager.upgrade(player, uuid, useProtection);
        source.sendSuccess(() -> Component.translatable(success
                ? "yoiko_core.command.relic.upgrade_processed"
                : "yoiko_core.command.relic.upgrade_failed", relicUuid, player.getDisplayName()), true);
        return success ? 1 : 0;
    }

    private static int relicList(CommandSourceStack source, ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        StringJoiner joiner = new StringJoiner(", ");
        for (PlayerYoikoData.RelicInstance relic : data.ownedRelics) {
            RelicData relicData = RelicManager.get(relic.relicId);
            double value = relicData == null ? 0.0D : relicData.value(relic.rarity, relic.level);
            String secondary = relic.secondaryEffect.isBlank() ? "none" : relic.secondaryEffect;
            joiner.add(relic.uuid + "=" + relic.relicId + " " + relic.rarity + " +" + relic.level
                    + " value=" + String.format("%.2f", value) + " secondary=" + secondary);
        }
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.relic.list", player.getDisplayName(), joiner.toString()), false);
        return data.ownedRelics.size();
    }

    private static UUID parseUuid(CommandSourceStack source, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("yoiko_core.command.invalid_uuid", value));
            return null;
        }
    }

    private static int cosmeticGrant(CommandSourceStack source, ServerPlayer player, String cosmeticId) {
        if ("all".equalsIgnoreCase(cosmeticId)) {
            return cosmeticGrantAll(source, player);
        }
        boolean success = CosmeticManager.grant(player, cosmeticId, true);
        source.sendSuccess(() -> Component.translatable(success
                ? "yoiko_core.command.cosmetic.granted"
                : "yoiko_core.command.cosmetic.unknown", cosmeticId, player.getDisplayName()), true);
        return success ? 1 : 0;
    }

    private static int cosmeticGrantAll(CommandSourceStack source, ServerPlayer player) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        int added = 0;
        for (String cosmeticId : CosmeticManager.ids()) {
            var cosmetic = CosmeticManager.get(cosmeticId);
            if (cosmetic != null && data.ownedCosmetics.add(cosmeticId)) {
                added++;
            }
        }
        if (added > 0) {
            savedData.markDirty(player);
        }
        int granted = added;
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.cosmetic.all_granted",
                granted, player.getDisplayName()), true);
        return added;
    }

    private static int marketAudit(CommandSourceStack source, int count) {
        List<MarketplaceTransactionRecord> records = EconomyManager.auditHistory(source.getServer(), count);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.audit.market.header", records.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (MarketplaceTransactionRecord record : records) {
            source.sendSuccess(() -> Component.translatable("yoiko_core.command.audit.market.entry",
                    record.action(),
                    record.sellerName().isBlank() ? "-" : record.sellerName(),
                    record.buyerName().isBlank() ? "-" : record.buyerName(),
                    record.item().getHoverName(),
                    record.item().getCount(),
                    String.format(java.util.Locale.ROOT,"%,d",record.totalPrice()),
                    record.suspicious() ? Component.translatable("yoiko_core.command.audit.market.suspicious") : Component.empty()
            ).withStyle(record.suspicious() ? ChatFormatting.RED : ChatFormatting.GRAY), false);
        }
        return records.size();
    }

    private static int marketAuditFiltered(CommandSourceStack source, String playerName, String itemId,
                                           boolean suspiciousOnly, int page) {
        final int pageSize = 10;
        String normalizedPlayer = playerName == null ? "" : playerName.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedItem = itemId == null ? "" : itemId.trim().toLowerCase(java.util.Locale.ROOT);
        List<MarketplaceTransactionRecord> filtered = ServerYoikoAuditSavedData.get(source.getServer())
                .marketplaceTransactions().stream()
                .filter(record -> !suspiciousOnly || record.suspicious())
                .filter(record -> normalizedPlayer.isBlank()
                        || record.sellerName().toLowerCase(java.util.Locale.ROOT).contains(normalizedPlayer)
                        || record.buyerName().toLowerCase(java.util.Locale.ROOT).contains(normalizedPlayer))
                .filter(record -> normalizedItem.isBlank()
                        || BuiltInRegistries.ITEM.getKey(record.item().getItem()).toString()
                        .toLowerCase(java.util.Locale.ROOT).contains(normalizedItem))
                .toList();
        int totalPages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        int safePage = Math.min(Math.max(1, page), totalPages);
        int from = Math.min((safePage - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.audit.market.page",
                safePage, totalPages, filtered.size()).withStyle(ChatFormatting.GOLD), false);
        for (MarketplaceTransactionRecord record : filtered.subList(from, to)) {
            source.sendSuccess(() -> Component.translatable("yoiko_core.command.audit.market.entry",
                    record.action(),
                    record.sellerName().isBlank() ? "-" : record.sellerName(),
                    record.buyerName().isBlank() ? "-" : record.buyerName(),
                    BuiltInRegistries.ITEM.getKey(record.item().getItem()),
                    record.item().getCount(),
                    String.format(java.util.Locale.ROOT,"%,d",record.totalPrice()),
                    record.suspicious() ? Component.translatable("yoiko_core.command.audit.market.suspicious") : Component.empty()
            ).withStyle(record.suspicious() ? ChatFormatting.RED : ChatFormatting.GRAY), false);
        }
        return to - from;
    }

    private static int mailboxAudit(CommandSourceStack source, int page) {
        final int pageSize = 10;
        List<OperationalAuditRecord> records = ServerYoikoAuditSavedData.get(source.getServer())
                .operationalRecords().stream()
                .filter(record -> "MAIL".equals(record.category()))
                .toList();
        int totalPages = Math.max(1, (records.size() + pageSize - 1) / pageSize);
        int safePage = Math.min(Math.max(1, page), totalPages);
        int from = Math.min((safePage - 1) * pageSize, records.size());
        int to = Math.min(from + pageSize, records.size());
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.audit.mailbox.page",
                safePage, totalPages, records.size()).withStyle(ChatFormatting.GOLD), false);
        for (OperationalAuditRecord record : records.subList(from, to)) {
            source.sendSuccess(() -> Component.literal(record.action() + " | "
                    + (record.playerName().isBlank() ? record.playerUuid() : record.playerName())
                    + " | " + record.detail()).withStyle(
                    record.action().contains("REJECTED") || record.action().contains("FAILED")
                            ? ChatFormatting.RED : ChatFormatting.GRAY), false);
        }
        return to - from;
    }

    private static int mailboxQueue(CommandSourceStack source, ServerPlayer player) {
        MailboxManager.QueueStatus status = MailboxManager.queueStatus(player);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.mailbox.queue",
                player.getGameProfile().getName(), status.visible(), PlayerYoikoData.MAX_MAILBOX_MAILS,
                status.queued(), PlayerYoikoData.MAX_MAILBOX_OVERFLOW, status.removable()).withStyle(
                status.queued() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN), false);
        return status.queued();
    }

    private static int mailboxRecover(CommandSourceStack source, ServerPlayer player) {
        MailboxManager.QueueStatus status = MailboxManager.recoverQueue(player);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.mailbox.queue_recovered",
                player.getGameProfile().getName(), status.visible(), status.queued(), status.removable())
                .withStyle(status.queued() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN), true);
        return status.queued();
    }

    private static int particleStats(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.particle.client_rendered")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int cosmeticRevoke(CommandSourceStack source, ServerPlayer player, String cosmeticId) {
        boolean success = CosmeticManager.revoke(player, cosmeticId);
        source.sendSuccess(() -> Component.translatable(success
                ? "yoiko_core.command.cosmetic.revoked"
                : "yoiko_core.command.cosmetic.not_owned", cosmeticId, player.getDisplayName()), true);
        return success ? 1 : 0;
    }

    private static int cosmeticEquip(CommandSourceStack source, ServerPlayer player, String cosmeticId) {
        boolean success = CosmeticManager.equip(player, cosmeticId);
        source.sendSuccess(() -> Component.translatable(success
                ? "yoiko_core.command.cosmetic.equipped"
                : "yoiko_core.command.cosmetic.cannot_equip", cosmeticId, player.getDisplayName()), true);
        return success ? 1 : 0;
    }

    private static int cosmeticUnequip(CommandSourceStack source, ServerPlayer player, String type) {
        CosmeticEquipSlot slot = CosmeticEquipSlot.fromString(type);
        if (slot == null) {
            return 0;
        }
        CosmeticManager.unequip(player, slot);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.cosmetic.unequipped", type, player.getDisplayName()), true);
        return 1;
    }

    private static int cosmeticList(CommandSourceStack source, ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.cosmetic.list", player.getDisplayName(),
                String.join(", ", data.ownedCosmetics)), false);
        return data.ownedCosmetics.size();
    }

    private static int giveStack(CommandSourceStack source, ServerPlayer player, ItemStack stack, String label) {
        giveItem(player, stack);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.item.given", Component.translatable(label),
                stack.getCount(), player.getDisplayName()), true);
        return stack.getCount();
    }

    private static int giveRelicUpgradeCrystals(CommandSourceStack source, ServerPlayer player, int count) {
        RelicManager.addUpgradeCrystals(player, count);
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.relic.upgrade_crystals_given", count, player.getDisplayName()), true);
        return count;
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        if (!player.getInventory().add(copy)) {
            player.drop(copy, false);
        }
    }

    private static List<ItemStack> splitStack(ItemStack stack, int count) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        int maxStackSize = Math.max(1, Math.min(99, stack.getMaxStackSize()));
        int remaining = count;
        while (remaining > 0) {
            ItemStack copy = stack.copy();
            int amount = Math.min(maxStackSize, remaining);
            copy.setCount(amount);
            stacks.add(copy);
            remaining -= amount;
        }
        return stacks;
    }

    private static ItemStack ticketFor(GachaType type, int count) {
        return switch (type) {
            case LEGENDARY -> YoikoItems.LEGENDARY_POKEMON_GACHA_TICKET.toStack(count);
            case SHINY_ALL -> YoikoItems.SHINY_ALL_POKEMON_GACHA_TICKET.toStack(count);
            default -> YoikoItems.ALL_POKEMON_GACHA_TICKET.toStack(count);
        };
    }
}
