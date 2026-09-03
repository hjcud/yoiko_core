package com.yoiko.core.item;

import com.yoiko.core.turtle.TurtleBannerSchedule;
import com.yoiko.core.turtle.TurtleGenerationService;
import com.yoiko.core.turtle.TurtleTicketType;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class TurtleHatchTicketItem extends Item {
    private final TurtleTicketType type;
    public TurtleHatchTicketItem(Properties properties,TurtleTicketType type){super(properties);this.type=type;}
    public TurtleTicketType ticketType(){return type;}

    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);if(level.isClientSide())return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer serverPlayer))return InteractionResultHolder.pass(stack);
        try{
            CustomData data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY);if(data.contains("yoiko_owner")&&!data.copyTag().getString("yoiko_owner").equals(serverPlayer.getUUID().toString()))throw com.yoiko.core.turtle.TurtleLocalizedException.of("yoiko_core.turtle.error.ticket_owner_only");
            if(type.requiresStrategySelection()){
                com.yoiko.core.turtle.TurtleStrategyTicketService.open(serverPlayer,hand);
                return InteractionResultHolder.consume(stack);
            }
            var banner=TurtleBannerSchedule.current();
            TurtleGenerationService.hatchFromItem(serverPlayer,new TurtleGenerationService.HatchRequest(type,banner.activeSkill(),serverPlayer.getRandom().nextLong()));
            if(!serverPlayer.isCreative())stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }catch(RuntimeException exception){serverPlayer.sendSystemMessage(com.yoiko.core.turtle.TurtleLocalizedException.component(exception,"yoiko_core.turtle.error.request_failed").copy().withStyle(ChatFormatting.RED));return InteractionResultHolder.fail(stack);}
    }

    public static void bindTo(ItemStack stack,java.util.UUID owner){net.minecraft.nbt.CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putString("yoiko_owner",owner.toString());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}

    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        tooltip.add(Component.translatable("tooltip.yoiko_core.turtle_hatch_ticket."+type.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.yoiko_core.turtle_hatch_ticket.use").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.yoiko_core.turtle_hatch_ticket.no_trade").withStyle(ChatFormatting.RED));
    }
}
