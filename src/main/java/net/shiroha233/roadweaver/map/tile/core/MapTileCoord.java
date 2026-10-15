package net.shiroha233.roadweaver.map.tile.core;

/**
 * 单张瓦片的离散坐标。
 */
public record MapTileCoord(int zoom, int tileX, int tileZ) {
    public MapTileCoord {
        if (zoom < 0) {
            throw new IllegalArgumentException("zoom must be >= 0");
        }
    }

    public String zoomFolder() {
        return "z" + zoom;
    }

    public String tileFileName() {
        return tileZ + ".png";
    }
}