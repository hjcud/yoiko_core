package com.yoiko.core.client.screen;

import com.yoiko.core.network.OpenTurtleStrategyTicketPayload;
import com.yoiko.core.network.TurtleStrategyTicketChoosePayload;
import com.yoiko.core.network.TurtleStrategyTicketCancelPayload;
import com.yoiko.core.registry.YoikoItems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Compact no-blur strategy picker opened by the unified rare turtle egg. */
public final class TurtleStrategyTicketScreen extends Screen {
    private static final String[] STRATEGIES={"FRONT","STEADY","FOLLOW","CLOSER"};
    private static final int[] ACCENTS={0xD87842,0x5995A3,0x55A274,0x80699B};
    private static final int PANEL_WIDTH=276;
    private static final int PANEL_HEIGHT=216;

    private final OpenTurtleStrategyTicketPayload payload;
    private final Map<Button,String> strategyButtons=new LinkedHashMap<>();
    private String selectedStrategy;
    private Button confirmButton;
    private boolean submitted;
    private int panelX;
    private int panelY;

    public TurtleStrategyTicketScreen(OpenTurtleStrategyTicketPayload payload) {
        super(Component.translatable("yoiko_core.turtle.strategy_ticket.title"));
        this.payload=payload;
    }

    @Override
    protected void init() {
        strategyButtons.clear();
        panelX=(width-PANEL_WIDTH)/2;
        panelY=(height-PANEL_HEIGHT)/2;
        for(int i=0;i<STRATEGIES.length;i++){
            String strategy=STRATEGIES[i];
            int column=i%2,row=i/2;
            boolean selected=strategy.equals(selectedStrategy);
            Component label=Component.literal(selected?"◆ ":"◇ ")
                    .append(Component.translatable(strategyKey(strategy,"name")));
            Button button=YoikoButton.create(panelX+12+column*132,panelY+45+row*34,128,28,
                            label,ignored->select(strategy))
                    .withParchmentStyle().withoutTextShadow().withAccent(selected?ACCENTS[i]:0);
            strategyButtons.put(button,strategy);
            addRenderableWidget(button);
        }
        confirmButton=YoikoButton.create(panelX+82,panelY+177,112,20,
                        Component.translatable("yoiko_core.turtle.strategy_ticket.confirm"),ignored->confirm())
                .withParchmentStyle().withoutTextShadow().withAccent(0xC7923C);
        confirmButton.active=selectedStrategy!=null;
        addRenderableWidget(confirmButton);
    }

    private void select(String strategy) {
        selectedStrategy=strategy;
        rebuildWidgets();
    }

    private void confirm() {
        if(selectedStrategy==null)return;
        submitted=true;
        PacketDistributor.sendToServer(new TurtleStrategyTicketChoosePayload(payload.sessionId(),selectedStrategy));
        onClose();
    }

    @Override
    public void onClose() {
        if(!submitted)PacketDistributor.sendToServer(new TurtleStrategyTicketCancelPayload(payload.sessionId()));
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        // Keep the world readable; this selector deliberately avoids Minecraft's full-screen blur.
        graphics.fill(0,0,width,height,0x50000000);
        YoikoScreenStyle.renderTurtleCard(graphics,panelX,panelY,PANEL_WIDTH,PANEL_HEIGHT);
        graphics.renderItem(YoikoItems.TURTLE_RARE_STRATEGY_TICKET.get().getDefaultInstance(),panelX+13,panelY+10);
        graphics.drawString(font,title,panelX+35,panelY+10,0xFF5E4B35,false);
        String prompt=font.plainSubstrByWidth(Component.translatable("yoiko_core.turtle.strategy_ticket.prompt").getString(),
                PANEL_WIDTH-48);
        graphics.drawString(font,prompt,panelX+35,panelY+23,0xFF89755D,false);
        graphics.fill(panelX+10,panelY+38,panelX+PANEL_WIDTH-10,panelY+39,0x55B88D55);
        super.render(graphics,mouseX,mouseY,partialTick);
        graphics.fill(panelX+10,panelY+112,panelX+PANEL_WIDTH-10,panelY+113,0x55B88D55);
        if(selectedStrategy==null){
            graphics.drawCenteredString(font,Component.translatable("yoiko_core.turtle.strategy_ticket.select_hint"),
                    panelX+PANEL_WIDTH/2,panelY+139,0xFF89755D);
        }else{
            int accent=ACCENTS[java.util.Arrays.asList(STRATEGIES).indexOf(selectedStrategy)];
            graphics.drawString(font,Component.translatable(strategyKey(selectedStrategy,"name")),panelX+14,panelY+118,
                    0xFF000000|accent,false);
            drawProfileLine(graphics,"stats",panelY+131);
            drawProfileLine(graphics,"position",panelY+142);
            drawProfileLine(graphics,"stamina",panelY+153);
            drawProfileLine(graphics,"finish",panelY+164);
        }
        String cancelHint=font.plainSubstrByWidth(Component.translatable("yoiko_core.turtle.strategy_ticket.cancel_hint").getString(),
                PANEL_WIDTH-20);
        graphics.drawCenteredString(font,cancelHint,panelX+PANEL_WIDTH/2,panelY+202,0xFF89755D);
        for(var entry:strategyButtons.entrySet()){
            if(!entry.getKey().isHoveredOrFocused())continue;
            String strategy=entry.getValue();
            graphics.renderComponentTooltip(font,List.of(
                    Component.translatable(strategyKey(strategy,"name")).withStyle(ChatFormatting.AQUA),
                    Component.translatable(strategyKey(strategy,"description")).withStyle(ChatFormatting.GRAY),
                    Component.translatable("yoiko_core.turtle.strategy_ticket.select_then_confirm").withStyle(ChatFormatting.YELLOW)
            ),mouseX,mouseY);
            break;
        }
    }

    private void drawProfileLine(GuiGraphics graphics,String field,int y) {
        Component label=Component.translatable("yoiko_core.turtle.strategy_ticket.info."+field,
                Component.translatable("yoiko_core.turtle.strategy_ticket."+selectedStrategy.toLowerCase(Locale.ROOT)+"."+field));
        graphics.drawString(font,font.plainSubstrByWidth(label.getString(),PANEL_WIDTH-28),panelX+14,y,0xFF5E4B35,false);
    }

    private static String strategyKey(String strategy,String suffix) {
        return "yoiko_core.turtle.strategy."+strategy.toLowerCase(Locale.ROOT)+"."+suffix;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
