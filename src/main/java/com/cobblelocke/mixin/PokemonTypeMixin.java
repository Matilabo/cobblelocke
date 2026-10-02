package com.cobblelocke.mixin;

import com.cobblelocke.random.PokemonStamper;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Pokemon.class, remap = false)
public abstract class PokemonTypeMixin {
    @Inject(method = "getPrimaryType", at = @At("RETURN"), cancellable = true)
    private void cobblelocke$primaryType(CallbackInfoReturnable<ElementalType> cir) {
        String[] types = cobblelocke$types();
        if (types == null) {
            return;
        }
        ElementalType replacement = ElementalTypes.get(types[0]);
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }

    @Inject(method = "getSecondaryType", at = @At("RETURN"), cancellable = true)
    private void cobblelocke$secondaryType(CallbackInfoReturnable<ElementalType> cir) {
        String[] types = cobblelocke$types();
        if (types == null) {
            return;
        }
        if (types.length < 2) {
            cir.setReturnValue(null);
            return;
        }
        ElementalType replacement = ElementalTypes.get(types[1]);
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }

    private String[] cobblelocke$types() {
        Pokemon self = (Pokemon) (Object) this;
        if (PokemonStamper.isSpecialForm(self)) {
            return null;
        }
        return PokemonStamper.storedTypes(self);
    }
}
