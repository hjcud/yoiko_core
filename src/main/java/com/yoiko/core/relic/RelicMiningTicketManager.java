package com.yoiko.core.relic;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.reward.YoikoResetClock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Awards relic tickets from ordinary player mining without storing block coordinates. */
public final class RelicMiningTicketManager {
    private static final TagKey<Block> ELIGIBLE_BLOCKS = TagKey.create(
            Registries.BLOCK, YoikoServerCore.id("relic_tablet_mineable"));

    private RelicMiningTicketManager() {
    }

    public static void onBlockBroken(ServerPlayer player, BlockState state) {
        if (player.isCreative() || player.isSpectator() || player instanceof FakePlayer
                || !state.is(ELIGIBLE_BLOCKS)) {
            return;
        }

        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        refreshDailyPeriod(data);
        refreshEasyCredits(data);

        int foundToday = data.relicMiningTicketsToday;
        if (foundToday >= YoikoCommonConfig.RELIC_MINING_DAILY_TICKET_CAP.get()) {
            savedData.markDirty(player);
            return;
        }
        boolean easyCredit = data.relicMiningEasyCredits > 0;
        int pityLimit = pityLimit(foundToday, easyCredit);
        int missesIncludingThisBlock = pityLimit > 0
                ? (data.relicMiningPityMisses >= pityLimit
                        ? pityLimit
                        : data.relicMiningPityMisses + 1)
                : 0;
        boolean pityAward = pityLimit > 0 && missesIncludingThisBlock >= pityLimit;
        boolean naturalAward = player.getRandom().nextDouble() < chance(foundToday, easyCredit);

        if (!pityAward && !naturalAward) {
            if (pityLimit > 0) {
                data.relicMiningPityMisses = missesIncludingThisBlock;
                savedData.markDirty(player);
            }
            return;
        }

        data.relicMiningTicketsToday = foundToday == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : foundToday + 1;
        if (easyCredit) {
            data.relicMiningEasyCredits = Math.max(0, data.relicMiningEasyCredits - 1);
        }
        data.relicMiningPityMisses = 0;
        savedData.markDirty(player);
        giveTicket(player, data.relicMiningTicketsToday, pityAward);
    }

    static double chance(int foundToday, boolean easyCredit) {
        if (easyCredit) {
            return YoikoCommonConfig.RELIC_MINING_EASY_CHANCE.get();
        }
        if (foundToday < 5) {
            return YoikoCommonConfig.RELIC_MINING_MID_CHANCE.get();
        }
        return foundToday < YoikoCommonConfig.RELIC_MINING_DAILY_TICKET_CAP.get()
                ? YoikoCommonConfig.RELIC_MINING_HARD_CHANCE.get() : 0.0D;
    }

    static int pityLimit(int foundToday, boolean easyCredit) {
        if (easyCredit) {
            return YoikoCommonConfig.RELIC_MINING_EASY_PITY_BLOCKS.get();
        }
        if (foundToday < 5) {
            return YoikoCommonConfig.RELIC_MINING_MID_PITY_BLOCKS.get();
        }
        return foundToday < YoikoCommonConfig.RELIC_MINING_DAILY_TICKET_CAP.get()
                ? YoikoCommonConfig.RELIC_MINING_HARD_PITY_BLOCKS.get() : 0;
    }

    private static void refreshDailyPeriod(PlayerYoikoData data) {
        String currentPeriod = YoikoResetClock.dailyPeriodKey(System.currentTimeMillis());
        if (currentPeriod.equals(data.relicMiningPeriodKey)) {
            return;
        }
        data.relicMiningPeriodKey = currentPeriod;
        data.relicMiningTicketsToday = 0;
        data.relicMiningPityMisses = 0;
    }

    private static void refreshEasyCredits(PlayerYoikoData data) {
        long currentDay = YoikoResetClock.dailyPeriodDate(System.currentTimeMillis()).toEpochDay();
        long elapsedDays = data.relicMiningEasyCreditEpochDay <= 0L
                ? 1L : Math.max(0L, currentDay - data.relicMiningEasyCreditEpochDay);
        data.relicMiningEasyCreditEpochDay = currentDay;
        if (elapsedDays <= 0L) {
            return;
        }
        long earned = elapsedDays * (long) YoikoCommonConfig.RELIC_MINING_EASY_CREDITS_PER_DAY.get();
        long total = Math.min(YoikoCommonConfig.RELIC_MINING_EASY_CREDIT_CAP.get(),
                (long) data.relicMiningEasyCredits + earned);
        data.relicMiningEasyCredits = (int) Math.max(0L, total);
    }

    private static void giveTicket(ServerPlayer player, int foundToday, boolean pityAward) {
        ItemStack ticket = YoikoItems.RELIC_GACHA_TICKET.toStack();
        if (!player.getInventory().add(ticket) && !ticket.isEmpty()) {
            player.drop(ticket, false);
        }
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable(
                "yoiko_core.message.relic_mining_ticket.found", foundToday).withStyle(ChatFormatting.GOLD), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.75F, pityAward ? 0.9F : 1.1F);
    }
}
