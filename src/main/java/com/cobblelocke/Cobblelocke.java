package com.cobblelocke;

import net.minecraft.util.Formatting;
import com.cobblelocke.util.Lang;
import com.cobblelocke.command.CobblelockeCommand;
import com.cobblelocke.compat.GymLeaders;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.config.ConfigFiles;
import com.cobblelocke.data.CobblelockeState;
import com.cobblelocke.eventlock.EventLockService;
import com.cobblelocke.net.CobblelockeNetworking;
import com.cobblelocke.nuzlocke.CatchReservations;
import com.cobblelocke.nuzlocke.NicknameService;
import com.cobblelocke.nuzlocke.NuzlockeService;
import com.cobblelocke.random.Gimmicks;
import com.cobblelocke.random.GlobalPools;
import com.cobblelocke.random.HeldItemRoller;
import com.cobblelocke.random.RandomizerService;
import com.cobblelocke.random.ShinyRate;
import com.cobblelocke.util.SpeciesPool;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Cobblelocke implements ModInitializer {
    public static final String MOD_ID = "cobblelocke";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer server;

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        CobblelockeNetworking.register();
        RandomizerService.register();
        NuzlockeService.register();
        com.cobblelocke.nuzlocke.CatchCheck.register();

        if (FabricLoader.getInstance().isModLoaded("cobblemonraiddens")) {
            com.cobblelocke.compat.RaidDensCompat.register();
        }

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof PokemonEntity pokemonEntity) {
                RandomizerService.randomizeWild(pokemonEntity);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(Cobblelocke::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(startedServer -> onServerStopping());
        ServerTickEvents.END_SERVER_TICK.register(Cobblelocke::onServerTick);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) -> CobblelockeCommand.register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joinedServer) ->
                joinedServer.execute(() -> onPlayerJoin(handler.getPlayer())));

        LOGGER.info("Cobblelocke loaded");
    }

    public static MinecraftServer getServer() {
        return server;
    }

    private static void onServerStarted(MinecraftServer startedServer) {
        server = startedServer;
        SpeciesPool.invalidate();
        HeldItemRoller.invalidate();
        Gimmicks.invalidate();
        GymLeaders.invalidate();

        GymLeaders.known();
        EventLockService.reset();
        com.cobblelocke.nuzlocke.CatchCheck.reset();

        Path worldDirectory = startedServer.getSavePath(WorldSavePath.ROOT);
        long worldSeed = startedServer.getOverworld().getSeed();
        GlobalPools.init(worldDirectory, worldSeed);

        applySavedRulesToNewWorld(CobblelockeState.get(startedServer));
        ShinyRate.apply(config());
        LOGGER.info("Cobblelocke ready (world seed {})", worldSeed);
    }

    private static void applySavedRulesToNewWorld(CobblelockeState state) {
        ConfigFiles.loadPresets();
        ConfigFiles.Saved saved = ConfigFiles.loadConfig();
        if (state.getConfig().configured || saved.askOnFirstJoin()) {
            return;
        }
        CobblelockeConfig rules = saved.config().copy();
        rules.configured = true;
        rules.runActive = true;
        state.saveConfig(rules);
        GlobalPools pools = GlobalPools.get();
        if (pools != null && rules.anyGlobalPool()) {
            pools.prefillAll(rules);
        }
        LOGGER.info("New world started with the rules from config/cobblelocke/config.json5 ({})", rules.preset);
    }

    private static void onServerStopping() {
        GlobalPools.shutdown();
        EventLockService.reset();
        CatchReservations.clear();
        ShinyRate.restore();
        server = null;
    }

    private static void onServerTick(MinecraftServer tickingServer) {
        try {
            NuzlockeService.tick();
            RandomizerService.tick(tickingServer);
            EventLockService.tick(tickingServer);
            com.cobblelocke.nuzlocke.CatchCheck.tick(tickingServer);
        } catch (Exception e) {
            LOGGER.error("Cobblelocke tick failed: {}", e.toString());
        }
    }

    private static void onPlayerJoin(ServerPlayerEntity player) {
        CobblelockeState state = state();
        if (state == null || player == null) {
            return;
        }
        state.claimConfigOwner(player);

        MinecraftServer current = server;
        if (current == null) {
            return;
        }

        current.execute(() -> current.execute(() -> {
            NicknameService.resendPending(player);
            if (state.getPlayer(player.getUuid()).isStarterChoicePending()) {
                CobblelockeCommand.offerStarterAgain(player, state);
            }
            if (state.getConfig().configured || !state.isAdmin(player)) {
                return;
            }
            player.sendMessage(Lang.tr("welcome", "§dWelcome to Cobblelocke. §7Configure your run below, or "
                    + "reopen it any time with §f/cobblelocke config§7."));
            CobblelockeNetworking.sendOpenConfig(player);
        }));
    }

    public static CobblelockeState state() {
        MinecraftServer current = server;
        return current == null ? null : CobblelockeState.get(current);
    }

    public static CobblelockeConfig config() {
        CobblelockeState state = state();
        return state == null ? new CobblelockeConfig() : state.getConfig();
    }

    public static boolean canEditConfig(ServerPlayerEntity player) {
        CobblelockeState state = state();
        return state != null && state.canEditConfig(player);
    }
}
