package net.shiroha233.roadweaver.map.tile.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 某个缩放级别上的瓦片矩形范围。
 */
public record MapTileRect(int zoom, int minTileX, int minTileZ, int maxTileX, int maxTileZ) {
    public MapTileRect {
        if (zoom < 0) {
            throw new IllegalArgumentException("zoom must be >= 0");
        }
        if (maxTileX < minTileX || maxTileZ < minTileZ) {
            throw new IllegalArgumentException("invalid tile rect");
        }
    }

    public int width() {
        return maxTileX - minTileX + 1;
    }

    public int height() {
        return maxTileZ - minTileZ + 1;
    }

    public boolean contains(MapTileCoord coord) {
        return coord != null
                && coord.zoom() == zoom
                && coord.tileX() >= minTileX
                && coord.tileX() <= maxTileX
                && coord.tileZ() >= minTileZ
                && coord.tileZ() <= maxTileZ;
    }

    public List<MapTileCoord> coords() {
        ArrayList<MapTileCoord> out = new ArrayList<>(width() * height());
        for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                out.add(new MapTileCoord(zoom, tileX, tileZ));
            }
        }
        return out;
    }
}