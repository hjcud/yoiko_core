package com.yoiko.core.client.turtle;

import com.mojang.math.Axis;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.network.TurtleRaceHudPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Top-centre race progress rail with every marker fixed to one row. */
public final class ClientTurtleRaceHud {
    public static final ClientTurtleRaceHud INSTANCE = new ClientTurtleRaceHud();

    private static final int SURFACE_HEIGHT = 3;
    private static final long INTERPOLATION_MILLIS = 230L;
    private static final long RANK_LAYER_STABILITY_MILLIS = 300L;
    private static final long STALE_MILLIS = 1_200L;
    private static final long SKILL_FLASH_MILLIS = 900L;
    private static final ResourceLocation NUMBER_MARKERS =
            YoikoServerCore.id("textures/gui/turtle/numbers.png");
    private static final ResourceLocation LEADER_CROWN =
            YoikoServerCore.id("textures/gui/turtle/icons/race_leader_crown.png");
    private static final int NUMBER_TEXTURE_WIDTH = 48;
    private static final int NUMBER_TEXTURE_HEIGHT = 101;
    private static final int SPRITE_HEIGHT = 5;
    private static final int ROW_STRIDE = 6;
    private static final int DIGIT_WIDTH = 3;
    private static final int DIGIT_STRIDE = 4;
    private static final int MARKER_U = 40;
    private static final int MARKER_SOURCE_WIDTH = 8;
    private static final int MARKER_RENDER_WIDTH = 5;
    private static final int MARKER_RENDER_HEIGHT = 8;
    private static final int CROWDED_MARKER_SPACING = 4;
    private static final int ROW_WHITE = 0;
    private static final int ROW_DARK = 1;
    private static final int ROW_OWN = 11;
    private static final int ROW_SKILL = 12;
    private static final int[] ENTRY_MARKER_ROWS = {3, 4, 5, 6, 7, 8, 9, 10};
    private long sessionId;
    private long lastUpdateMillis;
    private int ownEntryNumber;
    private List<TurtleRaceHudPayload.SurfaceSegment> surfaces = List.of();
    private Map<Integer, Motion> motions = Map.of();
    private Map<Integer, Boolean> activeStates = Map.of();
    private final Map<Integer, Long> skillFlashUntil = new HashMap<>();
    private final Map<Integer, Integer> stableDrawRanks = new HashMap<>();
    private final Map<Integer, Integer> pendingDrawRanks = new HashMap<>();
    private final Map<Integer, Long> pendingDrawRankSince = new HashMap<>();
    private boolean visible;

    private ClientTurtleRaceHud() {
    }

