package com.yoiko.core.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.cosmetic.RabbitCrownStyle;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class CosmeticAccessoryModel {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(YoikoServerCore.id("cosmetic_accessories"), "main");

    private static final String[] MODEL_IDS = {
            RabbitCrownStyle.MODEL_ID,
            "supporter_crown",
            "crystal_crown",
            "crystal_badge",
            "witch_hat",
            "red_beret",
            "cat_ear_headband",
            "ribbon_brooch",
            "short_cape",
            "crossed_swords",
            "back_greatsword",
            "mechanical_backpack",
            "small_model_wings"
    };

    private final Map<String, ModelParts> models = new HashMap<>();

    public CosmeticAccessoryModel(ModelPart root) {
        for (String modelId : MODEL_IDS) {
            ModelPart model = root.getChild(modelId);
            models.put(modelId, new ModelParts(model.getChild("primary"), model.getChild("accent")));
        }
    }

    public void render(String modelId, PoseStack poseStack, VertexConsumer consumer,
                       int packedLight, int packedOverlay, int primaryColor, int accentColor) {
        ModelParts parts = models.get(modelId);
        if (parts == null) {
            return;
        }
        parts.primary().render(poseStack, consumer, packedLight, packedOverlay, primaryColor);
        parts.accent().render(poseStack, consumer, packedLight, packedOverlay, accentColor);
    }

    public boolean hasModel(String modelId) {
        return models.containsKey(modelId);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        addRabbitCrown(root);
        addSupporterCrown(root);
        addCrystalCrown(root);
        addCrystalBadge(root);
        addWitchHat(root);
        addRedBeret(root);
        addCatEarHeadband(root);
        addRibbonBrooch(root);
        addShortCape(root);
        addCrossedSwords(root);
        addBackGreatsword(root);
        addMechanicalBackpack(root);
        addSmallModelWings(root);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * A compact, open-topped Minecraft-style crown. The stepped prongs keep the silhouette
     * readable without filling the open center, so rabbit ears can pass through the same model
     * that is worn by players.
     */
    private static void addRabbitCrown(PartDefinition root) {
        PartDefinition model = model(root, RabbitCrownStyle.MODEL_ID);
        PartDefinition primary = group(model, "primary");

        // A one-pixel open band sized just outside an 8x8 player head.
        cube(primary, "front_band", -4.8F, -9.8F, -4.8F, 9.6F, 1.4F, 1.0F);
        cube(primary, "back_band", -4.8F, -9.8F, 3.8F, 9.6F, 1.4F, 1.0F);
        cube(primary, "left_band", -4.8F, -9.8F, -3.8F, 1.0F, 1.4F, 7.6F);
        cube(primary, "right_band", 3.8F, -9.8F, -3.8F, 1.0F, 1.4F, 7.6F);

        // Five stepped points give the front a clear royal silhouette; the rear corner points
        // keep the crown recognizable from third-person views behind the wearer.
        addCrownSpire(primary, "front_center", -0.8F, -4.9F, 1.6F, 4.2F);
        addCrownSpire(primary, "front_left", -4.45F, -4.8F, 1.4F, 3.0F);
        addCrownSpire(primary, "front_right", 3.05F, -4.8F, 1.4F, 3.0F);
        addCrownSpire(primary, "back_left", -4.45F, 3.7F, 1.4F, 2.6F);
        addCrownSpire(primary, "back_right", 3.05F, 3.7F, 1.4F, 2.6F);

        PartDefinition accent = group(model, "accent");
        cube(accent, "center_jewel", -0.65F, -10.7F, -5.05F, 1.3F, 1.3F, 0.4F);
        cube(accent, "left_jewel", -3.25F, -10.25F, -5.02F, 0.8F, 0.8F, 0.35F);
        cube(accent, "right_jewel", 2.45F, -10.25F, -5.02F, 0.8F, 0.8F, 0.35F);
    }

    private static void addCrownSpire(PartDefinition parent, String name, float x, float z,
                                      float width, float height) {
        PartDefinition spire = parent.addOrReplaceChild(
                name,
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(x, -9.8F - height, z, width, height, 1.1F, CubeDeformation.NONE),
                PartPose.ZERO
        );
        float tipWidth = Math.max(0.7F, width - 0.6F);
        spire.addOrReplaceChild(
                "tip",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(x + (width - tipWidth) * 0.5F, -10.65F - height, z + 0.15F,
                                tipWidth, 0.9F, 0.8F, CubeDeformation.NONE),
                PartPose.ZERO
        );
    }

    private static void addSupporterCrown(PartDefinition root) {
        PartDefinition model = model(root, "supporter_crown");
        PartDefinition primary = group(model, "primary");
        cube(primary, "front", -4.5F, -9.5F, -4.7F, 9.0F, 1.0F, 1.0F);
        cube(primary, "back", -4.5F, -9.5F, 3.7F, 9.0F, 1.0F, 1.0F);
        cube(primary, "left", -4.7F, -9.5F, -3.7F, 1.0F, 1.0F, 7.4F);
        cube(primary, "right", 3.7F, -9.5F, -3.7F, 1.0F, 1.0F, 7.4F);
        cube(primary, "left_spire", -4.3F, -12.0F, -4.35F, 1.2F, 2.5F, 1.2F);
        cube(primary, "center_spire", -0.75F, -13.0F, -4.5F, 1.5F, 3.5F, 1.2F);
        cube(primary, "right_spire", 3.1F, -12.0F, -4.35F, 1.2F, 2.5F, 1.2F);

        PartDefinition accent = group(model, "accent");
        cube(accent, "gem", -0.5F, -11.0F, -4.85F, 1.0F, 1.0F, 0.5F);
    }

    private static void addCrystalCrown(PartDefinition root) {
        PartDefinition model = model(root, "crystal_crown");
        PartDefinition primary = group(model, "primary");
        cube(primary, "front", -4.5F, -9.3F, -4.7F, 9.0F, 0.8F, 0.8F);
        cube(primary, "back", -4.5F, -9.3F, 3.9F, 9.0F, 0.8F, 0.8F);
        cube(primary, "left", -4.7F, -9.3F, -3.9F, 0.8F, 0.8F, 7.8F);
        cube(primary, "right", 3.9F, -9.3F, -3.9F, 0.8F, 0.8F, 7.8F);
        cube(primary, "crystal_left", -3.2F, -12.0F, -4.6F, 1.2F, 2.7F, 1.0F);
        cube(primary, "crystal_right", 2.0F, -12.0F, -4.6F, 1.2F, 2.7F, 1.0F);

        PartDefinition accent = group(model, "accent");
        cube(accent, "crystal_center", -0.85F, -13.5F, -4.8F, 1.7F, 4.2F, 1.3F);
        cube(accent, "crystal_tip", -0.45F, -14.4F, -4.55F, 0.9F, 1.0F, 0.9F);
    }

    private static void addCrystalBadge(PartDefinition root) {
        PartDefinition model = model(root, "crystal_badge");
        PartDefinition primary = group(model, "primary");
        cube(primary, "plate", -2.0F, 2.0F, -2.8F, 4.0F, 4.0F, 0.7F);
        cube(primary, "lower", -1.2F, 5.7F, -2.7F, 2.4F, 1.5F, 0.6F);

        PartDefinition accent = group(model, "accent");
        cube(accent, "crystal", -0.9F, 2.8F, -3.25F, 1.8F, 2.4F, 0.6F);
        cube(accent, "tip", -0.45F, 2.2F, -3.1F, 0.9F, 0.8F, 0.5F);
    }

    private static void addWitchHat(PartDefinition root) {
        PartDefinition model = model(root, "witch_hat");
        PartDefinition primary = group(model, "primary");
        cube(primary, "brim", -5.5F, -9.2F, -5.5F, 11.0F, 0.8F, 11.0F);
        cube(primary, "lower", -3.8F, -12.0F, -3.8F, 7.6F, 2.8F, 7.6F);
        cube(primary, "middle", -2.8F, -15.0F, -2.8F, 5.6F, 3.0F, 5.6F);
        PartDefinition tip = primary.addOrReplaceChild(
                "tip",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.7F, -3.5F, -1.7F, 3.4F, 3.8F, 3.4F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(0.0F, -14.8F, 0.0F, 0.0F, 0.0F, -0.18F)
        );
        tip.addOrReplaceChild(
                "crook",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.8F, -2.2F, -0.8F, 1.6F, 2.4F, 1.6F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(-1.0F, -3.0F, 0.0F, 0.0F, 0.0F, -0.32F)
        );

        PartDefinition accent = group(model, "accent");
        cube(accent, "band_front", -3.9F, -10.4F, -4.0F, 7.8F, 1.0F, 0.5F);
        cube(accent, "band_back", -3.9F, -10.4F, 3.5F, 7.8F, 1.0F, 0.5F);
        cube(accent, "buckle", -0.8F, -10.7F, -4.35F, 1.6F, 1.5F, 0.5F);
    }

    private static void addRedBeret(PartDefinition root) {
        PartDefinition model = model(root, "red_beret");
        PartDefinition primary = group(model, "primary");
        cube(primary, "rim", -4.4F, -9.1F, -4.4F, 8.8F, 0.7F, 8.8F);
        cube(primary, "crown", -4.0F, -10.8F, -4.0F, 8.0F, 1.8F, 8.0F);
        cube(primary, "tilted_top", -3.4F, -11.6F, -4.2F, 7.7F, 0.9F, 7.7F);

        PartDefinition accent = group(model, "accent");
        cube(accent, "stem", 0.4F, -12.4F, -0.4F, 0.8F, 1.0F, 0.8F);
    }

    private static void addCatEarHeadband(PartDefinition root) {
        PartDefinition model = model(root, "cat_ear_headband");
        PartDefinition primary = group(model, "primary");
        cube(primary, "band_top", -4.2F, -9.5F, -4.2F, 8.4F, 0.7F, 0.7F);
        cube(primary, "band_left", -4.3F, -9.3F, -4.1F, 0.7F, 2.4F, 1.0F);
        cube(primary, "band_right", 3.6F, -9.3F, -4.1F, 0.7F, 2.4F, 1.0F);
        cube(primary, "left_ear_base", -3.8F, -12.0F, -4.0F, 2.5F, 2.8F, 1.0F);
        cube(primary, "left_ear_tip", -3.1F, -13.2F, -3.9F, 1.2F, 1.4F, 0.8F);
        cube(primary, "right_ear_base", 1.3F, -12.0F, -4.0F, 2.5F, 2.8F, 1.0F);
        cube(primary, "right_ear_tip", 1.9F, -13.2F, -3.9F, 1.2F, 1.4F, 0.8F);

        PartDefinition accent = group(model, "accent");
        cube(accent, "left_inner", -3.15F, -11.7F, -4.35F, 1.2F, 1.8F, 0.5F);
        cube(accent, "right_inner", 1.95F, -11.7F, -4.35F, 1.2F, 1.8F, 0.5F);
    }

    private static void addRibbonBrooch(PartDefinition root) {
        PartDefinition model = model(root, "ribbon_brooch");
        PartDefinition primary = group(model, "primary");
        primary.addOrReplaceChild(
                "left_loop",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.2F, -1.2F, -0.4F, 3.2F, 2.4F, 0.8F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(-0.4F, 3.8F, -2.7F, 0.0F, 0.0F, 0.18F)
        );
        primary.addOrReplaceChild(
                "right_loop",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(0.0F, -1.2F, -0.4F, 3.2F, 2.4F, 0.8F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(0.4F, 3.8F, -2.7F, 0.0F, 0.0F, -0.18F)
        );
        primary.addOrReplaceChild(
                "left_tail",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.0F, 0.0F, -0.35F, 1.5F, 3.8F, 0.7F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(-0.8F, 4.2F, -2.65F, 0.0F, 0.0F, 0.16F)
        );
        primary.addOrReplaceChild(
                "right_tail",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.5F, 0.0F, -0.35F, 1.5F, 3.8F, 0.7F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(0.8F, 4.2F, -2.65F, 0.0F, 0.0F, -0.16F)
        );

        PartDefinition accent = group(model, "accent");
        cube(accent, "knot", -0.9F, 2.9F, -3.25F, 1.8F, 1.8F, 0.8F);
    }

    private static void addShortCape(PartDefinition root) {
        PartDefinition model = model(root, "short_cape");
        PartDefinition primary = group(model, "primary");
        cube(primary, "upper", -4.6F, 1.2F, 2.2F, 9.2F, 3.0F, 0.7F);
        cube(primary, "middle", -4.2F, 4.1F, 2.3F, 8.4F, 3.2F, 0.7F);
        cube(primary, "lower_left", -3.8F, 7.2F, 2.4F, 3.6F, 3.7F, 0.7F);
        cube(primary, "lower_right", 0.2F, 7.2F, 2.4F, 3.6F, 3.7F, 0.7F);

        PartDefinition accent = group(model, "accent");
        cube(accent, "collar", -4.3F, 0.3F, 2.0F, 8.6F, 1.2F, 0.9F);
        cube(accent, "left_clasp", -2.7F, 0.2F, -2.45F, 1.1F, 1.1F, 0.5F);
        cube(accent, "right_clasp", 1.6F, 0.2F, -2.45F, 1.1F, 1.1F, 0.5F);
    }

    private static void addCrossedSwords(PartDefinition root) {
        PartDefinition model = model(root, "crossed_swords");
        PartDefinition primary = group(model, "primary");
        addSword(primary, "left_sword", -0.48F);
        addSword(primary, "right_sword", 0.48F);

        PartDefinition accent = group(model, "accent");
        addSwordHilt(accent, "left_hilt", -0.48F);
        addSwordHilt(accent, "right_hilt", 0.48F);
    }

    private static void addBackGreatsword(PartDefinition root) {
        PartDefinition model = model(root, "back_greatsword");
        PartDefinition primary = group(model, "primary");
        PartDefinition blade = primary.addOrReplaceChild(
                "blade",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.15F, -6.0F, -0.4F, 2.3F, 15.0F, 0.8F, CubeDeformation.NONE)
                        .addBox(-0.65F, -7.4F, -0.3F, 1.3F, 1.5F, 0.6F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(0.0F, 3.0F, 2.8F, 0.0F, 0.0F, 0.22F)
        );
        blade.addOrReplaceChild(
                "ridge",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.25F, -5.8F, -0.55F, 0.5F, 14.0F, 0.4F, CubeDeformation.NONE),
                PartPose.ZERO
        );

        PartDefinition accent = group(model, "accent");
        PartDefinition hilt = accent.addOrReplaceChild(
                "hilt",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.6F, -0.4F, -0.5F, 5.2F, 0.8F, 1.0F, CubeDeformation.NONE)
                        .addBox(-0.55F, 0.2F, -0.4F, 1.1F, 3.3F, 0.8F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(-2.0F, 10.2F, 2.8F, 0.0F, 0.0F, 0.22F)
        );
        hilt.addOrReplaceChild(
                "pommel",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.8F, 3.1F, -0.6F, 1.6F, 1.2F, 1.2F, CubeDeformation.NONE),
                PartPose.ZERO
        );
    }

    private static void addMechanicalBackpack(PartDefinition root) {
        PartDefinition model = model(root, "mechanical_backpack");
        PartDefinition primary = group(model, "primary");
        cube(primary, "pack", -3.4F, 2.0F, 2.3F, 6.8F, 7.7F, 2.4F);
        cube(primary, "left_tank", -4.4F, 3.0F, 2.5F, 1.2F, 5.5F, 1.8F);
        cube(primary, "right_tank", 3.2F, 3.0F, 2.5F, 1.2F, 5.5F, 1.8F);
        cube(primary, "top_pipe", -2.3F, 1.1F, 3.0F, 4.6F, 1.0F, 1.0F);

        PartDefinition accent = group(model, "accent");
        cube(accent, "core", -1.4F, 4.0F, 4.45F, 2.8F, 2.8F, 0.5F);
        cube(accent, "left_light", -3.9F, 4.0F, 4.15F, 0.7F, 1.1F, 0.5F);
        cube(accent, "right_light", 3.2F, 4.0F, 4.15F, 0.7F, 1.1F, 0.5F);
    }

    private static void addSmallModelWings(PartDefinition root) {
        PartDefinition model = model(root, "small_model_wings");
        PartDefinition primary = group(model, "primary");
        addWing(primary, "left_wing", false);
        addWing(primary, "right_wing", true);

        PartDefinition accent = group(model, "accent");
        cube(accent, "center", -1.2F, 3.3F, 2.2F, 2.4F, 3.0F, 1.0F);
        accent.addOrReplaceChild(
                "left_tip",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.5F, -0.5F, -0.35F, 5.5F, 1.0F, 0.7F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(-0.8F, 3.7F, 2.8F, 0.0F, 0.0F, 0.42F)
        );
        accent.addOrReplaceChild(
                "right_tip",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(0.0F, -0.5F, -0.35F, 5.5F, 1.0F, 0.7F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(0.8F, 3.7F, 2.8F, 0.0F, 0.0F, -0.42F)
        );
    }

    private static void addSword(PartDefinition parent, String name, float rotation) {
        parent.addOrReplaceChild(
                name,
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.55F, -7.0F, -0.35F, 1.1F, 12.5F, 0.7F, CubeDeformation.NONE)
                        .addBox(-0.3F, -8.0F, -0.25F, 0.6F, 1.1F, 0.5F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(0.0F, 4.0F, 2.8F, 0.0F, 0.0F, rotation)
        );
    }

    private static void addSwordHilt(PartDefinition parent, String name, float rotation) {
        parent.addOrReplaceChild(
                name,
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.8F, -0.35F, -0.45F, 3.6F, 0.7F, 0.9F, CubeDeformation.NONE)
                        .addBox(-0.4F, 0.2F, -0.35F, 0.8F, 3.0F, 0.7F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(0.0F, 9.5F, 2.8F, 0.0F, 0.0F, rotation)
        );
    }

    private static void addWing(PartDefinition parent, String name, boolean right) {
        float sign = right ? 1.0F : -1.0F;
        PartDefinition wing = parent.addOrReplaceChild(
                name,
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(right ? 0.0F : -5.0F, -1.2F, -0.45F, 5.0F, 2.4F, 0.9F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(sign * 0.8F, 4.4F, 2.8F, 0.0F, 0.0F, sign * -0.28F)
        );
        wing.addOrReplaceChild(
                "upper_feather",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(right ? 0.0F : -4.4F, -0.7F, -0.35F, 4.4F, 1.4F, 0.7F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(sign * 3.0F, -0.7F, 0.0F, 0.0F, 0.0F, sign * -0.3F)
        );
        wing.addOrReplaceChild(
                "lower_feather",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(right ? 0.0F : -4.0F, -0.7F, -0.35F, 4.0F, 1.4F, 0.7F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(sign * 2.7F, 1.0F, 0.0F, 0.0F, 0.0F, sign * 0.34F)
        );
    }

    private static PartDefinition model(PartDefinition root, String name) {
        return root.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO);
    }

    private static PartDefinition group(PartDefinition model, String name) {
        return model.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO);
    }

    private static void cube(PartDefinition parent, String name, float x, float y, float z,
                             float width, float height, float depth) {
        parent.addOrReplaceChild(
                name,
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(x, y, z, width, height, depth, CubeDeformation.NONE),
                PartPose.ZERO
        );
    }

    private record ModelParts(ModelPart primary, ModelPart accent) {
    }
}
