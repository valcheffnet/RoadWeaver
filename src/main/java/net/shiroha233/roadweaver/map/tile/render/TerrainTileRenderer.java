package net.shiroha233.roadweaver.map.tile.render;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

import java.awt.image.BufferedImage;

/**
 * 基础地貌瓦片渲染器。
 */
public final class TerrainTileRenderer {
    private TerrainTileRenderer() {}

    public static BufferedImage render(ServerLevel level, TerrainSamplingCache cache, MapTileCoord coord) {
        int size = MapTileScheme.TILE_SIZE_PX;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int bpp = MapTileScheme.blocksPerPixel(coord.zoom());
        int nearWaterDistance = Math.max(16, bpp * 2);
        int seaLevel = level.getSeaLevel();

        int[][] heightGrid = sampleHeightGrid(level, cache, coord, size);

        for (int pixelZ = 0; pixelZ < size; pixelZ++) {
            int worldZ = MapTileScheme.sampleBlockZ(coord, pixelZ);
            for (int pixelX = 0; pixelX < size; pixelX++) {
                int worldX = MapTileScheme.sampleBlockX(coord, pixelX);
                int height = heightGrid[pixelZ][pixelX];
                int oceanFloor = cache.oceanFloor(level, worldX, worldZ);
                Holder<Biome> biome = cache.getBiome(level, worldX, worldZ);
                boolean columnWater = cache.isColumnWater(level, worldX, worldZ);
                boolean nearWater = columnWater || cache.isNearWaterLike(level, worldX, worldZ, nearWaterDistance);

                double heightShade = computePixelShade(heightGrid, pixelX, pixelZ, size, bpp);
                int argb = TerrainTilePalette.colorFor(biome, height, seaLevel, oceanFloor, columnWater, nearWater, heightShade);
                image.setRGB(pixelX, pixelZ, argb);
            }
        }

        return image;
    }

    private static int[][] sampleHeightGrid(ServerLevel level, TerrainSamplingCache cache, MapTileCoord coord, int size) {
        int[][] grid = new int[size][size];
        for (int pz = 0; pz < size; pz++) {
            int worldZ = MapTileScheme.sampleBlockZ(coord, pz);
            for (int px = 0; px < size; px++) {
                int worldX = MapTileScheme.sampleBlockX(coord, px);
                grid[pz][px] = cache.height(level, worldX, worldZ);
            }
        }
        return grid;
    }

    private static double computePixelShade(int[][] grid, int px, int pz, int size, int bpp) {
        if (px > 0 && px < size - 1 && pz > 0 && pz < size - 1) {
            int[][] local = {
                    {grid[pz - 1][px - 1], grid[pz - 1][px], grid[pz - 1][px + 1]},
                    {grid[pz][px - 1], grid[pz][px], grid[pz][px + 1]},
                    {grid[pz + 1][px - 1], grid[pz + 1][px], grid[pz + 1][px + 1]}
            };
            return HeightShader.computeShade(local);
        }
        int neighborH = px > 0 ? grid[pz][px - 1] : grid[pz][Math.min(px + 1, size - 1)];
        return HeightShader.simpleShade(grid[pz][px], neighborH, bpp);
    }
}
