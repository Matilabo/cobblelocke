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
import net.minecraft.nbt.NbtCompound;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.api.spawning.SpawnCause;
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
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import com.cobblelocke.util.Lang;
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

        MutableText refusal = catchRefusal(player, target, targetEntity, targetWorld, targetPos);
        if (refusal != null) {
            unwindBattleCapture(event);
            event.cancel();
            resyncInventory(player);
            player.sendMessage(refusal);
            return;
        }

        CobblelockeState state = Cobblelocke.state();
        if (state == null || !state.getConfig().runActive || target == null) {
            return;
        }

        RegistryKey<Biome> biome = Worlds.biomeAt(targetWorld, targetPos);
        PlayerState reserving = state.getPlayer(player.getUuid());
        CobblelockeConfig config = state.getConfig();
        boolean shinyPass = config.shinyClause && target.getShiny();
        Claim claim = shinyPass ? new Claim(null, null) : claim(config, reserving,
                EventLockService.catchBiome(reserving, targetEntity, Worlds.biomeId(biome)),
                biome == null ? null : BiomeInstanceKey.fromPosition(biome, targetPos, config.oneCatchPerRegion),
                EventLockService.triggerBiome(reserving, targetEntity) != null,
                EventLockService.isExtraCatch(config, reserving, targetEntity),
                CatchReservations.activeFor(player.getUuid(), target.getUuid()));
        CatchReservations.reserve(new CatchReservations.Reservation(
                player.getUuid(),
                target.getUuid(),
                claim.biomeId(),
                claim.region(),
                EvolutionLine.of(target.getSpecies()),
                shinyPass,
                System.currentTimeMillis()), event.getPokeBall());
    }

    public static MutableText catchRefusal(ServerPlayerEntity player, Pokemon target, PokemonEntity targetEntity,
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
        boolean extraCatch = EventLockService.isExtraCatch(config, playerState, targetEntity);

        MutableText eventRefusal = EventLockService.catchRefusal(player, playerState, config,
                EventLockService.targetArea(playerState, targetEntity, world, pos, config), targetEntity);
        if (eventRefusal != null) {
            return eventRefusal;
        }

        if (!config.nuzlockeModeEnabled) {
            return null;
        }

        if (config.shinyClause && target != null && target.getShiny()) {
            return null;
        }

        if (config.onlyCatchInBattle && targetEntity != null && !targetEntity.isBattling()) {
            return Lang.tr("catch.only_in_battle", "§cYou can only catch Pokémon you are battling. (Only Catch In-Battle)");
        }

        UUID targetId = target == null ? null : target.getUuid();
        List<CatchReservations.Reservation> inProgress = CatchReservations.activeFor(player.getUuid(), targetId)
                .stream()
                .filter(reservation -> !reservation.shinyPass())
                .toList();

        if (config.catchCooldownSeconds > 0) {
            if (playerState.isOnCatchCooldown(config.catchCooldownSeconds)) {
                long remaining = playerState.remainingCooldownMillis(config.catchCooldownSeconds);
                return Lang.tr("catch.cooldown", "§cCatch cooldown active. Wait %s.", Worlds.formatDuration(remaining));
            }
            if (!inProgress.isEmpty()) {
                return Lang.tr("catch.cooldown_in_progress", "§cFinish your current catch first. (Catch Cooldown)");
            }
        }

        if (config.noDuplicates && target != null) {
            String caught = alreadyCaughtInLine(playerState, target);
            if (caught != null) {
                String species = target.getSpecies().getName();
                return caught.equals(species)
                        ? Lang.tr("catch.duplicate_species", "§cYou already caught a %s! (Duplicate Clause)",
                                Lang.species(target.getSpecies()))
                        : Lang.tr("catch.duplicate_line", "§cYou already caught %s, same evolution line! (Duplicate Clause)",
                                Lang.species(com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(
                                        caught.toLowerCase(java.util.Locale.ROOT))));
            }
            Set<String> line = EvolutionLine.of(target.getSpecies());
            for (CatchReservations.Reservation reservation : inProgress) {
                if (reservation.evolutionLine().stream().anyMatch(line::contains)) {
                    return Lang.tr("catch.duplicate_in_progress",
                            "§cYou are already catching a Pokémon from that evolution line! (Duplicate Clause)");
                }
            }
        }

        BiomeInstanceKey regionKey = config.oneCatchPerRegion > 0 && biome != null
                ? BiomeInstanceKey.fromPosition(biome, pos, config.oneCatchPerRegion) : null;
        if (!extraCatch && config.oneCatchPerBiome && biomeId != null && regionKey != null) {
            if (!biomeTaken(playerState, biomeId, inProgress) || !regionTaken(playerState, regionKey, inProgress)) {
                return null;
            }
            String coords = "(" + regionKey.regionX() + ", " + regionKey.regionZ() + ")";
            if (playerState.hasCapturedInBiome(biomeId) && playerState.hasCapturedInInstance(regionKey)) {
                return Lang.tr("catch.biome_and_region_used",
                        "§cYou already used both the %1$s§c catch and its region §f%2$s§c catch!",
                        Lang.hl(Lang.biome(biomeId), Formatting.YELLOW), coords);
            }
            return Lang.tr("catch.region_in_progress",
                    "§cYou are already catching a Pokémon in the %1$s§c region §f%2$s§c!",
                    Lang.hl(Lang.biome(biomeId), Formatting.YELLOW), coords);
        }

        if (config.oneCatchPerBiome && biomeId != null && !extraCatch) {
            if (playerState.hasCapturedInBiome(biomeId)) {
                return Lang.tr("catch.biome_used", "§cYou already caught a Pokémon in %s§c!",
                        Lang.hl(Lang.biome(biomeId), Formatting.YELLOW));
            }
            for (CatchReservations.Reservation reservation : inProgress) {
                if (biomeId.equals(reservation.biomeId())) {
                    return Lang.tr("catch.biome_in_progress", "§cYou are already catching a Pokémon in %s§c!",
                            Lang.hl(Lang.biome(biomeId), Formatting.YELLOW));
                }
            }
        }

        if (config.oneCatchPerRegion > 0 && biome != null && !extraCatch) {
            BiomeInstanceKey key = BiomeInstanceKey.fromPosition(biome, pos, config.oneCatchPerRegion);
            MutableText regionBiome = Lang.hl(Lang.biome(biome), Formatting.YELLOW);
            String coords = "(" + key.regionX() + ", " + key.regionZ() + ")";
            if (playerState.hasCapturedInInstance(key)) {
                return Lang.tr("catch.region_used", "§cYou already caught a Pokémon in the %1$s§c region §f%2$s§c!",
                        regionBiome, coords);
            }
            for (CatchReservations.Reservation reservation : inProgress) {
                if (key.equals(reservation.instance())) {
                    return Lang.tr("catch.region_in_progress",
                            "§cYou are already catching a Pokémon in the %1$s§c region §f%2$s§c!", regionBiome, coords);
                }
            }
        }
        return null;
    }

    // What a catch uses up. With One Catch Per Biome and One Catch Per Region both on, a normal catch
    // takes the region's catch first and the biome's extra catch after it; an event encounter is
    // either an extra catch (nothing) or takes both.
    private record Claim(String biomeId, BiomeInstanceKey region) {
    }

    private static Claim claim(CobblelockeConfig config, PlayerState playerState, String biomeId,
                               BiomeInstanceKey region, boolean eventCatch, boolean extraCatch,
                               List<CatchReservations.Reservation> inProgress) {
        String biomeClaim = config.oneCatchPerBiome ? biomeId : null;
        BiomeInstanceKey regionClaim = config.oneCatchPerRegion > 0 ? region : null;
        if (extraCatch) {
            return new Claim(null, null);
        }
        if (eventCatch || biomeClaim == null || regionClaim == null) {
            return new Claim(biomeClaim, regionClaim);
        }
        if (!regionTaken(playerState, regionClaim, inProgress)) {
            return new Claim(null, regionClaim);
        }
        return new Claim(biomeClaim, null);
    }

    private static boolean biomeTaken(PlayerState playerState, String biomeId,
                                      List<CatchReservations.Reservation> inProgress) {
        return playerState.hasCapturedInBiome(biomeId)
                || inProgress.stream().anyMatch(reservation -> biomeId.equals(reservation.biomeId()));
    }

    private static boolean regionTaken(PlayerState playerState, BiomeInstanceKey region,
                                       List<CatchReservations.Reservation> inProgress) {
        return playerState.hasCapturedInInstance(region)
                || inProgress.stream().anyMatch(reservation -> region.equals(reservation.instance()));
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
        boolean eventCatch = EventLockService.onPokemonCaptured(player, pokemon);
        if (!config.nuzlockeModeEnabled) {
            return;
        }
        boolean extraCatch = eventCatch && config.firstCatchEventLocked && config.isExtraCatch;
        PlayerState playerState = state.getPlayer(player.getUuid());
        if (config.requireNicknames) {
            NicknameService.requestAfterCapture(player, pokemon);
        }

        String speciesName = pokemon.getSpecies().getName();
        boolean shinyPass = config.shinyClause && pokemon.getShiny();

        if (shinyPass) {
            player.sendMessage(Lang.tr("capture.shiny", "§6★ Shiny %s caught! §7(Shiny Clause - bypasses restrictions)",
                    Lang.species(pokemon.getSpecies())));
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
        String biomeId;
        BiomeInstanceKey instance;
        if (reservation != null) {
            biomeId = reservation.biomeId();
            instance = reservation.instance();
        } else {
            RegistryKey<Biome> fallback = Worlds.biomeAt(player.getServerWorld(), ballPos);
            Claim claim = claim(config, playerState, Worlds.biomeId(fallback), fallback == null ? null
                            : BiomeInstanceKey.fromPosition(fallback, ballPos, config.oneCatchPerRegion),
                    eventCatch, extraCatch, List.of());
            biomeId = claim.biomeId();
            instance = claim.region();
        }

        if (config.oneCatchPerBiome && biomeId != null && !extraCatch) {
            playerState.recordBiomeCapture(biomeId);
            playerState.recordCatch(PlayerState.biomeRecord(biomeId), speciesName, pokemon.getUuid());
            player.sendMessage(Lang.tr("capture.biome", "§aCaptured %1$s§a in %2$s§a. No more catches here.",
                    Lang.hl(Lang.species(pokemon.getSpecies()), Formatting.YELLOW), Lang.hl(Lang.biome(biomeId), Formatting.YELLOW)));
        }

        if (config.oneCatchPerRegion > 0 && instance != null && !extraCatch) {
            playerState.recordInstanceCapture(instance);
            playerState.recordCatch(PlayerState.regionRecord(instance), speciesName, pokemon.getUuid());
            player.sendMessage(Lang.tr("capture.region", "§aCaptured %1$s§a in the %2$s§a region §f%3$s§a.",
                    Lang.hl(Lang.species(pokemon.getSpecies()), Formatting.YELLOW), Lang.hl(Lang.biome(instance.biomeKey()), Formatting.YELLOW),
                    "(" + instance.regionX() + ", " + instance.regionZ() + ")"));
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

    private static final String KEY_SPAWNED_FOR = "cobblelocke_spawned_for";

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

        MutableText name = Lang.pokemon(pokemon);

        if (config.noHealing && config.dropHeldItemsOnFaint && dropHeldItem(player, pokemon)) {
            player.sendMessage(Lang.tr("faint.dropped", "§7%s dropped what it was holding.", name));
        }

        if (!inBattle && !config.enableTerrainDeaths) {
            if (config.noHealing || config.releaseFainted) {
                player.sendMessage(Lang.tr("faint.terrain_off",
                        "§7%s fainted outside battle and can still be revived. (Terrain deaths are off)", name));
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
            player.sendMessage(Lang.tr("faint.permadeath", "§4%s has fallen and cannot be revived! (Permadeath)", name));

            if (config.releaseFainted) {
                releaseFainted(player, pokemon);
                player.sendMessage(Lang.tr("faint.released", "§c%s has fainted and been released! (Nuzlocke)", name));
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
        player.sendMessage(Lang.tr("faint.line_free", "§7The %s line can be caught again. (Repeat after fainting)",
                Lang.species(fallen.getSpecies())));
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
            player.sendMessage(Lang.tr("faint.no_heal", "§c%s cannot be healed, they have fallen! (Permadeath)",
                    Lang.pokemon(pokemon)));
        }
    }

    private static void onSpawn(SpawnEvent<?> event) {
        CobblelockeState state = Cobblelocke.state();
        if (state == null || !state.isSpawnCapActive()) {
            return;
        }
        if (!(event.getEntity() instanceof PokemonEntity entity)) {
            return;
        }
        if (!(entity.getWorld() instanceof ServerWorld world) || entity.getOwnerUuid() != null) {
            return;
        }
        CobblelockeConfig config = state.getConfig();
        int chunks = config.capSpawningPerRegionChunks;
        BlockPos pos = entity.getBlockPos();
        String regionId = regionId(world, pos, chunks);
        Box region = regionBox(world, pos, chunks);
        boolean perPlayer = config.capSpawningPerRegionByPlayer;
        boolean memory = config.capSpawningPerRegionMemory;

        UUID owner = null;
        if (perPlayer) {
            owner = spawnedFor(event, world, region, pos);
            if (owner == null) {
                return;
            }
        }

        if (memory && regionClosed(state, regionId, owner)) {
            event.cancel();
            return;
        }

        int cap = Math.max(1, config.capSpawningPerRegionCount);
        String countKey = owner == null ? regionId : regionId + "|" + owner;
        UUID countedFor = owner;
        long now = world.getServer().getTicks();
        long[] counted = REGION_COUNTS.get(countKey);
        int living;
        long expiresAt;
        if (counted != null && now < counted[1]) {
            living = (int) counted[0];
            expiresAt = counted[1];
        } else {
            living = world.getEntitiesByClass(PokemonEntity.class, region,
                    other -> other != entity && other.isAlive() && other.getOwnerUuid() == null
                            && (countedFor == null || countedFor.equals(spawnedForTag(other)))).size();
            expiresAt = now + COUNT_CACHE_TICKS;
            pruneRegionCounts(now);
        }

        if (living >= cap) {
            event.cancel();
            if (capRefusals++ == 0) {
                Cobblelocke.LOGGER.info("Spawn cap active: refused a spawn in region {} which already "
                        + "holds {} wild Cobblemon (cap {}{})", regionId, living, cap,
                        owner == null ? ", shared" : ", per player");
            }
            REGION_COUNTS.put(countKey, new long[]{living, expiresAt});
            if (memory) {
                closeRegion(state, regionId, owner);
            }
            return;
        }
        if (owner != null) {
            try {
                entity.getPokemon().getPersistentData().putUuid(KEY_SPAWNED_FOR, owner);
            } catch (Exception ignored) {
            }
        }
        REGION_COUNTS.put(countKey, new long[]{living + 1, expiresAt});
        if (living + 1 >= cap && memory) {
            closeRegion(state, regionId, owner);
        }
    }

    private static UUID spawnedFor(SpawnEvent<?> event, ServerWorld world, Box region, BlockPos pos) {
        try {
            SpawnCause cause = event.getCause();
            if (cause != null && cause.getEntity() instanceof ServerPlayerEntity player) {
                return player.getUuid();
            }
        } catch (Exception ignored) {
        }
        try {
            if (event.getSpawner() instanceof PlayerSpawner spawner) {
                return spawner.getUuid();
            }
        } catch (Exception ignored) {
        }
        ServerPlayerEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity player : world.getPlayers()) {
            boolean insideRegion = player.getX() >= region.minX && player.getX() < region.maxX
                    && player.getZ() >= region.minZ && player.getZ() < region.maxZ;
            double distance = player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ());
            if ((insideRegion || distance <= 16384.0) && distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest == null ? null : nearest.getUuid();
    }

    private static UUID spawnedForTag(PokemonEntity entity) {
        try {
            NbtCompound data = entity.getPokemon().getPersistentData();
            return data != null && data.containsUuid(KEY_SPAWNED_FOR) ? data.getUuid(KEY_SPAWNED_FOR) : null;
        } catch (Exception e) {
            return null;
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

    private static boolean regionClosed(CobblelockeState state, String regionId, UUID owner) {
        return owner == null
                ? state.isRegionCapped(regionId)
                : state.getPlayer(owner).isRegionCapped(regionId);
    }

    private static void closeRegion(CobblelockeState state, String regionId, UUID owner) {
        if (owner == null) {
            state.markRegionCapped(regionId);
            return;
        }
        state.getPlayer(owner).markRegionCapped(regionId);
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
