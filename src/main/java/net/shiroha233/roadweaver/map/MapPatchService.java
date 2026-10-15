/* 文件职责：地图局部更新发布服务（服务端专用分支中为空实现）。 */
package net.shiroha233.roadweaver.map;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;

import java.util.List;

/**
 * Map patch publishing. Upstream builds patches for the road map client and sends them over the network;
 * the server-side only fork has no map client, so every method is a no-op. The call sites stay as they are
 * to keep the diff against upstream small.
 */
public final class MapPatchService {
    private MapPatchService() {}

    public static void publishConnection(ServerLevel level, StructureConnection connection) {}

    public static void publishStructures(ServerLevel level, List<StructureInfo> structures) {}

    public static void publishConnectionStatus(ServerLevel level, StructureConnection connection, ConnectionStatus status) {}

    public static void publishRoadForConnectionAsync(ServerLevel level, StructureConnection connection) {}
}
