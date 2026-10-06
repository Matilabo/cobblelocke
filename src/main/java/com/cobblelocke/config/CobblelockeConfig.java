package com.cobblelocke.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CobblelockeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // The rules version a config was made with. A config written before versioning has no
    // configVersion and counts as 1. When an update changes how a rule plays, the new behaviour is
    // gated on the version (see the helpers below), so a world keeps the rules it was set up with
    // until a new run is started. Bump CURRENT_VERSION and add a Migration whenever that happens.
    public static final int CURRENT_VERSION = 2;

    public int configVersion = CURRENT_VERSION;

    public boolean configured = false;

    public String serverConfigMode = "read-only";

    public boolean spawnCapAlwaysOn = false;

    public boolean runActive = false;

    public String preset = "Custom";

    public boolean randomStarters = false;

    public String starterRegion = "all";
    public boolean startersNoLegendaries = true;
    public boolean startersBasicOnly = false;
    public boolean startersTypeTrio = false;
    public boolean startersTriEvolution = false;
    public boolean randomStarterTypes = false;
    public boolean globalStarterTypes = false;
    public boolean randomStarterAbilities = false;
    public boolean globalStarterAbilities = false;
    public boolean randomStarterMoves = false;
    public boolean globalStarterMoves = false;

    public int offeredStarterTrios = 1;

    public boolean starterTeras = false;
    public boolean starterMegas = false;
    public boolean starterDyna = false;

    public boolean randomSpawns = false;
    public boolean smoothSpawning = true;
    public boolean spawnsLegendaries = false;

    public boolean legendariesReplaceLegendaries = false;

    public int legendaryRarity = 500;
    public boolean randomWildCaptures = false;
    public boolean randomWildTypes = false;
    public boolean globalWildTypes = false;
    public boolean randomWildAbilities = false;
    public boolean globalWildAbilities = false;
    public boolean randomWildMoves = false;
    public boolean globalWildMoves = false;
    public boolean randomWildHeldItems = false;

    public boolean randomEvolutions = false;
    public boolean evolutionsSameStage = true;
    public boolean evolutionsSimilarBST = false;

    public boolean randomTmMoves = false;
    public boolean typesKeepOneType = false;

    public int typesRandomCount = 0;
    public boolean overrideShinyRate = false;

    public static final int RANDOM_RARITY = -1;

    public static final int MAX_STARTER_TRIOS = 20;

    public int shinyRate = 8192;

    public List<String> heldItemWhitelist = new ArrayList<>();

    public List<String> heldItemBlacklist = new ArrayList<>();

    public boolean randomizeTeras = false;

    public boolean randomizeMegas = false;

    public boolean randomTrainerSpecies = false;

    public int trainerMinBst = 0;
    public int trainerMaxBst = 0;

    public int trainerLevelMode = 0;

    public int trainerTeamSize = 0;
    public int doubleBattleTeamSize = 4;
    public boolean randomTrainerTypes = false;
    public boolean globalTrainerTypes = false;
    public boolean randomTrainerAbilities = false;
    public boolean globalTrainerAbilities = false;
    public boolean randomTrainerMoves = false;
    public boolean globalTrainerMoves = false;
    public boolean randomTrainerHeldItems = false;

    public int doubleBattle = 0;
    public boolean trainerTeras = false;
    public boolean trainerMegas = false;
    public boolean trainerDyna = false;

    public int gymBstMin1 = 0;
    public int gymBstMax1 = 0;
    public int gymLevel1 = 0;
    public int gymCount1 = 0;
    public int gymBstMin2 = 0;
    public int gymBstMax2 = 0;
    public int gymLevel2 = 0;
    public int gymCount2 = 0;
    public int gymBstMin3 = 0;
    public int gymBstMax3 = 0;
    public int gymLevel3 = 0;
    public int gymCount3 = 0;
    public int gymBstMin4 = 0;
    public int gymBstMax4 = 0;
    public int gymLevel4 = 0;
    public int gymCount4 = 0;
    public int gymBstMin5 = 0;
    public int gymBstMax5 = 0;
    public int gymLevel5 = 0;
    public int gymCount5 = 0;
    public int gymBstMin6 = 0;
    public int gymBstMax6 = 0;
    public int gymLevel6 = 0;
    public int gymCount6 = 0;
    public int gymBstMin7 = 0;
    public int gymBstMax7 = 0;
    public int gymLevel7 = 0;
    public int gymCount7 = 0;
    public int gymBstMin8 = 0;
    public int gymBstMax8 = 0;
    public int gymLevel8 = 0;
    public int gymCount8 = 0;

    public int eliteBstMin1 = 0;
    public int eliteBstMax1 = 0;
    public int eliteLevel1 = 0;
    public int eliteCount1 = 0;
    public int eliteBstMin2 = 0;
    public int eliteBstMax2 = 0;
    public int eliteLevel2 = 0;
    public int eliteCount2 = 0;
    public int eliteBstMin3 = 0;
    public int eliteBstMax3 = 0;
    public int eliteLevel3 = 0;
    public int eliteCount3 = 0;
    public int eliteBstMin4 = 0;
    public int eliteBstMax4 = 0;
    public int eliteLevel4 = 0;
    public int eliteCount4 = 0;

    public int champBstMin = 0;
    public int champBstMax = 0;
    public int champLevel = 0;
    public int champCount = 0;

    public boolean nuzlockeModeEnabled = false;
    public boolean oneCatchPerBiome = false;

    public int oneCatchPerRegion = 0;

    public int capSpawningPerRegionChunks = 0;

    public int capSpawningPerRegionCount = 1;

    public boolean capSpawningPerRegionMemory = false;

    public boolean capSpawningPerRegionByPlayer = false;
    public boolean noDuplicates = false;
    public boolean allowRepeatAfterFaint = false;
    public boolean noHealing = false;
    public boolean releaseFainted = false;

    public boolean dropHeldItemsOnFaint = false;

    public boolean enableTerrainDeaths = false;

    public boolean ignorePvpFaints = true;
    public boolean shinyClause = true;
    public boolean onlyCatchInBattle = false;
    public boolean requireNicknames = false;
    public int catchCooldownSeconds = 0;

    public boolean firstCatchEventLocked = false;

    public boolean isExtraCatch = false;

    public int eventLockRegion = 0;

    public String animationPreset = "retro";

    public String animOverworld = "retro_grass";
    public String animWater = "retro_water";
    public String animCave = "retro_cave";
    public String animNetherCrimson = "default";
    public String animNetherWarped = "default";
    public String animEnd = "default";
    public String animDefault = "default";

    public int eventLevelMode = 0;

    public boolean disableRaidCatch = false;

    // Version 2: with One Catch Per Biome and One Catch Per Region both on, the biome's catch is an
    // extra catch on top of the region's. Version 1 used up both with every catch.
    public boolean biomeCatchIsExtra() {
        return configVersion >= 2;
    }

    // Version 2: an event encounter that escapes also uses up the region the player stands in.
    public boolean escapesUseRegion() {
        return configVersion >= 2;
    }

    public boolean wild(boolean rule) {
        return randomSpawns && rule;
    }

    public boolean trainer(boolean rule) {
        return randomTrainerSpecies && rule;
    }

    public boolean starter(boolean rule) {
        return randomStarters && rule;
    }

    public int starterTrios() {
        return Math.max(1, Math.min(MAX_STARTER_TRIOS, offeredStarterTrios));
    }

    public int gymSetting(String prefix, int gym) {
        try {
            return getClass().getField(prefix + Math.max(1, Math.min(8, gym))).getInt(this);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    public int leagueSetting(String field, com.cobblelocke.compat.GymLeaders.Slot slot) {
        if (slot == null) {
            return 0;
        }
        String name = switch (slot.role()) {
            case GYM -> "gym" + field + Math.max(1, Math.min(8, slot.number()));
            case ELITE -> "elite" + field + Math.max(1, Math.min(4, slot.number()));
            case CHAMPION -> "champ" + field;
        };
        try {
            return getClass().getField(name).getInt(this);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    public boolean nuzlocke(boolean rule) {
        return nuzlockeModeEnabled && rule;
    }

    public boolean anyTrainerRandomization() {
        return runActive && randomTrainerSpecies;
    }

    public boolean anyGlobalTypes() {
        return wild(randomWildTypes && globalWildTypes) || starter(randomStarterTypes && globalStarterTypes)
                || trainer(randomTrainerTypes && globalTrainerTypes);
    }

    public boolean anyGlobalAbilities() {
        return wild(randomWildAbilities && globalWildAbilities)
                || starter(randomStarterAbilities && globalStarterAbilities)
                || trainer(randomTrainerAbilities && globalTrainerAbilities);
    }

    public boolean anyGlobalMoves() {
        return wild(randomWildMoves && globalWildMoves) || starter(randomStarterMoves && globalStarterMoves)
                || trainer(randomTrainerMoves && globalTrainerMoves);
    }

    public boolean anyGlobalPool() {
        return wild(randomWildTypes && globalWildTypes) || wild(randomWildAbilities && globalWildAbilities)
                || wild(randomWildMoves && globalWildMoves)
                || starter(randomStarterTypes && globalStarterTypes)
                || starter(randomStarterAbilities && globalStarterAbilities)
                || starter(randomStarterMoves && globalStarterMoves)
                || trainer(randomTrainerTypes && globalTrainerTypes)
                || trainer(randomTrainerAbilities && globalTrainerAbilities)
                || trainer(randomTrainerMoves && globalTrainerMoves);
    }

    public static final Map<String, List<String>> ANIMATION_PRESETS = Map.of(
            "action", List.of("action_grass", "action_water", "action_cave",
                    "action_nether", "action_nether", "action_end", "action_default"),
            "minecraft", List.of("mc_grass_random", "mc_water", "mc_cave",
                    "mc_nether_crimson", "mc_nether_warped", "mc_end", "random"),
            "retro", List.of("retro_grass", "retro_water", "retro_cave",
                    "default", "retro_default", "retro_trainer", "random"),
            "custom", List.of());

    public void applyAnimationPreset(String preset) {
        List<String> clips = ANIMATION_PRESETS.get(preset);
        if (clips == null || clips.isEmpty()) {
            animationPreset = "custom";
            return;
        }
        animationPreset = preset;
        animOverworld = clips.get(0);
        animWater = clips.get(1);
        animCave = clips.get(2);
        animNetherCrimson = clips.get(3);
        animNetherWarped = clips.get(4);
        animEnd = clips.get(5);
        animDefault = clips.get(6);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public JsonObject toJsonObject() {
        return GSON.toJsonTree(this).getAsJsonObject();
    }

    public static CobblelockeConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new CobblelockeConfig();
        }
        try {
            return fromJsonObject(parseLenient(json));
        } catch (RuntimeException e) {
            return new CobblelockeConfig();
        }
    }

    public static CobblelockeConfig fromJsonObject(JsonObject object) {
        CobblelockeConfig parsed = GSON.fromJson(coerce(object), CobblelockeConfig.class);
        return parsed != null ? parsed : new CobblelockeConfig();
    }

    private static JsonObject coerce(JsonObject object) {
        JsonObject out = object.deepCopy();
        migrate(out);
        expandRanges(out);
        migrateRegionSize(out);
        migrateRetiredClips(out);
        for (java.lang.reflect.Field field : CobblelockeConfig.class.getFields()) {
            if (field.getType() != int.class) {
                continue;
            }
            JsonElement value = out.get(field.getName());
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            com.google.gson.JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                continue;
            }
            Integer replacement = asInt(primitive);
            if (replacement != null) {
                out.addProperty(field.getName(), replacement);
            } else {
                out.remove(field.getName());
            }
        }
        return out;
    }

    // One step per version that needs it, run in order on any config older than that version. A step
    // pins whatever the older version did (new options it never had are written with the value that
    // keeps the old behaviour) and renames or reshapes keys. Steps never raise configVersion: a world
    // stays on its version so the behaviour checks above keep applying. They run on every load, so
    // each one must be safe to repeat.
    private record Migration(int version, java.util.function.Consumer<JsonObject> step) {
    }

    private static final List<Migration> MIGRATIONS = List.of(
            // 2: Event Lock Extra Catch and Event Lock Region were added; version 1 had neither.
            new Migration(2, out -> {
                putIfMissing(out, "isExtraCatch", false);
                putIfMissing(out, "eventLockRegion", 0);
            }));

    public static int versionOf(JsonObject object) {
        JsonElement value = object.get("configVersion");
        if (value == null || !value.isJsonPrimitive()) {
            return 1;
        }
        try {
            return Math.max(1, value.getAsInt());
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private static void migrate(JsonObject out) {
        int version = versionOf(out);
        out.addProperty("configVersion", version);
        for (Migration migration : MIGRATIONS) {
            if (version < migration.version()) {
                migration.step().accept(out);
            }
        }
    }

    private static void putIfMissing(JsonObject out, String key, Object value) {
        if (out.has(key)) {
            return;
        }
        if (value instanceof Boolean flag) {
            out.addProperty(key, flag);
        } else if (value instanceof Number number) {
            out.addProperty(key, number);
        } else {
            out.addProperty(key, String.valueOf(value));
        }
    }

    private static final Map<String, String> RETIRED_CLIPS = Map.of(
            "classic_grass", "retro_grass",
            "classic_water", "retro_water",
            "classic_cave", "retro_cave");

    private static void migrateRetiredClips(JsonObject out) {
        for (String key : ConfigOptions.ANIMATION_KEYS) {
            JsonElement value = out.get(key);
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            String replacement = RETIRED_CLIPS.get(value.getAsString());
            if (replacement != null) {
                out.addProperty(key, replacement);
            }
        }
        JsonElement preset = out.get("animationPreset");
        if (preset != null && preset.isJsonPrimitive() && "classic".equals(preset.getAsString())) {
            out.addProperty("animationPreset", "retro");
        }
    }

    private static void migrateRegionSize(JsonObject out) {
        if (out.has("capSpawningPerRegionChunks") || !out.has("capSpawningPerRegionSize")) {
            return;
        }
        JsonElement value = out.remove("capSpawningPerRegionSize");
        if (value == null || !value.isJsonPrimitive()) {
            return;
        }
        Integer blocks = asInt(value.getAsJsonPrimitive());
        if (blocks == null || blocks <= 0) {
            return;
        }
        out.addProperty("capSpawningPerRegionChunks", Math.max(1, blocks / 16));
    }

    private static void expandRanges(JsonObject out) {
        for (ConfigOptions.Spec spec : ConfigOptions.all()) {
            if (spec.kind() != ConfigOptions.Kind.RANGE || spec.ids().size() < 2) {
                continue;
            }
            String name = ConfigOptions.rangeName(spec);
            JsonElement value = out.get(name);
            if (value == null || !value.isJsonArray()) {
                continue;
            }
            com.google.gson.JsonArray pair = value.getAsJsonArray();
            if (pair.size() >= 1) {
                out.add(spec.ids().get(0), pair.get(0));
            }
            if (pair.size() >= 2) {
                out.add(spec.ids().get(1), pair.get(1));
            }
            if (!name.equals(spec.ids().get(0)) && !name.equals(spec.ids().get(1))) {
                out.remove(name);
            }
        }
    }

    private static Integer asInt(com.google.gson.JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? 1 : 0;
        }
        String text = primitive.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
        if (text.equals("default") || text.equals("off") || text.equals("none") || text.equals("any")) {
            return 0;
        }
        if (text.equals("random")) {
            return RANDOM_RARITY;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean legendariesAreTrulyRandom() {
        return legendaryRarity == RANDOM_RARITY;
    }

    public static JsonObject parseLenient(String text) {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true);
        JsonElement element = JsonParser.parseReader(reader);
        if (!element.isJsonObject()) {
            throw new JsonParseException("Expected a JSON object");
        }
        return element.getAsJsonObject();
    }

    public static CobblelockeConfig fromPreset(String name, JsonObject rules) {
        JsonObject merged = new CobblelockeConfig().toJsonObject();
        // A preset without configVersion was written before versioning, so it is version 1.
        merged.remove("configVersion");
        if (rules != null) {
            for (Map.Entry<String, JsonElement> entry : rules.entrySet()) {
                merged.add(entry.getKey(), entry.getValue());
            }
        }
        CobblelockeConfig config = fromJsonObject(merged);
        config.preset = name;
        return config;
    }

    public void keepServerSettings(CobblelockeConfig from) {
        if (from == null) {
            return;
        }
        serverConfigMode = from.serverConfigMode;
        spawnCapAlwaysOn = from.spawnCapAlwaysOn;
        configured = from.configured;
        runActive = from.runActive;
    }

    public String spawnCapSignature() {
        return nuzlockeModeEnabled + "|" + capSpawningPerRegionChunks + "|" + capSpawningPerRegionCount + "|"
                + capSpawningPerRegionMemory + "|" + capSpawningPerRegionByPlayer;
    }

    public CobblelockeConfig copy() {
        return fromJson(toJson());
    }

    public void copyFrom(CobblelockeConfig other) {
        if (other == null) {
            return;
        }
        CobblelockeConfig source = other.copy();
        for (java.lang.reflect.Field field : CobblelockeConfig.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.set(this, field.get(source));
            } catch (IllegalAccessException ignored) {
            }
        }
    }
}
