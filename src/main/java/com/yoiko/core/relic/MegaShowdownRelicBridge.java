package com.yoiko.core.relic;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.drop.DropEntry;
import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.drops.LootDroppedEvent;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.network.RelicEffectHudPayload.ValueKind;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/** Optional Mega Showdown integration; all provider classes are resolved only when the mod is loaded. */
public final class MegaShowdownRelicBridge {
    private static final String MEGA_SHOWDOWN_MOD_ID = "mega_showdown";
    private static final ResourceLocation STELLAR_SHARD =
            ResourceLocation.fromNamespaceAndPath(MEGA_SHOWDOWN_MOD_ID, "stellar_tera_shard");
    private static boolean registrationAttempted;
    private static boolean registered;
    private static boolean runtimeFailureLogged;
    private static Field teralizationEnabled;
    private static Field teraShardDropRate;
    private static Field stellarShardDropRate;
    private static Method teraShardForType;

    private MegaShowdownRelicBridge() {
    }

    public static void init() {
        if (registrationAttempted) {
            return;
        }
        registrationAttempted = true;
        if (!ModList.get().isLoaded(MEGA_SHOWDOWN_MOD_ID)) {
            return;
        }

        try {
            ClassLoader loader = MegaShowdownRelicBridge.class.getClassLoader();
            Class<?> config = Class.forName(
                    "com.github.yajatkaul.mega_showdown.config.MegaShowdownConfig", false, loader);
            Class<?> helper = Class.forName(
                    "com.github.yajatkaul.mega_showdown.utils.TeraHelper", false, loader);
            teralizationEnabled = config.getField("teralization");
            teraShardDropRate = config.getField("teraShardDropRate");
            stellarShardDropRate = config.getField("stellarShardDropRate");
            teraShardForType = helper.getMethod("getTeraShardForType", ElementalType.class);

            CobblemonEvents.LOOT_DROPPED.subscribe(Priority.LOWEST, MegaShowdownRelicBridge::onLootDropped);
            registered = true;
            YoikoServerCore.LOGGER.info("Enabled the Mega Showdown relic integration.");
        } catch (LinkageError | ReflectiveOperationException exception) {
            YoikoServerCore.LOGGER.error("Could not register the Mega Showdown relic integration.", exception);
        }
    }

    public static boolean isRegistered() {
        return registered;
    }

    private static void onLootDropped(LootDroppedEvent event) {
        if (!(event.getEntity() instanceof PokemonEntity pokemonEntity)) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }

        double bonus = Math.max(0.0D, Math.min(
                100.0D, RelicManager.effectBonus(player, "mega_shard_find_bonus")));
        if (bonus <= 0.0D) {
            return;
        }

        try {
            if (!teralizationEnabled.getBoolean(null)) {
                return;
            }
            Item primaryShard = (Item) teraShardForType.invoke(
                    null, pokemonEntity.getPokemon().getPrimaryType());
            ResourceLocation primaryShardId = BuiltInRegistries.ITEM.getKey(primaryShard);
            if (hasExistingShard(event, pokemonEntity, primaryShard, primaryShardId)) {
                return;
            }

            double teraChance = chance(teraShardDropRate.getDouble(null));
            double stellarChance = chance(stellarShardDropRate.getDouble(null));
            double multiplier = 1.0D + bonus / 100.0D;
            double boostedTeraChance = Math.min(1.0D, teraChance * multiplier);
            double boostedStellarChance = Math.min(1.0D, stellarChance * multiplier);

            double baseTeraOutcome = teraChance;
            double baseStellarOutcome = (1.0D - teraChance) * stellarChance;
            double boostedTeraOutcome = boostedTeraChance;
            double boostedStellarOutcome = (1.0D - boostedTeraChance) * boostedStellarChance;
            double addedTeraOutcome = Math.max(0.0D, boostedTeraOutcome - baseTeraOutcome);
            double addedStellarOutcome = Math.max(0.0D, boostedStellarOutcome - baseStellarOutcome);
            double originalMissChance = (1.0D - teraChance) * (1.0D - stellarChance);
            double addedOutcome = addedTeraOutcome + addedStellarOutcome;
            if (originalMissChance <= 0.0D || addedOutcome <= 0.0D) {
                return;
            }

            double conditionalChance = Math.min(1.0D, addedOutcome / originalMissChance);
            if (player.getRandom().nextDouble() >= conditionalChance) {
                return;
            }

            ResourceLocation selectedShard = player.getRandom().nextDouble() * addedOutcome < addedTeraOutcome
                    ? primaryShardId
                    : STELLAR_SHARD;
            ItemDropEntry extraShard = new ItemDropEntry();
            extraShard.setItem(selectedShard);
            event.getDrops().add(extraShard);
            RelicHudNotifier.proc(player, "mega_shard_find_bonus", ValueKind.NONE, 0.0D);
        } catch (LinkageError | ReflectiveOperationException exception) {
            if (!runtimeFailureLogged) {
                runtimeFailureLogged = true;
                YoikoServerCore.LOGGER.error("Could not apply the Mega Showdown relic bonus.", exception);
            }
        }
    }

    private static boolean hasExistingShard(
            LootDroppedEvent event,
            PokemonEntity pokemonEntity,
            Item primaryShard,
            ResourceLocation primaryShardId
    ) {
        for (DropEntry drop : event.getDrops()) {
            if (drop instanceof ItemDropEntry itemDrop
                    && (primaryShardId.equals(itemDrop.getItem()) || STELLAR_SHARD.equals(itemDrop.getItem()))) {
                return true;
            }
        }

        return !pokemonEntity.level().getEntitiesOfClass(
                ItemEntity.class,
                pokemonEntity.getBoundingBox().inflate(0.5D),
                itemEntity -> itemEntity.tickCount <= 1 && itemEntity.getItem().is(primaryShard)
        ).isEmpty();
    }

    private static double chance(double percent) {
        return Math.max(0.0D, Math.min(1.0D, percent / 100.0D));
    }
}
