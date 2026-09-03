package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.gacha.GachaAnimationManager;
import com.yoiko.core.menu.PlayerMenuManager;
import com.yoiko.core.relic.RelicRuntimeManager;
import com.yoiko.core.turtle.TurtleHatchPendingService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class YoikoNetwork {
    private static final String PROTOCOL_SCHEMA_VERSION = "24";

    private YoikoNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(protocolVersion());
        registrar.playToClient(OpenDexPayload.TYPE, OpenDexPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> openDexOnClient(payload))
        );
        registrar.playToClient(OpenMailboxPayload.TYPE, OpenMailboxPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> openMailboxOnClient(payload))
        );
        registrar.playToClient(OpenRelicPayload.TYPE, OpenRelicPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> openRelicOnClient(payload))
        );
        registrar.playToClient(OpenRelicDexPayload.TYPE, OpenRelicDexPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> openRelicDexOnClient(payload))
        );
        registrar.playToClient(OpenCosmeticPayload.TYPE, OpenCosmeticPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> openCosmeticOnClient(payload))
        );
        registrar.playToClient(OpenMarketPayload.TYPE, OpenMarketPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> openMarketOnClient(payload))
        );
        registrar.playToClient(OpenProfilePayload.TYPE, OpenProfilePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> invokeClientHandler("openProfile", OpenProfilePayload.class, payload))
        );
        registrar.playToClient(MenuBadgePayload.TYPE, MenuBadgePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> syncMenuBadgesOnClient(payload))
        );
        registrar.playToClient(MenuSessionPayload.TYPE, MenuSessionPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> syncMenuSessionOnClient(payload))
        );
        registrar.playToClient(MenuActionResultPayload.TYPE, MenuActionResultPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> syncMenuActionResultOnClient(payload))
        );
        registrar.playToClient(RankNameplatePayload.TYPE, RankNameplatePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> syncRankNameplateOnClient(payload))
        );
        registrar.playToClient(ParticleCosmeticSyncPayload.TYPE, ParticleCosmeticSyncPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> syncParticleCosmeticOnClient(payload))
        );
        registrar.playToClient(ParticleCosmeticCatalogPayload.TYPE, ParticleCosmeticCatalogPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> syncParticleCosmeticCatalogOnClient(payload))
        );
        registrar.playToClient(CosmeticEquipmentSyncPayload.TYPE, CosmeticEquipmentSyncPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> syncCosmeticEquipmentOnClient(payload))
        );
        registrar.playToClient(CosmeticMenuCatalogPayload.TYPE, CosmeticMenuCatalogPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> syncCosmeticMenuCatalogOnClient(payload))
        );
        registrar.playToClient(GachaSelectionStartPayload.TYPE, GachaSelectionStartPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> startGachaSelectionOnClient(payload))
        );
        registrar.playToClient(GachaSelectionRevealPayload.TYPE, GachaSelectionRevealPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> revealGachaSelectionOnClient(payload))
        );
        registrar.playToClient(GachaSelectionEndPayload.TYPE, GachaSelectionEndPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> endGachaSelectionOnClient(payload))
        );
        registrar.playToClient(RelicGachaStartPayload.TYPE, RelicGachaStartPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> startRelicGachaOnClient(payload))
        );
        registrar.playToClient(RelicGachaEndPayload.TYPE, RelicGachaEndPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> endRelicGachaOnClient(payload))
        );
        registrar.playToClient(RelicGachaResultPayload.TYPE, RelicGachaResultPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> showRelicGachaResultOnClient(payload))
        );
        registrar.playToClient(RelicEffectHudPayload.TYPE, RelicEffectHudPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> syncRelicEffectHudOnClient(payload))
        );
        registrar.playToClient(TurtleTimeTrialGhostPayload.TYPE, TurtleTimeTrialGhostPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> invokeClientHandler("startTurtleTimeTrialGhost", TurtleTimeTrialGhostPayload.class, payload))
        );
        registrar.playToClient(TurtleHatchRevealPayload.TYPE, TurtleHatchRevealPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> invokeClientHandler("startTurtleHatchReveal", TurtleHatchRevealPayload.class, payload))
        );
        registrar.playToClient(OpenTurtleMenuPayload.TYPE, OpenTurtleMenuPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> invokeClientHandler("openTurtleMenu", OpenTurtleMenuPayload.class, payload))
        );
        registrar.playToClient(OpenTurtleStrategyTicketPayload.TYPE, OpenTurtleStrategyTicketPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> invokeClientHandler("openTurtleStrategyTicket", OpenTurtleStrategyTicketPayload.class, payload))
        );
        registrar.playToClient(TurtleRaceMenuStatusPayload.TYPE, TurtleRaceMenuStatusPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> invokeClientHandler("syncTurtleRaceMenuStatus", TurtleRaceMenuStatusPayload.class, payload))
        );
        registrar.playToClient(TurtleRaceHudPayload.TYPE, TurtleRaceHudPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> invokeClientHandler("syncTurtleRaceHud", TurtleRaceHudPayload.class, payload))
        );
        registrar.playToClient(BreakingNewsZonePayload.TYPE, BreakingNewsZonePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> invokeClientHandler("syncBreakingNewsZone", BreakingNewsZonePayload.class, payload))
        );
        registrar.playToClient(TreasureRabbitSearchZonePayload.TYPE,
                TreasureRabbitSearchZonePayload.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> invokeClientHandler("syncTreasureRabbitSearchZone",
                                TreasureRabbitSearchZonePayload.class, payload))
        );
        registrar.playToServer(MenuActionPayload.TYPE, MenuActionPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        boolean success = PlayerMenuManager.handleAction(player, payload);
                        if (MenuActionFeedback.isTracked(payload.action())) {
                            sendActionResult(player, payload.action(), success);
                        }
                    }
                })
        );
        registrar.playToServer(RelicBatchDismantlePayload.TYPE, RelicBatchDismantlePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MenuActionPayload authentication = new MenuActionPayload(
                                "relic_batch_dismantle", payload.sessionId(), payload.nonce());
                        boolean success = !payload.relicIds().isEmpty()
                                && com.yoiko.core.menu.MenuSessionManager.authenticate(player, authentication);
                        if (success) {
                            com.yoiko.core.menu.YoikoRelicMenu.batchDismantle(player, payload.relicIds());
                        }
                        sendActionResult(player, "relic_batch_dismantle", success);
                    }
                }));
        registrar.playToServer(GachaSelectionChoosePayload.TYPE, GachaSelectionChoosePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        GachaAnimationManager.choose(player, payload);
                    }
                })
        );
        registrar.playToServer(RelicAirStepPayload.TYPE, RelicAirStepPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        RelicRuntimeManager.tryAirStep(player, payload.sequence());
                    }
                })
        );
        registrar.playToServer(TurtleMenuActionPayload.TYPE, TurtleMenuActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MenuActionPayload authentication = new MenuActionPayload(
                                "turtle_action", payload.sessionId(), payload.nonce());
                        if (com.yoiko.core.menu.MenuSessionManager.authenticate(player, authentication)) {
                            com.yoiko.core.turtle.TurtleMenuService.handle(player, payload);
                        }
                    }
                }));
        registrar.playToServer(TurtleHatchConfirmPayload.TYPE, TurtleHatchConfirmPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TurtleHatchPendingService.confirm(player, payload.turtleId());
                    }
                })
        );
        registrar.playToServer(TurtleStrategyTicketChoosePayload.TYPE, TurtleStrategyTicketChoosePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.yoiko.core.turtle.TurtleStrategyTicketService.choose(player, payload);
                    }
                })
        );
        registrar.playToServer(TurtleStrategyTicketCancelPayload.TYPE, TurtleStrategyTicketCancelPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.yoiko.core.turtle.TurtleStrategyTicketService.cancel(player, payload.sessionId());
                    }
                })
        );
    }

    private static String protocolVersion() {
        String modVersion = ModList.get().getModContainerById(YoikoServerCore.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        return modVersion + "/" + PROTOCOL_SCHEMA_VERSION;
    }

    private static void sendActionResult(ServerPlayer player, String action, boolean success) {
        PacketDistributor.sendToPlayer(player, new MenuActionResultPayload(
                MenuActionFeedback.scope(action),
                MenuActionFeedback.root(action),
                success,
                success || !"relic_batch_dismantle".equals(MenuActionFeedback.root(action))
                        ? "" : "yoiko_core.message.menu.request_rejected"));
    }

    private static void openDexOnClient(OpenDexPayload payload) {
        invokeClientHandler("openDex", OpenDexPayload.class, payload);
    }

    private static void openMailboxOnClient(OpenMailboxPayload payload) {
        invokeClientHandler("openMailbox", OpenMailboxPayload.class, payload);
    }

    private static void openRelicOnClient(OpenRelicPayload payload) {
        invokeClientHandler("openRelic", OpenRelicPayload.class, payload);
    }

    private static void openRelicDexOnClient(OpenRelicDexPayload payload) {
        invokeClientHandler("openRelicDex", OpenRelicDexPayload.class, payload);
    }

    private static void openCosmeticOnClient(OpenCosmeticPayload payload) {
        invokeClientHandler("openCosmetic", OpenCosmeticPayload.class, payload);
    }

    private static void openMarketOnClient(OpenMarketPayload payload) {
        invokeClientHandler("openMarket", OpenMarketPayload.class, payload);
    }

    private static void syncRankNameplateOnClient(RankNameplatePayload payload) {
        invokeClientHandler("syncRankNameplate", RankNameplatePayload.class, payload);
    }

    private static void syncMenuBadgesOnClient(MenuBadgePayload payload) {
        invokeClientHandler("syncMenuBadges", MenuBadgePayload.class, payload);
    }

    private static void syncMenuSessionOnClient(MenuSessionPayload payload) {
        invokeClientHandler("syncMenuSession", MenuSessionPayload.class, payload);
    }

    private static void syncMenuActionResultOnClient(MenuActionResultPayload payload) {
        invokeClientHandler("syncMenuActionResult", MenuActionResultPayload.class, payload);
    }

    private static void syncParticleCosmeticOnClient(ParticleCosmeticSyncPayload payload) {
        invokeClientHandler("syncParticleCosmetic", ParticleCosmeticSyncPayload.class, payload);
    }

    private static void syncParticleCosmeticCatalogOnClient(ParticleCosmeticCatalogPayload payload) {
        invokeClientHandler("syncParticleCosmeticCatalog", ParticleCosmeticCatalogPayload.class, payload);
    }

    private static void syncCosmeticEquipmentOnClient(CosmeticEquipmentSyncPayload payload) {
        invokeClientHandler("syncCosmeticEquipment", CosmeticEquipmentSyncPayload.class, payload);
    }

    private static void syncCosmeticMenuCatalogOnClient(CosmeticMenuCatalogPayload payload) {
        invokeClientHandler("syncCosmeticMenuCatalog", CosmeticMenuCatalogPayload.class, payload);
    }

    private static void startGachaSelectionOnClient(GachaSelectionStartPayload payload) {
        invokeClientHandler("startGachaSelection", GachaSelectionStartPayload.class, payload);
    }

    private static void revealGachaSelectionOnClient(GachaSelectionRevealPayload payload) {
        invokeClientHandler("revealGachaSelection", GachaSelectionRevealPayload.class, payload);
    }

    private static void endGachaSelectionOnClient(GachaSelectionEndPayload payload) {
        invokeClientHandler("endGachaSelection", GachaSelectionEndPayload.class, payload);
    }

    private static void startRelicGachaOnClient(RelicGachaStartPayload payload) {
        invokeClientHandler("startRelicGacha", RelicGachaStartPayload.class, payload);
    }

    private static void endRelicGachaOnClient(RelicGachaEndPayload payload) {
        invokeClientHandler("endRelicGacha", RelicGachaEndPayload.class, payload);
    }

    private static void showRelicGachaResultOnClient(RelicGachaResultPayload payload) {
        invokeClientHandler("showRelicGachaResult", RelicGachaResultPayload.class, payload);
    }

    private static void syncRelicEffectHudOnClient(RelicEffectHudPayload payload) {
        invokeClientHandler("syncRelicEffectHud", RelicEffectHudPayload.class, payload);
    }

    private static void invokeClientHandler(String methodName, Class<?> payloadType, Object payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> handler = Class.forName("com.yoiko.core.client.YoikoClientPayloadHandler");
            handler.getMethod(methodName, payloadType).invoke(null, payload);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to handle Yoiko client payload: " + methodName, exception);
        }
    }
}
