/* 文件职责：保存规则区域精确采样返回的三张原版高度图数组。 */
package net.shiroha233.roadweaver.pathfinding.cache;

import java.util.Objects;

/**
 * 与请求网格索引一一对应的精确高度结果。
 */
public record AccurateHeightGrid(AccurateHeightGridRequest request,
                                 int[] worldSurface,
                                 int[] oceanFloor,
                                 int[] motionBlocking) {
    public AccurateHeightGrid {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(worldSurface, "worldSurface");
        Objects.requireNonNull(oceanFloor, "oceanFloor");
        Objects.requireNonNull(motionBlocking, "motionBlocking");
        int expected = request.sampleCount();
        if (worldSurface.length != expected || oceanFloor.length != expected || motionBlocking.length != expected) {
            throw new IllegalArgumentException("accurate height grid arrays do not match request");
        }
    }
}
