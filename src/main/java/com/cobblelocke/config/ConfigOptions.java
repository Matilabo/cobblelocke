package com.cobblelocke.config;

import java.util.ArrayList;
import java.util.List;

public final class ConfigOptions {
    public enum Tab {
        NUZLOCKE("Nuzlocke"),
        RANDOMIZER("Randomizer"),
        STARTERS("Starters"),
        TRAINERS("Trainers");

        public final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    public enum Kind {
        HEADING,
        TOGGLE,

        CHOICE,

        OPTION,

        SLIDER,

        RANGE,

        LIST
    }

    public record Spec(String key, String label, String description, Tab tab, String parent, Kind kind,
                       List<Integer> values, List<String> labels, String unit, List<String> ids,
                       String guide) {
        public boolean isHeading() {
            return kind == Kind.HEADING;
        }

        public int indexOfId(String id) {
            int index = ids.indexOf(id);
            return index < 0 ? 0 : index;
        }
    }

    private static final List<Spec> ALL = new ArrayList<>();

    private ConfigOptions() {
    }

    public static List<Spec> all() {
        return ALL;
    }

    public static String rangeName(Spec spec) {
        String low = spec.ids().isEmpty() ? spec.key() : spec.ids().get(0);
        if (low == null) {
            return spec.key();
        }
        String stripped = low.replaceFirst("Min", "");
        if (!stripped.equals(low)) {
            return stripped;
        }
        stripped = low.replaceFirst("min", "");
        return stripped.equals(low) ? low : stripped;
    }

    public static Spec byKey(String key) {
        for (Spec spec : ALL) {
            if (key.equals(spec.key())) {
                return spec;
            }
        }
        return null;
    }

    private static Tab current;

    private static void tab(Tab tab) {
        current = tab;
    }

    private static void heading(String title) {
        ALL.add(new Spec(null, title, null, current, null, Kind.HEADING, List.of(), List.of(), "", List.of(), ""));
    }

    private static void toggle(String key, String parent, String label, String description) {
        ALL.add(new Spec(key, label, withNote(key, description), current, parent, Kind.TOGGLE, List.of(),
                List.of(), "", List.of(), ""));
    }

    private static void choice(String key, String parent, String label, String description, String guide,
                               String... labels) {
        ALL.add(new Spec(key, label, description, current, parent, Kind.CHOICE, List.of(), List.of(labels), "",
                List.of(), guide));
    }

    private static void option(String key, String parent, String label, String description,
                               List<String> ids, List<String> labels, String guide) {
        ALL.add(new Spec(key, label, description, current, parent, Kind.OPTION, List.of(), labels, "", ids, guide));
    }

    private static void slider(String key, String parent, String label, String description, String unit,
                               List<Integer> values, List<String> labels) {
        slider(key, parent, label, description, unit, values, labels, null);
    }

    private static void range(String lowKey, String highKey, String parent, String label,
                              String description, List<Integer> values, List<String> labels,
                              String guide) {
        ALL.add(new Spec(lowKey, label, withNote(lowKey, description), current, parent, Kind.RANGE,
                values, labels, "", List.of(lowKey, highKey), guide));
    }

    private static void slider(String key, String parent, String label, String description, String unit,
                               List<Integer> values, List<String> labels, String guide) {
        ALL.add(new Spec(key, label, withNote(key, description), current, parent, Kind.SLIDER, values,
                labels, unit, List.of(), guide));
    }

    private static void list(String key, String parent, String label, String description) {
        ALL.add(new Spec(key, label, description, current, parent, Kind.LIST, List.of(), List.of(), "",
                List.of(), ""));
    }

    private static final List<Integer> BST_VALUES =
            List.of(0, 250, 300, 350, 400, 450, 500, 550, 600, 700, 800, 900);
    private static final List<String> BST_LABELS =
            List.of("Any", "250", "300", "350", "400", "450", "500", "550", "600", "700", "800", "900");

    private static final List<Integer> GYM_LEVELS =
            List.of(0, 10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90, 100);
    private static final List<String> GYM_LEVEL_LABELS =
            List.of("Default", "10", "15", "20", "25", "30", "35", "40", "45", "50", "60", "70", "80",
                    "90", "100");

