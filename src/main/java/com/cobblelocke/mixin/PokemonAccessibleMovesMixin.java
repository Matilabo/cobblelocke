package com.cobblelocke.mixin;

import com.cobblelocke.random.CustomLearnset;
import com.cobblelocke.random.PokemonStamper;
import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Mixin(value = Pokemon.class, remap = false)
public abstract class PokemonAccessibleMovesMixin {
    @Inject(method = "getAllAccessibleMoves", at = @At("RETURN"), cancellable = true)
    private void cobblelocke$accessibleMoves(CallbackInfoReturnable<Set<MoveTemplate>> cir) {
        Pokemon self = (Pokemon) (Object) this;
        CustomLearnset learnset = PokemonStamper.storedLearnset(self);
        if (learnset == null || learnset.isEmpty()) {
            return;
        }
        Set<MoveTemplate> accessible = new LinkedHashSet<>();
        for (String name : learnset.movesUpToLevel(self.getLevel())) {
            MoveTemplate template = Moves.getByName(name.toLowerCase(Locale.ROOT));
            if (template != null) {
                accessible.add(template);
            }
        }

        for (Move move : self.getMoveSet().getMoves()) {
            if (move != null) {
                accessible.add(move.getTemplate());
            }
        }
        for (BenchedMove benched : self.getBenchedMoves()) {
            if (benched.getMoveTemplate() != null) {
                accessible.add(benched.getMoveTemplate());
            }
        }
        cir.setReturnValue(accessible);
    }
}
