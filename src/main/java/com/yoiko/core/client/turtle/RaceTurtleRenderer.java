package com.yoiko.core.client.turtle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.turtle.entity.RaceTurtleEntity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.TurtleModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TurtleRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class RaceTurtleRenderer extends TurtleRenderer {
    private static final int OWNER_MARKER = 0xFF39E6E6;
    private static final int OWNER_MARKER_SHADOW = 0xFF062C33;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final double AIM_DISTANCE = 160.0;
    private static final int AIM_NAME_HOLD_TICKS = 40;
    private static UUID aimedTurtle;
    private static long aimedUntilTick = Long.MIN_VALUE;
    private static long lastAimUpdateTick = Long.MIN_VALUE;
    private static final Map<String,ResourceLocation> BODY_TEXTURES=Map.ofEntries(
            Map.entry("natural",bodyTexture("natural")),Map.entry("moss",bodyTexture("moss")),
            Map.entry("sand",bodyTexture("sand")),Map.entry("umber",bodyTexture("umber")),
            Map.entry("slate",bodyTexture("slate")),Map.entry("leucistic",bodyTexture("leucistic")),
            Map.entry("melanistic",bodyTexture("melanistic")),Map.entry("gold",bodyTexture("gold")));
    private static final Map<String,ResourceLocation> SHELL_TEXTURES=Map.ofEntries(
            Map.entry("natural",shellTexture("natural")),Map.entry("emerald",shellTexture("emerald")),
            Map.entry("ocean",shellTexture("ocean")),Map.entry("sky",shellTexture("sky")),
            Map.entry("violet",shellTexture("violet")),Map.entry("rose",shellTexture("rose")),
            Map.entry("amber",shellTexture("amber")),Map.entry("obsidian",shellTexture("obsidian")),
            Map.entry("coral",shellTexture("coral")),Map.entry("pearl",shellTexture("pearl")),
            Map.entry("frost",shellTexture("frost")),Map.entry("copper",shellTexture("copper")),
            Map.entry("mint",shellTexture("mint")),Map.entry("crimson",shellTexture("crimson")),
            Map.entry("gold",shellTexture("gold")),Map.entry("midnight",shellTexture("midnight")));

    public RaceTurtleRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new RaceTurtleShellLayer(this));
    }

    @Override public ResourceLocation getTextureLocation(Turtle entity) {
        return entity instanceof RaceTurtleEntity race
                ?BODY_TEXTURES.getOrDefault(race.bodyAppearance(),BODY_TEXTURES.get("natural"))
                :BODY_TEXTURES.get("natural");
    }

    static ResourceLocation shellTextureFor(Turtle entity) {
        return entity instanceof RaceTurtleEntity race
                ? SHELL_TEXTURES.getOrDefault(race.appearance(), SHELL_TEXTURES.get("natural"))
                : SHELL_TEXTURES.get("natural");
    }

    @Override public boolean shouldRender(Turtle entity, net.minecraft.client.renderer.culling.Frustum camera, double camX, double camY, double camZ) {
        return super.shouldRender(entity,camera,camX,camY,camZ) || entity.distanceToSqr(camX,camY,camZ) < 160.0*160.0;
    }

    @Override public void render(Turtle entity,float yaw,float partialTick,PoseStack pose,MultiBufferSource buffers,int light) {
        super.render(entity,yaw,partialTick,pose,buffers,light);
        Minecraft minecraft=Minecraft.getInstance();
        if(!(entity instanceof RaceTurtleEntity race)||minecraft.player==null)return;
        boolean owner=race.ownerId().isPresent()&&race.ownerId().get().equals(minecraft.player.getUUID());
        if(race.activeSkillHighlighted())renderOutline(entity,partialTick,pose,light,0xFFD34E);
        if(owner)renderOwnerMarker(entity,partialTick,pose,buffers);
    }

    @Override protected void renderNameTag(Turtle entity,Component label,PoseStack pose,MultiBufferSource buffers,int light,float partialTick) {
        if(!(entity instanceof RaceTurtleEntity race)){
            super.renderNameTag(entity,label,pose,buffers,light,partialTick);
            return;
        }
        Minecraft minecraft=Minecraft.getInstance();
        updateAimedTurtle(minecraft);
        boolean owner=minecraft.player!=null&&race.ownerId().filter(minecraft.player.getUUID()::equals).isPresent();
        boolean aimed=entity.getUUID().equals(aimedTurtle)&&minecraft.level!=null&&minecraft.level.getGameTime()<=aimedUntilTick;
        super.renderNameTag(entity,owner||aimed?label:compactLaneLabel(label),pose,buffers,light,partialTick);
    }

    private static Component compactLaneLabel(Component fullLabel){
        String text=fullLabel.getString();
        int end=text.indexOf(']');
        if(!text.startsWith("[")||end<2)return fullLabel; // Podium and result labels remain intact.
        return Component.literal(text.substring(0,end+1)).setStyle(fullLabel.getStyle());
    }

    /** Race turtles deliberately are not pickable, so use a narrow, wall-aware client ray for name inspection. */
    private static void updateAimedTurtle(Minecraft minecraft){
        if(minecraft.level==null||minecraft.player==null){aimedTurtle=null;aimedUntilTick=Long.MIN_VALUE;lastAimUpdateTick=Long.MIN_VALUE;return;}
        long tick=minecraft.level.getGameTime();
        if(tick==lastAimUpdateTick)return;
        lastAimUpdateTick=tick;
        Vec3 eye=minecraft.player.getEyePosition();
        Vec3 look=minecraft.player.getViewVector(1.0F);
        Vec3 farEnd=eye.add(look.scale(AIM_DISTANCE));
        var blockHit=minecraft.level.clip(new ClipContext(eye,farEnd,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,minecraft.player));
        Vec3 end=blockHit.getType()==HitResult.Type.MISS?farEnd:blockHit.getLocation();
        AABB search=new AABB(eye,end).inflate(.65);
        RaceTurtleEntity nearest=null;
        double nearestDistance=Double.MAX_VALUE;
        for(RaceTurtleEntity candidate:minecraft.level.getEntitiesOfClass(RaceTurtleEntity.class,search)){
            Optional<Vec3> hit=candidate.getBoundingBox().inflate(.42).clip(eye,end);
            if(hit.isEmpty())continue;
            double distance=eye.distanceToSqr(hit.get());
            if(distance<nearestDistance){nearest=candidate;nearestDistance=distance;}
        }
        if(nearest!=null){aimedTurtle=nearest.getUUID();aimedUntilTick=tick+AIM_NAME_HOLD_TICKS;}
        else if(tick>aimedUntilTick)aimedTurtle=null;
    }

    private void renderOutline(Turtle entity,float partialTick,PoseStack pose,int light,int color){
        Minecraft minecraft=Minecraft.getInstance();
        pose.pushPose();
        float bodyYaw=Mth.rotLerp(partialTick,entity.yBodyRotO,entity.yBodyRot);
        float age=getBob(entity,partialTick);
        float scale=entity.getScale();
        pose.scale(scale,scale,scale);
        setupRotations(entity,pose,age,bodyYaw,partialTick,scale);
        pose.scale(-1.0F,-1.0F,1.0F);
        scale(entity,pose,partialTick);
        pose.translate(0.0F,-1.501F,0.0F);
        TurtleModel<Turtle> model=getModel();
        float walkSpeed=entity.walkAnimation.speed(partialTick);
        float walkPosition=entity.walkAnimation.position(partialTick)*3.0F;
        model.young=true;
        model.prepareMobModel(entity,walkPosition,walkSpeed,partialTick);
        model.setupAnim(entity,walkPosition,walkSpeed,age,Mth.wrapDegrees(Mth.rotLerp(partialTick,entity.yHeadRotO,entity.yHeadRot)-bodyYaw),Mth.lerp(partialTick,entity.xRotO,entity.getXRot()));
        var outlines=minecraft.renderBuffers().outlineBufferSource();
        outlines.setColor((color>>16)&255,(color>>8)&255,color&255,255);
        minecraft.levelRenderer.requestOutlineEffect();
        // Body and shell are separate texture layers. Rendering only the body texture leaves the
        // transparent shell UV out of the outline buffer, so merge both masks into one silhouette.
        model.renderToBuffer(pose,outlines.getBuffer(RenderType.outline(getTextureLocation(entity))),
                light,OverlayTexture.NO_OVERLAY,0xFFFFFFFF);
        model.renderToBuffer(pose,outlines.getBuffer(RenderType.outline(shellTextureFor(entity))),
                light,OverlayTexture.NO_OVERLAY,0xFFFFFFFF);
        pose.popPose();
    }

    /** Keeps the outline channel exclusive to active skills. */
    private void renderOwnerMarker(Turtle entity,float partialTick,PoseStack pose,MultiBufferSource buffers){
        Component marker=Component.literal("▼").withStyle(ChatFormatting.AQUA,ChatFormatting.BOLD);
        float bob=Mth.sin((entity.tickCount+partialTick)*0.16F)*0.035F;
        pose.pushPose();
        pose.translate(0,0.62F+bob,0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(-.078F,-.078F,.078F);
        float x=-getFont().width(marker)/2.0F;
        getFont().drawInBatch(marker,x+0.8F,0.8F,OWNER_MARKER_SHADOW,false,pose.last().pose(),buffers,
                Font.DisplayMode.SEE_THROUGH,0,FULL_BRIGHT);
        getFont().drawInBatch(marker,x,0,OWNER_MARKER,false,pose.last().pose(),buffers,
                Font.DisplayMode.SEE_THROUGH,0xD0061218,FULL_BRIGHT);
        pose.popPose();
    }

    private static ResourceLocation texture(String id){return YoikoServerCore.id("textures/entity/turtle/"+id+".png");}
    private static ResourceLocation bodyTexture(String id){return texture("body/"+id);}
    private static ResourceLocation shellTexture(String id){return YoikoServerCore.id("textures/entity/turtle/shell/"+id+".png");}
}
