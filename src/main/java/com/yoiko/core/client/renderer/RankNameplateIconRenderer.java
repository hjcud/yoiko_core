package com.yoiko.core.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.client.rank.ClientRankNameplateCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import org.joml.Matrix4f;

public class RankNameplateIconRenderer {
    private static final ResourceLocation FALLBACK_TEXTURE = YoikoServerCore.id("textures/gui/rank/supporter.png");
    private static final float ICON_SIZE = 8.0F;
    private static final float ICON_GAP = 2.0F;

    @SubscribeEvent
    public void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double distance = minecraft.getEntityRenderDispatcher().distanceToSqr(player);
        if (!shouldRenderNameplateIcon(event, minecraft, player, distance)) {
            return;
        }

        ClientRankNameplateCache.Entry entry = ClientRankNameplateCache.get(player.getUUID());
        if (entry == null) {
            return;
        }

        if (!ClientHooks.isNameplateInRenderDistance(player, distance)) {
            return;
        }

        Vec3 namePosition = player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, player.getViewYRot(event.getPartialTick()));
        if (namePosition == null) {
            return;
        }

        Font font = event.getEntityRenderer().getFont();
        int textWidth = font.width(event.getContent());
        int yOffset = "deadmau5".equals(event.getContent().getString()) ? -10 : 0;
        float left = -textWidth / 2.0F - ICON_GAP - ICON_SIZE;
        float top = yOffset - 1.0F;
        ResourceLocation texture = resolveTexture(minecraft, entry.texture());

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(namePosition.x, namePosition.y + 0.5D, namePosition.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        if (!player.isDiscrete()) {
            renderIcon(bufferSource.getBuffer(RenderType.textSeeThrough(texture)), matrix, left, top, event.getPackedLight(), 120);
        }
        renderIcon(bufferSource.getBuffer(RenderType.text(texture)), matrix, left, top, event.getPackedLight(), 255);
        poseStack.popPose();
    }

    private static boolean shouldRenderNameplateIcon(RenderNameTagEvent event, Minecraft minecraft,
                                                     Player player, double distance) {
        // The local player's vanilla nameplate is hidden in third person and GUI model previews.
        // RenderNameTagEvent still fires for those render passes, so explicitly reject them.
        if (player.isLocalPlayer() || player == minecraft.getCameraEntity() || event.canRender().isFalse()) {
            return false;
        }
        if (event.canRender().isTrue()) {
            return true;
        }

        float vanillaRange = player.isDiscrete() ? 32.0F : 64.0F;
        if (distance >= vanillaRange * vanillaRange || minecraft.player == null) {
            return false;
        }

        Player localPlayer = minecraft.player;
        boolean normallyVisible = !player.isInvisibleTo(localPlayer);
        Team team = player.getTeam();
        Team localTeam = localPlayer.getTeam();
        if (team != null) {
            return switch (team.getNameTagVisibility()) {
                case ALWAYS -> normallyVisible;
                case NEVER -> false;
                case HIDE_FOR_OTHER_TEAMS -> localTeam == null
                        ? normallyVisible
                        : team.isAlliedTo(localTeam) && (team.canSeeFriendlyInvisibles() || normallyVisible);
                case HIDE_FOR_OWN_TEAM -> localTeam == null
                        ? normallyVisible
                        : !team.isAlliedTo(localTeam) && normallyVisible;
            };
        }

        return Minecraft.renderNames() && normallyVisible && !player.isVehicle();
    }

    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRankNameplateCache.clear();
    }

    private static ResourceLocation resolveTexture(Minecraft minecraft, ResourceLocation texture) {
        if (minecraft.getResourceManager().getResource(texture).isPresent()) {
            return texture;
        }
        return minecraft.getResourceManager().getResource(FALLBACK_TEXTURE).isPresent() ? FALLBACK_TEXTURE : texture;
    }

    private static void renderIcon(VertexConsumer consumer, Matrix4f matrix, float left, float top, int packedLight, int alpha) {
        float right = left + ICON_SIZE;
        float bottom = top + ICON_SIZE;
        consumer.addVertex(matrix, left, bottom, 0.0F).setColor(255, 255, 255, alpha).setUv(0.0F, 1.0F).setLight(packedLight);
        consumer.addVertex(matrix, right, bottom, 0.0F).setColor(255, 255, 255, alpha).setUv(1.0F, 1.0F).setLight(packedLight);
        consumer.addVertex(matrix, right, top, 0.0F).setColor(255, 255, 255, alpha).setUv(1.0F, 0.0F).setLight(packedLight);
        consumer.addVertex(matrix, left, top, 0.0F).setColor(255, 255, 255, alpha).setUv(0.0F, 0.0F).setLight(packedLight);
    }
}
