package com.yoiko.core.client.screen;

import com.yoiko.core.network.OpenProfilePayload;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class YoikoProfileScreen extends Screen {
    private static final int PANEL_WIDTH = 384;
    private static final int PANEL_HEIGHT = 260;
    private static final int CARD_WIDTH = 172;
    private static final int CARD_HEIGHT = 38;
    private static final int TITLE_CARD_WIDTH = 106;
    private static final int TITLE_CARD_HEIGHT = 32;
    private OpenProfilePayload payload;

    public YoikoProfileScreen(OpenProfilePayload payload) {
        super(YoikoClientText.tr("yoiko_core.ui.profile.title"));
        this.payload = payload;
    }

    public void update(OpenProfilePayload payload) {
        this.payload = payload;
        rebuild();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int left = left();
        int top = top();
        OpenProfilePayload.Profile profile = payload.profile();
        int index = selectedIndex();
        addRenderableWidget(new ProfileButton(left + 12, top + 238, 58, 16,
                YoikoClientText.tr("yoiko_core.ui.profile.back"), () -> send("storage_open"), false));
        if (profile.ownProfile()) {
            addRenderableWidget(new ProfileButton(left + 78, top + 238, 112, 16,
                    YoikoClientText.tr(profile.sharingEnabled()
                            ? "yoiko_core.ui.profile.sharing_on"
                            : "yoiko_core.ui.profile.sharing_off"),
                    () -> send("profile_sharing|" + !profile.sharingEnabled()), profile.sharingEnabled()));
        }
        ProfileButton previous = new ProfileButton(left + 275, top + 238, 22, 16,
                Component.literal("<"), () -> selectRelative(-1), false);
        previous.active = index > 0;
        addRenderableWidget(previous);
        ProfileButton next = new ProfileButton(left + 350, top + 238, 22, 16,
                Component.literal(">"), () -> selectRelative(1), false);
        next.active = index >= 0 && index + 1 < payload.players().size();
        addRenderableWidget(next);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YoikoScreenStyle.renderBackdrop(graphics, width, height);
        int left = left();
        int top = top();
        YoikoScreenStyle.renderPanel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        YoikoScreenStyle.renderCosmeticRibbon(graphics, left + 10, top + 8, PANEL_WIDTH - 20, 31);
        renderHeader(graphics, left, top);
        renderShowcaseTitles(graphics, left, top);
        renderCards(graphics, left, top);
        int index = selectedIndex();
        graphics.drawCenteredString(font,
                Component.literal((Math.max(0, index) + 1) + "/" + Math.max(1, payload.players().size())),
                left + 324, top + 242, 0xFF806044);
        YoikoScreenStyle.renderWidgets(this, graphics, mouseX, mouseY, partialTick);
        ClientServerRequestState.render(graphics, font, "profile", width / 2, height - 28);
    }

    private void renderHeader(GuiGraphics graphics, int left, int top) {
        OpenProfilePayload.Profile profile = payload.profile();
        ResourceLocation skin = skinTexture(profile.playerId());
        PlayerFaceRenderer.draw(graphics, skin, left + 22, top + 12, 24);
        graphics.drawString(font, Component.literal(profile.playerName()), left + 54, top + 13,
                0xFF684426, false);
        Component rank = profile.activeRankId().isBlank()
                ? YoikoClientText.tr("yoiko_core.ui.profile.default_rank")
                : Component.literal(YoikoClientText.dataText(profile.activeRankName()));
        graphics.drawString(font, rank, left + 54, top + 26,
                0xFF000000 | profile.rankColor(), false);
        renderEquippedCosmetic(graphics, left + 300, top + 14, "HEAD", profile.headCosmeticId(), profile.rankColor());
        renderEquippedCosmetic(graphics, left + 322, top + 14, "CHEST", profile.chestCosmeticId(), profile.rankColor());
        renderEquippedCosmetic(graphics, left + 344, top + 14, "FEET", profile.feetCosmeticId(), profile.rankColor());
    }

    private void renderShowcaseTitles(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, YoikoClientText.tr("yoiko_core.ui.profile.showcase_titles"),
                left + 18, top + 45, 0xFF8A5A32, false);
        List<OpenProfilePayload.ShowcaseTitle> titles = payload.profile().showcaseTitles();
        for (int i = 0; i < 3; i++) {
            int x = left + 18 + i * 116;
            int y = top + 57;
            YoikoScreenStyle.renderCosmeticPaperCard(graphics, x, y, TITLE_CARD_WIDTH, TITLE_CARD_HEIGHT);
            if (i >= titles.size()) {
                graphics.drawCenteredString(font, YoikoClientText.tr("yoiko_core.profile.none"),
                        x + TITLE_CARD_WIDTH / 2, y + 12, 0xFFAA9A84);
                continue;
            }
            OpenProfilePayload.ShowcaseTitle title = titles.get(i);
            YoikoCosmeticIconRenderer.renderEntryIcon(graphics, x + 8, y + 7, 18,
                    "RANK", "rank:" + title.id(), true, title.color(), "", "NONE");
            Component name = Component.literal(YoikoClientText.dataText(title.displayName()));
            graphics.drawString(font, Component.literal(abbreviated(name.getString(), 68)),
                    x + 31, y + 12, 0xFF000000 | title.color(), false);
        }
    }

    private void renderCards(GuiGraphics graphics, int left, int top) {
        OpenProfilePayload.Profile p = payload.profile();
        int leftX = left + 18;
        int rightX = left + 194;
        int firstY = top + 96;
        renderCard(graphics, leftX, firstY,
                YoikoClientText.tr("yoiko_core.ui.profile.achievements"),
                YoikoClientText.tr("yoiko_core.ui.profile.achievements_value",
                        p.completedAchievements(), p.totalAchievements()));
        renderCard(graphics, leftX, firstY + 43,
                YoikoClientText.tr("yoiko_core.ui.profile.activity"),
                YoikoClientText.tr("yoiko_core.ui.profile.activity_value", p.dailyStreak(), p.marketSales()));
        Component rarity = p.highestRelicRarity().isBlank()
                ? YoikoClientText.tr("yoiko_core.profile.none")
                : YoikoClientText.tr("yoiko_core.rarity." + p.highestRelicRarity().toLowerCase(Locale.ROOT));
        renderCard(graphics, leftX, firstY + 86,
                YoikoClientText.tr("yoiko_core.ui.profile.relics"),
                YoikoClientText.tr("yoiko_core.ui.profile.relics_value",
                        p.ownedRelics(), p.appraisedRelics(), rarity));

        renderCard(graphics, rightX, firstY,
                YoikoClientText.tr("yoiko_core.ui.profile.rabbits"),
                YoikoClientText.tr("yoiko_core.ui.profile.rabbits_value", p.caughtRabbits(),
                        discoveredRareRabbits(p)));
        renderCard(graphics, rightX, firstY + 43,
                YoikoClientText.tr("yoiko_core.ui.profile.gacha"),
                YoikoClientText.tr("yoiko_core.ui.profile.gacha_value",
                        p.gachaRolls(), p.shinyGachaRolls(), p.legendaryGachaRolls()));
        renderCard(graphics, rightX, firstY + 86,
                YoikoClientText.tr("yoiko_core.ui.profile.turtles"),
                YoikoClientText.tr("yoiko_core.ui.profile.turtles_value", p.ownedTurtles(),
                        p.officialWins(), p.officialFinishes(), p.timeTrialRecords(),
                        p.goldenShell() ? "★" : "-"));
    }

    private void renderCard(GuiGraphics graphics, int x, int y, Component title, Component value) {
        YoikoScreenStyle.renderCosmeticPaperCard(graphics, x, y, CARD_WIDTH, CARD_HEIGHT);
        graphics.drawString(font, title, x + 8, y + 7, 0xFF8A5A32, false);
        graphics.drawString(font, Component.literal(abbreviated(value.getString(), CARD_WIDTH - 16)),
                x + 8, y + 21, 0xFF5F4934, false);
    }

    private void renderEquippedCosmetic(GuiGraphics graphics, int x, int y, String type, String id, int rankColor) {
        if (id == null || id.isBlank()) {
            YoikoCosmeticIconRenderer.renderProfileSlotIcon(graphics, x, y, type);
            return;
        }
        YoikoCosmeticIconRenderer.renderEntryIcon(graphics, x + 1, y + 1, 16,
                type, id, true, rankColor, "", "NONE");
    }

    private int discoveredRareRabbits(OpenProfilePayload.Profile profile) {
        int count = 0;
        if (profile.radiantRabbit()) count++;
        if (profile.mirrorRabbit()) count++;
        if (profile.crownRabbit()) count++;
        return count;
    }

    private ResourceLocation skinTexture(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerId);
            if (info != null) return info.getSkin().texture();
        }
        return DefaultPlayerSkin.get(playerId).texture();
    }

    private int selectedIndex() {
        UUID selected = payload.profile().playerId();
        for (int i = 0; i < payload.players().size(); i++) {
            if (selected.equals(payload.players().get(i).playerId())) return i;
        }
        return -1;
    }

    private void selectRelative(int delta) {
        int index = selectedIndex();
        int next = index + delta;
        if (next >= 0 && next < payload.players().size()) {
            send("profile_select|" + payload.players().get(next).playerId());
        }
    }

    private void send(String action) {
        if (ClientServerRequestState.begin(this, action)) {
            PacketDistributor.sendToServer(ClientMenuSession.action(action));
        }
    }

    private String abbreviated(String text, int width) {
        if (font.width(text) <= width) return text;
        String suffix = "...";
        String value = text;
        while (!value.isEmpty() && font.width(value) + font.width(suffix) > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class ProfileButton extends AbstractButton {
        private final Runnable action;
        private final boolean selected;

        private ProfileButton(int x, int y, int width, int height, Component message,
                              Runnable action, boolean selected) {
            super(x, y, width, height, message);
            this.action = action;
            this.selected = selected;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            YoikoScreenStyle.renderButton(graphics, getX(), getY(), getWidth(), getHeight(),
                    isHoveredOrFocused() || selected, active);
            graphics.drawCenteredString(font, getMessage(), getX() + getWidth() / 2,
                    getY() + 4, active ? 0xFF704C2D : 0xFF9A8F7D);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}
