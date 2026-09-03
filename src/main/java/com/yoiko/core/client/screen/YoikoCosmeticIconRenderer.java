package com.yoiko.core.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.client.cosmetic.ClientCosmeticMenuCatalog;
import com.yoiko.core.client.renderer.CosmeticAccessoryLayer;
import com.yoiko.core.client.renderer.CosmeticAccessoryModel;
import com.yoiko.core.network.CosmeticMenuCatalogPayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;

final class YoikoCosmeticIconRenderer {
    private static final ResourceLocation HEAD_CROWN = YoikoServerCore.id("textures/gui/cosmetic/badges/crystal_head_crown.png");
    private static final ResourceLocation SUPPORTER_CROWN = YoikoServerCore.id("textures/gui/cosmetic/badges/supporter_crown.png");
    private static final ResourceLocation CHEST_BADGE = YoikoServerCore.id("textures/gui/cosmetic/badges/crystal_chest_badge.png");
    private static final ResourceLocation PARTICLE_TRAIL_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/trail.png");
    private static final ResourceLocation PARTICLE_RING_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/ring.png");
    private static final ResourceLocation PARTICLE_ORBIT_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/orbit.png");
    private static final ResourceLocation PARTICLE_WING_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/wing.png");
    private static final ResourceLocation PARTICLE_AURA_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/aura.png");
    private static final ResourceLocation PARTICLE_COMPANION_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/companion.png");
    private static final ResourceLocation PARTICLE_TRAIL_MYTHIC_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/trail_mythic.png");
    private static final ResourceLocation PARTICLE_RING_MYTHIC_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/ring_mythic.png");
    private static final ResourceLocation PARTICLE_ORBIT_MYTHIC_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/orbit_mythic.png");
    private static final ResourceLocation PARTICLE_WING_MYTHIC_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/wing_mythic.png");
    private static final ResourceLocation PARTICLE_AURA_MYTHIC_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/aura_mythic.png");
    private static final ResourceLocation PARTICLE_COMPANION_MYTHIC_ICON = YoikoServerCore.id("textures/gui/cosmetic/particles/companion_mythic.png");
    private static final ResourceLocation SLOT_HEAD = YoikoServerCore.id("textures/gui/cosmetic/slots/equipment/head.png");
    private static final ResourceLocation SLOT_CHEST = YoikoServerCore.id("textures/gui/cosmetic/slots/equipment/chest.png");
    private static final ResourceLocation SLOT_PARTICLE = YoikoServerCore.id("textures/gui/cosmetic/slots/equipment/particle.png");
    private static final ResourceLocation SLOT_RANK = YoikoServerCore.id("textures/gui/cosmetic/slots/equipment/rank.png");
    private static final ResourceLocation PROFILE_SLOT_HEAD = YoikoServerCore.id("textures/gui/cosmetic/slots/profile/head.png");
    private static final ResourceLocation PROFILE_SLOT_CHEST = YoikoServerCore.id("textures/gui/cosmetic/slots/profile/chest.png");
    private static final ResourceLocation PROFILE_SLOT_FEET = YoikoServerCore.id("textures/gui/cosmetic/slots/profile/boot.png");
    private static final ResourceLocation PROFILE_SLOT_RANK = YoikoServerCore.id("textures/gui/cosmetic/slots/profile/rank.png");
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private static final CosmeticAccessoryModel ACCESSORY_MODEL = new CosmeticAccessoryModel(
            CosmeticAccessoryModel.createBodyLayer().bakeRoot()
    );

    private YoikoCosmeticIconRenderer() {
    }

    static void renderSlotBackground(GuiGraphics graphics, int x, int y, String type) {
        YoikoScreenStyle.renderInventoryCompactSlot(graphics, x, y);
        ResourceLocation overlay = switch (type) {
            case "HEAD" -> SLOT_HEAD;
            case "CHEST" -> SLOT_CHEST;
            case "PARTICLE", "FEET" -> SLOT_PARTICLE;
            case "RANK" -> SLOT_RANK;
            default -> null;
        };
        if (overlay != null) {
            graphics.blit(overlay, x, y, 18, 18, 0.0F, 0.0F, 18, 18, 18, 18);
        }
    }

    static void renderProfileSlotIcon(GuiGraphics graphics, int x, int y, String type) {
        ResourceLocation texture = switch (type) {
            case "HEAD" -> PROFILE_SLOT_HEAD;
            case "CHEST" -> PROFILE_SLOT_CHEST;
            case "PARTICLE", "FEET" -> PROFILE_SLOT_FEET;
            case "RANK" -> PROFILE_SLOT_RANK;
            default -> null;
        };
        if (texture != null) {
            graphics.blit(texture, x, y, 18, 18, 0.0F, 0.0F, 18, 18, 18, 18);
        }
    }

    static void refreshProfileSlotTextures() {
        var textureManager = Minecraft.getInstance().getTextureManager();
        textureManager.release(PROFILE_SLOT_HEAD);
        textureManager.release(PROFILE_SLOT_CHEST);
        textureManager.release(PROFILE_SLOT_FEET);
        textureManager.release(PROFILE_SLOT_RANK);
    }

