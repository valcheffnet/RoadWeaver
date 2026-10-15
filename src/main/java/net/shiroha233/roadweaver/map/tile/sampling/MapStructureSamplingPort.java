/* 文件职责：定义主动地图采样完成后查询区域结构点的端口。 */
package net.shiroha233.roadweaver.map.tile.sampling;

import net.minecraft.server.level.ServerLevel;

/**
 * 主动地图采样的结构索引后处理端口。
 */
@FunctionalInterface
public interface MapStructureSamplingPort {
    void query(ServerLevel level, MapSamplingBounds bounds);
}
