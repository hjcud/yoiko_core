package com.yoiko.core.item;

import com.yoiko.core.menu.YoikoRelicDexMenu;
import com.yoiko.core.relic.RelicAppraisalCategory;
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

public class RelicGachaTicketItem extends Item {
    private final RelicAppraisalCategory appraisalCategory;

    public RelicGachaTicketItem(Properties properties) {
        this(properties, RelicAppraisalCategory.ALL);
    }

    public RelicGachaTicketItem(Properties properties, RelicAppraisalCategory appraisalCategory) {
        super(properties);
        this.appraisalCategory = appraisalCategory == null ? RelicAppraisalCategory.ALL : appraisalCategory;
    }

    public RelicAppraisalCategory appraisalCategory() {
        return appraisalCategory;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                YoikoRelicDexMenu.open(serverPlayer, appraisalCategory);
            } else {
                RelicGachaAnimationManager.start(serverPlayer, stack, appraisalCategory);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (appraisalCategory == RelicAppraisalCategory.ALL) {
            tooltip.add(Component.translatable("tooltip.yoiko_core.relic_gacha_ticket").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(
                    "tooltip.yoiko_core.relic_gacha_ticket.category",
                    Component.translatable(appraisalCategory.translationKey())
            ).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.yoiko_core.relic_gacha_ticket.category_radiant")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.yoiko_core.relic_gacha_ticket.roll").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.yoiko_core.relic_gacha_ticket.dex").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