    static void renderEntryIcon(GuiGraphics graphics, int x, int y, int size, String type, String id, boolean owned, int rankColor) {
        renderEntryIcon(graphics, x, y, size, type, id, owned, rankColor, "", "NONE");
    }

    static void renderEntryIcon(GuiGraphics graphics, int x, int y, int size, String type, String id, boolean owned,
                                int rankColor, String rarity, String particleCategory) {
        CosmeticMenuCatalogPayload.Entry catalogEntry = ClientCosmeticMenuCatalog.entry(id);
        renderEntryIcon(graphics, x, y, size, type, id, owned, rankColor, rarity, particleCategory,
                catalogEntry == null ? "" : catalogEntry.modelId(),
                catalogEntry == null ? 0xFFFFFFFF : catalogEntry.modelPrimaryColor(),
                catalogEntry == null ? 0xFFFFFFFF : catalogEntry.modelAccentColor());
    }

    static void renderEntryIcon(GuiGraphics graphics, int x, int y, int size, String type, String id, boolean owned,
                                int rankColor, String rarity, String particleCategory, String modelId,
                                int primaryColor, int accentColor) {
        if ("RANK".equals(type)) {
            renderRankIcon(graphics, x, y, size, id, owned);
            return;
        }
        if (("HEAD".equals(type) || "CHEST".equals(type))
                && renderModelIcon(graphics, x, y, size, type, modelId, primaryColor, accentColor)) {
            return;
        }
        if ("HEAD".equals(type)) {
            renderTexture(graphics, headTexture(id), x, y, size, 16, 16);
            return;
        }
        if ("CHEST".equals(type)) {
            int drawSize = size >= 24 ? 18 : 8;
            int offset = Math.max(0, (size - drawSize) / 2);
            renderTexture(graphics, CHEST_BADGE, x + offset, y + offset, drawSize, 16, 16);
            return;
        }
        if ("PARTICLE".equals(type)) {
            renderTexture(graphics, particleTexture(particleCategory, "MYTHIC".equalsIgnoreCase(rarity)), x, y, size, 16, 16);
            return;
        }
        renderFallbackIcon(graphics, x, y, size, owned ? 0xFFB8C0C2 : 0xFF566063);
    }

    private static boolean renderModelIcon(GuiGraphics graphics, int x, int y, int size, String type,
                                           String modelId, int primaryColor, int accentColor) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        ModelResourceLocation texturedModel = texturedModel(modelId);
        if (texturedModel == null && !ACCESSORY_MODEL.hasModel(modelId)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = graphics.pose();
        graphics.flush();
        graphics.enableScissor(x, y, x + size, y + size);
        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        poseStack.pushPose();
        try {
            if (texturedModel != null) {
                renderTexturedModel(minecraft, buffers, poseStack, texturedModel, x, y, size);
            } else {
                renderProceduralModel(buffers, poseStack, type, modelId, primaryColor, accentColor, x, y, size);
            }
            buffers.endBatch();
        } finally {
            poseStack.popPose();
            Lighting.setupForFlatItems();
            graphics.disableScissor();
        }
        return true;
    }

    private static void renderProceduralModel(MultiBufferSource.BufferSource buffers, PoseStack poseStack,
                                              String type, String modelId, int primaryColor, int accentColor,
                                              int x, int y, int size) {
        IconBounds bounds = proceduralBounds(modelId);
        float contentSize = Math.max(4.0F, size - 3.0F);
        float modelSpan = Math.max(bounds.width(), bounds.height());
        float scale = contentSize * 16.0F / modelSpan;
        poseStack.translate(x + size * 0.5F, y + size * 0.5F, 250.0F);
        // Match the front-facing GUI presentation used by textured cosmetic models.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-bounds.centerX() / 16.0F, -bounds.centerY() / 16.0F, 0.0F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEXTURE));
        ACCESSORY_MODEL.render(modelId, poseStack, consumer, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, primaryColor, accentColor);
    }

    private static void renderTexturedModel(Minecraft minecraft, MultiBufferSource.BufferSource buffers,
                                            PoseStack poseStack, ModelResourceLocation location,
                                            int x, int y, int size) {
        BakedModel model = minecraft.getModelManager().getModel(location);
        if (model == minecraft.getModelManager().getMissingModel()) {
            return;
        }
        poseStack.translate(x + size * 0.5F, y + size * 0.5F, 250.0F);
        poseStack.scale(size, -size, size);
        model = ClientHooks.handleCameraTransforms(poseStack, model, ItemDisplayContext.GUI, false);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        minecraft.getItemRenderer().renderModelLists(model, ItemStack.EMPTY, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, poseStack, consumer);
    }

