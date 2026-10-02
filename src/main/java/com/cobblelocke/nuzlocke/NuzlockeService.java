package com.cobblelocke.nuzlocke;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import com.cobblelocke.data.BiomeInstanceKey;
import com.cobblelocke.data.CobblelockeState;
import com.cobblelocke.data.PlayerState;
import com.cobblelocke.eventlock.EventLockService;
import com.cobblelocke.util.Worlds;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.entity.PokemonSideDelegate;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonFaintedEvent;
import com.cobblemon.mod.common.api.events.pokemon.healing.PokemonHealedEvent;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleCaptureAction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonServerDelegate;
import com.cobblemon.mod.common.net.messages.client.battle.BattleCaptureEndPacket;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

public final class NuzlockeService {
    private static boolean registered = false;

    private NuzlockeService() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST,
                (Consumer<ThrownPokeballHitEvent>) NuzlockeService::onPokeballHit);
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.HIGHEST,
                (Consumer<PokemonCapturedEvent>) NuzlockeService::onCaptured);
        CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.HIGHEST,
                (Consumer<PokemonFaintedEvent>) event -> onHealthReachedZero(event.getPokemon()));
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.HIGHEST,
                (Consumer<BattleFaintedEvent>) event -> {
                    BattlePokemon killed = event.getKilled();
                    if (killed == null) {
                        return;
                    }
                    Pokemon pokemon = killed.getOriginalPokemon() != null
                            ? killed.getOriginalPokemon()
                            : killed.getEffectedPokemon();
                    if (pokemon == null) {
                        return;
                    }

                    claimAsBattleFaint(pokemon);
                    claimAsBattleFaint(killed.getEffectedPokemon());
                    if (isPlayerVersusPlayer(event.getBattle())) {
                        return;
                    }
                    onFainted(pokemon, true);
                });
        CobblemonEvents.POKEMON_HEALED.subscribe(Priority.HIGHEST,
                (Consumer<PokemonHealedEvent>) NuzlockeService::onHealed);
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.HIGH,
                (Consumer<SpawnEvent<?>>) NuzlockeService::onSpawn);

        Cobblelocke.LOGGER.info("Nuzlocke rules registered");
    }

    private static void onPokeballHit(ThrownPokeballHitEvent event) {
        Entity thrower = event.getPokeBall().getOwner();
        if (!(thrower instanceof ServerPlayerEntity player)) {
            return;
        }
        PokemonEntity targetEntity = event.getPokemon();
        Pokemon target = targetEntity.getPokemon();
        BlockPos targetPos = targetEntity.getBlockPos();
        ServerWorld targetWorld = targetEntity.getWorld() instanceof ServerWorld world
                ? world
                : player.getServerWorld();

        String refusal = catchRefusal(player, target, targetEntity, targetWorld, targetPos);
        if (refusal != null) {
            unwindBattleCapture(event);
            event.cancel();
            resyncInventory(player);
            player.sendMessage(Text.literal(refusal));
            return;
        }

        CobblelockeState state = Cobblelocke.state();
        if (state == null || !state.getConfig().runActive || target == null) {
            return;
        }

        RegistryKey<Biome> biome = Worlds.biomeAt(targetWorld, targetPos);
        PlayerState reserving = state.getPlayer(player.getUuid());
        CatchReservations.reserve(new CatchReservations.Reservation(
                player.getUuid(),
                target.getUuid(),
                EventLockService.catchBiome(reserving, targetEntity, Worlds.biomeId(biome)),
                biome == null ? null
                        : BiomeInstanceKey.fromPosition(biome, targetPos,
                                state.getConfig().oneCatchPerRegion),
                EvolutionLine.of(target.getSpecies()),
                state.getConfig().shinyClause && target.getShiny(),
                System.currentTimeMillis()), event.getPokeBall());
    }

    public static String catchRefusal(ServerPlayerEntity player, Pokemon target, PokemonEntity targetEntity,
                                      ServerWorld targetWorld, BlockPos targetPos) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return null;
        }
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive) {
            return null;
        }
        PlayerState playerState = state.getPlayer(player.getUuid());

        BlockPos pos = targetPos != null ? targetPos : player.getBlockPos();
        ServerWorld world = targetWorld != null ? targetWorld : player.getServerWorld();
        RegistryKey<Biome> biome = Worlds.biomeAt(world, pos);

        String biomeId = EventLockService.catchBiome(playerState, targetEntity, Worlds.biomeId(biome));

        String eventRefusal = EventLockService.catchRefusal(player, playerState, config, biomeId, targetEntity);
        if (eventRefusal != null) {
            return eventRefusal;
        }

        if (!config.nuzlockeModeEnabled || !playerState.hasChosenStarter()) {
            return null;
        }

        if (config.shinyClause && target != null && target.getShiny()) {
            return null;
        }

        if (config.onlyCatchInBattle && targetEntity != null && !targetEntity.isBattling()) {
            return "§cYou can only catch Pokémon you are battling. (Only Catch In-Battle)";
        }

        UUID targetId = target == null ? null : target.getUuid();
        List<CatchReservations.Reservation> inProgress = CatchReservations.activeFor(player.getUuid(), targetId)
                .stream()
                .filter(reservation -> !reservation.shinyPass())
                .toList();

        if (config.catchCooldownSeconds > 0) {
            if (playerState.isOnCatchCooldown(config.catchCooldownSeconds)) {
                long remaining = playerState.remainingCooldownMillis(config.catchCooldownSeconds);
                return "§cCatch cooldown active. Wait " + Worlds.formatDuration(remaining) + ".";
            }
            if (!inProgress.isEmpty()) {
                return "§cFinish your current catch first. (Catch Cooldown)";
            }
        }

        if (config.noDuplicates && target != null) {
            String caught = alreadyCaughtInLine(playerState, target);
            if (caught != null) {
                String species = target.getSpecies().getName();
                return caught.equals(species)
                        ? "§cYou already caught a " + species + "! (Duplicate Clause)"
                        : "§cYou already caught " + caught + ", same evolution line! (Duplicate Clause)";
            }
            Set<String> line = EvolutionLine.of(target.getSpecies());
            for (CatchReservations.Reservation reservation : inProgress) {
                if (reservation.evolutionLine().stream().anyMatch(line::contains)) {
                    return "§cYou are already catching a Pokémon from that evolution line! (Duplicate Clause)";
                }
            }
        }

        if (config.oneCatchPerBiome && biomeId != null) {
            if (playerState.hasCapturedInBiome(biomeId)) {
                return "§cYou already caught a Pokémon in §e" + Worlds.prettyBiomeName(biome) + "§c!";
            }
            for (CatchReservations.Reservation reservation : inProgress) {
                if (biomeId.equals(reservation.biomeId())) {
                    return "§cYou are already catching a Pokémon in §e" + Worlds.prettyBiomeName(biome) + "§c!";
                }
            }
        }

        if (config.oneCatchPerRegion > 0 && biome != null) {
            BiomeInstanceKey key = BiomeInstanceKey.fromPosition(biome, pos, config.oneCatchPerRegion);
            String region = "§e" + Worlds.prettyBiomeName(biome) + "§c region §f(" + key.regionX()
                    + ", " + key.regionZ() + ")§c";
            if (playerState.hasCapturedInInstance(key)) {
                return "§cYou already caught a Pokémon in " + region + "!";
            }
            for (CatchReservations.Reservation reservation : inProgress) {
                if (key.equals(reservation.instance())) {
                    return "§cYou are already catching a Pokémon in " + region + "!";
                }
            }
        }
        return null;
    }

    private static String alreadyCaughtInLine(PlayerState playerState, Pokemon pokemon) {
        for (String species : EvolutionLine.of(pokemon.getSpecies())) {
            if (playerState.hasCaughtSpecies(species)) {
                return species;
            }
        }
        return null;
    }

    private static void onCaptured(PokemonCapturedEvent event) {
        ServerPlayerEntity player = event.getPlayer();
        CobblelockeState state = Cobblelocke.state();
        Pokemon pokemon = event.getPokemon();
        if (player == null || state == null || pokemon == null) {
            return;
        }

        CatchReservations.Reservation reservation = CatchReservations.take(pokemon.getUuid());

        CobblelockeConfig config = state.getConfig();
        if (!config.runActive) {
            return;
        }
        EventLockService.onPokemonCaptured(player, pokemon);
        if (!config.nuzlockeModeEnabled) {
            return;
        }
        PlayerState playerState = state.getPlayer(player.getUuid());
        if (!playerState.hasChosenStarter()) {
            return;
        }
        if (config.requireNicknames) {
            NicknameService.requestAfterCapture(player, pokemon);
        }

        String speciesName = pokemon.getSpecies().getName();
        boolean shinyPass = config.shinyClause && pokemon.getShiny();

        if (shinyPass) {
            player.sendMessage(Text.literal("§6★ Shiny " + speciesName
                    + " caught! §7(Shiny Clause - bypasses restrictions)"));
        }

        if (config.noDuplicates) {
            for (String species : EvolutionLine.of(pokemon.getSpecies())) {
                playerState.recordSpecies(species);
            }
        }
        if (shinyPass) {
            state.markDirty();
            return;
        }

        BlockPos ballPos = event.getPokeBallEntity() != null
                ? event.getPokeBallEntity().getBlockPos()
                : player.getBlockPos();
        String biomeId = reservation != null ? reservation.biomeId() : null;
        BiomeInstanceKey instance = reservation != null ? reservation.instance() : null;
        if (biomeId == null) {
            RegistryKey<Biome> fallback = Worlds.biomeAt(player.getServerWorld(), ballPos);
            biomeId = Worlds.biomeId(fallback);
            instance = fallback == null ? null
                    : BiomeInstanceKey.fromPosition(fallback, ballPos, config.oneCatchPerRegion);
        }

        if (config.oneCatchPerBiome && biomeId != null) {
            playerState.recordBiomeCapture(biomeId);
            player.sendMessage(Text.literal("§aCaptured §e" + speciesName + "§a in §e"
                    + Worlds.prettyBiomeName(biomeId) + "§a. No more catches here."));
        }

        if (config.oneCatchPerRegion > 0 && instance != null) {
            playerState.recordInstanceCapture(instance);
            player.sendMessage(Text.literal("§aCaptured §e" + speciesName + "§a in §e"
                    + Worlds.prettyBiomeName(instance.biomeKey()) + "§a region §f(" + instance.regionX()
                    + ", " + instance.regionZ() + ")§a."));
        }

        if (config.catchCooldownSeconds > 0) {
            playerState.setLastCatchTime(System.currentTimeMillis());
        }
        state.markDirty();
    }

    private static final Map<UUID, PendingFaint> PENDING_FAINTS = new HashMap<>();

    private static final Map<String, long[]> REGION_COUNTS = new HashMap<>();

    private static final int COUNT_CACHE_TICKS = 40;

    private static final int REGION_COUNT_LIMIT = 256;

    private static int capRefusals = 0;

    private static final Map<UUID, Long> RECENT_BATTLE_FAINTS = new HashMap<>();

    private static final Map<UUID, Pokemon> AT_ZERO_HP = new HashMap<>();

    private static final int ZERO_HP_SWEEP_TICKS = 40;

    private static final int BATTLE_CLAIM_TICKS = 40;

    private static final int IN_BATTLE_GRACE_TICKS = 20 * 60 * 10;
    private static long serverTicks = 0;

    private record PendingFaint(Pokemon pokemon, int[] ticksLeft, int[] waited) {
    }

    private static boolean isPlayerVersusPlayer(PokemonBattle battle) {
        CobblelockeState state = Cobblelocke.state();
        if (battle == null || state == null || !state.getConfig().ignorePvpFaints) {
            return false;
        }
        try {
            for (BattleSide side : battle.getSides()) {
                boolean hasPlayer = false;
                for (BattleActor actor : side.getActors()) {
                    if (actor.getPlayerUUIDs().iterator().hasNext()) {
                        hasPlayer = true;
                        break;
                    }
                }
                if (!hasPlayer) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean ownerIsBattling(Pokemon pokemon) {
        try {
            UUID ownerId = pokemon.getOwnerUUID();
            return ownerId != null
                    && com.cobblemon.mod.common.battles.BattleRegistry
                    .getBattleByParticipatingPlayerId(ownerId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static void claimAsBattleFaint(Pokemon pokemon) {
        if (pokemon == null) {
            return;
        }
        PENDING_FAINTS.remove(pokemon.getUuid());
        RECENT_BATTLE_FAINTS.put(pokemon.getUuid(), serverTicks);
        AT_ZERO_HP.put(pokemon.getUuid(), pokemon);
    }

    private static void onHealthReachedZero(Pokemon pokemon) {
        if (pokemon == null || pokemon.getOwnerUUID() == null) {
            return;
        }
        if (AT_ZERO_HP.put(pokemon.getUuid(), pokemon) != null) {
            return;
        }
        Long battleTick = RECENT_BATTLE_FAINTS.get(pokemon.getUuid());
        if (battleTick != null && serverTicks - battleTick < BATTLE_CLAIM_TICKS * 2L) {
            return;
        }
        PENDING_FAINTS.put(pokemon.getUuid(),
                new PendingFaint(pokemon, new int[]{BATTLE_CLAIM_TICKS}, new int[]{0}));
    }

    public static void tick() {
        serverTicks++;
        if (!RECENT_BATTLE_FAINTS.isEmpty() && serverTicks % 200 == 0) {
            RECENT_BATTLE_FAINTS.values().removeIf(tick -> serverTicks - tick > BATTLE_CLAIM_TICKS * 2L);
        }
        if (!AT_ZERO_HP.isEmpty() && serverTicks % ZERO_HP_SWEEP_TICKS == 0) {
            AT_ZERO_HP.values().removeIf(pokemon -> {
                try {
                    return pokemon.getCurrentHealth() > 0;
                } catch (Exception e) {
                    return true;
                }
            });
        }
        if (PENDING_FAINTS.isEmpty()) {
            return;
        }
        List<Pokemon> terrain = new java.util.ArrayList<>();
        PENDING_FAINTS.values().removeIf(pending -> {
            pending.waited()[0]++;
            if (--pending.ticksLeft()[0] > 0) {
                return false;
            }

            if (pending.waited()[0] < IN_BATTLE_GRACE_TICKS && ownerIsBattling(pending.pokemon())) {
                pending.ticksLeft()[0] = BATTLE_CLAIM_TICKS;
                return false;
            }
            terrain.add(pending.pokemon());
            return true;
        });
        for (Pokemon pokemon : terrain) {
            onFainted(pokemon, false);
        }
    }

    private static void onFainted(Pokemon pokemon, boolean inBattle) {
        if (pokemon == null) {
            return;
        }
        UUID ownerId = pokemon.getOwnerUUID();
        MinecraftServer server = Cobblelocke.getServer();
        if (ownerId == null || server == null) {
            return;
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerId);
        CobblelockeState state = Cobblelocke.state();
        if (player == null || state == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive || !config.nuzlockeModeEnabled) {
            return;
        }
        PlayerState playerState = state.getPlayer(ownerId);
        if (!playerState.hasChosenStarter()) {
            return;
        }

        String name = displayName(pokemon);

        if (config.noHealing && config.dropHeldItemsOnFaint && dropHeldItem(player, pokemon)) {
            player.sendMessage(Text.literal("§7" + name + " dropped what it was holding."));
        }

        if (!inBattle && !config.enableTerrainDeaths) {
            if (config.noHealing || config.releaseFainted) {
                player.sendMessage(Text.literal("§7" + name
                        + " fainted outside battle and can still be revived. (Terrain deaths are off)"));
            }
            return;
        }

        if (playerState.isPokemonDead(pokemon.getUuid())) {
            return;
        }
        boolean died = false;
        if (config.noHealing) {
            playerState.markPokemonDead(pokemon.getUuid());
            state.markDirty();
            died = true;
            player.sendMessage(Text.literal("§4" + name + " has fallen and cannot be revived! (Permadeath)"));

            if (config.releaseFainted) {
                releaseFainted(player, pokemon);
                player.sendMessage(Text.literal("§c" + name + " has fainted and been released! (Nuzlocke)"));
            }
        }

        if (died && config.noDuplicates && config.allowRepeatAfterFaint) {
            freeEvolutionLineIfGone(player, playerState, pokemon);
            state.markDirty();
        }
    }

    private static void freeEvolutionLineIfGone(ServerPlayerEntity player, PlayerState playerState, Pokemon fallen) {
        Set<String> line = EvolutionLine.of(fallen.getSpecies());
        List<Pokemon> owned = new java.util.ArrayList<>();
        try {
            Cobblemon.INSTANCE.getStorage().getParty(player).forEach(owned::add);
            Cobblemon.INSTANCE.getStorage().getPC(player).forEach(owned::add);
        } catch (Exception e) {
            return;
        }
        for (Pokemon pokemon : owned) {
            if (pokemon.getUuid().equals(fallen.getUuid()) || playerState.isPokemonDead(pokemon.getUuid())) {
                continue;
            }
            if (line.contains(pokemon.getSpecies().getName())) {
                return;
            }
        }
        line.forEach(playerState::forgetSpecies);
        player.sendMessage(Text.literal("§7The " + fallen.getSpecies().getName()
                + " line can be caught again. (Repeat after fainting)"));
    }

    private static boolean releaseFainted(ServerPlayerEntity player, Pokemon pokemon) {
        try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            return party.remove(pokemon);
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not release {}: {}", displayName(pokemon), e.toString());
            return false;
        }
    }

    private static boolean dropHeldItem(ServerPlayerEntity player, Pokemon pokemon) {
        try {
            ItemStack held = pokemon.heldItem();
            if (held.isEmpty()) {
                return false;
            }
            ItemStack dropped = pokemon.swapHeldItem(ItemStack.EMPTY, false, true);
            if (dropped.isEmpty()) {
                dropped = held.copy();
            }
            if (dropped.isEmpty()) {
                return false;
            }

            ServerWorld world = player.getServerWorld();
            Vec3d where = player.getPos();
            PokemonEntity entity = pokemon.getEntity();
            if (entity != null && entity.getWorld() instanceof ServerWorld entityWorld) {
                Vec3d entityPos = entity.getPos();
                if (Double.isFinite(entityPos.x) && Double.isFinite(entityPos.y) && Double.isFinite(entityPos.z)) {
                    world = entityWorld;
                    where = entityPos;
                }
            }
            ItemEntity itemEntity = new ItemEntity(world, where.x, where.y, where.z, dropped);
            itemEntity.setToDefaultPickupDelay();
            world.spawnEntity(itemEntity);
            return true;
        } catch (Exception e) {
            Cobblelocke.LOGGER.warn("Could not drop a held item on release: {}", e.toString());
            return false;
        }
    }

    private static void onHealed(PokemonHealedEvent event) {
        Pokemon pokemon = event.getPokemon();
        UUID ownerId = pokemon == null ? null : pokemon.getOwnerUUID();
        CobblelockeState state = Cobblelocke.state();
        if (ownerId == null || state == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive || !config.nuzlockeModeEnabled || !config.noHealing) {
            return;
        }
        PlayerState playerState = state.getPlayer(ownerId);
        if (!playerState.isPokemonDead(pokemon.getUuid())) {
            return;
        }
        event.cancel();

        MinecraftServer server = Cobblelocke.getServer();
        ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(ownerId);
        if (player != null) {
            player.sendMessage(Text.literal("§c" + displayName(pokemon)
                    + " cannot be healed - they have fallen! (Permadeath)"));
        }
    }

    private static void onSpawn(SpawnEvent<?> event) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        int chunks = config.nuzlocke(true) ? config.capSpawningPerRegionChunks : 0;
        if (!config.runActive || chunks <= 0) {
            return;
        }
        if (!(event.getEntity() instanceof PokemonEntity entity)) {
            return;
        }
        if (!(entity.getWorld() instanceof ServerWorld world) || entity.getOwnerUuid() != null) {
            return;
        }
        BlockPos pos = entity.getBlockPos();
        String regionId = regionId(world, pos, chunks);
        Box region = regionBox(world, pos, chunks);

        List<ServerPlayerEntity> nearby = new java.util.ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!state.getPlayer(player.getUuid()).hasChosenStarter()) {
                continue;
            }
            boolean insideRegion = player.getX() >= region.minX && player.getX() < region.maxX
                    && player.getZ() >= region.minZ && player.getZ() < region.maxZ;
            if (insideRegion || player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) <= 16384.0) {
                nearby.add(player);
            }
        }
        if (nearby.isEmpty()) {
            return;
        }
        if (config.capSpawningPerRegionMemory && regionClosed(state, config, regionId, nearby)) {
            event.cancel();
            return;
        }

        int cap = Math.max(1, config.capSpawningPerRegionCount);
        long now = world.getServer().getTicks();
        long[] counted = REGION_COUNTS.get(regionId);
        int living;
        long expiresAt;
        if (counted != null && now < counted[1]) {
            living = (int) counted[0];
            expiresAt = counted[1];
        } else {
            living = world.getEntitiesByClass(PokemonEntity.class, region,
                    other -> other != entity && other.isAlive() && other.getOwnerUuid() == null).size();
            expiresAt = now + COUNT_CACHE_TICKS;
            pruneRegionCounts(now);
        }

        if (living >= cap) {
            event.cancel();
            if (capRefusals++ == 0) {
                Cobblelocke.LOGGER.info("Spawn cap active: refused a spawn in region {} which already "
                        + "holds {} wild Cobblemon (cap {})", regionId, living, cap);
            }
            REGION_COUNTS.put(regionId, new long[]{living, expiresAt});
            if (config.capSpawningPerRegionMemory) {
                closeRegion(state, config, regionId, nearby);
            }
            return;
        }
        REGION_COUNTS.put(regionId, new long[]{living + 1, expiresAt});
        if (living + 1 >= cap && config.capSpawningPerRegionMemory) {
            closeRegion(state, config, regionId, nearby);
        }
    }

    private static void pruneRegionCounts(long now) {
        if (REGION_COUNTS.size() > REGION_COUNT_LIMIT) {
            REGION_COUNTS.values().removeIf(entry -> now >= entry[1]);
        }
    }

    private static String regionId(ServerWorld world, BlockPos pos, int chunks) {
        return world.getRegistryKey().getValue() + "/" + chunks + "c:"
                + Math.floorDiv(pos.getX() >> 4, chunks) + "," + Math.floorDiv(pos.getZ() >> 4, chunks);
    }

    private static Box regionBox(ServerWorld world, BlockPos pos, int chunks) {
        double x = Math.floorDiv(pos.getX() >> 4, chunks) * chunks * 16.0;
        double z = Math.floorDiv(pos.getZ() >> 4, chunks) * chunks * 16.0;
        double side = chunks * 16.0;
        return new Box(x, world.getBottomY(), z, x + side, world.getTopY(), z + side);
    }

    private static boolean regionClosed(CobblelockeState state, CobblelockeConfig config, String regionId,
                                        List<ServerPlayerEntity> nearby) {
        if (config.capSpawningPerRegionByPlayer) {
            return state.isRegionCapped(regionId);
        }
        for (ServerPlayerEntity player : nearby) {
            if (!state.getPlayer(player.getUuid()).isRegionCapped(regionId)) {
                return false;
            }
        }
        return true;
    }

    private static void closeRegion(CobblelockeState state, CobblelockeConfig config, String regionId,
                                    List<ServerPlayerEntity> nearby) {
        if (config.capSpawningPerRegionByPlayer) {
            state.markRegionCapped(regionId);
            return;
        }
        for (ServerPlayerEntity player : nearby) {
            state.getPlayer(player.getUuid()).markRegionCapped(regionId);
        }
        state.markDirty();
    }

    private static void unwindBattleCapture(ThrownPokeballHitEvent event) {
        try {
            PokemonSideDelegate delegate = event.getPokemon().getDelegate();
            if (!(delegate instanceof PokemonServerDelegate serverDelegate)) {
                return;
            }
            PokemonBattle battle = serverDelegate.getBattle();
            if (battle == null) {
                return;
            }
            BattleCaptureAction action = battle.getCaptureActions().stream()
                    .filter(candidate -> candidate.getPokeBallEntity() == event.getPokeBall())
                    .findFirst()
                    .orElse(null);
            if (action == null) {
                return;
            }
            battle.sendUpdate(new BattleCaptureEndPacket(action.getTargetPokemon().getPNX(), false));
            battle.finishCaptureAction(action);
            event.getPokeBall().getCaptureFuture()
                    .completeExceptionally(new CancellationException("Capture blocked by Cobblelocke"));
        } catch (Exception e) {
            Cobblelocke.LOGGER.debug("Could not unwind a battle capture: {}", e.toString());
        }
    }

    public static void resyncInventory(ServerPlayerEntity player) {
        try {
            player.getInventory().markDirty();
            player.currentScreenHandler.updateToClient();
            player.playerScreenHandler.updateToClient();
        } catch (Exception ignored) {
        }
    }

    private static String displayName(Pokemon pokemon) {
        try {
            return pokemon.getNickname() != null
                    ? pokemon.getNickname().getString()
                    : pokemon.getSpecies().getName();
        } catch (Exception e) {
            return "Pokémon";
        }
    }

    public static void enforcePermadeath(ServerPlayerEntity player, PlayerPartyStore party) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        if (!config.runActive || !config.nuzlockeModeEnabled || !config.noHealing) {
            return;
        }
        PlayerState playerState = state.getPlayer(player.getUuid());
        Set<UUID> dead = playerState.getDeadPokemon();
        if (dead.isEmpty()) {
            return;
        }
        for (Pokemon pokemon : party) {
            if (!dead.contains(pokemon.getUuid())) {
                continue;
            }
            if (pokemon.getCurrentHealth() > 0) {
                pokemon.setCurrentHealth(0);
            }
            pokemon.setFaintedTimer(1);
        }
    }
}
