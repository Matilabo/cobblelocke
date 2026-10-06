package com.cobblelocke.util;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.biome.Biome;

import java.util.List;

public final class Lang {
    public static final String PREFIX = "cobblelocke.";

    private Lang() {
    }

    public static MutableText tr(String key, String english, Object... args) {
        MutableText text = Text.translatableWithFallback(PREFIX + key, english, args);
        Formatting base = leadingColour(english);
        return base == null ? text : text.formatted(base);
    }

    public static MutableText option(String key, String english) {
        return Text.translatableWithFallback(PREFIX + "option." + key, english);
    }

    private static Formatting leadingColour(String english) {
        if (english == null || english.length() < 2 || english.charAt(0) != '§') {
            return null;
        }
        Formatting formatting = Formatting.byCode(english.charAt(1));
        return formatting != null && formatting.isColor() ? formatting : null;
    }

    public static MutableText hl(Object value, Formatting... formatting) {
        MutableText text = value instanceof Text existing ? existing.copy() : Text.literal(String.valueOf(value));
        return text.formatted(formatting);
    }

    public static MutableText biome(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return Text.literal("?");
        }
        return Text.translatableWithFallback("biome." + biomeId.replace(':', '.'), Worlds.prettyBiomeName(biomeId));
    }

    public static MutableText biome(RegistryKey<Biome> biome) {
        return biome == null ? Text.literal("?") : biome(Worlds.biomeId(biome));
    }

    public static MutableText species(Species species) {
        if (species == null) {
            return Text.literal("?");
        }
        try {
            return species.getTranslatedName().copy();
        } catch (Exception e) {
            return Text.literal(species.getName());
        }
    }

    public static MutableText pokemon(Pokemon pokemon) {
        if (pokemon == null) {
            return Text.literal("?");
        }
        try {
            MutableText nickname = pokemon.getNickname();
            if (nickname != null && !nickname.getString().isBlank()) {
                return nickname.copy();
            }
        } catch (Exception ignored) {
        }
        return species(pokemon.getSpecies());
    }

    public static MutableText join(List<? extends Text> parts, Text separator) {
        MutableText out = Text.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(separator.copy());
            }
            out.append(parts.get(i).copy());
        }
        return out;
    }

    public static MutableText join(List<? extends Text> parts) {
        return join(parts, Text.literal(", "));
    }
}
