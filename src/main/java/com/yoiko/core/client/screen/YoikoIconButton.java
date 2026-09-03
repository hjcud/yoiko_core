package com.yoiko.core.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import com.yoiko.core.config.YoikoClientConfig;
import net.minecraft.world.item.Items;

public class YoikoIconButton extends Button {
    private final int iconIndex;
    private final String navigationId;

    private YoikoIconButton(int x, int y, int width, int height, Component message, int iconIndex,
                            String navigationId, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.iconIndex = iconIndex;
        this.navigationId = navigationId;
    }

    public static YoikoIconButton create(int x, int y, int iconIndex, Component message, OnPress onPress) {
        return new YoikoIconButton(x, y, 30, 30, message, iconIndex, "", onPress);
    }

    public static YoikoIconButton navigation(int x, int y, int iconIndex, String navigationId,
                                             Component message, OnPress onPress) {
        return new YoikoIconButton(x, y, 30, 30, message, iconIndex, navigationId, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = !this.active || this.isHoveredOrFocused();
        YoikoScreenStyle.renderRightTabButton(graphics, getX(), getY(), getWidth(), getHeight(), highlighted);
        if ("turtle".equals(navigationId)) {
            graphics.renderItem(Items.TURTLE_EGG.getDefaultInstance(), getX() + 7, getY() + 7);
        } else {
            YoikoScreenStyle.renderMenuIcon(graphics, iconIndex, getX() + 6, getY() + 6);
        }
        int badge = "market".equals(navigationId) ? ClientMenuBadgeCache.unseenMarketSales() : 0;
        boolean warning = "mailbox".equals(navigationId) && ClientMenuBadgeCache.mailboxNearCapacity();
        if (badge > 0 || warning) {
            graphics.fill(getX() + 21, getY() + 2, getX() + 29, getY() + 10,
                    warning ? 0xFFE18B3C : 0xFFD94A43);
            String text = badge > 9 ? "9+" : badge > 0 ? Integer.toString(badge) : "!";
            graphics.drawCenteredString(Minecraft.getInstance().font, Component.literal(text),
                    getX() + 25, getY() + 2, 0xFFFFFFFF);
        }
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        if (YoikoClientConfig.UI_SOUNDS.get()) {
            soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.45F, 0.18F));
        }
    }
}
