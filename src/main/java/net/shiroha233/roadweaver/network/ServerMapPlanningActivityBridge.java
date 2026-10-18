/* 文件职责：定义服务端向地图客户端同步自动规划采样范围的端口。 */
package net.shiroha233.roadweaver.network;

import net.shiroha233.roadweaver.network.neoforge.ServerMapPlanningActivityBridgeImpl;
import net.minecraft.server.level.ServerLevel;

/**
 * 自动规划采样活动的服务端地图同步端口。
 */
public final class ServerMapPlanningActivityBridge {
    private ServerMapPlanningActivityBridge() {}

    public static void broadcast(ServerLevel level) {
        ServerMapPlanningActivityBridgeImpl.broadcast(level);
    }
}
