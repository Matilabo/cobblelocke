package com.cobblelocke.eventlock;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.data.CobblelockeState;
import com.cobblelocke.data.PlayerState;
import com.cobblelocke.net.CobblelockeNetworking;
import com.cobblelocke.nuzlocke.EvolutionLine;
import com.cobblelocke.random.PokemonStamper;
import com.cobblelocke.util.SpeciesPool;
import com.cobblelocke.util.Worlds;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class EventLockService {
    public static final String KEY_EVENT = "cobblelocke_event";

    public static final String KEY_EVENT_BIOME = "cobblelocke_event_biome";

    private static final int BATTLE_START_DELAY_TICKS = 10;

    private static final int MIN_BATTLE_DELAY_TICKS = 2;

    private static final int CLIENT_REPORT_GRACE_MS = 1500;

    private static final int CHECK_INTERVAL = 20;

    private static final int MAX_REROLLS = 32;

    private static final int MISSING_CHECKS_BEFORE_RESOLVE = 5;

    private static final int ENTRY_DWELL_TICKS = 100;

    private record BiomeArrival(String biomeId, int sinceTick) {
    }

    private static final Map<UUID, BiomeArrival> BIOME_ARRIVALS = new HashMap<>();

    private static final List<PendingCutscene> cutscenes = new ArrayList<>();
    private static final List<PendingBattle> battles = new ArrayList<>();
    private static final Map<UUID, Integer> missingChecks = new HashMap<>();
    private static int tickCounter = 0;

    private EventLockService() {
    }

    private record PendingCutscene(UUID playerId, String biomeId, String clipId, int[] ticksLeft,
                                   boolean waitingOnClient) {
    }

    private record PendingBattle(UUID playerId, UUID entityId, int[] ticksLeft) {
    }

    public static void reset() {
        cutscenes.clear();
        battles.clear();
        BIOME_ARRIVALS.clear();
        missingChecks.clear();
        tickCounter = 0;
    }

    public static void reset(java.util.UUID playerId) {
        cutscenes.remove(playerId);
        battles.remove(playerId);
        BIOME_ARRIVALS.remove(playerId);
        missingChecks.remove(playerId);
    }

    public static void onStarterChosen(ServerPlayerEntity player, PlayerState playerState) {
        RegistryKey<Biome> biome = Worlds.biomeAt(player);
        String biomeId = Worlds.biomeId(biome);
        if (biomeId == null) {
            return;
        }
        playerState.setStartingBiome(biomeId);

        playerState.markEventFired(biomeId);

        CobblelockeConfig config = Cobblelocke.config();
        if (config.runActive && config.firstCatchEventLocked) {
            player.sendMessage(Text.literal("§d★ Event Lock active. §7You cannot catch anything until you "
                    + "leave §e" + Worlds.prettyBiomeName(biome) + "§7."));
        }
    }

    public static String catchRefusal(ServerPlayerEntity player, PlayerState playerState,
                                      CobblelockeConfig config, String targetBiomeId,
                                      PokemonEntity targetEntity) {
        if (!config.firstCatchEventLocked || !playerState.hasChosenStarter()) {
            return null;
        }

        String starting = playerState.getStartingBiome();
        if (!playerState.hasLeftStartingBiome() && starting != null) {
            String current = Worlds.biomeId(Worlds.biomeAt(player));
            if (current != null && !current.equals(starting)) {
                playerState.setLeftStartingBiome(true);
            } else {
                return "§cYou cannot catch anything yet. §7Travel to a different biome first. (Event Lock)";
            }
        }

        if (triggerBiome(playerState, targetEntity) != null) {
            return null;
        }

        if (targetBiomeId == null) {
            return null;
        }
        String biomeName = Worlds.prettyBiomeName(targetBiomeId);

        UUID pending = playerState.getPendingEvent(targetBiomeId);
        if (pending != null) {
            if (targetEntity != null && pending.equals(targetEntity.getUuid())) {
                return null;
            }
            return "§cThe first catch in §e" + biomeName
                    + "§c belongs to its event encounter. (Event Lock)";
        }
        if (!playerState.hasEventFiredIn(targetBiomeId)) {
            return "§cYou have not faced the encounter in §e" + biomeName
                    + "§c yet. Step into that biome first. (Event Lock)";
        }
        return null;
    }

    public static String triggerBiome(PlayerState playerState, PokemonEntity targetEntity) {
        if (targetEntity == null) {
            return null;
        }
        try {
            NbtCompound data = targetEntity.getPokemon().getPersistentData();
            if (!data.getBoolean(KEY_EVENT)) {
                return null;
            }
            String biomeId = data.getString(KEY_EVENT_BIOME);
            if (biomeId == null || biomeId.isEmpty()) {
                return null;
            }
            UUID pending = playerState.getPendingEvent(biomeId);
            return pending != null && pending.equals(targetEntity.getUuid()) ? biomeId : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String catchBiome(PlayerState playerState, PokemonEntity targetEntity, String standingIn) {
        String trigger = triggerBiome(playerState, targetEntity);
        return trigger != null ? trigger : standingIn;
    }

    public static void onPokemonCaptured(ServerPlayerEntity player, Pokemon pokemon) {
        NbtCompound data = pokemon == null ? null : pokemon.getPersistentData();
        if (data == null || !data.getBoolean(KEY_EVENT)) {
            return;
        }
        String biomeId = data.getString(KEY_EVENT_BIOME);
        data.remove(KEY_EVENT);
        data.remove(KEY_EVENT_BIOME);

        CobblelockeState state = Cobblelocke.state();
        if (state == null || biomeId == null || biomeId.isEmpty()) {
            return;
        }
        PlayerState playerState = state.getPlayer(player.getUuid());
        UUID entityId = playerState.getPendingEvent(biomeId);
        if (entityId != null) {
            missingChecks.remove(entityId);
        }
        playerState.clearPendingEvent(biomeId);
        state.markDirty();
    }

    public static void tick(MinecraftServer server) {
        advanceCutscenes(server);
        advanceBattles(server);

        if (++tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        CobblelockeState state = CobblelockeState.get(server);
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive || !config.firstCatchEventLocked) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                resolveFinishedEncounters(server, state, config, player);
                checkPlayer(state, player);
            } catch (Exception e) {
                Cobblelocke.LOGGER.warn("Event lock check failed for {}: {}",
                        player.getName().getString(), e.toString());
            }
        }
    }

    private static void checkPlayer(CobblelockeState state, ServerPlayerEntity player) {
        PlayerState playerState = state.getPlayer(player.getUuid());
        if (!playerState.hasChosenStarter()) {
            return;
        }
        RegistryKey<Biome> biome = Worlds.biomeAt(player);
        String biomeId = Worlds.biomeId(biome);
        if (biomeId == null) {
            return;
        }

        if (playerState.getStartingBiome() == null) {
            playerState.setStartingBiome(biomeId);
            playerState.markEventFired(biomeId);
            state.markDirty();
            return;
        }

        if (!playerState.hasLeftStartingBiome() && !biomeId.equals(playerState.getStartingBiome())) {
            playerState.setLeftStartingBiome(true);
            state.markDirty();
            player.sendMessage(Text.literal("§a★ You left your starting biome. Catching is now unlocked."));
        }

        if (playerState.hasEventFiredIn(biomeId)) {
            BIOME_ARRIVALS.remove(player.getUuid());
            return;
        }

        BiomeArrival arrival = BIOME_ARRIVALS.get(player.getUuid());
        if (arrival == null || !arrival.biomeId().equals(biomeId)) {
            BIOME_ARRIVALS.put(player.getUuid(), new BiomeArrival(biomeId, player.getServer().getTicks()));
            return;
        }
        if (player.getServer().getTicks() - arrival.sinceTick() < ENTRY_DWELL_TICKS) {
            return;
        }
        if (isBusy(player) || hasEncounterUnderway(player.getUuid())) {
            return;
        }
        BIOME_ARRIVALS.remove(player.getUuid());

        playerState.markEventFired(biomeId);
        state.markDirty();
        player.sendMessage(Text.literal("§d★ A wild encounter blocks your path in §e"
                + Worlds.prettyBiomeName(biome) + "§d!"));

        String clip = EncounterAnimations.choose(player, state.getConfig());
        boolean playing = CobblelockeNetworking.sendCutscene(player, clip);

        int coverAtMs = EncounterAnimations.coverAtMs(clip);
        int deadlineMs = playing ? coverAtMs + CLIENT_REPORT_GRACE_MS : coverAtMs;
        int spawnTicks = Math.max(1, deadlineMs / 50);
        cutscenes.add(new PendingCutscene(player.getUuid(), biomeId, clip,
                new int[]{spawnTicks}, playing));
    }

    public static void onScreenCovered(ServerPlayerEntity player, String clipId) {
        if (player == null || clipId == null) {
            return;
        }
        Iterator<PendingCutscene> iterator = cutscenes.iterator();
        while (iterator.hasNext()) {
            PendingCutscene cutscene = iterator.next();
            if (!cutscene.playerId().equals(player.getUuid()) || !clipId.equals(cutscene.clipId())) {
                continue;
            }
            iterator.remove();
            try {
                spawnEncounter(player, cutscene.biomeId(), battleDelayTicks(cutscene.clipId()));
            } catch (Exception e) {
                Cobblelocke.LOGGER.error("Could not spawn the event encounter for {}: {}",
                        player.getName().getString(), e.toString());
            }
            return;
        }
    }

    private static int battleDelayTicks(String clipId) {
        int window = EncounterAnimations.coverUntilMs(clipId) - EncounterAnimations.coverAtMs(clipId);
        return Math.max(MIN_BATTLE_DELAY_TICKS,
                Math.min(BATTLE_START_DELAY_TICKS, window / 50));
    }

    private static void resolveFinishedEncounters(MinecraftServer server, CobblelockeState state,
                                                  CobblelockeConfig config, ServerPlayerEntity player) {
        PlayerState playerState = state.getPlayer(player.getUuid());
        if (playerState.getPendingEvents().isEmpty()) {
            return;
        }
        for (Map.Entry<String, UUID> entry : new ArrayList<>(playerState.getPendingEvents().entrySet())) {
            String biomeId = entry.getKey();
            UUID entityId = entry.getValue();

            Entity entity = findEntity(server, entityId);
            if (entity != null && entity.isAlive()) {
                missingChecks.remove(entityId);
                continue;
            }

            if (battles.stream().anyMatch(battle -> battle.entityId().equals(entityId))) {
                continue;
            }
            int misses = missingChecks.merge(entityId, 1, Integer::sum);
            if (misses < MISSING_CHECKS_BEFORE_RESOLVE) {
                continue;
            }
            missingChecks.remove(entityId);
            playerState.clearPendingEvent(biomeId);

            String biomeName = Worlds.prettyBiomeName(biomeId);
            if (config.nuzlockeModeEnabled && config.oneCatchPerBiome
                    && !playerState.hasCapturedInBiome(biomeId)) {
                playerState.recordBiomeCapture(biomeId);
                player.sendMessage(Text.literal("§7The encounter in §e" + biomeName
                        + "§7 got away. That was this biome's encounter."));
            } else {
                player.sendMessage(Text.literal("§7The encounter in §e" + biomeName
                        + "§7 is over. Catching there is open."));
            }
            state.markDirty();
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID entityId) {
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static boolean hasEncounterUnderway(UUID playerId) {
        return cutscenes.stream().anyMatch(cutscene -> cutscene.playerId().equals(playerId))
                || battles.stream().anyMatch(battle -> battle.playerId().equals(playerId));
    }

    private static boolean isBusy(ServerPlayerEntity player) {
        try {
            if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
                return true;
            }
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            for (Pokemon pokemon : party) {
                if (pokemon.getCurrentHealth() > 0) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private static void advanceCutscenes(MinecraftServer server) {
        if (cutscenes.isEmpty()) {
            return;
        }
        Iterator<PendingCutscene> iterator = cutscenes.iterator();
        while (iterator.hasNext()) {
            PendingCutscene cutscene = iterator.next();
            if (--cutscene.ticksLeft()[0] > 0) {
                continue;
            }
            iterator.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(cutscene.playerId());
            if (player == null) {
                continue;
            }
            try {
                spawnEncounter(player, cutscene.biomeId(), battleDelayTicks(cutscene.clipId()));
            } catch (Exception e) {
                Cobblelocke.LOGGER.error("Could not spawn the event encounter for {}: {}",
                        player.getName().getString(), e.toString());
            }
        }
    }

    private static void advanceBattles(MinecraftServer server) {
        if (battles.isEmpty()) {
            return;
        }
        Iterator<PendingBattle> iterator = battles.iterator();
        while (iterator.hasNext()) {
            PendingBattle battle = iterator.next();
            if (--battle.ticksLeft()[0] > 0) {
                continue;
            }
            iterator.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(battle.playerId());
            if (player == null) {
                continue;
            }
            Entity entity = player.getServerWorld().getEntity(battle.entityId());
            if (!(entity instanceof PokemonEntity pokemonEntity) || !pokemonEntity.isAlive()) {
                continue;
            }
            if (pokemonEntity.isBattling() || BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
                continue;
            }
            try {
                BattleBuilder.INSTANCE.pve(player, pokemonEntity);
            } catch (Exception e) {
                Cobblelocke.LOGGER.error("Could not start the event battle for {}: {}",
                        player.getName().getString(), e.toString());
            }
        }
    }

    private static void spawnEncounter(ServerPlayerEntity player, String biomeId, int battleDelayTicks) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        PlayerState playerState = state.getPlayer(player.getUuid());

        Species species = rollEncounterSpecies(playerState, player, config);
        if (species == null) {
            Cobblelocke.LOGGER.warn("No species available for the event encounter");
            return;
        }

        PokemonProperties properties = PokemonProperties.Companion.parse(
                species.getName().toLowerCase(Locale.ROOT) + " level=" + encounterLevel(player, config));
        Pokemon pokemon = properties.create();

        NbtCompound data = pokemon.getPersistentData();

        data.putBoolean(PokemonStamper.KEY_SPAWN_HANDLED, true);
        data.putBoolean(KEY_EVENT, true);
        data.putString(KEY_EVENT_BIOME, biomeId);

        PokemonStamper.stamp(pokemon, config, state.getConfigGeneration(), PokemonStamper.Context.WILD);
        pokemon.heal();

        ServerWorld world = player.getServerWorld();
        PokemonEntity entity = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
        Vec3d spot = spawnSpot(player);
        entity.refreshPositionAndAngles(spot.x, spot.y, spot.z, player.getYaw() + 180.0f, 0.0f);
        if (!world.spawnEntity(entity)) {
            Cobblelocke.LOGGER.warn("Could not spawn the event encounter entity");
            return;
        }

        playerState.setPendingEvent(biomeId, entity.getUuid());
        state.markDirty();
        player.sendMessage(Text.literal("§d★ A wild §b" + species.getName() + "§d appeared!"));
        battles.add(new PendingBattle(player.getUuid(), entity.getUuid(),
                new int[]{Math.max(MIN_BATTLE_DELAY_TICKS, battleDelayTicks)}));
    }

    private static Species rollEncounterSpecies(PlayerState playerState, ServerPlayerEntity player,
                                                CobblelockeConfig config) {
        Random random = new Random();
        Set<String> alreadyOwned = ownedSpeciesNames(player);

        Species fallback = null;
        for (int attempt = 0; attempt < MAX_REROLLS; attempt++) {
            Species candidate = SpeciesPool.rollWild(random, config);
            if (candidate == null) {
                return null;
            }
            if (fallback == null) {
                fallback = candidate;
            }
            if (isDuplicate(candidate, playerState, alreadyOwned, config)) {
                continue;
            }
            return candidate;
        }

        return fallback;
    }

    private static boolean isDuplicate(Species candidate, PlayerState playerState,
                                       Set<String> alreadyOwned, CobblelockeConfig config) {
        if (alreadyOwned.contains(candidate.getName())) {
            return true;
        }
        if (!config.noDuplicates) {
            return false;
        }
        for (String species : EvolutionLine.of(candidate)) {
            if (playerState.hasCaughtSpecies(species) || alreadyOwned.contains(species)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> ownedSpeciesNames(ServerPlayerEntity player) {
        Set<String> names = new HashSet<>();
        try {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                names.add(pokemon.getSpecies().getName());
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private static int encounterLevel(ServerPlayerEntity player, CobblelockeConfig config) {
        return com.cobblelocke.util.PartyLevels.forMode(config.eventLevelMode, player, new Random());
    }

    private static Vec3d spawnSpot(ServerPlayerEntity player) {
        Vec3d facing = player.getRotationVec(1.0f).multiply(3.0);
        Vec3d target = player.getPos().add(facing.x, 0.0, facing.z);
        ServerWorld world = player.getServerWorld();
        BlockPos pos = BlockPos.ofFloored(target);

        BlockPos ground = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos);
        if (Math.abs(ground.getY() - player.getBlockY()) > 6) {
            return player.getPos().add(facing.x, 0.0, facing.z);
        }
        return new Vec3d(target.x, ground.getY(), target.z);
    }
}
