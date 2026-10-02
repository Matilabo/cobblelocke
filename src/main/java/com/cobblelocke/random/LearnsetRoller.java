package com.cobblelocke.random;

import com.cobblelocke.util.SpeciesPool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class LearnsetRoller {
    private static final int[] LEVEL_UP_LEVELS = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
    private static final int TM_MOVE_COUNT = 10;
    private static final int EGG_MOVE_COUNT = 5;

    private static final Map<String, String> EVOLUTION_MOVE_REQUIREMENTS = new HashMap<>();

    static {
        EVOLUTION_MOVE_REQUIREMENTS.put("aipom", "doublehit");
        EVOLUTION_MOVE_REQUIREMENTS.put("applin", "dragoncheer");
        EVOLUTION_MOVE_REQUIREMENTS.put("basculin", "wavecrash");
        EVOLUTION_MOVE_REQUIREMENTS.put("bonsly", "mimic");
        EVOLUTION_MOVE_REQUIREMENTS.put("bounsweet", "stomp");
        EVOLUTION_MOVE_REQUIREMENTS.put("clobbopus", "taunt");
        EVOLUTION_MOVE_REQUIREMENTS.put("dipplin", "dragoncheer");
        EVOLUTION_MOVE_REQUIREMENTS.put("dunsparce", "hyperdrill");
        EVOLUTION_MOVE_REQUIREMENTS.put("girafarig", "twinbeam");
        EVOLUTION_MOVE_REQUIREMENTS.put("lickitung", "rollout");
        EVOLUTION_MOVE_REQUIREMENTS.put("mankey", "ragefist");
        EVOLUTION_MOVE_REQUIREMENTS.put("primeape", "ragefist");
        EVOLUTION_MOVE_REQUIREMENTS.put("mimejr", "mimic");
        EVOLUTION_MOVE_REQUIREMENTS.put("piloswine", "ancientpower");
        EVOLUTION_MOVE_REQUIREMENTS.put("poipole", "dragonpulse");
        EVOLUTION_MOVE_REQUIREMENTS.put("qwilfish", "barbbarrage");
        EVOLUTION_MOVE_REQUIREMENTS.put("stantler", "psyshieldbash");
        EVOLUTION_MOVE_REQUIREMENTS.put("steenee", "stomp");
        EVOLUTION_MOVE_REQUIREMENTS.put("swinub", "ancientpower");
        EVOLUTION_MOVE_REQUIREMENTS.put("tangela", "ancientpower");
        EVOLUTION_MOVE_REQUIREMENTS.put("yanma", "ancientpower");
    }

    private LearnsetRoller() {
    }

    public static CustomLearnset roll(String speciesName, Random random) {
        CustomLearnset learnset = new CustomLearnset();
        List<String> pool = SpeciesPool.moveNames();
        if (pool.isEmpty()) {
            return learnset;
        }

        Set<String> used = new HashSet<>();

        learnset.addLevelUpMove(1, "tackle");
        used.add("tackle");

        boolean plantedEvolutionMove = false;
        String requiredMove = speciesName == null
                ? null
                : EVOLUTION_MOVE_REQUIREMENTS.get(speciesName.toLowerCase(Locale.ROOT));
        if (requiredMove != null && !requiredMove.isBlank() && !used.contains(requiredMove)) {
            learnset.addLevelUpMove(5, requiredMove);
            used.add(requiredMove);
            plantedEvolutionMove = true;
        }

        for (int level : LEVEL_UP_LEVELS) {
            if (level == 5 && plantedEvolutionMove) {
                continue;
            }
            String move = unusedMove(pool, used, random);
            if (move != null) {
                learnset.addLevelUpMove(level, move);
                used.add(move.toLowerCase(Locale.ROOT));
            }
        }
        for (int i = 0; i < TM_MOVE_COUNT; i++) {
            String move = unusedMove(pool, used, random);
            if (move != null) {
                learnset.addTmMove(move);
                used.add(move.toLowerCase(Locale.ROOT));
            }
        }
        for (int i = 0; i < EGG_MOVE_COUNT; i++) {
            String move = unusedMove(pool, used, random);
            if (move != null) {
                learnset.addEggMove(move);
                used.add(move.toLowerCase(Locale.ROOT));
            }
        }
        return learnset;
    }

    private static String unusedMove(List<String> pool, Set<String> used, Random random) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String move = pool.get(random.nextInt(pool.size()));
            if (!used.contains(move.toLowerCase(Locale.ROOT))) {
                return move;
            }
        }
        return null;
    }
}
