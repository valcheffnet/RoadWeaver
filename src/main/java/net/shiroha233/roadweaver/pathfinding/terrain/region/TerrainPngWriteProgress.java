/* 文件职责：定义地形 PNG 瓦片写入过程的进度输出边界。 */
package net.shiroha233.roadweaver.pathfinding.terrain.region;

/**
 * 接收地形 PNG 瓦片写入进度。
 */
@FunctionalInterface
public interface TerrainPngWriteProgress {
    TerrainPngWriteProgress NONE = (completedTiles, totalTiles) -> {};

    void onTileCompleted(long completedTiles, long totalTiles);
}
