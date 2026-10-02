package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.util.SpeciesPool;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalPools {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final long SALT_TYPES = 0x54595045_4CL;
    private static final long SALT_ABILITIES = 0x4142494C_49L;
    private static final long SALT_MOVES = 0x4D4F5645_53L;

    private static volatile GlobalPools instance;

    private final Path file;
    private final long seed;

    private final Map<String, String[]> types = new ConcurrentHashMap<>();
    private final Map<String, String> abilities = new ConcurrentHashMap<>();
    private final Map<String, CustomLearnset> moves = new ConcurrentHashMap<>();

    private volatile boolean dirty = false;

    private GlobalPools(Path file, long seed) {
        this.file = file;
        this.seed = seed;
    }

    public static GlobalPools get() {
        return instance;
    }

    public static long worldSeed() {
        GlobalPools pools = get();
        return pools == null ? 0L : pools.seed;
    }

    public static void init(Path worldDirectory, long worldSeed) {
        Path target = worldDirectory.resolve("cobblelocke").resolve("global_pools.json");
        GlobalPools pools = new GlobalPools(target, worldSeed);
        pools.load();
        instance = pools;
    }

    public static void shutdown() {
        GlobalPools pools = instance;
        if (pools != null) {
            pools.save();
        }
        instance = null;
    }

    public String[] typesFor(String speciesName, CobblelockeConfig config, String[] originalTypes) {
        String key = key(speciesName);
        if (key == null) {
            return null;
        }
        String[] existing = types.get(key);
        if (existing != null) {
            return existing;
        }
        String[] rolled = TypeRoller.roll(rngFor(key, SALT_TYPES), config, originalTypes);
        types.put(key, rolled);
        dirty = true;
        return rolled;
    }

    public String abilityFor(String speciesName) {
        String key = key(speciesName);
        if (key == null) {
            return null;
        }
        String existing = abilities.get(key);
        if (existing != null) {
            return existing;
        }
        List<String> pool = SpeciesPool.abilityNames();
        if (pool.isEmpty()) {
            return null;
        }
        String rolled = pool.get(rngFor(key, SALT_ABILITIES).nextInt(pool.size()));
        abilities.put(key, rolled);
        dirty = true;
        return rolled;
    }

    public CustomLearnset movesFor(String speciesName) {
        String key = key(speciesName);
        if (key == null) {
            return null;
        }
        CustomLearnset existing = moves.get(key);
        if (existing != null) {
            return existing;
        }
        CustomLearnset rolled = LearnsetRoller.roll(key, rngFor(key, SALT_MOVES));
        moves.put(key, rolled);
        dirty = true;
        return rolled;
    }

    public void prefillAll(CobblelockeConfig config) {
        for (com.cobblemon.mod.common.pokemon.Species species : SpeciesPool.all()) {
            String name = species.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (config.anyGlobalTypes()) {
                typesFor(name, config, SpeciesPool.originalTypes(species.getStandardForm()));
            }
            if (config.anyGlobalAbilities()) {
                abilityFor(name);
            }
            if (config.anyGlobalMoves()) {
                movesFor(name);
            }
        }
        save();
    }

    public void reset() {
        types.clear();
        abilities.clear();
        moves.clear();
        dirty = true;
        save();
    }

    private Random rngFor(String key, long salt) {
        return new Random(seed * 31L + key.hashCode() * 2654435761L + salt);
    }

    private static String key(String speciesName) {
        if (speciesName == null || speciesName.isBlank()) {
            return null;
        }
        return speciesName.toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();

            if (root.has("types")) {
                JsonObject section = root.getAsJsonObject("types");
                for (String key : section.keySet()) {
                    List<String> parsed = new ArrayList<>();
                    section.getAsJsonArray(key).forEach(element -> parsed.add(element.getAsString()));
                    if (!parsed.isEmpty()) {
                        types.put(key, parsed.toArray(new String[0]));
                    }
                }
            }
            if (root.has("abilities")) {
                JsonObject section = root.getAsJsonObject("abilities");
                for (String key : section.keySet()) {
                    abilities.put(key, section.get(key).getAsString());
                }
            }
            if (root.has("moves")) {
                JsonObject section = root.getAsJsonObject("moves");
                for (String key : section.keySet()) {
                    moves.put(key, learnsetFromJson(section.getAsJsonObject(key)));
                }
            }
            Cobblelocke.LOGGER.info("Loaded global pools: {} types, {} abilities, {} movepools",
                    types.size(), abilities.size(), moves.size());
        } catch (Exception e) {
            Cobblelocke.LOGGER.error("Could not read {}, starting a fresh table: {}", file, e.toString());
        }
    }

    public void save() {
        if (!dirty) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());

            JsonObject root = new JsonObject();

            root.addProperty("seed", seed);

            JsonObject typeSection = new JsonObject();
            for (Map.Entry<String, String[]> entry : new TreeMap<>(types).entrySet()) {
                com.google.gson.JsonArray array = new com.google.gson.JsonArray();
                for (String type : entry.getValue()) {
                    array.add(type);
                }
                typeSection.add(entry.getKey(), array);
            }
            root.add("types", typeSection);

            JsonObject abilitySection = new JsonObject();
            for (Map.Entry<String, String> entry : new TreeMap<>(abilities).entrySet()) {
                abilitySection.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("abilities", abilitySection);

            JsonObject moveSection = new JsonObject();
            for (Map.Entry<String, CustomLearnset> entry : new TreeMap<>(moves).entrySet()) {
                moveSection.add(entry.getKey(), learnsetToJson(entry.getValue()));
            }
            root.add("moves", moveSection);

            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) {
            Cobblelocke.LOGGER.error("Could not write {}: {}", file, e.toString());
        }
    }

    private static JsonObject learnsetToJson(CustomLearnset learnset) {
        JsonObject object = new JsonObject();
        JsonObject levels = new JsonObject();
        for (Map.Entry<Integer, String> entry : learnset.getLevelUpMoves().entrySet()) {
            levels.addProperty(String.valueOf(entry.getKey()), entry.getValue());
        }
        object.add("levelUp", levels);

        com.google.gson.JsonArray tms = new com.google.gson.JsonArray();
        learnset.getTmMoves().forEach(tms::add);
        object.add("tm", tms);

        com.google.gson.JsonArray eggs = new com.google.gson.JsonArray();
        learnset.getEggMoves().forEach(eggs::add);
        object.add("egg", eggs);
        return object;
    }

    private static CustomLearnset learnsetFromJson(JsonObject object) {
        CustomLearnset learnset = new CustomLearnset();
        if (object.has("levelUp")) {
            JsonObject levels = object.getAsJsonObject("levelUp");
            for (String key : levels.keySet()) {
                try {
                    learnset.addLevelUpMove(Integer.parseInt(key), levels.get(key).getAsString());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (object.has("tm")) {
            object.getAsJsonArray("tm").forEach(element -> learnset.addTmMove(element.getAsString()));
        }
        if (object.has("egg")) {
            object.getAsJsonArray("egg").forEach(element -> learnset.addEggMove(element.getAsString()));
        }
        return learnset;
    }
}
