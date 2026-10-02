package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.data.CobblelockeState;
import com.cobblelocke.util.SpeciesPool;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.config.starter.StarterCategory;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class StarterRandomizer {
    private static final long SALT = 0x53544152_544552L;

    private static final String[] TRIO_TYPES = {"grass", "fire", "water"};

    private StarterRandomizer() {
    }

    public static List<StarterCategory> randomize(ServerPlayerEntity player,
                                                  List<StarterCategory> original) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null || player == null || original == null || original.isEmpty()) {
            return null;
        }
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive || !config.randomStarters) {
            return null;
        }

        List<Species> pool = candidatePool(config);
        if (pool.isEmpty()) {
            Cobblelocke.LOGGER.warn("No species match the starter filters; leaving starters alone");
            return null;
        }

        int trios = config.starterTrios();
        List<StarterCategory> replacement = new ArrayList<>();
        for (int categoryIndex = 0; categoryIndex < trios; categoryIndex++) {
            int count = TRIO_TYPES.length;

            Random random = seedFor(player.getUuid(), state.getConfigGeneration(), categoryIndex);
            List<PokemonProperties> picks = new ArrayList<>();
            Set<String> used = new HashSet<>();

            for (int slot = 0; slot < count; slot++) {
                List<Species> slotPool = config.startersTypeTrio
                        ? ofType(pool, TRIO_TYPES[slot % TRIO_TYPES.length], config)
                        : pool;
                if (slotPool.isEmpty()) {
                    slotPool = pool;
                }
                Species chosen = pickUnused(slotPool, used, random);
                if (chosen == null) {
                    continue;
                }
                used.add(chosen.getName());
                PokemonProperties properties = propertiesFor(chosen, config.starterRegion);
                if (properties != null) {
                    picks.add(properties);
                }
            }
            if (picks.isEmpty()) {
                return null;
            }
            replacement.add(new StarterCategory(
                    "cobblelocke_trio_" + (categoryIndex + 1),
                    categoryIndex,
                    trios == 1 ? "Random Starters" : "Random Starters " + (categoryIndex + 1),
                    picks,
                    false));
        }
        return replacement;
    }

    private static List<Species> candidatePool(CobblelockeConfig config) {
        List<Species> candidates = new ArrayList<>();
        for (Species species : SpeciesPool.byRegion(config.starterRegion)) {
            String name = species.getName();
            if (name == null || name.contains(".") || name.contains("_")) {
                continue;
            }
            if (species.getNationalPokedexNumber() <= 0) {
                continue;
            }
            if (config.startersNoLegendaries && SpeciesPool.isRestricted(species)) {
                continue;
            }
            if (config.startersBasicOnly && !SpeciesPool.isBasic(species)) {
                continue;
            }
            if (SpeciesPool.isGimmickForm(species)) {
                continue;
            }

            if (config.startersTriEvolution && !SpeciesPool.inThreeStageLine(species)) {
                continue;
            }
            candidates.add(species);
        }

        candidates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return candidates;
    }

    private static List<Species> ofType(List<Species> pool, String type, CobblelockeConfig config) {
        GlobalPools pools = GlobalPools.get();
        List<Species> out = new ArrayList<>();
        for (Species species : pool) {
            String[] natural = SpeciesPool.originalTypes(species.getStandardForm());
            String[] effective = config.starter(config.randomStarterTypes && config.globalStarterTypes) && pools != null
                    ? pools.typesFor(species.getName(), config, natural)
                    : natural;
            if (effective == null) {
                continue;
            }
            for (String candidate : effective) {
                if (type.equalsIgnoreCase(candidate)) {
                    out.add(species);
                    break;
                }
            }
        }
        return out;
    }

    private static Species pickUnused(List<Species> pool, Set<String> used, Random random) {
        List<Species> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        for (Species species : shuffled) {
            if (!used.contains(species.getName())) {
                return species;
            }
        }
        return null;
    }

    private static PokemonProperties propertiesFor(Species species, String region) {
        try {
            StringBuilder properties = new StringBuilder(species.getName().toLowerCase(Locale.ROOT));
            for (String aspect : SpeciesPool.regionalAspects(species, region)) {
                properties.append(' ').append(aspect.contains("=") ? aspect : aspect + "=true");
            }
            return PokemonProperties.Companion.parse(properties.toString());
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not build starter properties for {}: {}",
                    species.getName(), e.toString());
            return null;
        }
    }

    private static Random seedFor(UUID playerId, int generation, int categoryIndex) {
        long worldSeed = 0L;
        try {
            if (Cobblelocke.getServer() != null) {
                worldSeed = Cobblelocke.getServer().getOverworld().getSeed();
            }
        } catch (Exception ignored) {
        }
        return new Random(worldSeed
                + playerId.hashCode() * 31L
                + generation * 7919L
                + categoryIndex * 104729L
                + SALT);
    }
}
