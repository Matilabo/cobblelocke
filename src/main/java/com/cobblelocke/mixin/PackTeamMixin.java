package com.cobblelocke.mixin;

import com.cobblelocke.random.BattleSpeciesRegistry;
import com.cobblelocke.random.PokemonStamper;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BattleRegistry.class, remap = false)
public class PackTeamMixin {
    @Redirect(
            method = "packTeam(Ljava/util/List;)Ljava/lang/String;",
            at = @At(value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;showdownId()Ljava/lang/String;"),
            require = 0)
    private String cobblelocke$showdownId(Pokemon pokemon) {
        String original = pokemon.showdownId();
        try {
            String[] types = PokemonStamper.storedTypes(pokemon);
            if (types == null || PokemonStamper.isSpecialForm(pokemon)) {
                return original;
            }
            String customId = BattleSpeciesRegistry.idFor(original, types);

            return BattleSpeciesRegistry.isRegistered(customId) ? customId : original;
        } catch (Exception e) {
            return original;
        }
    }
}
