package com.cobblelocke.mixin;

import com.cobblelocke.random.SmoothSpawning;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = SpawnPool.class, remap = false)
public class SpawnPoolMixin {
    @Inject(method = "retrieve", at = @At("RETURN"), cancellable = true, require = 0)
    private void cobblelocke$smoothSpawning(String bucket, SpawnablePosition position,
                                            CallbackInfoReturnable<List<SpawnDetail>> cir) {
        List<SpawnDetail> filtered = SmoothSpawning.withoutHerds(cir.getReturnValue());
        if (filtered != cir.getReturnValue()) {
            cir.setReturnValue(filtered);
        }
    }
}
