package com.yoiko.core.turtle.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.level.Level;

public final class TurtleGhostEntity extends Turtle {
    public TurtleGhostEntity(EntityType<? extends Turtle> type, Level level) {
        super(type,level);
        setAge(-24_000);
        setNoAi(true);
        setInvulnerable(true);
        setSilent(true);
        noPhysics=true;
    }
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
}
