package com.yoiko.core.client.gacha;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity.CaptureState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.gacha.GachaRarity;
import com.yoiko.core.network.GachaSelectionChoosePayload;
import com.yoiko.core.network.GachaSelectionRevealPayload;
import com.yoiko.core.network.GachaSelectionStartPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GachaSelectionClientManager {
    private static final int LANDING_SETTLE_TICKS = 10;
    private static final int NON_SELECTED_FADE_TICKS = 12;
    private static final int OPEN_TICK = 10;
    private static final int OPEN_BALL_TILT_TICKS = 8;
    private static final int POKEMON_APPEAR_TICK = OPEN_TICK + OPEN_BALL_TILT_TICKS + 2;
    private static final float POKEMON_REVEAL_SCALE = 1.0F / 3.0F;
    private static final float OPEN_BALL_TILT_DEGREES = -25.0F;
    private static final float FALL_ROLL_DEGREES = 720.0F;
    private static final double BALL_SHAKE_PIVOT_Y = -0.22D;
    private static final float BALL_SHAKE_DEGREES = 11.0F;
    private static final double REVEAL_PARTICLE_BASE_Y = 0.18D;
    private static final double REVEAL_PARTICLE_STEP_Y = 0.12D;
    private static final Map<UUID, ClientSession> SESSIONS = new HashMap<>();
    private static UUID hoveredSession;
    private static int hoveredIndex = -1;
    private static int nextFakeEntityId = -10_000;

    public GachaSelectionClientManager() {
    }

    public static void start(GachaSelectionStartPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ClientSession previous = SESSIONS.remove(payload.sessionId());
        if (previous != null) {
            previous.close();
        }
        SESSIONS.put(payload.sessionId(), new ClientSession(minecraft.level, payload));
    }

    public static void reveal(GachaSelectionRevealPayload payload) {
        ClientSession session = SESSIONS.get(payload.sessionId());
        if (session == null || session.revealed) {
            return;
        }
        session.beginReveal(payload);
        if (payload.sessionId().equals(hoveredSession)) {
            clearHover();
        }
    }

    public static void end(UUID sessionId) {
        ClientSession removed = SESSIONS.remove(sessionId);
        if (removed != null) {
            removed.close();
        }
        if (sessionId.equals(hoveredSession)) {
            clearHover();
        }
    }

    public static void clear() {
        for (ClientSession session : SESSIONS.values()) {
            session.close();
        }
        SESSIONS.clear();
        clearHover();
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }

        Iterator<ClientSession> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            ClientSession session = iterator.next();
            if (session.level != minecraft.level || session.tick()) {
                session.close();
                iterator.remove();
            }
        }
        updateHover(minecraft);
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
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();

        for (ClientSession session : SESSIONS.values()) {
            if (session.level != minecraft.level) {
                continue;
            }
            session.render(minecraft, dispatcher, poseStack, buffers, camera, partialTick);
        }
        buffers.endBatch();
    }

    @SubscribeEvent
    public void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND
                || hoveredSession == null || hoveredIndex < 0) {
            return;
        }
        ClientSession session = SESSIONS.get(hoveredSession);
        if (session == null || session.selectionSent || session.revealed) {
            return;
        }
        session.selectionSent = true;
        PacketDistributor.sendToServer(new GachaSelectionChoosePayload(session.payload.sessionId(), hoveredIndex));
        event.setCanceled(true);
        event.setSwingHand(true);
        clearHover();
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void updateHover(Minecraft minecraft) {
        clearHover();
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle();
        double bestDistance = Double.MAX_VALUE;

        for (ClientSession session : SESSIONS.values()) {
            if (!session.isInteractive(minecraft) || !session.ready()) {
                continue;
            }
            double maxDistance = session.payload.selectionDistance();
            Vec3 end = eye.add(look.scale(maxDistance));
            BlockHitResult blockHit = minecraft.level.clip(new ClipContext(
                    eye,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    minecraft.player
            ));
            double blockDistance = blockHit.getType() == HitResult.Type.BLOCK
                    ? eye.distanceToSqr(blockHit.getLocation())
                    : maxDistance * maxDistance;

            for (int i = 0; i < session.balls.size(); i++) {
                Vec3 position = session.balls.get(i).currentPosition;
                if (eye.distanceToSqr(position) > maxDistance * maxDistance) {
                    continue;
                }
                AABB bounds = AABB.ofSize(position.add(0.0D, 0.18D, 0.0D), 0.9D, 0.9D, 0.9D);
                var hit = bounds.clip(eye, end);
                if (hit.isEmpty()) {
                    continue;
                }
                double distance = eye.distanceToSqr(hit.get());
                if (distance <= blockDistance + 1.0E-4D && distance < bestDistance) {
                    bestDistance = distance;
                    hoveredSession = session.payload.sessionId();
                    hoveredIndex = i;
                }
            }
        }
    }

    private static void clearHover() {
        hoveredSession = null;
        hoveredIndex = -1;
    }

    private static final class ClientSession {
        private final ClientLevel level;
        private final GachaSelectionStartPayload payload;
        private final List<ClientBall> balls;
        private final boolean containsShiny;
        private int age;
        private boolean selectionSent;
        private boolean revealed;
        private int selectedIndex = -1;
        private int revealAge;
        private int revealTicks;
        private PokemonEntity pokemon;
        private boolean ballOpened;
        private boolean revealSoundPlayed;
        private boolean shinyCuePlayed;

        private ClientSession(ClientLevel level, GachaSelectionStartPayload payload) {
            this.level = level;
            this.payload = payload;
            this.balls = new ArrayList<>(payload.balls().size());
            this.containsShiny = payload.balls().stream().anyMatch(GachaSelectionStartPayload.BallEntry::shiny);
            for (int i = 0; i < payload.balls().size(); i++) {
                this.balls.add(new ClientBall(level, payload.balls().get(i), i, payload.fallHeight()));
            }
            level.playLocalSound(
                    payload.balls().get(1).x(),
                    payload.balls().get(1).y() + payload.fallHeight(),
                    payload.balls().get(1).z(),
                    SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.PLAYERS,
                    0.55F,
                    1.35F,
                    false
            );
        }

        private boolean tick() {
            age++;
            for (ClientBall ball : balls) {
                ball.tick(this);
            }
            if (containsShiny && !shinyCuePlayed && ready()) {
                shinyCuePlayed = true;
                ClientBall center = balls.get(1);
                level.playLocalSound(
                        center.currentPosition.x,
                        center.currentPosition.y,
                        center.currentPosition.z,
                        CobblemonSounds.POKE_BALL_SHINY_SEND_OUT,
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F,
                        false
                );
            }
            if (revealed) {
                revealAge++;
                tickReveal();
                return revealAge > revealTicks + 20;
            }
            return age > payload.timeoutTicks() + 60;
        }

        private void tickReveal() {
            ClientBall selected = balls.get(selectedIndex);
            if (!ballOpened && revealAge >= OPEN_TICK) {
                ballOpened = true;
                try {
                    selected.entity.setCaptureState(CaptureState.HIT);
                } catch (Throwable throwable) {
                    YoikoServerCore.LOGGER.warn("Failed to start Cobblemon poke ball open animation", throwable);
                }
            }
            if (!revealSoundPlayed && revealAge >= POKEMON_APPEAR_TICK) {
                revealSoundPlayed = true;
                level.playLocalSound(
                        selected.currentPosition.x,
                        selected.currentPosition.y,
                        selected.currentPosition.z,
                        SoundEvents.FIREWORK_ROCKET_BLAST,
                        SoundSource.PLAYERS,
                        0.8F,
                        1.15F,
                        false
                );
            }
            if (pokemon != null) {
                pokemon.tickCount++;
                float previousYaw = (revealAge - 1) * 2.6F;
                float yaw = revealAge * 2.6F;
                pokemon.yRotO = previousYaw;
                pokemon.yHeadRotO = previousYaw;
                pokemon.yBodyRotO = previousYaw;
                pokemon.setYRot(yaw);
                pokemon.setYHeadRot(yaw);
                pokemon.yBodyRot = yaw;
            }
            int density = payload.particleDensity();
            if (density > 0 && revealAge >= OPEN_TICK && revealAge <= POKEMON_APPEAR_TICK + 28
                    && revealAge % Math.max(1, 3 - Math.min(2, density)) == 0) {
                Vec3 position = selected.currentPosition;
                for (int i = 0; i < density * 2; i++) {
                    double angle = (revealAge * 0.34D) + (Math.PI * 2.0D * i / Math.max(1, density * 2));
                    level.addParticle(
                            ParticleTypes.END_ROD,
                            position.x + Math.cos(angle) * 0.32D,
                            position.y + REVEAL_PARTICLE_BASE_Y + (i % 2) * REVEAL_PARTICLE_STEP_Y,
                            position.z + Math.sin(angle) * 0.32D,
                            Math.cos(angle) * 0.012D,
                            0.035D,
                            Math.sin(angle) * 0.012D
                    );
                }
            }
        }

        private void beginReveal(GachaSelectionRevealPayload reveal) {
            if (reveal.selectedIndex() < 0 || reveal.selectedIndex() >= balls.size()) {
                return;
            }
            revealed = true;
            selectionSent = true;
            selectedIndex = reveal.selectedIndex();
            revealTicks = Math.max(40, reveal.revealTicks());
            ClientBall selected = balls.get(selectedIndex);
            level.playLocalSound(
                    selected.currentPosition.x,
                    selected.currentPosition.y,
                    selected.currentPosition.z,
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    0.75F,
                    0.9F,
                    false
            );
            try {
                PokemonProperties properties = new PokemonProperties();
                properties.setSpecies(reveal.species());
                properties.setLevel(reveal.level());
                properties.setShiny(reveal.shiny());
                pokemon = properties.createEntity(level);
                synchronizeRevealShinyAppearance(pokemon, reveal.shiny());
                pokemon.setId(nextFakeEntityId--);
                pokemon.noPhysics = true;
                pokemon.setNoGravity(true);
                pokemon.setSilent(true);
                pokemon.hideNameRendering();
                pokemon.setCustomNameVisible(false);
            } catch (Throwable throwable) {
                pokemon = null;
                YoikoServerCore.LOGGER.warn("Failed to create render-only Cobblemon Pokemon for {}", reveal.species(), throwable);
            }
        }

        private void render(Minecraft minecraft, EntityRenderDispatcher dispatcher, PoseStack poseStack,
                            MultiBufferSource.BufferSource buffers, Vec3 camera, float partialTick) {
            for (int i = 0; i < balls.size(); i++) {
                ClientBall ball = balls.get(i);
                if (!ball.visible(age, payload.staggerTicks())) {
                    continue;
                }
                float scale = ballScale(i);
                if (scale <= 0.01F) {
                    continue;
                }
                boolean hovered = payload.sessionId().equals(hoveredSession) && hoveredIndex == i;
                renderBall(minecraft, dispatcher, poseStack, buffers, camera, partialTick, ball, scale, hovered);
            }
            if (revealed && pokemon != null && revealAge >= POKEMON_APPEAR_TICK) {
                renderPokemon(dispatcher, poseStack, buffers, camera, partialTick);
            }
        }

        private void renderBall(Minecraft minecraft, EntityRenderDispatcher dispatcher, PoseStack poseStack,
                                MultiBufferSource.BufferSource buffers, Vec3 camera, float partialTick,
                                ClientBall ball, float scale, boolean hovered) {
            Vec3 relative = ball.currentPosition.subtract(camera);
            poseStack.pushPose();
            poseStack.translate(relative.x, relative.y, relative.z);
            poseStack.scale(scale, scale, scale);
            // Establish the ball's owner-facing local frame before applying any rocking.
            // This keeps the shake on the ball's own front-to-back axis at every facing yaw.
            poseStack.mulPose(Axis.YP.rotationDegrees(ball.entity.getYRot()));
            if (revealed && ball.index == selectedIndex && revealAge < OPEN_TICK) {
                float strength = 1.0F - revealAge / (float) OPEN_TICK;
                float shake = (float) Math.sin(revealAge * 2.3D) * BALL_SHAKE_DEGREES * strength;
                poseStack.translate(0.0D, BALL_SHAKE_PIVOT_Y, 0.0D);
                poseStack.mulPose(Axis.ZP.rotationDegrees(shake));
                poseStack.translate(0.0D, -BALL_SHAKE_PIVOT_Y, 0.0D);
            }
            // Apply orientation outside Cobblemon's renderer so the optional opening pitch
            // happens around the ball's local X axis after it has turned toward the owner.
            float fallRoll = ball.fallRollDegrees(age, payload.staggerTicks(), payload.fallTicks(), partialTick);
            if (fallRoll != 0.0F) {
                // Pitch around the local left-right axis so the ball tumbles forward instead
                // of twisting clockwise/counter-clockwise around its front axis.
                poseStack.mulPose(Axis.XP.rotationDegrees(fallRoll));
            }
            if (revealed && ball.index == selectedIndex && revealAge >= OPEN_TICK) {
                float tiltProgress = Mth.clamp(
                        (revealAge - OPEN_TICK + partialTick) / OPEN_BALL_TILT_TICKS,
                        0.0F,
                        1.0F
                );
                poseStack.mulPose(Axis.XP.rotationDegrees(OPEN_BALL_TILT_DEGREES * tiltProgress));
            }
            int light = lightAt(ball.currentPosition);
            try {
                dispatcher.render(ball.entity, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, poseStack, buffers, light);
                if (hovered) {
                    OutlineBufferSource outlines = minecraft.renderBuffers().outlineBufferSource();
                    outlines.setColor(255, 255, 255, 255);
                    minecraft.levelRenderer.requestOutlineEffect();
                    dispatcher.render(ball.entity, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, poseStack, outlines, LightTexture.FULL_BRIGHT);
                }
            } catch (Throwable throwable) {
                if (!ball.renderFailed) {
                    ball.renderFailed = true;
                    YoikoServerCore.LOGGER.warn("Failed to render Cobblemon poke ball selection model", throwable);
                }
            }
            poseStack.popPose();
        }

        private void renderPokemon(EntityRenderDispatcher dispatcher, PoseStack poseStack,
                                   MultiBufferSource.BufferSource buffers, Vec3 camera, float partialTick) {
            ClientBall selected = balls.get(selectedIndex);
            Vec3 position = selected.currentPosition.add(0.0D, 0.58D, 0.0D);
            Vec3 relative = position.subtract(camera);
            float modelScale = correctedPokemonScale() * POKEMON_REVEAL_SCALE;
            float appear = Mth.clamp((revealAge - POKEMON_APPEAR_TICK) / 10.0F, 0.0F, 1.0F);
            float vanish = Mth.clamp((revealTicks - revealAge) / 15.0F, 0.0F, 1.0F);
            float scale = modelScale * appear * vanish;
            if (scale <= 0.01F) {
                return;
            }
            pokemon.setPos(position.x, position.y, position.z);
            poseStack.pushPose();
            poseStack.translate(relative.x, relative.y, relative.z);
            poseStack.scale(scale, scale, scale);
            float renderYaw = Mth.rotLerp(partialTick, pokemon.yRotO, pokemon.getYRot());
            try {
                dispatcher.render(pokemon, 0.0D, 0.0D, 0.0D, renderYaw, partialTick,
                        poseStack, buffers, LightTexture.FULL_BRIGHT);
            } catch (Throwable throwable) {
                YoikoServerCore.LOGGER.warn("Failed to render Cobblemon Pokemon reveal model", throwable);
                pokemon = null;
            }
            poseStack.popPose();
        }

        private float correctedPokemonScale() {
            float size = Math.max(pokemon.getBbHeight(), pokemon.getBbWidth());
            if (size <= 0.01F) {
                return 1.0F;
            }
            return Mth.clamp(1.55F / size, 0.35F, 1.2F);
        }

        private static void synchronizeRevealShinyAppearance(PokemonEntity entity, boolean shiny) {
            var model = entity.getPokemon();
            boolean clientState = model.isClient$common();
            try {
                // Client-only render entities skip Cobblemon's server-side aspect refresh.
                // Recompute once before returning the model to client mode, and force the
                // shiny aspect so the registered Pokemon renderer selects the shiny texture.
                model.setClient$common(false);
                model.setShiny(shiny);
                Set<String> forcedAspects = new HashSet<>(model.getForcedAspects());
                if (shiny) {
                    forcedAspects.add("shiny");
                } else {
                    forcedAspects.remove("shiny");
                }
                model.setForcedAspects(forcedAspects);
            } finally {
                model.setClient$common(clientState);
            }
            entity.setPokemon(model);
        }

        private float ballScale(int index) {
            if (!revealed || index == selectedIndex) {
                return 1.0F;
            }
            return Mth.clamp(1.0F - revealAge / (float) NON_SELECTED_FADE_TICKS, 0.0F, 1.0F);
        }

        private boolean ready() {
            return age >= payload.fallTicks() + payload.staggerTicks() * (balls.size() - 1) + LANDING_SETTLE_TICKS;
        }

        private boolean isInteractive(Minecraft minecraft) {
            return !revealed && !selectionSent && minecraft.player != null
                    && payload.ownerId().equals(minecraft.player.getUUID());
        }

        private int lightAt(Vec3 position) {
            try {
                return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, BlockPos.containing(position));
            } catch (Throwable ignored) {
                return LightTexture.FULL_BRIGHT;
            }
        }

        private void close() {
            pokemon = null;
            balls.clear();
        }
    }

    private static final class ClientBall {
        private final GachaSelectionStartPayload.BallEntry entry;
        private final int index;
        private final EmptyPokeBallEntity entity;
        private Vec3 currentPosition;
        private boolean landingSoundPlayed;
        private boolean renderFailed;

        private ClientBall(ClientLevel level, GachaSelectionStartPayload.BallEntry entry, int index, double fallHeight) {
            this.entry = entry;
            this.index = index;
            this.currentPosition = new Vec3(entry.x(), entry.y() + fallHeight, entry.z());
            this.entity = new EmptyPokeBallEntity(
                    switch (entry.appearanceRarity()) {
                        case SUB_LEGENDARY -> PokeBalls.getGreatBall();
                        case MYTHICAL -> PokeBalls.getUltraBall();
                        case LEGENDARY -> PokeBalls.getMasterBall();
                        case COMMON -> PokeBalls.getPokeBall();
                    },
                    level,
                    CobblemonEntities.EMPTY_POKEBALL
            );
            entity.setId(nextFakeEntityId--);
            entity.noPhysics = true;
            entity.setNoGravity(true);
            entity.setSilent(true);
            // CaptureState.NOT always selects Cobblemon's looping throw pose. FALL selects the
            // closed/idle pose while still allowing HIT to transition into the open animation.
            entity.setCaptureState(CaptureState.FALL);
            entity.setXRot(0.0F);
            entity.setYRot(entry.facingYaw());
            entity.setPos(currentPosition.x, currentPosition.y, currentPosition.z);
        }

        private void tick(ClientSession session) {
            // The entity is render-only and is not added to ClientLevel, so advance its
            // Cobblemon delegate and animation scheduler manually once per client tick.
            entity.tick();
            int delay = index * session.payload.staggerTicks();
            int localAge = session.age - delay;
            double groundY = entry.y();
            if (localAge <= 0) {
                currentPosition = new Vec3(entry.x(), groundY + session.payload.fallHeight(), entry.z());
            } else if (localAge < session.payload.fallTicks()) {
                double progress = localAge / (double) session.payload.fallTicks();
                double y = groundY + session.payload.fallHeight() * (1.0D - progress * progress);
                currentPosition = new Vec3(entry.x(), y, entry.z());
            } else {
                int bounceAge = localAge - session.payload.fallTicks();
                double bounce = 0.0D;
                if (bounceAge < LANDING_SETTLE_TICKS) {
                    double damping = 1.0D - bounceAge / (double) LANDING_SETTLE_TICKS;
                    bounce = Math.abs(Math.sin(bounceAge * Math.PI / 4.0D)) * 0.16D * damping;
                }
                currentPosition = new Vec3(entry.x(), groundY + bounce, entry.z());
                if (!landingSoundPlayed) {
                    landingSoundPlayed = true;
                    session.level.playLocalSound(
                            entry.x(), groundY, entry.z(), SoundEvents.WOOD_HIT,
                            SoundSource.PLAYERS, 0.55F, 0.85F + index * 0.08F, false
                    );
                }
            }
            entity.setPos(currentPosition.x, currentPosition.y, currentPosition.z);
            // Keep the button/front aimed at the owner for the entire fall and landing.
            entity.setXRot(0.0F);
            entity.setYRot(entry.facingYaw());
            emitShinyParticles(session);
        }

        private void emitShinyParticles(ClientSession session) {
            int density = session.payload.particleDensity();
            if (!entry.shiny() || density <= 0 || !visible(session.age, session.payload.staggerTicks()) || session.revealed) {
                return;
            }
            int interval = Math.max(3, 9 - density * 2);
            if ((session.age + index * 2) % interval != 0) {
                return;
            }
            double angle = session.age * 0.18D + index * 2.1D;
            double radius = 0.32D;
            session.level.addParticle(
                    session.age % (interval * 3) == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD,
                    currentPosition.x + Math.cos(angle) * radius,
                    currentPosition.y + 0.2D + Math.sin(angle * 0.7D) * 0.12D,
                    currentPosition.z + Math.sin(angle) * radius,
                    -Math.sin(angle) * 0.008D,
                    0.018D,
                    Math.cos(angle) * 0.008D
            );
        }

        private boolean visible(int sessionAge, int staggerTicks) {
            return sessionAge >= index * staggerTicks;
        }

        private float fallRollDegrees(int sessionAge, int staggerTicks, int fallTicks, float partialTick) {
            float localAge = sessionAge + partialTick - index * staggerTicks;
            if (localAge <= 0.0F || localAge >= fallTicks) {
                return 0.0F;
            }
            return FALL_ROLL_DEGREES * (localAge / fallTicks);
        }
    }
}
