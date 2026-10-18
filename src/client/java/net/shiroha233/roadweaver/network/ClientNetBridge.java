package net.shiroha233.roadweaver.network;

import net.shiroha233.roadweaver.network.fabric.ClientNetBridgeImpl;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;

/**
 * 客户端网络桥接
 */
public final class ClientNetBridge {
    private ClientNetBridge() {}

    public static void requestSnapshot(int requestSeq,
                                       Identifier dimensionId,
                                       MapLoadPhase phase,
                                       int responseIndex,
                                       int minX,
                                       int minZ,
                                       int maxX,
                                       int maxZ) {
        ClientNetBridgeImpl.requestSnapshot(requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ);
    }

    public static void requestTeleport(int x, int y, int z) {
        ClientNetBridgeImpl.requestTeleport(x, y, z);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        ClientNetBridgeImpl.requestManualConnect(ax, az, bx, bz);
    }

    public static void requestSearch(int requestSeq, Identifier dimensionId, String query) {
        ClientNetBridgeImpl.requestSearch(requestSeq, dimensionId, query);
    }

    public static void requestRawPathDebug(int requestSeq, Identifier dimensionId) {
        ClientNetBridgeImpl.requestRawPathDebug(requestSeq, dimensionId);
    }
}
