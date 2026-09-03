package com.yoiko.core.turtle;

import com.yoiko.core.item.TurtleHatchTicketItem;
import com.yoiko.core.network.OpenTurtleStrategyTicketPayload;
import com.yoiko.core.network.TurtleStrategyTicketChoosePayload;
import com.yoiko.core.registry.YoikoItems;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative selection transaction for the unified strategy egg. */
public final class TurtleStrategyTicketService {
    private static final long SELECTION_TIMEOUT_TICKS = 60L * 20L;
    private static final TurtleStrategySelectionGuard SELECTIONS = new TurtleStrategySelectionGuard();

    private TurtleStrategyTicketService() { }

    public static void open(ServerPlayer player, InteractionHand hand) {
        TurtleHatchPendingService.ensureReady(player);
        ItemStack stack = player.getItemInHand(hand);
        requireSelectableEgg(player, stack);
        UUID sessionId = SELECTIONS.open(player.getUUID(),hand,
                (long) player.server.getTickCount() + SELECTION_TIMEOUT_TICKS);
        PacketDistributor.sendToPlayer(player, new OpenTurtleStrategyTicketPayload(sessionId));
    }

    public static void choose(ServerPlayer player, TurtleStrategyTicketChoosePayload payload) {
        TurtleStrategySelectionGuard.Pending pending = SELECTIONS.claim(player.getUUID(),payload.sessionId(),
                player.server.getTickCount());
        try {
            if (pending == null) {
                throw TurtleLocalizedException.of("yoiko_core.turtle.error.strategy_ticket_expired");
            }
            TurtleTicketType resolved = switch (payload.strategy()) {
                case "FRONT" -> TurtleTicketType.RARE_FRONT;
                case "STEADY" -> TurtleTicketType.RARE_STEADY;
                case "FOLLOW" -> TurtleTicketType.RARE_FOLLOW;
                case "CLOSER" -> TurtleTicketType.RARE_CLOSER;
                default -> throw TurtleLocalizedException.of("yoiko_core.turtle.error.unsupported_strategy");
            };
            ItemStack stack = player.getItemInHand(pending.hand());
            requireSelectableEgg(player, stack);
            TurtleBannerSchedule.Banner banner = TurtleBannerSchedule.current();
            TurtleGenerationService.hatchFromItem(player, new TurtleGenerationService.HatchRequest(
                    resolved, banner.activeSkill(), player.getRandom().nextLong()));
            if (!player.isCreative()) stack.shrink(1);
        } catch (RuntimeException exception) {
            player.sendSystemMessage(TurtleLocalizedException.component(exception,
                    "yoiko_core.turtle.error.request_failed").copy().withStyle(ChatFormatting.RED));
        }
    }

    public static void cancel(ServerPlayer player, UUID sessionId) {
        SELECTIONS.cancel(player.getUUID(),sessionId);
    }

    public static void clear(UUID playerId) {
        SELECTIONS.clear(playerId);
    }

    private static void requireSelectableEgg(ServerPlayer player, ItemStack stack) {
        if (!stack.is(YoikoItems.TURTLE_RARE_STRATEGY_TICKET.get())) {
            throw TurtleLocalizedException.of("yoiko_core.turtle.error.strategy_ticket_missing");
        }
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.contains("yoiko_owner")
                && !data.copyTag().getString("yoiko_owner").equals(player.getUUID().toString())) {
            throw TurtleLocalizedException.of("yoiko_core.turtle.error.ticket_owner_only");
        }
        if (!(stack.getItem() instanceof TurtleHatchTicketItem ticket)
                || !ticket.ticketType().requiresStrategySelection()) {
            throw TurtleLocalizedException.of("yoiko_core.turtle.error.strategy_ticket_missing");
        }
    }
}
