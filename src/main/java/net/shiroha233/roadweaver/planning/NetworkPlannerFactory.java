package net.shiroha233.roadweaver.planning;

import net.shiroha233.roadweaver.config.sub.PlanningConfig.PlanningAlgorithm;
import net.shiroha233.roadweaver.planning.impl.DelaunayPlanner;
import net.shiroha233.roadweaver.planning.impl.KNNPlanner;
import net.shiroha233.roadweaver.planning.impl.MSTPlanner;
import net.shiroha233.roadweaver.planning.impl.RNGPlanner;
import net.shiroha233.roadweaver.planning.impl.SnapBranchPlanner;

/**
 * 路网规划器工厂，根据算法枚举返回对应实现
 */
public final class NetworkPlannerFactory {
    private NetworkPlannerFactory() {}

    public static NetworkPlanner create(PlanningAlgorithm algorithm) {
        return switch (algorithm) {
            case KNN -> new KNNPlanner();
            case DELAUNAY -> new DelaunayPlanner();
            case RNG -> new RNGPlanner();
            case MST -> new MSTPlanner();
            case SNAP_BRANCH -> new SnapBranchPlanner();
        };
    }
}
