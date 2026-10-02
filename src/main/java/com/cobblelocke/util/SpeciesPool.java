package com.cobblelocke.util;

import com.cobblelocke.Cobblelocke;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class SpeciesPool {
    public static final List<String> REGIONS = List.of(
            "all", "kanto", "johto", "hoenn", "sinnoh", "unova", "kalos", "alola", "galar", "hisui", "paldea");

    private static final Set<String> RESTRICTED_LABELS = Set.of(
            "legendary", "mythical", "ultra_beast", "paradox", "restricted");

    private static final Set<String> GIMMICK_LABELS = Set.of(
            "mega", "gmax", "gigantamax", "primal", "eternamax", "ultra_burst", "totem");

    private static final String[] GIMMICK_AFFIXES = {
            "mega", "megax", "megay", "gmax", "gigantamax", "primal", "eternamax", "ultra"};

    private static volatile Set<String> cachedNames = null;

    private static volatile List<Species> cachedSpecies = null;
    private static volatile List<String> cachedTypeNames = null;
    private static volatile List<String> cachedMoveNames = null;
    private static volatile List<String> cachedAbilityNames = null;

    private SpeciesPool() {
    }

    public static void invalidate() {
        cachedSpecies = null;
        cachedTypeNames = null;
        cachedMoveNames = null;
        cachedAbilityNames = null;
        cachedTmMoveNames = null;
        cachedNames = null;
        EVOLVES_TWICE.clear();
    }

    public static List<Species> all() {
        List<Species> cached = cachedSpecies;
        if (cached != null) {
            return cached;
        }
        List<Species> built = new ArrayList<>();
        try {
            built.addAll(PokemonSpecies.getSpecies());
        } catch (Exception e) {
            Cobblelocke.LOGGER.error("Could not read the species registry: {}", e.toString());
        }
        cachedSpecies = built;
        return built;
    }

    public static List<Species> battleSafe(boolean excludeLegendaries) {
        List<Species> out = new ArrayList<>();
        for (Species species : all()) {
            String name = species.getName();
            if (name == null || name.contains(".") || name.contains("_")) {
                continue;
            }
            if (species.getNationalPokedexNumber() <= 0) {
                continue;
            }
            if (excludeLegendaries && isRestricted(species)) {
                continue;
            }
            if (isGimmickForm(species)) {
                continue;
            }
            out.add(species);
        }
        return out;
    }

    public static Species rollWild(Random random, com.cobblelocke.config.CobblelockeConfig config) {
        if (!config.spawnsLegendaries) {
            return randomBattleSafe(random, true);
        }
        if (config.legendariesAreTrulyRandom()) {
            return randomBattleSafe(random, false);
        }
        int rarity = Math.max(1, config.legendaryRarity);
        if (random.nextInt(rarity) == 0) {
            List<Species> legendaries = new ArrayList<>();
            for (Species species : battleSafe(false)) {
                if (isRestricted(species)) {
                    legendaries.add(species);
                }
            }
            if (!legendaries.isEmpty()) {
                return legendaries.get(random.nextInt(legendaries.size()));
            }
        }
        return randomBattleSafe(random, true);
    }

    public static Species randomLegendary(Random random) {
        List<Species> legendaries = new ArrayList<>();
        for (Species species : battleSafe(false)) {
            if (isRestricted(species)) {
                legendaries.add(species);
            }
        }
        if (legendaries.isEmpty()) {
            return null;
        }
        return legendaries.get(random.nextInt(legendaries.size()));
    }

    public static Species randomInBstBand(Random random, int minBst, int maxBst) {
        List<Species> pool = new ArrayList<>();
        for (Species species : battleSafe(false)) {
            int total = baseStatTotal(species);
            if ((minBst <= 0 || total >= minBst) && (maxBst <= 0 || total <= maxBst)) {
                pool.add(species);
            }
        }
        if (pool.isEmpty()) {
            return randomBattleSafe(random, false);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private static volatile List<String> cachedTmMoveNames = null;

    public static List<String> tmMoveNames() {
        List<String> cached = cachedTmMoveNames;
        if (cached != null) {
            return cached;
        }
        List<String> names = new ArrayList<>();
        try {
            for (com.cobblemon.mod.common.api.moves.MoveTemplate move
                    : com.cobblemon.mod.common.api.tms.TechnicalMachines.INSTANCE.getMoveToTM().keySet()) {
                if (move != null && move.getName() != null) {
                    names.add(move.getName());
                }
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not read the TM registry: {}", e.toString());
        }
        if (names.isEmpty()) {
            names = new ArrayList<>(moveNames());
        }
        cachedTmMoveNames = names;
        return names;
    }

    public static Species randomBattleSafe(Random random, boolean excludeLegendaries) {
        List<Species> pool = battleSafe(excludeLegendaries);
        if (pool.isEmpty()) {
            pool = all();
        }
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    public static List<Species> byRegion(String region) {
        if (region == null || region.isBlank() || "all".equalsIgnoreCase(region)) {
            return all();
        }
        List<String> names = RegionalDex.speciesOf(region.toLowerCase(Locale.ROOT));
        if (names.isEmpty()) {
            return all();
        }
        List<Species> out = new ArrayList<>(names.size());
        for (String name : names) {
            Species species = PokemonSpecies.getByName(name);
            if (species != null) {
                out.add(species);
            }
        }
        return out.isEmpty() ? all() : out;
    }

    public static FormData regionalForm(Species species, String region) {
        String normalized = normalizeRegion(region);
        if (species == null || normalized == null) {
            return null;
        }
        String label = switch (normalized) {
            case "alola" -> "alolan_form";
            case "galar" -> "galarian_form";
            case "paldea" -> "paldean_form";
            case "hisui" -> "hisuian_form";
            default -> "";
        };
        for (FormData form : species.getForms()) {
            Set<String> labels = form.getLabels();
            if (labels != null) {
                for (String candidate : labels) {
                    if (label.equalsIgnoreCase(candidate)) {
                        return form;
                    }
                }
            }
            for (String aspect : form.getAspects()) {
                if (aspect.toLowerCase(Locale.ROOT).contains(normalized)) {
                    return form;
                }
            }
        }
        return null;
    }

    public static Set<String> regionalAspects(Species species, String region) {
        FormData form = regionalForm(species, region);
        return form == null ? Collections.emptySet() : new HashSet<>(form.getAspects());
    }

    public static String normalizeRegion(String category) {
        if (category == null) {
            return null;
        }
        String key = category.toLowerCase(Locale.ROOT);
        if (key.contains("alola")) return "alola";
        if (key.contains("galar")) return "galar";
        if (key.contains("paldea")) return "paldea";
        if (key.contains("hisui")) return "hisui";
        return null;
    }

    public static boolean isRestricted(Species species) {
        try {
            Set<String> labels = new HashSet<>();
            if (species.getLabels() != null) {
                for (String label : species.getLabels()) {
                    labels.add(label.toLowerCase(Locale.ROOT));
                }
            }
            FormData standard = species.getStandardForm();
            if (standard != null && standard.getLabels() != null) {
                for (String label : standard.getLabels()) {
                    labels.add(label.toLowerCase(Locale.ROOT));
                }
            }
            for (String label : labels) {
                for (String restricted : RESTRICTED_LABELS) {
                    if (label.contains(restricted)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return isRestrictedDexNumber(species.getNationalPokedexNumber());
    }

    private static boolean isRestrictedDexNumber(int dex) {
        return (dex >= 144 && dex <= 151)
                || (dex >= 243 && dex <= 251)
                || (dex >= 377 && dex <= 386)
                || (dex >= 480 && dex <= 493)
                || (dex >= 638 && dex <= 649)
                || (dex >= 716 && dex <= 721)
                || (dex >= 785 && dex <= 809)
                || (dex >= 888 && dex <= 898)
                || (dex >= 984 && dex <= 1025);
    }

    public static boolean isGimmickForm(Species species) {
        if (species == null) {
            return false;
        }
        try {
            if (hasGimmickLabel(species.getLabels())) {
                return true;
            }
            FormData standard = species.getStandardForm();
            if (standard != null && (hasGimmickLabel(standard.getLabels())
                    || hasGimmickLabel(standard.getAspects()))) {
                return true;
            }
            return wrapsAnotherSpecies(species.getName());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasGimmickLabel(Iterable<String> labels) {
        if (labels == null) {
            return false;
        }
        for (String label : labels) {
            if (label == null) {
                continue;
            }
            String key = label.toLowerCase(Locale.ROOT).replace('-', '_');
            if (GIMMICK_LABELS.contains(key) || key.startsWith("mega_") || key.startsWith("gmax_")) {
                return true;
            }
        }
        return false;
    }

    private static boolean wrapsAnotherSpecies(String rawName) {
        if (rawName == null) {
            return false;
        }
        String name = plainName(rawName);
        for (String affix : GIMMICK_AFFIXES) {
            if (name.length() <= affix.length()) {
                continue;
            }
            if (name.startsWith(affix) && namesASpecies(name.substring(affix.length()))) {
                return true;
            }
            if (name.endsWith(affix) && namesASpecies(name.substring(0, name.length() - affix.length()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean namesASpecies(String candidate) {
        if (candidate.length() < 3) {
            return false;
        }
        Set<String> names = names();
        if (names.contains(candidate)) {
            return true;
        }
        char last = candidate.charAt(candidate.length() - 1);
        return (last == 'x' || last == 'y')
                && names.contains(candidate.substring(0, candidate.length() - 1));
    }

    private static Set<String> names() {
        Set<String> cached = cachedNames;
        if (cached != null) {
            return cached;
        }
        Set<String> built = new HashSet<>();
        for (Species species : all()) {
            String name = species.getName();
            if (name != null) {
                built.add(plainName(name));
            }
        }
        cachedNames = built;
        return built;
    }

    private static String plainName(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (char letter : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(letter)) {
                out.append(letter);
            }
        }
        return out.toString();
    }

    public static boolean isBasic(Species species) {
        try {
            return species.getPreEvolution() == null;
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean evolvesTwice(Species species) {
        if (species == null || !isBasic(species)) {
            return false;
        }
        String key = species.getName();
        Boolean cached = EVOLVES_TWICE.get(key);
        if (cached != null) {
            return cached;
        }
        boolean result = false;
        for (Species next : nextStages(species)) {
            if (!nextStages(next).isEmpty()) {
                result = true;
                break;
            }
        }
        EVOLVES_TWICE.put(key, result);
        return result;
    }

    private static final java.util.Map<String, Boolean> EVOLVES_TWICE = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean inThreeStageLine(Species species) {
        Species base = species;
        for (int step = 0; step < 4 && base != null; step++) {
            if (evolvesTwice(base)) {
                return true;
            }
            PreEvolution pre;
            try {
                pre = base.getPreEvolution();
            } catch (Exception e) {
                return false;
            }
            if (pre == null) {
                return false;
            }
            base = pre.getSpecies();
        }
        return false;
    }

    private static List<Species> nextStages(Species species) {
        List<Species> out = new ArrayList<>();
        try {
            Set<com.cobblemon.mod.common.api.pokemon.evolution.Evolution> evolutions = new HashSet<>(species.getEvolutions());
            for (FormData form : species.getForms()) {
                evolutions.addAll(form.getEvolutions());
            }
            for (com.cobblemon.mod.common.api.pokemon.evolution.Evolution evolution : evolutions) {
                String name = evolution.getResult() == null ? null : evolution.getResult().getSpecies();
                if (name == null || name.isBlank()) {
                    continue;
                }

                String bare = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
                Species next = PokemonSpecies.getByName(bare.toLowerCase(Locale.ROOT));
                if (next != null && !next.getName().equals(species.getName())) {
                    out.add(next);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean isFullyEvolved(Species species) {
        try {
            Set<?> evolutions = species.getEvolutions();
            return evolutions == null || evolutions.isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    public static int evolutionStage(Species species) {
        try {
            PreEvolution pre = species.getPreEvolution();
            if (pre == null) {
                return 0;
            }
            Species preSpecies = pre.getSpecies();
            return (preSpecies != null && preSpecies.getPreEvolution() != null) ? 2 : 1;
        } catch (Exception e) {
            return 0;
        }
    }

    public static int baseStatTotal(Species species) {
        try {
            int total = 0;
            for (Integer stat : species.getBaseStats().values()) {
                if (stat != null) {
                    total += stat;
                }
            }
            return total;
        } catch (Exception e) {
            return 300;
        }
    }

    public static List<String> typeNames() {
        List<String> cached = cachedTypeNames;
        if (cached != null) {
            return cached;
        }
        List<String> names = new ArrayList<>();
        try {
            for (ElementalType type : ElementalTypes.all()) {
                String name = type.getName();
                if (name != null && !name.isBlank()) {
                    names.add(name.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not read the type registry: {}", e.toString());
        }
        if (names.isEmpty()) {
            names = new ArrayList<>(List.of("normal", "fire", "water", "electric", "grass", "ice",
                    "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost",
                    "dragon", "dark", "steel", "fairy"));
        }
        cachedTypeNames = names;
        return names;
    }

    public static List<String> moveNames() {
        List<String> cached = cachedMoveNames;
        if (cached != null) {
            return cached;
        }
        final List<String> collected = new ArrayList<>();
        try {
            Moves.all().forEach(template -> {
                String name = template.getName();
                if (name == null || name.isBlank() || name.contains(".") || name.contains("_")) {
                    return;
                }

                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith("max") || lower.startsWith("gmax") || template.getPp() <= 1) {
                    return;
                }
                collected.add(name);
            });
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not read the move registry: {}", e.toString());
        }
        List<String> names = collected.isEmpty()
                ? new ArrayList<>(List.of("tackle", "growl", "scratch", "leer"))
                : collected;
        cachedMoveNames = names;
        return names;
    }

    public static List<String> abilityNames() {
        List<String> cached = cachedAbilityNames;
        if (cached != null) {
            return cached;
        }
        final List<String> collected = new ArrayList<>();
        try {
            Abilities.all().forEach(template -> {
                String name = template.getName();
                if (name == null || name.isBlank() || name.equalsIgnoreCase("dummy")) {
                    return;
                }
                if (name.contains(".") || name.contains("_")) {
                    return;
                }
                collected.add(name);
            });
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not read the ability registry: {}", e.toString());
        }
        List<String> names = collected.isEmpty() ? new ArrayList<>(List.of("overgrow")) : collected;
        cachedAbilityNames = names;
        return names;
    }

    public static String[] originalTypes(FormData form) {
        try {
            ElementalType primary = form.getPrimaryType();
            if (primary == null) {
                return new String[]{"normal"};
            }
            ElementalType secondary = form.getSecondaryType();
            if (secondary != null) {
                return new String[]{primary.getName(), secondary.getName()};
            }
            return new String[]{primary.getName()};
        } catch (Exception e) {
            return new String[]{"normal"};
        }
    }
}