    private static IconBounds proceduralBounds(String modelId) {
        return switch (modelId) {
            case "rabbit_crown" -> new IconBounds(-4.8F, 4.8F, -14.85F, -8.4F);
            case "supporter_crown" -> new IconBounds(-4.7F, 4.7F, -13.0F, -8.5F);
            case "crystal_crown" -> new IconBounds(-4.7F, 4.7F, -14.4F, -8.5F);
            case "witch_hat" -> new IconBounds(-5.5F, 5.5F, -20.0F, -8.4F);
            case "red_beret" -> new IconBounds(-4.4F, 4.4F, -12.4F, -8.4F);
            case "cat_ear_headband" -> new IconBounds(-4.3F, 4.3F, -13.2F, -6.9F);
            case "crystal_badge" -> new IconBounds(-2.0F, 2.0F, 2.0F, 7.2F);
            case "ribbon_brooch" -> new IconBounds(-3.6F, 3.6F, 2.6F, 8.0F);
            case "short_cape" -> new IconBounds(-4.6F, 4.6F, 0.2F, 10.9F);
            case "crossed_swords" -> new IconBounds(-7.0F, 7.0F, -4.5F, 13.0F);
            case "back_greatsword" -> new IconBounds(-4.8F, 3.2F, -4.5F, 14.7F);
            case "mechanical_backpack" -> new IconBounds(-4.4F, 4.4F, 1.1F, 9.7F);
            case "small_model_wings" -> new IconBounds(-9.2F, 9.2F, 1.4F, 8.2F);
            default -> new IconBounds(-6.0F, 6.0F, -6.0F, 6.0F);
        };
    }

    private static ModelResourceLocation texturedModel(String modelId) {
        return switch (modelId) {
            case "sylveon_headpiece" -> CosmeticAccessoryLayer.SYLVEON_HEADPIECE_MODEL;
            case "sylveon_headpiece_shiny" -> CosmeticAccessoryLayer.SYLVEON_HEADPIECE_SHINY_MODEL;
            case "jolteon_headpiece" -> CosmeticAccessoryLayer.JOLTEON_HEADPIECE_MODEL;
            case "jolteon_headpiece_shiny" -> CosmeticAccessoryLayer.JOLTEON_HEADPIECE_SHINY_MODEL;
            case "vaporeon_headpiece" -> CosmeticAccessoryLayer.VAPOREON_HEADPIECE_MODEL;
            case "vaporeon_headpiece_shiny" -> CosmeticAccessoryLayer.VAPOREON_HEADPIECE_SHINY_MODEL;
            default -> null;
        };
    }

    private static ResourceLocation headTexture(String id) {
        String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return normalized.contains("supporter") ? SUPPORTER_CROWN : HEAD_CROWN;
    }

    private static ResourceLocation particleTexture(String category, boolean mythic) {
        return switch (category == null ? "NONE" : category.toUpperCase(Locale.ROOT)) {
            case "RING" -> mythic ? PARTICLE_RING_MYTHIC_ICON : PARTICLE_RING_ICON;
            case "ORBIT" -> mythic ? PARTICLE_ORBIT_MYTHIC_ICON : PARTICLE_ORBIT_ICON;
            case "WINGS" -> mythic ? PARTICLE_WING_MYTHIC_ICON : PARTICLE_WING_ICON;
            case "AURA" -> mythic ? PARTICLE_AURA_MYTHIC_ICON : PARTICLE_AURA_ICON;
            case "COMPANION" -> mythic ? PARTICLE_COMPANION_MYTHIC_ICON : PARTICLE_COMPANION_ICON;
            default -> mythic ? PARTICLE_TRAIL_MYTHIC_ICON : PARTICLE_TRAIL_ICON;
        };
    }

    private static void renderTexture(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size, int sourceWidth, int sourceHeight) {
        graphics.blit(texture, x, y, size, size, 0.0F, 0.0F, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
    }

    private static void renderRankIcon(GuiGraphics graphics, int x, int y, int size, String id, boolean owned) {
        int drawSize = size <= 16 ? 12 : Math.max(12, Math.round(size * 0.75F));
        int offset = Math.max(0, (size - drawSize) / 2);
        int drawX = x + offset;
        int drawY = y + offset;
        renderTexture(graphics, rankTexture(id), drawX, drawY, drawSize, 8, 8);
    }

    private static ResourceLocation rankTexture(String id) {
        String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        if (normalized.startsWith("rank_")) {
            normalized = normalized.substring("rank_".length());
        }
        if (normalized.isBlank()) {
            normalized = "supporter";
        }
        return YoikoServerCore.id("textures/gui/rank/" + normalized + ".png");
    }

    private static void renderFallbackIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + size, color);
        graphics.fill(x + 3, y + 3, x + size - 3, y + size - 3, 0xFF1E2629);
    }

    private record IconBounds(float minX, float maxX, float minY, float maxY) {
        private float width() {
            return Math.max(1.0F, maxX - minX);
        }

        private float height() {
            return Math.max(1.0F, maxY - minY);
        }

        private float centerX() {
            return (minX + maxX) * 0.5F;
        }

        private float centerY() {
            return (minY + maxY) * 0.5F;
        }
    }

}
