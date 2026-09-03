package com.yoiko.core.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class TurtleTrainingResetTicketItem extends Item {
    public TurtleTrainingResetTicketItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.yoiko_core.turtle_training_reset_ticket").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.yoiko_core.turtle_training_reset_ticket.use").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.yoiko_core.turtle_hatch_ticket.no_trade").withStyle(ChatFormatting.RED));
    }
}
