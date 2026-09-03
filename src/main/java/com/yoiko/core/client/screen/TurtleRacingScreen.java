package com.yoiko.core.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.network.OpenTurtleMenuPayload;
import com.yoiko.core.network.TurtleRaceMenuStatusPayload;
import com.yoiko.core.network.TurtleMenuActionPayload;
import com.yoiko.core.turtle.TurtleBodyAppearanceCatalog;
import com.yoiko.core.turtle.TurtleBettingRules;
import com.yoiko.core.turtle.TurtleNameCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/** Turtle racing as a first-class page of the regular YOIKO menu. */
public final class TurtleRacingScreen extends Screen {
    private enum Tab {
        INFO("yoiko_core.turtle.ui.tab.info", "textures/gui/turtle/tabs/tab_turtles.png"),
        GROWTH("yoiko_core.turtle.ui.tab.growth", "textures/gui/turtle/tabs/tab_growth.png"),
        APPEARANCE("yoiko_core.turtle.ui.tab.appearance", "textures/gui/turtle/tabs/tab_appearance.png"),
        RACE("yoiko_core.turtle.ui.tab.race", "textures/gui/turtle/tabs/tab_race.png");

        private final String label;
        private final ResourceLocation icon;

        Tab(String label, String icon) {
            this.label = label;
            this.icon = ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID, icon);
        }
    }

    private static final UUID EMPTY_ID = new UUID(0L, 0L);
    private static final long[] BET_AMOUNTS = {20, 50, 100, 200};
    private static final String[] TRAINING_IDS = {"SPEED_DRILL", "ENDURANCE_SWIM", "PUSH_OFF_DRILL", "DODGE_DRILL", "COURSE_STUDY"};
    private static final int[][] TRAINING_GAINS={{2,0,1,0,0},{0,2,0,1,0},{0,0,2,0,1},{0,1,0,2,0},{1,0,0,0,2}};
    private static final String[] STRATEGY_IDS = {"FRONT", "STEADY", "FOLLOW", "CLOSER"};
    private static final String[] STAT_IDS = {"speed", "stamina", "power", "calm", "navigation"};
    private static final int[] RADAR_STAT_COLORS = {0xFF18BBD1, 0xFFE14D57, 0xFFE68A24, 0xFF2789C1, 0xFF7EA31A};
    private static final String[] COMPANION_IDS = {"dolphin","axolotl","blue_axolotl","frog","rabbit","bee","parrot","wolf","fox","armadillo","allay","goat","sniffer"};
    private static final ResourceLocation STAT_ICON_SHEET = infoIcon("stat", "stats");
    private static final ResourceLocation ICON_ACTIVE = infoIcon("skill", "active");
    private static final ResourceLocation ICON_PASSIVE = infoIcon("skill", "passive");
    private static final ResourceLocation ICON_COMPANION = infoIcon("skill", "companion");
    private static final ResourceLocation ICON_SURFACE = infoIcon("surface", "surface");
    private static final ResourceLocation ICON_WEATHER = infoIcon("weather", "weather");
    private static final ResourceLocation ICON_SHELL_MEDAL = infoIcon("resource", "shell_medal");
    private static final ResourceLocation ICON_AWAKENING = infoIcon("resource", "awakening_point");
    private static final ResourceLocation ICON_TRAINING = infoIcon("resource", "training_count");
    private static final String[] TIME_TRIAL_LEAGUES = {"D", "C", "B", "A", "S"};
    private static final int CONTENT_WIDTH = 176;
    private static final int PARCHMENT_TEXT = 0xFF5E4B35;
    private static final int PARCHMENT_MUTED = 0xFF89755D;
    private static final int PARCHMENT_TEAL = 0xFF3F7F78;
    private static final int PARCHMENT_GOLD = 0xFFB47728;
    private static final int INFO_ACTIVE_Y = 31;
    private static final int INFO_STAT_Y = 47;
    private static final int INFO_STAT_ROW_HEIGHT = 15;
    private static final int INFO_PASSIVE_Y = 132;
    private static final int GROWTH_STAT_Y = 31;
    private static final int GROWTH_STAT_ROW_HEIGHT = 14;
    private static final int GROWTH_PASSIVE_Y = 104;
    private static final int RADAR_CENTER_X = 38;
    private static final int INFO_RADAR_CENTER_Y = 80;
    private static final int GROWTH_RADAR_CENTER_Y = 65;
    private static final int RADAR_RADIUS = 21;
    private static final int RADAR_ICON_MARGIN = 8;
    private static final int RADAR_ICON_SIZE = 8;
    private static final int STAT_ROW_X = 76;
    private static final int TURTLE_PASSIVE_COLUMN_WIDTH = 63;
    private static final int TURTLE_PASSIVE_ROW_GAP = 21;
    private static final Set<String> SPECIAL_PASSIVES=Set.of("ancient_patience","golden_gap","weather_crown",
            "adaptive_shell","quiet_champion","comeback_star");
    private static final Pattern EFFECT_NUMBER=Pattern.compile("[±+\\-]?\\d+(?:\\.\\d+)?(?:\\s*[~～–]\\s*\\d+(?:\\.\\d+)?)?\\s*(?:%p?|초|틱|블록|위|秒|ティック|ブロック|位|seconds?|ticks?|blocks?)?",Pattern.CASE_INSENSITIVE);
    private enum EffectEmphasis { BODY, TRIGGER, VERY_SMALL, SMALL, MEDIUM, LARGE, VERY_LARGE, NUMBER }
    private static final int APPEARANCE_COLUMNS = 7;
    private static final int APPEARANCE_VISIBLE_ROWS = 3;
    private static final Map<String,OpenTurtleMenuPayload.AppearanceDefinition> APPEARANCE_CATALOG_CACHE = new LinkedHashMap<>();
    private static final Map<String,ResourceLocation> APPEARANCE_BUCKET_TEXTURE_CACHE = new HashMap<>();
    private static final byte[] SPARKLE_X={2,19,4,20,11,1};
    private static final byte[] SPARKLE_Y={5,3,19,17,1,12};

    private final List<YoikoIconButton> navigationButtons = new ArrayList<>();
    private final List<ModeButton> modeButtons = new ArrayList<>();
    private final List<AppearanceButton> appearanceButtons = new ArrayList<>();
    private final List<Button> trainingButtons = new ArrayList<>();
    private final Map<Button,Integer> trainingButtonStats = new HashMap<>();
    private final List<Button> passiveButtons = new ArrayList<>();
    private final List<Button> strategyButtons = new ArrayList<>();
    private final List<BetCandidateButton> betCandidateButtons=new ArrayList<>();
    private final List<Button> appearanceModalButtons = new ArrayList<>();
    private final List<Button> rerollModalButtons = new ArrayList<>();
    private final Map<String,String> appearanceNameCache = new HashMap<>();
    private List<AppearanceView> appearanceViews = List.of();
    private OpenTurtleMenuPayload data;
    private TurtleRaceMenuStatusPayload raceStatus;
    private Tab tab;
    private int selected;
    private int page;
    private int betHeat;
    private int betOption;
    private int betAmountIndex = 1;
    private int rerollChoice;
    private int rerollCandidatePage;
    private boolean companionDexOpen;
    private int timeTrialLeagueChoice = -1;
    private Button registerButton;
    private Button lockButton;
    private Button releaseButton;
    private Button companionButton;
    private Button companionDexButton;
    private Button trainingResetButton;
    private Button awakenButton;
    private Button rerollInfoButton;
    private Button rerollExecuteButton;
    private String appearanceConfirmId;
    private String pendingAppearanceId;
    private String pendingAppearanceAction;
    private String appearanceFeedbackId;
    private String appearanceFeedbackAction;
    private int appearanceFeedbackTicks;
    private int appearanceScrollRow;
    private int raceStatusRefreshTicks;

    public TurtleRacingScreen(OpenTurtleMenuPayload data) {
        super(Component.translatable("yoiko_core.turtle.ui.title"));
        this.data = data;
        this.raceStatus = new TurtleRaceMenuStatusPayload(data);
        updateAppearanceCatalog(data);
        this.tab = parseTab(data.initialTab());
        this.page = data.turtlePage();
    }

    public void update(OpenTurtleMenuPayload value) {
        OpenTurtleMenuPayload previousData=data;
        UUID before = selectedCard() == null ? null : selectedCard().id();
        int previousPage=page;
        data = value;
        raceStatus = new TurtleRaceMenuStatusPayload(value);
        updateAppearanceCatalog(value);
        page=value.turtlePage();
        if(previousPage!=page)selected=0;
        if (before != null) {
            for (int i = 0; i < data.turtles().size(); i++) {
                if (data.turtles().get(i).id().equals(before)) selected = i;
            }
        }
        selected = Math.max(0, Math.min(selected, Math.max(0, data.turtles().size() - 1)));
        resolveAppearanceRequest(previousData,value);
        clampBetSelection();
        if(tab==Tab.APPEARANCE&&appearanceConfirmId==null&&canRefreshAppearanceOnly(previousData,value))refreshAppearanceWidgets();
        else rebuild();
    }

    public void updateRaceStatus(TurtleRaceMenuStatusPayload value){
        TurtleRaceMenuStatusPayload previous=raceStatus;
        raceStatus=value;
        if("RUNNING".equals(value.competition().phase()))betHeat=value.competition().currentHeat();
        clampBetSelection();
        if(tab==Tab.RACE&&raceWidgetsChanged(previous,value))rebuild();
    }

    private void updateAppearanceCatalog(OpenTurtleMenuPayload payload){
        if(!payload.appearanceCatalog().isEmpty()){
            APPEARANCE_CATALOG_CACHE.clear();
            payload.appearanceCatalog().forEach(value->APPEARANCE_CATALOG_CACHE.put(value.id(),value));
            appearanceNameCache.clear();
        }
        Map<String,OpenTurtleMenuPayload.AppearanceState> states=new HashMap<>();
        payload.appearanceStates().forEach(value->states.put(value.id(),value));
        appearanceViews=APPEARANCE_CATALOG_CACHE.values().stream()
                .map(definition->new AppearanceView(definition,states.getOrDefault(definition.id(),
                        new OpenTurtleMenuPayload.AppearanceState(definition.id(),false,false,0,0))))
                .sorted(Comparator.comparing((AppearanceView value)->!value.favorite()))
                .toList();
        int rows=(appearanceViews.size()+APPEARANCE_COLUMNS-1)/APPEARANCE_COLUMNS;
        appearanceScrollRow=Math.max(0,Math.min(appearanceScrollRow,Math.max(0,rows-APPEARANCE_VISIBLE_ROWS)));
    }

    private AppearanceView appearanceById(String id){
        if(id==null)return null;
        return appearanceViews.stream().filter(value->value.id().equals(id)).findFirst().orElse(null);
    }

    private void resolveAppearanceRequest(OpenTurtleMenuPayload before,OpenTurtleMenuPayload after){
        if(pendingAppearanceId==null)return;
        OpenTurtleMenuPayload.AppearanceState oldState=stateById(before,pendingAppearanceId);
        OpenTurtleMenuPayload.AppearanceState newState=stateById(after,pendingAppearanceId);
        OpenTurtleMenuPayload.Card current=selectedCard();
        boolean success=switch(pendingAppearanceAction==null?"":pendingAppearanceAction){
            case "APPEARANCE_UNLOCK"->newState!=null&&newState.unlocked();
            case "APPEARANCE_FAVORITE"->oldState!=null&&newState!=null&&oldState.favorite()!=newState.favorite();
            case "APPEARANCE"->current!=null&&pendingAppearanceId.equals(current.appearance());
            default->false;
        };
        if(success){
            appearanceFeedbackId=pendingAppearanceId;appearanceFeedbackAction=pendingAppearanceAction;appearanceFeedbackTicks=24;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.AMETHYST_BLOCK_CHIME,1.35F,0.65F));
        }
        pendingAppearanceId=null;pendingAppearanceAction=null;
    }

    private static OpenTurtleMenuPayload.AppearanceState stateById(OpenTurtleMenuPayload payload,String id){
        return payload.appearanceStates().stream().filter(value->value.id().equals(id)).findFirst().orElse(null);
    }

    private boolean canRefreshAppearanceOnly(OpenTurtleMenuPayload before,OpenTurtleMenuPayload after){
        return !appearanceButtons.isEmpty()&&before.turtlePage()==after.turtlePage()
                &&before.turtles().size()==after.turtles().size();
    }

    private boolean raceWidgetsChanged(TurtleRaceMenuStatusPayload before,TurtleRaceMenuStatusPayload after){
        if(before==null||after==null)return true;
        if(before.competition().active()!=after.competition().active()
                ||!before.competition().league().equals(after.competition().league())
                ||!before.competition().phase().equals(after.competition().phase())
                ||before.competition().currentHeat()!=after.competition().currentHeat()
                ||before.competition().playerRegistered()!=after.competition().playerRegistered()
                ||before.timeTrial().engaged()!=after.timeTrial().engaged()
                ||before.betPools().size()!=after.betPools().size())return true;
        for(int i=0;i<before.betPools().size();i++){
            var oldBet=before.betPools().get(i);var newBet=after.betPools().get(i);
            if(oldBet.heat()!=newBet.heat()||!oldBet.entryId().equals(newBet.entryId()))return true;
        }
        return false;
    }

    private void refreshAppearanceWidgets(){
        OpenTurtleMenuPayload.Card card=selectedCard();
        int first=appearanceScrollRow*APPEARANCE_COLUMNS;
        for(int i=0;i<appearanceButtons.size();i++){
            int index=first+i;
            if(index>=appearanceViews.size())break;
            AppearanceButton button=appearanceButtons.get(i);
            button.update(appearanceViews.get(index),card!=null&&appearanceViews.get(index).id().equals(card.appearance()));
            updateAppearanceButtonState(button);
        }
    }

    private void updateAppearanceButtonState(AppearanceButton button){
        AppearanceView appearance=button.appearance;
        button.active=(appearance.unlocked()||appearance.unlockKind().equals("GEM"))
                &&(!isShellAppearancePending()||!pendingAppearanceId.equals(appearance.id()))
                &&appearanceConfirmId==null;
    }

    private boolean isShellAppearancePending(){return pendingAppearanceId!=null;}

    @Override public void tick(){
        if(appearanceFeedbackTicks>0)appearanceFeedbackTicks--;
        if(tab==Tab.RACE&&appearanceConfirmId==null&&data.rerollOffer()==null&&data.rerollCandidates()==null){
            if(++raceStatusRefreshTicks>=40){
                raceStatusRefreshTicks=0;
                PacketDistributor.sendToServer(ClientMenuSession.turtleAction("STATUS_REFRESH",EMPTY_ID,Integer.toString(betCandidateSignature()),0));
            }
        }else raceStatusRefreshTicks=0;
    }

    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY){
        if(tab==Tab.APPEARANCE&&appearanceConfirmId==null){
            int right=YoikoMenuLayout.splitLeft(width)+YoikoMenuLayout.SPLIT_RIGHT_PANEL_X;
            int top=YoikoMenuLayout.splitTop(height);
            if(mouseX>=right+8&&mouseX<right+184&&mouseY>=top+105&&mouseY<top+180){
                int rows=(appearanceViews.size()+APPEARANCE_COLUMNS-1)/APPEARANCE_COLUMNS;
                int next=Math.max(0,Math.min(Math.max(0,rows-APPEARANCE_VISIBLE_ROWS),appearanceScrollRow+(scrollY<0?1:-1)));
                if(next!=appearanceScrollRow){appearanceScrollRow=next;rebuild();return true;}
            }
        }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        navigationButtons.clear();
        modeButtons.clear();
        appearanceButtons.clear();
        trainingButtons.clear();
        trainingButtonStats.clear();
        passiveButtons.clear();
        strategyButtons.clear();
        betCandidateButtons.clear();
        appearanceModalButtons.clear();
        rerollModalButtons.clear();
        lockButton=null;
        releaseButton=null;
        companionButton=null;
        companionDexButton=null;
        trainingResetButton=null;
        awakenButton=null;
        rerollInfoButton=null;
        rerollExecuteButton=null;

        int left = YoikoMenuLayout.splitLeft(width);
        int top = YoikoMenuLayout.splitTop(height);
        int right = left + YoikoMenuLayout.SPLIT_RIGHT_PANEL_X;
        YoikoNavigationTabs.add(navigationButtons, left, top, "turtle", this::sendNavigation,
                this::addRenderableWidget);
        for (int i = 0; i < Tab.values().length; i++) {
            Tab value = Tab.values()[i];
            ModeButton button = new ModeButton(right + 8 + i * 42, top + 6, value, value == tab, ignored -> {
                tab = value;
                companionDexOpen=false;
                rebuild();
            });
            modeButtons.add(button);
            addRenderableWidget(button);
        }
        buildTurtleCards(left, top);

        OpenTurtleMenuPayload.Card card = selectedCard();
        int x = right + 8;
        int y = top + 70;
        switch (tab) {
            case INFO -> buildInfoActions(card, right + 10, top + 53);
            case GROWTH -> buildGrowthActions(card, right + 10, top + 53);
            case APPEARANCE -> buildAppearanceActions(card,x,y);
            case RACE -> buildRaceActions(card, right + 10, top + 53);
        }
        if(tab==Tab.APPEARANCE&&appearanceConfirmId!=null)buildAppearanceConfirm(card,right,top);
        if(data.rerollOffer()!=null)buildRerollChoice(right,top,data.rerollOffer());
        else if(data.rerollCandidates()!=null)buildRerollCandidates(right,top,data.rerollCandidates());
        YoikoMousePosition.restoreIfRemembered();
    }

    private void buildTurtleCards(int left, int top) {
        for (int i = 0; i < data.turtles().size(); i++) {
            int index = i;
            TurtleCardButton button = new TurtleCardButton(left + 8, top + 44 + i * 24,
                    data.turtles().get(i), i == selected, ignored -> {
                selected = index;
                companionDexOpen=false;
                rerollChoice=0;
                timeTrialLeagueChoice=-1;
                rebuild();
            });
            addRenderableWidget(button);
        }
        if (page > 0) addRenderableWidget(turtleButton(left + 8, top + 238, 18, 18,
                Component.literal("<"), ignored -> requestPage(page-1)).withKind(YoikoButton.Kind.PREVIOUS));
        if (page+1<data.turtlePages()) addRenderableWidget(turtleButton(left + 166, top + 238,
                18, 18, Component.literal(">"), ignored -> requestPage(page+1)).withKind(YoikoButton.Kind.NEXT));
    }

    private void requestPage(int requested){
        PacketDistributor.sendToServer(ClientMenuSession.turtleAction("TURTLE_PAGE",EMPTY_ID,requested+"|"+tab.name(),0));
    }

    private void buildInfoActions(OpenTurtleMenuPayload.Card card, int x, int y) {
        if(card==null)return;
        lockButton=actionButton(card.locked()?"◆":"◇",x+132,y+2,20,"LOCK",card,"");
        addRenderableWidget(lockButton);
        String releaseAction=card.releasePending()?"RELEASE_CONFIRM":"RELEASE_PREPARE";
        releaseButton=actionButton(card.releasePending()?"!":"×",x+156,y+2,20,releaseAction,card,"");
        releaseButton.active=!card.locked();
        addRenderableWidget(releaseButton);
    }

    private void buildGrowthActions(OpenTurtleMenuPayload.Card card, int x, int y) {
        if(card==null)return;
        if(companionDexOpen){
            companionDexButton=turtleButton(x+132,y+2,44,18,Component.translatable("yoiko_core.turtle.ui.companion_dex.back"),ignored->{companionDexOpen=false;rebuild();});
            addRenderableWidget(companionDexButton);
            return;
        }
        if(growthLocked(card)){
            companionDexButton=turtleButton(x+132,y+167,44,20,Component.translatable("yoiko_core.turtle.ui.companion_dex.short"),ignored->{companionDexOpen=true;rebuild();});
            addRenderableWidget(companionDexButton);
            return;
        }
        trainingResetButton=actionButton("↺",x+132,y+2,20,"TRAINING_RESET",card,"");
        trainingResetButton.active=card.training()>0&&data.trainingResetTickets()>0;
        addRenderableWidget(trainingResetButton);
        awakenButton=turtleButton(x+156,y+2,20,18,Component.translatable("yoiko_core.turtle.ui.awaken_short"),ignored->
                PacketDistributor.sendToServer(ClientMenuSession.turtleAction("MEDAL_AWAKEN",card.id(),"",card.revision())))
                .withIcon(ICON_AWAKENING,true);
        awakenButton.active=data.medals()>=100&&card.awakening()<30;
        addRenderableWidget(awakenButton);
        for(int i=0;i<TRAINING_IDS.length;i++){
            if(!card.trainableTrainings().contains(TRAINING_IDS[i]))continue;
            int stat=i;
            YoikoButton button=turtleButton(x+153,y+GROWTH_STAT_Y+i*GROWTH_STAT_ROW_HEIGHT,23,13,
                    Component.literal(card.recommendedTraining().equals(TRAINING_IDS[i])?"+★":"+"),ignored->PacketDistributor.sendToServer(
                            ClientMenuSession.turtleAction("TRAIN",card.id(),TRAINING_IDS[stat],card.revision())));
            button.active=data.training()>0;
            if(card.recommendedTraining().equals(TRAINING_IDS[i]))button.withAccent(0xD1A14A);
            trainingButtons.add(button);trainingButtonStats.put(button,i);addRenderableWidget(button);
        }
        for(int slot=0;slot<Math.min(4,card.passives().size());slot++){
            int value=slot,column=slot%2,row=slot/2;
            boolean unlocked=slot<activeSlots(card.awakening());
            Component passiveName=skillName("passive",card.passives().get(slot));
            Component label=Component.literal((unlocked?"◆ ":"◇ ")+(SPECIAL_PASSIVES.contains(card.passives().get(slot))?"★ ":"")+passiveName.getString());
            YoikoButton button=turtleButton(x+column*(TURTLE_PASSIVE_COLUMN_WIDTH+2),y+GROWTH_PASSIVE_Y+row*TURTLE_PASSIVE_ROW_GAP,
                    TURTLE_PASSIVE_COLUMN_WIDTH,18,Component.literal(font.plainSubstrByWidth(label.getString(),TURTLE_PASSIVE_COLUMN_WIDTH-8)),ignored->{rerollChoice=value;rebuild();});
            if(slot==rerollChoice)button.withAccent(SPECIAL_PASSIVES.contains(card.passives().get(slot))?0xB46CFF:0xD1A14A);
            else if(SPECIAL_PASSIVES.contains(card.passives().get(slot)))button.withAccent(0x8C52C9);
            button.active=true;
            passiveButtons.add(button);addRenderableWidget(button);
        }
        rerollInfoButton=turtleButton(x+130,y+GROWTH_PASSIVE_Y,46,18,Component.literal("?"),ignored->
                PacketDistributor.sendToServer(ClientMenuSession.turtleAction("REROLL_CANDIDATES",card.id(),Integer.toString(rerollChoice),card.revision())));
        addRenderableWidget(rerollInfoButton);
        rerollExecuteButton=turtleButton(x+130,y+GROWTH_PASSIVE_Y+TURTLE_PASSIVE_ROW_GAP,46,18,Component.translatable("yoiko_core.turtle.ui.reroll_short"),ignored->
                PacketDistributor.sendToServer(ClientMenuSession.turtleAction("REROLL",card.id(),Integer.toString(rerollChoice),card.revision())));
        rerollExecuteButton.active=card.rerollCredits()>0&&card.rerollsUsed()<4;
        addRenderableWidget(rerollExecuteButton);
        for(int i=0;i<STRATEGY_IDS.length;i++){
            String strategy=STRATEGY_IDS[i];
            YoikoButton button=turtleButton(x+i*45,y+146,41,18,strategyShortName(strategy),ignored->
                    PacketDistributor.sendToServer(ClientMenuSession.turtleAction("STRATEGY",card.id(),strategy,card.revision())));
            if(strategy.equals(card.strategy()))button.withAccent(0x4AB7A8);
            strategyButtons.add(button);
            addRenderableWidget(button);
        }
        String companion=nextCompanion(card.companion());
        companionButton=turtleButton(x,y+167,140,20,Component.literal(companionName(card.companion())),ignored->
                PacketDistributor.sendToServer(ClientMenuSession.turtleAction("COMPANION",card.id(),companion,card.revision())))
                .withIcon(ICON_COMPANION,false);
        addRenderableWidget(companionButton);
        companionDexButton=turtleButton(x+144,y+167,32,20,Component.translatable("yoiko_core.turtle.ui.companion_dex.short"),ignored->{companionDexOpen=true;rebuild();});
        addRenderableWidget(companionDexButton);
    }

    private void buildAppearanceActions(OpenTurtleMenuPayload.Card card,int x,int y){
        if(card==null)return;
        int first=appearanceScrollRow*APPEARANCE_COLUMNS;
        int end=Math.min(appearanceViews.size(),first+APPEARANCE_COLUMNS*APPEARANCE_VISIBLE_ROWS);
        for(int i=first;i<end;i++){
            AppearanceView appearance=appearanceViews.get(i);int visible=i-first,column=visible%APPEARANCE_COLUMNS,row=visible/APPEARANCE_COLUMNS;
            AppearanceButton button=new AppearanceButton(x+column*25,y+35+row*25,24,24,appearance,
                    appearance.id().equals(card.appearance()),ignored->appearanceLeftClick(appearance,card));
            updateAppearanceButtonState(button);
            appearanceButtons.add(button);addRenderableWidget(button);
        }
    }

    private void appearanceLeftClick(AppearanceView appearance,OpenTurtleMenuPayload.Card card){
        if(pendingAppearanceId!=null)return;
        if(appearance.unlocked()){
            sendAppearanceRequest("APPEARANCE",appearance.id(),card);
        }else if(appearance.unlockKind().equals("GEM")){
            appearanceConfirmId=appearance.id();
            rebuild();
        }
    }

    private void buildAppearanceConfirm(OpenTurtleMenuPayload.Card card,int right,int top){
        for(var renderable:renderables)if(renderable instanceof net.minecraft.client.gui.components.AbstractWidget widget)widget.active=false;
        AppearanceView appearance=appearanceById(appearanceConfirmId);
        if(appearance==null){appearanceConfirmId=null;return;}
        int x=right+22,y=top+130;
        Button cancel=turtleButton(x,y,68,20,Component.translatable("yoiko_core.turtle.appearance.purchase.cancel"),ignored->{appearanceConfirmId=null;rebuild();}).withKind(YoikoButton.Kind.CANCEL);
        Button confirm=turtleButton(x+76,y,68,20,Component.translatable("yoiko_core.turtle.appearance.purchase.confirm"),ignored->{
            appearanceConfirmId=null;
            sendAppearanceRequest("APPEARANCE_UNLOCK",appearance.id(),card);
        }).withKind(YoikoButton.Kind.CONFIRM).withAccent(0xD1A14A);
        appearanceModalButtons.add(cancel);appearanceModalButtons.add(confirm);
        addRenderableWidget(cancel);addRenderableWidget(confirm);
    }

    private void sendAppearanceRequest(String action,String id,OpenTurtleMenuPayload.Card card){
        pendingAppearanceId=id;pendingAppearanceAction=action;
        PacketDistributor.sendToServer(ClientMenuSession.turtleAction(action,card.id(),id,card.revision()));
        rebuild();
    }

    private void buildRerollChoice(int right,int top,OpenTurtleMenuPayload.RerollOffer offer){
        for(var renderable:renderables)if(renderable instanceof net.minecraft.client.gui.components.AbstractWidget widget)widget.active=false;
        int x=right+18,y=top+214;
        Button keep=turtleButton(x,y,72,20,Component.translatable("yoiko_core.turtle.reroll.keep"),ignored->sendRerollChoice("REROLL_KEEP",offer));
        Button apply=turtleButton(x+80,y,72,20,Component.translatable("yoiko_core.turtle.reroll.apply"),ignored->sendRerollChoice("REROLL_APPLY",offer)).withAccent(0x4AB7A8);
        rerollModalButtons.add(keep);rerollModalButtons.add(apply);addRenderableWidget(keep);addRenderableWidget(apply);
    }

    private void buildRerollCandidates(int right,int top,OpenTurtleMenuPayload.RerollCandidates preview){
        for(var renderable:renderables)if(renderable instanceof net.minecraft.client.gui.components.AbstractWidget widget)widget.active=false;
        int pages=Math.max(1,(preview.candidates().size()+19)/20);
        rerollCandidatePage=Math.max(0,Math.min(rerollCandidatePage,pages-1));
        Button close=turtleButton(right+158,top+42,18,18,Component.literal("×"),ignored->
                PacketDistributor.sendToServer(ClientMenuSession.turtleAction("REROLL_CANDIDATES_CLOSE",preview.turtleId(),"",preview.turtleRevision())));
        close.active=true;rerollModalButtons.add(close);addRenderableWidget(close);
        if(rerollCandidatePage>0){
            Button previous=turtleButton(right+18,top+218,18,18,Component.literal("<"),ignored->{rerollCandidatePage--;rebuild();}).withKind(YoikoButton.Kind.PREVIOUS);
            previous.active=true;rerollModalButtons.add(previous);addRenderableWidget(previous);
        }
        if(rerollCandidatePage+1<pages){
            Button next=turtleButton(right+156,top+218,18,18,Component.literal(">"),ignored->{rerollCandidatePage++;rebuild();}).withKind(YoikoButton.Kind.NEXT);
            next.active=true;rerollModalButtons.add(next);addRenderableWidget(next);
        }
    }

    private void sendRerollChoice(String action,OpenTurtleMenuPayload.RerollOffer offer){
        rerollModalButtons.forEach(button->button.active=false);
        PacketDistributor.sendToServer(ClientMenuSession.turtleAction(action,offer.turtleId(),offer.candidatePassive(),offer.turtleRevision()));
    }

    private void buildRaceActions(OpenTurtleMenuPayload.Card card, int x, int y) {
        registerButton=null;
        var competition=raceStatus.competition();
        boolean officialActive=competition.active();
        if(raceStatus.timeTrial().engaged()){
            addRenderableWidget(actionButton(tr("yoiko_core.turtle.ui.time_trial_cancel"),x,y+113,176,
                    "TIME_TRIAL_CANCEL",null,""));
            return;
        }
        if(officialActive&&card!=null){
            boolean registrationOpen="REGISTRATION_OPEN".equals(competition.phase());
            boolean leagueMatches=competition.league().equals(card.league());
            String label=competition.playerRegistered()?(registrationOpen?tr("yoiko_core.turtle.ui.cancel_registration"):tr("yoiko_core.turtle.ui.registration.confirmed"))
                    :competition.league().isBlank()?tr("yoiko_core.turtle.ui.registration.none")
                    :!registrationOpen?tr("yoiko_core.turtle.ui.registration.closed")
                    :!leagueMatches?tr("yoiko_core.turtle.ui.registration.requires",leagueName(competition.league()))
                    :tr("yoiko_core.turtle.ui.register",forecastGrade(card));
            String action=competition.playerRegistered()?"UNREGISTER":"REGISTER";
            registerButton=actionButton(label,x,y+51,176,action,competition.playerRegistered()?null:card,"");
            registerButton.active=registrationOpen&&(competition.playerRegistered()||leagueMatches);
            addRenderableWidget(registerButton);
        }

        List<OpenTurtleMenuPayload.BetCandidate> candidates=currentBetCandidates();
        if(officialActive&&!candidates.isEmpty()){
            boolean running="RUNNING".equals(competition.phase());
            Button previousHeat=turtleButton(x,y+79,18,18,Component.literal("<"),ignored->{betHeat=Math.floorMod(betHeat-1,heatCount());betOption=0;rebuild();}).withKind(YoikoButton.Kind.PREVIOUS);
            Button nextHeat=turtleButton(x+158,y+79,18,18,Component.literal(">"),ignored->{betHeat=(betHeat+1)%heatCount();betOption=0;rebuild();}).withKind(YoikoButton.Kind.NEXT);
            previousHeat.active=!running;nextHeat.active=!running;addRenderableWidget(previousHeat);addRenderableWidget(nextHeat);
            for(int i=0;i<candidates.size();i++){
                int index=i,column=i%4,row=i/4;OpenTurtleMenuPayload.BetCandidate candidate=candidates.get(i);
                BetCandidateButton button=new BetCandidateButton(x+column*44,y+100+row*21,41,19,candidate,i==betOption,ignored->{betOption=index;rebuild();});
                betCandidateButtons.add(button);addRenderableWidget(button);
            }
            boolean canBet="BETTING_OPEN".equals(competition.phase())&&!competition.playerRegistered(),canCheer=running;
            OpenTurtleMenuPayload.BetCandidate selectedCandidate=selectedBetCandidate();
            boolean canPlaceBet=canBet&&selectedCandidate!=null&&canAddBet(selectedCandidate);
            for(int i=0;i<BET_AMOUNTS.length;i++){
                int amount=i;YoikoButton button=turtleButton(x+i*44,y+142,40,18,Component.literal(formatBetAmount(BET_AMOUNTS[i])),ignored->{betAmountIndex=amount;rebuild();});
                if(i==betAmountIndex)button.withAccent(0xD1A14A);button.active=canPlaceBet&&canStake(selectedCandidate,BET_AMOUNTS[i]);addRenderableWidget(button);
            }
            Button bet=turtleButton(x,y+163,84,20,Component.translatable("yoiko_core.turtle.ui.bet"),ignored->{OpenTurtleMenuPayload.BetCandidate candidate=selectedBetCandidate();if(candidate!=null)PacketDistributor.sendToServer(ClientMenuSession.turtleAction("BET",EMPTY_ID,candidate.heat()+"|"+candidate.entryId()+"|"+BET_AMOUNTS[betAmountIndex],0));}).withAccent(0xD1A14A);
            bet.active=canPlaceBet&&canStake(selectedCandidate,BET_AMOUNTS[betAmountIndex]);addRenderableWidget(bet);
            Button cheer=turtleButton(x+92,y+163,84,20,Component.translatable("yoiko_core.turtle.ui.cheer"),ignored->{OpenTurtleMenuPayload.BetCandidate candidate=selectedBetCandidate();if(candidate!=null)PacketDistributor.sendToServer(ClientMenuSession.turtleAction("CHEER",EMPTY_ID,candidate.entryId(),0));});
            cheer.active=canCheer;addRenderableWidget(cheer);
            return;
        }
        if(officialActive||card==null)return;
        if(timeTrialLeagueChoice<0)timeTrialLeagueChoice=Math.max(0,Math.min(4,"DCBAS".indexOf(card.raceClass())));
        String[] labels={"D","C","B","A","S"};
        for(int i=0;i<labels.length;i++){
            int choice=i;
            YoikoButton button=turtleButton(x+i*35,y+83,32,20,Component.literal(labels[i]),ignored->{timeTrialLeagueChoice=choice;rebuild();});
            if(i==timeTrialLeagueChoice)button.withAccent(0x4AB7A8);
            addRenderableWidget(button);
        }
        Button timeTrialStartButton=actionButton(tr("yoiko_core.turtle.ui.time_trial_start"),x,y+107,176,"TIME_TRIAL",card,TIME_TRIAL_LEAGUES[timeTrialLeagueChoice]);
        timeTrialStartButton.active=true;
        addRenderableWidget(timeTrialStartButton);
    }

    private Button actionButton(String label, int x, int y, int buttonWidth, String action,
                                OpenTurtleMenuPayload.Card card, String value) {
        return turtleButton(x, y, buttonWidth, 20, Component.literal(label), ignored ->
                PacketDistributor.sendToServer(ClientMenuSession.turtleAction(action,
                        card == null ? EMPTY_ID : card.id(), value, card == null ? 0 : card.revision())));
    }

    private static YoikoButton turtleButton(int x,int y,int width,int height,Component message,Button.OnPress onPress){
        return YoikoButton.create(x,y,width,height,message,onPress).withParchmentStyle().withoutTextShadow();
    }

    private void sendNavigation(String action) {
        YoikoMousePosition.remember();
        PacketDistributor.sendToServer(ClientMenuSession.action(action));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YoikoScreenStyle.renderBackdrop(graphics, width, height);
        int left = YoikoMenuLayout.splitLeft(width);
        int top = YoikoMenuLayout.splitTop(height);
        int right = left + YoikoMenuLayout.SPLIT_RIGHT_PANEL_X;
        YoikoScreenStyle.renderTurtleRacingLeftPanel(graphics, left, top);
        YoikoScreenStyle.renderTurtleRacingRightPanel(graphics, right, top);
        graphics.drawString(font, Component.translatable("yoiko_core.turtle.ui.owned",data.owned(),data.maxOwned()), left + 10, top + 10,
                0xFF5E4B35, false);
        if (data.turtles().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("yoiko_core.turtle.ui.empty"), left + 96, top + 118,
                    PARCHMENT_MUTED);
        }
        drawResourceStrip(graphics, right + 8, top + 35);
        renderTabContent(graphics, right + 10, top + 53, mouseX, mouseY);

        YoikoScreenStyle.renderWidgets(this, graphics, mouseX, mouseY, partialTick);
        if(data.rerollOffer()!=null){
            renderRerollChoice(graphics,right,top,data.rerollOffer(),mouseX,mouseY,partialTick);
            return;
        }
        if(data.rerollCandidates()!=null){
            renderRerollCandidates(graphics,right,top,data.rerollCandidates(),mouseX,mouseY,partialTick);
            return;
        }
        if(appearanceConfirmId!=null){
            renderAppearancePurchaseModal(graphics,right,top,mouseX,mouseY,partialTick);
            return;
        }
        for (ModeButton button : modeButtons) {
            if (button.isHoveredOrFocused()) {
                graphics.renderTooltip(font, button.getMessage(), mouseX, mouseY);
                return;
            }
        }
        for(var renderable:renderables)if(renderable instanceof TurtleCardButton button&&button.isHoveredOrFocused()){
            graphics.renderComponentTooltip(font,button.tooltip(),mouseX,mouseY);return;
        }
        for(BetCandidateButton button:betCandidateButtons)if(button.isHoveredOrFocused()){
            graphics.renderComponentTooltip(font,button.tooltip(),mouseX,mouseY);return;
        }
        if(renderCompanionDexTooltip(graphics,mouseX,mouseY,right+10,top+53))return;
        if(renderCareerTooltip(graphics,mouseX,mouseY,right+10,top+53))return;
        if(renderRaceClassProgressTooltip(graphics,mouseX,mouseY,right+10,top+53))return;
        if(renderStatTooltip(graphics,mouseX,mouseY,right+10,top+53))return;
        if (renderSkillTooltip(graphics, mouseX, mouseY, right + 10, top + 53)) return;
        if(renderBodyColorTooltip(graphics,mouseX,mouseY,right+10,top+53))return;
        if(renderInfoIconTooltip(graphics,mouseX,mouseY,right,top))return;
        for(AppearanceButton button:appearanceButtons)if(button.isHoveredOrFocused()){
            graphics.renderComponentTooltip(font,button.tooltip(),mouseX,mouseY);return;
        }
        if(tab==Tab.GROWTH&&!companionDexOpen)for(Button trainingButton:trainingButtons)if(trainingButton.isHoveredOrFocused()){
            int i=trainingButtonStats.getOrDefault(trainingButton,0);
            String[][] stats={{"speed","power"},{"stamina","calm"},{"power","navigation"},{"calm","stamina"},{"navigation","speed"}};
            List<Component> tooltip=new ArrayList<>();
            tooltip.add(Component.translatable("yoiko_core.turtle.ui.training.gain.major",Component.translatable("yoiko_core.turtle.stat."+stats[i][0])).withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("yoiko_core.turtle.ui.training.gain.minor",Component.translatable("yoiko_core.turtle.stat."+stats[i][1])).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(Screen.hasShiftDown()?"yoiko_core.turtle.ui.radar.projected":"yoiko_core.turtle.ui.training.preview")
                    .withStyle(ChatFormatting.GOLD));
            if(cardIsRecommended(selectedCard(),TRAINING_IDS[i]))tooltip.add(Component.translatable("yoiko_core.turtle.ui.training.recommend_reason").withStyle(ChatFormatting.YELLOW));
            OpenTurtleMenuPayload.Card card=selectedCard();if(card!=null)tooltip.add(Component.literal(raceProgressText(card)).withStyle(ChatFormatting.DARK_GRAY));graphics.renderComponentTooltip(font,tooltip,mouseX,mouseY);return;
        }
        if(tab==Tab.GROWTH&&rerollInfoButton!=null&&rerollInfoButton.isHoveredOrFocused()){
            graphics.renderComponentTooltip(font,List.of(Component.translatable("yoiko_core.turtle.reroll.candidates.help").withStyle(ChatFormatting.AQUA),Component.translatable("yoiko_core.turtle.reroll.candidates.rule").withStyle(ChatFormatting.GRAY)),mouseX,mouseY);return;
        }
        if(tab==Tab.GROWTH&&rerollExecuteButton!=null&&rerollExecuteButton.isHoveredOrFocused()){
            OpenTurtleMenuPayload.Card card=selectedCard();List<Component> tooltip=new ArrayList<>();
            tooltip.add(Component.translatable("yoiko_core.turtle.ui.reroll_execute").withStyle(ChatFormatting.AQUA));
            if(card!=null)tooltip.add(Component.translatable("yoiko_core.turtle.reroll.credit_status",card.rerollCredits(),card.rerollsUsed(),4).withStyle(card.rerollCredits()>0?ChatFormatting.GRAY:ChatFormatting.RED));
            graphics.renderComponentTooltip(font,tooltip,mouseX,mouseY);return;
        }
        if(tab==Tab.GROWTH)for(int i=0;i<strategyButtons.size();i++)if(strategyButtons.get(i).isHoveredOrFocused()){
            OpenTurtleMenuPayload.Card card=selectedCard();String strategy=STRATEGY_IDS[i];List<Component> tooltip=new ArrayList<>(strategyTooltip(strategy));if(card!=null){tooltip.add(Component.literal(strategyForecast(card,strategy)).withStyle(ChatFormatting.YELLOW));tooltip.add(Component.translatable(strategy.equals(card.strategy())?"yoiko_core.turtle.ui.strategy.current":"yoiko_core.turtle.ui.strategy.change_hint").withStyle(ChatFormatting.DARK_GRAY));}graphics.renderComponentTooltip(font,wrapTooltip(tooltip,190),mouseX,mouseY);return;
        }
        if(tab==Tab.GROWTH&&companionButton!=null&&companionButton.isHoveredOrFocused()){
            OpenTurtleMenuPayload.Card card=selectedCard();if(card!=null){String id=card.companion().toLowerCase(Locale.ROOT);List<Component> tooltip=new ArrayList<>();if(card.companion().equals("NONE"))tooltip.add(Component.translatable("yoiko_core.turtle.companion.none.description").withStyle(ChatFormatting.GRAY));else{tooltip.add(Component.translatable("yoiko_core.turtle.companion."+id+".name").withStyle(ChatFormatting.AQUA));tooltip.addAll(effectDetails("companion",id));tooltip.add(Component.translatable("yoiko_core.turtle.companion."+id+".unlock").withStyle(ChatFormatting.DARK_GRAY));}graphics.renderComponentTooltip(font,wrapTooltip(tooltip,190),mouseX,mouseY);return;}
        }
        if(tab==Tab.GROWTH&&trainingResetButton!=null&&trainingResetButton.isHoveredOrFocused()){
            OpenTurtleMenuPayload.Card card=selectedCard();
            List<Component> tooltip=new ArrayList<>();
            tooltip.add(Component.translatable("yoiko_core.turtle.ui.training_reset.title").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("yoiko_core.turtle.ui.training_reset.detail").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("yoiko_core.turtle.ui.training_reset.owned",data.trainingResetTickets()).withStyle(data.trainingResetTickets()>0?ChatFormatting.YELLOW:ChatFormatting.RED));
            if(card!=null&&card.training()==0)tooltip.add(Component.translatable("yoiko_core.turtle.ui.training_reset.empty").withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(font,wrapTooltip(tooltip,200),mouseX,mouseY);return;
        }
        if(tab==Tab.GROWTH&&awakenButton!=null&&awakenButton.isHoveredOrFocused()){
            OpenTurtleMenuPayload.Card card=selectedCard();
            if(card!=null)graphics.renderComponentTooltip(font,List.of(
                    Component.translatable("yoiko_core.turtle.ui.awakening_progress",card.awakening()).withStyle(ChatFormatting.AQUA),
                    Component.translatable("yoiko_core.turtle.ui.resource.shell",data.medals()).withStyle(ChatFormatting.GRAY)),mouseX,mouseY);
            return;
        }
        if(tab==Tab.INFO&&lockButton!=null&&lockButton.isHoveredOrFocused()){
            OpenTurtleMenuPayload.Card card=selectedCard();if(card!=null)graphics.renderTooltip(font,Component.translatable(card.locked()?"yoiko_core.turtle.ui.unlock":"yoiko_core.turtle.ui.lock"),mouseX,mouseY);return;
        }
        if(tab==Tab.INFO&&releaseButton!=null&&releaseButton.isHoveredOrFocused()){
            OpenTurtleMenuPayload.Card card=selectedCard();if(card!=null)graphics.renderTooltip(font,Component.translatable(card.releasePending()?"yoiko_core.turtle.ui.release_confirm":"yoiko_core.turtle.ui.release_prepare"),mouseX,mouseY);return;
        }
        if(tab==Tab.RACE&&registerButton!=null&&registerButton.isHoveredOrFocused()){
            OpenTurtleMenuPayload.Card card=selectedCard();List<Component> tooltip=new ArrayList<>();
            if(raceStatus.competition().playerRegistered())tooltip.add(Component.translatable("REGISTRATION_OPEN".equals(raceStatus.competition().phase())?"yoiko_core.turtle.ui.registration.cancel_hint":"yoiko_core.turtle.ui.registration.confirmed_hint").withStyle(ChatFormatting.YELLOW));
            else if(!raceStatus.competition().active())tooltip.add(Component.translatable("yoiko_core.turtle.ui.registration.none_hint").withStyle(ChatFormatting.GRAY));
            else if(!"REGISTRATION_OPEN".equals(raceStatus.competition().phase()))tooltip.add(Component.translatable("yoiko_core.turtle.ui.registration.phase_hint").withStyle(ChatFormatting.GRAY));
            else if(card!=null&&!raceStatus.competition().league().equals(card.league())){
                tooltip.add(Component.translatable("yoiko_core.turtle.ui.registration.mismatch",leagueName(raceStatus.competition().league())).withStyle(ChatFormatting.RED));
                tooltip.add(Component.translatable("yoiko_core.turtle.ui.registration.selected",leagueName(card.league()),card.total()).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal(raceProgressText(card)).withStyle(ChatFormatting.DARK_GRAY));
            }else if(card!=null){tooltip.add(Component.translatable("yoiko_core.turtle.ui.league.available",leagueName(card.league())).withStyle(ChatFormatting.GREEN));tooltip.add(Component.literal(currentStrategyForecast(card)).withStyle(ChatFormatting.YELLOW));tooltip.add(Component.translatable("yoiko_core.turtle.ui.strategy.no_direct_stats").withStyle(ChatFormatting.DARK_GRAY));}
            graphics.renderComponentTooltip(font,wrapTooltip(tooltip,190),mouseX,mouseY);return;
        }
        YoikoNavigationTabs.renderTooltip(graphics, font, navigationButtons, mouseX, mouseY);
    }

    private void renderRerollChoice(GuiGraphics graphics,int right,int top,OpenTurtleMenuPayload.RerollOffer offer,int mouseX,int mouseY,float partialTick){
        YoikoScreenStyle.renderTurtleDialogPanel(graphics,right+6,top+33);
        graphics.drawCenteredString(font,Component.translatable("yoiko_core.turtle.reroll.title",offer.slot()+1),right+96,top+44,PARCHMENT_GOLD);
        graphics.drawCenteredString(font,Component.translatable("yoiko_core.turtle.reroll.choice_hint"),right+96,top+58,PARCHMENT_MUTED);
        renderRerollPassiveCard(graphics,right+12,top+75,offer.currentPassive(),"yoiko_core.turtle.reroll.current",0xFF846B53);
        renderRerollPassiveCard(graphics,right+12,top+137,offer.candidatePassive(),"yoiko_core.turtle.reroll.candidate",PARCHMENT_TEAL);
        graphics.drawCenteredString(font,Component.translatable("yoiko_core.turtle.reroll.credit_spent"),right+96,top+198,PARCHMENT_MUTED);
        for(Button button:rerollModalButtons)button.render(graphics,mouseX,mouseY,partialTick);
    }

    private void renderRerollPassiveCard(GuiGraphics graphics,int x,int y,String passive,String labelKey,int accent){
        graphics.fill(x,y,x+168,y+52,0x26D4B982);
        graphics.fill(x,y,x+3,y+52,accent);
        graphics.drawString(font,Component.translatable(labelKey),x+8,y+5,PARCHMENT_MUTED,false);
        graphics.drawString(font,font.plainSubstrByWidth(skillName("passive",passive).getString(),150),x+8,y+17,accent,false);
        Component description=styledEffectDescription("passive",passive);
        drawWrappedComponent(graphics,description,x+8,y+30,150,2);
    }

    private void renderRerollCandidates(GuiGraphics graphics,int right,int top,OpenTurtleMenuPayload.RerollCandidates preview,int mouseX,int mouseY,float partialTick){
        YoikoScreenStyle.renderTurtleDialogPanel(graphics,right+6,top+33);
        graphics.drawString(font,Component.translatable("yoiko_core.turtle.reroll.candidates.title",preview.slot()+1),right+12,top+44,PARCHMENT_GOLD,false);
        graphics.drawString(font,Component.translatable("yoiko_core.turtle.reroll.candidates.count",preview.candidates().size()),right+12,top+57,PARCHMENT_MUTED,false);
        int start=rerollCandidatePage*20,end=Math.min(preview.candidates().size(),start+20);
        for(int i=start;i<end;i++){
            int visible=i-start,column=visible%2,row=visible/2;
            String id=preview.candidates().get(i);
            int px=right+12+column*86,py=top+74+row*14;
            boolean special=SPECIAL_PASSIVES.contains(id);
            if(special)graphics.fill(px-2,py-1,px+82,py+11,0x26C79ACF);
            String label=(special?"★ ":"")+skillName("passive",id).getString();
            graphics.drawString(font,font.plainSubstrByWidth(label,80),px,py,special?0xFF8F55A8:PARCHMENT_TEXT,false);
        }
        int pages=Math.max(1,(preview.candidates().size()+19)/20);
        graphics.drawCenteredString(font,Component.translatable("yoiko_core.turtle.reroll.candidates.page",rerollCandidatePage+1,pages),right+96,top+224,PARCHMENT_MUTED);
        for(Button button:rerollModalButtons)button.render(graphics,mouseX,mouseY,partialTick);
        for(int i=start;i<end;i++){
            int visible=i-start,column=visible%2,row=visible/2,px=right+12+column*86,py=top+74+row*14;
            if(mouseX<px||mouseX>=px+82||mouseY<py-1||mouseY>=py+11)continue;
            String id=preview.candidates().get(i);boolean special=SPECIAL_PASSIVES.contains(id);
            List<Component> tooltip=new ArrayList<>();
            tooltip.add(skillName("passive",id).copy().withStyle(special?ChatFormatting.LIGHT_PURPLE:ChatFormatting.WHITE));
            tooltip.addAll(effectDetails("passive",id));
            if(special)tooltip.add(Component.translatable("yoiko_core.turtle.skill.kind.passive_special").withStyle(ChatFormatting.LIGHT_PURPLE,ChatFormatting.BOLD));
            graphics.renderComponentTooltip(font,wrapTooltip(tooltip,190),mouseX,mouseY);break;
        }
    }

    private void renderTabContent(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        OpenTurtleMenuPayload.Card card = selectedCard();
        switch (tab) {
            case INFO -> {
                if (card == null) return;
                drawCard(graphics,x,y,CONTENT_WIDTH,188);
                Component turtleName=TurtleNameCatalog.component(card.name());
                graphics.drawString(font,font.plainSubstrByWidth(turtleName.getString(),100),x+6,y+5,PARCHMENT_TEXT,false);
                if(TurtleBodyAppearanceCatalog.isRare(card.bodyAppearance())){
                    graphics.renderItem(Items.NETHER_STAR.getDefaultInstance(),x+112,y+1);
                }
                graphics.drawString(font,Component.translatable("yoiko_core.turtle.ui.class_points",card.raceClass(),card.racePoints()),x+6,y+18,PARCHMENT_MUTED,false);
                Component activeName=skillName("active",card.active());
                drawInfoIcon(graphics,ICON_ACTIVE,x+3,y+INFO_ACTIVE_Y-3,16);
                graphics.drawString(font,font.plainSubstrByWidth(activeName.getString(),145),x+22,y+INFO_ACTIVE_Y,PARCHMENT_TEAL,false);
                int hoveredStat=hoveredStatIndex(mouseX,mouseY,x,y);
                renderStatRadar(graphics,card,x+RADAR_CENTER_X,y+INFO_RADAR_CENTER_Y,RADAR_RADIUS,-1,hoveredStat,true);
                String[] statLabels={tr("yoiko_core.turtle.stat.speed"),tr("yoiko_core.turtle.stat.stamina"),tr("yoiko_core.turtle.stat.power"),tr("yoiko_core.turtle.stat.calm"),tr("yoiko_core.turtle.stat.navigation")};
                for(int i=0;i<5;i++){
                    int rowY=y+INFO_STAT_Y+i*INFO_STAT_ROW_HEIGHT;
                    graphics.fill(x+STAT_ROW_X,rowY-1,x+174,rowY+13,i==hoveredStat?0x38F0B448:(i&1)==0?0x18D2B77F:0x0EC8AA70);
                    Component statLabel=Component.literal(statLabels[i]);
                    graphics.drawString(font,font.plainSubstrByWidth(statLabel.getString(),58),x+STAT_ROW_X+2,rowY+2,i==hoveredStat?PARCHMENT_GOLD:PARCHMENT_TEXT,false);
                    String value=card.stats().get(i)+"  "+card.statGrades().get(i);
                    drawRightAligned(graphics,value,x+169,rowY+2,statGradeColor(card.statGrades().get(i)));
                }
                drawInfoIcon(graphics,ICON_PASSIVE,x+2,y+118,16);
                graphics.drawString(font,Component.translatable("yoiko_core.turtle.ui.passive.summary"),x+20,y+122,PARCHMENT_TEAL,false);
                for(int i=0;i<Math.min(4,card.passives().size());i++){
                    int px=x+3+(i%2)*86,py=y+INFO_PASSIVE_Y+(i/2)*19;
                    boolean unlocked=i<activeSlots(card.awakening()),special=SPECIAL_PASSIVES.contains(card.passives().get(i));
                    String label=(unlocked?"◆ ":"◇ ")+(special?"★ ":"")+skillName("passive",card.passives().get(i)).getString();
                    graphics.drawString(font,font.plainSubstrByWidth(label,82),px,py,special?0xFF8F55A8:unlocked?PARCHMENT_TEXT:0xFFAA9B88,false);
                }
                graphics.drawString(font,tr("yoiko_core.turtle.ui.career.compact",card.career().rating(),card.career().starts(),card.career().wins()),x+4,y+170,PARCHMENT_GOLD,false);
            }
            case GROWTH -> {
                if(card==null)return;
                drawCard(graphics,x,y,CONTENT_WIDTH,188);
                graphics.drawString(font,font.plainSubstrByWidth(TurtleNameCatalog.component(card.name()).getString(),70),x+6,y+5,PARCHMENT_TEXT,false);
                String trainingProgress=tr("yoiko_core.turtle.icon.training.progress",card.training(),24);
                drawRightAligned(graphics,font.plainSubstrByWidth(trainingProgress,96),x+170,y+5,PARCHMENT_GOLD);
                int trainingBarWidth=92,trainingFilled=Math.min(trainingBarWidth,card.training()*trainingBarWidth/24);
                graphics.fill(x+78,y+17,x+170,y+20,0x66977B58);
                graphics.fill(x+78,y+17,x+78+trainingFilled,y+20,0xFF5E9E91);
                if(companionDexOpen){
                    renderCompanionDex(graphics,x,y);
                    return;
                }
                if(growthLocked(card)){
                    graphics.fill(x+3,y+28,x+173,y+82,0x22C99762);
                    graphics.drawCenteredString(font,Component.translatable("yoiko_core.turtle.ui.growth_registered.title"),x+88,y+39,PARCHMENT_GOLD);
                    drawWrapped(graphics,tr("yoiko_core.turtle.ui.growth_registered.detail"),x+12,y+55,152,3,PARCHMENT_MUTED);
                    return;
                }
                int hoveredTraining=-1;
                for(var entry:trainingButtonStats.entrySet()){
                    if(entry.getKey().isMouseOver(mouseX,mouseY)){
                        hoveredTraining=entry.getValue();
                        break;
                    }
                }
                int hoveredStat=hoveredStatIndex(mouseX,mouseY,x,y);
                int highlightedStat=hoveredStat>=0?hoveredStat:hoveredTraining;
                renderStatRadar(graphics,card,x+RADAR_CENTER_X,y+GROWTH_RADAR_CENTER_Y,RADAR_RADIUS,hoveredTraining,highlightedStat,false);
                String shiftHint=font.plainSubstrByWidth(Component.translatable("yoiko_core.turtle.ui.radar.shift_short").getString(),73);
                graphics.drawString(font,shiftHint,x+STAT_ROW_X+2,y+22,Screen.hasShiftDown()?0xFFF0B448:PARCHMENT_MUTED,false);
                String[] statLabels={tr("yoiko_core.turtle.stat.speed"),tr("yoiko_core.turtle.stat.stamina"),tr("yoiko_core.turtle.stat.power"),tr("yoiko_core.turtle.stat.calm"),tr("yoiko_core.turtle.stat.navigation")};
                for(int i=0;i<5;i++){
                    int rowY=y+GROWTH_STAT_Y+i*GROWTH_STAT_ROW_HEIGHT;
                    graphics.fill(x+STAT_ROW_X,rowY-1,x+151,rowY+13,i==highlightedStat?0x38F0B448:(i&1)==0?0x18D2B77F:0x0EC8AA70);
                    graphics.drawString(font,font.plainSubstrByWidth(statLabels[i],38),x+STAT_ROW_X+2,rowY+2,i==highlightedStat?PARCHMENT_GOLD:PARCHMENT_TEXT,false);
                    drawRightAligned(graphics,card.stats().get(i)+" "+card.statGrades().get(i),x+147,rowY+2,statGradeColor(card.statGrades().get(i)));
                }
                graphics.fill(x+2,y+101,x+174,y+102,0x55B88D55);
                graphics.fill(x+2,y+143,x+174,y+144,0x55B88D55);
                graphics.fill(x+2,y+164,x+174,y+165,0x55B88D55);
            }
            case APPEARANCE -> {
                if(card==null)return;
                drawCard(graphics,x,y,CONTENT_WIDTH,36);
                graphics.drawString(font,TurtleNameCatalog.component(card.name()),x+6,y+5,PARCHMENT_TEXT,false);
                AppearanceView appearance=appearanceById(card.appearance());
                String appearanceLabel=appearanceName(card.appearance());
                drawRightAligned(graphics,appearanceLabel,x+170,y+5,PARCHMENT_TEAL);
                if(appearance!=null&&appearance.favorite()){
                    YoikoScreenStyle.renderFavoriteIcon(graphics,x+170-font.width(appearanceLabel)-18,y+2,18);
                }
                String body=tr("yoiko_core.turtle.body."+card.bodyAppearance());
                graphics.drawString(font,font.plainSubstrByWidth(tr("yoiko_core.turtle.body.current",body),164),x+6,y+18,PARCHMENT_MUTED,false);
                graphics.drawString(font,Component.translatable("yoiko_core.turtle.appearance.section.shell"),x,y+40,PARCHMENT_GOLD,false);
                graphics.drawString(font,Component.translatable("yoiko_core.turtle.appearance.section.body"),x,y+130,PARCHMENT_GOLD,false);
                YoikoScreenStyle.renderSlot(graphics,x,y+144);
                graphics.renderItem(Items.TURTLE_SCUTE.getDefaultInstance(),x+4,y+148);
                boolean rareBody=TurtleBodyAppearanceCatalog.isRare(card.bodyAppearance());
                graphics.drawString(font,body,x+28,y+147,rareBody?PARCHMENT_GOLD:PARCHMENT_TEXT,false);
                graphics.drawString(font,Component.translatable("yoiko_core.turtle.body.innate"),x+28,y+159,PARCHMENT_MUTED,false);
                if(rareBody)graphics.renderItem(Items.NETHER_STAR.getDefaultInstance(),x+154,y+144);
            }
            case RACE -> {
                drawCard(graphics,x,y,CONTENT_WIDTH,28);
                drawWrapped(graphics,competitionStatus(),x+6,y+5,104,2,PARCHMENT_GOLD);
                List<OpenTurtleMenuPayload.BetCandidate> candidates=currentBetCandidates();
                if(raceStatus.competition().active())drawRaceConditionIcons(graphics,x+112,y+6);
                if(card!=null){
                    String summary=TurtleNameCatalog.component(card.name()).getString()+" · "+strategyShortName(card.strategy()).getString()+" · "+companionName(card.companion());
                    graphics.drawString(font,font.plainSubstrByWidth(summary,172),x+2,y+36,PARCHMENT_TEAL,false);
                }
                if(raceStatus.timeTrial().engaged()){
                    List<String> status=timeTrialStatus();
                    graphics.drawString(font,status.get(0),x+6,y+61,PARCHMENT_TEAL,false);
                    graphics.drawString(font,status.get(1),x+6,y+75,PARCHMENT_TEXT,false);
                    graphics.drawString(font,tr("yoiko_core.turtle.ui.time_trial_note"),x+6,y+91,PARCHMENT_MUTED,false);
                }else if(raceStatus.competition().active()&&!candidates.isEmpty()){
                    graphics.drawString(font,tr("yoiko_core.turtle.ui.bet_section"),x,y+74,PARCHMENT_MUTED,false);
                    graphics.drawCenteredString(font,tr("yoiko_core.turtle.ui.heat",betHeat+1),x+88,y+87,PARCHMENT_TEXT);
                }else if(raceStatus.competition().active()){
                    graphics.drawCenteredString(font,tr("yoiko_core.turtle.ui.no_bet_candidates"),x+88,y+91,PARCHMENT_MUTED);
                }else{
                    List<String> status=timeTrialStatus();
                    graphics.drawString(font,status.get(0),x+6,y+58,PARCHMENT_TEAL,false);
                    graphics.drawString(font,tr("yoiko_core.turtle.ui.time_trial_note"),x+6,y+70,PARCHMENT_MUTED,false);
                    String difficulty=TIME_TRIAL_LEAGUES[Math.max(0,timeTrialLeagueChoice)];long best=timeTrialBest(difficulty);
                    graphics.drawString(font,tr("yoiko_core.turtle.ui.time_trial.best",difficulty,best<=0?tr("yoiko_core.turtle.ui.time_trial.no_record"):formatMillis(best)),x+2,y+132,best<=0?PARCHMENT_MUTED:PARCHMENT_TEAL,false);
                    if(card!=null&&card.lastRace()!=null)renderLastRace(graphics,card.lastRace(),x,y+145);
                }
            }
        }
    }

    private void renderCompanionDex(GuiGraphics graphics,int x,int y){
        int unlocked=(int)Arrays.stream(COMPANION_IDS).filter(id->data.companions().contains(id.toUpperCase(Locale.ROOT))).count();
        graphics.drawString(font,Component.translatable("yoiko_core.turtle.ui.companion_dex.title"),x+6,y+27,PARCHMENT_TEAL,false);
        drawRightAligned(graphics,unlocked+"/"+COMPANION_IDS.length,x+169,y+27,PARCHMENT_GOLD);
        graphics.drawString(font,Component.translatable("yoiko_core.turtle.ui.companion_dex.hint"),x+6,y+41,PARCHMENT_MUTED,false);
        for(int i=0;i<COMPANION_IDS.length;i++){
            int column=i%7,row=i/7,slotX=x+3+column*24,slotY=y+58+row*27;
            YoikoScreenStyle.renderSlot(graphics,slotX,slotY);
            boolean isUnlocked=data.companions().contains(COMPANION_IDS[i].toUpperCase(Locale.ROOT));
            graphics.renderItem(companionIcon(COMPANION_IDS[i]).getDefaultInstance(),slotX+1,slotY+1);
            if(!isUnlocked)YoikoScreenStyle.renderSlotMask(graphics,slotX,slotY,18,18,0x15191D,4);
            else graphics.drawString(font,Component.literal("✓").withStyle(ChatFormatting.GREEN),slotX+11,slotY+10,0xFF79E39B,true);
        }
    }

    private boolean renderCompanionDexTooltip(GuiGraphics graphics,int mouseX,int mouseY,int x,int y){
        if(tab!=Tab.GROWTH||!companionDexOpen)return false;
        for(int i=0;i<COMPANION_IDS.length;i++){
            int slotX=x+3+(i%7)*24,slotY=y+58+(i/7)*27;
            if(mouseX<slotX||mouseX>=slotX+18||mouseY<slotY||mouseY>=slotY+18)continue;
            String id=COMPANION_IDS[i];boolean unlocked=data.companions().contains(id.toUpperCase(Locale.ROOT));
            List<Component> tooltip=new ArrayList<>();
            tooltip.add(Component.translatable("yoiko_core.turtle.companion."+id+".name").withStyle(unlocked?ChatFormatting.AQUA:ChatFormatting.GRAY));
            tooltip.addAll(effectDetails("companion",id));
            tooltip.add(Component.translatable("yoiko_core.turtle.companion."+id+".unlock").withStyle(unlocked?ChatFormatting.DARK_GRAY:ChatFormatting.RED));
            tooltip.add(Component.translatable(unlocked?"yoiko_core.turtle.ui.companion_dex.unlocked":"yoiko_core.turtle.ui.companion_dex.locked").withStyle(unlocked?ChatFormatting.GREEN:ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(font,wrapTooltip(tooltip,205),mouseX,mouseY);return true;
        }
        return false;
    }

    private boolean renderCareerTooltip(GuiGraphics graphics,int mouseX,int mouseY,int x,int y){
        if(tab!=Tab.INFO||mouseX<x||mouseX>=x+176||mouseY<y+166||mouseY>=y+188)return false;
        OpenTurtleMenuPayload.Card card=selectedCard();if(card==null)return false;var career=card.career();List<Component> tooltip=new ArrayList<>();
        tooltip.add(Component.translatable("yoiko_core.turtle.ui.career.title").withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD));
        tooltip.add(Component.translatable("yoiko_core.turtle.ui.career.rating",career.rating()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("yoiko_core.turtle.ui.career.record",career.starts(),career.wins(),career.podiums(),career.averageRankTenths()/10.0,career.averageStamina()).withStyle(ChatFormatting.GRAY));
        String recent=career.recentRanks().isEmpty()?"-":career.recentRanks().stream().map(v->tr("yoiko_core.turtle.ui.place",v)).collect(java.util.stream.Collectors.joining(" · "));
        tooltip.add(Component.translatable("yoiko_core.turtle.ui.career.recent",recent).withStyle(ChatFormatting.YELLOW));
        for(int i=0;i<Math.min(4,career.strategyStarts().size());i++)tooltip.add(Component.translatable("yoiko_core.turtle.ui.career.strategy",strategyShortName(STRATEGY_IDS[i]),career.strategyWins().get(i),career.strategyStarts().get(i)).withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font,wrapTooltip(tooltip,220),mouseX,mouseY);return true;
    }

    private static Item companionIcon(String id){
        return switch(id){
            case "dolphin"->Items.DOLPHIN_SPAWN_EGG;
            case "axolotl","blue_axolotl"->Items.AXOLOTL_BUCKET;
            case "frog"->Items.FROG_SPAWN_EGG;
            case "rabbit"->Items.RABBIT_FOOT;
            case "bee"->Items.HONEYCOMB;
            case "parrot"->Items.PARROT_SPAWN_EGG;
            case "wolf"->Items.BONE;
            case "fox"->Items.SWEET_BERRIES;
            case "armadillo"->Items.ARMADILLO_SCUTE;
            case "allay"->Items.AMETHYST_SHARD;
            case "goat"->Items.GOAT_HORN;
            default->Items.SNIFFER_EGG;
        };
    }

    private boolean renderSkillTooltip(GuiGraphics graphics,int mouseX,int mouseY,int x,int y){
        if((tab!=Tab.INFO&&tab!=Tab.GROWTH)||companionDexOpen)return false;OpenTurtleMenuPayload.Card card=selectedCard();if(card==null)return false;
        String type=null,id=null;boolean unlocked=true;
        if(tab==Tab.INFO&&mouseX>=x+2&&mouseX<x+170&&mouseY>=y+INFO_ACTIVE_Y-3&&mouseY<y+INFO_ACTIVE_Y+13){type="active";id=card.active();}
        else for(int i=0;i<Math.min(4,card.passives().size());i++){
            int px=tab==Tab.INFO?x+3+(i%2)*86:x+(i%2)*(TURTLE_PASSIVE_COLUMN_WIDTH+2);
            int py=tab==Tab.INFO?y+INFO_PASSIVE_Y+(i/2)*19:y+GROWTH_PASSIVE_Y+(i/2)*TURTLE_PASSIVE_ROW_GAP;
            int width=tab==Tab.INFO?82:TURTLE_PASSIVE_COLUMN_WIDTH,height=tab==Tab.INFO?11:18;
            if(mouseX>=px&&mouseX<px+width&&mouseY>=py-1&&mouseY<py+height){type="passive";id=card.passives().get(i);unlocked=i<activeSlots(card.awakening());break;}
        }
        if(id==null)return false;
        List<Component> tooltip=new ArrayList<>();
        boolean special=type.equals("passive")&&SPECIAL_PASSIVES.contains(id);
        tooltip.add(skillName(type,id).copy().withStyle(type.equals("active")?ChatFormatting.AQUA:special?ChatFormatting.LIGHT_PURPLE:ChatFormatting.WHITE));
        tooltip.addAll(effectDetails(type,id));
        if(special)tooltip.add(Component.translatable("yoiko_core.turtle.skill.kind.passive_special").withStyle(ChatFormatting.LIGHT_PURPLE,ChatFormatting.BOLD));
        tooltip.add(Component.translatable(type.equals("active")?"yoiko_core.turtle.skill.kind.active":unlocked?"yoiko_core.turtle.skill.kind.passive_active":"yoiko_core.turtle.skill.kind.passive_locked").withStyle(unlocked?ChatFormatting.DARK_GRAY:ChatFormatting.RED));
        graphics.renderComponentTooltip(font,wrapTooltip(tooltip,190),mouseX,mouseY);return true;
    }

    /** Shared five-axis graph. Info compares initial/current stats; growth compares current/future stats. */
    private void renderStatRadar(GuiGraphics graphics,OpenTurtleMenuPayload.Card card,int centerX,int centerY,int radius,
                                 int trainingIndex,int highlightedStat,boolean showInitial){
        boolean initialComparison=showInitial&&card.baseStats().size()==STAT_IDS.length;
        boolean finalProjection=!showInitial&&Screen.hasShiftDown()&&card.projectedStats().size()==STAT_IDS.length;
        int[] currentX=new int[STAT_IDS.length],currentY=new int[STAT_IDS.length];
        int[] previewX=new int[STAT_IDS.length],previewY=new int[STAT_IDS.length];
        for(int i=0;i<STAT_IDS.length;i++){
            double angle=Math.toRadians(-90+i*72);
            int current=Math.max(0,Math.min(100,card.stats().get(i)));
            int preview=current;
            if(initialComparison){
                preview=Math.max(0,Math.min(100,card.baseStats().get(i)));
            }else if(finalProjection){
                preview=Math.max(0,Math.min(100,card.projectedStats().get(i)));
            }else if(trainingIndex>=0){
                preview=Math.min(100,current+TRAINING_GAINS[trainingIndex][i]);
            }
            currentX[i]=centerX+(int)Math.round(Math.cos(angle)*radius*current/100.0);
            currentY[i]=centerY+(int)Math.round(Math.sin(angle)*radius*current/100.0);
            previewX[i]=centerX+(int)Math.round(Math.cos(angle)*radius*preview/100.0);
            previewY[i]=centerY+(int)Math.round(Math.sin(angle)*radius*preview/100.0);
            // A +1 increase is smaller than one pixel at this compact size. Preserve the exact scale,
            // but guarantee one visible outward pixel whenever rounding would otherwise hide the gain.
            if(!finalProjection&&trainingIndex>=0&&TRAINING_GAINS[trainingIndex][i]>0
                    &&previewX[i]==currentX[i]&&previewY[i]==currentY[i]){
                previewX[i]+=(int)Math.round(Math.cos(angle));
                previewY[i]+=(int)Math.round(Math.sin(angle));
            }
        }
        fillPolygon(graphics,currentX,currentY,0x343F9A91);
        renderRadarGrid(graphics,centerX,centerY,radius,highlightedStat);
        if(initialComparison)drawPolygon(graphics,previewX,previewY,0xFFB89564,true);
        drawPolygon(graphics,currentX,currentY,0xFF2F8F87,false);
        for(int i=0;i<STAT_IDS.length;i++)graphics.fill(currentX[i]-1,currentY[i]-1,currentX[i]+2,currentY[i]+2,0xFFBDE9D6);
        if(!initialComparison&&(finalProjection||trainingIndex>=0))drawPolygon(graphics,previewX,previewY,0xFFF0B448,true);
        renderRadarStatIcons(graphics,centerX,centerY,radius,highlightedStat);
    }

    private static void renderRadarGrid(GuiGraphics graphics,int centerX,int centerY,int radius,int highlightedStat){
        for(int ring=1;ring<=4;ring++){
            int ringRadius=radius*ring/4;
            int[] ringX=new int[STAT_IDS.length],ringY=new int[STAT_IDS.length];
            for(int i=0;i<STAT_IDS.length;i++){
                double angle=Math.toRadians(-90+i*72);
                ringX[i]=centerX+(int)Math.round(Math.cos(angle)*ringRadius);
                ringY[i]=centerY+(int)Math.round(Math.sin(angle)*ringRadius);
            }
            drawPolygon(graphics,ringX,ringY,0x557F927D,false);
        }
        for(int i=0;i<STAT_IDS.length;i++){
            double angle=Math.toRadians(-90+i*72);
            int edgeX=centerX+(int)Math.round(Math.cos(angle)*radius);
            int edgeY=centerY+(int)Math.round(Math.sin(angle)*radius);
            drawPixelLine(graphics,centerX,centerY,edgeX,edgeY,i==highlightedStat?0xDDF0B448:0x447F927D,false);
        }
    }

    private static void renderRadarStatIcons(GuiGraphics graphics,int centerX,int centerY,int radius,int highlightedStat){
        for(int i=0;i<STAT_IDS.length;i++){
            int iconX=radarIconX(centerX,radius,i),iconY=radarIconY(centerY,radius,i);
            if(i==highlightedStat){
                graphics.fill(iconX-1,iconY+2,iconX,iconY+6,PARCHMENT_GOLD);
                graphics.fill(iconX+RADAR_ICON_SIZE,iconY+2,iconX+RADAR_ICON_SIZE+1,iconY+6,PARCHMENT_GOLD);
                graphics.fill(iconX+2,iconY-1,iconX+6,iconY,PARCHMENT_GOLD);
                graphics.fill(iconX+2,iconY+RADAR_ICON_SIZE,iconX+6,iconY+RADAR_ICON_SIZE+1,PARCHMENT_GOLD);
            }
            drawRadarStatIcon(graphics,iconX,iconY,i,RADAR_STAT_COLORS[i]);
        }
    }

    private static void drawRadarStatIcon(GuiGraphics graphics,int x,int y,int stat,int color){
        RenderSystem.setShaderColor(((color>>16)&255)/255.0F,((color>>8)&255)/255.0F,(color&255)/255.0F,1.0F);
        graphics.blit(STAT_ICON_SHEET,x,y,RADAR_ICON_SIZE,RADAR_ICON_SIZE,stat*8.0F,0.0F,8,8,40,8);
        RenderSystem.setShaderColor(1.0F,1.0F,1.0F,1.0F);
    }

    private static int radarIconX(int centerX,int radius,int stat){
        double angle=Math.toRadians(-90+stat*72);
        return centerX+(int)Math.round(Math.cos(angle)*(radius+RADAR_ICON_MARGIN))-RADAR_ICON_SIZE/2;
    }

    private static int radarIconY(int centerY,int radius,int stat){
        double angle=Math.toRadians(-90+stat*72);
        return centerY+(int)Math.round(Math.sin(angle)*(radius+RADAR_ICON_MARGIN))-RADAR_ICON_SIZE/2;
    }

    private static void drawPolygon(GuiGraphics graphics,int[] xs,int[] ys,int color,boolean dotted){
        for(int i=0;i<xs.length;i++)drawPixelLine(graphics,xs[i],ys[i],xs[(i+1)%xs.length],ys[(i+1)%ys.length],color,dotted);
    }

    private static void drawPixelLine(GuiGraphics graphics,int x0,int y0,int x1,int y1,int color,boolean dotted){
        int dx=Math.abs(x1-x0),dy=Math.abs(y1-y0),sx=x0<x1?1:-1,sy=y0<y1?1:-1,error=dx-dy,step=0;
        while(true){
            if(!dotted||((step/2)&1)==0)graphics.fill(x0,y0,x0+1,y0+1,color);
            if(x0==x1&&y0==y1)break;
            int doubled=error*2;
            if(doubled>-dy){error-=dy;x0+=sx;}
            if(doubled<dx){error+=dx;y0+=sy;}
            step++;
        }
    }

    private static void fillPolygon(GuiGraphics graphics,int[] xs,int[] ys,int color){
        int minY=Arrays.stream(ys).min().orElse(0),maxY=Arrays.stream(ys).max().orElse(0);
        double[] intersections=new double[xs.length];
        for(int y=minY;y<=maxY;y++){
            int count=0;
            for(int i=0;i<xs.length;i++){
                int next=(i+1)%xs.length,y0=ys[i],y1=ys[next];
                if(y0==y1||y<Math.min(y0,y1)||y>=Math.max(y0,y1))continue;
                intersections[count++]=xs[i]+(double)(y-y0)*(xs[next]-xs[i])/(y1-y0);
            }
            Arrays.sort(intersections,0,count);
            for(int i=0;i+1<count;i+=2){
                int left=(int)Math.ceil(intersections[i]),right=(int)Math.floor(intersections[i+1]);
                if(right>=left)graphics.fill(left,y,right+1,y+1,color);
            }
        }
    }

    private boolean renderStatTooltip(GuiGraphics graphics,int mouseX,int mouseY,int x,int y){
        if((tab!=Tab.INFO&&tab!=Tab.GROWTH)||companionDexOpen)return false;
        OpenTurtleMenuPayload.Card card=selectedCard();
        if(card==null)return false;
        int hoveredStat=hoveredStatIndex(mouseX,mouseY,x,y);
        if(hoveredStat>=0){
            String key="yoiko_core.turtle.stat."+STAT_IDS[hoveredStat];
            List<Component> tooltip=new ArrayList<>();
            var statTitle=Component.translatable(key).copy()
                    .append(Component.literal("  "+statDisplay(card,hoveredStat)))
                    .withStyle(ChatFormatting.AQUA);
            tooltip.add(statTitle);
            tooltip.add(Component.translatable(key+".effect").withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable(key+".detail").withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font,wrapTooltip(tooltip,210),mouseX,mouseY);
            return true;
        }
        int radarCenterY=tab==Tab.INFO?INFO_RADAR_CENTER_Y:GROWTH_RADAR_CENTER_Y;
        int iconDistance=RADAR_RADIUS+RADAR_ICON_MARGIN;
        if(mouseX>=x&&mouseX<x+STAT_ROW_X&&mouseY>=y+radarCenterY-iconDistance-RADAR_ICON_SIZE/2
                &&mouseY<y+radarCenterY+iconDistance+RADAR_ICON_SIZE/2){
            List<Component> tooltip=new ArrayList<>();
            tooltip.add(Component.translatable("yoiko_core.turtle.ui.radar.current").withStyle(ChatFormatting.AQUA));
            if(tab==Tab.INFO){
                tooltip.add(Component.translatable("yoiko_core.turtle.ui.radar.initial").withStyle(ChatFormatting.GOLD));
            }else{
                tooltip.add(Component.translatable(Screen.hasShiftDown()?"yoiko_core.turtle.ui.radar.projected":"yoiko_core.turtle.ui.radar.shift_hint")
                        .withStyle(Screen.hasShiftDown()?ChatFormatting.GOLD:ChatFormatting.GRAY));
            }
            graphics.renderComponentTooltip(font,tooltip,mouseX,mouseY);
            return true;
        }
        return false;
    }

    private int hoveredStatIndex(int mouseX,int mouseY,int x,int y){
        if(tab!=Tab.INFO&&tab!=Tab.GROWTH)return -1;
        int centerX=x+RADAR_CENTER_X;
        int centerY=y+(tab==Tab.INFO?INFO_RADAR_CENTER_Y:GROWTH_RADAR_CENTER_Y);
        for(int i=0;i<STAT_IDS.length;i++){
            int iconX=radarIconX(centerX,RADAR_RADIUS,i),iconY=radarIconY(centerY,RADAR_RADIUS,i);
            if(mouseX>=iconX-1&&mouseX<iconX+RADAR_ICON_SIZE+1
                    &&mouseY>=iconY-1&&mouseY<iconY+RADAR_ICON_SIZE+1)return i;
        }
        int statY=tab==Tab.INFO?INFO_STAT_Y:GROWTH_STAT_Y;
        int rowHeight=tab==Tab.INFO?INFO_STAT_ROW_HEIGHT:GROWTH_STAT_ROW_HEIGHT;
        int right=tab==Tab.INFO?x+174:x+151;
        if(mouseX<x+STAT_ROW_X||mouseX>=right)return -1;
        for(int i=0;i<STAT_IDS.length;i++){
            int rowY=y+statY+i*rowHeight;
            if(mouseY>=rowY-1&&mouseY<rowY+13)return i;
        }
        return -1;
    }

    private boolean renderBodyColorTooltip(GuiGraphics graphics,int mouseX,int mouseY,int x,int y){
        OpenTurtleMenuPayload.Card card=selectedCard();
        if(card==null)return false;
        boolean rare=TurtleBodyAppearanceCatalog.isRare(card.bodyAppearance());
        boolean hovered=tab==Tab.INFO&&rare
                &&mouseX>=x+112&&mouseX<x+128&&mouseY>=y+1&&mouseY<y+17;
        if(tab==Tab.APPEARANCE){
            hovered=mouseX>=x&&mouseX<x+170&&mouseY>=y+144&&mouseY<y+168;
        }
        if(!hovered)return false;
        TurtleBodyAppearanceCatalog.Appearance appearance=TurtleBodyAppearanceCatalog.get(card.bodyAppearance());
        List<Component> tooltip=new ArrayList<>();
        Component title=Component.translatable("yoiko_core.turtle.body."+appearance.id())
                .withStyle(rare?ChatFormatting.GOLD:ChatFormatting.AQUA);
        tooltip.add(rare?title.copy().withStyle(ChatFormatting.BOLD):title);
        tooltip.add(Component.translatable("yoiko_core.turtle.body.innate.detail").withStyle(ChatFormatting.GRAY));
        if(rare){
            String chance=String.format(Locale.ROOT,"%.2f",appearance.weight()/100.0);
            tooltip.add(Component.translatable("yoiko_core.turtle.body.rare",chance).withStyle(ChatFormatting.LIGHT_PURPLE,ChatFormatting.BOLD));
        }
        graphics.renderComponentTooltip(font,tooltip,mouseX,mouseY);
        return true;
    }

    private boolean renderRaceClassProgressTooltip(GuiGraphics graphics,int mouseX,int mouseY,int x,int y){
        if(tab!=Tab.INFO||mouseX<x+6||mouseX>=x+170||mouseY<y+16||mouseY>=y+29)return false;
        OpenTurtleMenuPayload.Card card=selectedCard();if(card==null)return false;
        int minimum=switch(card.raceClass()){case "C"->100;case "B"->250;case "A"->500;case "S"->850;default->0;};
        int threshold=switch(card.raceClass()){case "D"->100;case "C"->250;case "B"->500;case "A"->850;default->850;};
        List<Component> tooltip=new ArrayList<>();
        tooltip.add(Component.translatable("yoiko_core.turtle.ui.class_progress.title",card.raceClass(),card.racePoints()).withStyle(ChatFormatting.AQUA));
        if(card.raceClass().equals("S")){
            tooltip.add(Component.literal("■■■■■■■■■■").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("yoiko_core.turtle.ui.class_progress.maximum").withStyle(ChatFormatting.GRAY));
        }else{
            int current=Math.max(0,card.racePoints()-minimum),required=Math.max(1,threshold-minimum);
            int filled=Math.min(10,(int)Math.floor((double)current*10/required));
            tooltip.add(Component.literal("■".repeat(filled)+"□".repeat(10-filled)+"  "+current+"/"+required+" pt").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("yoiko_core.turtle.ui.class_progress.next",nextRaceClass(card.raceClass()),Math.max(0,threshold-card.racePoints())).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("yoiko_core.turtle.ui.class_progress.awards").withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font,tooltip,mouseX,mouseY);return true;
    }

    private static String nextRaceClass(String current){return switch(current){case "D"->"C";case "C"->"B";case "B"->"A";default->"S";};}

    private void drawResourceStrip(GuiGraphics graphics,int x,int y){
        int cell=58;
        drawInfoIcon(graphics,ICON_TRAINING,x+2,y,16);
        graphics.drawString(font,compactNumber(data.training()),x+20,y+4,PARCHMENT_TEXT,false);
        drawInfoIcon(graphics,ICON_SHELL_MEDAL,x+cell+2,y,16);
        graphics.drawString(font,compactNumber(data.medals()),x+cell+20,y+4,PARCHMENT_TEAL,false);
        String gems=compactNumber(data.gems());
        int gemWidth=YoikoCurrencyIconRenderer.ICON_SIZE+YoikoCurrencyIconRenderer.TEXT_GAP+font.width(gems);
        int gemX=x+cell*2+(56-gemWidth)/2;
        YoikoCurrencyIconRenderer.render(graphics,"GEM",gemX,y+4);
        graphics.drawString(font,gems,gemX+YoikoCurrencyIconRenderer.ICON_SIZE+YoikoCurrencyIconRenderer.TEXT_GAP,
                y+4,YoikoCurrencyIconRenderer.GEM_COLOR,false);
    }

    private static ResourceLocation infoIcon(String category,String id){
        return ResourceLocation.fromNamespaceAndPath(YoikoServerCore.MODID,
                "textures/gui/turtle/icons/"+category+"/"+id+".png");
    }

    private static void drawInfoIcon(GuiGraphics graphics,ResourceLocation icon,int x,int y,int size){
        graphics.blit(icon,x,y,size,size,0.0F,0.0F,16,16,16,16);
    }

    private List<ConditionIcon> raceConditionIcons(){
        List<ConditionIcon> result=new ArrayList<>(4);
        switch(raceStatus.forecastWeather()){
            case "CLEAR"->result.add(new ConditionIcon(ICON_WEATHER,"yoiko_core.turtle.condition.weather.clear",
                    0,8,24,8,8,0xFFF0B448));
            case "LIGHT_RAIN"->result.add(new ConditionIcon(ICON_WEATHER,"yoiko_core.turtle.condition.weather.rain",
                    16,8,24,8,8,0xFF4FA7D8));
            case "FOREST_MIST"->result.add(new ConditionIcon(ICON_WEATHER,"yoiko_core.turtle.condition.weather.fog",
                    8,8,24,8,8,0xFFA7B9BD));
            default->{ }
        }
        if((raceStatus.forecastSurfaceMask()&1)!=0)result.add(new ConditionIcon(ICON_SURFACE,"yoiko_core.turtle.condition.surface.sand",
                8,8,24,8,8,0xFFFFFFFF));
        if((raceStatus.forecastSurfaceMask()&2)!=0)result.add(new ConditionIcon(ICON_SURFACE,"yoiko_core.turtle.condition.surface.mud",
                0,8,24,8,8,0xFFFFFFFF));
        if((raceStatus.forecastSurfaceMask()&4)!=0)result.add(new ConditionIcon(ICON_SURFACE,"yoiko_core.turtle.condition.surface.water",
                16,8,24,8,8,0xFFFFFFFF));
        return result;
    }

    private void drawRaceConditionIcons(GuiGraphics graphics,int x,int y){
        List<ConditionIcon> icons=raceConditionIcons();
        for(int i=0;i<icons.size();i++){
            ConditionIcon icon=icons.get(i);int inset=(12-icon.displaySize())/2;
            drawConditionIcon(graphics,icon,x+i*15+inset,y+inset);
        }
    }

    private static void drawConditionIcon(GuiGraphics graphics,ConditionIcon icon,int x,int y){
        int color=icon.color();
        RenderSystem.setShaderColor(((color>>16)&255)/255.0F,((color>>8)&255)/255.0F,(color&255)/255.0F,
                ((color>>>24)&255)/255.0F);
        graphics.blit(icon.icon(),x,y,icon.displaySize(),icon.displaySize(),icon.sourceX(),0.0F,
                icon.sourceSize(),icon.sourceSize(),icon.textureWidth(),icon.textureHeight());
        RenderSystem.setShaderColor(1.0F,1.0F,1.0F,1.0F);
    }

    private boolean renderInfoIconTooltip(GuiGraphics graphics,int mouseX,int mouseY,int right,int top){
        int resourceX=right+8,resourceY=top+35;
        if(mouseY>=resourceY&&mouseY<resourceY+16&&mouseX>=resourceX&&mouseX<resourceX+174){
            int cell=Math.min(2,(mouseX-resourceX)/58);
            Component tooltip=switch(cell){
                case 0->Component.translatable("yoiko_core.turtle.icon.resource.training_count",data.training());
                case 1->Component.translatable("yoiko_core.turtle.icon.resource.shell_medal",data.medals());
                default->Component.translatable("yoiko_core.turtle.icon.resource.gem",data.gems());
            };
            graphics.renderTooltip(font,tooltip,mouseX,mouseY);return true;
        }
        if(tab==Tab.GROWTH&&!companionDexOpen){
            int x=right+10,y=top+53;
            if(mouseX>=x+78&&mouseX<x+170&&mouseY>=y+1&&mouseY<y+20){
                OpenTurtleMenuPayload.Card card=selectedCard();
                if(card!=null)graphics.renderTooltip(font,Component.translatable("yoiko_core.turtle.icon.training.progress",card.training(),24),mouseX,mouseY);
                return true;
            }
        }
        if(tab==Tab.RACE&&raceStatus.competition().active()){
            List<ConditionIcon> icons=raceConditionIcons();int x=right+10+112,y=top+53+6;
            for(int i=0;i<icons.size();i++)if(mouseX>=x+i*15&&mouseX<x+i*15+12&&mouseY>=y&&mouseY<y+12){
                graphics.renderTooltip(font,Component.translatable(icons.get(i).tooltipKey()),mouseX,mouseY);return true;
            }
        }
        return false;
    }

    private void drawCard(GuiGraphics graphics,int x,int y,int width,int height){
        YoikoScreenStyle.renderTurtleCard(graphics,x,y,width,height);
    }

    private void drawRightAligned(GuiGraphics graphics,String text,int right,int y,int color){graphics.drawString(font,text,right-font.width(text),y,color,false);}

    private List<Component> wrapTooltip(List<Component> source,int maxWidth){
        List<Component> result=new ArrayList<>();
        for(Component component:source){
            if(component.getString().isBlank()){result.add(component);continue;}
            for(FormattedText line:font.getSplitter().splitLines(component,maxWidth,Style.EMPTY)){
                var copy=Component.empty();
                line.visit((style,text)->{copy.append(Component.literal(text).setStyle(style));return java.util.Optional.empty();},Style.EMPTY);
                result.add(copy);
            }
        }
        return result;
    }

    private List<String> timeTrialStatus(){
        var status=raceStatus.timeTrial();
        return List.of(presetName(status.preset()),tr("yoiko_core.turtle.ui.time_trial_status",timeTrialStateName(status.state()),status.queuePosition()));
    }

    private String competitionStatus(){
        var status=raceStatus.competition();
        if(!status.active())return tr("yoiko_core.turtle.ui.competition.none_weekly_next",status.weeklyFinishes(),status.weeklyBestRank(),leagueName(status.nextLeague()),remaining(status.nextStartAt()));
        return tr("yoiko_core.turtle.ui.competition.status_weekly",leagueName(status.league()),tr("yoiko_core.turtle.ui.phase."+status.phase().toLowerCase(Locale.ROOT)),status.registrations(),status.weeklyFinishes(),status.weeklyBestRank());
    }

    private static String betLabel(OpenTurtleMenuPayload.BetCandidate option){
        if(option.entryId().startsWith("ai:"))return tr("yoiko_core.turtle.ui.ai_name",tr("yoiko_core.turtle.ai."+option.entryId().substring(3)+".name"));
        return TurtleNameCatalog.component(option.label()).getString();
    }

    private static String presetName(String id){return tr("yoiko_core.turtle.ui.preset."+switch(id){
        case "coast_sprint"->"coast_sprint";case "boardwalk_turns"->"boardwalk_turns";
        case "tailwind_longrun"->"tailwind_longrun";case "forest_quickstep"->"forest_quickstep";
        case "mist_technical"->"mist_technical";case "forest_endurance"->"forest_endurance";
        default->"unknown";
    });}

    private static String timeTrialStateName(String value){return tr("yoiko_core.turtle.ui.state."+switch(value){
        case "BUILDING"->"building";case "READY"->"ready";case "COUNTDOWN"->"countdown";
        case "RUNNING"->"running";case "RESULT"->"result";case "CLEANING"->"cleaning";default->"idle";
    });}

    private static String compactNumber(long value){
        if(value<10_000)return Long.toString(value);
        if(value<1_000_000)return String.format(Locale.ROOT,"%.1fK",value/1_000.0);
        return String.format(Locale.ROOT,"%.1fM",value/1_000_000.0);
    }

    private static String formatBetAmount(long value){return value>=1_000?(value/1_000)+"K":Long.toString(value);}

    private long timeTrialBest(String raceClass){return raceStatus.timeTrial().bests().stream().filter(value->value.raceClass().equals(raceClass)).mapToLong(OpenTurtleMenuPayload.TimeTrialBest::finishMillis).findFirst().orElse(0);}
    private static String remaining(long epochMillis){long seconds=Math.max(0,(epochMillis-System.currentTimeMillis())/1000),hours=seconds/3600,minutes=(seconds%3600)/60;return hours>0?hours+tr("yoiko_core.turtle.ui.time.hour")+" "+minutes+tr("yoiko_core.turtle.ui.time.minute"):minutes+tr("yoiko_core.turtle.ui.time.minute");}

    private long estimatedPayout(OpenTurtleMenuPayload.BetCandidate candidate){OpenTurtleMenuPayload.BetPool pool=betPool(candidate);boolean open="BETTING_OPEN".equals(raceStatus.competition().phase())&&!raceStatus.competition().playerRegistered()&&canAddBet(candidate)&&canStake(candidate,BET_AMOUNTS[betAmountIndex]);long addition=open?BET_AMOUNTS[betAmountIndex]:0,mine=pool.myStake()+addition;return mine<=0||candidate.oddsMilli()<=0?0:TurtleBettingRules.capGrossPayout(mine*candidate.oddsMilli()/1_000L);}
    private String formatWinChance(int basisPoints){if(basisPoints<=0)return "…";if(basisPoints<100)return "<1%";return basisPoints%100==0?(basisPoints/100)+"%":String.format(Locale.ROOT,"%.1f%%",basisPoints/100.0);}
    private String formatOdds(int oddsMilli){return oddsMilli<=0?"…":String.format(Locale.ROOT,"×%.2f",oddsMilli/1_000.0);}

    private void renderLastRace(GuiGraphics graphics,OpenTurtleMenuPayload.RaceAnalysis race,int x,int y){
        graphics.drawString(font,Component.translatable("yoiko_core.turtle.ui.analysis.title"),x,y,PARCHMENT_GOLD,false);
        graphics.drawString(font,race.finished()?tr("yoiko_core.turtle.ui.analysis.result",race.rank(),formatMillis(race.finishMillis()),race.staminaPercent()):tr("yoiko_core.turtle.ui.analysis.dnf",race.staminaPercent()),x,y+12,race.finished()?PARCHMENT_TEXT:0xFFB74747,false);
        graphics.drawString(font,tr("yoiko_core.turtle.ui.analysis.flow",race.overtakes(),race.laneChanges(),race.blockedTicks()/20.0),x,y+24,PARCHMENT_MUTED,false);
        graphics.drawString(font,tr("yoiko_core.turtle.ui.analysis.skill",race.breaths(),race.activeUsed()?tr("yoiko_core.common.yes"):tr("yoiko_core.common.no"),race.ratingChange()>=0?"+"+race.ratingChange():Integer.toString(race.ratingChange())),x,y+36,PARCHMENT_TEAL,false);
    }

    private static String formatMillis(long millis){return String.format(Locale.ROOT,"%d:%02d.%03d",millis/60_000,(millis/1_000)%60,millis%1_000);}

    private static Component skillName(String type,String id){return Component.translatable("yoiko_core.turtle.skill."+type+"."+id+".name");}

    private static List<Component> effectDetails(String type,String id){
        if(hasShiftDown())return List.of(styledEffectDescription(type,id));
        return List.of(styledEffectDescription(type,id),Component.translatable("yoiko_core.turtle.effect.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component styledEffectDescription(String type,String id){
        String key=effectDescriptionKey(type,id);
        boolean exact=hasShiftDown();
        return styleEffectText(type,tr(exact?key:key+".qualitative"),exact);
    }

    private static String effectDescriptionKey(String type,String id){return type.equals("companion")
            ?"yoiko_core.turtle.companion."+id+".description":"yoiko_core.turtle.skill."+type+"."+id+".description";}

    private static Component styleEffectText(String type,String text,boolean exact){
        EffectEmphasis[] emphasis=new EffectEmphasis[text.length()];Arrays.fill(emphasis,EffectEmphasis.BODY);
        int triggerEnd=activationPrefixEnd(type,text,exact);if(triggerEnd>0)Arrays.fill(emphasis,0,Math.min(triggerEnd,emphasis.length),EffectEmphasis.TRIGGER);
        markPhrases(text,emphasis,EffectEmphasis.SMALL,"조금","わずかに","少し","slightly","a little");
        markPhrases(text,emphasis,EffectEmphasis.VERY_SMALL,"아주 조금","ほんのわずかに","very slightly");
        markPhrases(text,emphasis,EffectEmphasis.MEDIUM,"적당히","ある程度","moderately");
        markPhrases(text,emphasis,EffectEmphasis.LARGE,"크게","大きく","greatly");
        markPhrases(text,emphasis,EffectEmphasis.VERY_LARGE,"매우 크게","매우 빠르게","대부분","非常に大きく","非常に速","大部分","dramatically","most of");
        Matcher matcher=EFFECT_NUMBER.matcher(text);while(matcher.find())Arrays.fill(emphasis,matcher.start(),matcher.end(),EffectEmphasis.NUMBER);
        var result=Component.empty();int start=0;
        while(start<text.length()){int end=start+1;while(end<text.length()&&emphasis[end]==emphasis[start])end++;appendEffectSegment(result,text.substring(start,end),emphasis[start]);start=end;}
        return result;
    }

    private static void markPhrases(String text,EffectEmphasis[] emphasis,EffectEmphasis value,String... phrases){
        String lower=text.toLowerCase(Locale.ROOT);
        for(String phrase:phrases){String needle=phrase.toLowerCase(Locale.ROOT);int index=0;while((index=lower.indexOf(needle,index))>=0){Arrays.fill(emphasis,index,index+needle.length(),value);index+=needle.length();}}
    }

    private static int activationPrefixEnd(String type,String text,boolean exact){
        if(type.equals("active")&&(!exact||text.contains("발동")||text.contains("発動")||text.toLowerCase(Locale.ROOT).contains("activates"))){int end=firstSentenceEnd(text);if(end>0)return end;}
        String lower=text.toLowerCase(Locale.ROOT);
        if(lower.startsWith("always "))return 6;if(text.startsWith("항상 "))return 3;if(text.startsWith("常に"))return 2;
        int comma=Math.min(positiveOrMax(text.indexOf(',')),positiveOrMax(text.indexOf('、')));
        if(comma<Integer.MAX_VALUE&&(lower.startsWith("when ")||lower.startsWith("while ")||lower.startsWith("during ")||lower.startsWith("after ")||lower.startsWith("before ")||lower.startsWith("in the ")||lower.startsWith("on ")||lower.startsWith("at ")))return comma+1;
        String[] markers={" 시 ","에서 ","이면 ","하면 ","동안 ","이후 "," 이전 "," 전 "," 날 "," 때 ","になると","なら","では","で","後、","間、"};
        int best=Integer.MAX_VALUE;for(String marker:markers){int index=text.indexOf(marker);if(index>=0)best=Math.min(best,index+marker.length());}
        return best==Integer.MAX_VALUE?0:best;
    }

    private static int firstSentenceEnd(String text){int dot=text.indexOf('.'),japanese=text.indexOf('。');int end=Math.min(positiveOrMax(dot),positiveOrMax(japanese));return end==Integer.MAX_VALUE?0:end+1;}
    private static int positiveOrMax(int value){return value<0?Integer.MAX_VALUE:value;}

    private static void appendEffectSegment(net.minecraft.network.chat.MutableComponent result,String value,EffectEmphasis emphasis){
        var part=Component.literal(value);
        switch(emphasis){
            case TRIGGER->part.withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD);
            case VERY_SMALL->part.withStyle(ChatFormatting.DARK_GRAY);
            case SMALL->part.withStyle(ChatFormatting.GREEN);
            case MEDIUM->part.withStyle(ChatFormatting.AQUA);
            case LARGE->part.withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD);
            case VERY_LARGE->part.withStyle(ChatFormatting.LIGHT_PURPLE,ChatFormatting.BOLD);
            case NUMBER->part.withStyle(ChatFormatting.AQUA,ChatFormatting.BOLD);
            default->part.withStyle(ChatFormatting.GRAY);
        }
        result.append(part);
    }

    private String appearanceName(String id){
        return appearanceNameCache.computeIfAbsent(id,value->Component.translatable("yoiko_core.turtle.appearance."+value).getString());
    }

    private void drawWrapped(GuiGraphics graphics, String value, int x, int y, int lineWidth, int maxLines, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(value), lineWidth);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 11, color, false);
        }
    }

    private void drawWrappedComponent(GuiGraphics graphics,Component value,int x,int y,int lineWidth,int maxLines){
        List<net.minecraft.util.FormattedCharSequence> lines=font.split(value,lineWidth);
        for(int i=0;i<Math.min(maxLines,lines.size());i++)graphics.drawString(font,lines.get(i),x,y+i*11,PARCHMENT_TEXT,false);
    }

    private OpenTurtleMenuPayload.Card selectedCard() {
        return data.turtles().isEmpty() ? null : data.turtles().get(Math.max(0,
                Math.min(selected, data.turtles().size() - 1)));
    }

    private List<OpenTurtleMenuPayload.BetCandidate> currentBetCandidates() {
        return data.betCandidates().stream().filter(option -> option.heat() == betHeat).toList();
    }

    private OpenTurtleMenuPayload.BetCandidate selectedBetCandidate(){List<OpenTurtleMenuPayload.BetCandidate> candidates=currentBetCandidates();return candidates.isEmpty()?null:candidates.get(Math.min(betOption,candidates.size()-1));}

    private List<OpenTurtleMenuPayload.BetPool> currentBetPools(){return raceStatus.betPools().stream().filter(value->value.heat()==betHeat).toList();}
    private OpenTurtleMenuPayload.BetPool betPool(OpenTurtleMenuPayload.BetCandidate candidate){return raceStatus.betPools().stream().filter(value->value.heat()==candidate.heat()&&value.entryId().equals(candidate.entryId())).findFirst().orElse(new OpenTurtleMenuPayload.BetPool(candidate.heat(),candidate.entryId(),0,0));}
    private boolean canAddBet(OpenTurtleMenuPayload.BetCandidate candidate){return currentBetPools().stream().noneMatch(value->value.myStake()>0&&!value.entryId().equals(candidate.entryId()));}
    private boolean canStake(OpenTurtleMenuPayload.BetCandidate candidate,long amount){return candidate!=null&&amount>=TurtleBettingRules.MIN_STAKE&&betPool(candidate).myStake()+amount<=TurtleBettingRules.MAX_STAKE_PER_RACE;}

    private int heatCount() {
        return Math.max(1, data.betCandidates().stream().mapToInt(OpenTurtleMenuPayload.BetCandidate::heat).max().orElse(-1) + 1);
    }

    private void clampBetSelection() {
        betHeat = Math.min(betHeat, heatCount() - 1);
        betOption = Math.min(betOption, Math.max(0, currentBetCandidates().size() - 1));
    }

    private String nextCompanion(String current) {
        if (data.companions().isEmpty()) return "NONE";
        if (current.equals("NONE")) return data.companions().getFirst();
        int index = data.companions().indexOf(current);
        return index < 0 || index + 1 >= data.companions().size() ? "NONE" : data.companions().get(index + 1);
    }

    private static int activeSlots(int points) {
        return points >= 30 ? 4 : points >= 15 ? 3 : points >= 5 ? 2 : 1;
    }

    private int betCandidateSignature(){int hash=1;for(var candidate:data.betCandidates()){hash=31*hash+candidate.heat();hash=31*hash+candidate.entryId().hashCode();}return hash;}

    private boolean growthLocked(OpenTurtleMenuPayload.Card card){return card.registered()&&raceStatus.competition().active();}

    private static Tab parseTab(String value) {
        String normalized=value==null?"":value.toUpperCase(Locale.ROOT);
        if(normalized.equals("TURTLES"))return Tab.INFO;
        if(normalized.equals("TRAINING"))return Tab.GROWTH;
        if(normalized.equals("TIME_TRIAL"))return Tab.RACE;
        try { return Tab.valueOf(normalized); }
        catch (RuntimeException ignored) { return Tab.INFO; }
    }

    private static Component strategyShortName(String value){return Component.translatable("yoiko_core.turtle.ui.strategy.short."+value.toLowerCase(Locale.ROOT));}

    private static List<Component> strategyTooltip(String value){String id=value.toLowerCase(Locale.ROOT);return List.of(Component.translatable("yoiko_core.turtle.strategy."+id+".name").withStyle(ChatFormatting.AQUA),Component.translatable("yoiko_core.turtle.strategy."+id+".description").withStyle(ChatFormatting.GRAY),Component.translatable("yoiko_core.turtle.strategy.shared_lane_rule").withStyle(ChatFormatting.DARK_GRAY));}

    private static boolean cardIsRecommended(OpenTurtleMenuPayload.Card card,String id){return card!=null&&card.recommendedTraining().equals(id);}
    private static String trainingName(String id){return id==null||id.isBlank()?"":tr("yoiko_core.turtle.ui.training."+id.toLowerCase(Locale.ROOT));}
    private static String statDisplay(OpenTurtleMenuPayload.Card card,int index){int current=card.stats().get(index),base=card.baseStats().get(index),trained=current-base;return current+" "+card.statGrades().get(index)+(trained>0?" ("+base+" +"+trained+")":"");}
    private static int statGradeColor(String grade){return switch(grade){case "S"->0xFFAA691D;case "A"->0xFF76509D;case "B"->0xFF347C70;case "C"->0xFF4E708B;default->PARCHMENT_MUTED;};}
    private static String raceProgressText(OpenTurtleMenuPayload.Card card){int next=switch(card.raceClass()){case "D"->100;case "C"->250;case "B"->500;case "A"->850;default->card.racePoints();};return card.raceClass().equals("S")?tr("yoiko_core.turtle.ui.class_progress.maximum"):tr("yoiko_core.turtle.ui.class_progress.next",card.raceClass(),Math.max(0,next-card.racePoints()));}
    private static String currentStrategyForecast(OpenTurtleMenuPayload.Card card){if(card.strategyForecasts().size()!=4)return tr("yoiko_core.turtle.ui.forecast.none");int index=switch(card.strategy()){case "FRONT"->0;case "STEADY"->1;case "FOLLOW"->2;default->3;};String[] v=card.strategyForecasts().get(index).split("\\|",-1);if(v.length!=5)return card.strategyForecasts().get(index);return tr("yoiko_core.turtle.ui.forecast.line",forecastLevel(v[0]),forecastLevel(v[1]),forecastLevel(v[2]),forecastLevel(v[3]),forecastLevel(v[4]));}
    private static String strategyForecast(OpenTurtleMenuPayload.Card card,String strategy){if(card.strategyForecasts().size()!=4)return tr("yoiko_core.turtle.ui.forecast.none");int index=switch(strategy){case "FRONT"->0;case "STEADY"->1;case "FOLLOW"->2;default->3;};String[] v=card.strategyForecasts().get(index).split("\\|",-1);if(v.length!=5)return tr("yoiko_core.turtle.ui.forecast.none");return tr("yoiko_core.turtle.ui.forecast.line",forecastLevel(v[0]),forecastLevel(v[1]),forecastLevel(v[2]),forecastLevel(v[3]),forecastLevel(v[4]));}
    private static String forecastGrade(OpenTurtleMenuPayload.Card card){if(card.strategyForecasts().size()!=4)return tr("yoiko_core.turtle.ui.forecast.normal");int index=switch(card.strategy()){case "FRONT"->0;case "STEADY"->1;case "FOLLOW"->2;default->3;};String value=card.strategyForecasts().get(index);int good=count(value,"HIGH")+count(value,"VERY_GOOD")*2;int low=count(value,"LOW")+count(value,"VERY_LOW")*2;return tr(good>=2&&low==0?"yoiko_core.turtle.ui.forecast.fit":low>=2?"yoiko_core.turtle.ui.forecast.strain":"yoiko_core.turtle.ui.forecast.normal");}
    private static int count(String value,String target){int result=0,index=0;while((index=value.indexOf(target,index))>=0){result++;index+=target.length();}return result;}

    private static String companionName(String value) {
        return value.equals("NONE") ? tr("yoiko_core.turtle.ui.none") : tr("yoiko_core.turtle.companion."+value.toLowerCase(Locale.ROOT)+".name");
    }

    private static String forecastLevel(String value){return tr("yoiko_core.turtle.ui.forecast.level."+value.toLowerCase(Locale.ROOT));}
    private static String leagueName(String value){return tr("yoiko_core.turtle.ui.league."+value.toLowerCase(Locale.ROOT));}

    private static String tr(String key,Object... args){return Component.translatable(key,args).getString();}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class ModeButton extends Button {
        private final Tab mode;
        private final boolean selectedMode;

        private ModeButton(int x, int y, Tab mode, boolean selectedMode, OnPress onPress) {
            super(x, y, 30, 24, Component.translatable(mode.label), onPress, DEFAULT_NARRATION);
            this.mode = mode;
            this.selectedMode = selectedMode;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            YoikoScreenStyle.renderTurtleButton(graphics, getX(), getY(), getWidth(), getHeight(),
                    selectedMode || isHoveredOrFocused(), true);
            if (selectedMode) graphics.fill(getX() + 3, getY() + 21, getX() + 27, getY() + 22, PARCHMENT_GOLD);
            graphics.blit(mode.icon, getX() + 7, getY() + 4,
                    16, 16, 0.0F, 0.0F, 16, 16, 16, 16);
        }
    }

    private final class TurtleCardButton extends Button {
        private final OpenTurtleMenuPayload.Card card;
        private final boolean selectedCard;

        private TurtleCardButton(int x, int y, OpenTurtleMenuPayload.Card card, boolean selectedCard, OnPress onPress) {
            super(x, y, 176, 21, TurtleNameCatalog.component(card.name()), onPress, DEFAULT_NARRATION);
            this.card = card;
            this.selectedCard = selectedCard;
        }

        private List<Component> tooltip(){return List.of(Component.translatable("yoiko_core.turtle.ui.card.rating",card.career().rating()).withStyle(ChatFormatting.AQUA),Component.translatable(card.favorite()?"yoiko_core.turtle.ui.favorite.remove_hint":"yoiko_core.turtle.ui.favorite.add_hint").withStyle(ChatFormatting.GRAY));}

        @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
            if(button==1&&isMouseOver(mouseX,mouseY)){
                PacketDistributor.sendToServer(ClientMenuSession.turtleAction("TURTLE_FAVORITE",card.id(),tab.name(),card.revision()));
                return true;
            }
            return super.mouseClicked(mouseX,mouseY,button);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if(selectedCard||isHoveredOrFocused()){
                graphics.fill(getX()+2,getY()+2,getX()+getWidth()-2,getY()+getHeight()-2,
                        selectedCard?0x3A72A997:0x26E8C98D);
            }
            int accent = raceClassColor(card.raceClass());
            graphics.fill(getX() + 2, getY() + 3, getX() + 4, getY() + 18, accent);
            var name=Component.empty();
            if(card.locked())name.append(Component.literal("◆ "));
            name.append(TurtleNameCatalog.component(card.name()));
            int nameX=getX()+8;
            if(card.favorite()){YoikoScreenStyle.renderFavoriteIcon(graphics,getX()+4,getY()+4,12);nameX=getX()+18;}
            graphics.drawString(Minecraft.getInstance().font,
                    font.plainSubstrByWidth(name.getString(),100), nameX, getY() + 4, PARCHMENT_TEXT, false);
            String summary = card.raceClass() + " · R" + card.career().rating();
            graphics.drawString(Minecraft.getInstance().font, summary,
                    getX() + getWidth() - 6 - Minecraft.getInstance().font.width(summary), getY() + 4,
                    PARCHMENT_MUTED, false);
        }
    }

    private final class BetCandidateButton extends Button{
        private final OpenTurtleMenuPayload.BetCandidate candidate;private final boolean selectedCandidate;private final int laneNumber;
        private BetCandidateButton(int x,int y,int width,int height,OpenTurtleMenuPayload.BetCandidate candidate,boolean selectedCandidate,OnPress onPress){
            super(x,y,width,height,Component.literal(betLabel(candidate)),onPress,DEFAULT_NARRATION);this.candidate=candidate;this.selectedCandidate=selectedCandidate;
            this.laneNumber=currentBetCandidates().indexOf(candidate)+1;
        }
        private List<Component> tooltip(){OpenTurtleMenuPayload.BetPool pool=betPool(candidate);List<Component> lines=new ArrayList<>();
            lines.add(Component.literal("["+laneNumber+"] ").append(Component.literal(betLabel(candidate))).withStyle(ChatFormatting.AQUA,ChatFormatting.BOLD));
            lines.add(Component.translatable("yoiko_core.turtle.ui.bet_profile",candidate.raceClass(),candidate.rating(),strategyShortName(candidate.strategy()),candidate.recentForm()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("yoiko_core.turtle.ui.bet_active",skillName("active",candidate.active())).withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable("yoiko_core.turtle.ui.bet_fixed_forecast",formatWinChance(candidate.winBasisPoints()),formatOdds(candidate.oddsMilli())).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("yoiko_core.turtle.ui.bet_stake_payout",pool.myStake(),estimatedPayout(candidate)).withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable("yoiko_core.turtle.ui.bet_popularity_pool",pool.totalPool()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("yoiko_core.turtle.ui.bet_payout_cap",TurtleBettingRules.MAX_GROSS_PAYOUT_PER_RACE).withStyle(ChatFormatting.GRAY));
            if(!canAddBet(candidate))lines.add(Component.translatable("yoiko_core.turtle.error.one_bet_candidate").withStyle(ChatFormatting.RED));
            return wrapTooltip(lines,210);
        }
        @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
            YoikoScreenStyle.renderTurtleButton(graphics,getX(),getY(),getWidth(),getHeight(),selectedCandidate||isHoveredOrFocused(),true);
            if(selectedCandidate)graphics.fill(getX()+2,getY()+getHeight()-3,getX()+getWidth()-2,getY()+getHeight()-2,PARCHMENT_GOLD);
            graphics.fill(getX()+2,getY()+3,getX()+4,getY()+getHeight()-3,raceClassColor(candidate.raceClass()));
            String text="["+laneNumber+"] "+formatWinChance(candidate.winBasisPoints());graphics.drawCenteredString(font,font.plainSubstrByWidth(text,getWidth()-7),getX()+getWidth()/2,getY()+5,PARCHMENT_TEXT);
        }
    }

    private final class AppearanceButton extends Button{
        private AppearanceView appearance;
        private boolean selectedAppearance;

        private AppearanceButton(int x,int y,int w,int h,AppearanceView appearance,boolean selected,OnPress press){
            super(x,y,w,h,Component.literal(appearanceName(appearance.id())),press,DEFAULT_NARRATION);
            update(appearance,selected);
        }

        private void update(AppearanceView value,boolean selected){
            appearance=value;
            selectedAppearance=selected;
            setMessage(Component.literal(appearanceName(value.id())));
        }

        @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
            if(button==1&&isMouseOver(mouseX,mouseY)&&pendingAppearanceId==null&&appearanceConfirmId==null){
                OpenTurtleMenuPayload.Card card=selectedCard();
                if(card!=null)sendAppearanceRequest("APPEARANCE_FAVORITE",appearance.id(),card);
                return true;
            }
            return super.mouseClicked(mouseX,mouseY,button);
        }

        @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
            boolean locked=!appearance.unlocked();
            if(locked)YoikoScreenStyle.renderLockedSlot(graphics,getX(),getY());
            else YoikoScreenStyle.renderSlot(graphics,getX(),getY());
            renderPaintBucket(graphics,getX()+4,getY()+4,appearance.id());
            if(locked)renderUnlockIcon(graphics,appearance,getX()+15,getY()+15);
            if(appearance.favorite())YoikoScreenStyle.renderFavoriteIcon(graphics,getX()+getWidth()-18,getY(),18);
            if(isHoveredOrFocused()){
                YoikoScreenStyle.renderSlotMask(graphics,getX(),getY(),getWidth(),getHeight(),0xFFD36A,1);
            }
            if(selectedAppearance)YoikoScreenStyle.renderCheckIcon(graphics,getX()+13,getY()+1,10);
            if(!active&&!(isShellAppearancePending()&&appearance.id().equals(pendingAppearanceId))){
                YoikoScreenStyle.renderSlotMask(graphics,getX(),getY(),getWidth(),getHeight(),0x000000,2);
            }
            if(isShellAppearancePending()&&appearance.id().equals(pendingAppearanceId)){
                YoikoScreenStyle.renderSlotMask(graphics,getX(),getY(),getWidth(),getHeight(),0x000000,3);
                int phase=(int)((net.minecraft.Util.getMillis()/180L)%3L);
                graphics.drawCenteredString(font,".".repeat(phase+1),getX()+getWidth()/2,getY()+7,0xFFFFD36A);
            }
            if(appearance.id().equals(appearanceFeedbackId)&&appearanceFeedbackTicks>0)renderAppearanceSparkle(graphics,getX(),getY());
        }

        private List<Component> tooltip(){
            List<Component> lines=new ArrayList<>();
            lines.add(Component.literal(appearanceName(appearance.id()))
                    .withStyle(selectedAppearance?ChatFormatting.AQUA:ChatFormatting.WHITE));
            if(appearance.unlocked()){
                lines.add(Component.translatable(selectedAppearance
                        ?"yoiko_core.turtle.appearance.selected":"yoiko_core.turtle.appearance.apply_hint").withStyle(ChatFormatting.GRAY));
            }else if(appearance.unlockKind().equals("GEM")){
                lines.add(Component.translatable("yoiko_core.turtle.appearance.gem_unlock",appearance.gemPrice()).withStyle(ChatFormatting.GOLD));
            }else{
                lines.add(Component.translatable("yoiko_core.turtle.appearance.unlock."+appearance.unlockKey()).withStyle(ChatFormatting.YELLOW));
            }
            if(!appearance.unlocked()&&appearance.target()>0){
                lines.add(Component.literal(progressBar(appearance.progress(),appearance.target())+" "+appearance.progress()+"/"+appearance.target())
                        .withStyle(ChatFormatting.AQUA));
            }
            lines.add(Component.translatable(appearance.favorite()
                    ?"yoiko_core.turtle.appearance.favorite.remove":"yoiko_core.turtle.appearance.favorite.add").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
    }

    /** Each fixed catalogue color is precomposed as one 16x16 texture; no per-frame item/mask layering. */
    private static void renderPaintBucket(GuiGraphics graphics,int x,int y,String appearanceId){
        ResourceLocation texture=APPEARANCE_BUCKET_TEXTURE_CACHE.computeIfAbsent(appearanceId,id->
                YoikoServerCore.id("textures/gui/turtle/shell_palette/"+id+".png"));
        graphics.blit(texture,x,y,16,16,0.0F,0.0F,16,16,16,16);
    }

    private void renderAppearancePurchaseModal(GuiGraphics graphics,int right,int top,int mouseX,int mouseY,float partialTick){
        AppearanceView appearance=appearanceById(appearanceConfirmId);
        if(appearance==null)return;
        int x=right+14,y=top+82;
        YoikoScreenStyle.renderTurtleDialogPanel(graphics,right+6,top+33);
        graphics.fill(x,y,x+164,y+76,0x20D4B982);
        graphics.drawCenteredString(font,Component.translatable("yoiko_core.turtle.appearance.purchase.title"),x+82,y+8,PARCHMENT_GOLD);
        graphics.drawCenteredString(font,appearanceName(appearance.id()),x+82,y+23,PARCHMENT_TEXT);
        graphics.drawCenteredString(font,Component.translatable("yoiko_core.turtle.appearance.purchase.summary",appearance.gemPrice(),Math.max(0,data.gems()-appearance.gemPrice())),x+82,y+37,PARCHMENT_TEAL);
        for(Button button:appearanceModalButtons)button.render(graphics,mouseX,mouseY,partialTick);
    }

    private static void renderUnlockIcon(GuiGraphics graphics,AppearanceView appearance,int x,int y){
        Item item;
        if(appearance.unlockKind().equals("GEM"))item=Items.EMERALD;
        else if(appearance.unlockKey().startsWith("official_finish")||appearance.unlockKey().equals("golden_shell_mastery"))item=Items.GOLD_INGOT;
        else if(appearance.unlockKey().equals("time_trial_finish"))item=Items.CLOCK;
        else if(appearance.unlockKey().equals("companion_5"))item=Items.LEAD;
        else item=Items.NETHER_STAR;
        graphics.pose().pushPose();
        graphics.pose().translate(x,y,280.0F);
        graphics.pose().scale(.5F,.5F,1.0F);
        graphics.renderItem(item.getDefaultInstance(),0,0);
        graphics.pose().popPose();
    }

    private static String progressBar(int current,int target){
        int segments=5;
        int filled=target<=0?0:Math.min(segments,(int)Math.floor((double)Math.max(0,current)*segments/target));
        return "■".repeat(filled)+"□".repeat(segments-filled);
    }

    private void renderAppearanceSparkle(GuiGraphics graphics,int x,int y){
        int phase=(24-appearanceFeedbackTicks)/3;
        int color=(phase&1)==0?0xFFFFFF9B:0xFF72F1FF;
        for(int i=0;i<SPARKLE_X.length;i++)if((i+phase)%2==0){
            int px=x+SPARKLE_X[i],py=y+SPARKLE_Y[i];
            graphics.fill(px-1,py,px+2,py+1,color);
            graphics.fill(px,py-1,px+1,py+2,color);
        }
    }

    private record ConditionIcon(ResourceLocation icon,String tooltipKey,int sourceX,int sourceSize,
                                 int textureWidth,int textureHeight,int displaySize,int color){ }

    private record AppearanceView(OpenTurtleMenuPayload.AppearanceDefinition definition,
                                  OpenTurtleMenuPayload.AppearanceState state){
        private String id(){return definition.id();}
        private String unlockKind(){return definition.unlockKind();}
        private String unlockKey(){return definition.unlockKey();}
        private int gemPrice(){return definition.gemPrice();}
        private boolean unlocked(){return state.unlocked();}
        private boolean favorite(){return state.favorite();}
        private int progress(){return state.progress();}
        private int target(){return state.target();}
    }

    private static int raceClassColor(String raceClass) {
        return switch (raceClass) {
            case "S" -> 0xFFFFB84A;
            case "A" -> 0xFFC784FF;
            case "B" -> 0xFF54BFFF;
            case "C" -> 0xFF6DD27C;
            default -> 0xFF9DA4AD;
        };
    }
}
