package com.yoiko.core.gacha;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class PokemonNameFormatter {
    private PokemonNameFormatter() {
    }

    public static MutableComponent localizedName(String speciesId) {
        Species species = findSpecies(speciesId);
        if (species != null) {
            return species.getTranslatedName();
        }
        if (speciesId == null || speciesId.isBlank()) {
            return Component.translatable("yoiko_core.pokemon.unknown");
        }
        return Component.literal(prettyName(speciesId));
    }

    public static String canonicalId(String speciesId) {
        Species species = findSpecies(speciesId);
        if (species != null && species.getResourceIdentifier() != null) {
            return species.getResourceIdentifier().toString();
        }
        return speciesId == null ? "" : speciesId;
    }

    public static Species findSpecies(String speciesId) {
        if (speciesId == null || speciesId.isBlank()) {
            return null;
        }

        String trimmed = speciesId.trim();
        Species species = byIdentifier(trimmed);
        if (species != null) {
            return species;
        }

        String path = pathOnly(trimmed);
        species = byIdentifier("cobblemon:" + path);
        if (species != null) {
            return species;
        }

        species = byName(trimmed);
        if (species != null) {
            return species;
        }
        species = byName(path);
        if (species != null) {
            return species;
        }

        return scanSpecies(trimmed);
    }

    private static Species byIdentifier(String value) {
        try {
            ResourceLocation id = value.contains(":")
                    ? ResourceLocation.parse(value)
                    : ResourceLocation.fromNamespaceAndPath("cobblemon", value);
            return PokemonSpecies.getByIdentifier(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Species byName(String value) {
        try {
            return PokemonSpecies.getByName(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Species scanSpecies(String value) {
        String normalized = normalize(value);
        String showdown = showdownKey(value);
        try {
            for (Species species : PokemonSpecies.getSpecies()) {
                if (species == null) {
                    continue;
                }
                ResourceLocation id = species.getResourceIdentifier();
                if (id != null && (normalize(id.toString()).equals(normalized) || normalize(id.getPath()).equals(normalized))) {
                    return species;
                }
                if (normalize(species.getName()).equals(normalized)
                        || showdownKey(species.getName()).equals(showdown)
                        || showdownKey(species.showdownId()).equals(showdown)) {
                    return species;
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    private static String pathOnly(String speciesId) {
        int separator = speciesId.indexOf(':');
        return separator >= 0 ? speciesId.substring(separator + 1) : speciesId;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String showdownKey(String value) {
        return pathOnly(value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private static String prettyName(String speciesId) {
        String path = pathOnly(speciesId == null ? "" : speciesId).replace('_', ' ').replace('-', ' ').trim();
        if (path.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(path.length());
        boolean uppercase = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (Character.isWhitespace(c)) {
                uppercase = true;
                builder.append(c);
            } else if (uppercase) {
                builder.append(Character.toUpperCase(c));
                uppercase = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
