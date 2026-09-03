package com.yoiko.core.client;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.client.screen.ClientMenuBadgeCache;
import com.yoiko.core.config.YoikoClientConfig;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class YoikoMenuHudClientEvents {
    private static final ResourceLocation DIARY_TEXTURE =
            YoikoServerCore.id("textures/item/menu/yoiko_menu.png");
    private static final int VANILLA_HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_ICON_GAP = 2;
    private static final int DIARY_ICON_SIZE = 16;
    private static final int OFFHAND_SLOT_WIDTH = 29;
    private static final int LEFT_ATTACK_INDICATOR_WIDTH = 22;
    private static final int RIGHT_ATTACK_INDICATOR_WIDTH = 24;
    private static final int SCREEN_MARGIN = 2;

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!YoikoClientConfig.SHOW_MENU_HUD.get() || minecraft.options.hideGui
                || minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Component key = YoikoClientKeys.OPEN_MENU.getTranslatedKeyMessage();
        HudPlacement placement = placement(minecraft, graphics, minecraft.font.width(key));
        int x = placement.x();
        int y = graphics.guiHeight() - 19;
        int unread = ClientMenuBadgeCache.unreadMailCount();

        graphics.blit(DIARY_TEXTURE, x, y, 0, 0,
                DIARY_ICON_SIZE, DIARY_ICON_SIZE, DIARY_ICON_SIZE, DIARY_ICON_SIZE);

        if (placement.showKey()) {
            int keyX = placement.keyOnRight()
                    ? x + DIARY_ICON_SIZE + 2
                    : x - minecraft.font.width(key) - 2;
            int keyY = y + (DIARY_ICON_SIZE - minecraft.font.lineHeight) / 2;
            graphics.drawString(minecraft.font, key, keyX, keyY, 0xFFFFFFFF, true);
        }

        if (unread > 0) {
            String badge = unread > 99 ? "99+" : Integer.toString(unread);
            int badgeWidth = Math.max(11, minecraft.font.width(badge) + 5);
            int badgeRight = x + DIARY_ICON_SIZE + 4;
            int badgeX = badgeRight - badgeWidth;
            graphics.fill(badgeX, y - 5, badgeRight, y + 6, 0xFFF04444);
            graphics.drawCenteredString(minecraft.font, badge,
                    badgeX + badgeWidth / 2, y - 3, 0xFFFFFFFF);
        }
    }

    private static HudPlacement placement(Minecraft minecraft, GuiGraphics graphics, int keyWidth) {
        int centerX = graphics.guiWidth() / 2;
        HumanoidArm offhandArm = minecraft.player.getMainArm().getOpposite();
        int leftReserved = 0;
        int rightReserved = 0;

        if (!minecraft.player.getOffhandItem().isEmpty()) {
            if (offhandArm == HumanoidArm.LEFT) {
                leftReserved = OFFHAND_SLOT_WIDTH;
            } else {
                rightReserved = OFFHAND_SLOT_WIDTH;
            }
        }
        if (minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            if (offhandArm == HumanoidArm.RIGHT) {
                leftReserved = Math.max(leftReserved, LEFT_ATTACK_INDICATOR_WIDTH);
            } else {
                rightReserved = Math.max(rightReserved, RIGHT_ATTACK_INDICATOR_WIDTH);
            }
        }

        int rightX = centerX + VANILLA_HOTBAR_HALF_WIDTH + rightReserved + HOTBAR_ICON_GAP;
        boolean iconFitsRight = rightX + DIARY_ICON_SIZE + 4 <= graphics.guiWidth() - SCREEN_MARGIN;
        if (iconFitsRight) {
            boolean keyFits = rightX + DIARY_ICON_SIZE + 2 + keyWidth <= graphics.guiWidth() - SCREEN_MARGIN;
            return new HudPlacement(rightX, keyFits, true);
        }

        int leftX = centerX - VANILLA_HOTBAR_HALF_WIDTH - leftReserved - HOTBAR_ICON_GAP - DIARY_ICON_SIZE;
        leftX = Math.max(SCREEN_MARGIN, leftX);
        boolean keyFits = leftX - keyWidth - 2 >= SCREEN_MARGIN;
        return new HudPlacement(leftX, keyFits, false);
    }

    private record HudPlacement(int x, boolean showKey, boolean keyOnRight) {
    }
}
