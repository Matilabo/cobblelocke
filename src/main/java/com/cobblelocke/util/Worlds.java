package com.cobblelocke.util;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

public final class Worlds {
    private Worlds() {
    }

    public static RegistryKey<Biome> biomeAt(ServerWorld world, BlockPos pos) {
        try {
            Registry<Biome> registry = world.getRegistryManager().getOptional(RegistryKeys.BIOME).orElse(null);
            if (registry == null) {
                return null;
            }
            return registry.getKey(world.getBiome(pos).value()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static RegistryKey<Biome> biomeAt(ServerPlayerEntity player) {
        return biomeAt(player.getServerWorld(), player.getBlockPos());
    }

    public static String biomeId(RegistryKey<Biome> biome) {
        return biome == null ? null : biome.getValue().toString();
    }

    public static String prettyBiomeName(RegistryKey<Biome> biome) {
        if (biome == null) {
            return "Unknown";
        }
        String path = biome.getValue().getPath();
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? path : out.toString();
    }

    public static String prettyBiomeName(String biomeId) {
        if (biomeId == null) {
            return "Unknown";
        }
        String path = biomeId.contains(":") ? biomeId.substring(biomeId.indexOf(':') + 1) : biomeId;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? path : out.toString();
    }

    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