    public void update(TurtleRaceHudPayload payload) {
        long now = System.currentTimeMillis();
        if (!payload.visible()) {
            clear();
            return;
        }
        boolean newSession = !visible || sessionId != payload.sessionId();
        if(newSession){stableDrawRanks.clear();pendingDrawRanks.clear();pendingDrawRankSince.clear();}
        Map<Integer, Motion> next = new HashMap<>();
        Map<Integer, Boolean> nextActiveStates = new HashMap<>();
        for (TurtleRaceHudPayload.Marker marker : payload.markers()) {
            double targetProgress = marker.progressU16() / 65_535.0;
            Motion previous = newSession ? null : motions.get(marker.entryNumber());
            double fromProgress = previous == null ? targetProgress : previous.progress(now);
            if (targetProgress + .20 < fromProgress) fromProgress = targetProgress;
            updateDrawRank(marker.entryNumber(),marker.currentRank(),now,newSession);
            next.put(marker.entryNumber(),new Motion(fromProgress,targetProgress,marker.laneU8(),marker.currentRank(),marker.finishRank(),now));
            boolean wasActive = !newSession && activeStates.getOrDefault(marker.entryNumber(), false);
            if (marker.activeEffect() && !wasActive)
                skillFlashUntil.put(marker.entryNumber(), now + SKILL_FLASH_MILLIS);
            nextActiveStates.put(marker.entryNumber(), marker.activeEffect());
        }
        sessionId = payload.sessionId();
        ownEntryNumber = payload.ownEntryNumber();
        surfaces = payload.surfaces();
        motions = Map.copyOf(next);
        activeStates = Map.copyOf(nextActiveStates);
        skillFlashUntil.entrySet().removeIf(entry -> entry.getValue() <= now || !motions.containsKey(entry.getKey()));
        stableDrawRanks.keySet().removeIf(entryNumber->!motions.containsKey(entryNumber));
        pendingDrawRanks.keySet().removeIf(entryNumber->!motions.containsKey(entryNumber));
        pendingDrawRankSince.keySet().removeIf(entryNumber->!motions.containsKey(entryNumber));
        lastUpdateMillis = now;
        visible = !motions.isEmpty();
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (!visible || now - lastUpdateMillis > STALE_MILLIS || minecraft.options.hideGui
                || minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        boolean compact = graphics.guiWidth() < 220 || graphics.guiHeight() < 160;
        HudLayout layout = compact ? HudLayout.COMPACT : HudLayout.NORMAL;
        int panelWidth = Math.max(160, Math.min(260, graphics.guiWidth() - 20));
        if (graphics.guiWidth() < 180) panelWidth = Math.max(120, graphics.guiWidth() - 8);
        int panelX = (graphics.guiWidth() - panelWidth) / 2;
        int panelY = 3;
        int trackX = panelX + layout.leftMargin();
        int trackRight = panelX + panelWidth - layout.rightMargin();
        int trackWidth = Math.max(60, trackRight - trackX);
        // Keep the surface strip on the same visual row as the start/finish labels. The labels
        // occupy the side margins, so this removes a mostly empty header row without overlap.
        int surfaceY = panelY + layout.surfaceOffset();
        int markerTop = panelY + layout.markerTopOffset();
        int railAnchorY = surfaceY + SURFACE_HEIGHT + 1;
        int finishSpace=Math.max(8,graphics.guiWidth()-2-(trackRight+layout.finishGap()));
        int finishPitch=Math.max(1,Math.min(layout.finishPitch(),finishSpace/8));

        graphics.drawCenteredString(minecraft.font, Component.translatable("yoiko_core.turtle.race_hud.start"),
                panelX + layout.headerInset(), panelY, 0xFFD8DEE5);
        graphics.drawCenteredString(minecraft.font, Component.translatable("yoiko_core.turtle.race_hud.finish"),
                panelX + panelWidth - layout.headerInset(), panelY, 0xFFD8DEE5);
        drawSurfaceStrip(graphics, trackX, surfaceY, trackWidth);

        List<MarkerView> views = new ArrayList<>(motions.size());
        for (Map.Entry<Integer, Motion> value : motions.entrySet()) {
            int number = value.getKey();
            Motion motion = value.getValue();
            double progress = Math.max(0, Math.min(1, value.getValue().progress(now)));
            int minimumX = trackX + layout.markerWidth() / 2 + 2;
            int maximumX = trackRight - layout.markerWidth() / 2 - 1;
            int markerY=markerTop+laneOffset(motion.laneU8());
            int drawRank=drawRank(number,now,motion.currentRank());
            if (motion.finishRank() > 0) {
                int finishX=trackRight+layout.finishGap()+(9-motion.finishRank())*finishPitch;
                views.add(new MarkerView(number,trackRight,finishX,motion.currentRank(),drawRank,motion.finishRank(),true,markerY));
            } else {
                int exactX = minimumX + (int) Math.round(progress * Math.max(1, maximumX - minimumX));
                views.add(new MarkerView(number,exactX,exactX,motion.currentRank(),drawRank,0,false,markerY));
            }
        }
        spreadCrowdedMarkers(views,trackX+layout.markerWidth()/2+2,
                trackRight-layout.markerWidth()/2-1);
        views.sort(Comparator.comparingInt((MarkerView view)->view.number()==ownEntryNumber?1:0)
                .thenComparing(Comparator.comparingInt(MarkerView::drawRank).reversed())
                .thenComparingInt(MarkerView::number));
        for(MarkerView view:views)if(!view.finished())drawMarkerConnector(graphics,view,railAnchorY);
        for(MarkerView view:views){
            drawMarker(graphics,view,now,layout);
            if(view.currentRank()==1)drawLeaderCrown(graphics,view,layout);
        }
    }

    private void drawSurfaceStrip(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x - 1, y - 1, x + width + 2, y + SURFACE_HEIGHT + 1, 0xFF151A1F);
        if (surfaces.isEmpty()) {
            graphics.fill(x, y, x + width + 1, y + SURFACE_HEIGHT, surfaceColor(0));
            return;
        }
        for (TurtleRaceHudPayload.SurfaceSegment segment : surfaces) {
            int from = x + (int) Math.floor(segment.startU16() / 65_535.0 * width);
            int to = x + (int) Math.ceil(segment.endU16() / 65_535.0 * width);
            int end = Math.max(from + 1, to);
            graphics.fill(from, y, end, y + SURFACE_HEIGHT, surfaceColor(segment.surface()));
            drawSurfacePattern(graphics, from, end, y, segment.surface());
            if (from > x) graphics.fill(from, y, from + 1, y + SURFACE_HEIGHT, 0xB922282E);
        }
        graphics.fill(x, y, x + width + 1, y + 1, 0x55FFFFFF);
    }

