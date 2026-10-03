package com.cobblelocke.command;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.config.ConfigFiles;
import com.cobblelocke.config.ConfigOptions;
import com.cobblelocke.data.CobblelockeState;
import com.cobblelocke.data.PlayerState;
import com.cobblelocke.eventlock.EncounterAnimations;
import com.cobblelocke.eventlock.EventLockService;
import com.cobblelocke.net.CobblelockeNetworking;
import com.cobblelocke.random.GlobalPools;
import com.cobblelocke.random.PokemonStamper;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.player.GeneralPlayerData;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class CobblelockeCommand {
    private CobblelockeCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("cobblelocke")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player != null) {
                        player.sendMessage(Text.literal("§dCobblelocke §7- use §f/cobblelocke config"
                                + "§7 to open the settings menu."));
                    }
                    return 1;
                })
                .then(CommandManager.literal("config")
                        .requires(source -> allowed(source, true))
                        .executes(context -> openConfig(context.getSource())))
                .then(CommandManager.literal("status")
                        .requires(source -> allowed(source, false))
                        .executes(context -> showStatus(context.getSource())))
                .then(CommandManager.literal("start")
                        .requires(source -> allowed(source, false))
                        .executes(context -> setRunActive(context.getSource(), true)))
                .then(CommandManager.literal("stop")
                        .requires(source -> allowed(source, false))
                        .executes(context -> setRunActive(context.getSource(), false)))
                .then(CommandManager.literal("reset")
                        .requires(source -> allowed(source, false))
                        .executes(context -> resetRun(context.getSource(), null))
                        .then(CommandManager.argument("players", EntityArgumentType.players())
                                .executes(context -> resetRun(context.getSource(),
                                        EntityArgumentType.getPlayers(context, "players")))))
                .then(CommandManager.literal("spawncap")
                        .requires(source -> allowed(source, false))
                        .executes(context -> showSpawnCap(context.getSource()))
                        .then(CommandManager.literal("on")
                                .executes(context -> setSpawnCap(context.getSource(), true)))
                        .then(CommandManager.literal("off")
                                .executes(context -> setSpawnCap(context.getSource(), false))))
                .then(CommandManager.literal("export")
                        .requires(source -> allowed(source, false))
                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                .executes(context -> exportPreset(context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(CommandManager.literal("starterchoicereset")
                        .requires(source -> allowed(source, false))
                        .executes(context -> resetStarterChoice(context.getSource(), null))
                        .then(CommandManager.argument("players", EntityArgumentType.players())
                                .executes(context -> resetStarterChoice(context.getSource(),
                                        EntityArgumentType.getPlayers(context, "players")))))
                .then(CommandManager.literal("pools")
                        .requires(source -> allowed(source, false))
                        .then(CommandManager.literal("regenerate")
                                .executes(context -> regeneratePools(context.getSource()))))
                .then(CommandManager.literal("inspect")
                        .requires(source -> allowed(source, false))
                        .then(CommandManager.argument("species", StringArgumentType.word())
                                .executes(context -> inspect(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "species")))))
                .then(CommandManager.literal("set")
                        .requires(source -> allowed(source, false))
                        .then(CommandManager.argument("option", StringArgumentType.word())
                                .suggests(CobblelockeCommand::suggestOptions)
                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(context -> setOption(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "option"),
                                                StringArgumentType.getString(context, "value")))))));
    }

    private static final String DENIED =
            "Only the host or an operator can do that. Server owners can widen this with "
                    + "serverConfigMode in config/cobblelocke/config.json5.";

    private static boolean allowed(ServerCommandSource source, boolean opensTheMenu) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayer();
        } catch (Exception e) {
            return true;
        }
        if (player == null) {
            return true;
        }
        CobblelockeState state = Cobblelocke.state();
        return state == null || state.canUseCommand(player, opensTheMenu);
    }

    private static CompletableFuture<Suggestions> suggestOptions(
            CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        for (Field field : CobblelockeConfig.class.getFields()) {
            if (field.getType() == boolean.class || field.getType() == int.class
                    || field.getType() == List.class || field.getType() == String.class) {
                builder.suggest(field.getName());
            }
        }
        return builder.buildFuture();
    }

    private static int setOption(ServerCommandSource source, String option, String value) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        if (player != null && !state.canUseCommand(player, false)) {
            source.sendError(Text.literal(DENIED));
            return 0;
        }

        CobblelockeConfig config = state.getConfig().copy();
        Field field;
        try {
            field = CobblelockeConfig.class.getField(option);
        } catch (NoSuchFieldException e) {
            source.sendError(Text.literal("Unknown option: " + option));
            return 0;
        }

        try {
            if (field.getType() == boolean.class) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    source.sendError(Text.literal(option + " expects true or false."));
                    return 0;
                }
                field.setBoolean(config, Boolean.parseBoolean(value));
            } else if (field.getType() == int.class) {
                field.setInt(config, Integer.parseInt(value));
            } else if (field.getType() == List.class) {
                List<String> items = new ArrayList<>();
                if (!value.equalsIgnoreCase("none")) {
                    for (String part : value.split(",")) {
                        if (!part.isBlank()) {
                            items.add(part.trim());
                        }
                    }
                }
                field.set(config, items);
            } else if (field.getType() == String.class) {
                ConfigOptions.Spec spec = ConfigOptions.byKey(option);
                if (spec != null && spec.kind() == ConfigOptions.Kind.OPTION && !spec.ids().contains(value)) {
                    source.sendError(Text.literal(option + " expects one of: " + String.join(", ", spec.ids())));
                    return 0;
                }
                field.set(config, value);
                if ("animationPreset".equals(option)) {
                    config.applyAnimationPreset(value);
                } else if (ConfigOptions.ANIMATION_KEYS.contains(option)) {
                    config.animationPreset = "custom";
                }
            } else {
                source.sendError(Text.literal(option + " cannot be set from a command."));
                return 0;
            }
        } catch (NumberFormatException e) {
            source.sendError(Text.literal(option + " expects a whole number."));
            return 0;
        } catch (IllegalAccessException e) {
            source.sendError(Text.literal("Could not set " + option + "."));
            return 0;
        }

        config.configured = true;
        state.saveConfig(config);
        source.sendFeedback(() -> Text.literal("§aSet §f" + option + "§a to §f" + value + "§a."), true);
        return 1;
    }

    private static int inspect(ServerCommandSource source, String speciesName) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        Species species = PokemonSpecies.getByName(speciesName.toLowerCase(Locale.ROOT));
        if (species == null) {
            source.sendError(Text.literal("Unknown species: " + speciesName));
            return 0;
        }

        Pokemon sample = new Pokemon();
        sample.setSpecies(species);
        sample.setLevel(50);
        sample.initializeMoveset(true);
        PokemonStamper.stamp(sample, state.getConfig(), state.getConfigGeneration(),
                PokemonStamper.Context.WILD);

        String types = sample.getPrimaryType().getName()
                + (sample.getSecondaryType() != null ? " / " + sample.getSecondaryType().getName() : "");
        List<String> moves = new ArrayList<>();
        sample.getMoveSet().getMoves().forEach(move -> moves.add(move.getName()));

        source.sendFeedback(() -> Text.literal("§d" + species.getName() + "§7 at level 50"), false);
        source.sendFeedback(() -> Text.literal("§7  Types: §f" + types), false);
        source.sendFeedback(() -> Text.literal("§7  Ability: §f" + sample.getAbility().getName()), false);
        source.sendFeedback(() -> Text.literal("§7  Moves: §f" + String.join(", ", moves)), false);
        return 1;
    }

    private static int openConfig(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Only a player can open the config screen."));
            return 0;
        }
        CobblelockeState state = Cobblelocke.state();
        if (state != null && !state.canUseCommand(player, true)) {
            source.sendError(Text.literal(DENIED));
            return 0;
        }
        CobblelockeNetworking.sendOpenConfig(player);
        return 1;
    }

    private static int setRunActive(ServerCommandSource source, boolean active) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        if (player != null && !state.canUseCommand(player, false)) {
            source.sendError(Text.literal(DENIED));
            return 0;
        }
        CobblelockeConfig config = state.getConfig().copy();
        config.runActive = active;
        config.configured = true;
        state.saveConfig(config);

        if (active) {
            GlobalPools pools = GlobalPools.get();
            CobblelockeConfig saved = state.getConfig();
            if (pools != null && saved.anyGlobalPool()) {
                pools.prefillAll(saved);
            }
        }
        source.sendFeedback(() -> Text.literal(active
                ? "§d★ Cobblelocke run started."
                : "§7Cobblelocke run stopped."), true);
        return 1;
    }

    private static int resetRun(ServerCommandSource source, Collection<ServerPlayerEntity> targets) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        MinecraftServer server = source.getServer();
        if (state == null) {
            return 0;
        }
        if (player != null && !state.canUseCommand(player, false)) {
            source.sendError(Text.literal(DENIED));
            return 0;
        }
        if (targets == null) {
            state.resetRun();
            EventLockService.reset();
            source.sendFeedback(() -> Text.literal("§cCobblelocke run progress reset for everyone. "
                    + "Catch history, permadeath records, event-lock progress and the shared "
                    + "spawn-cap memory are cleared."), true);
            return Math.max(1, server.getPlayerManager().getCurrentPlayerCount());
        }
        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity target : targets) {
            state.resetRun(target.getUuid());
            EventLockService.reset(target.getUuid());
            names.add(target.getName().getString());
            target.sendMessage(Text.literal("§cYour Cobblelocke run was reset."));
        }
        source.sendFeedback(() -> Text.literal("§cCobblelocke run progress reset for §f"
                + String.join(", ", names) + "§c. §7Nobody else was touched; run "
                + "§f/cobblelocke reset§7 with no player to clear the whole world."), true);
        return names.size();
    }

    private static int setSpawnCap(ServerCommandSource source, boolean on) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        if (player != null && !state.canUseCommand(player, false)) {
            source.sendError(Text.literal(DENIED));
            return 0;
        }
        if (on) {
            CobblelockeConfig config = state.getConfig().copy();
            config.spawnCapAlwaysOn = true;
            state.saveConfig(config, false);
        } else {
            state.turnSpawnCapOff();
        }
        ConfigFiles.writeConfig(state.getConfig(), StatusReport.asksOnFirstJoin());

        CobblelockeConfig config = state.getConfig();
        String message;
        if (on) {
            message = config.nuzlockeModeEnabled && config.capSpawningPerRegionChunks > 0
                    ? "§aSpawn cap on. §7The Cap Spawning rules now apply even before a run starts."
                    : "§aSpawn cap on. §7It takes effect once Nuzlocke Mode is on and Cap Spawning Per "
                            + "Region by Size is set.";
        } else {
            message = "§eSpawn cap off. §7It stays off until the next run starts, and every remembered "
                    + "region was forgotten.";
        }
        source.sendFeedback(() -> Text.literal(message), true);
        return 1;
    }

    private static int showSpawnCap(ServerCommandSource source) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        CobblelockeConfig config = state.getConfig();
        String status = state.isSpawnCapActive() ? "§aactive" : "§7not active";
        String why;
        if (!config.nuzlockeModeEnabled || config.capSpawningPerRegionChunks <= 0) {
            why = "Nuzlocke Mode and Cap Spawning Per Region by Size must both be on.";
        } else if (config.spawnCapAlwaysOn) {
            why = "spawnCapAlwaysOn is on, so it applies even before a run.";
        } else if (state.hasRunEverStarted() || config.runActive) {
            why = "a run has started on this world, so it stays on through stops and resets.";
        } else {
            why = "no run has started yet. Use /cobblelocke spawncap on to apply it now.";
        }
        source.sendFeedback(() -> Text.literal("§7Spawn cap: " + status + "§7, " + why), false);
        if (config.capSpawningPerRegionChunks > 0) {
            source.sendFeedback(() -> Text.literal("§7  " + config.capSpawningPerRegionCount
                    + " per " + config.capSpawningPerRegionChunks + " chunk region, "
                    + (config.capSpawningPerRegionByPlayer ? "counted per player" : "shared by everyone")
                    + (config.capSpawningPerRegionMemory ? ", with memory" : "")), false);
        }
        return 1;
    }

    private static int exportPreset(ServerCommandSource source, String name) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        if (player != null && !state.canUseCommand(player, false)) {
            source.sendError(Text.literal(DENIED));
            return 0;
        }
        String wanted = name.trim();
        if (wanted.isEmpty()) {
            source.sendError(Text.literal("Give the preset a name."));
            return 0;
        }
        ConfigFiles.ExportResult result = ConfigFiles.exportPreset(wanted, state.getConfig());
        String message = CobblelockeNetworking.exportMessage(wanted, result);
        if (result == ConfigFiles.ExportResult.FAILED) {
            source.sendError(Text.literal(message));
            return 0;
        }
        source.sendFeedback(() -> Text.literal(message), true);
        return 1;
    }

    private static int resetStarterChoice(ServerCommandSource source,
                                          Collection<ServerPlayerEntity> targets) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        if (player != null && !state.canUseCommand(player, false)) {
            source.sendError(Text.literal(DENIED));
            return 0;
        }
        Collection<ServerPlayerEntity> chosen = targets;
        if (chosen == null) {
            if (player == null) {
                source.sendError(Text.literal("Name a player, or use @a for everyone."));
                return 0;
            }
            chosen = List.of(player);
        }

        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity target : chosen) {
            if (offerStarterAgain(target, state)) {
                names.add(target.getName().getString());
            }
        }
        if (names.isEmpty()) {
            source.sendError(Text.literal("Could not reopen the starter choice."));
            return 0;
        }
        state.markDirty();
        source.sendFeedback(() -> Text.literal("§aStarter choice reopened for §f"
                + String.join(", ", names) + "§a."), true);
        return names.size();
    }

    private static boolean offerStarterAgain(ServerPlayerEntity target, CobblelockeState state) {
        try {
            GeneralPlayerData data = Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(target);
            data.setStarterPrompted(false);
            data.setStarterSelected(false);
            data.setStarterLocked(false);
            Cobblemon.INSTANCE.getPlayerDataManager().saveSingle(data,
                    PlayerInstancedDataStoreTypes.INSTANCE.getGENERAL());

            PlayerState playerState = state.getPlayer(target.getUuid());
            playerState.setHasChosenStarter(false);

            Cobblemon.INSTANCE.getStarterHandler().requestStarterChoice(target);
            target.sendMessage(Text.literal("§d★ You can choose a starter again."));
            return true;
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not reopen the starter choice for {}: {}",
                    target.getName().getString(), e.toString());
            return false;
        }
    }

    private static int regeneratePools(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        GlobalPools pools = GlobalPools.get();
        if (state == null || pools == null) {
            return 0;
        }
        if (player != null && !state.canUseCommand(player, false)) {
            source.sendError(Text.literal(DENIED));
            return 0;
        }
        pools.reset();
        pools.prefillAll(state.getConfig());

        state.saveConfig(state.getConfig().copy());
        source.sendFeedback(() -> Text.literal("§aGlobal pools regenerated and written to "
                + "§fcobblelocke/global_pools.json§a."), true);
        return 1;
    }

    private static int showStatus(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        CobblelockeConfig config = state.getConfig();

        source.sendFeedback(() -> Text.literal("§5═══════ §d§lCobblelocke§5 ═══════"), false);
        source.sendFeedback(() -> Text.literal("§7Run: " + (config.runActive ? "§aACTIVE" : "§cinactive")), false);

        if (player != null) {
            PlayerState playerState = state.getPlayer(player.getUuid());
            source.sendFeedback(() -> Text.literal("§7Starter chosen: "
                    + (playerState.hasChosenStarter() ? "§ayes" : "§cno")), false);
            if (config.firstCatchEventLocked) {
                source.sendFeedback(() -> Text.literal("§7Event lock: "
                        + (playerState.hasLeftStartingBiome()
                        ? "§acatching unlocked"
                        : "§clocked §7- leave your starting biome")), false);
            }
        }

        for (String line : StatusReport.of(config, StatusReport.asksOnFirstJoin())) {
            source.sendFeedback(() -> Text.literal(line), false);
        }
        source.sendFeedback(() -> Text.literal("§5══════════════════════════"), false);
        return 1;
    }

    private static String flag(String label, boolean enabled) {
        return (enabled ? "§a" : "§8") + label;
    }
}
