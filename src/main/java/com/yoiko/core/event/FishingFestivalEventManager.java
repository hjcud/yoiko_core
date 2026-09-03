package com.yoiko.core.event;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.fishing.BobberSpawnPokemonEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.economy.RestedGoldManager;
import com.yoiko.core.registry.YoikoItems;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Fishing-specific behavior used while the breaking-news fishing incident is active. */
public final class FishingFestivalEventManager {
    private static final String PATTERN_ASPECT_PREFIX = "magikarp-jump-";
    private static final List<String> MAGIKARP_PATTERNS = List.of(
            "apricot-stripes", "apricot-tiger", "apricot-zebra",
            "black-forehead", "black-mask", "blue-raindrops", "blue-saucy",
            "brown-stripes", "brown-tiger", "brown-zebra",
            "calico-orange-gold", "calico-orange-white", "calico-orange-white-black",
            "calico-white-orange", "gray-bubbles", "gray-diamonds", "gray-patches",
            "orange-dapples", "orange-forehead", "orange-mask", "orange-orca", "orange-two-tone",
            "pink-dapples", "pink-orca", "pink-two-tone",
            "purple-bubbles", "purple-diamonds", "purple-patches", "skelly",
            "violet-raindrops", "violet-saucy"
    );
    private static boolean registered;

    private FishingFestivalEventManager() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        CobblemonEvents.BOBBER_SPAWN_POKEMON_MODIFY.subscribe(
                Priority.LOWEST, FishingFestivalEventManager::onFishingPokemonModified);
        CobblemonEvents.BOBBER_SPAWN_POKEMON_POST.subscribe(
                Priority.LOWEST, FishingFestivalEventManager::onFishingPokemonSpawned);
    }

    private static void onFishingPokemonModified(BobberSpawnPokemonEvent.Modify event) {
        ServerPlayer player = fishingPlayer(event);
        if (player == null || !BreakingNewsEventManager.isActive(
                player.server, BreakingNewsEventManager.Type.FISHING_FESTIVAL)
                || !BreakingNewsEventManager.isFishingFestivalLocation(
                event.getSpawnAction().getSpawnablePosition().getWorld(),
                event.getSpawnAction().getSpawnablePosition().getPosition())) {
            return;
        }

        Pokemon original = event.getPokemon().getPokemon();
        boolean feebas = player.getRandom().nextDouble() < BreakingNewsEventManager.feebasChance();
        String propertiesText;
        if (feebas) {
            propertiesText = "feebas";
        } else {
            String pattern = MAGIKARP_PATTERNS.get(player.getRandom().nextInt(MAGIKARP_PATTERNS.size()));
            propertiesText = "magikarp magikarp_jump=" + pattern;
        }

        try {
            PokemonProperties properties = PokemonProperties.Companion.parse(propertiesText);
            // Preserve the completed fishing roll. An explicit shiny value avoids a second base roll,
            // while nature/IV/EV and potential marks retain general-purpose bait effects.
            properties.setLevel(original.getLevel());
            properties.setShiny(original.getShiny());
            properties.setNature(original.getNature().getName().toString());
            properties.setIvs(original.getIvs());
            properties.setEvs(original.getEvs());
            Pokemon replacement = properties.create(player);
            replacement.setPotentialMarks(new HashSet<>(original.getPotentialMarks()));
            HashSet<String> forcedAspects = new HashSet<>(replacement.getForcedAspects());
            forcedAspects.add("fished");
            replacement.setForcedAspects(forcedAspects);
            if (!feebas && !replacement.getShiny()
                    && player.getRandom().nextDouble()
                    < BreakingNewsEventManager.patternedMagikarpBonusShinyChance()) {
                replacement.setShiny(true);
            }
            replacement.updateAspects();
            event.getPokemon().setPokemon(replacement);
        } catch (RuntimeException exception) {
            YoikoServerCore.LOGGER.error(
                    "Failed to replace a breaking-news fishing result with '{}'.", propertiesText, exception);
        }
    }

    private static void onFishingPokemonSpawned(BobberSpawnPokemonEvent.Post event) {
        ServerPlayer player = fishingPlayer(event);
        if (player == null) {
            return;
        }
        RestedGoldManager.awardActivity(player, RestedGoldManager.Activity.FISHING);
        if (!BreakingNewsEventManager.isActive(
                player.server, BreakingNewsEventManager.Type.FISHING_FESTIVAL)
                || !BreakingNewsEventManager.isFishingFestivalLocation(
                event.getSpawnAction().getSpawnablePosition().getWorld(),
                event.getSpawnAction().getSpawnablePosition().getPosition())) {
            return;
        }
        Pokemon pokemon = event.getPokemon().getPokemon();
        String species = pokemon.getSpecies().getResourceIdentifier().getPath().toLowerCase(Locale.ROOT);
        boolean feebas = "feebas".equals(species);
        boolean patternedMagikarp = "magikarp".equals(species)
                && pokemon.getAspects().stream().anyMatch(aspect -> aspect.startsWith(PATTERN_ASPECT_PREFIX));
        if (!feebas && !patternedMagikarp) {
            return;
        }
        BreakingNewsEventManager.recordFishingResult(
                player, feebas, patternedMagikarp && pokemon.getShiny());
        if (!patternedMagikarp || !BreakingNewsEventManager.claimFishingReward(player)) {
            return;
        }

        ItemStack ticket = YoikoItems.SHINY_ALL_POKEMON_GACHA_TICKET.toStack();
        if (!player.getInventory().add(ticket)) {
            player.drop(ticket, false);
            player.sendSystemMessage(Component.translatable(
                    "yoiko_core.breaking.fishing.reward_inventory_full").withStyle(ChatFormatting.YELLOW));
        }
        player.sendSystemMessage(Component.translatable(
                "yoiko_core.breaking.fishing.reward").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.9F, 1.3F);
    }

    private static ServerPlayer fishingPlayer(BobberSpawnPokemonEvent.Modify event) {
        Entity source = event.getSpawnAction().getSpawnablePosition().getCause().getEntity();
        return source instanceof ServerPlayer player ? player : null;
    }

    private static ServerPlayer fishingPlayer(BobberSpawnPokemonEvent.Post event) {
        Entity source = event.getSpawnAction().getSpawnablePosition().getCause().getEntity();
        return source instanceof ServerPlayer player ? player : null;
    }
}
