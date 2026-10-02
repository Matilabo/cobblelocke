package com.cobblelocke.client.mixin;

import com.cobblelocke.random.TmTables;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.client.gui.tmmachine.TMMachineScreen;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = TMMachineScreen.class, remap = false)
public class TmMachineScreenMixin {
    @ModifyExpressionValue(
            method = "setMoveList$lambda$0$0",
            at = @At(value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;getTmMoves()Ljava/util/List;"),
            require = 0)
    private static List<MoveTemplate> cobblelocke$listedTms(List<MoveTemplate> original,
                                                            @Local(argsOnly = true) Pokemon pokemon) {
        return TmTables.learnable(pokemon, original);
    }

    @ModifyExpressionValue(
            method = "canLearnTMMove",
            at = @At(value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;getTmMoves()Ljava/util/List;"),
            require = 0)
    private List<MoveTemplate> cobblelocke$learnableTms(List<MoveTemplate> original,
                                                        @Local(argsOnly = true) Pokemon pokemon) {
        return TmTables.learnable(pokemon, original);
    }
}
