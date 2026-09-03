package com.yoiko.core.client.treasure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.client.renderer.CosmeticAccessoryModel;
import com.yoiko.core.cosmetic.RabbitCrownStyle;
import com.yoiko.core.treasure.TreasureRabbitEntity;
import com.yoiko.core.treasure.TreasureRabbitVariant;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RabbitRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Rabbit;

public final class TreasureRabbitRenderer extends RabbitRenderer {
    private static final ResourceLocation ACCESSORY_TEXTURE = ResourceLocation.withDefaultNamespace(
            "textures/block/white_concrete.png");
    private static final ResourceLocation GOLDEN = YoikoServerCore.id("textures/entity/treasure_rabbit/golden.png");
    private static final ResourceLocation RADIANT = YoikoServerCore.id("textures/entity/treasure_rabbit/radiant.png");
    private static final ResourceLocation MIRROR = YoikoServerCore.id("textures/entity/treasure_rabbit/mirror.png");
    private static final ResourceLocation MIRROR_GLINT = YoikoServerCore.id(
            "textures/entity/treasure_rabbit/mirror_glint.png");
    private static final ResourceLocation CROWN = ResourceLocation.withDefaultNamespace(
            "textures/entity/rabbit/white.png");
    private static final ResourceLocation[] RADIANT_ANIMATION = {
            radiantFrame(0), radiantFrame(1), radiantFrame(2), radiantFrame(3),
            radiantFrame(4), radiantFrame(5), radiantFrame(6), radiantFrame(7),
            radiantFrame(8), radiantFrame(9)
    };
    private static final int RADIANT_FRAME_TICKS = 2;

    public TreasureRabbitRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new TreasureRabbitModel(context.bakeLayer(ModelLayers.RABBIT));
        addLayer(new CrownLayer(this,
                new CosmeticAccessoryModel(context.bakeLayer(CosmeticAccessoryModel.LAYER_LOCATION))));
        addLayer(new RadiantLayer(this));
        addLayer(new MirrorLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Rabbit entity) {
        if (entity instanceof TreasureRabbitEntity treasure) {
            return switch (treasure.treasureVariant()) {
                case RADIANT -> RADIANT;
                case MIRROR -> MIRROR;
                case CROWN -> CROWN;
                default -> GOLDEN;
            };
        }
        return GOLDEN;
    }

