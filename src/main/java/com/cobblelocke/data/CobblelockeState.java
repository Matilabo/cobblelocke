package com.cobblelocke.data;

import com.cobblelocke.Cobblelocke;
import com.cobblelocke.config.CobblelockeConfig;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CobblelockeState extends PersistentState {
    private static final String DATA_NAME = "cobblelocke";

    private final CobblelockeConfig config = new CobblelockeConfig();
    private final Map<UUID, PlayerState> players = new HashMap<>();

    private UUID configOwner = null;

    private int configGeneration = 1;

    private final java.util.Set<String> cappedRegions = new java.util.HashSet<>();

    private boolean runEverStarted = false;

    private String spawnCapSignature = null;

    private static final Type<CobblelockeState> TYPE = new Type<>(
            CobblelockeState::new, CobblelockeState::readNbt, DataFixTypes.LEVEL);

    public static CobblelockeState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, DATA_NAME);
    }

    public CobblelockeConfig getConfig() {
        return config;
    }

    public int getConfigGeneration() {
        return configGeneration;
    }

    public void saveConfig(CobblelockeConfig updated) {
        saveConfig(updated, true);
    }

    public void saveConfig(CobblelockeConfig updated, boolean reroll) {
        config.copyFrom(updated);
        if (reroll) {
            configGeneration++;
        }
        if (config.runActive) {
            runEverStarted = true;
        }
        refreshSpawnCapSignature();
        markDirty();
    }

    public boolean isSpawnCapActive() {
        if (!config.nuzlockeModeEnabled || config.capSpawningPerRegionChunks <= 0) {
            return false;
        }
        return config.spawnCapAlwaysOn || runEverStarted || config.runActive;
    }

    public boolean hasRunEverStarted() {
        return runEverStarted;
    }

    public void turnSpawnCapOff() {
        config.spawnCapAlwaysOn = false;
        runEverStarted = false;
        clearSpawnCapMemory();
        markDirty();
    }

    public void clearSpawnCapMemory() {
        cappedRegions.clear();
        players.values().forEach(PlayerState::clearCappedRegions);
        markDirty();
    }

    private void refreshSpawnCapSignature() {
        String current = config.spawnCapSignature();
        if (spawnCapSignature != null && !spawnCapSignature.equals(current)) {
            clearSpawnCapMemory();
            Cobblelocke.LOGGER.info("The spawn cap settings changed, so remembered regions were cleared");
        }
        spawnCapSignature = current;
    }

    public UUID getConfigOwner() {
        return configOwner;
    }

    public void claimConfigOwner(ServerPlayerEntity player) {
        if (configOwner != null || player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        boolean host = server != null && server.isSingleplayer() && server.isHost(player.getGameProfile());
        if (!host && !player.hasPermissionLevel(2)) {
            return;
        }
        configOwner = player.getUuid();
        markDirty();
        Cobblelocke.LOGGER.info("{} is the config owner for this world", player.getName().getString());
    }

    public boolean isAdmin(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        if (player.getUuid().equals(configOwner)) {
            return true;
        }
        if (player.hasPermissionLevel(2)) {
            return true;
        }
        MinecraftServer server = player.getServer();
        return server != null && server.isSingleplayer() && server.isHost(player.getGameProfile());
    }

    public String commandMode() {
        String mode = config.serverConfigMode == null ? "read-only"
                : config.serverConfigMode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (mode) {
            case "any" -> "any";
            case "config-only", "configonly", "config_only" -> "config-only";
            case "read-only", "readonly", "read_only" -> "read-only";
            default -> "admin";
        };
    }

    public boolean canEditConfig(ServerPlayerEntity player) {
        if (isAdmin(player)) {
            return true;
        }
        if (player == null) {
            return false;
        }
        String mode = commandMode();
        return mode.equals("config-only") || mode.equals("any");
    }

    public boolean canUseCommand(ServerPlayerEntity player, boolean opensTheMenu) {
        if (isAdmin(player)) {
            return true;
        }
        if (player == null) {
            return false;
        }
        return switch (commandMode()) {
            case "any" -> true;
            case "config-only", "read-only" -> opensTheMenu;
            default -> false;
        };
    }

    public PlayerState getPlayer(UUID playerId) {
        return players.computeIfAbsent(playerId, PlayerState::new);
    }

    public Map<UUID, PlayerState> getPlayers() {
        return players;
    }

    public boolean isRegionCapped(String regionId) {
        return cappedRegions.contains(regionId);
    }

    public void markRegionCapped(String regionId) {
        cappedRegions.add(regionId);
        markDirty();
    }

    public void resetRun() {
        players.values().forEach(PlayerState::resetProgress);
        markDirty();
    }

    public void resetRun(UUID playerId) {
        PlayerState player = players.get(playerId);
        if (player != null) {
            player.resetProgress();
        }
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putString("Config", config.toJson());
        nbt.putInt("ConfigGeneration", configGeneration);
        if (configOwner != null) {
            nbt.putString("ConfigOwner", configOwner.toString());
        }
        net.minecraft.nbt.NbtList regions = new net.minecraft.nbt.NbtList();
        cappedRegions.forEach(region -> regions.add(net.minecraft.nbt.NbtString.of(region)));
        nbt.put("CappedRegions", regions);
        nbt.putBoolean("RunEverStarted", runEverStarted);
        if (spawnCapSignature != null) {
            nbt.putString("SpawnCapSignature", spawnCapSignature);
        }
        NbtCompound playersTag = new NbtCompound();
        players.forEach((id, state) -> playersTag.put(id.toString(), state.toNbt()));
        nbt.put("Players", playersTag);
        return nbt;
    }

    private static CobblelockeState readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CobblelockeState state = new CobblelockeState();
        state.config.copyFrom(CobblelockeConfig.fromJson(nbt.getString("Config")));
        state.configGeneration = Math.max(1, nbt.getInt("ConfigGeneration"));
        if (nbt.contains("ConfigOwner")) {
            try {
                state.configOwner = UUID.fromString(nbt.getString("ConfigOwner"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        net.minecraft.nbt.NbtList regions = nbt.getList("CappedRegions", net.minecraft.nbt.NbtElement.STRING_TYPE);
        for (int i = 0; i < regions.size(); i++) {
            state.cappedRegions.add(regions.getString(i));
        }
        state.runEverStarted = nbt.getBoolean("RunEverStarted") || state.config.runActive;
        state.spawnCapSignature = nbt.contains("SpawnCapSignature")
                ? nbt.getString("SpawnCapSignature")
                : state.config.spawnCapSignature();
        NbtCompound playersTag = nbt.getCompound("Players");
        for (String key : playersTag.getKeys()) {
            try {
                PlayerState player = PlayerState.fromNbt(playersTag.getCompound(key));
                state.players.put(player.getPlayerId(), player);
            } catch (Exception e) {
                Cobblelocke.LOGGER.warn("Dropping unreadable player entry {}: {}", key, e.toString());
            }
        }
        return state;
    }
}
