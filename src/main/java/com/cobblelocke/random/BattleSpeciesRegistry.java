package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BattleSpeciesRegistry {
    private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

    private static final Set<String> GENDER_FORM_SPECIES = Set.of(
            "indeedee", "meowstic", "unfezant", "pyroar", "frillish", "jellicent");

    private BattleSpeciesRegistry() {
    }

    public static void clear() {
        REGISTERED.clear();
    }

    public static boolean isRegistered(String speciesId) {
        return speciesId != null && REGISTERED.contains(speciesId);
    }

    public static String idFor(String baseShowdownId, String[] types) {
        if (baseShowdownId == null || types == null || types.length == 0) {
            return null;
        }
        StringBuilder suffix = new StringBuilder();
        for (String type : types) {
            if (type == null || type.isBlank()) {
                return null;
            }
            suffix.append(type);
        }
        String combined = baseShowdownId + "rand" + suffix;
        return combined.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }

    public static String ensureRegistered(Pokemon pokemon, String[] types) {
        if (pokemon == null || types == null || types.length == 0) {
            return null;
        }
        try {
            Species species = pokemon.getSpecies();
            if (species == null || GENDER_FORM_SPECIES.contains(species.getName().toLowerCase(Locale.ROOT))) {
                return null;
            }
            String customId = idFor(pokemon.showdownId(), types);
            if (customId == null) {
                return null;
            }
            if (REGISTERED.contains(customId)) {
                return customId;
            }
            if (!register(pokemon, species, customId, types)) {
                return null;
            }
            REGISTERED.add(customId);
            return customId;
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not register a battle species: {}", e.toString());
            return null;
        }
    }

    private static boolean register(Pokemon pokemon, Species species, String customId, String[] types) {
        FormData form = pokemon.getForm();
        int dexNumber = species.getNationalPokedexNumber();
        if (form == null || dexNumber <= 0) {
            return false;
        }

        JsonObject json = new JsonObject();
        json.addProperty("num", dexNumber);
        json.addProperty("name", customId);

        json.addProperty("baseSpecies", species.getName());

        JsonArray typeArray = new JsonArray();
        for (String type : types) {
            typeArray.add(capitalize(type));
        }
        json.add("types", typeArray);

        String abilityName = pokemon.getAbility().getName().replace("_", "");
        JsonObject abilities = new JsonObject();
        abilities.addProperty("0", abilityName);
        abilities.addProperty("1", abilityName);
        abilities.addProperty("H", abilityName);
        abilities.addProperty("S", abilityName);
        json.add("abilities", abilities);

        JsonObject baseStats = new JsonObject();
        Map<Stat, Integer> stats = form.getBaseStats();
        baseStats.addProperty("hp", stats.getOrDefault(Stats.HP, 1));
        baseStats.addProperty("atk", stats.getOrDefault(Stats.ATTACK, 1));
        baseStats.addProperty("def", stats.getOrDefault(Stats.DEFENCE, 1));
        baseStats.addProperty("spa", stats.getOrDefault(Stats.SPECIAL_ATTACK, 1));
        baseStats.addProperty("spd", stats.getOrDefault(Stats.SPECIAL_DEFENCE, 1));
        baseStats.addProperty("spe", stats.getOrDefault(Stats.SPEED, 1));
        json.add("baseStats", baseStats);

        json.addProperty("heightm", form.getHeight() / 10.0);
        json.addProperty("weightkg", form.getWeight() / 10.0);

        JsonArray eggGroups = new JsonArray();
        for (EggGroup group : form.getEggGroups()) {
            eggGroups.add(group.getShowdownID());
        }
        json.add("eggGroups", eggGroups);

        float maleRatio = form.getMaleRatio();
        if (maleRatio == 0.0f) {
            json.addProperty("gender", "F");
        } else if (maleRatio == 1.0f) {
            json.addProperty("gender", "M");
        } else if (maleRatio < 0.0f || maleRatio > 1.0f) {
            json.addProperty("gender", "N");
        } else {
            JsonObject ratio = new JsonObject();
            ratio.addProperty("maleRatio", maleRatio);
            ratio.addProperty("femaleRatio", 1.0f - maleRatio);
            json.add("genderRatio", ratio);
        }

        json.add("evos", new JsonArray());
        json.addProperty("nfe", false);

        Map<String, String> payload = new HashMap<>();
        payload.put(customId, json.toString());
        ShowdownService.Companion.getService().sendRegistryData(payload, "species");
        Cobblelocke.LOGGER.debug("Registered battle species {} for {}", customId, species.getName());
        return true;
    }

    private static String capitalize(String type) {
        if (type == null || type.isEmpty()) {
            return type;
        }
        return Character.toUpperCase(type.charAt(0)) + type.substring(1).toLowerCase(Locale.ROOT);
    }
}
