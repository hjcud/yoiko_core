package com.yoiko.core.relic;

import com.yoiko.core.config.YoikoCommonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Owns the configurable chat policy for relic appraisal results. */
public final class RelicGachaAnnouncements {
    private RelicGachaAnnouncements() {
    }

    public static void announce(
            ServerPlayer player,
            RelicRarity rarity,
            Component relicName,
            Component rarityName,
            Component personalResult,
            boolean allowPersonalResult
    ) {
        boolean broadcast = YoikoCommonConfig.RELIC_GACHA_RARE_BROADCAST_ENABLED.get()
                && rarity.ordinal() >= minimumBroadcastRarity().ordinal();
        if (allowPersonalResult
                && (!broadcast || YoikoCommonConfig.RELIC_GACHA_PERSONAL_RESULT_MESSAGE.get())) {
            player.sendSystemMessage(personalResult);
        }
        if (broadcast) {
            Component message = Component.translatable(
                    "message.yoiko_core.relic_gacha.broadcast",
                    player.getDisplayName(), relicName, rarityName
            ).withStyle(ChatFormatting.GOLD);
            player.server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    private static RelicRarity minimumBroadcastRarity() {
        return RelicRarity.parse(YoikoCommonConfig.RELIC_GACHA_RARE_BROADCAST_MIN_RARITY.get())
                .orElse(RelicRarity.LEGENDARY);
    }
}
