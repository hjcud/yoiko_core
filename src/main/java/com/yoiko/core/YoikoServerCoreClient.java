package com.yoiko.core;

import com.yoiko.core.client.renderer.RankNameplateIconRenderer;
import com.yoiko.core.client.cosmetic.ParticleCosmeticClientEvents;
import com.yoiko.core.client.relic.RelicAirStepClientEvents;
import com.yoiko.core.client.relic.RelicGachaClientManager;
import com.yoiko.core.client.relic.ClientRelicEffectHud;
import com.yoiko.core.client.gacha.GachaSelectionClientManager;
import com.yoiko.core.client.YoikoClientKeys;
import com.yoiko.core.client.YoikoMenuKeyClientEvents;
import com.yoiko.core.client.YoikoMenuHudClientEvents;
import com.yoiko.core.client.renderer.CosmeticWingLayer;
import com.yoiko.core.client.renderer.CosmeticAccessoryLayer;
import com.yoiko.core.client.renderer.CosmeticAccessoryModel;
import com.yoiko.core.client.screen.YoikoStorageScreen;
import com.yoiko.core.client.screen.YoikoMarketScreen;
import com.yoiko.core.client.screen.RelicGachaResultOverlay;
import com.yoiko.core.config.YoikoClientConfig;
import com.yoiko.core.registry.YoikoMenus;
import com.yoiko.core.registry.YoikoEntities;
import com.yoiko.core.client.turtle.TurtleGhostRenderer;
import com.yoiko.core.client.turtle.RaceTurtleRenderer;
import com.yoiko.core.client.turtle.TurtleTimeTrialGhostClient;
import com.yoiko.core.client.turtle.TurtleHatchRevealClient;
import com.yoiko.core.client.turtle.ClientTurtleRaceHud;
import com.yoiko.core.client.treasure.TreasureRabbitRenderer;
import com.yoiko.core.client.event.BreakingNewsMapClientEvents;
import com.yoiko.core.client.event.TreasureRabbitMapClientEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = YoikoServerCore.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = YoikoServerCore.MODID, value = Dist.CLIENT)
public class YoikoServerCoreClient {
    public YoikoServerCoreClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.registerConfig(ModConfig.Type.CLIENT, YoikoClientConfig.SPEC);
        NeoForge.EVENT_BUS.register(new RankNameplateIconRenderer());
        NeoForge.EVENT_BUS.register(new ParticleCosmeticClientEvents());
        NeoForge.EVENT_BUS.register(new GachaSelectionClientManager());
        NeoForge.EVENT_BUS.register(new RelicAirStepClientEvents());
        NeoForge.EVENT_BUS.register(new RelicGachaClientManager());
        NeoForge.EVENT_BUS.register(RelicGachaResultOverlay.INSTANCE);
        NeoForge.EVENT_BUS.register(ClientRelicEffectHud.INSTANCE);
        NeoForge.EVENT_BUS.register(new YoikoMenuKeyClientEvents());
        NeoForge.EVENT_BUS.register(new YoikoMenuHudClientEvents());
        NeoForge.EVENT_BUS.register(TurtleTimeTrialGhostClient.INSTANCE);
        NeoForge.EVENT_BUS.register(TurtleHatchRevealClient.INSTANCE);
        NeoForge.EVENT_BUS.register(ClientTurtleRaceHud.INSTANCE);
        NeoForge.EVENT_BUS.register(new BreakingNewsMapClientEvents());
        NeoForge.EVENT_BUS.register(new TreasureRabbitMapClientEvents());
    }

    @SubscribeEvent
    static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(YoikoMenus.YOIKO_STORAGE.get(), YoikoStorageScreen::new);
        event.register(YoikoMenus.YOIKO_MARKET.get(), YoikoMarketScreen::new);
    }

    @SubscribeEvent
    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(YoikoClientKeys.OPEN_MENU);
        event.register(YoikoClientKeys.STORAGE_SLOT_LOCK);
        event.register(YoikoClientKeys.STORAGE_SORT);
    }

    @SubscribeEvent
    static void addPlayerRenderLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            var renderer = event.<net.minecraft.client.renderer.entity.player.PlayerRenderer>getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new CosmeticAccessoryLayer(renderer, event.getEntityModels()));
                renderer.addLayer(new CosmeticWingLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(YoikoEntities.TURTLE_GHOST.get(), TurtleGhostRenderer::new);
        event.registerEntityRenderer(YoikoEntities.RACE_TURTLE.get(), RaceTurtleRenderer::new);
        event.registerEntityRenderer(YoikoEntities.TREASURE_RABBIT.get(), TreasureRabbitRenderer::new);
    }

    @SubscribeEvent
    static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                CosmeticAccessoryModel.LAYER_LOCATION,
                CosmeticAccessoryModel::createBodyLayer
        );
    }

    @SubscribeEvent
    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(CosmeticAccessoryLayer.SYLVEON_HEADPIECE_MODEL);
        event.register(CosmeticAccessoryLayer.SYLVEON_HEADPIECE_SHINY_MODEL);
        event.register(CosmeticAccessoryLayer.JOLTEON_HEADPIECE_MODEL);
        event.register(CosmeticAccessoryLayer.JOLTEON_HEADPIECE_SHINY_MODEL);
        event.register(CosmeticAccessoryLayer.VAPOREON_HEADPIECE_MODEL);
        event.register(CosmeticAccessoryLayer.VAPOREON_HEADPIECE_SHINY_MODEL);
    }

    @SubscribeEvent
    static void diagnoseCosmeticModels(ModelEvent.BakingCompleted event) {
        CosmeticAccessoryLayer.diagnoseBakedModels(event.getModels());
    }
}
