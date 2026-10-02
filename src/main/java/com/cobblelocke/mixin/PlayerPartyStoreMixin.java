package com.cobblelocke.mixin;

import com.cobblelocke.nuzlocke.NuzlockeService;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerPartyStore.class, remap = false)
public class PlayerPartyStoreMixin {
    @Inject(method = "onSecondPassed", at = @At("HEAD"))
    private void cobblelocke$enforcePermadeath(ServerPlayerEntity player, CallbackInfo ci) {
        NuzlockeService.enforcePermadeath(player, (PlayerPartyStore) (Object) this);
    }
}
