package com.yoiko.core.turtle;

import com.yoiko.core.advancement.YoikoAdvancementManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class TurtleCompanionUnlockService {
    private record Toss(UUID itemId,UUID playerId,int expiresAt,String fingerprint){}
    private record DolphinPickup(UUID playerId,int expiresAt,String fingerprint){}
    private final Map<UUID,Toss> tosses=new HashMap<>();private final Map<UUID,DolphinPickup> dolphins=new HashMap<>();
    private static final Map<TurtleCompanion,String> VANILLA=Map.of(
            TurtleCompanion.AXOLOTL,"minecraft:husbandry/axolotl_in_a_bucket",TurtleCompanion.FROG,"minecraft:husbandry/leash_all_frog_variants",
            TurtleCompanion.BEE,"minecraft:husbandry/safely_harvest_honey",TurtleCompanion.PARROT,"minecraft:adventure/spyglass_at_parrot",
            TurtleCompanion.ARMADILLO,"minecraft:adventure/brush_armadillo",TurtleCompanion.ALLAY,"minecraft:husbandry/allay_deliver_item_to_player",
            TurtleCompanion.GOAT,"minecraft:husbandry/ride_a_boat_with_a_goat",TurtleCompanion.SNIFFER,"minecraft:husbandry/obtain_sniffer_egg");
    private static final Map<TurtleCompanion,String> CUSTOM=Map.of(
            TurtleCompanion.DOLPHIN,"dolphin_playmate",TurtleCompanion.BLUE_AXOLOTL,"blue_axolotl_friend",
            TurtleCompanion.RABBIT,"rabbit_friend",TurtleCompanion.WOLF,"wolf_friend",TurtleCompanion.FOX,"fox_friend");
    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        syncVanilla(player);
        syncCustom(player);
        TurtleRacingSavedData saved = TurtleRacingSavedData.get(player.server);
        TurtlePlayerProgress progress = saved.getOrCreatePlayer(player.getUUID());
        if (progress.officialWins() == 0 && saved.weeklyResults().stream()
                .anyMatch(result -> result.playerId().equals(player.getUUID()) && result.rank() == 1)) {
            progress.recordOfficialWin();
            saved.markChanged();
        }
        TurtleGoldenShellAdvancement.sync(player, progress);
        TurtleAchievementManager.sync(player, progress);
    }
    @SubscribeEvent public void advancement(net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event){if(event.getEntity() instanceof ServerPlayer p)syncVanilla(p);}
    @SubscribeEvent public void tame(AnimalTameEvent event){if(event.getTamer() instanceof ServerPlayer p&&event.getAnimal() instanceof Wolf)unlock(p,TurtleCompanion.WOLF,"wolf_friend");}
    @SubscribeEvent public void breed(BabyEntitySpawnEvent event){if(!(event.getCausedByPlayer() instanceof ServerPlayer p))return;if(event.getParentA() instanceof Rabbit&&event.getParentB() instanceof Rabbit)unlock(p,TurtleCompanion.RABBIT,"rabbit_friend");if(event.getParentA() instanceof Fox&&event.getParentB() instanceof Fox)unlock(p,TurtleCompanion.FOX,"fox_friend");}
    @SubscribeEvent public void interact(PlayerInteractEvent.EntityInteractSpecific event){blueAxolotl(event.getEntity(),event.getTarget(),event.getHand());}
    @SubscribeEvent public void interact(PlayerInteractEvent.EntityInteract event){blueAxolotl(event.getEntity(),event.getTarget(),event.getHand());}
    private void blueAxolotl(Player player,Entity target,InteractionHand hand){if(player instanceof ServerPlayer p&&target instanceof Axolotl axolotl&&axolotl.getVariant()==Axolotl.Variant.BLUE&&p.getItemInHand(hand).is(Items.WATER_BUCKET))unlock(p,TurtleCompanion.BLUE_AXOLOTL,"blue_axolotl_friend");}
    @SubscribeEvent public void toss(ItemTossEvent event){if(tosses.size()>=32||!(event.getPlayer() instanceof ServerPlayer player))return;ItemEntity item=event.getEntity();if(!item.isInWater()&&!player.isInWater()&&!item.level().getFluidState(item.blockPosition()).is(FluidTags.WATER))return;tosses.put(item.getUUID(),new Toss(item.getUUID(),player.getUUID(),player.server.getTickCount()+200,fingerprint(item)));}
    @SubscribeEvent public void join(EntityJoinLevelEvent event){if(!(event.getEntity() instanceof ItemEntity item)||item.getOwner()==null)return;DolphinPickup pickup=dolphins.remove(item.getOwner().getUUID());if(pickup==null||!pickup.fingerprint.equals(fingerprint(item)))return;if(event.getLevel().getServer()!=null){ServerPlayer p=event.getLevel().getServer().getPlayerList().getPlayer(pickup.playerId);if(p!=null)unlock(p,TurtleCompanion.DOLPHIN,"dolphin_playmate");}}
    @SubscribeEvent public void tick(ServerTickEvent.Post event){int tick=event.getServer().getTickCount();if(tick%5!=0)return;Iterator<Toss> it=tosses.values().iterator();while(it.hasNext()){Toss toss=it.next();if(tick>toss.expiresAt){it.remove();continue;}Entity entity=null;for(var level:event.getServer().getAllLevels()){entity=level.getEntity(toss.itemId);if(entity!=null)break;}if(!(entity instanceof ItemEntity item)){it.remove();continue;}List<Dolphin> nearby=item.level().getEntitiesOfClass(Dolphin.class,item.getBoundingBox().inflate(2));for(Dolphin dolphin:nearby){if(fingerprint(dolphin.getMainHandItem()).equals(toss.fingerprint)){dolphins.put(dolphin.getUUID(),new DolphinPickup(toss.playerId,tick+100,toss.fingerprint));it.remove();break;}}}dolphins.values().removeIf(v->tick>v.expiresAt);}
    private static String fingerprint(ItemEntity item){return fingerprint(item.getItem());}
    private static String fingerprint(net.minecraft.world.item.ItemStack stack){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())+":"+stack.getCount();}
    private static void syncVanilla(ServerPlayer player){for(var entry:VANILLA.entrySet()){AdvancementHolder holder=player.server.getAdvancements().get(ResourceLocation.parse(entry.getValue()));if(holder!=null&&player.getAdvancements().getOrStartProgress(holder).isDone())unlock(player,entry.getKey(),null);}}
    private static void syncCustom(ServerPlayer player){TurtlePlayerProgress progress=TurtleRacingSavedData.get(player.server).getOrCreatePlayer(player.getUUID());for(var entry:CUSTOM.entrySet()){if(progress.unlockedCompanions().contains(entry.getKey()))awardCustom(player,entry.getValue());}}
    private static void awardCustom(ServerPlayer player,String advancement){YoikoAdvancementManager.awardExternalCriterion(player,"turtle/"+advancement,"unlock","turtle_"+advancement,"advancement.yoiko_core.turtle."+advancement);}
    private static void unlock(ServerPlayer player,TurtleCompanion companion,String customAdvancement){TurtleRacingSavedData saved=TurtleRacingSavedData.get(player.server);TurtlePlayerProgress progress=saved.getOrCreatePlayer(player.getUUID());boolean newlyUnlocked=!progress.unlockedCompanions().contains(companion);if(newlyUnlocked){progress.unlock(companion);saved.markChanged();player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("yoiko_core.turtle.companion.unlocked",net.minecraft.network.chat.Component.translatable("yoiko_core.turtle.companion."+companion.name().toLowerCase(java.util.Locale.ROOT)+".name")));}if(customAdvancement!=null)awardCustom(player,customAdvancement);}
}
