/* 文件职责：按原版 NoiseChunk 语义在 CPU 烘焙单个区块的三类高度图。 */
package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * 基于原版 NoiseChunk 批量烘焙整块高度图的精确采样器
 * 26.x: density is sampled in volumes (NoiseChunk.cachingSamplers + Aquifer.computeSubstance), the same way
 * NoiseBasedChunkGenerator.doFill/iterateNoiseColumn do it. The chunk is sampled top-down in slabs so the
 * work stops once every requested column has found its surface.
 */
final class NoiseChunkHeightSampler {
    /** Slab height for top-down sampling; a multiple of the vanilla cell height (8) so interpolation matches. */
    private static final int SLAB_HEIGHT = 32;

    private final RandomState randomState;
    private final NoiseGeneratorSettings generatorSettings;
    private final NoiseSettings noiseSettings;
    private final Aquifer.FluidPicker fluidPicker;
    private final BlockState defaultBlock;
    private final DensityFunction finalDensity;
    private NoiseChunkHeightSampler(RandomState randomState,
                                    NoiseGeneratorSettings generatorSettings,
                                    NoiseSettings noiseSettings,
                                    Aquifer.FluidPicker fluidPicker) {
        this.randomState = randomState;
        this.generatorSettings = generatorSettings;
        this.noiseSettings = noiseSettings;
        this.fluidPicker = fluidPicker;
        this.defaultBlock = generatorSettings.defaultBlock();
        this.finalDensity = generatorSettings.noiseRouter().finalDensity();
    }

    static NoiseChunkHeightSampler create(ServerLevel level, NoiseBasedChunkGenerator generator, RandomState randomState) {
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        return create(level, settings, randomState);
    }

    static NoiseChunkHeightSampler create(LevelHeightAccessor level,
                                          NoiseGeneratorSettings settings,
                                          RandomState randomState) {
        NoiseSettings noiseSettings = settings.noiseSettings().clampToHeightAccessor(level);
        return new NoiseChunkHeightSampler(randomState, settings, noiseSettings, createFluidPicker(settings));
    }

    /**
     * 释放缓存。用新实例替换而非 clear()，确保内部 table 数组被 GC 回收。
     */
    void clear() {
        // 当前实现不持有跨区块状态，方法保留给统一后端生命周期。
    }

    AccurateHeightChunk sampleChunk(int chunkX, int chunkZ) {
        return sampleChunkColumns(chunkX, chunkZ, null);
    }

    AccurateHeightChunk sampleChunkColumns(int chunkX, int chunkZ, int[] requestedColumns) {
        int minY = noiseSettings.minY();
        int[] worldSurface = new int[256];
        int[] oceanFloor = new int[256];
        int[] motionBlocking = new int[256];
        Arrays.fill(worldSurface, minY);
        Arrays.fill(oceanFloor, minY);
        Arrays.fill(motionBlocking, minY);

        byte[] unresolvedMasks = new byte[256];
        int unresolvedColumns = initializeRequestedColumns(requestedColumns, unresolvedMasks);
        if (unresolvedColumns == 0) {
            return new AccurateHeightChunk(chunkX, chunkZ, worldSurface, oceanFloor, motionBlocking);
        }

        Predicate<BlockState> worldSurfacePredicate = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
        Predicate<BlockState> oceanFloorPredicate = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
        Predicate<BlockState> motionPredicate = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.isOpaque();

        int minBlockX = chunkX << 4;
        int minBlockZ = chunkZ << 4;

        int topY = minY + noiseSettings.height();
        for (int slabTop = topY; slabTop > minY && unresolvedColumns > 0; slabTop -= SLAB_HEIGHT) {
            int slabBottom = Math.max(minY, slabTop - SLAB_HEIGHT);
            DensityVolume volume = new DensityVolume(16, slabTop - slabBottom, 16, minBlockX, slabBottom, minBlockZ);
            try (NoiseChunk noiseChunk = new NoiseChunk(
                    randomState, Beardifier.EMPTY, generatorSettings, fluidPicker, Blender.empty(), volume);
                 ScopedDensityBuffer densities = noiseChunk.cachingSamplers().get(finalDensity).sampleVolume(volume)) {
                Aquifer aquifer = noiseChunk.aquifer();
                for (int iy = volume.sizeY() - 1; iy >= 0 && unresolvedColumns > 0; iy--) {
                    int blockY = volume.blockY(iy);
                    for (int iz = 0; iz < 16 && unresolvedColumns > 0; iz++) {
                        for (int ix = 0; ix < 16; ix++) {
                            int index = ix | (iz << 4);
                            byte mask = unresolvedMasks[index];
                            if (mask == 0) {
                                continue;
                            }
                            double density = densities.get(volume.indexUnchecked(ix, iy, iz));
                            BlockState state = aquifer.computeSubstance(volume.blockX(ix), blockY, volume.blockZ(iz), density);
                            if (state == null) {
                                state = defaultBlock;
                            }
                            if (state.isAir()) {
                                continue;
                            }

                            byte nextMask = mask;
                            if ((nextMask & 0x01) != 0 && worldSurfacePredicate.test(state)) {
                                worldSurface[index] = blockY + 1;
                                nextMask &= ~0x01;
                            }
                            if ((nextMask & 0x02) != 0 && oceanFloorPredicate.test(state)) {
                                oceanFloor[index] = blockY + 1;
                                nextMask &= ~0x02;
                            }
                            if ((nextMask & 0x04) != 0 && motionPredicate.test(state)) {
                                motionBlocking[index] = blockY + 1;
                                nextMask &= ~0x04;
                            }

                            if (nextMask != mask) {
                                unresolvedMasks[index] = nextMask;
                                if (nextMask == 0) {
                                    unresolvedColumns--;
                                }
                            }
                        }
                    }
                }
            }
        }

        return new AccurateHeightChunk(chunkX, chunkZ, worldSurface, oceanFloor, motionBlocking);
    }

    private static int initializeRequestedColumns(int[] requestedColumns, byte[] unresolvedMasks) {
        if (requestedColumns == null) {
            Arrays.fill(unresolvedMasks, (byte) 0x07);
            return unresolvedMasks.length;
        }

        int count = 0;
        for (int column : requestedColumns) {
            if (column < 0 || column >= AccurateHeightChunk.COLUMN_COUNT) {
                throw new IndexOutOfBoundsException("height column index: " + column);
            }
            if (unresolvedMasks[column] != 0) {
                continue;
            }
            unresolvedMasks[column] = 0x07;
            count++;
        }
        return count;
    }

    static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus defaultFluid = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        return (x, y, z) -> y < Math.min(-54, seaLevel) ? lava : defaultFluid;
    }

}
