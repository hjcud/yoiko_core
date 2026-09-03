package com.yoiko.core.registry;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.turtle.entity.TurtleGhostEntity;
import com.yoiko.core.turtle.entity.RaceTurtleEntity;
import com.yoiko.core.treasure.TreasureRabbitEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YoikoEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,YoikoServerCore.MODID);
    public static final DeferredHolder<EntityType<?>,EntityType<TurtleGhostEntity>> TURTLE_GHOST=ENTITIES.register("turtle_ghost",()->EntityType.Builder.<TurtleGhostEntity>of(TurtleGhostEntity::new,MobCategory.MISC).sized(.36F,.12F).clientTrackingRange(1).updateInterval(Integer.MAX_VALUE).noSave().noSummon().build(YoikoServerCore.MODID+":turtle_ghost"));
    public static final DeferredHolder<EntityType<?>,EntityType<RaceTurtleEntity>> RACE_TURTLE=ENTITIES.register("race_turtle",()->EntityType.Builder.<RaceTurtleEntity>of(RaceTurtleEntity::new,MobCategory.MISC).sized(.36F,.12F).clientTrackingRange(10).updateInterval(1).noSave().noSummon().build(YoikoServerCore.MODID+":race_turtle"));
    public static final DeferredHolder<EntityType<?>,EntityType<TreasureRabbitEntity>> TREASURE_RABBIT=ENTITIES.register(
            "treasure_rabbit",()->EntityType.Builder.<TreasureRabbitEntity>of(TreasureRabbitEntity::new,MobCategory.CREATURE)
                    .sized(.68F,.72F).clientTrackingRange(10).updateInterval(2)
                    .build(YoikoServerCore.MODID+":treasure_rabbit"));
    private YoikoEntities(){}
    public static void register(IEventBus bus){ENTITIES.register(bus);}
}
