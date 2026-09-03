package com.yoiko.core.client.screen;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yoiko.core.gacha.PokemonNameFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

final class YoikoPokemonModelRenderer {
    private static final float COBBLEMON_PROFILE_WIDTH = 137.0F;
    private static final float COBBLEMON_PROFILE_HEIGHT = 68.0F;
    private static final Map<String, RenderEntry> CACHE = new HashMap<>();

    private YoikoPokemonModelRenderer() {
    }

    static void render(GuiGraphics graphics, String speciesId, int x, int y, int width, int height, float partialTick) {
        if (speciesId == null || speciesId.isBlank() || width <= 0 || height <= 0) {
            renderFallback(graphics, x, y, width, height);
            return;
        }

        boolean scissorEnabled = false;
        boolean posePushed = false;
        boolean fallback = false;
        PoseStack pose = graphics.pose();
        try {
            RenderEntry entry = entry(speciesId);
            if (entry == null) {
                renderFallback(graphics, x, y, width, height);
                return;
            }

            graphics.enableScissor(x, y, x + width, y + height);
            scissorEnabled = true;
            pose.pushPose();
            posePushed = true;
            boolean thumbnail = width <= 40 && height <= 40;
            float fit = Math.min(width / COBBLEMON_PROFILE_WIDTH, height / COBBLEMON_PROFILE_HEIGHT);
            float scale = Math.max(thumbnail ? 0.50F : 0.38F, fit * (thumbnail ? 2.55F : 2.0F));
            pose.translate(x + width / 2.0F, y + height * (thumbnail ? 0.02F : -0.18F), 1000.0F);
            pose.scale(scale, scale, scale);
            Quaternionf rotation = new Quaternionf().rotateXYZ((float) Math.toRadians(13.0F), (float) Math.toRadians(30.0F), 0.0F);
            PokemonGuiUtilsKt.drawProfilePokemon(
                    entry.renderable(),
                    pose,
                    rotation,
                    PoseType.PROFILE,
                    entry.state(),
                    partialTick,
                    20.0F,
                    true,
                    false,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    0.0F,
                    0.0F
            );
        } catch (Throwable ignored) {
            CACHE.remove(PokemonNameFormatter.canonicalId(speciesId));
            fallback = true;
        } finally {
            if (posePushed) {
                pose.popPose();
            }
            if (scissorEnabled) {
                graphics.disableScissor();
            }
        }
        if (fallback) {
            renderFallback(graphics, x, y, width, height);
        }
    }

    private static RenderEntry entry(String speciesId) {
        Species species = PokemonNameFormatter.findSpecies(speciesId);
        if (species == null || species.getResourceIdentifier() == null) {
            return null;
        }
        String key = species.getResourceIdentifier().toString();
        return CACHE.computeIfAbsent(key, ignored -> {
            Set<String> aspects = new HashSet<>(species.getStandardForm().getAspects());
            FloatingState state = new FloatingState();
            state.setCurrentAspects(aspects);
            return new RenderEntry(new RenderablePokemon(species, aspects, ItemStack.EMPTY), state);
        });
    }

    private static void renderFallback(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x66154D57);
        graphics.drawCenteredString(
                net.minecraft.client.Minecraft.getInstance().font,
                Component.literal("?"),
                x + width / 2,
                y + height / 2 - 4,
                0x99E8FFFF
        );
    }

    private record RenderEntry(RenderablePokemon renderable, FloatingState state) {
    }
}
