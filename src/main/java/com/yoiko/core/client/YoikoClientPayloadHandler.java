package com.yoiko.core.client;

import com.yoiko.core.client.rank.ClientRankNameplateCache;
import com.yoiko.core.client.cosmetic.ClientParticleCosmeticCache;
import com.yoiko.core.client.cosmetic.ClientParticleCosmeticCatalog;
import com.yoiko.core.client.cosmetic.ClientCosmeticEquipmentCache;
import com.yoiko.core.client.cosmetic.ClientCosmeticMenuCatalog;
import com.yoiko.core.client.gacha.GachaSelectionClientManager;
import com.yoiko.core.client.relic.RelicGachaClientManager;
import com.yoiko.core.client.screen.YoikoDexScreen;
import com.yoiko.core.client.screen.YoikoMailboxScreen;
import com.yoiko.core.client.screen.YoikoLoginRewardScreen;
import com.yoiko.core.client.screen.YoikoCosmeticScreen;
import com.yoiko.core.client.screen.YoikoMarketScreen;
import com.yoiko.core.client.screen.YoikoRelicDexScreen;
import com.yoiko.core.client.screen.YoikoRelicScreen;
import com.yoiko.core.network.OpenCosmeticPayload;
import com.yoiko.core.network.OpenDexPayload;
import com.yoiko.core.network.OpenMailboxPayload;
import com.yoiko.core.network.OpenMarketPayload;
import com.yoiko.core.network.OpenRelicDexPayload;
import com.yoiko.core.network.OpenRelicPayload;
import com.yoiko.core.network.OpenProfilePayload;
import com.yoiko.core.network.RankNameplatePayload;
import com.yoiko.core.network.ParticleCosmeticSyncPayload;
import com.yoiko.core.network.ParticleCosmeticCatalogPayload;
import com.yoiko.core.network.CosmeticEquipmentSyncPayload;
import com.yoiko.core.network.CosmeticMenuCatalogPayload;
import com.yoiko.core.network.GachaSelectionEndPayload;
import com.yoiko.core.network.GachaSelectionRevealPayload;
import com.yoiko.core.network.GachaSelectionStartPayload;
import com.yoiko.core.network.MenuBadgePayload;
import com.yoiko.core.network.MenuSessionPayload;
import com.yoiko.core.network.MenuActionResultPayload;
import com.yoiko.core.network.RelicGachaEndPayload;
import com.yoiko.core.network.RelicGachaResultPayload;
import com.yoiko.core.network.RelicGachaStartPayload;
import com.yoiko.core.network.RelicEffectHudPayload;
import com.yoiko.core.network.TurtleTimeTrialGhostPayload;
import com.yoiko.core.client.turtle.TurtleTimeTrialGhostClient;
import com.yoiko.core.client.turtle.TurtleHatchRevealClient;
import com.yoiko.core.network.TurtleHatchRevealPayload;
import com.yoiko.core.network.OpenTurtleMenuPayload;
import com.yoiko.core.network.OpenTurtleStrategyTicketPayload;
import com.yoiko.core.network.TurtleRaceMenuStatusPayload;
import com.yoiko.core.network.TurtleRaceHudPayload;
import com.yoiko.core.network.BreakingNewsZonePayload;
import com.yoiko.core.client.event.ClientBreakingNewsZone;
import com.yoiko.core.network.TreasureRabbitSearchZonePayload;
import com.yoiko.core.client.event.ClientTreasureRabbitSearchZones;
import com.yoiko.core.client.turtle.ClientTurtleRaceHud;
import com.yoiko.core.client.screen.TurtleRacingScreen;
import com.yoiko.core.client.screen.TurtleStrategyTicketScreen;
import com.yoiko.core.client.screen.YoikoProfileScreen;
import com.yoiko.core.client.screen.ClientMenuBadgeCache;
import com.yoiko.core.client.screen.ClientMenuSession;
import net.minecraft.client.Minecraft;

public final class YoikoClientPayloadHandler {
    private YoikoClientPayloadHandler() {
    }

