package com.cobblelocke.command;

import com.cobblelocke.compat.GymLeaders;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.config.ConfigFiles;
import com.cobblelocke.eventlock.EncounterAnimations;
import com.cobblelocke.util.Lang;
import com.cobblelocke.util.PartyLevels;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StatusReport {
    private final List<Text> lines = new ArrayList<>();

    private StatusReport() {
    }

    public static List<Text> of(CobblelockeConfig config, boolean askOnFirstJoin) {
        StatusReport report = new StatusReport();
        report.build(config, askOnFirstJoin);
        return report.lines;
    }

    private void build(CobblelockeConfig config, boolean askOnFirstJoin) {
        if (config.configVersion < CobblelockeConfig.CURRENT_VERSION) {
            lines.add(Lang.tr("status.version", "§7Rules version: %1$s §8(made with an older version, so its "
                    + "rules play as they did then; Start Run! moves it to version %2$s)",
                    Lang.hl(config.configVersion, Formatting.WHITE), CobblelockeConfig.CURRENT_VERSION));
        }
        if (askOnFirstJoin) {
            lines.add(Lang.tr("status.asks", "§7Asks for rules on a new world"));
        }
        if (!"any".equalsIgnoreCase(config.serverConfigMode)) {
            lines.add(Lang.tr("status.commands", "§7Commands: %s §7for non-operators",
                    Lang.hl(config.serverConfigMode, Formatting.WHITE)));
        }

        if (config.nuzlockeModeEnabled) {
            List<Text> rules = new ArrayList<>();
            if (config.oneCatchPerBiome) {
                rules.add(Lang.tr("status.rule.biome", "one catch per biome"));
            }
            if (config.oneCatchPerRegion > 0) {
                rules.add(Lang.tr("status.rule.region", "one catch per %s block region", config.oneCatchPerRegion));
            }
            if (config.noDuplicates) {
                rules.add(Lang.tr("status.rule.duplicates", "no duplicates"));
            }
            if (config.noHealing) {
                rules.add(Lang.tr("status.rule.permadeath", "permadeath"));
            }
            if (config.shinyClause) {
                rules.add(Lang.tr("status.rule.shiny", "shiny clause"));
            }
            if (config.catchCooldownSeconds > 0) {
                rules.add(Lang.tr("status.rule.cooldown", "%ss catch cooldown", config.catchCooldownSeconds));
            }
            if (config.capSpawningPerRegionChunks > 0) {
                rules.add(Lang.tr("status.rule.spawncap", "%1$s spawn(s) per %2$s chunk region%3$s%4$s",
                        config.capSpawningPerRegionCount, config.capSpawningPerRegionChunks,
                        config.capSpawningPerRegionByPlayer ? Lang.tr("status.rule.per_player", " per player") : "",
                        config.spawnCapAlwaysOn ? Lang.tr("status.rule.always_on", " (always on)") : ""));
            }
            line("status.nuzlocke", "§7Nuzlocke: %s", rules);
        }

        if (config.firstCatchEventLocked) {
            lines.add(Lang.tr("status.event", "§7Event lock: levels %1$s§7, animations %2$s",
                    Lang.hl(levelMode(config.eventLevelMode), Formatting.WHITE),
                    Lang.hl(config.animationPreset, Formatting.WHITE)));
            for (EncounterAnimations.Environment where : EncounterAnimations.Environment.values()) {
                String clip = EncounterAnimations.clipFor(where, config);
                Text timing = EncounterAnimations.isChoice(clip)
                        ? Lang.tr("status.event.per_encounter", "picked per encounter")
                        : Text.literal(EncounterAnimations.battleAtMs(clip) + "ms");
                String name = where.name().toLowerCase(Locale.ROOT);
                lines.add(Lang.tr("status.event.clip", "§7  %1$s: %2$s §8(%3$s)",
                        Lang.tr("status.env." + name, name), Lang.hl(clip, Formatting.WHITE),
                        Lang.hl(timing, Formatting.DARK_GRAY)));
            }
        }

        if (config.randomSpawns) {
            List<Text> wild = new ArrayList<>();
            if (config.spawnsLegendaries) {
                wild.add(config.legendariesAreTrulyRandom()
                        ? Lang.tr("status.wild.legendaries_random", "legendaries at no special rarity")
                        : Lang.tr("status.wild.legendaries", "legendaries 1 in %s", config.legendaryRarity));
            }
            addShared(wild, "types", "types", config.randomWildTypes, config.globalWildTypes,
                    config.randomStarterTypes, config.globalStarterTypes);
            addShared(wild, "abilities", "abilities", config.randomWildAbilities, config.globalWildAbilities,
                    config.randomStarterAbilities, config.globalStarterAbilities);
            addShared(wild, "moves", "moves", config.randomWildMoves, config.globalWildMoves,
                    config.randomStarterMoves, config.globalStarterMoves);
            line("status.wild", "§7Random spawns: %s", wild);
        }
        if (config.wild(config.legendariesReplaceLegendaries)) {
            lines.add(Lang.tr("status.legendaries", "§7Legendaries: §fonly ever replaced by other legendaries"));
        }
        if (config.smoothSpawning) {
            lines.add(Lang.tr("status.smooth", "§7Smooth spawning: §fno group spawns"));
        }
        if (config.randomStarters) {
            List<Text> starters = new ArrayList<>();
            starters.add("all".equals(config.starterRegion)
                    ? Lang.tr("status.starters.every_region", "every region")
                    : Text.literal(capitalise(config.starterRegion)));
            if (config.starterTrios() > 1) {
                starters.add(Lang.tr("status.starters.trios", "%s trios offered", config.starterTrios()));
            }
            line("status.starters", "§7Random starters: %s", starters);
        }
        if (config.randomEvolutions) {
            lines.add(Lang.tr("status.evolutions", "§7Random evolutions: §fon"));
        }
        if (config.randomTmMoves) {
            lines.add(Lang.tr("status.tms", "§7Random TM tables: §fon"));
        }
        if (config.overrideShinyRate) {
            lines.add(Lang.tr("status.shiny_rate", "§7Shiny rate: §f1 in %s", config.shinyRate));
        }
        if (config.heldItemWhitelist != null && !config.heldItemWhitelist.isEmpty()) {
            lines.add(Lang.tr("status.items_from", "§7Held items from: %s",
                    Lang.hl(String.join(", ", config.heldItemWhitelist), Formatting.WHITE)));
        }
        if (config.heldItemBlacklist != null && !config.heldItemBlacklist.isEmpty()) {
            lines.add(Lang.tr("status.items_never", "§7Held items never: %s",
                    Lang.hl(String.join(", ", config.heldItemBlacklist), Formatting.WHITE)));
        }

        List<Text> compat = new ArrayList<>();
        if (config.disableRaidCatch) {
            compat.add(Lang.tr("status.compat.raids", "no raid catches"));
        }
        if (config.randomizeTeras) {
            compat.add(Lang.tr("status.compat.wild_teras", "wild teras"));
        }
        if (config.randomizeMegas) {
            compat.add(Lang.tr("status.compat.wild_megas", "wild megas"));
        }
        if (config.starterTeras) {
            compat.add(Lang.tr("status.compat.starter_teras", "starter teras"));
        }
        if (config.starterMegas) {
            compat.add(Lang.tr("status.compat.starter_megas", "starter megas"));
        }
        if (config.starterDyna) {
            compat.add(Lang.tr("status.compat.starter_dynamax", "starter dynamax"));
        }
        if (!compat.isEmpty()) {
            line("status.compat", "§7Compatibility: %s", compat);
        }

        trainers(config);
    }

    private void trainers(CobblelockeConfig config) {
        List<Text> npc = new ArrayList<>();
        if (config.randomTrainerSpecies) {
            npc.add(Lang.tr("status.trainers.species", "random species"));
            Text band = band(config.trainerMinBst, config.trainerMaxBst);
            if (band != null) {
                npc.add(Lang.tr("status.trainers.stats", "stats %s", band));
            }
            if (config.trainerLevelMode != PartyLevels.RANDOM_UP_TO_HIGHEST) {
                npc.add(Lang.tr("status.trainers.levels", "levels %s", levelMode(config.trainerLevelMode)));
            }
            if (config.randomTrainerTypes) {
                npc.add(config.globalTrainerTypes
                        ? Lang.tr("status.trainers.global_types", "global types")
                        : Lang.tr("status.trainers.random_types", "random types"));
            }
            if (config.randomTrainerAbilities) {
                npc.add(config.globalTrainerAbilities
                        ? Lang.tr("status.trainers.global_abilities", "global abilities")
                        : Lang.tr("status.trainers.random_abilities", "random abilities"));
            }
            if (config.randomTrainerMoves) {
                npc.add(config.globalTrainerMoves
                        ? Lang.tr("status.trainers.global_moves", "global moves")
                        : Lang.tr("status.trainers.random_moves", "random moves"));
            }
            if (config.randomTrainerHeldItems) {
                npc.add(Lang.tr("status.trainers.items", "random items"));
            }
        }
        if (config.trainerTeamSize > 0) {
            npc.add(Lang.tr("status.trainers.team", "%s per team", config.trainerTeamSize));
        }
        if (config.doubleBattle == 1) {
            npc.add(Lang.tr("status.trainers.doubles", "doubles of %s", config.doubleBattleTeamSize));
        } else if (config.doubleBattle == 2) {
            npc.add(Lang.tr("status.trainers.doubles_random", "singles or doubles of %s, at random",
                    config.doubleBattleTeamSize));
        }
        if (config.trainerTeras) {
            npc.add(Lang.tr("status.trainers.teras", "teras"));
        }
        if (config.trainerMegas) {
            npc.add(Lang.tr("status.trainers.megas", "megas"));
        }
        if (config.trainerDyna) {
            npc.add(Lang.tr("status.trainers.dynamax", "dynamax"));
        }
        if (!npc.isEmpty()) {
            line("status.trainers", "§7Trainers: %s", npc);
        }

        if (GymLeaders.known() > 0) {
            lines.add(Lang.tr("status.league", "§7League trainers recognised: %1$s§7 gym, %2$s§7 elite four, "
                            + "%3$s§7 champion",
                    Lang.hl(GymLeaders.known(GymLeaders.Role.GYM), Formatting.WHITE),
                    Lang.hl(GymLeaders.known(GymLeaders.Role.ELITE), Formatting.WHITE),
                    Lang.hl(GymLeaders.known(GymLeaders.Role.CHAMPION), Formatting.WHITE)));
        }
        for (int gym = 1; gym <= GymLeaders.GYMS_PER_REGION; gym++) {
            slot(config, new GymLeaders.Slot(GymLeaders.Role.GYM, gym), Lang.tr("status.gym", "Gym %s", gym));
        }
        for (int elite = 1; elite <= GymLeaders.ELITES_PER_REGION; elite++) {
            slot(config, new GymLeaders.Slot(GymLeaders.Role.ELITE, elite),
                    Lang.tr("status.elite", "Elite Four %s", elite));
        }
        slot(config, new GymLeaders.Slot(GymLeaders.Role.CHAMPION, 1), Lang.tr("status.champion", "Champion"));
    }

    private void slot(CobblelockeConfig config, GymLeaders.Slot slot, Text label) {
        List<Text> parts = new ArrayList<>();
        Text band = band(config.leagueSetting("BstMin", slot), config.leagueSetting("BstMax", slot));
        if (band != null) {
            parts.add(Lang.tr("status.trainers.stats", "stats %s", band));
        }
        int level = config.leagueSetting("Level", slot);
        if (level > 0) {
            parts.add(Lang.tr("status.slot.level", "level %s", level));
        }
        int count = config.leagueSetting("Count", slot);
        if (count > 0) {
            parts.add(Lang.tr("status.slot.count", "%s Cobblemon", count));
        }
        if (!parts.isEmpty()) {
            lines.add(Lang.tr("status.slot", "§7%1$s: %2$s", label, Lang.hl(Lang.join(parts), Formatting.WHITE)));
        }
    }

    private static Text band(int min, int max) {
        if (min > 0 && max > 0) {
            return Text.literal(min + "-" + max);
        }
        if (min > 0) {
            return Lang.tr("status.band.at_least", "at least %s", min);
        }
        return max > 0 ? Lang.tr("status.band.up_to", "up to %s", max) : null;
    }

    private static void addShared(List<Text> into, String key, String what, boolean wild, boolean wildGlobal,
                                  boolean starters, boolean startersGlobal) {
        if (!wild && !starters) {
            return;
        }
        List<Text> who = new ArrayList<>();
        if (wild) {
            who.add(wildGlobal
                    ? Lang.tr("status.who.wild_global", "wild (global)")
                    : Lang.tr("status.who.wild", "wild"));
        }
        if (starters) {
            who.add(startersGlobal
                    ? Lang.tr("status.who.starters_global", "starters (global)")
                    : Lang.tr("status.who.starters", "starters"));
        }
        into.add(Lang.tr("status.random_for", "random %1$s for %2$s", Lang.tr("status.what." + key, what),
                Lang.join(who, Lang.tr("status.and", " and "))));
    }

    private static MutableText levelMode(int mode) {
        return switch (mode) {
            case 0 -> Lang.tr("status.level.default", "random up to your best");
            case 1 -> Lang.tr("status.level.close", "close to your best");
            case 2 -> Lang.tr("status.level.random", "1-100");
            case 3 -> Lang.tr("status.level.match", "matching your best");
            default -> Text.literal("?");
        };
    }

    private static String capitalise(String value) {
        return value == null || value.isEmpty() ? "" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void line(String key, String english, List<Text> parts) {
        Text joined = parts.isEmpty() ? Lang.tr("status.on", "on") : Lang.join(parts);
        lines.add(Lang.tr(key, english, Lang.hl(joined, Formatting.WHITE)));
    }

    public static boolean asksOnFirstJoin() {
        try {
            return ConfigFiles.loadConfig().askOnFirstJoin();
        } catch (Exception e) {
            return false;
        }
    }
}