    private void drawSurfacePattern(GuiGraphics graphics, int from, int to, int y, int surface) {
        switch (surface) {
            case 1 -> {
                for (int x = from + 1; x < to; x += 4)
                    graphics.fill(x, y + 1, Math.min(to, x + 2), y + 2, 0xFF5F4638);
            }
            case 2 -> {
                for (int x = from; x < to; x++) {
                    int waveY = y + (Math.floorMod(x, 4) < 2 ? 1 : 2);
                    graphics.fill(x, waveY, x + 1, waveY + 1, 0xFF83D3E3);
                }
            }
            default -> graphics.fill(from, y, to, y + 1, 0xFFE0C57D);
        }
    }

    private void drawMarkerConnector(GuiGraphics graphics,MarkerView view,int railAnchorY){
        boolean own=view.number()==ownEntryNumber;
        drawLine(graphics,view.exactX(),railAnchorY,view.displayX,view.displayY,own?0xD0A8E9DE:0x806E7A84);
    }

    private void drawMarker(GuiGraphics graphics,MarkerView view,long now,HudLayout layout) {
        int centerX = view.displayX;
        int markerX = centerX - layout.markerWidth() / 2;
        boolean own=view.number()==ownEntryNumber;

        Long flashUntil = skillFlashUntil.get(view.number());
        if (flashUntil != null && flashUntil > now) {
            int pulseOffset = ((now / 90L) & 1L) == 0L ? 2 : 1;
            drawMarkerHalo(graphics, markerX, view.displayY(), ROW_SKILL, pulseOffset);
        }
        if (own) drawMarkerHalo(graphics, markerX, view.displayY(), ROW_OWN, 1);
        int entryIndex = Math.max(0, Math.min(ENTRY_MARKER_ROWS.length - 1, view.number() - 1));
        drawMarkerSprite(graphics, markerX, view.displayY(), ENTRY_MARKER_ROWS[entryIndex]);
        int digitRow = switch (view.number()) {
            case 3, 4, 5, 8 -> ROW_DARK;
            default -> ROW_WHITE;
        };
        drawDigit(graphics, view.number(), markerX + 1, view.displayY() + 2, digitRow);
    }

    private static void drawLeaderCrown(GuiGraphics graphics,MarkerView view,HudLayout layout){
        int x=view.displayX-layout.markerWidth()/2;
        // Overlap the crown's bottom row with the marker tip so it reads as worn,
        // rather than as a separate sprite resting above it.
        int y=view.displayY()-2;
        graphics.blit(LEADER_CROWN,x,y,0,0,5,3,5,3);
    }

    private void updateDrawRank(int entryNumber,int currentRank,long now,boolean reset){
        Integer stable=stableDrawRanks.get(entryNumber);
        if(reset||stable==null){
            stableDrawRanks.put(entryNumber,currentRank);
            pendingDrawRanks.remove(entryNumber);
            pendingDrawRankSince.remove(entryNumber);
        }else if(stable==currentRank){
            pendingDrawRanks.remove(entryNumber);
            pendingDrawRankSince.remove(entryNumber);
        }else if(pendingDrawRanks.getOrDefault(entryNumber,0)!=currentRank){
            pendingDrawRanks.put(entryNumber,currentRank);
            pendingDrawRankSince.put(entryNumber,now);
        }
    }

    private int drawRank(int entryNumber,long now,int fallback){
        Integer pending=pendingDrawRanks.get(entryNumber);
        Long since=pendingDrawRankSince.get(entryNumber);
        if(pending!=null&&since!=null&&now-since>=RANK_LAYER_STABILITY_MILLIS){
            stableDrawRanks.put(entryNumber,pending);
            pendingDrawRanks.remove(entryNumber);
            pendingDrawRankSince.remove(entryNumber);
        }
        return stableDrawRanks.getOrDefault(entryNumber,fallback);
    }

    /**
     * Fans out only genuinely overlapping racers. The connector keeps the exact course position
     * readable while the short formation leaves each number visible. Stable draw rank prevents
     * two neck-and-neck racers from continuously swapping sides on every interpolated frame.
     */
    private static void spreadCrowdedMarkers(List<MarkerView> views,int minimumX,int maximumX){
        List<MarkerView> active=views.stream().filter(view->!view.finished())
                .sorted(Comparator.comparingInt(MarkerView::exactX)
                        .thenComparing(Comparator.comparingInt(MarkerView::drawRank).reversed())
                        .thenComparingInt(MarkerView::number))
                .toList();
        int clusterStart=0;
        while(clusterStart<active.size()){
            int clusterEnd=clusterStart+1;
            while(clusterEnd<active.size()
                    &&active.get(clusterEnd).exactX()-active.get(clusterEnd-1).exactX()
                    <=MARKER_RENDER_WIDTH)clusterEnd++;
            spreadCluster(active.subList(clusterStart,clusterEnd),minimumX,maximumX);
            clusterStart=clusterEnd;
        }
    }

