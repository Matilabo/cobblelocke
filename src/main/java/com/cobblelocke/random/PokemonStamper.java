package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.util.SpeciesPool;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class PokemonStamper {
    public static final String KEY_GENERATION = "cobblelocke_gen";
    public static final String KEY_TYPE_1 = "cobblelocke_type1";
    public static final String KEY_TYPE_2 = "cobblelocke_type2";
    public static final String KEY_LEARNSET = "cobblelocke_learnset";

    public static final String KEY_SPAWN_HANDLED = "cobblelocke_spawned";

    public static final String KEY_TRIO = "cobblelocke_trio";

    public static final String KEY_CONTEXT = "cobblelocke_ctx";

    public static final String KEY_RANDOM_ABILITY = "cobblelocke_ability";

    public static final String KEY_TMS = "cobblelocke_tms";

    public enum Context {
        WILD,
        STARTER,
        TRAINER,
        OWNED
    }

    private PokemonStamper() {
    }

    public static String[] storedTypes(Pokemon pokemon) {
        if (pokemon == null) {
            return null;
        }
        try {
            NbtCompound data = pokemon.getPersistentData();
            if (data == null || !data.contains(KEY_TYPE_1)) {
                return null;
            }
            String primary = data.getString(KEY_TYPE_1);
            if (primary == null || primary.isBlank()) {
                return null;
            }
            String secondary = data.contains(KEY_TYPE_2) ? data.getString(KEY_TYPE_2) : null;
            if (secondary == null || secondary.isBlank()) {
                return new String[]{primary};
            }
            return new String[]{primary, secondary};
        } catch (Exception e) {
            return null;
        }
    }

    public static CustomLearnset storedLearnset(Pokemon pokemon) {
        if (pokemon == null) {
            return null;
        }
        try {
            NbtCompound data = pokemon.getPersistentData();
            if (data == null || !data.contains(KEY_LEARNSET)) {
                return null;
            }
            return CustomLearnset.fromNbt(data.getCompound(KEY_LEARNSET));
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> storedTms(Pokemon pokemon) {
        try {
            NbtCompound data = pokemon == null ? null : pokemon.getPersistentData();
            if (data == null || !data.contains(KEY_TMS)) {
                return null;
            }
            NbtList list = data.getList(KEY_TMS, NbtElement.STRING_TYPE);
            List<String> out = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                out.add(list.getString(i));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static Context storedContext(Pokemon pokemon) {
        try {
            String raw = pokemon.getPersistentData().getString(KEY_CONTEXT);
            return raw.isEmpty() ? Context.OWNED : Context.valueOf(raw);
        } catch (Exception e) {
            return Context.OWNED;
        }
    }

    public static boolean stamp(Pokemon pokemon, CobblelockeConfig config, int generation, Context context) {
        if (pokemon == null || config == null || !config.runActive) {
            return false;
        }
        NbtCompound data;
        try {
            data = pokemon.getPersistentData();
        } catch (Exception e) {
            return false;
        }
        if (data == null) {
            return false;
        }

        boolean changed = false;

        if (config.randomTmMoves && !data.contains(KEY_TMS)) {
            data.put(KEY_TMS, rollTms(pokemon, new Random()));
            changed = true;
        }

        if (data.getInt(KEY_GENERATION) == generation) {
            return changed;
        }

        if (isSpecialForm(pokemon)) {
            data.putInt(KEY_GENERATION, generation);
            return changed;
        }

        if (context != Context.OWNED) {
            data.putString(KEY_CONTEXT, context.name());
        }

        Random random = new Random();
        String speciesName = speciesNameOf(pokemon);

        boolean typeThemedStarter = data.getBoolean(KEY_TRIO) && !globalTypes(config, context);
        if (!typeThemedStarter && randomTypes(config, context)) {
            changed |= applyTypes(pokemon, data, config, random, speciesName, globalTypes(config, context));
        }
        if (randomAbilities(config, context)) {
            if (applyAbility(pokemon, config, random, speciesName, globalAbilities(config, context))) {
                data.putBoolean(KEY_RANDOM_ABILITY, true);
                changed = true;
            }
        }
        if (randomMoves(config, context)) {
            changed |= applyMoves(pokemon, data, random, speciesName, globalMoves(config, context), true);
        }
        if (randomHeldItem(config, context)) {
            changed |= HeldItemRoller.apply(pokemon, random, config, context != Context.TRAINER);
        }
        if (context == Context.WILD) {
            if (config.wild(config.randomizeTeras)) {
                changed |= Gimmicks.randomTera(pokemon, random);
            }

            if (config.wild(config.randomizeMegas)) {
                changed |= Gimmicks.giveMegaStone(pokemon, true);
            }
        }

        data.putInt(KEY_GENERATION, generation);
        return changed;
    }

    public static void restampAfterEvolution(Pokemon pokemon, CobblelockeConfig config, int generation) {
        NbtCompound data;
        try {
            data = pokemon.getPersistentData();
        } catch (Exception e) {
            return;
        }
        if (data == null || isSpecialForm(pokemon)) {
            return;
        }
        Context context = storedContext(pokemon);
        Random random = new Random();
        String speciesName = speciesNameOf(pokemon);

        if (data.contains(KEY_LEARNSET)) {
            applyMoves(pokemon, data, random, speciesName, globalMoves(config, context), false);
        }
        if (data.getBoolean(KEY_RANDOM_ABILITY)) {
            applyAbility(pokemon, config, random, speciesName, globalAbilities(config, context));
        }
        if (data.contains(KEY_TYPE_1) && !data.getBoolean(KEY_TRIO)) {
            applyTypes(pokemon, data, config, random, speciesName, globalTypes(config, context));
        }
        data.putInt(KEY_GENERATION, generation);
    }

    static boolean randomTypes(CobblelockeConfig c, Context context) {
        return switch (context) {
            case WILD -> c.wild(c.randomWildTypes);
            case STARTER -> c.starter(c.randomStarterTypes);
            case TRAINER -> c.trainer(c.randomTrainerTypes);
            case OWNED -> false;
        };
    }

    static boolean globalTypes(CobblelockeConfig c, Context context) {
        return switch (context) {
            case WILD -> c.wild(c.randomWildTypes && c.globalWildTypes);
            case STARTER -> c.starter(c.randomStarterTypes && c.globalStarterTypes);
            case TRAINER -> c.trainer(c.randomTrainerTypes && c.globalTrainerTypes);
            case OWNED -> false;
        };
    }

    static boolean randomAbilities(CobblelockeConfig c, Context context) {
        return switch (context) {
            case WILD -> c.wild(c.randomWildAbilities);
            case STARTER -> c.starter(c.randomStarterAbilities);
            case TRAINER -> c.trainer(c.randomTrainerAbilities);
            case OWNED -> false;
        };
    }

    static boolean globalAbilities(CobblelockeConfig c, Context context) {
        return switch (context) {
            case WILD -> c.wild(c.randomWildAbilities && c.globalWildAbilities);
            case STARTER -> c.starter(c.randomStarterAbilities && c.globalStarterAbilities);
            case TRAINER -> c.trainer(c.randomTrainerAbilities && c.globalTrainerAbilities);
            case OWNED -> false;
        };
    }

    static boolean randomMoves(CobblelockeConfig c, Context context) {
        return switch (context) {
            case WILD -> c.wild(c.randomWildMoves);
            case STARTER -> c.starter(c.randomStarterMoves);
            case TRAINER -> c.trainer(c.randomTrainerMoves);
            case OWNED -> false;
        };
    }

    static boolean globalMoves(CobblelockeConfig c, Context context) {
        return switch (context) {
            case WILD -> c.wild(c.randomWildMoves && c.globalWildMoves);
            case STARTER -> c.starter(c.randomStarterMoves && c.globalStarterMoves);
            case TRAINER -> c.trainer(c.randomTrainerMoves && c.globalTrainerMoves);
            case OWNED -> false;
        };
    }

    static boolean randomHeldItem(CobblelockeConfig c, Context context) {
        return switch (context) {
            case WILD -> c.wild(c.randomWildHeldItems);
            case TRAINER -> c.trainer(c.randomTrainerHeldItems);
            default -> false;
        };
    }

    public static boolean applyTypes(Pokemon pokemon, NbtCompound data, CobblelockeConfig config,
                                     Random random, String speciesName, boolean global) {
        String[] original = SpeciesPool.originalTypes(pokemon.getForm());
        String[] types;
        GlobalPools pools = GlobalPools.get();
        if (global && pools != null) {
            types = pools.typesFor(speciesName, config, original);
        } else {
            types = TypeRoller.roll(random, config, original);
        }
        if (types == null || types.length == 0) {
            return false;
        }
        data.putString(KEY_TYPE_1, types[0].toLowerCase(Locale.ROOT));
        if (types.length > 1 && types[1] != null && !types[1].isBlank()) {
            data.putString(KEY_TYPE_2, types[1].toLowerCase(Locale.ROOT));
        } else {
            data.remove(KEY_TYPE_2);
        }
        return true;
    }

    public static boolean applyAbility(Pokemon pokemon, CobblelockeConfig config, Random random,
                                       String speciesName, boolean global) {
        String abilityName;
        GlobalPools pools = GlobalPools.get();
        if (global && pools != null) {
            abilityName = pools.abilityFor(speciesName);
        } else {
            List<String> pool = SpeciesPool.abilityNames();
            abilityName = pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
        }
        return applyNamedAbility(pokemon, abilityName);
    }

    public static boolean applyNamedAbility(Pokemon pokemon, String abilityName) {
        if (abilityName == null || abilityName.isBlank()) {
            return false;
        }
        try {
            AbilityTemplate template = Abilities.get(abilityName);
            if (template == null) {
                return false;
            }
            pokemon.updateAbility(template.create(false, Priority.LOWEST));
            return true;
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not apply ability {}: {}", abilityName, e.toString());
            return false;
        }
    }

    public static boolean applyMoves(Pokemon pokemon, NbtCompound data, Random random, String speciesName,
                                     boolean global, boolean refreshMoveSet) {
        CustomLearnset learnset;
        GlobalPools pools = GlobalPools.get();
        if (global && pools != null) {
            learnset = pools.movesFor(speciesName);
        } else {
            learnset = LearnsetRoller.roll(speciesName, random);
        }
        if (learnset == null || learnset.isEmpty()) {
            return false;
        }
        data.put(KEY_LEARNSET, learnset.toNbt());
        if (refreshMoveSet) {
            refreshMoveSet(pokemon, learnset);
        }
        return true;
    }

    public static void refreshMoveSet(Pokemon pokemon, CustomLearnset learnset) {
        try {
            MoveSet moveSet = pokemon.getMoveSet();
            moveSet.clear();
            int level = pokemon.getLevel();
            List<String> available = learnset.movesUpToLevel(level);

            int wanted = level < 5 ? 1 : level < 10 ? 2 : level < 15 ? 3 : 4;
            wanted = Math.min(wanted, available.size());
            int start = Math.max(0, available.size() - wanted);
            for (int i = start; i < available.size(); i++) {
                MoveTemplate template = Moves.getByName(available.get(i).toLowerCase(Locale.ROOT));
                if (template != null) {
                    moveSet.add(template.create());
                }
            }
            if (moveSet.getMoves().isEmpty()) {
                MoveTemplate fallback = Moves.getByName("tackle");
                if (fallback == null) {
                    fallback = Moves.getExceptional();
                }
                if (fallback != null) {
                    moveSet.add(fallback.create());
                }
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not rebuild moveset: {}", e.toString());
        }
    }

    private static NbtList rollTms(Pokemon pokemon, Random random) {
        List<String> pool = SpeciesPool.tmMoveNames();
        int size = 25;
        try {
            size = Math.max(12, Math.min(60, pokemon.getForm().getMoves().tmLearnableMoves().size()));
        } catch (Exception ignored) {
        }
        List<String> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, random);
        NbtList list = new NbtList();
        for (int i = 0; i < Math.min(size, shuffled.size()); i++) {
            list.add(NbtString.of(shuffled.get(i)));
        }
        return list;
    }

    public static void clearStamp(Pokemon pokemon) {
        try {
            NbtCompound data = pokemon.getPersistentData();
            if (data != null) {
                data.remove(KEY_GENERATION);
            }
        } catch (Exception ignored) {
        }
    }

    public static String speciesNameOf(Pokemon pokemon) {
        try {
            return pokemon.getSpecies().getName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static boolean isSpecialForm(Pokemon pokemon) {
        try {
            String formName = pokemon.getForm() == null ? null : pokemon.getForm().getName();
            if (formName == null || formName.isEmpty()) {
                return false;
            }
            String lower = formName.toLowerCase(Locale.ROOT);
            return lower.contains("mega")
                    || lower.contains("gmax")
                    || lower.contains("gigantamax")
                    || lower.contains("primal")
                    || lower.contains("ultra")
                    || lower.contains("eternamax")
                    || lower.contains("terastal")
                    || lower.contains("stellar");
        } catch (Exception e) {
            return false;
        }
    }
}
