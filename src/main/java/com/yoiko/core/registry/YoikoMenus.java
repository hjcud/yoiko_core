package com.yoiko.core.registry;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.storage.YoikoStorageMenu;
import com.yoiko.core.menu.YoikoMarketContainerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YoikoMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, YoikoServerCore.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<YoikoStorageMenu>> YOIKO_STORAGE = MENUS.register(
            "yoiko_storage",
            () -> IMenuTypeExtension.create(YoikoStorageMenu::new)
    );

    public static final DeferredHolder<MenuType<?>, MenuType<YoikoMarketContainerMenu>> YOIKO_MARKET = MENUS.register(
            "yoiko_market",
            () -> IMenuTypeExtension.create(YoikoMarketContainerMenu::new)
    );

    private YoikoMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
