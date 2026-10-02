package com.cobblelocke.client;

import com.cobblelocke.client.cutscene.CutscenePlayer;
import com.cobblelocke.client.gui.CobblelockeConfigScreen;
import com.cobblelocke.client.gui.NicknameScreen;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.net.CutscenePayload;
import com.cobblelocke.net.NicknamePromptPayload;
import com.cobblelocke.net.OpenConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Deque;

public class CobblelockeClient implements ClientModInitializer {
    private static final Deque<NicknamePromptPayload> NICKNAMES = new ArrayDeque<>();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenConfigPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    CobblelockeConfig config = CobblelockeConfig.fromJson(payload.configJson());
                    MinecraftClient.getInstance().setScreen(new CobblelockeConfigScreen(
                            config, payload.presetsJson(), payload.canEdit(), payload.raidDensInstalled()));
                }));

        ClientPlayNetworking.registerGlobalReceiver(CutscenePayload.ID, (payload, context) ->
                context.client().execute(() -> CutscenePlayer.play(payload.clipId(), true)));

        ClientPlayNetworking.registerGlobalReceiver(NicknamePromptPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    NICKNAMES.removeIf(queued -> queued.pokemonId().equals(payload.pokemonId()));
                    NICKNAMES.add(payload);
                }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!NICKNAMES.isEmpty() && client.player != null && client.currentScreen == null
                    && !CutscenePlayer.isPlaying()) {
                NicknamePromptPayload next = NICKNAMES.poll();
                client.setScreen(new NicknameScreen(next.pokemonId(), next.species()));
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> CutscenePlayer.renderHud(drawContext));

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterRender(screen).register((current, drawContext, mouseX, mouseY, delta) ->
                        CutscenePlayer.renderOverScreen(drawContext)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> {
                    CutscenePlayer.stop();
                    NICKNAMES.clear();
                }));
    }
}
