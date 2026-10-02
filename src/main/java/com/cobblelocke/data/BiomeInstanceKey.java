package com.cobblelocke.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.RegistryKey;

import java.util.Objects;

public record BiomeInstanceKey(String biomeKey, int regionX, int regionZ) {
    public static final int DEFAULT_REGION_SIZE = 512;

    public static BiomeInstanceKey fromPosition(RegistryKey<Biome> biome, BlockPos pos) {
        return fromPosition(biome, pos, DEFAULT_REGION_SIZE);
    }

    public static BiomeInstanceKey fromPosition(RegistryKey<Biome> biome, BlockPos pos, int size) {
        int edge = size > 0 ? size : DEFAULT_REGION_SIZE;
        return new BiomeInstanceKey(
                biome.getValue().toString(),
                Math.floorDiv(pos.getX(), edge),
                Math.floorDiv(pos.getZ(), edge));
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putString("Biome", biomeKey);
        tag.putInt("RegionX", regionX);
        tag.putInt("RegionZ", regionZ);
        return tag;
    }

    public static BiomeInstanceKey fromNbt(NbtCompound tag) {
        return new BiomeInstanceKey(
                tag.getString("Biome"), tag.getInt("RegionX"), tag.getInt("RegionZ"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof BiomeInstanceKey key
                && regionX == key.regionX
                && regionZ == key.regionZ
                && Objects.equals(biomeKey, key.biomeKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(biomeKey, regionX, regionZ);
    }

    @Override
    public String toString() {
        return biomeKey + " [" + regionX + ", " + regionZ + "]";
    }
}
