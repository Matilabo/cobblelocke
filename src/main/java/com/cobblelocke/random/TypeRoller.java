package com.cobblelocke.random;

import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.util.SpeciesPool;

import java.util.List;
import java.util.Random;

public final class TypeRoller {
    private TypeRoller() {
    }

    public static String randomType(Random random) {
        List<String> types = SpeciesPool.typeNames();
        return types.get(random.nextInt(types.size()));
    }

    public static String[] roll(Random random, CobblelockeConfig config, String[] originalTypes) {
        return roll(random, config.typesKeepOneType, originalTypes, config.typesRandomCount);
    }

    public static String[] roll(Random random, boolean keepOneOriginal, String[] originalTypes, int typeCount) {
        List<String> pool = SpeciesPool.typeNames();
        if (pool.size() < 2) {
            return new String[]{pool.isEmpty() ? "normal" : pool.get(0)};
        }

        if (keepOneOriginal && originalTypes != null && originalTypes.length > 0) {
            String kept = originalTypes[random.nextInt(originalTypes.length)];
            boolean wantsSecond = typeCount == 2
                    || (typeCount == 0 && originalTypes.length > 1 && random.nextBoolean());
            if (!wantsSecond) {
                return new String[]{kept};
            }
            return new String[]{kept, differentFrom(random, pool, kept)};
        }

        String first = randomType(random);
        boolean dualType = switch (typeCount) {
            case 1 -> false;
            case 2 -> true;

            default -> random.nextFloat() < 0.6f;
        };
        if (!dualType) {
            return new String[]{first};
        }
        return new String[]{first, differentFrom(random, pool, first)};
    }

    private static String differentFrom(Random random, List<String> pool, String exclude) {
        for (int attempt = 0; attempt < 64; attempt++) {
            String candidate = pool.get(random.nextInt(pool.size()));
            if (!candidate.equalsIgnoreCase(exclude)) {
                return candidate;
            }
        }
        for (String candidate : pool) {
            if (!candidate.equalsIgnoreCase(exclude)) {
                return candidate;
            }
        }
        return exclude;
    }
}
