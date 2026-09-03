package com.yoiko.core.client.turtle;

import com.mojang.math.Transformation;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.client.screen.YoikoButton;
import com.yoiko.core.network.TurtleHatchConfirmPayload;
import com.yoiko.core.network.TurtleHatchRevealPayload;
import com.yoiko.core.registry.YoikoEntities;
import com.yoiko.core.turtle.TurtleBodyAppearanceCatalog;
import com.yoiko.core.turtle.TurtleNameCatalog;
import com.yoiko.core.turtle.entity.RaceTurtleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Client-only seaside hatch ceremony and its mandatory, server-authoritative result acknowledgement. */
public final class TurtleHatchRevealClient {
    public static final TurtleHatchRevealClient INSTANCE = new TurtleHatchRevealClient();
    private static final int INTRO_TICK = 18;
    private static final int FIRST_CRACK_TICK = 36;
    private static final int SECOND_CRACK_TICK = 60;
    private static final int RARITY_PRELUDE_TICK = 74;
    private static final int REVEAL_TICK = 86;
    private static int nextFakeEntityId = -2_100_000_000;

    private Session session;

    private TurtleHatchRevealClient() {
    }

    public void start(TurtleHatchRevealPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (session != null) session.clear();
        if (minecraft.screen instanceof HatchResultScreen) minecraft.setScreen(null);
        session = new Session(minecraft.level, payload);
    }

