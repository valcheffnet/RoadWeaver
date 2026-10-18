package net.shiroha233.roadweaver.client.map.tile;

import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;

/**
 * 单人模式 overlay 瓦片只读调度。
 */
public final class SingleplayerOverlayTileManager extends AbstractSingleplayerTileManager {
    @Override
    protected MapTileLayer layer() {
        return MapTileLayer.OVERLAY;
    }
}