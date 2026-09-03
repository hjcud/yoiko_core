package com.yoiko.core.client;

import com.yoiko.core.client.screen.YoikoCosmeticScreen;
import com.yoiko.core.client.screen.YoikoDexScreen;
import com.yoiko.core.client.screen.YoikoMailboxScreen;
import com.yoiko.core.client.screen.YoikoMarketScreen;
import com.yoiko.core.client.screen.YoikoRelicDexScreen;
import com.yoiko.core.client.screen.YoikoRelicScreen;
import com.yoiko.core.client.screen.YoikoStorageScreen;
import net.minecraft.client.gui.screens.Screen;

public final class YoikoMenuScreenState {
    private YoikoMenuScreenState() {
    }

    public static boolean isYoikoMenu(Screen screen) {
        return screen instanceof YoikoStorageScreen
                || screen instanceof YoikoMailboxScreen
                || screen instanceof YoikoRelicScreen
                || screen instanceof YoikoRelicDexScreen
                || screen instanceof YoikoDexScreen
                || screen instanceof YoikoCosmeticScreen
                || screen instanceof YoikoMarketScreen;
    }
}
