package com.yoiko.core.client.turtle;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.TurtleModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.animal.Turtle;

/**
 * Draws only the shell UV over a body texture whose shell pixels are transparent.
 * Head, belly and flipper colors therefore remain identical for every appearance.
 */
final class RaceTurtleShellLayer extends RenderLayer<Turtle, TurtleModel<Turtle>> {
    RaceTurtleShellLayer(RenderLayerParent<Turtle, TurtleModel<Turtle>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       Turtle turtle, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        getParentModel().renderToBuffer(
                poseStack,
                bufferSource.getBuffer(RenderType.entityCutoutNoCull(RaceTurtleRenderer.shellTextureFor(turtle))),
                packedLight,
                LivingEntityRenderer.getOverlayCoords(turtle, 0.0F),
                0xFFFFFFFF
        );
    }
}
