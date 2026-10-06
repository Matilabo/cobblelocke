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
import com.cobblelocke.nuzlocke.CatchCheck;
import com.cobblelocke.util.Lang;
import net.minecraft.util.Formatting;
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
                        player.sendMessage(Lang.tr("command.root",
                                "§dCobblelocke §7- use §f/cobblelocke config§7 to open the settings menu."));
                    }
                    return 1;
                })
                .then(CommandManager.literal("config")
                        .requires(source -> allowed(source, true))
                        .executes(context -> openConfig(context.getSource())))
                .then(CommandManager.literal("status")
                        .executes(context -> showStatus(context.getSource())))
                .then(CommandManager.literal("here")
                        .executes(context -> showHere(context.getSource())))
                .then(CommandManager.literal("hints")
                        .executes(context -> showHints(context.getSource()))
                        .then(CommandManager.literal("on")
                                .executes(context -> setHints(context.getSource(), true)))
                        .then(CommandManager.literal("off")
                                .executes(context -> setHints(context.getSource(), false))))
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

    private static Text denied() {
        return Lang.tr("command.denied", "Only the host or an operator can do that. Server owners can widen "
                + "this with serverConfigMode in config/cobblelocke/config.json5.");
    }

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
            source.sendError(denied());
            return 0;
        }

        CobblelockeConfig config = state.getConfig().copy();
        Field field;
        try {
            field = CobblelockeConfig.class.getField(option);
        } catch (NoSuchFieldException e) {
            source.sendError(Lang.tr("command.set.unknown", "Unknown option: %s", option));
            return 0;
        }

        try {
            if (field.getType() == boolean.class) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    source.sendError(Lang.tr("command.set.boolean", "%s expects true or false.", option));
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
                    source.sendError(Lang.tr("command.set.one_of", "%1$s expects one of: %2$s", option,
                            String.join(", ", spec.ids())));
                    return 0;
                }
                field.set(config, value);
                if ("animationPreset".equals(option)) {
                    config.applyAnimationPreset(value);
                } else if (ConfigOptions.ANIMATION_KEYS.contains(option)) {
                    config.animationPreset = "custom";
                }
            } else {
                source.sendError(Lang.tr("command.set.unsupported", "%s cannot be set from a command.", option));
                return 0;
            }
        } catch (NumberFormatException e) {
            source.sendError(Lang.tr("command.set.number", "%s expects a whole number.", option));
            return 0;
        } catch (IllegalAccessException e) {
            source.sendError(Lang.tr("command.set.failed", "Could not set %s.", option));
            return 0;
        }

        config.configured = true;
        state.saveConfig(config);
        source.sendFeedback(() -> Lang.tr("command.set.done", "§aSet %1$s§a to %2$s§a.",
                Lang.hl(option, Formatting.WHITE), Lang.hl(value, Formatting.WHITE)), true);
        return 1;
    }

    private static int inspect(ServerCommandSource source, String speciesName) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        Species species = PokemonSpecies.getByName(speciesName.toLowerCase(Locale.ROOT));
        if (species == null) {
            source.sendError(Lang.tr("command.inspect.unknown", "Unknown species: %s", speciesName));
            return 0;
        }

        Pokemon sample = new Pokemon();
        sample.setSpecies(species);
        sample.setLevel(50);
        sample.initializeMoveset(true);
        PokemonStamper.stamp(sample, state.getConfig(), state.getConfigGeneration(),
                PokemonStamper.Context.WILD);

        List<Text> typeNames = new ArrayList<>();
        typeNames.add(sample.getPrimaryType().getDisplayName());
        if (sample.getSecondaryType() != null) {
            typeNames.add(sample.getSecondaryType().getDisplayName());
        }
        List<Text> moves = new ArrayList<>();
        sample.getMoveSet().getMoves().forEach(move -> moves.add(move.getDisplayName()));
        Text types = Lang.join(typeNames, Text.literal(" / "));
        Text ability = Text.translatable(sample.getAbility().getDisplayName());

        source.sendFeedback(() -> Lang.tr("command.inspect.header", "§d%s§7 at level 50", Lang.species(species)), false);
        source.sendFeedback(() -> Lang.tr("command.inspect.types", "§7  Types: %s", Lang.hl(types, Formatting.WHITE)), false);
        source.sendFeedback(() -> Lang.tr("command.inspect.ability", "§7  Ability: %s",
                Lang.hl(ability, Formatting.WHITE)), false);
        source.sendFeedback(() -> Lang.tr("command.inspect.moves", "§7  Moves: %s",
                Lang.hl(Lang.join(moves), Formatting.WHITE)), false);
        return 1;
    }

    private static int openConfig(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Lang.tr("command.config.players_only", "Only a player can open the config screen."));
            return 0;
        }
        CobblelockeState state = Cobblelocke.state();
        if (state != null && !state.canUseCommand(player, true)) {
            source.sendError(denied());
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
            source.sendError(denied());
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
        source.sendFeedback(() -> active
                ? Lang.tr("command.run.started", "§d★ Cobblelocke run started.")
                : Lang.tr("command.run.stopped", "§7Cobblelocke run stopped."), true);
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
            source.sendError(denied());
            return 0;
        }
        if (targets == null) {
            state.resetRun();
            EventLockService.reset();
            CobblelockeNetworking.rerollGlobalPools(state.getConfig());
            state.markDirty();
            source.sendFeedback(() -> Lang.tr("command.reset.everyone", "§cCobblelocke run progress reset for "
                    + "everyone. Catch history, permadeath records and event-lock progress are cleared and the "
                    + "global pools were re-rolled. The spawn cap and the regions it remembers are kept."), true);
            return Math.max(1, server.getPlayerManager().getCurrentPlayerCount());
        }
        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity target : targets) {
            state.resetRun(target.getUuid());
            EventLockService.reset(target.getUuid());
            names.add(target.getName().getString());
            target.sendMessage(Lang.tr("command.reset.yours", "§cYour Cobblelocke run was reset."));
        }
        state.markDirty();
        source.sendFeedback(() -> Lang.tr("command.reset.players", "§cCobblelocke run progress reset for %s§c. "
                + "§7Nobody else was touched and the global pools were kept; run §f/cobblelocke reset§7 with no "
                + "player to clear the whole world.",
                Lang.hl(String.join(", ", names), Formatting.WHITE)), true);
        return names.size();
    }

    private static int showHere(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Lang.tr("command.players_only", "Only a player can check this."));
            return 0;
        }
        for (Text line : CatchCheck.report(player)) {
            source.sendFeedback(() -> line, false);
        }
        return 1;
    }

    private static int showHints(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        if (player == null || state == null) {
            source.sendError(Lang.tr("command.players_only", "Only a player can check this."));
            return 0;
        }
        boolean on = state.getPlayer(player.getUuid()).wantsCatchHints();
        source.sendFeedback(() -> on
                ? Lang.tr("command.hints.state_on", "§7Catch hints are §aon§7. Turn them off with /cobblelocke hints off.")
                : Lang.tr("command.hints.state_off", "§7Catch hints are §coff§7. Turn them on with /cobblelocke hints on."),
                false);
        return 1;
    }

    private static int setHints(ServerCommandSource source, boolean on) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        if (player == null || state == null) {
            source.sendError(Lang.tr("command.players_only", "Only a player can check this."));
            return 0;
        }
        state.getPlayer(player.getUuid()).setCatchHints(on);
        state.markDirty();
        source.sendFeedback(() -> on
                ? Lang.tr("command.hints.on", "§aCatch hints on. §7You will see whether you can catch as you move between "
                        + "biomes, and when a wild battle starts.")
                : Lang.tr("command.hints.off", "§7Catch hints off. §7/cobblelocke here still works any time."), false);
        return 1;
    }

    private static int setSpawnCap(ServerCommandSource source, boolean on) {
        ServerPlayerEntity player = source.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        if (player != null && !state.canUseCommand(player, false)) {
            source.sendError(denied());
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
        Text message;
        if (on) {
            message = config.nuzlockeModeEnabled && config.capSpawningPerRegionChunks > 0
                    ? Lang.tr("command.spawncap.on", "§aSpawn cap on. §7The Cap Spawning rules now apply even "
                            + "before a run starts.")
                    : Lang.tr("command.spawncap.on_inactive", "§aSpawn cap on. §7It takes effect once Nuzlocke "
                            + "Mode is on and Cap Spawning Per Region by Size is set.");
        } else {
            message = Lang.tr("command.spawncap.off", "§eSpawn cap off. §7It stays off until the next run starts, "
                    + "and every remembered region was forgotten.");
        }
        source.sendFeedback(() -> message, true);
        return 1;
    }

    private static int showSpawnCap(ServerCommandSource source) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return 0;
        }
        CobblelockeConfig config = state.getConfig();
        Text status = state.isSpawnCapActive()
                ? Lang.tr("command.spawncap.active", "§aactive")
                : Lang.tr("command.spawncap.inactive", "§7not active");
        Text why;
        if (!config.nuzlockeModeEnabled || config.capSpawningPerRegionChunks <= 0) {
            why = Lang.tr("command.spawncap.why_rules", "Nuzlocke Mode and Cap Spawning Per Region by Size must both "
                    + "be on.");
        } else if (config.spawnCapAlwaysOn) {
            why = Lang.tr("command.spawncap.why_always", "spawnCapAlwaysOn is on, so it applies even before a run.");
        } else if (state.hasRunEverStarted() || config.runActive) {
            why = Lang.tr("command.spawncap.why_started", "a run has started on this world, so it stays on through "
                    + "stops and resets.");
        } else {
            why = Lang.tr("command.spawncap.why_waiting", "no run has started yet. Use /cobblelocke spawncap on to "
                    + "apply it now.");
        }
        source.sendFeedback(() -> Lang.tr("command.spawncap.status", "§7Spawn cap: %1$s§7, %2$s", status, why), false);
        if (config.capSpawningPerRegionChunks > 0) {
            Text sharing = config.capSpawningPerRegionByPlayer
                    ? Lang.tr("command.spawncap.per_player", "counted per player")
                    : Lang.tr("command.spawncap.shared", "shared by everyone");
            Text memory = config.capSpawningPerRegionMemory
                    ? Lang.tr("command.spawncap.with_memory", ", with memory")
                    : Text.empty();
            source.sendFeedback(() -> Lang.tr("command.spawncap.detail", "§7  %1$s per %2$s chunk region, %3$s%4$s",
                    config.capSpawningPerRegionCount, config.capSpawningPerRegionChunks, sharing, memory), false);
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
            source.sendError(denied());
            return 0;
        }
        String wanted = name.trim();
        if (wanted.isEmpty()) {
            source.sendError(Lang.tr("command.export.no_name", "Give the preset a name."));
            return 0;
        }
        ConfigFiles.ExportResult result = ConfigFiles.exportPreset(wanted, state.getConfig());
        Text message = CobblelockeNetworking.exportMessage(wanted, result);
        if (result == ConfigFiles.ExportResult.FAILED) {
            source.sendError(message);
            return 0;
        }
        source.sendFeedback(() -> message, true);
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
            source.sendError(denied());
            return 0;
        }
        Collection<ServerPlayerEntity> chosen = targets;
        if (chosen == null) {
            if (player == null) {
                source.sendError(Lang.tr("command.starter.no_target", "Name a player, or use @a for everyone."));
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
            source.sendError(Lang.tr("command.starter.failed", "Could not reopen the starter choice."));
            return 0;
        }
        state.markDirty();
        source.sendFeedback(() -> Lang.tr("command.starter.done", "§aStarter choice re-rolled and reopened for %s§a.",
                Lang.hl(String.join(", ", names), Formatting.WHITE)), true);
        return names.size();
    }

    // Reopens Cobblemon's starter prompt with a freshly rolled set of starters. The global pools are
    // left alone: only a world-wide reset or a new run re-rolls those.
    private static boolean offerStarterAgain(ServerPlayerEntity target, CobblelockeState state) {
        try {
            state.getPlayer(target.getUuid()).bumpStarterRoll();
            state.markDirty();
            GeneralPlayerData data = Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(target);
            data.setStarterPrompted(false);
            data.setStarterSelected(false);
            data.setStarterLocked(false);
            Cobblemon.INSTANCE.getPlayerDataManager().saveSingle(data,
                    PlayerInstancedDataStoreTypes.INSTANCE.getGENERAL());

            PlayerState playerState = state.getPlayer(target.getUuid());
            playerState.setHasChosenStarter(false);

            Cobblemon.INSTANCE.getStarterHandler().requestStarterChoice(target);
            target.sendMessage(Lang.tr("command.starter.yours", "§d★ You can choose a starter again."));
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
            source.sendError(denied());
            return 0;
        }
        pools.reset();
        pools.prefillAll(state.getConfig());

        state.saveConfig(state.getConfig().copy());
        source.sendFeedback(() -> Lang.tr("command.pools.done", "§aGlobal pools regenerated and written to "
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
        source.sendFeedback(() -> Lang.tr("status.run", "§7Run: %s", config.runActive
                ? Lang.tr("status.run.active", "§aACTIVE")
                : Lang.tr("status.run.inactive", "§cinactive")), false);

        if (player != null) {
            PlayerState playerState = state.getPlayer(player.getUuid());
            source.sendFeedback(() -> Lang.tr("status.starter", "§7Starter chosen: %s", playerState.hasChosenStarter()
                    ? Lang.tr("status.yes", "§ayes")
                    : Lang.tr("status.no", "§cno")), false);
            if (config.firstCatchEventLocked) {
                source.sendFeedback(() -> Lang.tr("status.player_event", "§7Event lock: %s", playerState.hasLeftStartingBiome()
                        ? Lang.tr("status.player_event.unlocked", "§acatching unlocked")
                        : com.cobblelocke.eventlock.EventLockService.isRegionArea(playerState.getStartingBiome())
                        ? Lang.tr("status.player_event.locked_region", "§clocked §7- leave your starting region")
                        : Lang.tr("status.player_event.locked", "§clocked §7- leave your starting biome")), false);
            }
        }

        for (Text line : StatusReport.of(config, StatusReport.asksOnFirstJoin())) {
            source.sendFeedback(() -> line, false);
        }
        source.sendFeedback(() -> Text.literal("§5══════════════════════════"), false);
        return 1;
    }

    private static String flag(String label, boolean enabled) {
        return (enabled ? "§a" : "§8") + label;
    }
}
