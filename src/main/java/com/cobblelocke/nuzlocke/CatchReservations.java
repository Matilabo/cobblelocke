package com.cobblelocke.nuzlocke;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.data.BiomeInstanceKey;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CatchReservations {
    private static final long EXPIRY_MILLIS = 60_000L;

    public record Reservation(
            UUID playerId,
            UUID targetPokemonId,
            String biomeId,
            BiomeInstanceKey instance,
            Set<String> evolutionLine,
            boolean shinyPass,
            long createdAt) {
    }

    private static final Map<UUID, Reservation> BY_TARGET = new ConcurrentHashMap<>();

    private CatchReservations() {
    }

    public static void clear() {
        BY_TARGET.clear();
    }

    public static void reserve(Reservation reservation, EmptyPokeBallEntity ball) {
        BY_TARGET.put(reservation.targetPokemonId(), reservation);
        if (ball == null) {
            return;
        }
        try {
            ball.getCaptureFuture().whenComplete((success, error) -> {
                MinecraftServer server = Cobblelocke.getServer();
                if (server == null || (error == null && Boolean.TRUE.equals(success))) {
                    return;
                }
                server.execute(() -> release(reservation.targetPokemonId()));
            });
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not watch a capture: {}", e.toString());
        }
    }

    public static Reservation take(UUID targetPokemonId) {
        return targetPokemonId == null ? null : BY_TARGET.remove(targetPokemonId);
    }

    public static void release(UUID targetPokemonId) {
        if (targetPokemonId != null) {
            BY_TARGET.remove(targetPokemonId);
        }
    }

    public static List<Reservation> activeFor(UUID playerId, UUID excludingTarget) {
        long now = System.currentTimeMillis();
        BY_TARGET.values().removeIf(reservation -> now - reservation.createdAt() > EXPIRY_MILLIS);

        List<Reservation> out = new ArrayList<>();
        for (Reservation reservation : BY_TARGET.values()) {
            if (!reservation.playerId().equals(playerId)) {
                continue;
            }
            if (reservation.targetPokemonId().equals(excludingTarget)) {
                continue;
            }
            out.add(reservation);
        }
        return out;
    }
}
