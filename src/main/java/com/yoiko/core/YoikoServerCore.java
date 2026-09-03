package com.yoiko.core;

import com.mojang.logging.LogUtils;
import com.yoiko.core.command.YoikoCommand;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.network.YoikoNetwork;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.registry.YoikoMenus;
import com.yoiko.core.registry.YoikoEntities;
import com.yoiko.core.server.YoikoServerEvents;
import com.yoiko.core.turtle.TurtleCompanionUnlockService;
import com.yoiko.core.turtle.arena.TurtleArenaInteractionService;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.Rabbit;
import org.slf4j.Logger;

@Mod(YoikoServerCore.MODID)
public class YoikoServerCore {
    public static final String MODID = "yoiko_core";
    public static final Logger LOGGER = LogUtils.getLogger();

    public YoikoServerCore(IEventBus modEventBus, ModContainer modContainer) {
        YoikoItems.register(modEventBus);
        YoikoMenus.register(modEventBus);
        YoikoEntities.register(modEventBus);
        modEventBus.addListener(YoikoServerCore::registerEntityAttributes);
        modEventBus.addListener(YoikoNetwork::register);

        NeoForge.EVENT_BUS.register(new YoikoServerEvents());
        NeoForge.EVENT_BUS.register(new TurtleCompanionUnlockService());
        NeoForge.EVENT_BUS.register(new TurtleArenaInteractionService());
        NeoForge.EVENT_BUS.addListener(YoikoCommand::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, YoikoCommonConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(YoikoEntities.TURTLE_GHOST.get(), Turtle.createAttributes().build());
        event.put(YoikoEntities.RACE_TURTLE.get(), Turtle.createAttributes().build());
        event.put(YoikoEntities.TREASURE_RABBIT.get(), Rabbit.createAttributes().build());
    }
}
