package net.shiroha233.roadweaver.map.permission;

import net.shiroha233.roadweaver.map.permission.fabric.MapAccessPlatformBridgeImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class MapAccessPlatformBridge {
    private MapAccessPlatformBridge() {
    }

    public static MapAccessPolicy getPolicy(MinecraftServer server) {
        return MapAccessPlatformBridgeImpl.getPolicy(server);
    }

    public static void setPolicy(MinecraftServer server, MapAccessPolicy policy) {
        MapAccessPlatformBridgeImpl.setPolicy(server, policy);
    }

    public static void syncPlayer(ServerPlayer player) {
        MapAccessPlatformBridgeImpl.syncPlayer(player);
    }

    public static void syncAll(MinecraftServer server) {
        MapAccessPlatformBridgeImpl.syncAll(server);
    }
}
