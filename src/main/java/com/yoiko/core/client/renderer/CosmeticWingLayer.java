package com.yoiko.core.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yoiko.core.client.cosmetic.ClientParticleCosmeticCache;
import com.yoiko.core.client.cosmetic.ClientCosmeticPreviewState;
import com.yoiko.core.client.cosmetic.ClientParticleRenderBudget;
import com.yoiko.core.config.YoikoClientConfig;
import com.yoiko.core.cosmetic.ParticleCategory;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Samples wing points from the exact transform used by {@link PlayerModel#body},
 * then emits vanilla particles at the resulting world positions.
 */
public final class CosmeticWingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final double TAU = Math.PI * 2.0D;
    private static final int EDGE_POINTS = 9;
    private static final int NEAR_EMISSION_INTERVAL = 1;
    private static final int FAR_EMISSION_INTERVAL = 2;
    private static final double FAR_DISTANCE_SQR = 18.0D * 18.0D;
    private static final double WORLD_RENDER_SANITY_DISTANCE_SQR = 16.0D;
    /*
     * Bow mesh vertices in player-body local block coordinates. The outer/inner pairs form an actual
     * ribbon strip around a hollow loop; Z is curved so the loop also has readable depth from the side.
     */
    private static final float[][] RIBBON_LOOP_OUTER_VERTICES = {
            {0.08F, 0.14F, 0.36F}, {0.24F, -0.08F, 0.42F}, {0.60F, -0.22F, 0.50F},
            {0.86F, 0.01F, 0.54F}, {0.64F, 0.27F, 0.49F}, {0.25F, 0.31F, 0.40F},
            {0.08F, 0.23F, 0.35F}
    };
    private static final float[][] RIBBON_LOOP_INNER_VERTICES = {
            {0.15F, 0.16F, 0.31F}, {0.30F, 0.03F, 0.35F}, {0.54F, -0.08F, 0.41F},
            {0.66F, 0.01F, 0.44F}, {0.50F, 0.15F, 0.40F}, {0.28F, 0.20F, 0.34F},
            {0.15F, 0.21F, 0.31F}
    };
    private static final float[][] RIBBON_TAIL_VERTICES = {
            {0.04F, 0.24F, 0.42F}, {0.19F, 0.27F, 0.40F}, {0.30F, 0.52F, 0.43F},
            {0.50F, 0.84F, 0.47F}, {0.36F, 0.72F, 0.42F}, {0.24F, 0.88F, 0.39F},
            {0.15F, 0.54F, 0.37F}, {0.04F, 0.24F, 0.42F}
    };
    private static final float[][] RIBBON_KNOT_FRONT_VERTICES = {
            {-0.10F, 0.10F, 0.29F}, {0.00F, 0.06F, 0.27F}, {0.10F, 0.10F, 0.29F},
            {0.145F, 0.17F, 0.31F}, {0.13F, 0.26F, 0.30F}, {0.00F, 0.32F, 0.27F},
            {-0.13F, 0.26F, 0.30F}, {-0.145F, 0.17F, 0.31F}, {-0.10F, 0.10F, 0.29F}
    };
    private static final float[][] RIBBON_KNOT_BACK_VERTICES = {
            {-0.08F, 0.12F, 0.40F}, {0.00F, 0.09F, 0.41F}, {0.08F, 0.12F, 0.40F},
            {0.11F, 0.17F, 0.39F}, {0.10F, 0.25F, 0.40F}, {0.00F, 0.29F, 0.41F},
            {-0.10F, 0.25F, 0.40F}, {-0.11F, 0.17F, 0.39F}, {-0.08F, 0.12F, 0.40F}
    };
    private static final Map<AbstractClientPlayer, Integer> LAST_EMISSION_TICK = new WeakHashMap<>();

    public CosmeticWingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean localPreview = player == minecraft.player && ClientCosmeticPreviewState.isActive();
        boolean previewing = localPreview && "WINGS".equals(ClientCosmeticPreviewState.particleCategory());
        if (localPreview && ClientCosmeticPreviewState.overridesSlot("CHEST") && !previewing) {
            return;
        }
        if ((!previewing && !ClientParticleRenderBudget.isAllowed(player.getUUID()))
                || com.yoiko.core.client.cosmetic.YoikoParticleSettings.density() <= 0) {
            return;
        }
        String particleId = previewing
                ? ClientCosmeticPreviewState.particleId()
                : ClientParticleCosmeticCache.equippedForCategory(player.getUUID(), ParticleCategory.WINGS);
        WingStyle style = WingStyle.fromId(particleId);
        ClientLevel level = minecraft.level;
        if (player.isInvisible() || style == null || level == null || minecraft.gameRenderer.getMainCamera() == null) {
            return;
        }

        double cameraDistanceSqr = minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(player.position());
        double configuredDistance = YoikoClientConfig.PARTICLE_RENDER_DISTANCE.get();
        if (cameraDistanceSqr > configuredDistance * configuredDistance) {
            return;
        }
        ParticleStatus particleStatus = minecraft.options.particles().get();
        if (particleStatus == ParticleStatus.MINIMAL) {
            return;
        }
        int interval = cameraDistanceSqr > FAR_DISTANCE_SQR ? FAR_EMISSION_INTERVAL : NEAR_EMISSION_INTERVAL;
        if (particleStatus == ParticleStatus.DECREASED) {
            interval++;
        }
        if (style.shape() == WingShape.GRAND_RIBBON) {
            interval = Math.max(interval, 2);
        }
        int tick = player.tickCount;
        if (Math.floorMod(tick, interval) != 0 || LAST_EMISSION_TICK.getOrDefault(player, Integer.MIN_VALUE) == tick) {
            return;
        }

        poseStack.pushPose();
        this.getParentModel().body.translateAndRotate(poseStack);
        Matrix4f bodyTransform = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();

        Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
        WingEmitter emitter = new WingEmitter(minecraft.particleEngine, bodyTransform, cameraPosition, style, 0.0F);
        if (!emitter.isWorldRender(player, partialTick)) {
            return;
        }

        LAST_EMISSION_TICK.put(player, tick);
        int phase = Math.floorDiv(tick, interval);
        switch (style.shape()) {
            case FEATHER -> emitFeatherWings(emitter, style, phase, false);
            case ELECTRIC -> emitFeatherWings(emitter, style, phase, true);
            case BUTTERFLY -> emitButterflyWings(emitter, style, phase);
            case DRAGON -> emitDragonWings(emitter, style, phase);
            case PEACOCK -> emitPeacockWings(emitter, style, phase);
            case CATHEDRAL -> emitCathedralWings(emitter, style, phase);
            case IRIS -> emitIrisWings(emitter, style, phase);
            case GRAND_RIBBON -> emitGrandRibbonWings(emitter, style, phase);
        }
    }

    private static void emitFeatherWings(WingEmitter emitter, WingStyle style, int phase, boolean angular) {
        for (int side : new int[] {-1, 1}) {
            for (int i = 0; i < EDGE_POINTS; i++) {
                float t = (float) i / (EDGE_POINTS - 1);
                float edgeX = side * (0.20F + t * style.width());
                float edgeY = 0.18F - (float) Math.sin(t * Math.PI) * style.height() + t * 0.20F;
                if (angular) {
                    edgeX += side * (i % 2 == 0 ? 0.055F : -0.035F);
                    edgeY += i % 2 == 0 ? -0.045F : 0.035F;
                }
                float edgeZ = 0.18F + t * style.depth();
                emitter.emit(style.edgeParticle(), edgeX, edgeY, edgeZ, style.edgeRise(), style.edgeLifetime());

                int layer = Math.floorMod(phase + i, style.fillLayers()) + 1;
                float fill = (float) layer / (style.fillLayers() + 1);
                float innerX = side * (0.10F + t * 0.16F);
                float lowerY = 0.34F + t * 0.12F;
                float x = lerp(innerX, edgeX, fill);
                float y = lerp(lowerY, edgeY, fill);
                float z = edgeZ - 0.025F * (1.0F - fill);
                if (angular && ((i + layer) & 1) == 0) {
                    x += side * 0.025F;
                    y -= 0.025F;
                }
                emitter.emit(style.fillParticle(), x, y, z, style.fillRise(), style.fillLifetime());
            }
        }
    }

    private static void emitButterflyWings(WingEmitter emitter, WingStyle style, int phase) {
        emitter = emitter.withDepthOffset(-0.07F);
        int outlinePass = Math.floorMod(phase, 3);
        for (int side : new int[] {-1, 1}) {
            for (int lobe = 0; lobe < 2; lobe++) {
                int points = lobe == 0 ? 10 : 8;
                float centerX = side * (lobe == 0 ? 0.34F : 0.25F);
                float centerY = lobe == 0 ? 0.02F : 0.38F;
                float width = lobe == 0 ? 0.30F : 0.22F;
                float height = lobe == 0 ? 0.35F : 0.25F;
                for (int i = 0; i < points; i++) {
                    if (Math.floorMod(i + lobe, 3) != outlinePass) {
                        continue;
                    }
                    float angle = (float) (Math.PI * 2.0D * i / points);
                    float x = centerX + side * (float) Math.cos(angle) * width;
                    float y = centerY + (float) Math.sin(angle) * height;
                    float z = 0.33F;
                    emitter.emit(style.edgeParticle(), x, y, z, style.edgeRise(), style.edgeLifetime());
                }

                // One moving highlight per lobe keeps the pink inner glow readable without filling the
                // entire butterfly silhouette with persistent square Dust particles.
                int highlightIndex = Math.floorMod(phase * 2 + lobe * 3 + (side > 0 ? 0 : points / 2), points);
                float highlightAngle = (float) (Math.PI * 2.0D * highlightIndex / points);
                float highlightX = centerX + side * (float) Math.cos(highlightAngle) * width * 0.57F;
                float highlightY = centerY + (float) Math.sin(highlightAngle) * height * 0.57F;
                emitter.emit(style.fillParticle(), highlightX, highlightY, 0.315F,
                        style.fillRise(), style.fillLifetime());

                // A faint point closer to the body hints at the original inner fill and connects both lobes.
                if (Math.floorMod(phase + lobe, 2) == 0) {
                    emitter.emit(style.fillParticle(),
                            lerp(side * 0.04F, highlightX, 0.48F),
                            lerp(0.24F, highlightY, 0.48F),
                            0.305F, style.fillRise(), style.fillLifetime());
                }
            }
        }
    }

    private static void emitDragonWings(WingEmitter emitter, WingStyle style, int phase) {
        for (int side : new int[] {-1, 1}) {
            for (int i = 0; i < 11; i++) {
                float t = (float) i / 10.0F;
                float x = side * (0.18F + t * style.width());
                float arch = (float) Math.sin(t * Math.PI);
                float y = 0.22F - arch * style.height() + t * 0.25F;
                if (i == 4 || i == 8) {
                    y += 0.13F;
                }
                float z = 0.20F + t * style.depth();
                emitter.emit(style.edgeParticle(), x, y, z, style.edgeRise(), style.edgeLifetime());

                int layer = Math.floorMod(phase + i, style.fillLayers()) + 1;
                float fill = (float) layer / (style.fillLayers() + 1);
                emitter.emit(style.fillParticle(),
                        lerp(side * 0.10F, x, fill), lerp(0.30F, y, fill), z - 0.02F,
                        style.fillRise(), style.fillLifetime());
            }
        }
    }

    private static void emitPeacockWings(WingEmitter emitter, WingStyle style, int phase) {
        emitter = emitter.withDepthOffset(-0.08F);
        int featherCount = 9;
        float rootY = 0.64F;
        float[] root = {0.0F, rootY, 0.29F};
        float[][] tips = new float[featherCount][];
        float[][] innerPoints = new float[featherCount][];
        for (int feather = 0; feather < featherCount; feather++) {
            float spread = (float) (Math.PI * (1.01D + 0.98D * feather / (featherCount - 1.0D)));
            float centerWeight = 1.0F - Math.abs(feather - 4) / 4.0F;
            float length = 0.72F + centerWeight * 0.17F;
            float tipX = (float) Math.cos(spread) * length;
            float tipY = rootY + (float) Math.sin(spread) * length;
            float[] tip = {tipX, tipY, 0.36F};
            tips[feather] = tip;
            float innerRadius = 0.30F;
            innerPoints[feather] = new float[] {(float) Math.cos(spread) * innerRadius,
                    rootY + (float) Math.sin(spread) * innerRadius, 0.315F};

            if (Math.floorMod(feather + phase, 3) != 0) {
                continue;
            }
            float[] control = {tipX * 0.42F, 0.50F + (tipY - 0.50F) * 0.28F, 0.32F};
            emitQuadraticCurve(emitter, style.edgeParticle(), root, control, tip,
                    style.edgeRise(), style.edgeLifetime(), 4);

            float eyeRadius = feather == Math.floorMod(phase / 2, featherCount) ? 0.082F : 0.068F;
            for (int eye = 0; eye < 8; eye++) {
                float eyeAngle = (float) (TAU * eye / 8.0D);
                emitter.emit(eye % 2 == 0 ? style.edgeParticle() : dust(0x5DE1CA, 0.48F),
                        tipX + (float) Math.cos(eyeAngle) * eyeRadius,
                        tipY + (float) Math.sin(eyeAngle) * eyeRadius, 0.36F,
                        style.edgeRise(), style.edgeLifetime());
            }
            for (int eye = 0; eye < 4; eye++) {
                float eyeAngle = (float) (TAU * eye / 4.0D);
                emitter.emit(dust(0x4A67E8, 0.54F),
                        tipX + (float) Math.cos(eyeAngle) * eyeRadius * 0.48F,
                        tipY + (float) Math.sin(eyeAngle) * eyeRadius * 0.48F, 0.345F,
                        style.fillRise(), style.fillLifetime());
            }
            emitter.emit(dust(0xFFD86A, 0.62F), tipX, tipY, 0.335F,
                    style.fillRise(), style.fillLifetime());
        }
        for (int feather = 0; feather < featherCount - 1; feather++) {
            if (Math.floorMod(phase + feather, 3) == 0) {
                emitLine(emitter, dust(0x8AF5D5, 0.54F), tips[feather], tips[feather + 1],
                        style.fillRise(), style.fillLifetime(), 2);
                emitLine(emitter, dust(0x47BFA9, 0.42F), innerPoints[feather], innerPoints[feather + 1],
                        style.fillRise(), style.fillLifetime(), 1);
            }
        }
    }

    private static void emitCathedralWings(WingEmitter emitter, WingStyle style, int phase) {
        float centerX = 0.0F;
        float centerY = 0.14F;
        float centerZ = 0.39F;
        float rotation = phase * 0.045F;
        ParticleOptions gold = dust(0xFFE58A, 0.52F);
        ParticleOptions violet = dust(0xB17CFF, 0.50F);
        ParticleOptions white = dust(0xFFFBE8, 0.58F);

        emitCircle(emitter, style.edgeParticle(), centerX, centerY, centerZ,
                0.43F, 18, rotation, style.edgeRise(), style.edgeLifetime());
        emitCircle(emitter, violet, centerX, centerY, centerZ - 0.01F,
                0.25F, 12, -rotation * 1.35F, style.fillRise(), style.fillLifetime());

        for (int point = 0; point < 16; point++) {
            float angle = rotation + (float) (TAU * point / 16.0D);
            float radius = (point & 1) == 0 ? 0.30F : 0.135F;
            float x = centerX + (float) Math.cos(angle) * radius;
            float y = centerY + (float) Math.sin(angle) * radius;
            emitter.emit(gold, x, y, centerZ - 0.018F,
                    style.fillRise(), style.fillLifetime());
            if (point % 4 == 0) {
                emitLine(emitter, violet,
                        new float[] {centerX, centerY, centerZ - 0.02F},
                        new float[] {x, y, centerZ - 0.02F},
                        style.fillRise(), style.fillLifetime(), 2);
            }
        }

        emitLine(emitter, white,
                new float[] {0.0F, centerY - 0.33F, centerZ - 0.03F},
                new float[] {0.0F, centerY + 0.33F, centerZ - 0.03F},
                style.edgeRise(), style.edgeLifetime(), 5);
        emitLine(emitter, white,
                new float[] {-0.23F, centerY, centerZ - 0.03F},
                new float[] {0.23F, centerY, centerZ - 0.03F},
                style.edgeRise(), style.edgeLifetime(), 4);

        for (int side : new int[] {-1, 1}) {
            float[] root = {side * 0.22F, centerY, centerZ};
            float[][] tips = {
                    {side * 0.90F, -0.34F, 0.45F},
                    {side * 1.02F, 0.03F, 0.47F},
                    {side * 0.82F, 0.52F, 0.44F}
            };
            for (int ray = 0; ray < tips.length; ray++) {
                float[] tip = tips[ray];
                float[] control = {
                        side * (0.48F + ray * 0.09F),
                        centerY + (tip[1] - centerY) * 0.18F,
                        0.42F
                };
                emitQuadraticCurve(emitter, ray == 1 ? white : style.edgeParticle(),
                        root, control, tip, style.edgeRise(), style.edgeLifetime(), 5);
                float runeX = lerp(root[0], tip[0], 0.62F);
                float runeY = lerp(root[1], tip[1], 0.62F);
                if (ray == Math.floorMod(phase / 3, 3)) {
                    emitDiamond(emitter, gold, runeX, runeY, 0.425F, 0.055F,
                            style.fillRise(), style.fillLifetime());
                } else {
                    emitter.emit(violet, runeX, runeY, 0.425F,
                            style.fillRise(), style.fillLifetime());
                }
            }
            emitLine(emitter, violet, tips[0], tips[1],
                    style.fillRise(), style.fillLifetime(), 3);
            emitLine(emitter, violet, tips[1], tips[2],
                    style.fillRise(), style.fillLifetime(), 3);
        }

        for (int rune = 0; rune < 8; rune++) {
            float angle = -rotation * 0.75F + (float) (TAU * rune / 8.0D);
            float x = centerX + (float) Math.cos(angle) * 0.51F;
            float y = centerY + (float) Math.sin(angle) * 0.51F;
            if (rune == Math.floorMod(phase / 2, 8)) {
                emitDiamond(emitter, ParticleTypes.END_ROD, x, y, centerZ, 0.035F,
                        style.fillRise(), 5);
            } else {
                emitter.emit(gold, x, y, centerZ, style.fillRise(), style.fillLifetime());
            }
        }
    }

    private static void emitIrisWings(WingEmitter emitter, WingStyle style, int phase) {
        emitter = emitter.withDepthOffset(-0.08F);
        float open = 0.16F + (float) ((Math.sin(phase * 0.11D) + 1.0D) * 0.06D);
        for (int side : new int[] {-1, 1}) {
            float centerX = side * 0.40F;
            float centerY = 0.18F;
            for (int blade = 0; blade < 6; blade++) {
                float angle = (float) (blade * Math.PI * 2.0D / 6.0D + phase * 0.035D);
                float rootX = centerX + side * (float) Math.cos(angle) * open;
                float rootY = centerY + (float) Math.sin(angle) * open;
                float tipX = centerX + side * (float) Math.cos(angle + 0.55F) * 0.38F;
                float tipY = centerY + (float) Math.sin(angle + 0.55F) * 0.38F;
                emitLine(emitter, blade % 2 == 0 ? style.edgeParticle() : style.fillParticle(),
                        new float[] {rootX, rootY, 0.33F}, new float[] {tipX, tipY, 0.36F},
                        style.edgeRise(), blade % 2 == 0 ? style.edgeLifetime() : style.fillLifetime(), 2);
                float innerTipX = centerX + side * (float) Math.cos(angle + 0.90F) * 0.25F;
                float innerTipY = centerY + (float) Math.sin(angle + 0.90F) * 0.25F;
                emitLine(emitter, style.fillParticle(),
                        new float[] {rootX, rootY, 0.325F}, new float[] {innerTipX, innerTipY, 0.345F},
                        style.fillRise(), style.fillLifetime(), 1);
            }
            for (int hub = 0; hub < 8; hub++) {
                float angle = (float) (Math.PI * 2.0D * hub / 8.0D);
                emitter.emit(style.fillParticle(), centerX + side * (float) Math.cos(angle) * 0.075F,
                        centerY + (float) Math.sin(angle) * 0.075F, 0.32F,
                        style.fillRise(), style.fillLifetime());
            }
        }
    }

    private static void emitGrandRibbonWings(WingEmitter emitter, WingStyle style, int phase) {
        emitter = emitter.withDepthOffset(-0.08F);
        float loopScale = 1.0F + (float) Math.sin(phase * 0.075D) * 0.006F;
        float tailSway = (float) Math.sin(phase * 0.10D) * 0.020F;
        float tailLift = (float) Math.cos(phase * 0.085D) * 0.006F;
        ParticleOptions highlight = dust(0xE8FAFF, 0.32F);
        ParticleOptions ribbonBody = dust(0x93DFF0, 0.36F);
        ParticleOptions thickEdge = dust(0x72CDEB, 0.52F);
        ParticleOptions shadowEdge = dust(0x397E91, 0.30F);

        for (int side : new int[] {-1, 1}) {
            float[][] loopOuter = mirrorRibbonVertices(RIBBON_LOOP_OUTER_VERTICES, side, loopScale);
            float[][] loopInner = mirrorRibbonVertices(RIBBON_LOOP_INNER_VERTICES, side, loopScale);
            emitRibbonStripMesh(emitter, style, loopOuter, loopInner,
                    thickEdge, ribbonBody, highlight, shadowEdge);

            float[][] tail = mirrorRibbonVertices(RIBBON_TAIL_VERTICES, side, 1.0F);
            bendRibbonTail(tail, side, tailSway, tailLift);
            float[][] tailBack = offsetRibbonDepth(tail, 0.055F);
            emitPolyline(emitter, thickEdge, tail,
                    style.edgeRise(), style.edgeLifetime(), 2);
            emitPolyline(emitter, shadowEdge, tailBack,
                    style.fillRise(), style.fillLifetime(), 1);
            emitLine(emitter, ribbonBody, tail[0], tail[4],
                    style.fillRise(), style.fillLifetime(), 3);
            emitLine(emitter, highlight, tail[1], tail[5],
                    style.fillRise(), style.fillLifetime(), 3);
            emitLine(emitter, ribbonBody, tail[2], tail[6],
                    style.fillRise(), style.fillLifetime(), 2);
            for (int index : new int[] {1, 3, 4, 5, 6}) {
                emitLine(emitter, shadowEdge, tail[index], tailBack[index],
                        style.fillRise(), style.fillLifetime(), 1);
            }
        }

        emitPolyline(emitter, thickEdge, RIBBON_KNOT_FRONT_VERTICES,
                style.edgeRise(), style.edgeLifetime(), 1);
        emitPolyline(emitter, shadowEdge, RIBBON_KNOT_BACK_VERTICES,
                style.fillRise(), style.fillLifetime(), 1);
        float[] knotCenter = {0.0F, 0.19F, 0.255F};
        for (int index : new int[] {0, 2, 4, 6}) {
            emitLine(emitter, ribbonBody, RIBBON_KNOT_FRONT_VERTICES[index], knotCenter,
                    style.fillRise(), style.fillLifetime(), 2);
            emitLine(emitter, shadowEdge, RIBBON_KNOT_FRONT_VERTICES[index],
                    RIBBON_KNOT_BACK_VERTICES[index], style.fillRise(), style.fillLifetime(), 1);
        }
        emitLine(emitter, highlight, new float[] {-0.09F, 0.13F, 0.265F},
                new float[] {0.09F, 0.13F, 0.265F}, style.fillRise(), style.fillLifetime(), 3);
    }

    private static void emitRibbonStripMesh(WingEmitter emitter, WingStyle style,
                                            float[][] outer, float[][] inner,
                                            ParticleOptions edge, ParticleOptions body,
                                            ParticleOptions highlight, ParticleOptions shadow) {
        float[][] outerBack = offsetRibbonDepth(outer, 0.060F);
        float[][] innerBack = offsetRibbonDepth(inner, 0.050F);
        emitPolyline(emitter, edge, outer, style.edgeRise(), style.edgeLifetime(), 2);
        emitPolyline(emitter, highlight, inner, style.fillRise(), style.fillLifetime(), 2);
        emitPolyline(emitter, shadow, outerBack, style.fillRise(), style.fillLifetime(), 1);
        emitPolyline(emitter, shadow, innerBack, style.fillRise(), style.fillLifetime(), 1);

        float[][] midline = midpointRibbonVertices(outer, inner);
        emitPolyline(emitter, body, midline, style.fillRise(), style.fillLifetime(), 2);
        for (int index : new int[] {0, 1, 3, 5, 6}) {
            emitLine(emitter, body, outer[index], inner[index],
                    style.fillRise(), style.fillLifetime(), 1);
        }
        for (int index : new int[] {0, 2, 3, 4, 6}) {
            emitLine(emitter, shadow, outer[index], outerBack[index],
                    style.fillRise(), style.fillLifetime(), 1);
            emitLine(emitter, shadow, inner[index], innerBack[index],
                    style.fillRise(), style.fillLifetime(), 1);
        }
        emitLine(emitter, body, outer[0], outer[outer.length - 1],
                style.fillRise(), style.fillLifetime(), 1);
        emitLine(emitter, highlight, inner[0], inner[inner.length - 1],
                style.fillRise(), style.fillLifetime(), 1);
    }

    private static float[][] mirrorRibbonVertices(float[][] source, int side, float xScale) {
        float[][] result = new float[source.length][3];
        for (int index = 0; index < source.length; index++) {
            result[index][0] = source[index][0] * side * xScale;
            result[index][1] = source[index][1];
            result[index][2] = source[index][2];
        }
        return result;
    }

    private static void bendRibbonTail(float[][] vertices, int side, float sway, float lift) {
        for (float[] vertex : vertices) {
            float weight = Mth.clamp((vertex[1] - 0.24F) / 0.64F, 0.0F, 1.0F);
            vertex[0] += side * sway * weight;
            vertex[1] += lift * weight;
            vertex[2] += sway * 0.35F * weight;
        }
    }

    private static float[][] offsetRibbonDepth(float[][] source, float offset) {
        float[][] result = new float[source.length][3];
        for (int index = 0; index < source.length; index++) {
            result[index][0] = source[index][0];
            result[index][1] = source[index][1];
            result[index][2] = source[index][2] + offset;
        }
        return result;
    }

    private static float[][] midpointRibbonVertices(float[][] first, float[][] second) {
        int count = Math.min(first.length, second.length);
        float[][] result = new float[count][3];
        for (int index = 0; index < count; index++) {
            result[index][0] = (first[index][0] + second[index][0]) * 0.5F;
            result[index][1] = (first[index][1] + second[index][1]) * 0.5F;
            result[index][2] = (first[index][2] + second[index][2]) * 0.5F;
        }
        return result;
    }

    private static void emitQuadraticCurve(WingEmitter emitter, ParticleOptions particle,
                                           float[] from, float[] control, float[] to,
                                           double rise, int lifetime, int samples) {
        int count = Math.max(2, samples);
        for (int i = 0; i <= count; i++) {
            float t = (float) i / count;
            float inverse = 1.0F - t;
            emitter.emit(particle,
                    inverse * inverse * from[0] + 2.0F * inverse * t * control[0] + t * t * to[0],
                    inverse * inverse * from[1] + 2.0F * inverse * t * control[1] + t * t * to[1],
                    inverse * inverse * from[2] + 2.0F * inverse * t * control[2] + t * t * to[2],
                    rise, lifetime);
        }
    }

    private static void emitCircle(WingEmitter emitter, ParticleOptions particle,
                                   float centerX, float centerY, float z, float radius,
                                   int points, float phase, double rise, int lifetime) {
        int count = Math.max(6, points);
        for (int i = 0; i < count; i++) {
            float angle = phase + (float) (TAU * i / count);
            emitter.emit(particle,
                    centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius,
                    z, rise, lifetime);
        }
    }

    private static void emitDiamond(WingEmitter emitter, ParticleOptions particle,
                                    float centerX, float centerY, float z, float radius,
                                    double rise, int lifetime) {
        emitter.emit(particle, centerX, centerY - radius, z, rise, lifetime);
        emitter.emit(particle, centerX + radius, centerY, z, rise, lifetime);
        emitter.emit(particle, centerX, centerY + radius, z, rise, lifetime);
        emitter.emit(particle, centerX - radius, centerY, z, rise, lifetime);
    }

    private static void emitPolyline(WingEmitter emitter, ParticleOptions particle, float[][] points,
                                     double rise, int lifetime, int samples) {
        for (int i = 0; i < points.length - 1; i++) {
            emitLine(emitter, particle, points[i], points[i + 1], rise, lifetime, samples);
        }
    }

    private static void emitLine(WingEmitter emitter, ParticleOptions particle, float[] from, float[] to,
                                 double rise, int lifetime, int samples) {
        int count = Math.max(1, samples);
        for (int i = 0; i <= count; i++) {
            float t = (float) i / count;
            emitter.emit(particle, lerp(from[0], to[0], t), lerp(from[1], to[1], t),
                    lerp(from[2], to[2], t), rise, lifetime);
        }
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static DustParticleOptions dust(int color, float scale) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        return new DustParticleOptions(new Vector3f(red, green, blue), scale);
    }

    private enum WingShape {
        FEATHER,
        ELECTRIC,
        BUTTERFLY,
        DRAGON,
        PEACOCK,
        CATHEDRAL,
        IRIS,
        GRAND_RIBBON
    }

    private record WingStyle(WingShape shape, ParticleOptions edgeParticle, ParticleOptions fillParticle,
                             int fillLayers, float width, float height, float depth,
                             double edgeRise, double fillRise, int edgeLifetime, int fillLifetime) {
        private static WingStyle fromId(String cosmeticId) {
            String id = cosmeticId == null ? "" : cosmeticId.toLowerCase(Locale.ROOT);
            return switch (id) {
                case "angel_wings" ->
                        style(WingShape.FEATHER, dust(0xF7FFFF, 0.90F), dust(0xCDEFFF, 0.78F),
                                3, 0.76F, 0.50F, 0.20F, 0.0D, 0.0D, 14, 13);
                case "shadow_wings" ->
                        style(WingShape.FEATHER, dust(0x302D3E, 0.84F), dust(0x74509A, 0.74F),
                                3, 0.76F, 0.52F, 0.20F, 0.0D, 0.0D, 15, 14);
                case "fairy_wings" ->
                        style(WingShape.BUTTERFLY, dust(0x74E6E2, 0.68F), dust(0xFF9EDB, 0.52F),
                                2, 0.62F, 0.42F, 0.20F, 0.0D, 0.0D, 8, 6);
                case "azure_dragon_wings" ->
                        style(WingShape.ELECTRIC, dust(0xD8FBFF, 0.82F), ParticleTypes.ELECTRIC_SPARK,
                                2, 0.80F, 0.52F, 0.23F, 0.0D, 0.0D, 13, 5);
                case "flame_wings" ->
                        style(WingShape.FEATHER, ParticleTypes.FLAME, ParticleTypes.SMALL_FLAME,
                                2, 0.78F, 0.54F, 0.24F, 0.002D, 0.001D, 9, 8);
                case "soulfire_wings" ->
                        style(WingShape.FEATHER, ParticleTypes.SOUL_FIRE_FLAME, dust(0x7ADCEB, 0.84F),
                                2, 0.76F, 0.52F, 0.22F, 0.002D, 0.0D, 9, 13);
                case "end_wings" ->
                        style(WingShape.DRAGON, dust(0xA56BE0, 0.86F), dust(0x572778, 0.76F),
                                3, 0.88F, 0.55F, 0.28F, 0.0D, 0.0D, 15, 14);
                case "peacock_fan_wings" ->
                        style(WingShape.PEACOCK, dust(0x32D6B0, 0.54F), dust(0x3558D8, 0.62F),
                                1, 1.00F, 0.72F, 0.22F, 0.0D, 0.0D, 8, 7);
                case "cathedral_wings" ->
                        style(WingShape.CATHEDRAL, dust(0xFFF8D7, 0.52F), dust(0xB17CFF, 0.50F),
                                1, 1.02F, 0.72F, 0.22F, 0.0D, 0.0D, 7, 6);
                case "mechanical_iris_wings" ->
                        style(WingShape.IRIS, dust(0xC58C43, 0.56F), dust(0xF2D477, 0.54F),
                                1, 0.84F, 0.54F, 0.22F, 0.0D, 0.0D, 9, 7);
                case "grand_ribbon_wings" ->
                        style(WingShape.GRAND_RIBBON, dust(0x72CDEB, 0.40F), dust(0xE8FAFF, 0.32F),
                                1, 0.80F, 0.70F, 0.24F, 0.0D, 0.0D, 4, 3);
                default -> null;
            };
        }

        private static WingStyle style(WingShape shape, ParticleOptions edgeParticle, ParticleOptions fillParticle,
                                       int fillLayers, float width, float height, float depth,
                                       double edgeRise, double fillRise, int edgeLifetime, int fillLifetime) {
            return new WingStyle(shape, edgeParticle, fillParticle, fillLayers,
                    width, height, depth, edgeRise, fillRise, edgeLifetime, fillLifetime);
        }
    }

    private record WingEmitter(ParticleEngine particleEngine, Matrix4f bodyTransform,
                               Vec3 cameraPosition, WingStyle style, float depthOffset) {
        private WingEmitter withDepthOffset(float additionalOffset) {
            return new WingEmitter(particleEngine, bodyTransform, cameraPosition, style,
                    depthOffset + additionalOffset);
        }

        private boolean isWorldRender(AbstractClientPlayer player, float partialTick) {
            Vec3 bodyOrigin = worldPosition(0.0F, 0.0F, 0.0F);
            Vec3 interpolatedPlayerPosition = new Vec3(
                    Mth.lerp(partialTick, player.xOld, player.getX()),
                    Mth.lerp(partialTick, player.yOld, player.getY()),
                    Mth.lerp(partialTick, player.zOld, player.getZ())
            );
            return bodyOrigin.distanceToSqr(interpolatedPlayerPosition) <= WORLD_RENDER_SANITY_DISTANCE_SQR;
        }

        private void emit(ParticleOptions particle, float x, float y, float z, double rise, int lifetime) {
            Vec3 world = worldPosition(x, y, z);
            Particle spawned = particleEngine.createParticle(
                    particle, world.x(), world.y(), world.z(), 0.0D, rise, 0.0D
            );
            if (spawned != null) {
                spawned.setParticleSpeed(0.0D, rise, 0.0D);
                spawned.setLifetime(lifetime);
            }
        }

        private Vec3 worldPosition(float x, float y, float z) {
            Vector3f transformed = bodyTransform.transformPosition(x, y, z + depthOffset, new Vector3f());
            return cameraPosition.add(transformed.x(), transformed.y(), transformed.z());
        }
    }
}
