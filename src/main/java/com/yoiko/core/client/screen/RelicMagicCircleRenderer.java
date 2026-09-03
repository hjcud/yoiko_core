package com.yoiko.core.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yoiko.core.YoikoServerCore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Native-pixel, multi-anchor renderer for the nine upgrade magic-circle layers. */
final class RelicMagicCircleRenderer {
    private static final int SIZE = 127;
    private static final float EDGE_GLOW_WIDTH = 2.5F;
    private static final float CHARGE_RED = 0.36F;
    private static final float CHARGE_GREEN = 0.84F;
    private static final float CHARGE_BLUE = 1.0F;
    // Muted, warm brick red: readable as a failure without overpowering the parchment UI.
    private static final float FAILURE_RED = 0.82F;
    private static final float FAILURE_GREEN = 0.42F;
    private static final float FAILURE_BLUE = 0.34F;
    private static final int RESULT_COLOR_TRANSITION_TICKS = 4;
    private static final float RESULT_COLOR_LEAD_TICKS = 1.0F;
    private static final Layer[] LAYERS = {
            new Layer("magic_circle_1.png", 0.08F, 0.76F, 58.0F,
                    anchor(63, 17, 0.00F, 1.18F), anchor(25, 87, 0.15F, 0.90F),
                    anchor(101, 87, 0.28F, 0.72F)),
            new Layer("magic_circle_2.png", 0.19F, 0.64F, 56.0F,
                    anchor(63, 17, 0.18F, 0.82F), anchor(25, 87, 0.00F, 1.25F),
                    anchor(101, 87, 0.10F, 0.95F)),
            new Layer("magic_circle_3.png", 0.11F, 0.69F, 55.0F,
                    anchor(63, 17, 0.12F, 1.00F), anchor(25, 87, 0.22F, 0.74F),
                    anchor(101, 87, 0.00F, 1.22F)),
            new Layer("magic_circle_4.png", 0.03F, 0.56F, 52.0F,
                    anchor(63, 17, 0.20F, 0.72F), anchor(25, 87, 0.06F, 1.02F),
                    anchor(101, 87, 0.00F, 1.28F)),
            new Layer("magic_circle_5.png", 0.27F, 0.65F, 37.0F,
                    anchor(45, 18, 0.00F, 0.72F), anchor(81, 18, 0.05F, 0.94F),
                    anchor(16, 77, 0.11F, 1.18F), anchor(35, 103, 0.18F, 1.38F),
                    anchor(111, 77, 0.25F, 1.08F), anchor(91, 103, 0.31F, 0.82F)),
            new Layer("magic_circle_6.png", 0.15F, 0.55F, 26.0F,
                    anchor(63, 13, 0.00F, 0.76F), anchor(106, 38, 0.06F, 1.02F),
                    anchor(106, 88, 0.13F, 1.31F), anchor(63, 113, 0.20F, 0.91F),
                    anchor(20, 88, 0.27F, 1.18F), anchor(20, 38, 0.34F, 0.70F)),
            new Layer("magic_circle_7.png", 0.06F, 0.62F, 32.0F,
                    anchor(63, 13, 0.24F, 1.22F), anchor(106, 39, 0.16F, 0.73F),
                    anchor(106, 88, 0.08F, 1.04F), anchor(63, 113, 0.00F, 1.36F),
                    anchor(20, 88, 0.12F, 0.84F), anchor(20, 39, 0.28F, 1.10F)),
            new Layer("magic_circle_8.png", 0.00F, 0.42F, 19.0F,
                    anchor(45, 45, 0.00F, 0.75F), anchor(81, 45, 0.09F, 1.00F),
                    anchor(81, 81, 0.18F, 1.30F), anchor(45, 81, 0.27F, 0.90F)),
            new Layer("magic_circle_9.png", 0.13F, 0.46F, 18.0F,
                    anchor(63, 39, 0.21F, 0.95F), anchor(87, 63, 0.14F, 0.72F),
                    anchor(63, 87, 0.07F, 1.18F), anchor(39, 63, 0.00F, 1.36F))
    };

    private RelicMagicCircleRenderer() {
    }

