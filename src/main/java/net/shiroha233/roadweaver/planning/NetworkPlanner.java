package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.StructureConnection;

import java.util.List;

/**
 * 路网拓扑规划器接口
 */
public interface NetworkPlanner {
    List<StructureConnection> plan(List<BlockPos> points, int maxEdgeLenBlocks);
}
