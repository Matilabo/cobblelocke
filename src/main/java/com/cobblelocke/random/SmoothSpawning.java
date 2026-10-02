package com.cobblelocke.random;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.data.CobblelockeState;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;

import java.util.ArrayList;
import java.util.List;

public final class SmoothSpawning {
    private SmoothSpawning() {
    }

    public static boolean enabled() {
        CobblelockeState state = Cobblelocke.state();
        return state != null && state.getConfig().runActive && state.getConfig().smoothSpawning;
    }

    public static List<SpawnDetail> withoutHerds(List<SpawnDetail> details) {
        if (details == null || details.isEmpty() || !enabled()) {
            return details;
        }
        boolean anyHerd = false;
        for (SpawnDetail detail : details) {
            if (detail instanceof PokemonHerdSpawnDetail) {
                anyHerd = true;
                break;
            }
        }
        if (!anyHerd) {
            return details;
        }
        List<SpawnDetail> kept = new ArrayList<>(details.size());
        for (SpawnDetail detail : details) {
            if (!(detail instanceof PokemonHerdSpawnDetail)) {
                kept.add(detail);
            }
        }
        return kept;
    }
}
