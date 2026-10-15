package net.shiroha233.roadweaver.map.tile.core;

/**
 * 地图瓦片坐标与缩放规则。
 */
public final class MapTileScheme {
    private MapTileScheme() {}

    public static final int TILE_SIZE_PX = 256;
    public static final int BASE_BLOCKS_PER_PIXEL = 4;
    public static final int MIN_ZOOM = 0;
    public static final int MAX_ZOOM = 6;

    public static int clampZoom(int zoom) {
        if (zoom < MIN_ZOOM) return MIN_ZOOM;
        if (zoom > MAX_ZOOM) return MAX_ZOOM;
        return zoom;
    }

    public static int blocksPerPixel(int zoom) {
        validateZoom(zoom);
        return BASE_BLOCKS_PER_PIXEL << zoom;
    }

    public static int blocksPerTile(int zoom) {
        return TILE_SIZE_PX * blocksPerPixel(zoom);
    }

    public static MapTileCoord tileAtBlock(int zoom, int blockX, int blockZ) {
        int tileSize = blocksPerTile(zoom);
        return new MapTileCoord(zoom, Math.floorDiv(blockX, tileSize), Math.floorDiv(blockZ, tileSize));
    }

    public static MapTileRect tileRectForBlockRect(int zoom, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int x0 = Math.min(minBlockX, maxBlockX);
        int x1 = Math.max(minBlockX, maxBlockX);
        int z0 = Math.min(minBlockZ, maxBlockZ);
        int z1 = Math.max(minBlockZ, maxBlockZ);
        MapTileCoord min = tileAtBlock(zoom, x0, z0);
        MapTileCoord max = tileAtBlock(zoom, x1, z1);
        return new MapTileRect(zoom, min.tileX(), min.tileZ(), max.tileX(), max.tileZ());
    }

    public static int tileMinBlockX(MapTileCoord coord) {
        return coord.tileX() * blocksPerTile(coord.zoom());
    }

    public static int tileMaxBlockX(MapTileCoord coord) {
        return tileMinBlockX(coord) + blocksPerTile(coord.zoom()) - 1;
    }

    public static int tileMinBlockZ(MapTileCoord coord) {
        return coord.tileZ() * blocksPerTile(coord.zoom());
    }

    public static int tileMaxBlockZ(MapTileCoord coord) {
        return tileMinBlockZ(coord) + blocksPerTile(coord.zoom()) - 1;
    }

    public static int sampleBlockX(MapTileCoord coord, int pixelX) {
        validatePixel(pixelX);
        int bpp = blocksPerPixel(coord.zoom());
        return tileMinBlockX(coord) + pixelX * bpp + bpp / 2;
    }

    public static int sampleBlockZ(MapTileCoord coord, int pixelZ) {
        validatePixel(pixelZ);
        int bpp = blocksPerPixel(coord.zoom());
        return tileMinBlockZ(coord) + pixelZ * bpp + bpp / 2;
    }

    public static double worldToPixel(double worldBlock, int zoom) {
        return worldBlock / blocksPerPixel(zoom);
    }

    public static double tileOriginPixelX(MapTileCoord coord) {
        return (double) coord.tileX() * TILE_SIZE_PX;
    }

    public static double tileOriginPixelZ(MapTileCoord coord) {
        return (double) coord.tileZ() * TILE_SIZE_PX;
    }

    /**
     * 根据当前视口的 blocksPerPixel 选择最合适的 zoom 级别。
     * blocksPerPixel = 世界范围 / 屏幕像素数，即每像素对应的方块数。
     * 选择使得 schemeBpp <= currentBpp * tolerance 的最大 zoom，
     * 即不超过当前分辨率的最粗级别（避免加载过多精细瓦片）。
     */
    public static int zoomForBlocksPerPixel(double blocksPerPixel) {
        double tolerance = 1.5;
        for (int zoom = MAX_ZOOM; zoom >= MIN_ZOOM; zoom--) {
            int schemeBpp = BASE_BLOCKS_PER_PIXEL << zoom;
            if (schemeBpp <= blocksPerPixel * tolerance) {
                return zoom;
            }
        }
        return MIN_ZOOM;
    }

    private static void validateZoom(int zoom) {
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw new IllegalArgumentException("zoom out of range: " + zoom);
        }
    }

    private static void validatePixel(int pixel) {
        if (pixel < 0 || pixel >= TILE_SIZE_PX) {
            throw new IllegalArgumentException("pixel out of range: " + pixel);
        }
    }
}