    @SubscribeEvent
    public void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (session == null || minecraft.isPaused()) return;
        if (minecraft.level != session.level) {
            session.clear();
            session = null;
            return;
        }
        session.tick();
        if (session.resultReady && !(minecraft.screen instanceof HatchResultScreen result
                && result.belongsTo(session))) {
            minecraft.setScreen(new HatchResultScreen(session));
        }
    }

    @SubscribeEvent
    public void renderCeremonyHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (session == null || session.resultReady || minecraft.options.hideGui
                || minecraft.player == null || minecraft.screen != null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int center = graphics.guiWidth() / 2;
        Component ticket = ticketName(session.payload.ticketType());
        Component phase = Component.translatable(session.phaseKey());
        int boxWidth = Math.max(150, Math.max(minecraft.font.width(ticket), minecraft.font.width(phase)) + 22);
        int x = center - boxWidth / 2;
        graphics.fill(x, 7, x + boxWidth, 37, 0x980B2830);
        graphics.fill(x + 1, 8, x + boxWidth - 1, 9, 0xCC79D9D2);
        graphics.drawCenteredString(minecraft.font, ticket, center, 12, 0xFFFFE0A3);
        graphics.drawCenteredString(minecraft.font, phase, center, 23, 0xFFEAF7EF);
        int barX = center - 60;
        graphics.fill(barX, 33, barX + 120, 35, 0x884A706F);
        int progress = Math.min(120, Math.max(1, Math.round(120F * session.tick / REVEAL_TICK)));
        graphics.fill(barX, 33, barX + progress, 35,
                session.tick >= RARITY_PRELUDE_TICK ? rarityColor(session.payload) : 0xFF70CFC8);
    }

    @SubscribeEvent
    public void hideFirstPersonHand(RenderHandEvent event) {
        if (session != null) event.setCanceled(true);
    }

    private void confirm(Session confirmed) {
        if (session != confirmed) return;
        PacketDistributor.sendToServer(new TurtleHatchConfirmPayload(confirmed.payload.turtleId()));
        confirmed.clear();
        session = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof HatchResultScreen) minecraft.setScreen(null);
    }

    private static final class Session {
        private final ClientLevel level;
        private final TurtleHatchRevealPayload payload;
        private Display.BlockDisplay egg;
        private Turtle turtle;
        private int tick;
        private int resultTicks;
        private boolean resultReady;

        private Session(ClientLevel level, TurtleHatchRevealPayload payload) {
            this.level = level;
            this.payload = payload;
            replaceEgg(0, false);
            play(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.45F, 1.18F);
            waterRing(18, 1.18D, 0.015D);
        }

        private void tick() {
            tick++;
            animateEgg();
            if (resultReady) {
                resultTicks++;
                if (resultTicks % 18 == 0) resultAmbient();
                return;
            }
            if (tick < REVEAL_TICK && tick % 4 == 0) gatheringWave();
            if (tick == INTRO_TICK) {
                waterRing(22, 1.02D, 0.025D);
                play(SoundEvents.AXOLOTL_SPLASH, 0.48F, 1.28F);
            } else if (tick == FIRST_CRACK_TICK) {
                replaceEgg(1, true);
            } else if (tick == SECOND_CRACK_TICK) {
                replaceEgg(2, true);
            } else if (tick == RARITY_PRELUDE_TICK) {
                rarityPrelude();
            } else if (tick == REVEAL_TICK) {
                reveal();
            }
        }

        private String phaseKey() {
            if (tick < INTRO_TICK) return "yoiko_core.turtle.hatch.ceremony.gather";
            if (tick < RARITY_PRELUDE_TICK) return "yoiko_core.turtle.hatch.ceremony.crack";
            return "yoiko_core.turtle.hatch.ceremony.awaken";
        }

        private void animateEgg() {
            if (egg == null) return;
            int untilCrack = tick < FIRST_CRACK_TICK ? FIRST_CRACK_TICK - tick
                    : tick < SECOND_CRACK_TICK ? SECOND_CRACK_TICK - tick : REVEAL_TICK - tick;
            double strength = untilCrack >= 0 && untilCrack <= 8 ? (9 - untilCrack) / 9.0D : 0.0D;
            double shake = Math.sin(tick * 2.35D) * 0.018D * strength;
            double yaw = Math.toRadians(payload.yaw());
            egg.setPos(payload.x() + Math.cos(yaw) * shake, payload.y() + 0.035D,
                    payload.z() + Math.sin(yaw) * shake);
            // The egg deliberately keeps a fixed yaw; anticipation comes from cracking and a short lateral tremble.
            egg.setYRot(payload.yaw());
        }

        private void gatheringWave() {
            double progress = Math.min(1.0D, tick / (double) REVEAL_TICK);
            double radius = 1.34D - progress * 0.70D;
            int points = tick < FIRST_CRACK_TICK ? 7 : 10;
            double offset = tick * 0.11D;
            for (int i = 0; i < points; i++) {
                double angle = offset + Math.PI * 2.0D * i / points;
                level.addParticle(i % 3 == 0 ? ParticleTypes.BUBBLE_POP : ParticleTypes.SPLASH,
                        payload.x() + Math.cos(angle) * radius,
                        payload.y() + 0.025D + (i & 1) * 0.025D,
                        payload.z() + Math.sin(angle) * radius,
                        -Math.cos(angle) * 0.012D, 0.018D, -Math.sin(angle) * 0.012D);
            }
        }

        private void replaceEgg(int hatchStage, boolean sound) {
            if (egg != null) egg.discard();
            egg = createBlock(level,
                    Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.HATCH, hatchStage),
                    1.12F, 1.12F, 1.12F, 8.0F);
            egg.setPos(payload.x(), payload.y() + 0.035D, payload.z());
            egg.setYRot(payload.yaw());
            level.addEntity(egg);
            if (!sound) return;
            int fragments = 7 + hatchStage * 4;
            for (int i = 0; i < fragments; i++) {
                level.addParticle(i % 3 == 0 ? ParticleTypes.SPLASH : ParticleTypes.POOF,
                        payload.x() + (level.random.nextDouble() - 0.5D) * 0.48D,
                        payload.y() + 0.13D + level.random.nextDouble() * 0.22D,
                        payload.z() + (level.random.nextDouble() - 0.5D) * 0.48D,
                        0.0D, 0.02D + level.random.nextDouble() * 0.025D, 0.0D);
            }
            play(SoundEvents.TURTLE_EGG_CRACK, 0.9F, 0.90F + hatchStage * 0.16F);
            play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.35F, 1.10F + hatchStage * 0.14F);
        }

        private void rarityPrelude() {
            dustRing(14 + payload.rarity().ordinal() * 4, 0.72D, 0.045D);
            waterRing(18, 0.88D, 0.035D);
            play(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.68F, 1.18F + payload.rarity().ordinal() * 0.06F);
        }

        private void reveal() {
            if (egg != null) {
                egg.discard();
                egg = null;
            }
            RaceTurtleEntity revealed = new RaceTurtleEntity(YoikoEntities.RACE_TURTLE.get(), level);
            revealed.setBodyAppearance(payload.bodyAppearance());
            revealed.setAppearance("natural");
            turtle = revealed;
            turtle.setId(nextFakeEntityId--);
            turtle.setAge(-24_000);
            turtle.setNoAi(true);
            turtle.setSilent(true);
            turtle.moveTo(payload.x(), payload.y() + 0.02D, payload.z(), payload.yaw(), 0.0F);
            level.addEntity(turtle);

            int burst = 22 + payload.rarity().ordinal() * 6;
            for (int i = 0; i < burst; i++) {
                double angle = Math.PI * 2.0D * i / burst;
                double speed = 0.055D + level.random.nextDouble() * 0.085D;
                level.addParticle(i % 3 == 0 ? ParticleTypes.BUBBLE_POP : ParticleTypes.SPLASH,
                        payload.x(), payload.y() + 0.16D, payload.z(),
                        Math.cos(angle) * speed, 0.055D + level.random.nextDouble() * 0.09D,
                        Math.sin(angle) * speed);
            }
            dustRing(22 + payload.rarity().ordinal() * 7, 0.96D, 0.11D);
            if (payload.rarity().ordinal() >= 3) {
                level.addParticle(ParticleTypes.FLASH, payload.x(), payload.y() + 0.30D, payload.z(), 0, 0, 0);
            }
            if (TurtleBodyAppearanceCatalog.isRare(payload.bodyAppearance())) {
                for (int i = 0; i < 18; i++) {
                    level.addParticle(ParticleTypes.ENCHANT,
                            payload.x() + (level.random.nextDouble() - 0.5D) * 0.65D,
                            payload.y() + 0.12D + level.random.nextDouble() * 0.38D,
                            payload.z() + (level.random.nextDouble() - 0.5D) * 0.65D,
                            0, 0.025D, 0);
                }
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.78F, 1.38F);
            }
            play(SoundEvents.TURTLE_EGG_HATCH, 0.95F, 1.04F);
            play(payload.rarity().ordinal() >= 3
                    ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                    0.88F, 1.06F + payload.rarity().ordinal() * 0.055F);
            resultReady = true;
            resultTicks = 0;
        }

        private void resultAmbient() {
            int count = 5 + payload.rarity().ordinal();
            Vector3f color = rarityParticleColor(payload);
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0D * i / count + resultTicks * 0.025D;
                level.addParticle(new DustParticleOptions(color, 0.72F),
                        payload.x() + Math.cos(angle) * 0.68D,
                        payload.y() + 0.08D + (i & 1) * 0.08D,
                        payload.z() + Math.sin(angle) * 0.68D, 0, 0.018D, 0);
            }
        }

        private void waterRing(int count, double radius, double verticalSpeed) {
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0D * i / count;
                level.addParticle(i % 4 == 0 ? ParticleTypes.DOLPHIN : ParticleTypes.SPLASH,
                        payload.x() + Math.cos(angle) * radius,
                        payload.y() + 0.035D + (i & 1) * 0.025D,
                        payload.z() + Math.sin(angle) * radius,
                        -Math.cos(angle) * 0.012D, verticalSpeed, -Math.sin(angle) * 0.012D);
            }
        }

        private void dustRing(int count, double radius, double verticalSpeed) {
            Vector3f color = rarityParticleColor(payload);
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0D * i / count;
                level.addParticle(new DustParticleOptions(color, 0.92F),
                        payload.x() + Math.cos(angle) * radius,
                        payload.y() + 0.10D + (i & 1) * 0.08D,
                        payload.z() + Math.sin(angle) * radius,
                        Math.cos(angle) * 0.01D, verticalSpeed, Math.sin(angle) * 0.01D);
            }
        }

        private void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
            level.playLocalSound(payload.x(), payload.y(), payload.z(), sound,
                    SoundSource.PLAYERS, volume, pitch, false);
        }

        private void clear() {
            if (egg != null) {
                egg.discard();
                egg = null;
            }
            if (turtle != null) {
                turtle.discard();
                turtle = null;
            }
        }
    }

    private static final class HatchResultScreen extends Screen {
        private static final ResourceLocation RESULT_CARD =
                YoikoServerCore.id("textures/gui/turtle/hatch_result_card.png");
        private static final int TEXTURE_WIDTH = 246;
        private static final int TEXTURE_HEIGHT = 256;
        private static final int DETAILS_COMPLETE_TICKS = 48;
        private final Session session;
        private Button confirmButton;

        private HatchResultScreen(Session session) {
            super(Component.translatable("yoiko_core.turtle.hatch.screen_title"));
            this.session = session;
        }

        private boolean belongsTo(Session value) {
            return session == value;
        }

        @Override
        protected void init() {
            Layout layout = layout();
            int buttonWidth = Math.min(88, layout.width() - 36);
            confirmButton = YoikoButton.create(layout.x() + (layout.width() - buttonWidth) / 2,
                    layout.y() + layout.height() - 24, buttonWidth, 16,
                    Component.translatable("yoiko_core.turtle.hatch.confirm"),
                    ignored -> INSTANCE.confirm(session)).withAccent(rarityColor(session.payload));
            confirmButton.visible = false;
            confirmButton.active = false;
            addRenderableWidget(confirmButton);
        }

        @Override
        public void tick() {
            if (confirmButton != null) {
                boolean ready = session.resultTicks >= DETAILS_COMPLETE_TICKS;
                confirmButton.visible = ready;
                confirmButton.active = ready;
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderEdgeVignette(graphics);
            Layout target = layout();
            double progress = Math.min(1.0D, (session.resultTicks + partialTick) / 10.0D);
            double eased = 1.0D - Math.pow(1.0D - progress, 3.0D);
            int x = target.x() + (int) Math.round((1.0D - eased) * 28.0D);
            int y = target.y();
            graphics.blit(RESULT_CARD, x, y, 0, 0, target.width(), target.height(),
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);

            int sidePadding = Math.max(17, Math.round(target.width() * 24.0F / TEXTURE_WIDTH));
            int innerX = x + sidePadding;
            int innerWidth = Math.max(84, target.width() - sidePadding * 2);
            int labelWidth = Math.min(60, Math.max(48, innerWidth * 2 / 5));
            graphics.enableScissor(innerX, y + Math.max(14, target.height() / 12),
                    innerX + innerWidth, y + target.height() - 24);
            try {
                if (session.resultTicks >= 8) {
                    Component result = Component.translatable("yoiko_core.turtle.hatch.result",
                            TurtleNameCatalog.component(session.payload.name()), session.payload.rarity().symbol());
                    drawCenteredClamped(graphics, result, x + target.width() / 2,
                            y + Math.max(31, target.height() * 19 / 100), innerWidth,
                            rarityColor(session.payload));
                }
                int infoY = y + target.height() * 35 / 100;
                int gap = 15;
                if (session.resultTicks >= 16) {
                    drawInfoRow(graphics,
                            Component.translatable("yoiko_core.turtle.hatch.label.skill"),
                            Component.translatable("yoiko_core.turtle.skill.active."
                                    + session.payload.activeSkill() + ".name"),
                            innerX, infoY, innerWidth, labelWidth, 0xFF367B78);
                }
                if (session.resultTicks >= 24) {
                    drawStatsRow(graphics, innerX, infoY + gap, innerWidth, labelWidth);
                }
                if (session.resultTicks >= 32) {
                    drawInfoRow(graphics,
                            Component.translatable("yoiko_core.turtle.hatch.label.training"),
                            trainingName(session.payload.recommendedTraining()),
                            innerX, infoY + gap * 2, innerWidth, labelWidth, 0xFF4B3A29);
                }
                if (session.resultTicks >= 40) {
                    drawInfoRow(graphics,
                            Component.translatable("yoiko_core.turtle.hatch.label.passive"),
                            Component.translatable(session.payload.specialPassive()
                                    ? "yoiko_core.turtle.hatch.value.present"
                                    : "yoiko_core.turtle.hatch.value.none"),
                            innerX, infoY + gap * 3, innerWidth, labelWidth,
                            session.payload.specialPassive() ? 0xFFB87324 : 0xFF6E725F);
                }
                if (session.resultTicks >= DETAILS_COMPLETE_TICKS) {
                    boolean rareBody = TurtleBodyAppearanceCatalog.isRare(session.payload.bodyAppearance());
                    Component body = Component.translatable("yoiko_core.turtle.body."
                            + session.payload.bodyAppearance()).copy().append(rareBody ? " ✦" : "");
                    drawInfoRow(graphics,
                            Component.translatable("yoiko_core.turtle.hatch.label.body"), body,
                            innerX, infoY + gap * 4, innerWidth, labelWidth,
                            rareBody ? 0xFFD08B30 : 0xFF6E725F);
                }
            }
            finally {
                graphics.disableScissor();
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private void drawInfoRow(GuiGraphics graphics, Component label, Component value,
                                 int x, int y, int maxWidth, int labelWidth, int valueColor) {
            drawClamped(graphics, label, x, y, Math.max(1, labelWidth - 6), 0xFFA27B4C);
            drawClamped(graphics, value, x + labelWidth, y,
                    Math.max(1, maxWidth - labelWidth), valueColor);
        }

        private void drawStatsRow(GuiGraphics graphics, int x, int y, int maxWidth, int labelWidth) {
            Component totalLabel = Component.translatable("yoiko_core.turtle.hatch.label.total");
            Component leagueLabel = Component.translatable("yoiko_core.turtle.hatch.label.league");
            String total = Integer.toString(session.payload.total());
            String league = session.payload.league();

            drawClamped(graphics, totalLabel, x, y, Math.max(1, labelWidth - 6), 0xFFA27B4C);
            drawClamped(graphics, Component.literal(total), x + labelWidth, y,
                    Math.max(1, maxWidth - labelWidth), 0xFF4B3A29);

            int leagueValueWidth = font.width(league);
            int leagueLabelWidth = font.width(leagueLabel);
            int leagueX = x + maxWidth - leagueLabelWidth - leagueValueWidth - 5;
            int minimumLeagueX = x + labelWidth + font.width(total) + 10;
            if (leagueX >= minimumLeagueX) {
                graphics.drawString(font, leagueLabel, leagueX, y, 0xFFA27B4C, false);
                graphics.drawString(font, league, x + maxWidth - leagueValueWidth, y, 0xFF4B3A29, false);
            }
        }

        private void drawClamped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
            String value = text.getString();
            if (font.width(value) > maxWidth) value = font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width("…"))) + "…";
            graphics.drawString(font, value, x, y, color, false);
        }

        private void drawCenteredClamped(GuiGraphics graphics, Component text, int centerX, int y,
                                         int maxWidth, int color) {
            String value = text.getString();
            if (font.width(value) > maxWidth) value = font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width("…"))) + "…";
            graphics.drawCenteredString(font, value, centerX, y, color);
        }

        private void renderEdgeVignette(GuiGraphics graphics) {
            int strip = Math.max(3, Math.min(6, width / 90));
            for (int i = 0; i < 5; i++) {
                int alpha = 34 - i * 6;
                int color = alpha << 24;
                graphics.fill(i * strip, 0, (i + 1) * strip, height, color);
                graphics.fill(width - (i + 1) * strip, 0, width - i * strip, height, color);
            }
        }

        private Layout layout() {
            int maxWidth = Math.max(148, width / 2 - 10);
            int cardWidth = Math.min(188, maxWidth);
            int cardHeight = Math.round(cardWidth * (TEXTURE_HEIGHT / (float) TEXTURE_WIDTH));
            if (cardHeight > height - 12) {
                cardHeight = Math.max(154, height - 12);
                cardWidth = Math.round(cardHeight * (TEXTURE_WIDTH / (float) TEXTURE_HEIGHT));
            }
            return new Layout(width - cardWidth - 6, Math.max(6, (height - cardHeight) / 2),
                    cardWidth, cardHeight);
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Intentionally empty: the world and the newly hatched turtle remain sharp and visible.
        }

        @Override
        protected void renderBlurredBackground(float partialTick) {
            // Prevent the vanilla menu blur from hiding the world-space reveal.
        }

        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public void onClose() { }
        @Override public boolean isPauseScreen() { return false; }

        private record Layout(int x, int y, int width, int height) { }
    }

    private static Display.BlockDisplay createBlock(ClientLevel level, BlockState state,
                                                     float scaleX, float scaleY, float scaleZ,
                                                     float viewRange) {
        Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.put("block_state", NbtUtils.writeBlockState(state));
        Transformation transformation = new Transformation(
                new Vector3f(-scaleX / 2.0F, 0.0F, -scaleZ / 2.0F),
                new Quaternionf(), new Vector3f(scaleX, scaleY, scaleZ), new Quaternionf());
        Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, transformation)
                .ifSuccess(encoded -> tag.put("transformation", encoded));
        tag.putFloat("view_range", viewRange);
        display.load(tag);
        display.setId(nextFakeEntityId--);
        display.setNoGravity(true);
        display.setSilent(true);
        return display;
    }

    private static int rarityColor(TurtleHatchRevealPayload payload) {
        return switch (payload.rarity()) {
            case COMMON -> 0xFF777777;
            case UNCOMMON -> 0xFF4DAF67;
            case RARE -> 0xFF3A9DC4;
            case EPIC -> 0xFF9B59C4;
            case LEGENDARY -> 0xFFE4A934;
        };
    }

    private static Vector3f rarityParticleColor(TurtleHatchRevealPayload payload) {
        return switch (payload.rarity()) {
            case COMMON -> new Vector3f(0.65F, 0.65F, 0.65F);
            case UNCOMMON -> new Vector3f(0.30F, 0.86F, 0.40F);
            case RARE -> new Vector3f(0.30F, 0.68F, 0.94F);
            case EPIC -> new Vector3f(0.68F, 0.38F, 0.90F);
            case LEGENDARY -> new Vector3f(1.00F, 0.70F, 0.18F);
        };
    }

    private static Component ticketName(String value) {
        return Component.translatable(switch (value) {
            case "PICKUP" -> "item.yoiko_core.turtle_pickup_hatch_ticket";
            case "RARE_FRONT", "RARE_STEADY", "RARE_FOLLOW", "RARE_CLOSER", "RARE_STRATEGY_SELECT" ->
                    "item.yoiko_core.turtle_rare_strategy_ticket";
            case "EPIC_GUARANTEED" -> "item.yoiko_core.turtle_epic_hatch_ticket";
            default -> "item.yoiko_core.turtle_standard_hatch_ticket";
        });
    }

    private static Component trainingName(String value) {
        return switch (value) {
            case "SPEED_DRILL" -> Component.translatable("yoiko_core.turtle.ui.training.speed");
            case "ENDURANCE_SWIM" -> Component.translatable("yoiko_core.turtle.ui.training.endurance");
            case "PUSH_OFF_DRILL" -> Component.translatable("yoiko_core.turtle.ui.training.power");
            case "DODGE_DRILL" -> Component.translatable("yoiko_core.turtle.ui.training.calm");
            case "COURSE_STUDY" -> Component.translatable("yoiko_core.turtle.ui.training.navigation");
            default -> Component.literal("-");
        };
    }
}
