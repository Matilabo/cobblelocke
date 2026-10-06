package com.cobblelocke.nuzlocke;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.config.ConfigOptions;
import com.cobblelocke.data.BiomeInstanceKey;
import com.cobblelocke.data.CobblelockeState;
import com.cobblelocke.data.PlayerState;
import com.cobblelocke.eventlock.EventLockService;
import com.cobblelocke.util.Lang;
import com.cobblelocke.util.Worlds;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class CatchCheck {
    private static final int SCAN_INTERVAL = 10;
    private static final int SETTLE_TICKS = 20;
    private static final int BATTLE_NOTE_DELAY = 10;

    public enum Kind {
        NO_RULES,
        OPEN,
        STARTING_BIOME,
        EVENT_PENDING,
        EVENT_NOT_FACED,
        COOLDOWN,
        BIOME_USED,
        REGION_USED,
        OTHER
    }

    public record Spot(String biomeId, RegistryKey<Biome> biome, BiomeInstanceKey region, Kind kind,
                       long cooldownMillis, MutableText refusal, PlayerState.CatchRecord biomeCatch,
                       PlayerState.CatchRecord regionCatch, PlayerState.CatchRecord eventCatch, String eventArea) {
    }

    private record Watch(String announced, String candidate, long since) {
    }

    private record BattleNote(UUID playerId, UUID battleId, int[] ticksLeft) {
    }

    private static final Map<UUID, Watch> WATCHING = new HashMap<>();
    private static final List<BattleNote> BATTLE_NOTES = new ArrayList<>();

    private static boolean registered = false;
    private static int scanCounter = 0;

    private CatchCheck() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.LOW,
                (Consumer<BattleStartedEvent.Post>) CatchCheck::onBattleStarted);
    }

    public static void reset() {
        WATCHING.clear();
        BATTLE_NOTES.clear();
    }

    public static boolean anyLocationRule(CobblelockeConfig config, PlayerState playerState) {
        if (!config.runActive) {
            return false;
        }
        boolean eventLock = config.firstCatchEventLocked && playerState.hasChosenStarter();
        boolean nuzlocke = config.nuzlockeModeEnabled
                && (config.oneCatchPerBiome || config.oneCatchPerRegion > 0 || config.catchCooldownSeconds > 0);
        return eventLock || nuzlocke;
    }

    private static boolean anyCatchRule(CobblelockeConfig config, PlayerState playerState) {
        return anyLocationRule(config, playerState)
                || (config.runActive && config.nuzlockeModeEnabled && config.noDuplicates);
    }

    private static BiomeInstanceKey regionAt(CobblelockeConfig config, RegistryKey<Biome> biome, BlockPos pos) {
        if (!config.nuzlockeModeEnabled || config.oneCatchPerRegion <= 0 || biome == null) {
            return null;
        }
        return BiomeInstanceKey.fromPosition(biome, pos, config.oneCatchPerRegion);
    }

    public static Spot evaluate(ServerPlayerEntity player) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return null;
        }
        CobblelockeConfig config = state.getConfig();
        PlayerState playerState = state.getPlayer(player.getUuid());
        ServerWorld world = player.getServerWorld();
        BlockPos pos = player.getBlockPos();
        RegistryKey<Biome> biome = Worlds.biomeAt(world, pos);
        String biomeId = Worlds.biomeId(biome);
        BiomeInstanceKey region = regionAt(config, biome, pos);
        PlayerState.CatchRecord biomeCatch = config.nuzlockeModeEnabled && config.oneCatchPerBiome && biomeId != null
                ? playerState.getCatchRecord(PlayerState.biomeRecord(biomeId)) : null;
        PlayerState.CatchRecord regionCatch = region != null
                ? playerState.getCatchRecord(PlayerState.regionRecord(region)) : null;
        String area = config.firstCatchEventLocked ? EventLockService.areaAt(world, pos, config) : null;
        PlayerState.CatchRecord eventCatch = area != null
                ? playerState.getCatchRecord(PlayerState.eventRecord(area)) : null;
        if (!anyLocationRule(config, playerState)) {
            return new Spot(biomeId, biome, region, Kind.NO_RULES, 0L, null, biomeCatch, regionCatch, eventCatch,
                    area);
        }

        MutableText refusal = NuzlockeService.catchRefusal(player, null, null, world, pos);

        Kind kind = Kind.OPEN;
        long cooldown = 0L;
        if (config.firstCatchEventLocked && playerState.hasChosenStarter() && area != null) {
            String starting = playerState.getStartingBiome();
            if (!playerState.hasLeftStartingBiome() && starting != null && starting.equals(area)) {
                kind = Kind.STARTING_BIOME;
            } else if (playerState.getPendingEvent(area) != null) {
                kind = Kind.EVENT_PENDING;
            } else if (!playerState.hasEventFiredIn(area)) {
                kind = Kind.EVENT_NOT_FACED;
            }
        }
        if (kind == Kind.OPEN && config.nuzlockeModeEnabled) {
            if (config.catchCooldownSeconds > 0 && playerState.isOnCatchCooldown(config.catchCooldownSeconds)) {
                kind = Kind.COOLDOWN;
                cooldown = playerState.remainingCooldownMillis(config.catchCooldownSeconds);
            } else {
                boolean biomeUsed = config.oneCatchPerBiome && biomeId != null && playerState.hasCapturedInBiome(biomeId);
                boolean regionUsed = region != null && playerState.hasCapturedInInstance(region);
                boolean both = config.biomeCatchIsExtra() && config.oneCatchPerBiome && biomeId != null
                        && region != null;
                if (both) {
                    // The biome's catch is an extra one on top of the region's: blocked only once both are used.
                    if (biomeUsed && regionUsed) {
                        kind = Kind.BIOME_USED;
                    }
                } else if (biomeUsed) {
                    kind = Kind.BIOME_USED;
                } else if (regionUsed) {
                    kind = Kind.REGION_USED;
                }
            }
        }
        if ((kind == Kind.OPEN) != (refusal == null)) {
            kind = Kind.OTHER;
        }
        return new Spot(biomeId, biome, region, kind, cooldown, refusal, biomeCatch, regionCatch, eventCatch, area);
    }

    private static boolean regionEvents(Spot spot) {
        return EventLockService.isRegionArea(spot.eventArea());
    }

    private static MutableText eventPlace(Spot spot) {
        return regionEvents(spot) ? Lang.hl(EventLockService.areaName(spot.eventArea()), Formatting.YELLOW)
                : place(spot);
    }

    public static MutableText caught(PlayerState.CatchRecord record) {
        if (record.escaped()) {
            return Lang.hl(Lang.tr("record.escaped", "Escaped!"), Formatting.GOLD);
        }
        Species species = PokemonSpecies.getByName(record.species().toLowerCase(Locale.ROOT));
        return Lang.hl(species != null ? Lang.species(species) : Text.literal(record.species()), Formatting.AQUA);
    }

    private static MutableText recordText(PlayerState.CatchRecord record) {
        return record.escaped() ? caught(record) : Lang.tr("here.caught", "%s caught", caught(record));
    }

    public static MutableText place(Spot spot) {
        MutableText name = Lang.hl(Lang.biome(spot.biome()), Formatting.YELLOW);
        if (spot.region() == null) {
            return name;
        }
        return Text.empty().append(name).append(" ").append(Lang.hl(
                "(" + spot.region().regionX() + ", " + spot.region().regionZ() + ")", Formatting.DARK_GRAY));
    }

    public static MutableText hint(Spot spot) {
        MutableText place = place(spot);
        MutableText eventPlace = eventPlace(spot);
        return switch (spot.kind()) {
            case NO_RULES -> null;
            case OPEN -> spot.eventCatch() != null
                    ? Lang.tr("hint.open_after_event", "§a✔ %1$s§a: catch available §7(encounter: %2$s§7)", place,
                            caught(spot.eventCatch()))
                    : Lang.tr("hint.open", "§a✔ %s§a: catch available", place);
            case STARTING_BIOME -> regionEvents(spot)
                    ? Lang.tr("hint.starting_region", "§c✘ %s§c: leave your starting region first", eventPlace)
                    : Lang.tr("hint.starting_biome", "§c✘ %s§c: leave your starting biome first", place);
            case EVENT_PENDING -> Lang.tr("hint.event_pending", "§6★ %s§6: its encounter is waiting", eventPlace);
            case EVENT_NOT_FACED -> Lang.tr("hint.event_not_faced", "§e%s§e: encounter not faced yet", eventPlace);
            case COOLDOWN -> Lang.tr("hint.cooldown", "§e%1$s§e: catch cooldown, %2$s left", place,
                    Worlds.formatDuration(spot.cooldownMillis()));
            case BIOME_USED -> spot.biomeCatch() == null
                    ? Lang.tr("hint.biome_used", "§7✘ %s§7: already caught here", place)
                    : spot.biomeCatch().escaped()
                    ? Lang.tr("hint.escaped", "§7✘ %1$s§7: %2$s", place, caught(spot.biomeCatch()))
                    : Lang.tr("hint.biome_caught", "§7✘ %1$s§7: %2$s§7 caught here", place, caught(spot.biomeCatch()));
            case REGION_USED -> spot.regionCatch() == null
                    ? Lang.tr("hint.region_used", "§7✘ %s§7: already caught in this region", place)
                    : spot.regionCatch().escaped()
                    ? Lang.tr("hint.escaped", "§7✘ %1$s§7: %2$s", place, caught(spot.regionCatch()))
                    : Lang.tr("hint.region_caught", "§7✘ %1$s§7: %2$s§7 caught in this region", place,
                            caught(spot.regionCatch()));
            case OTHER -> spot.refusal();
        };
    }

    public static List<Text> report(ServerPlayerEntity player) {
        List<Text> lines = new ArrayList<>();
        CobblelockeState state = Cobblelocke.state();
        Spot spot = evaluate(player);
        if (state == null || spot == null) {
            return lines;
        }
        CobblelockeConfig config = state.getConfig();
        PlayerState playerState = state.getPlayer(player.getUuid());

        lines.add(Lang.tr("here.header", "§d★ %s", place(spot)));
        if (!config.runActive) {
            lines.add(Lang.tr("here.no_run", "§7No Cobblelocke run is active, so nothing limits catching."));
            return lines;
        }
        if (spot.kind() == Kind.NO_RULES) {
            lines.add(Lang.tr("here.no_rules", "§7No rule limits catching here."));
        } else if (spot.kind() == Kind.OPEN) {
            lines.add(Lang.tr("here.open", "§a✔ You can catch here."));
        } else {
            lines.add(Lang.tr("here.blocked", "§c✘ %s", spot.refusal() != null ? spot.refusal() : hint(spot)));
        }

        if (config.firstCatchEventLocked) {
            MutableText status;
            if (!playerState.hasChosenStarter()) {
                status = Lang.tr("here.event.no_starter", "starts once you choose a starter");
            } else if (spot.kind() == Kind.STARTING_BIOME) {
                status = regionEvents(spot)
                        ? Lang.tr("here.event.starting_region", "leave your starting region first")
                        : Lang.tr("here.event.starting", "leave your starting biome first");
            } else if (spot.eventArea() != null && playerState.getPendingEvent(spot.eventArea()) != null) {
                status = regionEvents(spot)
                        ? Lang.tr("here.event.pending_region", "this region's encounter is waiting")
                        : Lang.tr("here.event.pending", "this biome's encounter is waiting");
            } else if (spot.eventArea() != null && !playerState.hasEventFiredIn(spot.eventArea())) {
                status = Lang.tr("here.event.not_faced", "not faced yet, stay here a moment to trigger it");
            } else if (spot.eventCatch() != null) {
                status = recordText(spot.eventCatch());
            } else {
                status = Lang.tr("here.event.done", "encounter done");
            }
            if (regionEvents(spot)) {
                status = Lang.tr("here.event.area", "%1$s: %2$s",
                        EventLockService.areaName(spot.eventArea()), status);
            }
            lines.add(detail("firstCatchEventLocked", status));
        }
        if (config.nuzlockeModeEnabled) {
            if (config.oneCatchPerBiome) {
                boolean used = spot.biomeId() != null && playerState.hasCapturedInBiome(spot.biomeId());
                lines.add(detail("oneCatchPerBiome", !used ? availableText()
                        : spot.biomeCatch() != null ? recordText(spot.biomeCatch()) : usedText()));
            }
            if (config.oneCatchPerRegion > 0) {
                boolean used = spot.region() != null && playerState.hasCapturedInInstance(spot.region());
                lines.add(detail("oneCatchPerRegion", !used ? availableText()
                        : spot.regionCatch() != null ? recordText(spot.regionCatch()) : usedText()));
            }
            if (config.catchCooldownSeconds > 0) {
                long remaining = playerState.remainingCooldownMillis(config.catchCooldownSeconds);
                lines.add(detail("catchCooldownSeconds", remaining > 0
                        ? Lang.tr("here.cooldown_left", "%s left", Worlds.formatDuration(remaining))
                        : Lang.tr("here.ready", "ready")));
            }

            List<Text> other = new ArrayList<>();
            if (config.noDuplicates) {
                other.add(label("noDuplicates"));
            }
            if (config.onlyCatchInBattle) {
                other.add(label("onlyCatchInBattle"));
            }
            if (config.shinyClause) {
                other.add(label("shinyClause"));
            }
            if (!other.isEmpty()) {
                lines.add(Lang.tr("here.other", "§7  Other Settings: %s", Lang.hl(Lang.join(other), Formatting.WHITE)));
            }
        }
        return lines;
    }

    private static MutableText label(String key) {
        ConfigOptions.Spec spec = ConfigOptions.byKey(key);
        return Lang.option(key, spec == null ? key : spec.label());
    }

    private static MutableText detail(String key, MutableText value) {
        return Lang.tr("here.detail", "§7  %1$s: %2$s", label(key), Lang.hl(value, Formatting.WHITE));
    }

    private static MutableText usedText() {
        return Lang.tr("here.used", "already used");
    }

    private static MutableText availableText() {
        return Lang.tr("here.available", "available");
    }

    public static void tick(MinecraftServer server) {
        advanceBattleNotes(server);
        if (++scanCounter < SCAN_INTERVAL) {
            return;
        }
        scanCounter = 0;
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        long now = server.getTicks();
        List<UUID> online = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            online.add(player.getUuid());
            PlayerState playerState = state.getPlayer(player.getUuid());
            if (!playerState.wantsCatchHints() || !anyLocationRule(config, playerState)) {
                WATCHING.remove(player.getUuid());
                continue;
            }
            try {
                watch(player, config, now);
            } catch (Exception e) {
                Cobblelocke.LOGGER.debug("Catch hint failed for {}: {}", player.getName().getString(), e.toString());
            }
        }
        WATCHING.keySet().retainAll(online);
    }

    private static void watch(ServerPlayerEntity player, CobblelockeConfig config, long now) {
        RegistryKey<Biome> biome = Worlds.biomeAt(player);
        String biomeId = Worlds.biomeId(biome);
        if (biomeId == null) {
            return;
        }
        BiomeInstanceKey region = regionAt(config, biome, player.getBlockPos());
        String key = biomeId + (region == null ? "" : "|" + region.regionX() + "," + region.regionZ())
                + (config.eventLockRegion > 0 ? "|" + EventLockService.areaAt(player, config) : "");

        UUID id = player.getUuid();
        Watch watch = WATCHING.get(id);
        if (watch == null || !key.equals(watch.candidate())) {
            WATCHING.put(id, new Watch(watch == null ? null : watch.announced(), key, now));
            return;
        }
        if (key.equals(watch.announced()) || now - watch.since() < SETTLE_TICKS) {
            return;
        }
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null
                || EventLockService.hasEncounterUnderway(id)) {
            return;
        }
        Spot spot = evaluate(player);
        MutableText hint = spot == null ? null : hint(spot);
        if (hint != null) {
            player.sendMessage(hint, true);
        }
        WATCHING.put(id, new Watch(key, key, watch.since()));
    }

    private static void onBattleStarted(BattleStartedEvent.Post event) {
        PokemonBattle battle = event.getBattle();
        if (battle == null || !battle.isPvW()) {
            return;
        }
        for (BattleActor actor : battle.getActors()) {
            for (UUID playerId : actor.getPlayerUUIDs()) {
                BATTLE_NOTES.add(new BattleNote(playerId, battle.getBattleId(), new int[]{BATTLE_NOTE_DELAY}));
            }
        }
    }

    private static void advanceBattleNotes(MinecraftServer server) {
        if (BATTLE_NOTES.isEmpty()) {
            return;
        }
        Iterator<BattleNote> iterator = BATTLE_NOTES.iterator();
        while (iterator.hasNext()) {
            BattleNote note = iterator.next();
            if (--note.ticksLeft()[0] > 0) {
                continue;
            }
            iterator.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(note.playerId());
            PokemonBattle battle = BattleRegistry.getBattle(note.battleId());
            if (player == null || battle == null) {
                continue;
            }
            try {
                describeBattle(player, battle);
            } catch (Exception e) {
                Cobblelocke.LOGGER.debug("Battle catch note failed: {}", e.toString());
            }
        }
    }

    private static void describeBattle(ServerPlayerEntity player, PokemonBattle battle) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        PlayerState playerState = state.getPlayer(player.getUuid());
        if (!anyCatchRule(config, playerState) || !playerState.wantsCatchHints()) {
            return;
        }
        for (BattleActor actor : battle.getActors()) {
            if (actor.getPlayerUUIDs().iterator().hasNext()) {
                continue;
            }
            for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                PokemonEntity entity = battlePokemon.getEntity();
                if (entity == null || !(entity.getWorld() instanceof ServerWorld world)) {
                    continue;
                }
                Pokemon pokemon = entity.getPokemon();
                if (pokemon == null || !pokemon.isWild()) {
                    continue;
                }
                player.sendMessage(battleLine(player, playerState, config, pokemon, entity, world));
            }
        }
    }

    private static MutableText battleLine(ServerPlayerEntity player, PlayerState playerState,
                                          CobblelockeConfig config, Pokemon pokemon, PokemonEntity entity,
                                          ServerWorld world) {
        MutableText name = Lang.hl(Lang.species(pokemon.getSpecies()), Formatting.AQUA);
        MutableText refusal = NuzlockeService.catchRefusal(player, pokemon, entity, world, entity.getBlockPos());
        if (refusal != null) {
            return Lang.tr("battle.blocked", "§c✘ %1$s can't be caught: %2$s", name, refusal);
        }
        String eventBiome = EventLockService.triggerBiome(playerState, entity);
        if (eventBiome != null) {
            MutableText area = Lang.hl(EventLockService.areaName(eventBiome), Formatting.YELLOW);
            if (EventLockService.isExtraCatch(config, playerState, entity)) {
                return Lang.tr("battle.event_extra", "§d★ %1$s is the %2$s encounter. §7It is an extra catch, "
                        + "so it doesn't use up this biome or region.", name, area);
            }
            if (config.nuzlockeModeEnabled && config.oneCatchPerBiome && !EventLockService.isRegionArea(eventBiome)) {
                return Lang.tr("battle.event", "§d★ %1$s is the %2$s encounter. §7Catching it claims that biome.",
                        name, area);
            }
            return Lang.tr("battle.event_plain", "§d★ %1$s is the %2$s encounter.", name, area);
        }
        if (config.nuzlockeModeEnabled && config.shinyClause && pokemon.getShiny()) {
            return Lang.tr("battle.shiny", "§6★ %s is shiny: the Shiny Clause lets you catch it.", name);
        }
        if (config.nuzlockeModeEnabled && config.oneCatchPerBiome) {
            String biomeId = EventLockService.catchBiome(playerState, entity,
                    Worlds.biomeId(Worlds.biomeAt(world, entity.getBlockPos())));
            return Lang.tr("battle.open_biome", "§a✔ %1$s can be caught. §7It would be your %2$s catch.",
                    name, Lang.hl(Lang.biome(biomeId), Formatting.YELLOW));
        }
        return Lang.tr("battle.open", "§a✔ %s can be caught.", name);
    }
}