    public static void openDex(OpenDexPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof YoikoDexScreen dexScreen) {
            dexScreen.update(payload);
            return;
        }
        minecraft.setScreen(new YoikoDexScreen(payload));
    }

    public static void openMailbox(OpenMailboxPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.loginRewardScreen()) {
            if (minecraft.screen instanceof YoikoLoginRewardScreen rewardScreen) {
                rewardScreen.update(payload);
                return;
            }
            minecraft.setScreen(new YoikoLoginRewardScreen(payload));
            return;
        }
        if (minecraft.screen instanceof YoikoMailboxScreen mailboxScreen) {
            mailboxScreen.update(payload);
            return;
        }
        minecraft.setScreen(new YoikoMailboxScreen(payload));
    }

    public static void openRelic(OpenRelicPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof YoikoRelicScreen relicScreen) {
            relicScreen.update(payload);
            return;
        }
        minecraft.setScreen(new YoikoRelicScreen(payload));
    }

    public static void openRelicDex(OpenRelicDexPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof YoikoRelicDexScreen relicDexScreen) {
            relicDexScreen.update(payload);
            return;
        }
        minecraft.setScreen(new YoikoRelicDexScreen(payload));
    }

    public static void openCosmetic(OpenCosmeticPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        payload = ClientCosmeticMenuCatalog.prepare(payload);
        if (minecraft.screen instanceof YoikoCosmeticScreen cosmeticScreen) {
            cosmeticScreen.update(payload);
            return;
        }
        minecraft.setScreen(new YoikoCosmeticScreen(payload));
    }

    public static void openMarket(OpenMarketPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof YoikoMarketScreen marketScreen) {
            marketScreen.update(payload);
            return;
        }
        YoikoMarketScreen.cachePendingPayload(payload);
    }

    public static void openProfile(OpenProfilePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof YoikoProfileScreen profileScreen) {
            profileScreen.update(payload);
            return;
        }
        minecraft.setScreen(new YoikoProfileScreen(payload));
    }

    public static void syncRankNameplate(RankNameplatePayload payload) {
        ClientRankNameplateCache.update(payload);
    }

    public static void syncParticleCosmetic(ParticleCosmeticSyncPayload payload) {
        ClientParticleCosmeticCache.update(payload);
    }

    public static void syncParticleCosmeticCatalog(ParticleCosmeticCatalogPayload payload) {
        ClientParticleCosmeticCatalog.replace(payload);
    }

    public static void syncCosmeticEquipment(CosmeticEquipmentSyncPayload payload) {
        ClientCosmeticEquipmentCache.update(payload);
    }

    public static void syncCosmeticMenuCatalog(CosmeticMenuCatalogPayload payload) {
        ClientCosmeticMenuCatalog.replace(payload);
    }

    public static void syncMenuBadges(MenuBadgePayload payload) {
        ClientMailNotification.update(payload);
        ClientMenuBadgeCache.update(payload);
        if (payload.showIntro()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "yoiko_core.message.menu.hud_intro",
                                YoikoClientKeys.OPEN_MENU.getTranslatedKeyMessage()), false);
            }
        }
    }

    public static void syncMenuSession(MenuSessionPayload payload) {
        ClientMenuSession.update(payload);
    }

    public static void syncMenuActionResult(MenuActionResultPayload payload) {
        com.yoiko.core.client.screen.ClientServerRequestState.complete(payload.scope());
        Minecraft minecraft = Minecraft.getInstance();
        if (!payload.success() && minecraft.player != null && !payload.messageKey().isBlank()) {
            minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(payload.messageKey()), false);
        }
    }

    public static void startGachaSelection(GachaSelectionStartPayload payload) {
        GachaSelectionClientManager.start(payload);
    }

    public static void revealGachaSelection(GachaSelectionRevealPayload payload) {
        GachaSelectionClientManager.reveal(payload);
    }

    public static void endGachaSelection(GachaSelectionEndPayload payload) {
        GachaSelectionClientManager.end(payload.sessionId());
    }

    public static void startRelicGacha(RelicGachaStartPayload payload) {
        RelicGachaClientManager.start(payload);
    }

    public static void endRelicGacha(RelicGachaEndPayload payload) {
        RelicGachaClientManager.end(payload.sessionId());
    }

    public static void showRelicGachaResult(RelicGachaResultPayload payload) {
        com.yoiko.core.client.screen.RelicGachaResultOverlay.INSTANCE.show(payload);
    }

    public static void syncRelicEffectHud(RelicEffectHudPayload payload) {
        com.yoiko.core.client.relic.ClientRelicEffectHud.INSTANCE.update(payload);
    }

    public static void startTurtleTimeTrialGhost(TurtleTimeTrialGhostPayload payload) {
        TurtleTimeTrialGhostClient.INSTANCE.start(payload);
    }
    public static void startTurtleHatchReveal(TurtleHatchRevealPayload payload) { TurtleHatchRevealClient.INSTANCE.start(payload); }
    public static void openTurtleMenu(OpenTurtleMenuPayload payload) { Minecraft minecraft=Minecraft.getInstance();if(minecraft.screen instanceof TurtleRacingScreen screen)screen.update(payload);else minecraft.setScreen(new TurtleRacingScreen(payload)); }
    public static void openTurtleStrategyTicket(OpenTurtleStrategyTicketPayload payload) { Minecraft.getInstance().setScreen(new TurtleStrategyTicketScreen(payload)); }
    public static void syncTurtleRaceMenuStatus(TurtleRaceMenuStatusPayload payload) { Minecraft minecraft=Minecraft.getInstance();if(minecraft.screen instanceof TurtleRacingScreen screen)screen.updateRaceStatus(payload); }
    public static void syncTurtleRaceHud(TurtleRaceHudPayload payload) { ClientTurtleRaceHud.INSTANCE.update(payload); }
    public static void syncBreakingNewsZone(BreakingNewsZonePayload payload) { ClientBreakingNewsZone.update(payload); }
    public static void syncTreasureRabbitSearchZone(TreasureRabbitSearchZonePayload payload) {
        ClientTreasureRabbitSearchZones.update(payload);
    }
}
