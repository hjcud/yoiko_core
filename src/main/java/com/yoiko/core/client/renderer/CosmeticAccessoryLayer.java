package com.yoiko.core.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.client.cosmetic.ClientCosmeticEquipmentCache;
import com.yoiko.core.client.cosmetic.ClientCosmeticPreviewState;
import com.yoiko.core.cosmetic.CosmeticAnchor;
import com.yoiko.core.network.CosmeticEquipmentSyncPayload;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;

public final class CosmeticAccessoryLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelResourceLocation SYLVEON_HEADPIECE_MODEL =
            ModelResourceLocation.standalone(YoikoServerCore.id("cosmetic/sylveon_headpiece"));
    public static final ModelResourceLocation SYLVEON_HEADPIECE_SHINY_MODEL =
            ModelResourceLocation.standalone(YoikoServerCore.id("cosmetic/sylveon_headpiece_shiny"));
    public static final ModelResourceLocation JOLTEON_HEADPIECE_MODEL =
            ModelResourceLocation.standalone(YoikoServerCore.id("cosmetic/jolteon_headpiece"));
    public static final ModelResourceLocation JOLTEON_HEADPIECE_SHINY_MODEL =
            ModelResourceLocation.standalone(YoikoServerCore.id("cosmetic/jolteon_headpiece_shiny"));
    public static final ModelResourceLocation VAPOREON_HEADPIECE_MODEL =
            ModelResourceLocation.standalone(YoikoServerCore.id("cosmetic/vaporeon_headpiece"));
    public static final ModelResourceLocation VAPOREON_HEADPIECE_SHINY_MODEL =
            ModelResourceLocation.standalone(YoikoServerCore.id("cosmetic/vaporeon_headpiece_shiny"));

    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private static final Map<String, ModelResourceLocation> TEXTURED_MODELS = Map.of(
            "sylveon_headpiece", SYLVEON_HEADPIECE_MODEL,
            "sylveon_headpiece_shiny", SYLVEON_HEADPIECE_SHINY_MODEL,
            "jolteon_headpiece", JOLTEON_HEADPIECE_MODEL,
            "jolteon_headpiece_shiny", JOLTEON_HEADPIECE_SHINY_MODEL,
            "vaporeon_headpiece", VAPOREON_HEADPIECE_MODEL,
            "vaporeon_headpiece_shiny", VAPOREON_HEADPIECE_SHINY_MODEL
    );
    private static final Map<String, String> TEXTURED_MODEL_COSMETICS = Map.of(
            "sylveon_headpiece", "sylveon_headpiece",
            "sylveon_headpiece_shiny", "shiny_sylveon_headpiece",
            "jolteon_headpiece", "jolteon_headpiece",
            "jolteon_headpiece_shiny", "shiny_jolteon_headpiece",
            "vaporeon_headpiece", "vaporeon_headpiece",
            "vaporeon_headpiece_shiny", "shiny_vaporeon_headpiece"
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> REPORTED_MISSING_MODELS = new HashSet<>();

    private final CosmeticAccessoryModel model;

    public static void diagnoseBakedModels(Map<ModelResourceLocation, BakedModel> bakedModels) {
        for (Map.Entry<String, ModelResourceLocation> entry : TEXTURED_MODELS.entrySet()) {
            BakedModel model = bakedModels.get(entry.getValue());
            if (model == null || model == Minecraft.getInstance().getModelManager().getMissingModel()) {
                LOGGER.error("Missing cosmetic model JSON or texture: cosmeticId='{}', modelId='{}', resource='{}'",
                        TEXTURED_MODEL_COSMETICS.get(entry.getKey()), entry.getKey(), entry.getValue());
            }
        }
    }

    public CosmeticAccessoryLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer,
            EntityModelSet modelSet
    ) {
        super(renderer);
        this.model = new CosmeticAccessoryModel(modelSet.bakeLayer(CosmeticAccessoryModel.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        ClientCosmeticEquipmentCache.EquippedModels equipped =
                ClientCosmeticEquipmentCache.equipped(player.getUUID());
        boolean previewing = player == Minecraft.getInstance().player
                && ClientCosmeticPreviewState.isActive();
        CosmeticEquipmentSyncPayload.ModelEntry previewHead = previewing ? ClientCosmeticPreviewState.head()
                : CosmeticEquipmentSyncPayload.ModelEntry.EMPTY;
        CosmeticEquipmentSyncPayload.ModelEntry previewChest = previewing ? ClientCosmeticPreviewState.chest()
                : CosmeticEquipmentSyncPayload.ModelEntry.EMPTY;
        renderEntry(poseStack, bufferSource, packedLight, player,
                previewing && ClientCosmeticPreviewState.overridesSlot("HEAD") ? previewHead : equipped.head());
        renderEntry(poseStack, bufferSource, packedLight, player,
                previewing && ClientCosmeticPreviewState.overridesSlot("CHEST") ? previewChest : equipped.chest());
    }

    private void renderEntry(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                             AbstractClientPlayer player, CosmeticEquipmentSyncPayload.ModelEntry entry) {
        if (!entry.present()) {
            return;
        }
        poseStack.pushPose();
        if (entry.anchor() == CosmeticAnchor.HEAD || entry.anchor() == CosmeticAnchor.FACE) {
            getParentModel().head.translateAndRotate(poseStack);
        } else {
            getParentModel().body.translateAndRotate(poseStack);
        }
        ModelResourceLocation texturedModel = TEXTURED_MODELS.get(entry.modelId());
        if (texturedModel != null) {
            renderTexturedModel(poseStack, bufferSource, packedLight, texturedModel);
            poseStack.popPose();
            return;
        }
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEXTURE));
        if (!model.hasModel(entry.modelId())) {
            if (REPORTED_MISSING_MODELS.add(entry.modelId())) {
                LOGGER.error("Cosmetic model '{}' is not registered; cosmetic '{}' will not render",
                        entry.modelId(), entry.cosmeticId());
            }
            poseStack.popPose();
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        model.render(
                entry.modelId(),
                poseStack,
                consumer,
                packedLight,
                overlay,
                entry.primaryColor(),
                entry.accentColor()
        );
        poseStack.popPose();
    }

    private static void renderTexturedModel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                            ModelResourceLocation modelLocation) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel bakedModel = minecraft.getModelManager().getModel(modelLocation);
        if (bakedModel == minecraft.getModelManager().getMissingModel()) {
            if (REPORTED_MISSING_MODELS.add(modelLocation.toString())) {
                LOGGER.error("Cosmetic model JSON or referenced texture is missing: {}", modelLocation);
            }
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, -0.25F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.625F, -0.625F, -0.625F);
        bakedModel = ClientHooks.handleCameraTransforms(poseStack, bakedModel, ItemDisplayContext.HEAD, false);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        minecraft.getItemRenderer().renderModelLists(
                bakedModel,
                ItemStack.EMPTY,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                consumer
        );
        poseStack.popPose();
    }
}
