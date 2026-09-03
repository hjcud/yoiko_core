package com.yoiko.core.server;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.yoiko.core.cosmetic.CosmeticManager;
import com.yoiko.core.cosmetic.CosmeticEquipmentDisplayManager;
import com.yoiko.core.cosmetic.ParticleCosmeticDisplayManager;
import com.yoiko.core.menu.YoikoCosmeticMenu;
import com.yoiko.core.economy.EconomyManager;
import com.yoiko.core.data.ServerYoikoEconomySavedData;
import com.yoiko.core.event.DailyServerEventManager;
import com.yoiko.core.event.BreakingNewsEventManager;
import com.yoiko.core.gacha.GachaAnimationManager;
import com.yoiko.core.gacha.GachaPoolScanner;
import com.yoiko.core.menu.PlayerMenuManager;
import com.yoiko.core.menu.MenuBadgeManager;
import com.yoiko.core.menu.MenuSessionManager;
import com.yoiko.core.menu.YoikoMarketMenu;
import com.yoiko.core.newspaper.WeeklyNewspaperManager;
import com.yoiko.core.relic.CobblemonRelicBridge;
import com.yoiko.core.relic.MegaShowdownRelicBridge;
import com.yoiko.core.relic.QualityFoodRelicBridge;
import com.yoiko.core.relic.WaystonesRelicBridge;
import com.yoiko.core.relic.RelicGachaAnimationManager;
import com.yoiko.core.relic.RelicEffectHandlerRegistry;
import com.yoiko.core.relic.RelicManager;
import com.yoiko.core.relic.RelicMiningTicketManager;
import com.yoiko.core.relic.RelicRuntimeManager;
import com.yoiko.core.relic.RelicHudNotifier;
import com.yoiko.core.network.RelicEffectHudPayload.ValueKind;
import com.yoiko.core.rank.RankDisplayManager;
import com.yoiko.core.rank.RankManager;
import com.yoiko.core.reward.RewardManager;
import com.yoiko.core.turtle.TurtleRacingManager;
import com.yoiko.core.turtle.TurtleHatchPendingService;
import com.yoiko.core.turtle.race.TurtleSkillEffectPreviewManager;
import com.yoiko.core.treasure.TreasureRabbitManager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class YoikoServerEvents {
    private static boolean registered;
    private long lastMarketCleanupTick = Long.MIN_VALUE;

    public YoikoServerEvents() {
        registered = true;
    }

    public static boolean isRegistered() {
        return registered;
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        RankManager.init();
        CosmeticManager.init();
        CobblemonRelicBridge.init();
        MegaShowdownRelicBridge.init();
        WaystonesRelicBridge.init();
        RelicManager.init();
        RelicEffectHandlerRegistry.validateAll();
        RelicManager.disableRelicsWithInvalidRuntimeHandlers();
        EconomyManager.init();
        DailyServerEventManager.init();
        BreakingNewsEventManager.init();
        WeeklyNewspaperManager.init();
        if (ModList.get().isLoaded("cobblemonshinydays")) {
            YoikoServerCore.LOGGER.warn(
                    "Cobblemon Shiny Days is still loaded. Remove it to prevent its shiny rules from stacking with Yoiko daily events.");
        }
        // Reward defaults and validation depend on the loaded cosmetic catalog.
        RewardManager.init();
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerYoikoEconomySavedData.get(event.getServer());
        GachaPoolScanner.init();
        EconomyManager.cleanupExpired(event.getServer());
        TurtleRacingManager.init(event.getServer());
        DailyServerEventManager.refreshActive(event.getServer());
        BreakingNewsEventManager.tick(event.getServer());
        WeeklyNewspaperManager.tick(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        BreakingNewsEventManager.onServerStopped(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        TurtleRacingManager.get().tick();
        TurtleSkillEffectPreviewManager.tick(event.getServer());
        TreasureRabbitManager.tick(event.getServer());
        DailyServerEventManager.tick(event.getServer());
        BreakingNewsEventManager.tick(event.getServer());
        WeeklyNewspaperManager.tick(event.getServer());
        long tick = event.getServer().getTickCount();
        if (lastMarketCleanupTick == Long.MIN_VALUE || tick - lastMarketCleanupTick >= 1_200L) {
            lastMarketCleanupTick = tick;
            EconomyManager.cleanupExpired(event.getServer());
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RewardManager.enqueueLoginMailboxRewards(player);
            com.yoiko.core.mail.MailboxManager.openLoginReward(player);
            DailyServerEventManager.onPlayerLogin(player);
            BreakingNewsEventManager.onPlayerLogin(player);
            WeeklyNewspaperManager.onPlayerLogin(player);
            RankDisplayManager.refresh(player);
            ParticleCosmeticDisplayManager.syncCatalog(player);
            YoikoCosmeticMenu.syncCatalog(player);
            ParticleCosmeticDisplayManager.sync(player);
            CosmeticEquipmentDisplayManager.sync(player);
            MenuBadgeManager.syncOnLogin(player);
            YoikoAdvancementManager.onLogin(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BreakingNewsEventManager.onPlayerLogout(player);
            RankDisplayManager.clear(player);
            ParticleCosmeticDisplayManager.clear(player);
            CosmeticEquipmentDisplayManager.clear(player);
            GachaAnimationManager.cancel(player);
            RelicGachaAnimationManager.cancel(player);
            RelicRuntimeManager.clear(player);
            RelicManager.clearEffectSnapshot(player.getUUID());
            YoikoMarketMenu.clearRateLimit(player.getUUID());
            MenuSessionManager.clear(player.getUUID());
            TurtleHatchPendingService.clear(player.getUUID());
            com.yoiko.core.turtle.TurtleStrategyTicketService.clear(player.getUUID());
            com.yoiko.core.turtle.TurtleMenuService.clearPlayer(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TurtleHatchPendingService.clear(player.getUUID());
            com.yoiko.core.turtle.TurtleStrategyTicketService.clear(player.getUUID());
            RelicRuntimeManager.refreshPlayerAttributes(player);
            RankDisplayManager.refresh(player);
            ParticleCosmeticDisplayManager.sync(player);
            CosmeticEquipmentDisplayManager.sync(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TurtleHatchPendingService.clear(player.getUUID());
            com.yoiko.core.turtle.TurtleStrategyTicketService.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target) {
            RankDisplayManager.syncTo(target, viewer);
            ParticleCosmeticDisplayManager.syncTo(target, viewer);
            CosmeticEquipmentDisplayManager.syncTo(target, viewer);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GachaAnimationManager.tick(player);
        RelicGachaAnimationManager.tick(player);
        TurtleHatchPendingService.tick(player);
        RelicRuntimeManager.tick(player);
    }

    @SubscribeEvent
    public void onPlayerXpChange(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) {
            return;
        }
        double adjusted = RelicManager.applyEffect(player, "player_exp_multiplier_bonus", event.getAmount());
        if (adjusted <= event.getAmount()) {
            return;
        }
        event.setAmount(Math.max(event.getAmount(), (int) Math.round(adjusted)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            RelicMiningTicketManager.onBlockBroken(player, event.getState());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHarvestDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        boolean upgraded = false;
        for (var drop : event.getDrops()) {
            upgraded |= QualityFoodRelicBridge.tryUpgradeQuality(player, drop.getItem());
        }
        if (upgraded) {
            QualityFoodRelicBridge.playUpgradeFeedback(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && QualityFoodRelicBridge.tryUpgradeQuality(player, event.getCrafting())) {
            QualityFoodRelicBridge.playUpgradeFeedback(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && QualityFoodRelicBridge.tryUpgradeQuality(player, event.getSmelting())) {
            QualityFoodRelicBridge.playUpgradeFeedback(player);
        }
    }

    @SubscribeEvent
    public void onFoodUseStarted(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RelicRuntimeManager.onFoodUseStarted(player, event.getItem());
        }
    }

    @SubscribeEvent
    public void onFoodUseStopped(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RelicRuntimeManager.onFoodUseStopped(player);
        }
    }

    @SubscribeEvent
    public void onFoodConsumed(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RelicRuntimeManager.onFoodConsumed(player);
        }
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        RelicRuntimeManager.onLivingIncomingDamage(event);
    }

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        RelicRuntimeManager.onLivingDamagePre(event);
    }

    @SubscribeEvent
    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        RelicRuntimeManager.onLivingDamagePost(event);
    }

    @SubscribeEvent
    public void onLivingShieldBlock(LivingShieldBlockEvent event) {
        RelicRuntimeManager.onLivingShieldBlock(event);
    }

    @SubscribeEvent
    public void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        RelicRuntimeManager.onMobEffectApplicable(event);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        RelicRuntimeManager.onLivingDeath(event);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GachaAnimationManager.cancel(player);
        RelicRuntimeManager.clear(player);
        double chance = Math.max(0.0D, RelicManager.effectBonus(player, "totemless_revive_chance"));
        if (chance <= 0.0D || player.getRandom().nextDouble() * 100.0D >= chance) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.35F));
        player.clearFire();
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        player.level().broadcastEntityEvent(player, (byte) 35);
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        RelicHudNotifier.proc(player, "totemless_revive_chance", ValueKind.HEAL,
                Math.max(1.0F, player.getMaxHealth() * 0.35F));
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        RankDisplayManager.onChat(event);
    }

    @SubscribeEvent
    public void onTabListName(PlayerEvent.TabListNameFormat event) {
        RankDisplayManager.onTabListName(event);
    }

}
