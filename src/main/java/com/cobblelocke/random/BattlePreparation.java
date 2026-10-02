package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.compat.GymLeaders;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.data.CobblelockeState;
import com.cobblelocke.util.SpeciesPool;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class BattlePreparation {
    private static final String[] GYM_MARKERS = {"leader", "gym", "elite", "champion"};

    private static final int DOUBLES_OFF = 0;
    private static final int DOUBLES_ALWAYS = 1;
    private static final int DOUBLES_RANDOM = 2;

    private static final ThreadLocal<ServerPlayerEntity> LEVELLING_AGAINST = new ThreadLocal<>();

    private BattlePreparation() {
    }

    public static void prepare(BattleFormat format, BattleSide side1, BattleSide side2) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive) {
            return;
        }

        boolean doubles = isDoubles(format) || wantsDoubles(config, format, side1, side2);
        int generation = state.getConfigGeneration();
        LEVELLING_AGAINST.set(firstPlayer(side1, side2));
        try {
            prepareSide(side1, config, generation, doubles);
            prepareSide(side2, config, generation, doubles);
        } finally {
            LEVELLING_AGAINST.remove();
        }
    }

    private static ServerPlayerEntity firstPlayer(BattleSide... sides) {
        MinecraftServer server = Cobblelocke.getServer();
        if (server == null) {
            return null;
        }
        for (BattleSide side : sides) {
            if (side == null) {
                continue;
            }
            for (BattleActor actor : side.getActors()) {
                for (java.util.UUID id : actor.getPlayerUUIDs()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                    if (player != null) {
                        return player;
                    }
                }
            }
        }
        return null;
    }

    private static void prepareSide(BattleSide side, CobblelockeConfig config, int generation, boolean doubles) {
        if (side == null) {
            return;
        }
        for (BattleActor actor : side.getActors()) {
            if (actor == null) {
                continue;
            }
            boolean trainer = isTrainerActor(actor);
            GymLeaders.Slot gym = trainer ? leagueSlot(actor) : null;
            if (trainer) {
                resizeTeam(actor, config, doubles, gym);
            }
            for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                try {
                    prepareOne(battlePokemon, trainer, config, generation, gym);
                } catch (Exception e) {
                    Cobblelocke.LOGGER.warn("Could not prepare a battle Pokemon: {}", e.toString());
                }
            }
            if (trainer) {
                applyGimmicks(actor, config);
            }
        }
    }

    private static void prepareOne(BattlePokemon battlePokemon, boolean trainer,
                                   CobblelockeConfig config, int generation, GymLeaders.Slot gym) {
        if (battlePokemon == null) {
            return;
        }
        Pokemon pokemon = battlePokemon.getEffectedPokemon();
        if (pokemon == null) {
            return;
        }

        if (trainer && config.anyTrainerRandomization()) {
            applyTrainerRules(pokemon, config, generation, gym);
        } else {
            PokemonStamper.stamp(pokemon, config, generation, PokemonStamper.Context.OWNED);
        }

        if (!trainer && pokemon.isWild()) {
            Gimmicks.megaEvolve(pokemon);
        }

        String[] types = PokemonStamper.storedTypes(pokemon);
        if (types != null) {
            BattleSpeciesRegistry.ensureRegistered(pokemon, types);
        }
    }

    private static void applyTrainerRules(Pokemon pokemon, CobblelockeConfig config, int generation,
                                          GymLeaders.Slot gym) {
        Species replacement = SpeciesPool.randomInBstBand(new Random(),
                minBst(config, gym), maxBst(config, gym));
        if (replacement != null && !replacement.getName().equals(pokemon.getSpecies().getName())) {
            pokemon.setSpecies(replacement);
            PokemonStamper.clearStamp(pokemon);
            if (!config.trainer(config.randomTrainerMoves)) {
                try {
                    pokemon.initializeMoveset(true);
                } catch (Exception ignored) {
                }
            }
        }
        int level = trainerLevel(config, gym);
        if (level > 0) {
            try {
                pokemon.setLevel(level);
            } catch (Exception ignored) {
            }
        }
        PokemonStamper.stamp(pokemon, config, generation, PokemonStamper.Context.TRAINER);
    }

    private static int minBst(CobblelockeConfig config, GymLeaders.Slot gym) {
        int leagueValue = config.leagueSetting("BstMin", gym);
        return leagueValue > 0 ? leagueValue : config.trainerMinBst;
    }

    private static int maxBst(CobblelockeConfig config, GymLeaders.Slot gym) {
        int leagueValue = config.leagueSetting("BstMax", gym);
        return leagueValue > 0 ? leagueValue : config.trainerMaxBst;
    }

    private static int trainerLevel(CobblelockeConfig config, GymLeaders.Slot gym) {
        if (gym != null) {
            return config.leagueSetting("Level", gym);
        }
        if (config.trainerLevelMode == com.cobblelocke.util.PartyLevels.RANDOM_UP_TO_HIGHEST) {
            return 0;
        }
        ServerPlayerEntity opponent = LEVELLING_AGAINST.get();
        return opponent == null ? 0
                : com.cobblelocke.util.PartyLevels.forMode(config.trainerLevelMode, opponent, new Random());
    }

    private static void applyGimmicks(BattleActor actor, CobblelockeConfig config) {
        try {
            if (config.trainerDyna) {
                actor.setCanDynamax(true);
            }
            if (!config.trainerTeras && !config.trainerMegas) {
                return;
            }
            Random random = new Random();
            for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                Pokemon pokemon = battlePokemon.getEffectedPokemon();
                if (pokemon == null) {
                    continue;
                }
                if (config.trainerTeras) {
                    Gimmicks.randomTera(pokemon, random);
                }

                if (config.trainerMegas) {
                    Gimmicks.giveMegaStone(pokemon, false);
                }
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not set up a trainer's battle mechanics: {}", e.toString());
        }
    }

    private static void resizeTeam(BattleActor actor, CobblelockeConfig config, boolean doubles,
                                   GymLeaders.Slot gym) {
        int fromLeague = config.leagueSetting("Count", gym);
        int wanted = fromLeague > 0 ? fromLeague
                : doubles ? config.doubleBattleTeamSize
                : config.trainerTeamSize;
        if (wanted <= 0 && doubles) {
            wanted = config.doubleBattleTeamSize;
        }
        if (wanted <= 0) {
            return;
        }
        List<BattlePokemon> current = actor.getPokemonList();
        if (current.isEmpty() || current.size() == wanted) {
            return;
        }
        try {
            List<BattlePokemon> team = new ArrayList<>(current);
            while (team.size() > wanted) {
                team.remove(team.size() - 1);
            }
            int level = averageLevel(team);
            Random random = new Random();
            while (team.size() < wanted) {
                Species species = SpeciesPool.randomInBstBand(random, minBst(config, gym),
                        maxBst(config, gym));
                if (species == null) {
                    break;
                }
                Pokemon extra = PokemonProperties.Companion
                        .parse(species.getName().toLowerCase(Locale.ROOT) + " level=" + level)
                        .create();
                BattlePokemon member = BattlePokemon.Companion.safeCopyOf(extra);
                member.setActor(actor);
                team.add(member);
            }
            setTeam(actor, team);
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not resize a trainer team: {}", e.toString());
        }
    }

    private static void setTeam(BattleActor actor, List<BattlePokemon> team) throws Exception {
        try {
            Field field = BattleActor.class.getDeclaredField("pokemonList");
            field.setAccessible(true);
            field.set(actor, team);
            return;
        } catch (RuntimeException | ReflectiveOperationException e) {
            Cobblelocke.LOGGER.debug("Could not swap a battle team in: {}", e.toString());
        }
        List<BattlePokemon> existing = actor.getPokemonList();
        existing.clear();
        existing.addAll(team);
    }

    private static int averageLevel(List<BattlePokemon> team) {
        int total = 0;
        int count = 0;
        for (BattlePokemon member : team) {
            Pokemon pokemon = member.getEffectedPokemon();
            if (pokemon != null) {
                total += pokemon.getLevel();
                count++;
            }
        }
        return count == 0 ? 5 : Math.max(1, Math.min(100, total / count));
    }

    private static boolean isDoubles(BattleFormat format) {
        try {
            return format != null && format.getBattleType().getSlotsPerActor() >= 2;
        } catch (Exception e) {
            return false;
        }
    }

    public static BattleFormat toDoubles(BattleFormat format, BattleSide side1, BattleSide side2) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null || format == null || isDoubles(format)) {
            return format;
        }
        if (!wantsDoubles(state.getConfig(), format, side1, side2)) {
            return format;
        }
        try {
            return format.copy(format.getMod(), BattleFormat.Companion.getGEN_9_DOUBLES().getBattleType(),
                    format.getRuleSet(), format.getGen(), format.getAdjustLevel());
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not switch to a double battle: {}", e.toString());
            return format;
        }
    }

    private static boolean wantsDoubles(CobblelockeConfig config, BattleFormat format,
                                        BattleSide side1, BattleSide side2) {
        if (!config.runActive || config.doubleBattle == DOUBLES_OFF || format == null) {
            return false;
        }
        if (!hasTrainer(side1) && !hasTrainer(side2)) {
            return false;
        }

        if (teamSize(side1, config) < 2 || teamSize(side2, config) < 2) {
            return false;
        }

        return config.doubleBattle != DOUBLES_RANDOM || new Random().nextBoolean();
    }

    private static boolean hasTrainer(BattleSide side) {
        if (side == null) {
            return false;
        }
        for (BattleActor actor : side.getActors()) {
            if (isTrainerActor(actor)) {
                return true;
            }
        }
        return false;
    }

    private static int teamSize(BattleSide side, CobblelockeConfig config) {
        if (side == null) {
            return 0;
        }
        int smallest = Integer.MAX_VALUE;
        for (BattleActor actor : side.getActors()) {
            smallest = Math.min(smallest, effectiveTeamSize(actor, config));
        }
        return smallest == Integer.MAX_VALUE ? 0 : smallest;
    }

    private static int effectiveTeamSize(BattleActor actor, CobblelockeConfig config) {
        int actual = actor.getPokemonList().size();
        if (!isTrainerActor(actor)) {
            return actual;
        }
        GymLeaders.Slot gym = leagueSlot(actor);
        int fromLeague = config.leagueSetting("Count", gym);
        int wanted = fromLeague > 0 ? fromLeague
                : config.doubleBattle != DOUBLES_OFF && config.doubleBattleTeamSize > 0
                ? config.doubleBattleTeamSize
                : config.trainerTeamSize;
        return wanted > 0 ? wanted : actual;
    }

    private static boolean isTrainerActor(BattleActor actor) {
        try {
            if (actor.getPlayerUUIDs().iterator().hasNext()) {
                return false;
            }
            if (actor instanceof EntityBackedBattleActor<?> entityActor) {
                LivingEntity entity = entityActor.getEntity();
                return entity != null && !(entity instanceof PokemonEntity);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static GymLeaders.Slot leagueSlot(BattleActor actor) {
        GymLeaders.Slot fromData = GymLeaders.slotFor(trainerId(actor));
        if (fromData != null) {
            return fromData;
        }
        return isGymActor(actor) ? new GymLeaders.Slot(GymLeaders.Role.GYM, 1) : null;
    }

    private static String trainerId(BattleActor actor) {
        try {
            if (actor instanceof EntityBackedBattleActor<?> entityActor && entityActor.getEntity() != null) {
                Object entity = entityActor.getEntity();
                Method getTrainerId = entity.getClass().getMethod("getTrainerId");
                Object id = getTrainerId.invoke(entity);
                return id == null ? null : id.toString();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
        }
        return null;
    }

    private static boolean isGymActor(BattleActor actor) {
        StringBuilder identity = new StringBuilder();
        try {
            identity.append(actor.getName().getString());
            if (actor instanceof EntityBackedBattleActor<?> entityActor && entityActor.getEntity() != null) {
                Object entity = entityActor.getEntity();
                try {
                    Method getTrainerId = entity.getClass().getMethod("getTrainerId");
                    Object id = getTrainerId.invoke(entity);
                    if (id != null) {
                        identity.append(' ').append(id);
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Exception e) {
            return false;
        }
        String lower = identity.toString().toLowerCase(Locale.ROOT);
        for (String marker : GYM_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