    private static void spreadCluster(List<MarkerView> cluster,int minimumX,int maximumX){
        if(cluster.size()<2)return;
        List<MarkerView> ordered=cluster.stream()
                .sorted(Comparator.comparingInt(MarkerView::drawRank).reversed()
                        .thenComparingInt(MarkerView::number))
                .toList();
        int center=(int)Math.round(cluster.stream().mapToInt(MarkerView::exactX).average().orElse(minimumX));
        int span=CROWDED_MARKER_SPACING*(ordered.size()-1);
        int start=Math.max(minimumX,Math.min(center-span/2,maximumX-span));
        for(int index=0;index<ordered.size();index++)
            ordered.get(index).displayX=start+index*CROWDED_MARKER_SPACING;
    }

    private static int laneOffset(int laneU8){
        return (int)Math.round(Math.max(0,Math.min(255,laneU8))/255.0*2.0);
    }

    private static void drawMarkerHalo(GuiGraphics graphics, int x, int y, int row, int offset) {
        drawMarkerSprite(graphics, x - offset, y, row);
        drawMarkerSprite(graphics, x + offset, y, row);
        drawMarkerSprite(graphics, x, y - offset, row);
        drawMarkerSprite(graphics, x, y + offset, row);
    }

    private static void drawMarkerSprite(GuiGraphics graphics, int x, int y, int row) {
        graphics.pose().pushPose();
        // The marker tip faces the course rail. The source sprite points
        // right, so rotating counter-clockwise turns its tip upward while preserving its 5x8 box.
        graphics.pose().translate(x, y + MARKER_RENDER_HEIGHT, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(-90.0F));
        graphics.blit(NUMBER_MARKERS, 0, 0, MARKER_U, row * ROW_STRIDE,
                MARKER_SOURCE_WIDTH, SPRITE_HEIGHT, NUMBER_TEXTURE_WIDTH, NUMBER_TEXTURE_HEIGHT);
        graphics.pose().popPose();
    }

    private static void drawDigit(GuiGraphics graphics, int digit, int x, int y, int row) {
        int value = Math.max(0, Math.min(9, digit));
        graphics.blit(NUMBER_MARKERS, x, y, value * DIGIT_STRIDE, row * ROW_STRIDE,
                DIGIT_WIDTH, SPRITE_HEIGHT, NUMBER_TEXTURE_WIDTH, NUMBER_TEXTURE_HEIGHT);
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = error * 2;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }

    private static int surfaceColor(int surface) {
        return switch (surface) {
            case 1 -> 0xFF80604B; // mud
            case 2 -> 0xFF438EA8; // water
            default -> 0xFFC7A760; // sand
        };
    }

    private void clear() {
        visible = false;
        ownEntryNumber = 0;
        surfaces = List.of();
        motions = Map.of();
        activeStates = Map.of();
        skillFlashUntil.clear();
        stableDrawRanks.clear();
        pendingDrawRanks.clear();
        pendingDrawRankSince.clear();
    }

    private record Motion(double progressFrom,double progressTarget,int laneU8,int currentRank,int finishRank,long startedAt) {
        private double interpolation(long now) {
            double t = Math.max(0, Math.min(1, (now - startedAt) / (double) INTERPOLATION_MILLIS));
            return t * t * (3 - 2 * t);
        }

        private double progress(long now) { return progressFrom + (progressTarget - progressFrom) * interpolation(now); }
    }

    private static final class MarkerView {
        private final int number;
        private final int exactX;
        private final int currentRank;
        private final int drawRank;
        private final int finishRank;
        private final boolean finished;
        private int displayX;
        private int displayY;

        private MarkerView(int number,int exactX,int displayX,int currentRank,int drawRank,int finishRank,
                           boolean finished,int displayY) {
            this.number = number;
            this.exactX = exactX;
            this.currentRank = currentRank;
            this.drawRank = drawRank;
            this.finishRank = finishRank;
            this.finished = finished;
            this.displayX = displayX;
            this.displayY = displayY;
        }

        private int number() { return number; }
        private int exactX() { return exactX; }
        private int currentRank() { return currentRank; }
        private int drawRank() { return drawRank; }
        private int displayY() { return displayY; }
        private int finishRank() { return finishRank; }
        private boolean finished() { return finished; }
    }

    private record HudLayout(int markerWidth,int surfaceOffset,int markerTopOffset,int leftMargin,
                             int rightMargin,int headerInset,int finishGap,int finishPitch) {
        private static final HudLayout NORMAL = new HudLayout(MARKER_RENDER_WIDTH,4,11,30,36,15,2,6);
        private static final HudLayout COMPACT = new HudLayout(MARKER_RENDER_WIDTH,3,10,18,28,10,2,4);
    }
}