    private static final List<Integer> TEAM_SIZES = List.of(0, 1, 2, 3, 4, 5, 6);
    private static final List<String> TEAM_SIZE_LABELS = List.of("Default", "1", "2", "3", "4", "5", "6");

    private static final List<Integer> REGION_SIZES = List.of(0, 32, 64, 128, 256, 512, 1024, 2048);
    private static final List<String> REGION_SIZE_LABELS =
            List.of("Off", "32", "64", "128", "256", "512", "1024", "2048");

    private static final List<Integer> REGION_COUNTS = List.of(1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20);

    private static final List<Integer> REGION_CHUNKS = List.of(0, 1, 2, 4, 8, 16, 32);
    private static final List<String> REGION_CHUNK_LABELS =
            List.of("Off", "1 chunk", "2 chunks", "4 chunks", "8 chunks", "16 chunks", "32 chunks");

    private static final List<Integer> STARTER_TRIOS = List.of(1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20);

    private static final List<Integer> LEGENDARY_RARITIES =
            List.of(1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, CobblelockeConfig.RANDOM_RARITY);
    private static final List<String> LEGENDARY_RARITY_LABELS =
            List.of("Always", "1 in 5", "1 in 10", "1 in 25", "1 in 50", "1 in 100", "1 in 250", "1 in 500",
                    "1 in 1000", "1 in 2500", "1 in 5000", "1 in 10000", "Random");

    public static final List<String> REGION_IDS = List.of(
            "all", "kanto", "johto", "hoenn", "sinnoh", "unova", "kalos", "alola", "galar", "hisui", "paldea");
    private static final List<String> REGION_LABELS = List.of(
            "All Regions", "1 Kanto", "2 Johto", "3 Hoenn", "4 Sinnoh", "5 Unova", "6 Kalos", "7 Alola",
            "8 Galar", "8 Hisui", "9 Paldea");

    static final String[] LEVEL_MODE_LABELS = {"Default", "Close", "True Random Level", "Match Highest"};
    static final String LEVEL_MODE_HELP =
            "Default: randomly choose from level 1 to the level of strongest party member. Close: same or "
                    + "below some levels of your strongest party member. True Random Level: anything from 1 "
                    + "to 100. Match Highest: the same level as your strongest party member.";

    private static final String GYM_NOTE =
            "Gym numbers come from the pack's own badge order. Recommended to run with the "
                    + "COBBLEVERSE Gyms!";

    private static final String LEAGUE_NOTE =
            "Elite Four and Champion order comes from the pack's own league order. Recommended to run "
                    + "with the COBBLEVERSE Gyms!";

    private static String withNote(String key, String description) {
        if (key == null || description == null) {
            return description;
        }
        if (key.startsWith("gym")) {
            return description + " " + GYM_NOTE;
        }
        if (key.startsWith("elite") || key.startsWith("champ")) {
            return description + " " + LEAGUE_NOTE;
        }
        return description;
    }

    public static final String ITEM_GROUP_TIP =
            "Groups: #cobblemon:berries, #cobblemon:terrain_seeds, #cobblemon:type_gems, #cobblemon:battle.";

    private static final List<String> ANIMATION_PRESET_IDS =
            List.of("action", "minecraft", "retro", "custom");
    private static final List<String> ANIMATION_PRESET_LABELS =
            List.of("Action", "Cobblelocke Minecraft", "Retro", "Custom");

    public static final List<String> CLIP_IDS = List.of(
            "random", "default",
            "action_grass", "action_water", "action_cave", "action_nether", "action_end", "action_default",
            "mc_grass", "mc_grass_flowers", "mc_grass_random", "mc_water", "mc_cave",
            "mc_nether_crimson", "mc_nether_warped", "mc_end",
            "retro_grass", "retro_water", "retro_cave", "retro_trainer", "retro_default");
    private static final List<String> CLIP_LABELS = List.of(
            "Random", "Default Transition",
            "Action Grass", "Action Water", "Action Cave", "Action Nether", "Action End", "Action Default",
            "MC Grass", "MC Grass & Flowers", "MC Grass (Random)", "MC Water", "MC Cave",
            "MC Crimson Nether", "MC Warped Nether", "MC End",
            "Retro Grass", "Retro Water", "Retro Cave", "Retro Trainer", "Retro Default");

