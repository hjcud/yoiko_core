package com.yoiko.core.gacha;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.yoiko.core.YoikoServerCore;
import net.minecraft.server.level.ServerPlayer;

public final class CobblemonPokemonFactory {
    private CobblemonPokemonFactory() {
    }

    public static String buildProperties(GachaResult result) {
        StringBuilder builder = new StringBuilder(result.species());
        builder.append(" level=").append(result.level());
        if (result.shiny()) {
            builder.append(" shiny=true");
        }
        return builder.toString();
    }

    public static String buildGiveCommand(ServerPlayer player, GachaResult result) {
        return "givepokemonother " + player.getGameProfile().getName() + " " + buildProperties(result);
    }

    public static boolean giveDirect(ServerPlayer player, GachaResult result) {
        try {
            PokemonProperties properties = new PokemonProperties();
            properties.setSpecies(result.species());
            properties.setLevel(result.level());
            properties.setShiny(result.shiny());
            Pokemon pokemon = properties.create(player);
            Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
            return true;
        } catch (Throwable throwable) {
            YoikoServerCore.LOGGER.warn("Direct Cobblemon Pokemon grant failed, falling back to command: {}", result, throwable);
            return false;
        }
    }
}
