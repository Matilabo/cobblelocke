package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblemon.mod.common.Cobblemon;

public final class ShinyRate {
    private static Float original = null;

    private ShinyRate() {
    }

    public static void apply(CobblelockeConfig config) {
        try {
            var cobblemonConfig = Cobblemon.INSTANCE.getConfig();
            if (config.runActive && config.overrideShinyRate) {
                if (original == null) {
                    original = cobblemonConfig.getShinyRate();
                }
                cobblemonConfig.setShinyRate(Math.max(1, config.shinyRate));
            } else if (original != null) {
                cobblemonConfig.setShinyRate(original);
                original = null;
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not apply the shiny rate override: {}", e.toString());
        }
    }

    public static void restore() {
        try {
            if (original != null) {
                Cobblemon.INSTANCE.getConfig().setShinyRate(original);
            }
        } catch (Exception ignored) {
        }
        original = null;
    }
}
