package com.cobblelocke.config;

import com.cobblelocke.Cobblelocke;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConfigFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final String COBBLELOCKE_RANDOMLOCKE = "Cobblelocke's Randomlocke";
    public static final String CLASSIC_NUZLOCKE = "Classic Nuzlocke";
    public static final String CLASSIC_RANDOMLOCKE = "Classic Randomlocke";

    private static final String CONFIG_FILE = "config.json5";
    private static final String PRESETS_FILE = "presets.json5";

    private static final String LEGACY_CONFIG_FILE = "config.json";

    private ConfigFiles() {
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve(Cobblelocke.MOD_ID);
    }

    public record Saved(CobblelockeConfig config, boolean askOnFirstJoin) {
    }

    public static Saved loadConfig() {
        Path file = folder().resolve(CONFIG_FILE);
        Path legacy = folder().resolve(LEGACY_CONFIG_FILE);
        try {
            Path source = Files.exists(file) ? file : legacy;
            if (!Files.exists(source)) {
                CobblelockeConfig template = defaults();
                writeConfig(template, false);
                Cobblelocke.LOGGER.info("Wrote the default config template to {}", file);
                return new Saved(template, false);
            }
            JsonObject root = CobblelockeConfig.parseLenient(Files.readString(source, StandardCharsets.UTF_8));
            boolean ask = root.has("askOnFirstJoin") && root.get("askOnFirstJoin").getAsBoolean();
            root.remove("askOnFirstJoin");
            CobblelockeConfig config = CobblelockeConfig.fromJsonObject(root);

            if (config.preset != null && !config.preset.isBlank() && !config.preset.equalsIgnoreCase("Custom")) {
                CobblelockeConfig fromPreset = presetConfig(config.preset, loadPresets());
                if (config.preset.equals(fromPreset.preset)) {
                    fromPreset.keepServerSettings(config);
                    config = fromPreset;
                }
            }
            if (source == legacy) {
                writeConfig(config, ask);
                Cobblelocke.LOGGER.info("Moved {} to {}", legacy, file);
            }
            return new Saved(config, ask);
        } catch (Exception e) {
            Cobblelocke.LOGGER.error("Could not read {}, using defaults: {}", file, e.toString());
            return new Saved(defaults(), false);
        }
    }

    public static void writeConfig(CobblelockeConfig config, boolean askOnFirstJoin) {
        Path file = folder().resolve(CONFIG_FILE);
        try {
            Files.createDirectories(file.getParent());
            JsonObject values = config.toJsonObject();
            StringBuilder out = new StringBuilder();
            out.append("// Cobblelocke rules for new worlds. These rules apply for all the players, when any "
                    + "player saves the config it also saves for everyone else.\n");
            out.append("// Saving from the mc /cobblelocke config command rewrites this file.\n");
            out.append("// World-specific randomization pool tables are kept in each world's cobblelocke/ folder.\n");
            out.append("//\n");
            out.append("// Options that take more than one value:\n");
            out.append("//   Range options are written as [min, max], for example \"gymBst1\": [300, 385].\n");
            out.append("//   Put \"any\" on either side to leave that end open, for example [450, \"any\"].\n");
            out.append("//   List options are written as [\"one\", \"two\"], for example\n");
            out.append("//   \"heldItemWhitelist\": [\"#cobblemon:berries\", \"cobblemon:leftovers\"].\n");
            out.append("// Everything else takes a single value. On a slider, \"default\" or \"any\" turns the\n");
            out.append("// rule off, and Legendary Rarity also accepts \"random\" to apply no rarity at all.\n");
            out.append("{\n");
            out.append("  // true: open the config menu on a new world even though this file exists.\n");
            out.append("  // false: start new worlds with these rules without asking.\n");
            out.append("  \"askOnFirstJoin\": ").append(askOnFirstJoin).append(",\n");
            out.append("  // Behaviour for servers when a player types any /cobblelocke command\n");
            out.append("  // admin: only ops can enter commands and edit the config.\n");
            out.append("  // read-only: non ops can enter /cobblelocke config and view the commands, but "
                    + "cant edit and\n");
            out.append("  // can't enter any other command, admins still have full access.\n");
            out.append("  // config-only: non ops can enter /cobblelocke config and edit the config, "
                    + "including starting\n");
            out.append("  // a run, but can't enter any other command\n");
            out.append("  // any: any player can enter any command. only set this if you trust other "
                    + "players to not do\n");
            out.append("  // /cobblelocke reset @a\n");
            out.append("  // In every mode, any player can use /cobblelocke status, /cobblelocke here and "
                    + "/cobblelocke hints.\n");
            out.append("  \"serverConfigMode\": ").append(GSON.toJson(config.serverConfigMode))
                    .append(",\n");
            out.append("  // Spawn cap timing (true or false).\n");
            out.append("  // false: the Cap Spawning rules start working once a run has started on this server, "
                    + "and keep\n");
            out.append("  // working after the run is stopped or reset; only changing those rules or turning "
                    + "them off changes them.\n");
            out.append("  // true: they also work before any run has started.\n");
            out.append("  // /cobblelocke spawncap on|off changes this from the game. off also turns the spawn "
                    + "cap off until\n");
            out.append("  // the next run starts, and forgets every region it remembered.\n");
            out.append("  \"spawnCapAlwaysOn\": ").append(config.spawnCapAlwaysOn).append(",\n");
            out.append("  // The default preset that the mod uses, by default it's the settings below but if "
                    + "you change this it will load from presets.json5\n");
            out.append("  \"preset\": ").append(GSON.toJson(config.preset)).append(",\n");

            for (String line : optionLines(values)) {
                out.append(line).append('\n');
            }
            out.append("}\n");
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Cobblelocke.LOGGER.error("Could not write {}: {}", file, e.toString());
        }
    }

    private static List<String> optionLines(JsonObject values) {
        ConfigOptions.Tab tab = null;
        List<String> lines = new ArrayList<>();
        for (ConfigOptions.Spec spec : ConfigOptions.all()) {
            if (spec.tab() != tab) {
                tab = spec.tab();
                lines.add("\n  // ===== " + tab.title + " =====");
            }
            if (spec.isHeading()) {
                lines.add("  // --- " + spec.label().toUpperCase() + " ---");
                continue;
            }
            JsonElement value = values.get(spec.key());
            if (value == null) {
                continue;
            }
            if (spec.key().equals(ConfigOptions.ANIMATION_KEYS.get(0))) {
                for (String note : ConfigOptions.CLIP_GUIDE.split("\n")) {
                    lines.add("  // " + note);
                }
            }
            lines.add("  // " + spec.label() + ": " + spec.description() + describeRange(spec));
            if (spec.kind() == ConfigOptions.Kind.RANGE) {
                JsonElement high = values.get(spec.ids().get(1));
                lines.add("  \"" + ConfigOptions.rangeName(spec) + "\": ["
                        + written(spec, value) + ", "
                        + (high == null ? "\"any\"" : written(spec, high)) + "],");
                continue;
            }
            lines.add("  \"" + spec.key() + "\": " + written(spec, value) + ",");
        }

        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.startsWith("  \"") && line.endsWith(",")) {
                lines.set(i, line.substring(0, line.length() - 1));
                break;
            }
        }
        return lines;
    }

    private static String written(ConfigOptions.Spec spec, JsonElement value) {
        if ((spec.kind() == ConfigOptions.Kind.SLIDER || spec.kind() == ConfigOptions.Kind.RANGE)
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isNumber()) {
            int number = value.getAsInt();
            if (number == CobblelockeConfig.RANDOM_RARITY) {
                return "\"random\"";
            }
            if (number == 0 && !spec.labels().isEmpty()) {
                String zero = spec.labels().get(0);
                if ("Default".equals(zero) || "Any".equals(zero)) {
                    return "\"" + zero.toLowerCase(java.util.Locale.ROOT) + "\"";
                }
            }
        }
        return GSON.toJson(value);
    }

    private static String describeRange(ConfigOptions.Spec spec) {
        if (spec.guide() != null) {
            return spec.guide().isEmpty() ? "" : " (" + spec.guide() + ")";
        }
        return switch (spec.kind()) {
            case CHOICE, OPTION -> "";
            case SLIDER -> switch (spec.unit()) {
                case "in" -> " (1 in N)";
                case "s" -> " (seconds)";
                default -> "";
            };
            default -> "";
        };
    }

    public static JsonArray loadPresets() {
        Path file = folder().resolve(PRESETS_FILE);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, defaultPresetsFile(), StandardCharsets.UTF_8);
            }
            JsonObject root = CobblelockeConfig.parseLenient(Files.readString(file, StandardCharsets.UTF_8));
            return root.has("presets") ? root.getAsJsonArray("presets") : new JsonArray();
        } catch (Exception e) {
            Cobblelocke.LOGGER.error("Could not read {}: {}", file, e.toString());
            return new JsonArray();
        }
    }

    public static CobblelockeConfig presetConfig(String name, JsonArray presets) {
        for (JsonElement element : presets) {
            JsonObject preset = element.getAsJsonObject();
            if (preset.has("name") && name.equals(preset.get("name").getAsString())) {
                return CobblelockeConfig.fromPreset(name, preset.getAsJsonObject("rules"));
            }
        }
        return defaults();
    }

    private static String defaultPresetsFile() {
        StringBuilder out = new StringBuilder();
        out.append("// Presets shown at the top of /cobblelocke config.\n");
        out.append("// Each preset starts from the defaults and applies its rules over them; any option\n");
        out.append("// from config.json5 can be used, and the order here matches that file.\n");
        out.append("// Add your own by appending to this list.\n");
        out.append("{\n  \"presets\": [\n");

        out.append(preset(COBBLELOCKE_RANDOMLOCKE,
                "The Mod Author's Ruleset: Nuzlocke+Randomlocke rules with global abilities and moves, "
                        + "random TMs, Event Lock, Tri-evolution Starters and Double Battles for every "
                        + "Gym Trainer",
                defaults()));
        out.append(",\n");
        out.append(preset(CLASSIC_NUZLOCKE,
                "The classic challenge with no randomization: one catch per biome, no duplicates, no "
                        + "healing, fainted Cobblemon are released, catch only in battle, and nickname "
                        + "everything.",
                classicNuzlocke()));
        out.append(",\n");
        out.append(preset(CLASSIC_RANDOMLOCKE,
                "Random, world-wide moves and abilities for wild, starter and trainer Cobblemon, "
                        + "tri-evolution starters, random trainer items, the biome event lock, and the core "
                        + "nuzlocke rules.",
                classicRandomlocke()));
        out.append("\n  ]\n}\n");
        return out.toString();
    }

    private static String preset(String name, String description, CobblelockeConfig rules) {
        return preset(name, description, rules.toJsonObject());
    }

    private static String preset(String name, String description, JsonObject rules) {
        StringBuilder out = new StringBuilder();
        out.append("    {\n");
        out.append("      \"name\": ").append(GSON.toJson(name)).append(",\n");
        out.append("      \"description\": ").append(GSON.toJson(description)).append(",\n");
        out.append("      \"rules\": {\n");
        for (String line : optionLines(rules)) {
            out.append(line.startsWith("\n") ? "\n    " + line.substring(1) : "    " + line).append('\n');
        }
        out.append("      }\n    }");
        return out.toString();
    }

    public enum ExportResult {
        ADDED,
        REPLACED,
        FAILED
    }

    public static final String EXPORT_DESCRIPTION = "Saved from the in-game settings.";

    public static ExportResult exportPreset(String name, CobblelockeConfig config) {
        if (name == null || name.isBlank() || config == null) {
            return ExportResult.FAILED;
        }
        String wanted = name.trim();
        Path file = folder().resolve(PRESETS_FILE);
        try {
            JsonArray existing;
            if (Files.exists(file)) {
                JsonObject root = CobblelockeConfig.parseLenient(
                        Files.readString(file, StandardCharsets.UTF_8));
                existing = root.has("presets") ? root.getAsJsonArray("presets") : new JsonArray();
            } else {
                existing = loadPresets();
            }
            CobblelockeConfig saved = config.copy();
            saved.preset = wanted;
            saved.runActive = false;
            saved.configured = false;

            JsonObject entry = new JsonObject();
            entry.addProperty("name", wanted);
            entry.addProperty("description", EXPORT_DESCRIPTION);
            entry.add("rules", saved.toJsonObject());

            boolean replaced = false;
            JsonArray merged = new JsonArray();
            for (JsonElement element : existing) {
                JsonObject preset = element.getAsJsonObject();
                String presetName = preset.has("name") ? preset.get("name").getAsString() : "";
                if (presetName.equalsIgnoreCase(wanted)) {
                    merged.add(entry);
                    replaced = true;
                } else {
                    merged.add(preset);
                }
            }
            if (!replaced) {
                merged.add(entry);
            }

            StringBuilder out = new StringBuilder();
            out.append("// Presets shown at the top of /cobblelocke config.\n");
            out.append("// Each preset starts from the defaults and applies its rules over them; any option\n");
            out.append("// from config.json5 can be used, and the order here matches that file.\n");
            out.append("// Add your own by appending to this list, or from the game with the Export button\n");
            out.append("// in /cobblelocke config, or /cobblelocke export <name>.\n");
            out.append("{\n  \"presets\": [\n");
            for (int i = 0; i < merged.size(); i++) {
                JsonObject preset = merged.get(i).getAsJsonObject();
                out.append(preset(
                        preset.has("name") ? preset.get("name").getAsString() : "Unnamed",
                        preset.has("description") ? preset.get("description").getAsString() : "",
                        preset.has("rules") ? preset.getAsJsonObject("rules") : new JsonObject()));
                out.append(i == merged.size() - 1 ? "\n" : ",\n");
            }
            out.append("  ]\n}\n");

            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
            Cobblelocke.LOGGER.info("Exported the preset {} to {}", wanted, file);
            return replaced ? ExportResult.REPLACED : ExportResult.ADDED;
        } catch (Exception e) {
            Cobblelocke.LOGGER.error("Could not export the preset {}: {}", wanted, e.toString());
            return ExportResult.FAILED;
        }
    }

    public static boolean presetExists(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (JsonElement element : loadPresets()) {
            JsonObject preset = element.getAsJsonObject();
            if (preset.has("name") && preset.get("name").getAsString().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    public static CobblelockeConfig defaults() {
        CobblelockeConfig config = new CobblelockeConfig();
        config.preset = "Custom";

        config.nuzlockeModeEnabled = true;
        config.oneCatchPerBiome = false;
        config.oneCatchPerRegion = 2048;
        config.capSpawningPerRegionChunks = 8;
        config.capSpawningPerRegionCount = 1;
        config.capSpawningPerRegionMemory = false;
        config.capSpawningPerRegionByPlayer = false;
        config.noDuplicates = true;
        config.allowRepeatAfterFaint = true;
        config.noHealing = true;
        config.releaseFainted = false;
        config.dropHeldItemsOnFaint = true;
        config.enableTerrainDeaths = true;
        config.shinyClause = true;
        config.onlyCatchInBattle = true;
        config.requireNicknames = true;
        config.catchCooldownSeconds = 0;

        config.firstCatchEventLocked = true;
        config.isExtraCatch = true;
        config.eventLockRegion = 0;
        config.eventLevelMode = 0;
        config.applyAnimationPreset("minecraft");
        config.disableRaidCatch = true;

        config.smoothSpawning = true;
        config.randomSpawns = true;
        config.spawnsLegendaries = true;
        config.legendaryRarity = CobblelockeConfig.RANDOM_RARITY;
        config.randomWildCaptures = false;
        config.randomWildTypes = false;
        config.globalWildTypes = false;
        config.randomWildAbilities = true;
        config.globalWildAbilities = true;
        config.randomWildMoves = true;
        config.globalWildMoves = true;
        config.randomWildHeldItems = true;
        config.randomEvolutions = false;
        config.evolutionsSameStage = true;
        config.evolutionsSimilarBST = false;
        config.randomTmMoves = true;
        config.typesKeepOneType = false;
        config.typesRandomCount = 0;
        config.overrideShinyRate = false;
        config.shinyRate = 8192;
        config.heldItemWhitelist = new ArrayList<>(List.of("#cobblemon:berries",
                "#cobblemon:terrain_seeds", "#cobblemon:type_gems", "#cobblemon:battle"));
        config.randomizeTeras = true;
        config.randomizeMegas = false;

        config.randomStarters = true;
        config.starterRegion = "all";
        config.startersNoLegendaries = true;
        config.startersBasicOnly = true;
        config.startersTriEvolution = true;
        config.startersTypeTrio = false;
        config.randomStarterTypes = false;
        config.globalStarterTypes = false;
        config.randomStarterAbilities = true;
        config.globalStarterAbilities = true;
        config.randomStarterMoves = true;
        config.globalStarterMoves = true;
        config.offeredStarterTrios = 1;
        config.starterTeras = false;
        config.starterMegas = false;
        config.starterDyna = false;

        config.randomTrainerSpecies = true;
        config.trainerMinBst = 0;
        config.trainerTeamSize = 0;
        config.doubleBattle = 1;
        config.doubleBattleTeamSize = 4;
        config.randomTrainerTypes = false;
        config.globalTrainerTypes = false;
        config.randomTrainerAbilities = true;
        config.globalTrainerAbilities = true;
        config.randomTrainerMoves = true;
        config.globalTrainerMoves = true;
        config.randomTrainerHeldItems = true;
        config.trainerTeras = true;
        config.trainerMegas = true;
        config.trainerDyna = false;

        config.gymBstMin1 = 300;
        config.gymBstMax1 = 385;
        config.gymLevel1 = 14;
        config.gymCount1 = 3;
        config.gymBstMin2 = 300;
        config.gymBstMax2 = 515;
        config.gymLevel2 = 22;
        config.gymCount2 = 3;
        config.gymBstMin3 = 300;
        config.gymBstMax3 = 525;
        config.gymLevel3 = 25;
        config.gymCount3 = 3;
        config.gymBstMin4 = 430;
        config.gymBstMax4 = 540;
        config.gymLevel4 = 30;
        config.gymCount4 = 3;
        config.gymBstMin5 = 450;
        config.gymBstMax5 = 560;
        config.gymLevel5 = 36;
        config.gymCount5 = 4;
        config.gymBstMin6 = 450;
        config.gymBstMax6 = 600;
        config.gymLevel6 = 39;
        config.gymCount6 = 4;
        config.gymBstMin7 = 470;
        config.gymBstMax7 = 620;
        config.gymLevel7 = 45;
        config.gymCount7 = 4;
        config.gymBstMin8 = 490;
        config.gymBstMax8 = 650;
        config.gymLevel8 = 50;
        config.gymCount8 = 4;
        config.eliteBstMin1 = 530;
        config.eliteBstMax1 = 690;
        config.eliteLevel1 = 57;
        config.eliteCount1 = 5;
        config.eliteBstMin2 = 550;
        config.eliteBstMax2 = 700;
        config.eliteLevel2 = 59;
        config.eliteCount2 = 5;
        config.eliteBstMin3 = 560;
        config.eliteBstMax3 = 710;
        config.eliteLevel3 = 61;
        config.eliteCount3 = 5;
        config.eliteBstMin4 = 580;
        config.eliteBstMax4 = 720;
        config.eliteLevel4 = 63;
        config.eliteCount4 = 5;
        config.champBstMin = 650;
        config.champBstMax = 900;
        config.champLevel = 65;
        config.champCount = 6;
        return config;
    }

    private static CobblelockeConfig classicNuzlocke() {
        CobblelockeConfig config = new CobblelockeConfig();
        config.preset = CLASSIC_NUZLOCKE;
        config.nuzlockeModeEnabled = true;
        config.smoothSpawning = true;
        config.oneCatchPerBiome = true;
        config.noDuplicates = true;
        config.noHealing = true;
        config.releaseFainted = true;
        config.onlyCatchInBattle = true;
        config.requireNicknames = true;
        config.shinyClause = true;
        config.disableRaidCatch = true;
        config.applyAnimationPreset("retro");
        return config;
    }

    private static CobblelockeConfig classicRandomlocke() {
        CobblelockeConfig config = classicNuzlocke();
        config.preset = CLASSIC_RANDOMLOCKE;
        config.releaseFainted = false;
        config.firstCatchEventLocked = true;
        config.isExtraCatch = true;
        config.eventLockRegion = 0;
        config.randomStarters = true;
        config.offeredStarterTrios = 1;
        config.startersNoLegendaries = true;
        config.startersTriEvolution = true;
        config.randomStarterMoves = true;
        config.globalStarterMoves = true;
        config.randomStarterAbilities = true;
        config.globalStarterAbilities = true;
        config.randomSpawns = true;
        config.randomWildMoves = true;
        config.globalWildMoves = true;
        config.randomWildAbilities = true;
        config.globalWildAbilities = true;
        config.randomTrainerSpecies = true;
        config.randomTrainerMoves = true;
        config.globalTrainerMoves = true;
        config.randomTrainerAbilities = true;
        config.globalTrainerAbilities = true;
        config.randomTrainerHeldItems = true;
        config.applyAnimationPreset("retro");
        return config;
    }
}
