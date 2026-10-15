package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 快速高度采样器
 * Upstream scanned initialDensityWithoutJaggedness top-down for density > 0.390625. In 26.x the noise router
 * exposes that estimate directly as chunkSurfaceLevel (vanilla preliminary_surface_level), so it is sampled once
 * per 4x4 column. Non-noise generators fall back to ChunkGenerator.getBaseHeight.
 */
public final class FastHeightSampler {

    private final ServerLevel level;
    private final RandomState randomState;
    private final DensityFunction surfaceLevel;
    private final int minY;
    private final int maxY;
    private ConcurrentHashMap<Long, Integer> heightCache = new ConcurrentHashMap<>();

    private FastHeightSampler(ServerLevel level, RandomState randomState, DensityFunction surfaceLevel) {
        this.level = level;
        this.randomState = randomState;
        this.surfaceLevel = surfaceLevel;
        this.minY = level.getMinY();
        this.maxY = level.getMaxY();
    }

    public static FastHeightSampler create(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        RandomState randomState = chunkSource.getGeneratorState().randomState();
        DensityFunction surfaceLevel = null;
        if (chunkSource.getGenerator() instanceof NoiseBasedChunkGenerator noiseGen) {
            surfaceLevel = noiseGen.generatorSettings().value().noiseRouter().chunkSurfaceLevel();
        }
        return new FastHeightSampler(level, randomState, surfaceLevel);
    }

    public int sampleHeight(int x, int z) {
        int alignedX = (x >> 2) << 2;
        int alignedZ = (z >> 2) << 2;
        long key = packXZ(alignedX, alignedZ);
        Integer cached = heightCache.get(key);
        if (cached != null) return cached;

        int height = computeHeight(alignedX, alignedZ);
        heightCache.put(key, height);
        return height;
    }

    public void prewarmRegion(int minX, int minZ, int maxX, int maxZ, int step) {
        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                sampleHeight((x >> 2) << 2, (z >> 2) << 2);
            }
        }
    }

    private int computeHeight(int x, int z) {
        if (surfaceLevel != null) {
            float y = randomState.sampleBlockValueUncached(surfaceLevel, x, 0, z);
            return Mth.clamp(Mth.floor(y), minY, maxY);
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * 释放缓存。用新实例替换而非 clear()，确保内部 table 数组被 GC 回收。
     */
    public void clearCache() {
        heightCache = new ConcurrentHashMap<>();
    }

    public int getCacheSize() {
        return heightCache.size();
    }
}
