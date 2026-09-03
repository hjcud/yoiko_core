package com.yoiko.core.client.cosmetic;

import com.yoiko.core.cosmetic.CosmeticAnchor;
import com.yoiko.core.cosmetic.ParticleTrailManager;
import com.yoiko.core.client.screen.ClientMenuSession;
import com.yoiko.core.network.CosmeticEquipmentSyncPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientCosmeticPreviewState {
    private static CosmeticEquipmentSyncPayload.ModelEntry head = CosmeticEquipmentSyncPayload.ModelEntry.EMPTY;
    private static CosmeticEquipmentSyncPayload.ModelEntry chest = CosmeticEquipmentSyncPayload.ModelEntry.EMPTY;
    private static String particleId = "";
    private static String particleCategory = "NONE";
    private static String rankName = "";
    private static int rankColor = 0xFFFFFF;
    private static String equipSlot = "";
    private static boolean timed;
    private static int remainingTicks;
    private static String reopenCosmeticId = "";
    private static CameraType previousCameraType;
    private static boolean cameraChanged;

    private ClientCosmeticPreviewState() {
    }

    public static void set(String cosmeticId, String type, String modelId, String anchor,
                           int primaryColor, int accentColor, String category, String displayName, int color) {
        clear();
        CosmeticEquipmentSyncPayload.ModelEntry entry = new CosmeticEquipmentSyncPayload.ModelEntry(
                cosmeticId, modelId, CosmeticAnchor.fromString(anchor), primaryColor, accentColor);
        if ("HEAD".equals(type)) {
            head = entry;
        } else if ("CHEST".equals(type)) {
            chest = entry;
        } else if ("PARTICLE".equals(type)) {
            particleId = cosmeticId;
            particleCategory = category == null ? "NONE" : category;
        } else if ("RANK".equals(type)) {
            rankName = displayName == null ? "" : displayName;
            rankColor = color;
        }
        equipSlot = equipSlot(type, category);
    }

    public static void startTimed(String cosmeticId, String type, String modelId, String anchor,
                                  int primaryColor, int accentColor, String category,
                                  String displayName, int color, int durationTicks) {
        set(cosmeticId, type, modelId, anchor, primaryColor, accentColor, category, displayName, color);
        timed = true;
        remainingTicks = Math.max(1, durationTicks);
        reopenCosmeticId = cosmeticId;
        Minecraft minecraft = Minecraft.getInstance();
        previousCameraType = minecraft.options.getCameraType();
        if (previousCameraType == CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            cameraChanged = true;
        }
        if (minecraft.player != null) {
            minecraft.gui.setOverlayMessage(Component.translatable(
                    "yoiko_core.message.cosmetic.preview_started", Math.max(1, durationTicks / 20)), false);
        }
    }

    public static void tickTimedPreview(Minecraft minecraft) {
        if (!timed || minecraft.player == null || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        if (--remainingTicks > 0) {
            return;
        }
        String cosmeticId = reopenCosmeticId;
        clear();
        ParticleTrailManager.clear();
        if (!cosmeticId.isBlank()) {
            PacketDistributor.sendToServer(ClientMenuSession.action("cosmetic_select|" + cosmeticId));
        }
    }

    public static boolean isActive() {
        return head.present() || chest.present() || !particleId.isBlank() || !rankName.isBlank();
    }

    public static boolean isTimed() {
        return timed;
    }

    public static boolean overridesSlot(String slot) {
        return isActive() && equipSlot.equals(slot);
    }

    public static boolean overridesParticleCategory(String category) {
        return overridesSlot(equipSlot("PARTICLE", category));
    }

    public static CosmeticEquipmentSyncPayload.ModelEntry head() {
        return head;
    }

    public static CosmeticEquipmentSyncPayload.ModelEntry chest() {
        return chest;
    }

    public static String particleId() {
        return particleId;
    }

    public static String particleCategory() {
        return particleCategory;
    }

    public static String rankName() {
        return rankName;
    }

    public static int rankColor() {
        return rankColor;
    }

    public static void clear() {
        restoreCamera();
        head = CosmeticEquipmentSyncPayload.ModelEntry.EMPTY;
        chest = CosmeticEquipmentSyncPayload.ModelEntry.EMPTY;
        particleId = "";
        particleCategory = "NONE";
        rankName = "";
        rankColor = 0xFFFFFF;
        equipSlot = "";
        timed = false;
        remainingTicks = 0;
        reopenCosmeticId = "";
        previousCameraType = null;
        cameraChanged = false;
    }

    private static String equipSlot(String type, String category) {
        if ("HEAD".equals(type) || "CHEST".equals(type)) {
            return type;
        }
        if (!"PARTICLE".equals(type)) {
            return "";
        }
        return switch (category == null ? "NONE" : category) {
            case "RING", "COMPANION" -> "HEAD";
            case "WINGS", "AURA", "ORBIT" -> "CHEST";
            case "TRAIL" -> "FEET";
            default -> "";
        };
    }

    private static void restoreCamera() {
        if (!cameraChanged || previousCameraType == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            minecraft.options.setCameraType(previousCameraType);
        }
    }
}
