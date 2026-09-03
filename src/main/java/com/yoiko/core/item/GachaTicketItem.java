package com.yoiko.core.item;

import com.yoiko.core.gacha.GachaManager;
import com.yoiko.core.gacha.GachaType;
import com.yoiko.core.menu.YoikoDexMenu;
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

public class GachaTicketItem extends Item {
    private final GachaType type;

    public GachaTicketItem(Properties properties, GachaType type) {
        super(properties);
        this.type = type;
    }

    public GachaType getType() {
        return type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                YoikoDexMenu.open(serverPlayer, type);
            } else {
                GachaManager.rollFromTicket(serverPlayer, type, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.yoiko_core.gacha_ticket").withStyle(ChatFormatting.GRAY));
        tooltip.add(type.getTranslatedName().withStyle(type.getColor()));
        if (type == GachaType.LEGENDARY) {
            tooltip.add(Component.translatable("tooltip.yoiko_core.gacha_ticket.event_only")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
