package com.cobblelocke.compat;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.Cancelable;
import com.cobblemon.mod.common.api.reactive.Observable;
import kotlin.Unit;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.lang.reflect.Method;

public final class RaidDensCompat {
    private RaidDensCompat() {
    }

    @SuppressWarnings("unchecked")
    public static void register() {
        try {
            Class<?> events = Class.forName("com.necro.raid.dens.common.events.RaidEvents");
            Observable<Object> rewards = (Observable<Object>) events.getField("REWARD_POKEMON").get(null);
            rewards.subscribe(Priority.HIGHEST, event -> {
                onReward(event);
                return Unit.INSTANCE;
            });
            Cobblelocke.LOGGER.info("Cobblemon Raid Dens support enabled");
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Cobblemon Raid Dens is installed but its reward event could not be found; "
                    + "'Disable Raid Cobblemon Catch' will not work: {}", e.toString());
        }
    }

    private static void onReward(Object event) {
        CobblelockeConfig config = Cobblelocke.config();
        if (!config.runActive || !config.disableRaidCatch || !(event instanceof Cancelable cancelable)) {
            return;
        }
        cancelable.cancel();
        try {
            Method getPlayer = event.getClass().getMethod("getPlayer");
            if (getPlayer.invoke(event) instanceof ServerPlayerEntity player) {
                player.sendMessage(Text.literal("§cRaid Pokémon cannot be caught in this run. "
                        + "(Disable Raid Cobblemon Catch)"));
            }
        } catch (Exception ignored) {
        }
    }
}
