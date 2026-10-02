package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.data.CobblelockeState;
import com.cobblelocke.data.PlayerState;
import com.cobblelocke.eventlock.EventLockService;
import com.cobblelocke.nuzlocke.NicknameService;
import com.cobblelocke.util.SpeciesPool;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent;
import com.cobblemon.mod.common.api.events.starter.StarterChosenEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

public final class RandomizerService {
    private static final int PARTY_SWEEP_INTERVAL = 100;

    private static boolean registered = false;
    private static int sweepCounter = 0;

    private RandomizerService() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.HIGHEST,
                (Consumer<SpawnEvent<PokemonEntity>>) event -> randomizeWild(event.getEntity()));
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL,
                (Consumer<PokemonCapturedEvent>) RandomizerService::onCaptured);
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.HIGH,
                (Consumer<EvolutionCompleteEvent>) RandomizerService::onEvolved);
        CobblemonEvents.STARTER_CHOSEN.subscribe(Priority.LOW,
                (Consumer<StarterChosenEvent>) RandomizerService::onStarterChosen);
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_POST.subscribe(Priority.NORMAL,
                (Consumer<ExperienceGainedEvent.Post>) RandomizerService::onLevelledUp);

        Cobblelocke.LOGGER.info("Randomizer rules registered");
    }

    private static void onLevelledUp(ExperienceGainedEvent.Post event) {
        CobblelockeState state = Cobblelocke.state();
        Pokemon pokemon = event.getPokemon();
        if (state == null || pokemon == null) {
            return;
        }
        if (!state.getConfig().runActive) {
            return;
        }
        CustomLearnset learnset = PokemonStamper.storedLearnset(pokemon);
        if (learnset == null || learnset.isEmpty()) {
            return;
        }
        int from = event.getPreviousLevel();
        int to = event.getCurrentLevel();
        if (to <= from) {
            return;
        }

        try {
            for (int level = from + 1; level <= to; level++) {
                String moveName = learnset.getLevelUpMoves().get(level);
                if (moveName == null) {
                    continue;
                }
                MoveTemplate template = Moves.getByName(moveName.toLowerCase(Locale.ROOT));
                if (template == null || alreadyKnows(pokemon, moveName)) {
                    continue;
                }
                if (pokemon.getMoveSet().hasSpace()) {
                    pokemon.getMoveSet().add(template.create());
                } else {
                    pokemon.getBenchedMoves().add(new BenchedMove(template, 0));
                }
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not teach a randomized level-up move: {}", e.toString());
        }
    }

    private static boolean alreadyKnows(Pokemon pokemon, String moveName) {
        for (Move move : pokemon.getMoveSet().getMoves()) {
            if (move != null && move.getName().equalsIgnoreCase(moveName)) {
                return true;
            }
        }
        for (BenchedMove benched : pokemon.getBenchedMoves()) {
            if (benched.getMoveTemplate() != null
                    && benched.getMoveTemplate().getName().equalsIgnoreCase(moveName)) {
                return true;
            }
        }
        return false;
    }

    public static void randomizeWild(PokemonEntity entity) {
        CobblelockeState state = Cobblelocke.state();
        if (entity == null || state == null || entity.getWorld().isClient()) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive) {
            return;
        }
        Pokemon pokemon = entity.getPokemon();
        if (pokemon == null || !pokemon.isWild() || entity.isBattling()) {
            return;
        }

        try {
            NbtCompound data = pokemon.getPersistentData();
            if (data == null) {
                return;
            }

            if (data.getBoolean(EventLockService.KEY_EVENT)) {
                return;
            }

            boolean speciesLocked = data.getBoolean("raid")
                    || data.getBoolean("ultra_wormholes_boss")
                    || entity.getCommandTags().contains("ultra_wormholes_boss_entity");

            if (!data.getBoolean(PokemonStamper.KEY_SPAWN_HANDLED)) {
                data.putBoolean(PokemonStamper.KEY_SPAWN_HANDLED, true);
                if (config.randomSpawns && !speciesLocked) {
                    Random random = new Random();
                    boolean wasLegendary = config.legendariesReplaceLegendaries
                            && SpeciesPool.isRestricted(pokemon.getSpecies());
                    Species replacement = wasLegendary
                            ? SpeciesPool.randomLegendary(random)
                            : SpeciesPool.rollWild(random, config);
                    if (replacement != null) {
                        pokemon.setSpecies(replacement);
                    }
                }
            }
            PokemonStamper.stamp(pokemon, config, state.getConfigGeneration(), PokemonStamper.Context.WILD);
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not randomize a wild spawn: {}", e.toString());
        }
    }

    private static void onCaptured(PokemonCapturedEvent event) {
        CobblelockeState state = Cobblelocke.state();
        ServerPlayerEntity player = event.getPlayer();
        if (state == null || player == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive) {
            return;
        }
        Pokemon pokemon = event.getPokemon();
        Gimmicks.revertMega(pokemon);

        if (config.wild(config.randomWildCaptures)) {
            Species replacement = SpeciesPool.rollWild(new Random(), config);
            String original = pokemon.getSpecies().getName();
            if (replacement != null && !replacement.getName().equals(original)) {
                MinecraftServer server = Cobblelocke.getServer();
                Runnable transform = () -> {
                    try {
                        pokemon.setSpecies(replacement);

                        PokemonStamper.clearStamp(pokemon);
                        PokemonStamper.stamp(pokemon, config, state.getConfigGeneration(),
                                PokemonStamper.Context.WILD);
                        pokemon.heal();
                        player.sendMessage(Text.literal("§d★ " + original + " transformed into §b"
                                + replacement.getName() + "§d!"));
                    } catch (Exception e) {
                        Cobblelocke.LOGGER.warn("Could not transform a captured Pokemon: {}", e.toString());
                    }
                };

                if (server != null) {
                    server.execute(transform);
                } else {
                    transform.run();
                }
            }
        }
    }

    private static void onEvolved(EvolutionCompleteEvent event) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive) {
            return;
        }
        Pokemon pokemon = event.getPokemon();
        if (pokemon == null) {
            return;
        }

        MinecraftServer server = Cobblelocke.getServer();
        UUID ownerId = pokemon.getOwnerUUID();
        String before = pokemon.getSpecies().getName();

        Runnable apply = () -> {
            try {
                if (config.randomEvolutions) {
                    Species replacement = rollEvolution(pokemon.getSpecies(), config);
                    if (replacement != null && !replacement.getName().equals(before)) {
                        pokemon.setSpecies(replacement);
                        if (ownerId != null && server != null) {
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerId);
                            if (player != null) {
                                player.sendMessage(Text.literal("§d★ " + before + " evolved into §b"
                                        + replacement.getName() + "§d!"));
                            }
                        }
                    }
                }

                PokemonStamper.restampAfterEvolution(pokemon, config, state.getConfigGeneration());
            } catch (Exception e) {
                Cobblelocke.LOGGER.warn("Could not randomize an evolution: {}", e.toString());
            }
        };
        if (server != null) {
            server.execute(apply);
        } else {
            apply.run();
        }
    }

    private static Species rollEvolution(Species current, CobblelockeConfig config) {
        List<Species> candidates = SpeciesPool.battleSafe(true);
        if (candidates.isEmpty()) {
            return null;
        }
        int stage = SpeciesPool.evolutionStage(current);
        int bst = SpeciesPool.baseStatTotal(current);

        List<Species> filtered = candidates.stream()
                .filter(species -> !species.getName().equals(current.getName()))
                .filter(species -> !config.evolutionsSameStage || SpeciesPool.evolutionStage(species) == stage)
                .filter(species -> !config.evolutionsSimilarBST
                        || Math.abs(SpeciesPool.baseStatTotal(species) - bst) <= 100)
                .toList();
        if (filtered.isEmpty()) {
            filtered = candidates;
        }
        return filtered.get(new Random().nextInt(filtered.size()));
    }

    private static void onStarterChosen(StarterChosenEvent event) {
        CobblelockeState state = Cobblelocke.state();
        ServerPlayerEntity player = event.getPlayer();
        if (state == null || player == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive) {
            return;
        }

        PlayerState playerState = state.getPlayer(player.getUuid());
        playerState.setHasChosenStarter(true);
        state.markDirty();

        Pokemon starter = event.getPokemon();
        if (starter != null && config.starter(config.startersTypeTrio)) {
            starter.getPersistentData().putBoolean(PokemonStamper.KEY_TRIO, true);
        }
        if (starter != null) {
            PokemonStamper.stamp(starter, config, state.getConfigGeneration(), PokemonStamper.Context.STARTER);
            applyStarterGimmicks(starter, config);
        }
        EventLockService.onStarterChosen(player, playerState);

        if (starter != null && config.nuzlocke(config.requireNicknames)) {
            NicknameService.requestAfterCapture(player, starter);
        }

        player.sendMessage(Text.literal("§d★ Your Cobblelocke run has begun."));
    }

    private static void applyStarterGimmicks(Pokemon starter, CobblelockeConfig config) {
        try {
            if (config.starterTeras) {
                Gimmicks.randomTera(starter, new Random());
            }
            if (config.starterMegas) {
                Gimmicks.giveMegaStone(starter, true);
            }
            if (config.starterDyna) {
                Gimmicks.giveGmaxFactor(starter);
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not set up a starter's battle mechanics: {}", e.toString());
        }
    }

    public static void tick(MinecraftServer server) {
        if (++sweepCounter < PARTY_SWEEP_INTERVAL) {
            return;
        }
        sweepCounter = 0;

        CobblelockeState state = CobblelockeState.get(server);
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive) {
            return;
        }

        boolean stampParties = config.anyGlobalPool() || config.randomTmMoves;
        int generation = state.getConfigGeneration();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                boolean hasPokemon = false;
                for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                    hasPokemon = true;
                    if (stampParties) {
                        PokemonStamper.stamp(pokemon, config, generation, PokemonStamper.Context.OWNED);
                    }
                }

                if (hasPokemon) {
                    PlayerState playerState = state.getPlayer(player.getUuid());
                    if (!playerState.hasChosenStarter()) {
                        playerState.setHasChosenStarter(true);
                        EventLockService.onStarterChosen(player, playerState);
                        state.markDirty();
                    }
                }
            } catch (Exception e) {
                Cobblelocke.LOGGER.debug("Party sweep failed for {}: {}",
                        player.getName().getString(), e.toString());
            }
        }
        GlobalPools pools = GlobalPools.get();
        if (pools != null) {
            pools.save();
        }
    }
}
