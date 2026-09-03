package com.yoiko.core.event;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause;
import com.cobblemon.mod.common.api.spawning.position.GroundedSpawnablePosition;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Cobblemon hooks shared by exact-species capture goals and spatial mass outbreaks. */
public final class WildPokemonIncidentBridge {
    private static boolean registered;

    private WildPokemonIncidentBridge() {
    }

    public static void init() {
        if (registered) return;
        registered = true;
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOW, WildPokemonIncidentBridge::onPokemonSpawn);
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.LOW, WildPokemonIncidentBridge::onPokemonCaptured);
    }

    private static void onPokemonSpawn(SpawnEvent<PokemonEntity> event) {
        if (!(event.getSpawnablePosition().getWorld() instanceof ServerLevel level)
                || event.getSpawnablePosition().getCause() instanceof FishingSpawnCause
                || !(event.getSpawnablePosition() instanceof GroundedSpawnablePosition)
                || !(event.getSpawnablePosition().getCause().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PokemonEntity entity = event.getEntity();
        if (!entity.getCountsTowardsSpawnCap()) return;
        BlockPos position = event.getSpawnablePosition().getPosition();
        BreakingNewsEventManager.OutbreakContext context =
                BreakingNewsEventManager.outbreakContext(level, position);
        if (context == null) return;

        ResourceLocation targetId = ResourceLocation.tryParse(context.targetSpecies());
        Species target = targetId == null ? null : PokemonSpecies.getByIdentifier(targetId);
        if (target == null || !target.getImplemented()) return;

        Pokemon pokemon = entity.getPokemon();
        boolean targetSpecies = targetId.equals(pokemon.getSpecies().getResourceIdentifier());
        if (!targetSpecies && level.getRandom().nextDouble() < context.replacementChance()) {
            PokemonProperties properties = new PokemonProperties();
            properties.setSpecies(targetId.toString());
            properties.setLevel(pokemon.getLevel());
            properties.setShiny(pokemon.getShiny());
            properties.setNature(pokemon.getNature().getName().toString());
            properties.setIvs(pokemon.getIvs());
            properties.setEvs(pokemon.getEvs());
            Pokemon replacement = properties.create(player);
            replacement.setPotentialMarks(new HashSet<>(pokemon.getPotentialMarks()));
            replacement.updateAspects();
            entity.setPokemon(replacement);
            pokemon = replacement;
            targetSpecies = true;
        }
        if (!targetSpecies) return;
        if (!pokemon.getShiny() && level.getRandom().nextDouble() < context.bonusShinyChance()) {
            pokemon.setShiny(true);
        }
        entity.getPersistentData().putLong("YoikoOutbreakRunId", context.runId());
        BreakingNewsEventManager.recordOutbreakSpawn(level.getServer(), context.runId(), pokemon.getShiny());
    }

    private static void onPokemonCaptured(PokemonCapturedEvent event) {
        BreakingNewsEventManager.recordPokemonCaptured(event.getPlayer(), event.getPokemon());
    }
}
