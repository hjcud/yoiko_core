package com.yoiko.core.client.turtle;

import com.yoiko.core.turtle.entity.TurtleGhostEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TurtleRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Turtle;

public final class TurtleGhostRenderer extends TurtleRenderer {
    public TurtleGhostRenderer(EntityRendererProvider.Context context){super(context);}
    @Override protected boolean isBodyVisible(Turtle entity){return false;}
    @Override protected RenderType getRenderType(Turtle entity,boolean bodyVisible,boolean translucent,boolean glowing){return RenderType.entityTranslucent(getTextureLocation(entity));}
}
