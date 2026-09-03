package com.yoiko.core.cosmetic;

import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.economy.EconomyManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class ParticleGachaManager {
    private ParticleGachaManager() {
    }

    /** Rolls only among unowned, non-admin particle cosmetics, so this exceptionally rare ticket is never wasted. */
    public static boolean roll(ServerPlayer player, ItemStack ticket) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        List<CosmeticData> candidates = new ArrayList<>();
        for (CosmeticData cosmetic : CosmeticManager.particleCatalog()) {
            if (!CosmeticManager.isAdminOnlyCosmetic(cosmetic.id()) && !data.ownedCosmetics.contains(cosmetic.id())) {
                candidates.add(cosmetic);
            }
        }
        if (candidates.isEmpty()) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.particle_gacha.complete")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }

        int totalWeight = 0;
        for (CosmeticData cosmetic : candidates) {
            totalWeight += cosmetic.rarity() == CosmeticRarity.MYTHIC ? 1 : 4;
        }
        int roll = player.getRandom().nextInt(Math.max(1, totalWeight));
        CosmeticData selected = candidates.getFirst();
        for (CosmeticData cosmetic : candidates) {
            roll -= cosmetic.rarity() == CosmeticRarity.MYTHIC ? 1 : 4;
            if (roll < 0) {
                selected = cosmetic;
                break;
            }
        }
        if (!CosmeticManager.grant(player, selected.id(), false)) {
            return false;
        }
        if (!player.isCreative()) {
            ticket.shrink(1);
        }
        EconomyManager.recordEconomy(player, "PARTICLE_GACHA_UNLOCK", 1L,
                "cosmetic=" + selected.id() + ";rarity=" + selected.rarity().name());
        YoikoAdvancementManager.recordParticleGacha(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 0.8F, selected.rarity() == CosmeticRarity.MYTHIC ? 1.25F : 1.05F);
        player.sendSystemMessage(Component.translatable(
                "yoiko_core.message.particle_gacha.result",
                displayName(selected),
                Component.translatable(selected.rarity().translationKey())
        ).withStyle(selected.rarity() == CosmeticRarity.MYTHIC
                ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        return true;
    }

    private static Component displayName(CosmeticData cosmetic) {
        String name = cosmetic.displayName();
        return name.contains(".") ? Component.translatable(name) : Component.literal(name);
    }
}
