/* 文件职责：验证 CPU 稀疏精采列与完整 NoiseChunk 高度图严格一致。 */
package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoiseChunkHeightSamplerTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createWorldLookup();
    }

    @Test
    void sparseColumnsMatchCompleteChunkAtNegativeCoordinates() {
        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
                .value();
        RandomState randomState = RandomState.create(
                registries.lookupOrThrow(Registries.NOISE),
                0x5EED_1234_ABCDL,
                settings);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(
                settings.noiseSettings().minY(),
                settings.noiseSettings().height());
        NoiseChunkHeightSampler sampler = NoiseChunkHeightSampler.create(heightAccessor, settings, randomState);
        int[] requested = {0, 8, 8 << 4, 8 + (8 << 4), 255, 0};

        AccurateHeightChunk complete = sampler.sampleChunk(-3, 5);
        AccurateHeightChunk sparse = sampler.sampleChunkColumns(-3, 5, requested);

        for (int index : requested) {
            assertEquals(complete.worldSurfaceWgAt(index), sparse.worldSurfaceWgAt(index),
                    "WORLD_SURFACE_WG column " + index);
            assertEquals(complete.oceanFloorWgAt(index), sparse.oceanFloorWgAt(index),
                    "OCEAN_FLOOR_WG column " + index);
            assertEquals(complete.motionBlockingNoLeavesAt(index), sparse.motionBlockingNoLeavesAt(index),
                    "MOTION_BLOCKING_NO_LEAVES column " + index);
        }
    }

    @Test
    void columnsMatchVanillaBaseHeight() {
        Holder<NoiseGeneratorSettings> settingsHolder = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        NoiseGeneratorSettings settings = settingsHolder.value();
        RandomState randomState = RandomState.create(
                registries.lookupOrThrow(Registries.NOISE),
                0x5EED_1234_ABCDL,
                settings);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(
                settings.noiseSettings().minY(),
                settings.noiseSettings().height());
        // Biomes do not affect terrain density, so a fixed biome source is enough for getBaseHeight.
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                new FixedBiomeSource(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)),
                settingsHolder);
        NoiseChunkHeightSampler sampler = NoiseChunkHeightSampler.create(heightAccessor, settings, randomState);

        int[][] chunks = {{-3, 5}, {0, 0}, {40, -17}, {-125, 88}};
        int[] columns = {0, 7, 8 + (3 << 4), 15 + (15 << 4), 9 + (12 << 4)};
        for (int[] chunk : chunks) {
            AccurateHeightChunk heights = sampler.sampleChunk(chunk[0], chunk[1]);
            for (int column : columns) {
                int x = (chunk[0] << 4) + (column & 15);
                int z = (chunk[1] << 4) + (column >> 4);
                assertEquals(
                        generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState),
                        heights.worldSurfaceWgAt(column),
                        "WORLD_SURFACE_WG at " + x + "," + z);
                assertEquals(
                        generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, randomState),
                        heights.oceanFloorWgAt(column),
                        "OCEAN_FLOOR_WG at " + x + "," + z);
            }
        }
    }
}