    public static final String CLIP_GUIDE =
            "List of available animations:\n"
                    + "action_default, action_grass, action_water, action_cave, action_nether, action_end\n"
                    + "mc_grass, mc_grass_flowers, mc_water, mc_cave, mc_nether_crimson, mc_nether_warped, mc_end\n"
                    + "retro_grass, retro_water, retro_cave, retro_trainer, retro_default\n"
                    + "mc_grass_random: plays either mc_grass or mc_grass_flowers at random\n"
                    + "default: default animation. random: play any animation for that particular biome, "
                    + "if animDefault is set to random, picks between any retro animation";

    public static final List<String> ANIMATION_KEYS = List.of(
            "animOverworld", "animWater", "animCave", "animNetherCrimson", "animNetherWarped",
            "animEnd", "animDefault");

    static {
        tab(Tab.NUZLOCKE);
        heading("Nuzlocke Settings");
        toggle("nuzlockeModeEnabled", null, "Nuzlocke Mode",
                "Enable the Nuzlocke mode. It begins once you have chosen a starter.");
        toggle("oneCatchPerBiome", "nuzlockeModeEnabled", "One Catch Per Biome",
                "Only one catch per biome, anywhere in the world.");
        slider("oneCatchPerRegion", "nuzlockeModeEnabled", "One Catch Per Region",
                "The world is cut into block regions, the recomended is 512x512 blocks, one catch per "
                        + "region.", "", REGION_SIZES, REGION_SIZE_LABELS, "");
        slider("capSpawningPerRegionChunks", "nuzlockeModeEnabled", "Cap Spawning Per Region by Size",
                "Set a region size in chunks. Once the cap below is reached, further spawns there are stopped.",
                "", REGION_CHUNKS, REGION_CHUNK_LABELS, "in chunks, 1 chunk is 16x16 blocks");
        slider("capSpawningPerRegionCount", "capSpawningPerRegionChunks", "Cap Spawning Per Region Count",
                "How many wild Cobblemon may be alive at once inside one region.", "",
                REGION_COUNTS, List.of());
        toggle("capSpawningPerRegionMemory", "capSpawningPerRegionChunks", "Cap Spawning Per Region Memory",
                "If a player leaves the region and the cobblemon despawn and the cap for that region was "
                        + "reached, they don't respawn in that region ever again (per player)");
        toggle("capSpawningPerRegionByPlayer", "capSpawningPerRegionChunks", "Cap Spawning Per Region by Player",
                "If true, all players share the count and memory of a region, per player restrictions of the "
                        + "two previous rules are ignored");
        toggle("noDuplicates", "nuzlockeModeEnabled", "No Duplicates",
                "No repeat species, and no other member of an evolution line you already caught.");
        toggle("allowRepeatAfterFaint", "noDuplicates", "Allow Repeat Species After Fainting",
                "Once every Cobblemon you had from an evolution line has fainted for good, you may catch "
                        + "that line again.");
        toggle("noHealing", "nuzlockeModeEnabled", "No Healing",
                "A Cobblemon that faints can never be healed or revived.");
        toggle("releaseFainted", "noHealing", "Release Fainted",
                "A Cobblemon that faints is released from your party :(");
        toggle("dropHeldItemsOnFaint", "noHealing", "Drop Held Items On Faint",
                "A Cobblemon that faints drops whatever it was holding, whether it fainted in battle or "
                        + "to the world.");
        toggle("enableTerrainDeaths", "nuzlockeModeEnabled", "Enable Terrain Deaths",
                "Cobblemon that faint outside battle from drowning, fire, falling or suffocating count as "
                        + "fainted. When off, those faints can always be revived, whatever the other rules say.");
        toggle("ignorePvpFaints", "nuzlockeModeEnabled", "Ignore PvP Faints",
                "A Cobblemon that goes down to another player is sparring, not dying: those faints never "
                        + "count, whatever the other rules say.");
        toggle("shinyClause", "nuzlockeModeEnabled", "Shiny Clause",
                "Shiny Cobblemon ignore every catch restriction.");
        toggle("onlyCatchInBattle", "nuzlockeModeEnabled", "Only Catch In-Battle",
                "Poké Balls only work on a Cobblemon you are battling.");
        toggle("requireNicknames", "nuzlockeModeEnabled", "Nickname",
                "Every Cobblemon must be nicknamed: a naming box opens as soon as you catch one.");
        slider("catchCooldownSeconds", "nuzlockeModeEnabled", "Catch Cooldown",
                "Time you must wait between catches. value is in seconds", "s",
                List.of(0, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 14400, 28800, 43200, 86400),
                List.of(), "");

        heading("Event Lock");
        toggle("firstCatchEventLocked", null, "First Catch Is Event Locked",
                "Entering a new biome and staying 5 seconds plays an animation and forces a battle with a "
                        + "randomized Cobblemon. That Cobblemon is the biome's first catch, and catching stays "
                        + "locked until you leave the biome you started in.");
        choice("eventLevelMode", "firstCatchEventLocked", "Event Cobblemon Level", LEVEL_MODE_HELP,
                "", LEVEL_MODE_LABELS);
        option("animationPreset", "firstCatchEventLocked", "Encounter Animation Preset",
                "Sets every kind of event animations below at a specific set of clips. Action has quick "
                        + "and flashy animations, Cobblelocke Minecraft has Minecraft themed animations, and "
                        + "Retro is plain black wipes and is the most minimal. Changing any single setting "
                        + "below turns this to Custom.",
                ANIMATION_PRESET_IDS, ANIMATION_PRESET_LABELS,
                "action = Action, minecraft = Cobblelocke Minecraft, retro = Retro, custom = Custom");
        option("animOverworld", "firstCatchEventLocked", "Overworld Biomes",
                "Plays anywhere above ground in the overworld.", CLIP_IDS, CLIP_LABELS, "");
        option("animWater", "firstCatchEventLocked", "Water Biomes",
                "Plays in an ocean or river, and anywhere the player is actually in water.",
                CLIP_IDS, CLIP_LABELS, "");
        option("animCave", "firstCatchEventLocked", "Caves",
                "Plays underground, wherever there is no sky overhead.", CLIP_IDS, CLIP_LABELS, "");
        option("animNetherCrimson", "firstCatchEventLocked", "Crimson Nether",
                "Plays in the Nether, except in the warped half.", CLIP_IDS, CLIP_LABELS, "");
        option("animNetherWarped", "firstCatchEventLocked", "Warped Nether",
                "Plays in warped forests and soul sand valleys.", CLIP_IDS, CLIP_LABELS, "");
        option("animEnd", "firstCatchEventLocked", "The End",
                "Plays in the End.", CLIP_IDS, CLIP_LABELS, "");
        option("animDefault", "firstCatchEventLocked", "Everywhere Else",
                "Plays anywhere the settings above do not cover, including other mods' dimensions.",
                CLIP_IDS, CLIP_LABELS, "");

        heading("Compatibility");
        toggle("disableRaidCatch", null, "Disable Raid Cobblemon Catch",
                "Raid Dens bosses can still be battled, but never caught. Needs Cobblemon Raid Dens installed.");

        tab(Tab.RANDOMIZER);
        heading("Wild Cobblemon");
        toggle("smoothSpawning", null, "Smooth Spawning",
                "Disables the cobblemon mod feature that causes that a group of cobblemon from the same "
                        + "species spawn at the same time in a group, so that randomized cobblemon don't "
                        + "spawn all grouped and instead spawn more naturally.");
        toggle("randomSpawns", null, "Random Wild Spawns",
                "Enable wild randomization: any Cobblemon can spawn anywhere, and the wild options below unlock.");
        toggle("spawnsLegendaries", "randomSpawns", "Legendaries",
                "Lets legendary, mythical, paradox and ultra beast Cobblemon turn up in random spawns.");
        slider("legendaryRarity", "spawnsLegendaries", "Legendary Rarity",
                "How rare legendaries are among random spawns. Random lets the world pick its own rarity.",
                "in", LEGENDARY_RARITIES, LEGENDARY_RARITY_LABELS, "");
        toggle("legendariesReplaceLegendaries", "randomSpawns", "Legendaries Replace Legendaries",
                "A legendary that the world spawns on its own is only ever replaced by another legendary, "
                        + "and never turns into an ordinary Cobblemon. Covers fixed spawns like the "
                        + "Articuno, Zapdos and Moltres towers. Works whether or not Legendaries is on.");
        toggle("randomWildCaptures", "randomSpawns", "Random Wild Species",
                "A caught Cobblemon transforms into a random species.");
        toggle("randomWildTypes", "randomSpawns", "Random Wild Types",
                "Wild Cobblemon spawn with random types.");
        toggle("globalWildTypes", "randomWildTypes", "Global Types",
                "One fixed type table for the world: every wild Pikachu shares the same types.");
        toggle("randomWildAbilities", "randomSpawns", "Random Wild Abilities",
                "Wild Cobblemon spawn with random abilities.");
        toggle("globalWildAbilities", "randomWildAbilities", "Global Abilities",
                "One fixed ability table for the world: every wild Pikachu has the same ability.");
        toggle("randomWildMoves", "randomSpawns", "Random Wild Moves",
                "Wild Cobblemon get a random movepool, learned by level as usual.");
        toggle("globalWildMoves", "randomWildMoves", "Global Moves",
                "One fixed movepool table for the world: every wild Pikachu learns the same moves.");
        toggle("randomWildHeldItems", "randomSpawns", "Random Wild Held Items",
                "Wild Cobblemon spawn holding a random item.");

        heading("Evolutions & Moves");
        toggle("randomEvolutions", null, "Random Evolutions",
                "Cobblemon evolve into a random species.");
        toggle("evolutionsSameStage", "randomEvolutions", "Keep Evolution Stage",
                "A replacement evolution is at the same stage as the original.");
        toggle("evolutionsSimilarBST", "randomEvolutions", "Similar Base Stats",
                "A replacement evolution has a comparable base stat total.");
        toggle("randomTmMoves", null, "Learnable Random TMs",
                "Each Cobblemon gets its own random TM table, and keeps it when it evolves.");

        heading("Types");
        toggle("typesKeepOneType", null, "Keep One Original Type",
                "Randomized Cobblemon keep one of the types they started with.");
        choice("typesRandomCount", null, "Type Count",
                "How many types a randomized Cobblemon ends up with.", "", "Random", "Single", "Dual");

        heading("Shinies & Items");
        toggle("overrideShinyRate", null, "Override Shiny Rarity",
                "Replaces Cobblemon's own shiny rate while the run is active.");
        slider("shinyRate", "overrideShinyRate", "Shiny Rarity",
                "How rare shiny Cobblemon are.", "in",
                List.of(1, 16, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384), List.of());
        list("heldItemWhitelist", null, "Held Item Whitelist",
                "Item ids random held items are drawn from, e.g. cobblemon:leftovers. Empty uses Cobblemon's "
                        + "and Mega Showdown's battle items. " + ITEM_GROUP_TIP);
        list("heldItemBlacklist", null, "Held Item Blacklist",
                "Item ids that are never handed out as random held items. " + ITEM_GROUP_TIP);

        heading("Compatibility");
        toggle("randomizeTeras", null, "Randomize Teras",
                "Wild cobblemon can be Terastallized. Needs Cobblemon Mega Showdown installed.");
        toggle("randomizeMegas", null, "Randomize Megas",
                "Wild cobblemon can be Mega evolutions (and come with the mega stone equipped). Needs "
                        + "Cobblemon Mega Showdown installed.");

        tab(Tab.STARTERS);
        heading("Starter Randomization");
        toggle("randomStarters", null, "Random Starters",
                "Choose your starter from random Cobblemon.");
        option("starterRegion", "randomStarters", "Starter Region",
                "Which regional Pokédex starters are drawn from. These are the real regional dexes, so a "
                        + "region includes the older Cobblemon that appear in it.",
                REGION_IDS, REGION_LABELS, "all, kanto, johto, hoenn, sinnoh, unova, kalos, alola, galar, "
                        + "hisui, paldea");
        toggle("startersNoLegendaries", "randomStarters", "No Legendaries",
                "Excludes legendaries, mythicals, paradox Cobblemon and ultra beasts.");
        toggle("startersBasicOnly", "randomStarters", "No Evolved Cobblemon",
                "Only unevolved Cobblemon can appear as starters.");
        toggle("startersTriEvolution", "randomStarters", "Tri-evolution Starters",
                "Starters are Cobblemon that evolve twice, not always offered as the first stage unless No "
                        + "Evolved Cobblemon is on.");
        toggle("startersTypeTrio", "randomStarters", "Traditional Starter Trio Match",
                "Every starter set keeps the classic Grass / Fire / Water typing, filled with random species "
                        + "of those types.");
        toggle("randomStarterTypes", "randomStarters", "Randomize Types",
                "Starters get random types.");
        toggle("globalStarterTypes", "randomStarterTypes", "Global Types",
                "Starters take their types from the world's fixed table.");
        toggle("randomStarterAbilities", "randomStarters", "Randomize Abilities",
                "Starters get random abilities.");
        toggle("globalStarterAbilities", "randomStarterAbilities", "Global Abilities",
                "Starters take their ability from the world's fixed table.");
        toggle("randomStarterMoves", "randomStarters", "Randomize Movepool",
                "Starters get a random movepool.");
        toggle("globalStarterMoves", "randomStarterMoves", "Global Moves",
                "Starters take their movepool from the world's fixed table.");
        slider("offeredStarterTrios", "randomStarters", "Offered Starter Trios",
                "Changes the ammount of starter trios offered, so that players can only choose 1 starter "
                        + "from any amount of 3 starter groups.", "", STARTER_TRIOS, List.of(),
                "1 to " + CobblelockeConfig.MAX_STARTER_TRIOS);

        heading("Compatibility");
        toggle("starterTeras", null, "Starter Teras",
                "A starter can come with a random Tera type. Needs Cobblemon Mega Showdown installed.");
        toggle("starterMegas", null, "Starter Megas",
                "A starter that has a Mega Evolution comes with its mega stone equipped. Needs Cobblemon "
                        + "Mega Showdown installed.");
        toggle("starterDyna", null, "Starter Dynamax",
                "A starter that has a Gigantamax form can Dynamax. Needs Cobblemon Mega Showdown "
                        + "installed.");

        tab(Tab.TRAINERS);
        heading("NPC Trainers");
        toggle("randomTrainerSpecies", null, "Random Trainer Species",
                "Enables trainer Cobblemon to be replaced by random species, and the trainer options below "
                        + "unlock. Covers Cobblemon NPCs, RCT trainers and anything else that battles. "
                        + "(excluding pvp)");
        range("trainerMinBst", "trainerMaxBst", "randomTrainerSpecies", "Base Stat Total Range",
                "The base stat total random trainer Cobblemon are drawn from. Any on either side leaves "
                        + "that end open.", BST_VALUES, BST_LABELS,
                "min 300:easy, 450:medium, 600:hard - max 350:easy, 500:medium, 900:hard");
        choice("trainerLevelMode", "randomTrainerSpecies", "NPC Trainer Cobblemon Level", LEVEL_MODE_HELP,
                "", LEVEL_MODE_LABELS);
        slider("trainerTeamSize", null, "Trainer Team Size",
                "How many Cobblemon a regular trainer battles with. (max 6, default uses the rct mod "
                        + "default)", "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        choice("doubleBattle", null, "Enable Double Battle",
                "All trainers use a double battle combat instead. Random tosses a coin for each battle.",
                "0 = Off, 1 = On, 2 = Random", "Off", "On", "Random");
        slider("doubleBattleTeamSize", "doubleBattle", "Double Battle Team Size",
                "How many Cobblemon a trainer uses in a double battle.", "",
                List.of(2, 3, 4, 5, 6), List.of());
        toggle("randomTrainerTypes", "randomTrainerSpecies", "Random Trainer Types",
                "Trainer Cobblemon battle with random types.");
        toggle("globalTrainerTypes", "randomTrainerTypes", "Global Types",
                "Trainer Cobblemon take their types from the world's fixed table.");
        toggle("randomTrainerAbilities", "randomTrainerSpecies", "Random Trainer Abilities",
                "Trainer Cobblemon battle with random abilities.");
        toggle("globalTrainerAbilities", "randomTrainerAbilities", "Global Abilities",
                "Trainer Cobblemon take their ability from the world's fixed table.");
        toggle("randomTrainerMoves", "randomTrainerSpecies", "Random Trainer Moves",
                "Trainer Cobblemon battle with random movesets.");
        toggle("globalTrainerMoves", "randomTrainerMoves", "Global Moves",
                "Trainer Cobblemon take their movepool from the world's fixed table.");
        toggle("randomTrainerHeldItems", "randomTrainerSpecies", "Random Trainer Held Items",
                "Trainer Cobblemon battle holding random items. They don't drop the items on defeat");

        heading("Gym Trainers");
        range("gymBstMin1", "gymBstMax1", null, "Gym 1 Base Stat Range",
                "The base stat total a Gym 1 leader's random Cobblemon are drawn from. Leave both at Any "
                + "to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("gymLevel1", null, "Gym 1 Level",
                "The level a Gym 1 leader's Cobblemon come out at. Default keeps the pack's own levels.",
                "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("gymCount1", null, "Gym 1 Cobblemon Count",
                "How many Cobblemon a Gym 1 leader battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("gymBstMin2", "gymBstMax2", null, "Gym 2 Base Stat Range",
                "The base stat total a Gym 2 leader's random Cobblemon are drawn from. Leave both at Any "
                + "to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("gymLevel2", null, "Gym 2 Level",
                "The level a Gym 2 leader's Cobblemon come out at. Default keeps the pack's own levels.",
                "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("gymCount2", null, "Gym 2 Cobblemon Count",
                "How many Cobblemon a Gym 2 leader battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("gymBstMin3", "gymBstMax3", null, "Gym 3 Base Stat Range",
                "The base stat total a Gym 3 leader's random Cobblemon are drawn from. Leave both at Any "
                + "to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("gymLevel3", null, "Gym 3 Level",
                "The level a Gym 3 leader's Cobblemon come out at. Default keeps the pack's own levels.",
                "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("gymCount3", null, "Gym 3 Cobblemon Count",
                "How many Cobblemon a Gym 3 leader battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("gymBstMin4", "gymBstMax4", null, "Gym 4 Base Stat Range",
                "The base stat total a Gym 4 leader's random Cobblemon are drawn from. Leave both at Any "
                + "to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("gymLevel4", null, "Gym 4 Level",
                "The level a Gym 4 leader's Cobblemon come out at. Default keeps the pack's own levels.",
                "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("gymCount4", null, "Gym 4 Cobblemon Count",
                "How many Cobblemon a Gym 4 leader battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("gymBstMin5", "gymBstMax5", null, "Gym 5 Base Stat Range",
                "The base stat total a Gym 5 leader's random Cobblemon are drawn from. Leave both at Any "
                + "to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("gymLevel5", null, "Gym 5 Level",
                "The level a Gym 5 leader's Cobblemon come out at. Default keeps the pack's own levels.",
                "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("gymCount5", null, "Gym 5 Cobblemon Count",
                "How many Cobblemon a Gym 5 leader battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("gymBstMin6", "gymBstMax6", null, "Gym 6 Base Stat Range",
                "The base stat total a Gym 6 leader's random Cobblemon are drawn from. Leave both at Any "
                + "to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("gymLevel6", null, "Gym 6 Level",
                "The level a Gym 6 leader's Cobblemon come out at. Default keeps the pack's own levels.",
                "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("gymCount6", null, "Gym 6 Cobblemon Count",
                "How many Cobblemon a Gym 6 leader battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("gymBstMin7", "gymBstMax7", null, "Gym 7 Base Stat Range",
                "The base stat total a Gym 7 leader's random Cobblemon are drawn from. Leave both at Any "
                + "to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("gymLevel7", null, "Gym 7 Level",
                "The level a Gym 7 leader's Cobblemon come out at. Default keeps the pack's own levels.",
                "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("gymCount7", null, "Gym 7 Cobblemon Count",
                "How many Cobblemon a Gym 7 leader battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("gymBstMin8", "gymBstMax8", null, "Gym 8 Base Stat Range",
                "The base stat total a Gym 8 leader's random Cobblemon are drawn from. Leave both at Any "
                + "to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("gymLevel8", null, "Gym 8 Level",
                "The level a Gym 8 leader's Cobblemon come out at. Default keeps the pack's own levels.",
                "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("gymCount8", null, "Gym 8 Cobblemon Count",
                "How many Cobblemon a Gym 8 leader battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");


        heading("Elite Four & Champion");
        range("eliteBstMin1", "eliteBstMax1", null, "Elite Four 1 Base Stat Range",
                "The base stat total Elite Four member 1's random Cobblemon are drawn from. Leave both "
                + "at Any to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("eliteLevel1", null, "Elite Four 1 Level",
                "The level Elite Four member 1's Cobblemon come out at. Default keeps the pack's own "
                        + "levels.", "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("eliteCount1", null, "Elite Four 1 Cobblemon Count",
                "How many Cobblemon Elite Four member 1 battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("eliteBstMin2", "eliteBstMax2", null, "Elite Four 2 Base Stat Range",
                "The base stat total Elite Four member 2's random Cobblemon are drawn from. Leave both "
                + "at Any to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("eliteLevel2", null, "Elite Four 2 Level",
                "The level Elite Four member 2's Cobblemon come out at. Default keeps the pack's own "
                        + "levels.", "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("eliteCount2", null, "Elite Four 2 Cobblemon Count",
                "How many Cobblemon Elite Four member 2 battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("eliteBstMin3", "eliteBstMax3", null, "Elite Four 3 Base Stat Range",
                "The base stat total Elite Four member 3's random Cobblemon are drawn from. Leave both "
                + "at Any to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("eliteLevel3", null, "Elite Four 3 Level",
                "The level Elite Four member 3's Cobblemon come out at. Default keeps the pack's own "
                        + "levels.", "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("eliteCount3", null, "Elite Four 3 Cobblemon Count",
                "How many Cobblemon Elite Four member 3 battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("eliteBstMin4", "eliteBstMax4", null, "Elite Four 4 Base Stat Range",
                "The base stat total Elite Four member 4's random Cobblemon are drawn from. Leave both "
                + "at Any to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("eliteLevel4", null, "Elite Four 4 Level",
                "The level Elite Four member 4's Cobblemon come out at. Default keeps the pack's own "
                        + "levels.", "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("eliteCount4", null, "Elite Four 4 Cobblemon Count",
                "How many Cobblemon Elite Four member 4 battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");
        range("champBstMin", "champBstMax", null, "Champion Base Stat Range",
                "The base stat total a region's Champion's random Cobblemon are drawn from. Leave both "
                + "at Any to let them keep whatever the pack gave them.", BST_VALUES, BST_LABELS, "");
        slider("champLevel", null, "Champion Level",
                "The level a Champion's Cobblemon come out at. Default keeps the pack's own levels.",
                "", GYM_LEVELS, GYM_LEVEL_LABELS, "");
        slider("champCount", null, "Champion Cobblemon Count",
                "How many Cobblemon a Champion battles with. Default keeps the pack's own team.",
                "", TEAM_SIZES, TEAM_SIZE_LABELS, "");

        heading("Compatibility");
        toggle("trainerTeras", null, "Trainer Teras",
                "Trainers can Terastallize their Cobblemon");
        toggle("trainerMegas", null, "Trainer Megas",
                "Trainers can Mega Evolve their Cobblemon");
        toggle("trainerDyna", null, "Trainer Dynamax",
                "Trainers can Dynamax their Cobblemon");
    }
}
