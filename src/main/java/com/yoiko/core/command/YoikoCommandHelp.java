package com.yoiko.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Searchable, paged help for the public command roots.
 *
 * <p>The lists intentionally describe stable command shapes rather than every optional-argument expansion. Keep
 * {@code docs/COMMANDS.md} in sync when a command is added or removed.</p>
 */
final class YoikoCommandHelp {
    private static final int PAGE_SIZE = 8;

    private static final List<HelpCategory> ADMIN_CATEGORIES = List.of(
            category("system", "yoiko_core.command.help.category.system",
                    "/yoiko reload",
                    "/yoiko data save",
                    "/yoiko debug particle stats"),
            category("events", "yoiko_core.command.help.category.events",
                    "/yoiko newspaper <status|preview|send>",
                    "/yoiko incident status",
                    "/yoiko incident personal treasure_rabbit status [player]",
                    "/yoiko incident personal treasure_rabbit start <golden|radiant|mirror|crown> [player]",
                    "/yoiko incident personal treasure_rabbit stop [player]",
                    "/yoiko incident server daily <status|clear>",
                    "/yoiko incident server daily set <event_id>",
                    "/yoiko incident server breaking <status|stop>",
                    "/yoiko incident server breaking start <fishing|rabbits|capture|outbreak> [minutes]",
                    "/yoiko event <status|clear>",
                    "/yoiko event set <event_id>",
                    "/yoiko breaking <status|stop>",
                    "/yoiko breaking start <fishing|rabbits|capture|outbreak> [minutes]"),
            category("economy", "yoiko_core.command.help.category.economy",
                    "/yoiko economy stats",
                    "/yoiko economy dashboard [days]",
                    "/yoiko economy flow [days]",
                    "/yoiko economy daily [days]",
                    "/yoiko economy wealth",
                    "/yoiko market audit [count]",
                    "/yoiko market audit suspicious [page]",
                    "/yoiko market audit player <name> [page]",
                    "/yoiko market audit item <id> [page]",
                    "/yoiko currency balance <player>",
                    "/yoiko currency give <player> <gold|gem> <amount>",
                    "/yoiko currency take <player> <gold|gem> <amount>",
                    "/yoiko currency set <player> <gold|gem> <amount>"),
            category("player", "yoiko_core.command.help.category.player",
                    "/yoiko mailbox audit [page]",
                    "/yoiko mailbox queue <player>",
                    "/yoiko mailbox recover <player>",
                    "/yoiko mailbox open <player>",
                    "/yoiko mailbox send item <player> <item> <count> [message]",
                    "/yoiko mailbox send message <player> <message>",
                    "/yoiko storage open <player>",
                    "/yoiko storage status <player>",
                    "/yoiko reward claim <daily|first> <player>",
                    "/yoiko reward give keys <player>",
                    "/yoiko reward reset <boxes|streak|first> <player>",
                    "/yoiko reward status <player>",
                    "/yoiko rank grant <player> <rank>",
                    "/yoiko rank revoke <player> <rank>",
                    "/yoiko rank set <player> <rank|none>",
                    "/yoiko rank list <player>"),
            category("content", "yoiko_core.command.help.category.content",
                    "/yoiko gacha give <player> <type> <count>",
                    "/yoiko gacha give all <type> <count>",
                    "/yoiko gacha roll <player> <type>",
                    "/yoiko gacha history [count]",
                    "/yoiko gacha pity <player>",
                    "/yoiko gacha pool list <rarity>",
                    "/yoiko gacha pool <rescan|reload>",
                    "/yoiko dex open [player]",
                    "/yoiko dex summary",
                    "/yoiko dex list <rarity> [limit]",
                    "/yoiko dex search <query> [limit]",
                    "/yoiko dex info <species>",
                    "/yoiko relic give <player> random <rarity>",
                    "/yoiko relic give <player> <relic_id> <rarity> [secondary_effect]",
                    "/yoiko relic remove <player> <relic_uuid>",
                    "/yoiko relic upgrade <player> <relic_uuid> [use_protection]",
                    "/yoiko relic ticket give <player> <count>",
                    "/yoiko relic crystal give <player> <count>",
                    "/yoiko relic list <player>",
                    "/yoiko cosmetic grant <player> <cosmetic_id|all>",
                    "/yoiko cosmetic revoke <player> <cosmetic_id>",
                    "/yoiko cosmetic equip <player> <cosmetic_id>",
                    "/yoiko cosmetic unequip <player> <HEAD|CHEST|FEET>",
                    "/yoiko cosmetic list <player>"),
            category("race", "yoiko_core.command.help.category.race",
                    "/yoiko race status",
                    "/yoiko race force-start",
                    "/yoiko race force-stop [reason]",
                    "/yoiko race replay last",
                    "/yoiko race effect-preview <skill|all|stop>",
                    "/yoiko race course inspect",
                    "/yoiko race course inspect <visual|lanes|racing-line|clear>",
                    "/yoiko race training give <player> <count>",
                    "/yoiko race awakening give <player> <index> <count>",
                    "/yoiko race medal give <player> <count>",
                    "/yoiko race race-points give <player> <index> <count>",
                    "/yoiko race arena set-center",
                    "/yoiko race arena cleanup",
                    "/yoiko race arena preview <seed> <theme>",
                    "/yoiko race ticket give <player> <type> <count>",
                    "/yoiko race manual start <mode> <league> <minutes> <theme> [weather]",
                    "/yoiko race manual prepare <mode> <league> <minutes> <theme> [weather]",
                    "/yoiko race manual confirm <plan>",
                    "/yoiko race manual cancel <reason>"),
            category("test", "yoiko_core.command.help.category.test",
                    "/yoiko treasure_rabbit summon <variant> [player]",
                    "/yoiko treasure_rabbit ticket give <player> <count>"));

