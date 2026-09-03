package com.yoiko.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.treasure.TreasureRabbitEntity;
import com.yoiko.core.treasure.TreasureRabbitManager;
import com.yoiko.core.treasure.TreasureRabbitVariant;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TreasureRabbitCommand {
    private TreasureRabbitCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommands() {
        return Commands.literal("treasure_rabbit")
                        .then(Commands.literal("summon")
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                new String[]{"golden", "radiant", "mirror", "crown"}, builder))
                                        .executes(context -> summon(
                                                context.getSource(),
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "variant")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> summon(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "variant"))))))
                        .then(Commands.literal("ticket")
                                .then(Commands.literal("give")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
                                                        .executes(context -> giveTicket(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                IntegerArgumentType.getInteger(context, "count")))))));
    }

    private static int summon(CommandSourceStack source, ServerPlayer target, String rawVariant) {
        TreasureRabbitVariant variant = TreasureRabbitVariant.fromString(rawVariant);
        var facing = target.getDirection();
        var pos = target.blockPosition().relative(facing, 2);
        TreasureRabbitEntity rabbit = TreasureRabbitManager.spawn(target.serverLevel(), pos, variant, false);
        if (rabbit == null) {
            source.sendFailure(Component.translatable("yoiko_core.command.treasure_rabbit.summon_failed"));
            return 0;
        }
        rabbit.setEncounterTarget(target.getUUID());
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.treasure_rabbit.summoned", rabbit.getDisplayName(), target.getDisplayName()), true);
        return 1;
    }

    private static int giveTicket(CommandSourceStack source, ServerPlayer target, int count) {
        var remaining = YoikoItems.PARTICLE_GACHA_TICKET.toStack(count);
        if (!target.getInventory().add(remaining)) {
            target.drop(remaining, false);
        }
        source.sendSuccess(() -> Component.translatable(
                "yoiko_core.command.treasure_rabbit.ticket_given", target.getDisplayName(), count), true);
        return count;
    }
}
