package com.cobblelocke.command;

import com.cobblelocke.compat.GymLeaders;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.config.ConfigFiles;
import com.cobblelocke.eventlock.EncounterAnimations;
import com.cobblelocke.util.PartyLevels;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StatusReport {
    private static final String[] LEVEL_MODES = {"random up to your best", "close to your best",
            "1-100", "matching your best"};

    private final List<String> lines = new ArrayList<>();

    private StatusReport() {
    }

    public static List<String> of(CobblelockeConfig config, boolean askOnFirstJoin) {
        StatusReport report = new StatusReport();
        report.build(config, askOnFirstJoin);
        return report.lines;
    }

    private void build(CobblelockeConfig config, boolean askOnFirstJoin) {
        if (askOnFirstJoin) {
            line("Asks for rules on a new world");
        }
        if (!"any".equalsIgnoreCase(config.serverConfigMode)) {
            line("Commands: §f" + config.serverConfigMode + " §7for non-operators");
        }

        if (config.nuzlockeModeEnabled) {
            List<String> rules = new ArrayList<>();
            if (config.oneCatchPerBiome) {
                rules.add("one catch per biome");
            }
            if (config.oneCatchPerRegion > 0) {
                rules.add("one catch per " + config.oneCatchPerRegion + " block region");
            }
            if (config.noDuplicates) {
                rules.add("no duplicates");
            }
            if (config.noHealing) {
                rules.add("permadeath");
            }
            if (config.shinyClause) {
                rules.add("shiny clause");
            }
            if (config.catchCooldownSeconds > 0) {
                rules.add(config.catchCooldownSeconds + "s catch cooldown");
            }
            line("Nuzlocke: §f" + join(rules, "on"));
        }

        if (config.firstCatchEventLocked) {
            line("Event lock: §flevels " + levelMode(config.eventLevelMode)
                    + "§7, animations §f" + config.animationPreset);
            for (EncounterAnimations.Environment where : EncounterAnimations.Environment.values()) {
                String clip = EncounterAnimations.clipFor(where, config);
                String timing = EncounterAnimations.isChoice(clip)
                        ? "picked per encounter"
                        : EncounterAnimations.battleAtMs(clip) + "ms";
                line("  " + where.name().toLowerCase(Locale.ROOT) + ": §f" + clip + " §8(" + timing + ")");
            }
        }

        if (config.randomSpawns) {
            List<String> wild = new ArrayList<>();
            if (config.spawnsLegendaries) {
                wild.add(config.legendariesAreTrulyRandom()
                        ? "legendaries at no special rarity"
                        : "legendaries 1 in " + config.legendaryRarity);
            }
            wild.add(shared("types", config.randomWildTypes, config.globalWildTypes,
                    config.randomStarterTypes, config.globalStarterTypes));
            wild.add(shared("abilities", config.randomWildAbilities, config.globalWildAbilities,
                    config.randomStarterAbilities, config.globalStarterAbilities));
            wild.add(shared("moves", config.randomWildMoves, config.globalWildMoves,
                    config.randomStarterMoves, config.globalStarterMoves));
            wild.removeIf(String::isEmpty);
            line("Random spawns: §f" + join(wild, "on"));
        }
        if (config.wild(config.legendariesReplaceLegendaries)) {
            line("Legendaries: §fonly ever replaced by other legendaries");
        }
        if (config.smoothSpawning) {
            line("Smooth spawning: §fno group spawns");
        }
        if (config.randomStarters) {
            List<String> starters = new ArrayList<>();
            starters.add("all".equals(config.starterRegion) ? "every region" : config.starterRegion);
            if (config.starterTrios() > 1) {
                starters.add(config.starterTrios() + " trios offered");
            }
            line("Random starters: §f" + String.join(", ", starters));
        }
        if (config.randomEvolutions) {
            line("Random evolutions: §fon");
        }
        if (config.randomTmMoves) {
            line("Random TM tables: §fon");
        }
        if (config.overrideShinyRate) {
            line("Shiny rate: §f1 in " + config.shinyRate);
        }
        if (config.heldItemWhitelist != null && !config.heldItemWhitelist.isEmpty()) {
            line("Held items from: §f" + String.join(", ", config.heldItemWhitelist));
        }
        if (config.heldItemBlacklist != null && !config.heldItemBlacklist.isEmpty()) {
            line("Held items never: §f" + String.join(", ", config.heldItemBlacklist));
        }

        List<String> compat = new ArrayList<>();
        if (config.disableRaidCatch) {
            compat.add("no raid catches");
        }
        if (config.randomizeTeras) {
            compat.add("wild teras");
        }
        if (config.randomizeMegas) {
            compat.add("wild megas");
        }
        if (config.starterTeras) {
            compat.add("starter teras");
        }
        if (config.starterMegas) {
            compat.add("starter megas");
        }
        if (config.starterDyna) {
            compat.add("starter dynamax");
        }
        if (!compat.isEmpty()) {
            line("Compatibility: §f" + String.join(", ", compat));
        }

        trainers(config);
    }

    private void trainers(CobblelockeConfig config) {
        List<String> npc = new ArrayList<>();
        if (config.randomTrainerSpecies) {
            npc.add("random species");
            String band = band(config.trainerMinBst, config.trainerMaxBst);
            if (!band.isEmpty()) {
                npc.add("stats " + band);
            }
            if (config.trainerLevelMode != PartyLevels.RANDOM_UP_TO_HIGHEST) {
                npc.add("levels " + levelMode(config.trainerLevelMode));
            }
            if (config.randomTrainerTypes) {
                npc.add(config.globalTrainerTypes ? "global types" : "random types");
            }
            if (config.randomTrainerAbilities) {
                npc.add(config.globalTrainerAbilities ? "global abilities" : "random abilities");
            }
            if (config.randomTrainerMoves) {
                npc.add(config.globalTrainerMoves ? "global moves" : "random moves");
            }
            if (config.randomTrainerHeldItems) {
                npc.add("random items");
            }
        }
        if (config.trainerTeamSize > 0) {
            npc.add(config.trainerTeamSize + " per team");
        }
        if (config.doubleBattle == 1) {
            npc.add("doubles of " + config.doubleBattleTeamSize);
        } else if (config.doubleBattle == 2) {
            npc.add("singles or doubles of " + config.doubleBattleTeamSize + ", at random");
        }
        if (config.trainerTeras) {
            npc.add("teras");
        }
        if (config.trainerMegas) {
            npc.add("megas");
        }
        if (config.trainerDyna) {
            npc.add("dynamax");
        }
        if (!npc.isEmpty()) {
            line("Trainers: §f" + String.join(", ", npc));
        }

        int leaders = GymLeaders.known();
        if (leaders > 0) {
            line("League trainers recognised: §f" + GymLeaders.known(GymLeaders.Role.GYM)
                    + " gym§7, §f" + GymLeaders.known(GymLeaders.Role.ELITE)
                    + " elite four§7, §f" + GymLeaders.known(GymLeaders.Role.CHAMPION) + " champion");
        }
        for (int gym = 1; gym <= GymLeaders.GYMS_PER_REGION; gym++) {
            slot(config, new GymLeaders.Slot(GymLeaders.Role.GYM, gym), "Gym " + gym);
        }
        for (int elite = 1; elite <= GymLeaders.ELITES_PER_REGION; elite++) {
            slot(config, new GymLeaders.Slot(GymLeaders.Role.ELITE, elite), "Elite Four " + elite);
        }
        slot(config, new GymLeaders.Slot(GymLeaders.Role.CHAMPION, 1), "Champion");
    }

    private void slot(CobblelockeConfig config, GymLeaders.Slot slot, String label) {
        List<String> parts = new ArrayList<>();
        String band = band(config.leagueSetting("BstMin", slot), config.leagueSetting("BstMax", slot));
        if (!band.isEmpty()) {
            parts.add("stats " + band);
        }
        int level = config.leagueSetting("Level", slot);
        if (level > 0) {
            parts.add("level " + level);
        }
        int count = config.leagueSetting("Count", slot);
        if (count > 0) {
            parts.add(count + " Cobblemon");
        }
        if (!parts.isEmpty()) {
            line(label + ": §f" + String.join(", ", parts));
        }
    }

    private static String band(int min, int max) {
        if (min > 0 && max > 0) {
            return min + "-" + max;
        }
        if (min > 0) {
            return "at least " + min;
        }
        return max > 0 ? "up to " + max : "";
    }

    private static String shared(String what, boolean wild, boolean wildGlobal,
                                 boolean starters, boolean startersGlobal) {
        if (!wild && !starters) {
            return "";
        }
        List<String> who = new ArrayList<>();
        if (wild) {
            who.add(wildGlobal ? "wild (global)" : "wild");
        }
        if (starters) {
            who.add(startersGlobal ? "starters (global)" : "starters");
        }
        return "random " + what + " for " + String.join(" and ", who);
    }

    private static String levelMode(int mode) {
        return mode >= 0 && mode < LEVEL_MODES.length ? LEVEL_MODES[mode] : "?";
    }

    private static String join(List<String> parts, String whenEmpty) {
        return parts.isEmpty() ? whenEmpty : String.join(", ", parts);
    }

    private void line(String text) {
        lines.add("§7" + text);
    }

    public static boolean asksOnFirstJoin() {
        try {
            return ConfigFiles.loadConfig().askOnFirstJoin();
        } catch (Exception e) {
            return false;
        }
    }
}