    private static final List<String> TURTLE_COMMANDS = List.of(
            "/turtle",
            "/turtle menu",
            "/turtle status",
            "/turtle intro-ticket",
            "/turtle list [page]",
            "/turtle train <index> <type>",
            "/turtle rename <index> <name>",
            "/turtle lock <index> <on|off>",
            "/turtle strategy <index> <strategy>",
            "/turtle companion <index> <companion>",
            "/turtle register <index>",
            "/turtle unregister",
            "/turtle time-trial status",
            "/turtle time-trial start <index> <d|c|b|a|s>",
            "/turtle time-trial cancel",
            "/turtle bet <heat> <candidate> <amount>");

    private static final List<String> PROFILE_COMMANDS = List.of(
            "/yoikoprofile",
            "/yoikoprofile sharing on",
            "/yoikoprofile sharing off",
            "/yoikoprofile view <player>");

    private YoikoCommandHelp() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminHelpCommand() {
        return Commands.literal("help")
                .executes(context -> showAdminIndex(context.getSource()))
                .then(Commands.argument("category", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                ADMIN_CATEGORIES.stream().map(HelpCategory::id), builder))
                        .executes(context -> showAdminCategory(
                                context.getSource(), StringArgumentType.getString(context, "category"), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> showAdminCategory(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "category"),
                                        IntegerArgumentType.getInteger(context, "page")))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> turtleHelpCommand() {
        return Commands.literal("help")
                .executes(context -> showPage(
                        context.getSource(), "yoiko_core.command.help.turtle.header", TURTLE_COMMANDS, 1,
                        "/turtle help"))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> showPage(
                                context.getSource(), "yoiko_core.command.help.turtle.header", TURTLE_COMMANDS,
                                IntegerArgumentType.getInteger(context, "page"), "/turtle help")));
    }

    static LiteralArgumentBuilder<CommandSourceStack> profileHelpCommand() {
        return Commands.literal("help")
                .executes(context -> showPage(
                        context.getSource(), "yoiko_core.command.help.profile.header", PROFILE_COMMANDS, 1,
                        "/yoikoprofile help"));
    }

    static int showAdminIndex(CommandSourceStack source) {
        int commandCount = ADMIN_CATEGORIES.stream().mapToInt(category -> category.usages().size()).sum();
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.help.admin.header", ADMIN_CATEGORIES.size(), commandCount)
                .withStyle(ChatFormatting.GOLD), false);
        for (HelpCategory category : ADMIN_CATEGORIES) {
            String command = "/yoiko help " + category.id();
            MutableComponent line = clickable(command)
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(category.titleKey()).withStyle(ChatFormatting.GRAY));
            source.sendSuccess(() -> line, false);
        }
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.help.admin.hint")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int showAdminCategory(CommandSourceStack source, String id, int page) {
        HelpCategory category = ADMIN_CATEGORIES.stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElse(null);
        if (category == null) {
            source.sendFailure(Component.translatable(
                    "yoiko_core.command.help.unknown_category",
                    id,
                    String.join(", ", ADMIN_CATEGORIES.stream().map(HelpCategory::id).toList())));
            return 0;
        }
        return showPage(source, category.titleKey(), category.usages(), page, "/yoiko help " + id);
    }

    private static int showPage(
            CommandSourceStack source,
            String titleKey,
            List<String> usages,
            int page,
            String pageCommand) {
        int pageCount = Math.max(1, (usages.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pageCount) {
            source.sendFailure(Component.translatable("yoiko_core.command.help.invalid_page", page, pageCount));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.help.page_header", Component.translatable(titleKey), page, pageCount)
                .withStyle(ChatFormatting.GOLD), false);
        int start = (page - 1) * PAGE_SIZE;
        for (int index = start; index < Math.min(usages.size(), start + PAGE_SIZE); index++) {
            MutableComponent line = Component.literal("  ").append(clickable(usages.get(index)));
            source.sendSuccess(() -> line, false);
        }
        if (page < pageCount) {
            String nextCommand = pageCommand + " " + (page + 1);
            MutableComponent next = Component.translatable("yoiko_core.command.help.next_page")
                    .append(Component.literal(" "))
                    .append(clickable(nextCommand));
            source.sendSuccess(() -> next, false);
        }
        source.sendSuccess(() -> Component.translatable("yoiko_core.command.help.click_hint")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static MutableComponent clickable(String usage) {
        return Component.literal(usage).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestionFor(usage))));
    }

    private static String suggestionFor(String usage) {
        int argument = usage.indexOf('<');
        int optional = usage.indexOf('[');
        int cut = argument < 0 ? optional : optional < 0 ? argument : Math.min(argument, optional);
        String prefix = cut < 0 ? usage : usage.substring(0, cut);
        return prefix.stripTrailing() + (cut < 0 ? "" : " ");
    }

    private static HelpCategory category(String id, String titleKey, String... usages) {
        return new HelpCategory(id, titleKey, List.of(usages));
    }

    private record HelpCategory(String id, String titleKey, List<String> usages) {
    }
}
