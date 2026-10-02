package com.cobblelocke.mixin;

import com.cobblelocke.random.StarterRandomizer;
import com.cobblemon.mod.common.config.starter.StarterCategory;
import com.cobblemon.mod.common.starter.CobblemonStarterHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = CobblemonStarterHandler.class, remap = false)
public class StarterHandlerMixin {
    @Inject(method = "getStarterList", at = @At("RETURN"), cancellable = true, require = 0)
    private void cobblelocke$randomizeStarters(ServerPlayerEntity player,
                                               CallbackInfoReturnable<List<StarterCategory>> cir) {
        List<StarterCategory> replacement = StarterRandomizer.randomize(player, cir.getReturnValue());
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