    @Override
    public void render(Rabbit entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        if (entity instanceof TreasureRabbitEntity treasure && treasure.isBurrowing()) {
            float sink = treasure.burrowSinkProgress(partialTick);
            float easedSink = sink * sink * (3.0F - 2.0F * sink);
            float scratchBob = sink <= 0.0F
                    ? Mth.abs(Mth.sin(treasure.burrowAnimationTicks(partialTick) * 1.65F)) * 0.035F
                    : 0.0F;
            double sinkDepth = treasure.treasureVariant() == TreasureRabbitVariant.CROWN ? 3.85D : 0.92D;
            poseStack.translate(0.0D, -sinkDepth * easedSink - scratchBob, 0.0D);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
        poseStack.popPose();
    }

    @Override
    protected void scale(Rabbit entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        if (entity instanceof TreasureRabbitEntity crown
                && crown.treasureVariant() == TreasureRabbitVariant.CROWN) {
            poseStack.scale(TreasureRabbitEntity.CROWN_SCALE,
                    TreasureRabbitEntity.CROWN_SCALE, TreasureRabbitEntity.CROWN_SCALE);
        }
        if (!(entity instanceof TreasureRabbitEntity treasure) || !treasure.isBurrowing()) {
            return;
        }
        float scratch = treasure.burrowScratchProgress(partialTick);
        float sink = treasure.burrowSinkProgress(partialTick);
        float leaned = scratch * scratch * (3.0F - 2.0F * scratch);
        float settle = Mth.clamp((sink - 0.35F) / 0.65F, 0.0F, 1.0F);
        float forwardTilt = 58.0F * leaned - 28.0F * settle;
        poseStack.mulPose(Axis.XP.rotationDegrees(forwardTilt));
        if (sink <= 0.0F) {
            float diggingShake = Mth.sin(treasure.burrowAnimationTicks(partialTick) * 2.15F) * 3.5F;
            poseStack.mulPose(Axis.ZP.rotationDegrees(diggingShake));
        }
    }

    private static final class TreasureRabbitModel extends RabbitModel<Rabbit> {
        private final ModelPart head;
        private final ModelPart leftEar;
        private final ModelPart rightEar;

        private TreasureRabbitModel(ModelPart root) {
            super(root);
            head = root.getChild("head");
            leftEar = root.getChild("left_ear");
            rightEar = root.getChild("right_ear");
        }

        @Override
        public void setupAnim(Rabbit entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            leftEar.zRot = 0.0F;
            rightEar.zRot = 0.0F;
            if (!(entity instanceof TreasureRabbitEntity treasure) || !treasure.isFatigued()) {
                return;
            }
            int required = Math.max(2, treasure.requiredHits());
            float fatigue = Mth.clamp((float) (treasure.countedHits() - 1) / (required - 1), 0.0F, 1.0F);
            leftEar.xRot += 0.48F * fatigue;
            rightEar.xRot += 0.48F * fatigue;
            leftEar.zRot = 0.16F * fatigue;
            rightEar.zRot = -0.16F * fatigue;
        }

        private void translateToCrown(PoseStack poseStack) {
            head.translateAndRotate(poseStack);
            // The shared player model is 9.6 pixels wide. At 54%, it fits the vanilla
            // rabbit's five-pixel head while leaving the open center clear for both ears.
            poseStack.scale(0.54F, 0.54F, 0.54F);
        }
    }

    private static final class CrownLayer extends RenderLayer<Rabbit, RabbitModel<Rabbit>> {
        private final CosmeticAccessoryModel crownModel;

        private CrownLayer(TreasureRabbitRenderer renderer, CosmeticAccessoryModel crownModel) {
            super(renderer);
            this.crownModel = crownModel;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, Rabbit entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!(entity instanceof TreasureRabbitEntity treasure)
                    || treasure.treasureVariant() != TreasureRabbitVariant.CROWN
                    || entity.isInvisible()
                    || !(getParentModel() instanceof TreasureRabbitModel rabbitModel)) {
                return;
            }
            poseStack.pushPose();
            rabbitModel.translateToCrown(poseStack);
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(ACCESSORY_TEXTURE));
            crownModel.render(RabbitCrownStyle.MODEL_ID, poseStack, consumer, packedLight,
                    OverlayTexture.NO_OVERLAY, RabbitCrownStyle.GOLD_COLOR, RabbitCrownStyle.JEWEL_COLOR);
            poseStack.popPose();
        }
    }

    private static final class RadiantLayer extends RenderLayer<Rabbit, RabbitModel<Rabbit>> {
        private RadiantLayer(TreasureRabbitRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, Rabbit entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!(entity instanceof TreasureRabbitEntity treasure)
                    || treasure.treasureVariant() != TreasureRabbitVariant.RADIANT || entity.isInvisible()) {
                return;
            }
            VertexConsumer bodyGlow = buffers.getBuffer(RenderType.entityTranslucentEmissive(RADIANT));
            getParentModel().renderToBuffer(poseStack, bodyGlow, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 0x48FFFFFF);
            int frame = Math.floorMod(entity.tickCount / RADIANT_FRAME_TICKS, RADIANT_ANIMATION.length);
            VertexConsumer consumer = buffers.getBuffer(
                    RenderType.entityTranslucentEmissive(RADIANT_ANIMATION[frame]));
            getParentModel().renderToBuffer(poseStack, consumer, 0x00F000F0,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }

    private static final class MirrorLayer extends RenderLayer<Rabbit, RabbitModel<Rabbit>> {
        private MirrorLayer(TreasureRabbitRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, Rabbit entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!(entity instanceof TreasureRabbitEntity treasure)
                    || treasure.treasureVariant() != TreasureRabbitVariant.MIRROR || entity.isInvisible()) {
                return;
            }
            float pulse = 0.42F + 0.18F * Mth.sin((entity.tickCount + partialTick) * 0.24F);
            int alpha = Mth.clamp((int) (pulse * 255.0F), 0, 255);
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(MIRROR_GLINT));
            getParentModel().renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, alpha << 24 | 0xB8F5FF);
        }
    }

    private static ResourceLocation radiantFrame(int frame) {
        return YoikoServerCore.id(String.format(
                "textures/entity/treasure_rabbit/radiant_animation/frame_%02d.png", frame));
    }
}
