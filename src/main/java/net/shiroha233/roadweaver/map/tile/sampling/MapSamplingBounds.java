/* 文件职责：表示一次主动地图采样的不可变世界坐标范围。 */
package net.shiroha233.roadweaver.map.tile.sampling;

/**
 * 主动地图采样的闭区间边界。
 */
public record MapSamplingBounds(int minX, int minZ, int maxX, int maxZ) {
    public MapSamplingBounds {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("invalid map sampling bounds");
        }
    }
}
