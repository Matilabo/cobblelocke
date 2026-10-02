package com.cobblelocke.nuzlocke;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.data.CobblelockeState;
import com.cobblelocke.data.PlayerState;
import com.cobblelocke.net.NicknamePromptPayload;
import com.cobblelocke.net.SetNicknamePayload;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.UUID;

public final class NicknameService {
    public static final int MAX_LENGTH = 12;

    private NicknameService() {
    }

    public static void requestAfterCapture(ServerPlayerEntity player, Pokemon pokemon) {
        CobblelockeState state = Cobblelocke.state();
        MinecraftServer server = Cobblelocke.getServer();
        if (state == null || server == null) {
            return;
        }
        if (!ServerPlayNetworking.canSend(player, NicknamePromptPayload.ID)) {
            return;
        }
        PlayerState playerState = state.getPlayer(player.getUuid());
        playerState.addPendingNickname(pokemon.getUuid());
        state.markDirty();

        server.execute(() -> server.execute(() -> prompt(player, pokemon.getUuid())));
    }

    public static void resendPending(ServerPlayerEntity player) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        PlayerState playerState = state.getPlayer(player.getUuid());
        for (UUID pokemonId : new ArrayList<>(playerState.getPendingNicknames())) {
            prompt(player, pokemonId);
        }
    }

    private static void prompt(ServerPlayerEntity player, UUID pokemonId) {
        Pokemon pokemon = findOwned(player, pokemonId);
        CobblelockeState state = Cobblelocke.state();
        if (pokemon == null) {
            if (state != null) {
                state.getPlayer(player.getUuid()).removePendingNickname(pokemonId);
                state.markDirty();
            }
            return;
        }
        if (ServerPlayNetworking.canSend(player, NicknamePromptPayload.ID)) {
            ServerPlayNetworking.send(player, new NicknamePromptPayload(pokemonId, pokemon.getSpecies().getName()));
        }
    }

    public static void handleSubmit(ServerPlayerEntity player, SetNicknamePayload payload) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        PlayerState playerState = state.getPlayer(player.getUuid());
        if (!playerState.getPendingNicknames().contains(payload.pokemonId())) {
            return;
        }
        String name = clean(payload.name());
        Pokemon pokemon = findOwned(player, payload.pokemonId());
        if (pokemon == null) {
            playerState.removePendingNickname(payload.pokemonId());
            state.markDirty();
            return;
        }
        if (name.isEmpty()) {
            prompt(player, payload.pokemonId());
            return;
        }
        pokemon.setNickname(Text.literal(name));
        playerState.removePendingNickname(payload.pokemonId());
        state.markDirty();
        player.sendMessage(Text.literal("§aYour " + pokemon.getSpecies().getName() + " is now called §f" + name + "§a."));
    }

    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (!Character.isISOControl(c)) {
                out.append(c);
            }
        }
        String trimmed = out.toString().trim();
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }

    private static Pokemon findOwned(ServerPlayerEntity player, UUID pokemonId) {
        try {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                if (pokemon.getUuid().equals(pokemonId)) {
                    return pokemon;
                }
            }
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) {
                if (pokemon.getUuid().equals(pokemonId)) {
                    return pokemon;
                }
            }
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not search storage for a nickname: {}", e.toString());
        }
        return null;
    }
}
