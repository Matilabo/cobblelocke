package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class Gimmicks {
    private static final String MEGA_DATA_PATH = "mega_showdown/mega";

    private static final String KEY_MEGA_APPLY = "cobblelocke_mega_apply";
    private static final String KEY_MEGA_REVERT = "cobblelocke_mega_revert";

    private static final String ASPECT_UTILS = "com.github.yajatkaul.mega_showdown.utils.AspectUtils";

    private static final String[] GMAX_LABELS = {"gmax", "gigantamax"};

    public record MegaForm(Item stone, List<String> apply, List<String> revert) {
    }

    private static volatile Map<String, List<MegaForm>> megaForms = null;

    private Gimmicks() {
    }

    public static void invalidate() {
        megaForms = null;
    }

    public static boolean randomTera(Pokemon pokemon, Random random) {
        List<TeraType> types = teraTypes();
        if (types.isEmpty()) {
            return false;
        }
        try {
            pokemon.setTeraType(types.get(random.nextInt(types.size())));
            return true;
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not set a tera type: {}", e.toString());
            return false;
        }
    }

    private static List<TeraType> teraTypes() {
        List<TeraType> types = new ArrayList<>();
        try {
            for (TeraType type : TeraTypes.INSTANCE) {
                if (type != null) {
                    types.add(type);
                }
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not read the tera registry: {}", e.toString());
        }
        return types;
    }

    public static boolean canMega(Pokemon pokemon) {
        return !formsFor(pokemon).isEmpty();
    }

    public static boolean giveMegaStone(Pokemon pokemon, boolean canDrop) {
        List<MegaForm> options = formsFor(pokemon);
        if (options.isEmpty()) {
            return false;
        }
        MegaForm chosen = options.get(new Random().nextInt(options.size()));
        try {
            pokemon.swapHeldItem(new ItemStack(chosen.stone(), 1), false, canDrop);
            remember(pokemon, chosen);
            return true;
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not hand out a mega stone: {}", e.toString());
            return false;
        }
    }

    public static boolean megaEvolve(Pokemon pokemon) {
        List<String> aspects = stored(pokemon, KEY_MEGA_APPLY);
        if (aspects.isEmpty()) {
            return false;
        }
        return applyAspects(pokemon, aspects);
    }

    public static void revertMega(Pokemon pokemon) {
        List<String> aspects = stored(pokemon, KEY_MEGA_REVERT);
        if (!aspects.isEmpty()) {
            applyAspects(pokemon, aspects);
        }
        try {
            NbtCompound data = pokemon.getPersistentData();
            if (data != null) {
                data.remove(KEY_MEGA_APPLY);
                data.remove(KEY_MEGA_REVERT);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean canGmax(Pokemon pokemon) {
        try {
            for (FormData form : pokemon.getSpecies().getForms()) {
                if (form == null || form.getLabels() == null) {
                    continue;
                }
                for (String label : form.getLabels()) {
                    String key = label.toLowerCase(Locale.ROOT);
                    for (String gmax : GMAX_LABELS) {
                        if (key.equals(gmax)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean giveGmaxFactor(Pokemon pokemon) {
        if (!canGmax(pokemon)) {
            return false;
        }
        try {
            pokemon.setGmaxFactor(true);
            return true;
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not set the gmax factor: {}", e.toString());
            return false;
        }
    }

    private static boolean applyAspects(Pokemon pokemon, List<String> aspects) {
        try {
            Class<?> utils = Class.forName(ASPECT_UTILS);
            Method applyAspects = utils.getMethod("applyAspects", Pokemon.class, List.class);
            applyAspects.invoke(null, pokemon, aspects);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Cobblelocke.LOGGER.debug("Falling back to plain aspects: {}", e.toString());
        }
        boolean changed = false;
        for (String aspect : aspects) {
            try {
                PokemonProperties.Companion.parse(aspect).apply(pokemon);
                changed = true;
            } catch (Exception e) {
                Cobblelocke.LOGGER.debug("Could not apply the aspect {}: {}", aspect, e.toString());
            }
        }
        return changed;
    }

    private static void remember(Pokemon pokemon, MegaForm form) {
        try {
            NbtCompound data = pokemon.getPersistentData();
            if (data == null) {
                return;
            }
            data.put(KEY_MEGA_APPLY, asNbt(form.apply()));
            data.put(KEY_MEGA_REVERT, asNbt(form.revert()));
        } catch (Exception ignored) {
        }
    }

    private static NbtList asNbt(List<String> aspects) {
        NbtList list = new NbtList();
        for (String aspect : aspects) {
            list.add(NbtString.of(aspect));
        }
        return list;
    }

    private static List<String> stored(Pokemon pokemon, String key) {
        List<String> aspects = new ArrayList<>();
        try {
            NbtCompound data = pokemon.getPersistentData();
            if (data == null || !data.contains(key)) {
                return aspects;
            }
            NbtList list = data.getList(key, NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                aspects.add(list.getString(i));
            }
        } catch (Exception ignored) {
        }
        return aspects;
    }

    private static List<MegaForm> formsFor(Pokemon pokemon) {
        try {
            String species = pokemon.getSpecies().getName().toLowerCase(Locale.ROOT);
            List<MegaForm> options = stones().get(species);
            return options == null ? List.of() : options;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map<String, List<MegaForm>> stones() {
        Map<String, List<MegaForm>> cached = megaForms;
        if (cached != null) {
            return cached;
        }
        Map<String, List<MegaForm>> built = new HashMap<>();
        MinecraftServer server = Cobblelocke.getServer();
        if (server == null) {
            return built;
        }
        try {
            Map<Identifier, Resource> files = server.getResourceManager()
                    .findResources(MEGA_DATA_PATH, id -> id.getPath().endsWith(".json"));
            for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
                readMega(entry.getKey(), entry.getValue(), built);
            }
            if (!built.isEmpty()) {
                Cobblelocke.LOGGER.info("Found mega stones for {} species", built.size());
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not read the mega stone data: {}", e.toString());
        }
        megaForms = built;
        return built;
    }

    private static void readMega(Identifier file, Resource resource, Map<String, List<MegaForm>> into) {
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("pokemons")) {
                return;
            }

            String name = file.getPath();
            name = name.substring(name.lastIndexOf('/') + 1, name.length() - ".json".length());
            Item stone = Registries.ITEM.get(Identifier.of(file.getNamespace(), name));
            if (stone == null || stone == net.minecraft.item.Items.AIR) {
                return;
            }
            MegaForm form = new MegaForm(stone, aspects(root, "apply"), aspects(root, "revert"));
            JsonArray species = root.getAsJsonArray("pokemons");
            for (JsonElement element : species) {
                into.computeIfAbsent(element.getAsString().toLowerCase(Locale.ROOT),
                        key -> new ArrayList<>()).add(form);
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Skipping unreadable mega file {}: {}", file, e.toString());
        }
    }

    private static List<String> aspects(JsonObject root, String side) {
        List<String> aspects = new ArrayList<>();
        try {
            JsonObject conditions = root.getAsJsonObject("aspect_conditions");
            if (conditions == null || !conditions.has(side)) {
                return aspects;
            }
            JsonObject branch = conditions.getAsJsonObject(side);
            if (branch == null || !branch.has("aspects")) {
                return aspects;
            }
            for (JsonElement element : branch.getAsJsonArray("aspects")) {
                aspects.add(element.getAsString());
            }
        } catch (Exception ignored) {
        }
        return aspects;
    }
}
