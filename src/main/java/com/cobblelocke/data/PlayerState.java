package com.cobblelocke.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerState {
    private final UUID playerId;

    private boolean hasChosenStarter = false;

    private final Set<UUID> deadPokemon = new HashSet<>();

    private final Set<String> caughtSpecies = new HashSet<>();

    private final Map<String, Long> biomeCaptures = new HashMap<>();

    private final Set<BiomeInstanceKey> instanceCaptures = new HashSet<>();
    private final Set<BiomeInstanceKey> instanceSpawns = new HashSet<>();

    private final Set<String> cappedRegions = new HashSet<>();

    private long lastCatchTime = 0L;

    private String startingBiome = null;

    private final Set<String> eventBiomes = new HashSet<>();

    private boolean leftStartingBiome = false;

    private final Map<String, UUID> pendingEvents = new HashMap<>();

    private final Set<UUID> pendingNicknames = new HashSet<>();

    public PlayerState(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean hasChosenStarter() {
        return hasChosenStarter;
    }

    public void setHasChosenStarter(boolean chosen) {
        this.hasChosenStarter = chosen;
    }

    public boolean isPokemonDead(UUID pokemonId) {
        return deadPokemon.contains(pokemonId);
    }

    public void markPokemonDead(UUID pokemonId) {
        deadPokemon.add(pokemonId);
    }

    public Set<UUID> getDeadPokemon() {
        return deadPokemon;
    }

    public boolean hasCaughtSpecies(String species) {
        return caughtSpecies.contains(species);
    }

    public void recordSpecies(String species) {
        caughtSpecies.add(species);
    }

    public void forgetSpecies(String species) {
        caughtSpecies.remove(species);
    }

    public Set<String> getCaughtSpecies() {
        return caughtSpecies;
    }

    public boolean hasCapturedInBiome(String biomeKey) {
        return biomeCaptures.containsKey(biomeKey);
    }

    public void recordBiomeCapture(String biomeKey) {
        biomeCaptures.put(biomeKey, System.currentTimeMillis());
    }

    public boolean hasCapturedInInstance(BiomeInstanceKey key) {
        return instanceCaptures.contains(key);
    }

    public void recordInstanceCapture(BiomeInstanceKey key) {
        instanceCaptures.add(key);
    }

    public boolean isRegionCapped(String regionId) {
        return cappedRegions.contains(regionId);
    }

    public void markRegionCapped(String regionId) {
        cappedRegions.add(regionId);
    }

    public void clearCappedRegions() {
        cappedRegions.clear();
    }

    public boolean hasSpawnInInstance(BiomeInstanceKey key) {
        return instanceSpawns.contains(key);
    }

    public void recordInstanceSpawn(BiomeInstanceKey key) {
        instanceSpawns.add(key);
    }

    public long getLastCatchTime() {
        return lastCatchTime;
    }

    public void setLastCatchTime(long time) {
        this.lastCatchTime = time;
    }

    public boolean isOnCatchCooldown(int cooldownSeconds) {
        return remainingCooldownMillis(cooldownSeconds) > 0L;
    }

    public long remainingCooldownMillis(int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - lastCatchTime;
        return Math.max(0L, cooldownSeconds * 1000L - elapsed);
    }

    public String getStartingBiome() {
        return startingBiome;
    }

    public void setStartingBiome(String biome) {
        this.startingBiome = biome;
    }

    public boolean hasEventFiredIn(String biomeKey) {
        return eventBiomes.contains(biomeKey);
    }

    public void markEventFired(String biomeKey) {
        eventBiomes.add(biomeKey);
    }

    public boolean hasLeftStartingBiome() {
        return leftStartingBiome;
    }

    public void setLeftStartingBiome(boolean left) {
        this.leftStartingBiome = left;
    }

    public UUID getPendingEvent(String biomeKey) {
        return pendingEvents.get(biomeKey);
    }

    public void setPendingEvent(String biomeKey, UUID entityId) {
        pendingEvents.put(biomeKey, entityId);
    }

    public void clearPendingEvent(String biomeKey) {
        pendingEvents.remove(biomeKey);
    }

    public Map<String, UUID> getPendingEvents() {
        return pendingEvents;
    }

    public Set<UUID> getPendingNicknames() {
        return pendingNicknames;
    }

    public void addPendingNickname(UUID pokemonId) {
        pendingNicknames.add(pokemonId);
    }

    public void removePendingNickname(UUID pokemonId) {
        pendingNicknames.remove(pokemonId);
    }

    public void resetProgress() {
        pendingEvents.clear();
        hasChosenStarter = false;
        deadPokemon.clear();
        caughtSpecies.clear();
        biomeCaptures.clear();
        instanceCaptures.clear();
        instanceSpawns.clear();
        lastCatchTime = 0L;
        startingBiome = null;
        eventBiomes.clear();
        leftStartingBiome = false;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putString("Id", playerId.toString());
        tag.putBoolean("ChosenStarter", hasChosenStarter);
        tag.putLong("LastCatchTime", lastCatchTime);
        tag.putBoolean("LeftStartingBiome", leftStartingBiome);
        if (startingBiome != null) {
            tag.putString("StartingBiome", startingBiome);
        }

        long[] dead = new long[deadPokemon.size() * 2];
        int index = 0;
        for (UUID id : deadPokemon) {
            dead[index++] = id.getMostSignificantBits();
            dead[index++] = id.getLeastSignificantBits();
        }
        tag.putLongArray("DeadPokemon", dead);

        tag.put("CaughtSpecies", stringList(caughtSpecies));
        tag.put("EventBiomes", stringList(eventBiomes));

        NbtCompound captures = new NbtCompound();
        biomeCaptures.forEach(captures::putLong);
        tag.put("BiomeCaptures", captures);

        tag.put("InstanceCaptures", instanceList(instanceCaptures));
        tag.put("InstanceSpawns", instanceList(instanceSpawns));
        tag.put("CappedRegions", stringList(cappedRegions));

        NbtCompound pending = new NbtCompound();
        pendingEvents.forEach((biome, entityId) -> pending.putString(biome, entityId.toString()));
        tag.put("PendingEvents", pending);

        long[] names = new long[pendingNicknames.size() * 2];
        int n = 0;
        for (UUID id : pendingNicknames) {
            names[n++] = id.getMostSignificantBits();
            names[n++] = id.getLeastSignificantBits();
        }
        tag.putLongArray("PendingNicknames", names);
        return tag;
    }

    public static PlayerState fromNbt(NbtCompound tag) {
        PlayerState state = new PlayerState(UUID.fromString(tag.getString("Id")));
        state.hasChosenStarter = tag.getBoolean("ChosenStarter");
        state.lastCatchTime = tag.getLong("LastCatchTime");
        state.leftStartingBiome = tag.getBoolean("LeftStartingBiome");
        if (tag.contains("StartingBiome")) {
            state.startingBiome = tag.getString("StartingBiome");
        }

        long[] dead = tag.getLongArray("DeadPokemon");
        for (int i = 0; i + 1 < dead.length; i += 2) {
            state.deadPokemon.add(new UUID(dead[i], dead[i + 1]));
        }

        readStrings(tag, "CaughtSpecies", state.caughtSpecies);
        readStrings(tag, "EventBiomes", state.eventBiomes);

        NbtCompound captures = tag.getCompound("BiomeCaptures");
        for (String key : captures.getKeys()) {
            state.biomeCaptures.put(key, captures.getLong(key));
        }

        readInstances(tag, "InstanceCaptures", state.instanceCaptures);
        readInstances(tag, "InstanceSpawns", state.instanceSpawns);
        readStrings(tag, "CappedRegions", state.cappedRegions);

        NbtCompound pending = tag.getCompound("PendingEvents");
        for (String biome : pending.getKeys()) {
            try {
                state.pendingEvents.put(biome, UUID.fromString(pending.getString(biome)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        long[] names = tag.getLongArray("PendingNicknames");
        for (int i = 0; i + 1 < names.length; i += 2) {
            state.pendingNicknames.add(new UUID(names[i], names[i + 1]));
        }
        return state;
    }

    private static NbtList stringList(Set<String> values) {
        NbtList list = new NbtList();
        for (String value : values) {
            list.add(NbtString.of(value));
        }
        return list;
    }

    private static void readStrings(NbtCompound tag, String key, Set<String> target) {
        NbtList list = tag.getList(key, NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            target.add(list.getString(i));
        }
    }

    private static NbtList instanceList(Set<BiomeInstanceKey> keys) {
        NbtList list = new NbtList();
        for (BiomeInstanceKey key : keys) {
            list.add(key.toNbt());
        }
        return list;
    }

    private static void readInstances(NbtCompound tag, String key, Set<BiomeInstanceKey> target) {
        NbtList list = tag.getList(key, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            target.add(BiomeInstanceKey.fromNbt(list.getCompound(i)));
        }
    }
}
