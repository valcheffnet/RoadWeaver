package net.shiroha233.roadweaver.network.neoforge;

import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;

/**
 * Fabric 平台网络桥接实现
 */
public final class ClientNetBridgeImpl {
    private ClientNetBridgeImpl() {}

    public static void requestSnapshot(int requestSeq,
                                       Identifier dimensionId,
                                       MapLoadPhase phase,
                                       int responseIndex,
                                       int minX,
                                       int minZ,
                                       int maxX,
                                       int maxZ) {
        MapNetworkNeoForgeClient.requestSnapshot(requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ);
    }

    public static void requestTeleport(int x, int y, int z) {
        MapNetworkNeoForgeClient.requestTeleport(x, y, z);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        MapNetworkNeoForgeClient.requestManualConnect(ax, az, bx, bz);
    }

    public static void requestSearch(int requestSeq, Identifier dimensionId, String query) {
        MapNetworkNeoForgeClient.requestSearch(requestSeq, dimensionId, query);
    }

    public static void requestRawPathDebug(int requestSeq, Identifier dimensionId) {
        MapNetworkNeoForgeClient.requestRawPathDebug(requestSeq, dimensionId);
    }
}
