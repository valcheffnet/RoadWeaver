package net.shiroha233.roadweaver.network;

import net.shiroha233.roadweaver.network.neoforge.ServerMapPatchBridgeImpl;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;

/**
 * 服务端地图 patch 推送桥接。
 */
public final class ServerMapPatchBridge {
    private ServerMapPatchBridge() {}

    public static void broadcast(ServerLevel level, Identifier dimensionId, MapSnapshotPatch patch) {
        ServerMapPatchBridgeImpl.broadcast(level, dimensionId, patch);
    }
}