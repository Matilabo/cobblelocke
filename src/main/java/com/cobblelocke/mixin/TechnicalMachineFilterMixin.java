package com.cobblelocke.mixin;

import com.cobblelocke.random.TmTables;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.tms.TechnicalMachine;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = TechnicalMachine.Companion.class, remap = false)
public class TechnicalMachineFilterMixin {
    @ModifyExpressionValue(
            method = "filterTms",
            at = @At(value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;tmLearnableMoves()Ljava/util/List;"),
            require = 0)
    private List<MoveTemplate> cobblelocke$tmTable(List<MoveTemplate> original,
                                                   @Local(argsOnly = true) Pokemon pokemon) {
        return TmTables.learnable(pokemon, original);
    }
}
