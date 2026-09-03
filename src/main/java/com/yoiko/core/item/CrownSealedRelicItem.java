package com.yoiko.core.item;

import com.yoiko.core.relic.RelicGachaAnimationManager;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Event-exclusive sealed relic awarded to every Crown Rabbit contributor. */
public final class CrownSealedRelicItem extends Item {
    public CrownSealedRelicItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            RelicGachaAnimationManager.startCrown(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.yoiko_core.crown_sealed_relic")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.yoiko_core.crown_sealed_relic.rarity")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.yoiko_core.crown_sealed_relic.jump")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.yoiko_core.relic_gacha_ticket.roll")
                .withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
