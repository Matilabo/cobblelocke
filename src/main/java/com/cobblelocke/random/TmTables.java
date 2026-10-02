package com.cobblelocke.random;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TmTables {
    private TmTables() {
    }

    public static List<MoveTemplate> learnable(Pokemon pokemon, List<MoveTemplate> original) {
        if (pokemon == null) {
            return original;
        }
        List<String> names = PokemonStamper.storedTms(pokemon);
        if (names == null) {
            CustomLearnset learnset = PokemonStamper.storedLearnset(pokemon);
            if (learnset != null && !learnset.getTmMoves().isEmpty()) {
                names = learnset.getTmMoves();
            }
        }
        if (names == null) {
            return original;
        }
        List<MoveTemplate> out = new ArrayList<>(names.size());
        for (String name : names) {
            MoveTemplate template = Moves.getByName(name.toLowerCase(Locale.ROOT));
            if (template != null) {
                out.add(template);
            }
        }
        return out;
    }
}
