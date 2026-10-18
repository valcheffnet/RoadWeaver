package net.shiroha233.roadweaver.network.fabric;

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
        MapNetworkFabricClient.requestSnapshot(requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ);
    }

    public static void requestTeleport(int x, int y, int z) {
        MapNetworkFabricClient.requestTeleport(x, y, z);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        MapNetworkFabricClient.requestManualConnect(ax, az, bx, bz);
    }

    public static void requestSearch(int requestSeq, Identifier dimensionId, String query) {
        MapNetworkFabricClient.requestSearch(requestSeq, dimensionId, query);
    }

    public static void requestRawPathDebug(int requestSeq, Identifier dimensionId) {
        MapNetworkFabricClient.requestRawPathDebug(requestSeq, dimensionId);
    }
}