    static void render(
            GuiGraphics graphics,
            int textureX,
            int textureY,
            int chargeTicks,
            int resultTicks,
            boolean awaitingResult,
            boolean greatSuccess,
            boolean success,
            float partialTick,
            int totalChargeTicks,
            int totalResultTicks
    ) {
        if (chargeTicks > 0) {
            float progress = (totalChargeTicks - chargeTicks + partialTick) / totalChargeTicks;
            renderChargingLayers(graphics, textureX, textureY, progress);
        } else {
            float alpha = awaitingResult ? 1.0F : Math.min(1.0F, resultTicks / 12.0F);
            float red = CHARGE_RED;
            float green = CHARGE_GREEN;
            float blue = CHARGE_BLUE;
            if (!awaitingResult) {
                float transition = clamp01((totalResultTicks - resultTicks
                        + RESULT_COLOR_LEAD_TICKS + partialTick) / RESULT_COLOR_TRANSITION_TICKS);
                transition = easeOutCubic(transition);
                float targetRed = greatSuccess ? 1.0F : success ? 0.3F : FAILURE_RED;
                float targetGreen = greatSuccess ? 0.78F : success ? 0.95F : FAILURE_GREEN;
                float targetBlue = greatSuccess ? 0.24F : success ? 1.0F : FAILURE_BLUE;
                red = lerp(red, targetRed, transition);
                green = lerp(green, targetGreen, transition);
                blue = lerp(blue, targetBlue, transition);
            }
            RenderSystem.setShaderColor(red, green, blue, alpha);
            blitComplete(graphics, textureX, textureY);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderChargingLayers(
            GuiGraphics graphics, int textureX, int textureY, float globalProgress) {
        float clampedGlobal = clamp01(globalProgress);
        for (Layer layer : LAYERS) {
            float layerProgress = clamp01(
                    (clampedGlobal - layer.startProgress()) / layer.revealDuration());
            if (layerProgress <= 0.0F) {
                continue;
            }
            float eased = layerProgress * layerProgress * (3.0F - 2.0F * layerProgress);
            RenderSystem.setShaderColor(CHARGE_RED, CHARGE_GREEN, CHARGE_BLUE,
                    0.68F + 0.20F * clampedGlobal);
            blitRadialRange(graphics, layer.texture(), textureX, textureY,
                    layer.anchors(), layer.revealRadius(), 0.0F, eased);

            if (layerProgress < 1.0F) {
                float glowWidth = Math.min(EDGE_GLOW_WIDTH, layer.revealRadius() * 0.10F);
                float innerProgress = Math.max(0.0F, eased - glowWidth / layer.revealRadius());
                RenderSystem.setShaderColor(0.68F, 0.96F, 1.0F, 0.55F);
                blitRadialRange(graphics, layer.texture(), textureX, textureY,
                        layer.anchors(), layer.revealRadius(), innerProgress, eased);
            }
        }
    }

    private static void blitComplete(GuiGraphics graphics, int textureX, int textureY) {
        for (Layer layer : LAYERS) {
            graphics.blit(layer.texture(), textureX, textureY, SIZE, SIZE,
                    0.0F, 0.0F, SIZE, SIZE, SIZE, SIZE);
        }
    }

    private static void blitRadialRange(
            GuiGraphics graphics, ResourceLocation texture, int textureX, int textureY,
            Anchor[] anchors, float revealRadius, float innerProgress, float outerProgress) {
        int[] outerStarts = new int[anchors.length];
        int[] outerEnds = new int[anchors.length];
        int[] innerStarts = new int[anchors.length];
        int[] innerEnds = new int[anchors.length];
        for (int sourceY = 0; sourceY < SIZE; sourceY++) {
            int outerCount = buildIntervals(
                    anchors, sourceY, revealRadius, outerProgress, outerStarts, outerEnds);
            if (outerCount == 0) {
                continue;
            }
            int innerCount = innerProgress <= 0.0F ? 0 : buildIntervals(
                    anchors, sourceY, revealRadius, innerProgress, innerStarts, innerEnds);
            for (int outerIndex = 0; outerIndex < outerCount; outerIndex++) {
                int cursor = outerStarts[outerIndex];
                int outerEnd = outerEnds[outerIndex];
                for (int innerIndex = 0; innerIndex < innerCount; innerIndex++) {
                    if (innerEnds[innerIndex] < cursor) {
                        continue;
                    }
                    if (innerStarts[innerIndex] > outerEnd) {
                        break;
                    }
                    if (innerStarts[innerIndex] > cursor) {
                        blitRow(graphics, texture, textureX, textureY, sourceY, cursor,
                                Math.min(outerEnd, innerStarts[innerIndex] - 1));
                    }
                    cursor = Math.max(cursor, innerEnds[innerIndex] + 1);
                    if (cursor > outerEnd) {
                        break;
                    }
                }
                if (cursor <= outerEnd) {
                    blitRow(graphics, texture, textureX, textureY, sourceY, cursor, outerEnd);
                }
            }
        }
    }

    private static int buildIntervals(
            Anchor[] anchors, int sourceY, float revealRadius, float progress,
            int[] starts, int[] ends) {
        int intervalCount = 0;
        float clampedProgress = clamp01(progress);
        for (Anchor anchor : anchors) {
            float delayedProgress = clamp01(
                    (clampedProgress - anchor.startDelay()) / (1.0F - anchor.startDelay()));
            if (delayedProgress <= 0.0F) {
                continue;
            }
            double anchorRadius = revealRadius * Math.pow(delayedProgress, anchor.revealCurve());
            double horizontalSquared = anchorRadius * anchorRadius
                    - (sourceY - anchor.y()) * (sourceY - anchor.y());
            if (horizontalSquared < 0.0D) {
                continue;
            }
            int horizontal = (int) Math.floor(Math.sqrt(horizontalSquared));
            int start = Math.max(0, anchor.x() - horizontal);
            int end = Math.min(SIZE - 1, anchor.x() + horizontal);
            int insert = intervalCount;
            while (insert > 0 && starts[insert - 1] > start) {
                starts[insert] = starts[insert - 1];
                ends[insert] = ends[insert - 1];
                insert--;
            }
            starts[insert] = start;
            ends[insert] = end;
            intervalCount++;
        }
        int mergedCount = 0;
        for (int index = 0; index < intervalCount; index++) {
            if (mergedCount == 0 || starts[index] > ends[mergedCount - 1] + 1) {
                starts[mergedCount] = starts[index];
                ends[mergedCount] = ends[index];
                mergedCount++;
            } else {
                ends[mergedCount - 1] = Math.max(ends[mergedCount - 1], ends[index]);
            }
        }
        return mergedCount;
    }

    private static void blitRow(
            GuiGraphics graphics, ResourceLocation texture, int textureX, int textureY,
            int sourceY, int sourceStartX, int sourceEndX) {
        if (sourceEndX < sourceStartX) {
            return;
        }
        int width = sourceEndX - sourceStartX + 1;
        graphics.blit(texture, textureX + sourceStartX, textureY + sourceY, width, 1,
                sourceStartX, sourceY, width, 1, SIZE, SIZE);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float easeOutCubic(float value) {
        float remaining = 1.0F - clamp01(value);
        return 1.0F - remaining * remaining * remaining;
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static Anchor anchor(int x, int y, float startDelay, float revealCurve) {
        return new Anchor(x, y, startDelay, revealCurve);
    }

    private record Anchor(int x, int y, float startDelay, float revealCurve) {
    }

    private record Layer(
            ResourceLocation texture,
            float startProgress,
            float revealDuration,
            float revealRadius,
            Anchor[] anchors
    ) {
        private Layer(String textureName, float startProgress, float revealDuration,
                      float revealRadius, Anchor... anchors) {
            this(YoikoServerCore.id("textures/gui/relic/workbench/" + textureName),
                    startProgress, revealDuration, revealRadius, anchors);
            if (anchors.length == 0) {
                throw new IllegalArgumentException("Magic circle layer requires reveal anchors");
            }
            for (Anchor anchor : anchors) {
                if (anchor.startDelay() < 0.0F || anchor.startDelay() >= 1.0F
                        || anchor.revealCurve() <= 0.0F) {
                    throw new IllegalArgumentException("Invalid magic circle anchor timing");
                }
            }
        }
    }
}
