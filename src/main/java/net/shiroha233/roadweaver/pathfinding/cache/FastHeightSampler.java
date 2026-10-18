/* 文件职责：以接近旧实现的复杂度提供适配 1.21.1 语义的快速高度采样。 */
package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 快速高度采样器。
 * <p>
 * 仍然保持旧版“直接读噪声列”的低成本路径，但把固体阈值切换到
 * 1.21.1 原版 `Aquifer#computeSubstance` 使用的 `density > 0.0` 语义，
 * 并用 `preliminarySurfaceLevel` 作为起扫提示，避免总是从世界顶端扫描。
 */
public final class FastHeightSampler {

    private static final double SOLID_DENSITY_THRESHOLD = 0.0D;
    private static final int SAMPLE_GRID = 4;
    private static final int SURFACE_SCAN_MARGIN = 32;

    private final DensityFunction finalDensity;
    private final DensityFunction preliminarySurfaceLevel;
    private final int minY;
    private final int maxYExclusive;
    private final int cellHeight;
    private final ConcurrentHashMap<Long, Integer> surfaceHeightCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> oceanFloorCache = new ConcurrentHashMap<>();

    private FastHeightSampler(DensityFunction finalDensity,
                              DensityFunction preliminarySurfaceLevel,
                              NoiseSettings settings) {
        this.finalDensity = finalDensity;
        this.preliminarySurfaceLevel = preliminarySurfaceLevel;
        this.minY = settings.minY();
        this.maxYExclusive = minY + settings.height();
        this.cellHeight = settings.getCellHeight();
    }

    public static FastHeightSampler create(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        RandomState randomState = chunkSource.getGeneratorState().randomState();
        NoiseRouter router = randomState.router();
        NoiseSettings settings = getNoiseSettings(level);
        return new FastHeightSampler(router.finalDensity(), router.preliminarySurfaceLevel(), settings);
    }

    public int sampleHeight(int x, int z) {
        int alignedX = alignToSampleGrid(x);
        int alignedZ = alignToSampleGrid(z);
        long key = packXZ(alignedX, alignedZ);
        Integer cached = surfaceHeightCache.get(key);
        if (cached != null) {
            return cached;
        }

        int height = sampleSolidTop(alignedX, alignedZ);
        surfaceHeightCache.put(key, height);
        return height;
    }

    public int sampleOceanFloor(int x, int z) {
        int alignedX = alignToSampleGrid(x);
        int alignedZ = alignToSampleGrid(z);
        long key = packXZ(alignedX, alignedZ);
        Integer cached = oceanFloorCache.get(key);
        if (cached != null) {
            return cached;
        }

        int height = sampleSolidTop(alignedX, alignedZ);
        oceanFloorCache.put(key, height);
        return height;
    }

    public void prewarmRegion(int minX, int minZ, int maxX, int maxZ, int step) {
        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                int alignedX = alignToSampleGrid(x);
                int alignedZ = alignToSampleGrid(z);
                sampleHeight(alignedX, alignedZ);
                sampleOceanFloor(alignedX, alignedZ);
            }
        }
    }

    public void clearCache() {
        surfaceHeightCache.clear();
        oceanFloorCache.clear();
    }

    public int getCacheSize() {
        return surfaceHeightCache.size();
    }

    private int sampleSolidTop(int x, int z) {
        MutablePointContext context = new MutablePointContext(x, minY, z);
        int hintedStart = hintedScanStart(x, z);
        int sampled = scanDownward(context, hintedStart, minY);
        if (sampled != minY) {
            return sampled;
        }

        int topStart = alignDown(maxYExclusive - 1);
        if (hintedStart >= topStart) {
            return minY;
        }
        return scanDownward(context, topStart, hintedStart + cellHeight);
    }

    private int hintedScanStart(int x, int z) {
        int hinted = Mth.floor(preliminarySurfaceLevel.compute(new DensityFunction.SinglePointContext(x, 0, z)));
        int start = Math.min(maxYExclusive - 1, hinted + SURFACE_SCAN_MARGIN);
        return alignDown(Math.max(minY, start));
    }

    private int scanDownward(MutablePointContext context, int startY, int minInclusive) {
        for (int y = startY; y >= minInclusive; y -= cellHeight) {
            context.setY(y);
            if (finalDensity.compute(context) > SOLID_DENSITY_THRESHOLD) {
                return y;
            }
        }
        return minY;
    }

    private int alignDown(int y) {
        return minY + Mth.floorDiv(y - minY, cellHeight) * cellHeight;
    }

    private static int alignToSampleGrid(int value) {
        return Math.floorDiv(value, SAMPLE_GRID) * SAMPLE_GRID;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static NoiseSettings getNoiseSettings(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        if (generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseGen) {
            return noiseGen.generatorSettings().value().noiseSettings();
        }
        return NoiseSettings.create(-64, 384, 1, 2);
    }

    private static final class MutablePointContext implements DensityFunction.FunctionContext {
        private final int x;
        private int y;
        private final int z;

        private MutablePointContext(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private void setY(int y) {
            this.y = y;
        }

        @Override
        public int blockX() {
            return x;
        }

        @Override
        public int blockY() {
            return y;
        }

        @Override
        public int blockZ() {
            return z;
        }
    }
}
