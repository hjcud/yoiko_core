package com.yoiko.core.client.relic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.mojang.math.Axis;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.config.YoikoClientConfig;
import com.yoiko.core.network.RelicGachaStartPayload;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.relic.RelicRarity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RelicGachaClientManager {
    private static final int BASE_BRUSH_TICKS = 22;
    private static final int EXTRA_RARITY_STAGE_TICKS = 16;
    private static final int RISE_TICKS = 24;
    private static final int HOVER_TICKS = 12;
    private static final int SEAL_ROTATION_PERIOD = 40;
    private static final double SEALED_RELIC_HEIGHT = 0.56D;
    private static final double RELIC_TARGET_HEIGHT = 0.86D;
    private static final float SEALED_DISPLAY_SCALE = 0.62F;
    private static final float RELIC_DISPLAY_SCALE = 0.50F;
    private static final Vector3f SEAL_COLOR = new Vector3f(0.38F, 0.70F, 0.78F);
    private static final Map<UUID, ClientSession> SESSIONS = new HashMap<>();
    private static int nextFakeEntityId = -20_000;

    public static void start(RelicGachaStartPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        SESSIONS.put(payload.sessionId(), new ClientSession(minecraft.level, payload));
    }

    public static void end(UUID sessionId) {
        SESSIONS.remove(sessionId);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        Iterator<ClientSession> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            ClientSession session = iterator.next();
            if (session.level != minecraft.level || session.tick()) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || SESSIONS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (ClientSession session : SESSIONS.values()) {
            if (session.level == minecraft.level) {
                session.render(poseStack, buffers, camera);
            }
        }
        buffers.endBatch();
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static final class ClientSession {
        private final ClientLevel level;
        private final RelicGachaStartPayload payload;
        private Display.ItemDisplay brush;
        private Display.ItemDisplay relic;
        private ItemStack relicStack = ItemStack.EMPTY;
        private int age;
        private boolean renderFailureReported;

        private ClientSession(ClientLevel level, RelicGachaStartPayload payload) {
            this.level = level;
            this.payload = payload;
            this.brush = createDisplay(level, YoikoItems.RELIC_GACHA_TICKET.toStack(),
                    payload.x(), payload.groundY() + SEALED_RELIC_HEIGHT, payload.z(),
                    payload.facingYaw(), 0.0F, SEALED_DISPLAY_SCALE);
        }

        private boolean tick() {
            age++;
            int revealTick = revealTick(payload.rarity());
            if (age <= revealTick) {
                animateBrush();
            }
            RelicRarity cue = cueRarity(age, revealTick);
            if (cue != null) {
                emitAnticipationBurst(cue, payload.x(), payload.groundY() + 0.22D, payload.z());
            } else if (age > BASE_BRUSH_TICKS && age < revealTick && age % trailInterval(4) == 0) {
                emitParticles(new DustParticleOptions(rarityColor(anticipationRarity(age)), 0.8F),
                        payload.x(), payload.groundY() + 0.22D, payload.z(), 3, 0.25D, 0.12D, 0.25D, 0.018D);
            }
            if (age == revealTick) {
                beginReveal();
            }
            if (age >= revealTick && relic != null) {
                animateRelic(revealTick);
            }
            if (age >= revealTick + RISE_TICKS + HOVER_TICKS) {
                emitParticles(ParticleTypes.POOF, payload.x(), payload.groundY() + RELIC_TARGET_HEIGHT,
                        payload.z(), 12, 0.18D, 0.18D, 0.18D, 0.02D);
                return true;
            }
            return false;
        }

        private void animateBrush() {
            if (brush == null) {
                return;
            }
            double phase = Math.PI * 2.0D * age / SEAL_ROTATION_PERIOD;
            double y = payload.groundY() + SEALED_RELIC_HEIGHT + Math.sin(phase) * 0.045D;
            brush.setYRot(payload.facingYaw() + age * 4.5F);
            brush.setXRot((float) (Math.sin(phase * 0.5D) * 7.0D));
            brush.setPos(payload.x(), y, payload.z());
            if (age % trailInterval(6) == 0) {
                double ring = 0.34D - Math.min(0.18D, age * 0.0025D);
                for (int index = 0; index < 8; index++) {
                    double angle = phase + Math.PI * 2.0D * index / 8.0D;
                    level.addParticle(new DustParticleOptions(SEAL_COLOR, 0.72F),
                            payload.x() + Math.cos(angle) * ring, y,
                            payload.z() + Math.sin(angle) * ring,
                            -Math.cos(angle) * 0.012D, 0.006D, -Math.sin(angle) * 0.012D);
                }
            }
            if (age % trailInterval(16) == 4) {
                emitParticles(ParticleTypes.ENCHANT, payload.x(), y, payload.z(),
                        5, 0.28D, 0.18D, 0.28D, 0.01D);
            }
        }

        private void beginReveal() {
            brush = null;
            relicStack = relicDisplayStack(payload.rarity());
            relic = createDisplay(level, relicStack,
                    payload.x(), payload.groundY() + SEALED_RELIC_HEIGHT, payload.z(),
                    payload.facingYaw(), 0.0F, RELIC_DISPLAY_SCALE);
            emitRevealParticles();
        }

        private void animateRelic(int revealTick) {
            int elapsed = age - revealTick;
            double progress = Math.min(1.0D, Math.max(0.0D, elapsed / (double) RISE_TICKS));
            double eased = progress * progress * (3.0D - 2.0D * progress);
            double targetY = payload.groundY() + RELIC_TARGET_HEIGHT;
            double y = payload.groundY() + SEALED_RELIC_HEIGHT
                    + (targetY - (payload.groundY() + SEALED_RELIC_HEIGHT)) * eased;
            if (progress >= 1.0D) {
                y += Math.sin((elapsed - RISE_TICKS) * 0.18D) * 0.04D;
            }
            relic.setYRot(payload.facingYaw() + elapsed * 3.5F);
            relic.setXRot(0.0F);
            relic.setPos(payload.x(), y, payload.z());
            if (elapsed > 0 && elapsed <= 12 && elapsed % trailInterval(3) == 0) {
                emitExcavationTrail();
            }
            if (elapsed > 0 && elapsed % trailInterval(4) == 0) {
                emitParticles(revealParticle(payload.rarity()), payload.x(), y, payload.z(),
                        2, 0.13D, 0.10D, 0.13D, 0.012D);
            }
        }

        private void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera) {
            renderBrush(poseStack,buffers,camera);
            renderRelic(poseStack, buffers, camera);
        }

        private void renderBrush(PoseStack poseStack,MultiBufferSource.BufferSource buffers,Vec3 camera){
            if(brush==null)return;Vec3 relative=brush.position().subtract(camera);poseStack.pushPose();poseStack.translate(relative.x,relative.y,relative.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-brush.getYRot()));poseStack.mulPose(Axis.XP.rotationDegrees(brush.getXRot()));poseStack.scale(SEALED_DISPLAY_SCALE,SEALED_DISPLAY_SCALE,SEALED_DISPLAY_SCALE);
            Minecraft.getInstance().getItemRenderer().renderStatic(YoikoItems.RELIC_GACHA_TICKET.toStack(),ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,poseStack,buffers,level,payload.sessionId().hashCode());poseStack.popPose();
        }

        private void renderRelic(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera) {
            if (relic == null || relicStack.isEmpty()) {
                return;
            }
            Vec3 relative = relic.position().subtract(camera);
            poseStack.pushPose();
            poseStack.translate(relative.x, relative.y, relative.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-relic.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(relic.getXRot()));
            poseStack.scale(RELIC_DISPLAY_SCALE, RELIC_DISPLAY_SCALE, RELIC_DISPLAY_SCALE);
            try {
                Minecraft.getInstance().getItemRenderer().renderStatic(
                        relicStack,
                        ItemDisplayContext.FIXED,
                        LightTexture.FULL_BRIGHT,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        poseStack,
                        buffers,
                        level,
                        payload.sessionId().hashCode()
                );
            } catch (Throwable throwable) {
                if (!renderFailureReported) {
                    renderFailureReported = true;
                    YoikoServerCore.LOGGER.warn("Failed to render client-side relic gacha item model", throwable);
                }
            }
            poseStack.popPose();
        }

        private void emitAnticipationBurst(RelicRarity rarity, double x, double y, double z) {
            emitParticles(new DustParticleOptions(rarityColor(rarity), 1.0F), x, y, z,
                    28, 0.42D, 0.22D, 0.42D, 0.055D);
        }

        private void emitRevealParticles() {
            double y = payload.groundY() + SEALED_RELIC_HEIGHT;
            emitParticles(ParticleTypes.FLASH, payload.x(), y, payload.z(), 1, 0, 0, 0, 0);
            emitParticles(new DustParticleOptions(rarityColor(payload.rarity()), 1.2F),
                    payload.x(), y, payload.z(), 34, 0.46D, 0.38D, 0.46D, 0.07D);
            emitParticles(revealParticle(payload.rarity()), payload.x(), y, payload.z(),
                    24, 0.42D, 0.32D, 0.42D, 0.065D);
            if (payload.rarity() == RelicRarity.LEGENDARY || payload.rarity() == RelicRarity.MYSTIC
                    || payload.rarity() == RelicRarity.RADIANT) {
                emitParticles(ParticleTypes.FIREWORK, payload.x(), y + 0.12D, payload.z(),
                        24, 0.42D, 0.34D, 0.42D, 0.09D);
            }
        }

        private void emitExcavationTrail() {
            emitParticles(ParticleTypes.END_ROD, payload.x(), payload.groundY() + SEALED_RELIC_HEIGHT,
                    payload.z(), 5, 0.18D, 0.18D, 0.18D, 0.02D);
        }

        private void emitParticles(ParticleOptions particle, double x, double y, double z, int count,
                                   double spreadX, double spreadY, double spreadZ, double speed) {
            int density = com.yoiko.core.client.cosmetic.YoikoParticleSettings.density();
            if (density <= 0) {
                return;
            }
            int scaled = Math.max(1, (int) Math.ceil(count * Math.min(3, density) / 3.0D));
            for (int index = 0; index < scaled; index++) {
                double ox = (level.random.nextDouble() * 2.0D - 1.0D) * spreadX;
                double oy = (level.random.nextDouble() * 2.0D - 1.0D) * spreadY;
                double oz = (level.random.nextDouble() * 2.0D - 1.0D) * spreadZ;
                level.addParticle(particle, x + ox, y + oy, z + oz,
                        level.random.nextGaussian() * speed,
                        level.random.nextGaussian() * speed,
                        level.random.nextGaussian() * speed);
            }
        }

        private int trailInterval(int base) {
            return base;
        }

    }

    private static Display.ItemDisplay createDisplay(ClientLevel level, ItemStack stack,
                                                     double x, double y, double z,
                                                     float yRot, float xRot, float scale) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.put("item", stack.save(level.registryAccess()));
        tag.putString("item_display", ItemDisplayContext.FIXED.getSerializedName());
        tag.putFloat("view_range", 2.0F);
        Transformation transformation = new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf());
        Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, transformation)
                .ifSuccess(encoded -> tag.put("transformation", encoded));
        display.load(tag);
        display.setId(nextFakeEntityId--);
        display.setNoGravity(true);
        display.setSilent(true);
        display.setYRot(yRot);
        display.setXRot(xRot);
        display.setPos(x, y, z);
        return display;
    }

    private static ItemStack relicDisplayStack(RelicRarity rarity) {
        return switch (rarity) {
            case RADIANT -> YoikoItems.RELIC_DISPLAY_RADIANT.toStack();
            case MYSTIC -> YoikoItems.RELIC_DISPLAY_MYSTIC.toStack();
            case LEGENDARY -> YoikoItems.RELIC_DISPLAY_LEGENDARY.toStack();
            case EPIC -> YoikoItems.RELIC_DISPLAY_EPIC.toStack();
            case RARE -> YoikoItems.RELIC_DISPLAY_RARE.toStack();
            case UNCOMMON -> YoikoItems.RELIC_DISPLAY_UNCOMMON.toStack();
            default -> YoikoItems.RELIC_DISPLAY_COMMON.toStack();
        };
    }

    private static int revealTick(RelicRarity rarity) {
        return switch (rarity) {
            case EPIC -> BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS;
            case LEGENDARY -> BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 2;
            case MYSTIC -> BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 3;
            case RADIANT -> BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 4;
            default -> BASE_BRUSH_TICKS;
        };
    }

    private static RelicRarity cueRarity(int tick, int revealTick) {
        if (tick == BASE_BRUSH_TICKS && revealTick > tick) return RelicRarity.EPIC;
        if (tick == BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS && revealTick > tick) return RelicRarity.LEGENDARY;
        if (tick == BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 2 && revealTick > tick) return RelicRarity.MYSTIC;
        if (tick == BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 3 && revealTick > tick) return RelicRarity.RADIANT;
        return null;
    }

    private static RelicRarity anticipationRarity(int tick) {
        if (tick > BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 3) return RelicRarity.RADIANT;
        if (tick > BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS * 2) return RelicRarity.MYSTIC;
        if (tick > BASE_BRUSH_TICKS + EXTRA_RARITY_STAGE_TICKS) return RelicRarity.LEGENDARY;
        return RelicRarity.EPIC;
    }

    private static Vector3f rarityColor(RelicRarity rarity) {
        return switch (rarity) {
            case RADIANT -> new Vector3f(0.48F, 0.95F, 1.0F);
            case MYSTIC -> new Vector3f(1.0F, 0.16F, 0.28F);
            case LEGENDARY -> new Vector3f(1.0F, 0.72F, 0.12F);
            case EPIC -> new Vector3f(0.84F, 0.30F, 1.0F);
            case RARE -> new Vector3f(0.30F, 0.58F, 1.0F);
            case UNCOMMON -> new Vector3f(0.30F, 0.95F, 0.42F);
            default -> new Vector3f(0.72F, 0.72F, 0.72F);
        };
    }

    private static ParticleOptions revealParticle(RelicRarity rarity) {
        return switch (rarity) {
            case RADIANT -> ParticleTypes.FIREWORK;
            case MYSTIC -> ParticleTypes.REVERSE_PORTAL;
            case LEGENDARY -> ParticleTypes.TOTEM_OF_UNDYING;
            case EPIC -> ParticleTypes.WITCH;
            case RARE -> ParticleTypes.END_ROD;
            case UNCOMMON -> ParticleTypes.HAPPY_VILLAGER;
            default -> ParticleTypes.ENCHANT;
        };
    }
}
