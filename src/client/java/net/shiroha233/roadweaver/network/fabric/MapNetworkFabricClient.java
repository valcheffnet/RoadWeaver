/* Client half of the map networking: receivers for server packets and the requests the map screen sends. */
package net.shiroha233.roadweaver.network.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.client.debug.RawPathDebugOverlay;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapAutomaticPlanningSamplingCache;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import net.shiroha233.roadweaver.network.MapNetworkPayloads;
import net.shiroha233.roadweaver.network.RawPathDebugPayloads;

public final class MapNetworkFabricClient {
    private MapNetworkFabricClient() {}

    public static void registerClientReceivers() {
        MapNetworkFabric.registerPayloadTypes();

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.SNAP, (payload, context) ->
                context.client().execute(() -> {
                    MapAutomaticPlanningSamplingCache.replace(
                            payload.dimension(),
                            payload.automaticPlanningSamplingBounds());
                    if (context.client().gui.screen() instanceof RoadMapScreen screen) {
                        screen.acceptSnapshotPart(payload.requestSeq(), payload.dimension(), payload.phase(), payload.responseIndex(), payload.snapshot());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.PATCH, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().gui.screen() instanceof RoadMapScreen screen) {
                        screen.acceptPatch(payload.dimension(), payload.patch());
                    } else {
                        MapSnapshotCache.applyPatch(payload.dimension(), payload.patch());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.TP_ACK, (payload, context) ->
                context.client().execute(() -> {
                    if (context.player() == null) return;
                    if (payload.success()) {
                        context.player().sendOverlayMessage(Component.translatable("gui.roadweaver.map.teleport.success_pos", payload.x(), payload.y(), payload.z()));
                    } else {
                        context.player().sendOverlayMessage(Component.translatable("gui.roadweaver.map.teleport.denied"));
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.ACCESS_SYNC, (payload, context) ->
                context.client().execute(() -> ClientMapAccessGuard.applyServerState(context.client(), payload.allowed()))
        );

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.AUTO_PLANNING_SAMPLING, (payload, context) ->
                context.client().execute(() -> MapAutomaticPlanningSamplingCache.replace(
                        payload.dimension(),
                        payload.bounds()))
        );

        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.SEARCH_RESP, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().gui.screen() instanceof RoadMapScreen screen) {
                        screen.acceptSearchResults(payload.requestSeq(), payload.dimension(), payload.success(), payload.results());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(RawPathDebugPayloads.RESPONSE, (payload, context) ->
                context.client().execute(() -> RawPathDebugOverlay.accept(payload)));
    }

    public static void requestSnapshot(int requestSeq,
                                       Identifier dimensionId,
                                       MapLoadPhase phase,
                                       int responseIndex,
                                       int minX,
                                       int minZ,
                                       int maxX,
                                       int maxZ) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapRequestRectPayload(
                requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ));
    }

    public static void requestTeleport(int x, int y, int z) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapTeleportPayload(x, y, z));
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapManualConnectPayload(
                new BlockPos(ax, 0, az), new BlockPos(bx, 0, bz)));
    }

    public static void requestSearch(int requestSeq, Identifier dimensionId, String query) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapSearchRequestPayload(requestSeq, dimensionId, query));
    }

    public static void requestRawPathDebug(int requestSeq, Identifier dimensionId) {
        ClientPlayNetworking.send(new RawPathDebugPayloads.Request(requestSeq, dimensionId));
    }
}
