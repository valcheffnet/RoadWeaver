/* 文件职责：按配置与世界生成器选择精确高度采样后端。 */
package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.server.level.ServerLevel;

/**
 * 精确高度后端工厂。Server-side only fork: the OpenCL backend is removed, the CPU backend is always used.
 */
public final class AccurateHeightBackends {
    private AccurateHeightBackends() {}

    public static AccurateHeightBackend create(ServerLevel level, CpuAccurateHeightBackend cpu) {
        return cpu;
    }
}
