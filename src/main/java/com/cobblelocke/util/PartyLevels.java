package com.cobblelocke.util;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Random;

public final class PartyLevels {
    public static final int RANDOM_UP_TO_HIGHEST = 0;
    public static final int CLOSE_TO_HIGHEST = 1;
    public static final int TRUE_RANDOM = 2;
    public static final int MATCH_HIGHEST = 3;

    private static final int CLOSE_SPREAD = 5;

    private PartyLevels() {
    }

    public static int highestInParty(ServerPlayerEntity player) {
        int highest = 1;
        try {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                highest = Math.max(highest, pokemon.getLevel());
            }
        } catch (Exception ignored) {
        }
        return Math.min(100, Math.max(1, highest));
    }

    public static int forMode(int mode, ServerPlayerEntity player, Random random) {
        if (mode == TRUE_RANDOM) {
            return 1 + random.nextInt(100);
        }
        int highest = highestInParty(player);
        return switch (mode) {
            case MATCH_HIGHEST -> highest;
            case CLOSE_TO_HIGHEST -> {
                int lowest = Math.max(1, highest - CLOSE_SPREAD);
                yield lowest + random.nextInt(highest - lowest + 1);
            }

            default -> 1 + random.nextInt(highest);
        };
    }
}
