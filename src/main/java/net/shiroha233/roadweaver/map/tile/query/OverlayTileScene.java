package net.shiroha233.roadweaver.map.tile.query;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * overlay 瓦片静态场景。
 */
public record OverlayTileScene(List<BlockPos> structures, List<List<BlockPos>> roadPolylines) {
    public static OverlayTileScene empty() {
        return new OverlayTileScene(List.of(), List.of());
    }
}