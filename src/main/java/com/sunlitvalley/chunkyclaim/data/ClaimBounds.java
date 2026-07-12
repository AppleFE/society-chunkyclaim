package com.sunlitvalley.chunkyclaim.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record ClaimBounds(String dimension, int centerChunkX, int centerChunkZ) {
    public static final int RADIUS_CHUNKS = 1;
    public static final int DIAMETER_CHUNKS = RADIUS_CHUNKS * 2 + 1;

    public ClaimBounds {
        Objects.requireNonNull(dimension, "dimension");
    }

    public static ClaimBounds centeredAt(Level level, BlockPos position) {
        ChunkPos chunk = new ChunkPos(position);
        return new ClaimBounds(level.dimension().location().toString(), chunk.x, chunk.z);
    }

    public int minChunkX() {
        return centerChunkX - RADIUS_CHUNKS;
    }

    public int maxChunkX() {
        return centerChunkX + RADIUS_CHUNKS;
    }

    public int minChunkZ() {
        return centerChunkZ - RADIUS_CHUNKS;
    }

    public int maxChunkZ() {
        return centerChunkZ + RADIUS_CHUNKS;
    }

    public int minBlockX() {
        return minChunkX() << 4;
    }

    public int maxBlockX() {
        return (maxChunkX() << 4) + 15;
    }

    public int minBlockZ() {
        return minChunkZ() << 4;
    }

    public int maxBlockZ() {
        return (maxChunkZ() << 4) + 15;
    }

    public boolean contains(ResourceKey<Level> level, BlockPos position) {
        return dimension.equals(level.location().toString())
                && contains(position.getX(), position.getZ());
    }

    public boolean contains(int blockX, int blockZ) {
        return blockX >= minBlockX() && blockX <= maxBlockX()
                && blockZ >= minBlockZ() && blockZ <= maxBlockZ();
    }

    public double distanceTo(ClaimBounds other) {
        if (!dimension.equals(other.dimension)) {
            return Double.POSITIVE_INFINITY;
        }
        long dx = intervalDistance(minBlockX(), maxBlockX(), other.minBlockX(), other.maxBlockX());
        long dz = intervalDistance(minBlockZ(), maxBlockZ(), other.minBlockZ(), other.maxBlockZ());
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    public double distanceToPoint(String pointDimension, int blockX, int blockZ) {
        if (!dimension.equals(pointDimension)) {
            return Double.POSITIVE_INFINITY;
        }
        long dx = pointIntervalDistance(blockX, minBlockX(), maxBlockX());
        long dz = pointIntervalDistance(blockZ, minBlockZ(), maxBlockZ());
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    public int centerBlockX() {
        return (centerChunkX << 4) + 8;
    }

    public int centerBlockZ() {
        return (centerChunkZ << 4) + 8;
    }

    private static long intervalDistance(int minA, int maxA, int minB, int maxB) {
        if (maxA < minB) {
            return (long) minB - maxA;
        }
        if (maxB < minA) {
            return (long) minA - maxB;
        }
        return 0;
    }

    private static long pointIntervalDistance(int point, int min, int max) {
        if (point < min) {
            return (long) min - point;
        }
        if (point > max) {
            return (long) point - max;
        }
        return 0;
    }
}

