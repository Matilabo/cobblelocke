package com.cobblelocke.mixin;

import com.cobblelocke.random.BattlePreparation;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.BattleStartResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BattleRegistry.class, remap = false)
public class BattleRegistryMixin {
    @Inject(
            method = "startBattle(Lcom/cobblemon/mod/common/battles/BattleFormat;"
                    + "Lcom/cobblemon/mod/common/battles/BattleSide;"
                    + "Lcom/cobblemon/mod/common/battles/BattleSide;Z)"
                    + "Lcom/cobblemon/mod/common/battles/BattleStartResult;",
            at = @At("HEAD"),
            require = 0)
    private static void cobblelocke$onStartBattle(BattleFormat format, BattleSide side1, BattleSide side2,
                                                  boolean canPreempt,
                                                  CallbackInfoReturnable<BattleStartResult> cir) {
        BattlePreparation.prepare(format, side1, side2);
    }

    @ModifyVariable(
            method = "startBattle(Lcom/cobblemon/mod/common/battles/BattleFormat;"
                    + "Lcom/cobblemon/mod/common/battles/BattleSide;"
                    + "Lcom/cobblemon/mod/common/battles/BattleSide;Z)"
                    + "Lcom/cobblemon/mod/common/battles/BattleStartResult;",
            at = @At("HEAD"),
            argsOnly = true,
            index = 0,
            require = 0)
    private static BattleFormat cobblelocke$doubleBattle(BattleFormat format, BattleFormat ignored,
                                                         BattleSide side1, BattleSide side2,
                                                         boolean canPreempt) {
        return BattlePreparation.toDoubles(format, side1, side2);
    }
}
