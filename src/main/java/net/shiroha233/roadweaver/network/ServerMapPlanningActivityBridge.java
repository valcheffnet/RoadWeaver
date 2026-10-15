/* 文件职责：定义服务端向地图客户端同步自动规划采样范围的端口（服务端专用分支中为空实现）。 */
package net.shiroha233.roadweaver.network;

import net.minecraft.server.level.ServerLevel;

/**
 * Planning activity sync to the map client. No map client in the server-side only fork, so this is a no-op.
 */
public final class ServerMapPlanningActivityBridge {
    private ServerMapPlanningActivityBridge() {}

    public static void broadcast(ServerLevel level) {}
}
