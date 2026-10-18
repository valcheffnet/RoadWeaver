/* Client half of the map networking: receivers for server packets and the requests the map screen sends. */
package net.shiroha233.roadweaver.network.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.shiroha233.roadweaver.client.debug.RawPathDebugOverlay;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapAutomaticPlanningSamplingCache;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import net.shiroha233.roadweaver.network.MapNetworkPayloads;
import net.shiroha233.roadweaver.network.RawPathDebugPayloads;

public final class MapNetworkNeoForgeClient {
    private MapNetworkNeoForgeClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(MapNetworkNeoForgeClient::onRegisterHandlers);
    }

    private static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {

        event.register(MapNetworkPayloads.SNAP, (payload, context) ->
                Minecraft.getInstance().execute(() -> {
                    MapAutomaticPlanningSamplingCache.replace(
                            payload.dimension(),
                            payload.automaticPlanningSamplingBounds());
                    if (Minecraft.getInstance().gui.screen() instanceof RoadMapScreen screen) {
                        screen.acceptSnapshotPart(payload.requestSeq(), payload.dimension(), payload.phase(), payload.responseIndex(), payload.snapshot());
                    }
                })
        );

        event.register(MapNetworkPayloads.PATCH, (payload, context) ->
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().gui.screen() instanceof RoadMapScreen screen) {
                        screen.acceptPatch(payload.dimension(), payload.patch());
                    } else {
                        MapSnapshotCache.applyPatch(payload.dimension(), payload.patch());
                    }
                })
        );

        event.register(MapNetworkPayloads.TP_ACK, (payload, context) ->
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player == null) return;
                    if (payload.success()) {
                        Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("gui.roadweaver.map.teleport.success_pos", payload.x(), payload.y(), payload.z()));
                    } else {
                        Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("gui.roadweaver.map.teleport.denied"));
                    }
                })
        );

        event.register(MapNetworkPayloads.ACCESS_SYNC, (payload, context) ->
                Minecraft.getInstance().execute(() -> ClientMapAccessGuard.applyServerState(Minecraft.getInstance(), payload.allowed()))
        );

        event.register(MapNetworkPayloads.AUTO_PLANNING_SAMPLING, (payload, context) ->
                Minecraft.getInstance().execute(() -> MapAutomaticPlanningSamplingCache.replace(
                        payload.dimension(),
                        payload.bounds()))
        );

        event.register(MapNetworkPayloads.SEARCH_RESP, (payload, context) ->
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().gui.screen() instanceof RoadMapScreen screen) {
                        screen.acceptSearchResults(payload.requestSeq(), payload.dimension(), payload.success(), payload.results());
                    }
                })
        );

        event.register(RawPathDebugPayloads.RESPONSE, (payload, context) ->
                Minecraft.getInstance().execute(() -> RawPathDebugOverlay.accept(payload)));
    }

    public static void requestSnapshot(int requestSeq,
                                       Identifier dimensionId,
                                       MapLoadPhase phase,
                                       int responseIndex,
                                       int minX,
                                       int minZ,
                                       int maxX,
                                       int maxZ) {
        ClientPacketDistributor.sendToServer(new MapNetworkPayloads.MapRequestRectPayload(
                requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ));
    }

    public static void requestTeleport(int x, int y, int z) {
        ClientPacketDistributor.sendToServer(new MapNetworkPayloads.MapTeleportPayload(x, y, z));
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        ClientPacketDistributor.sendToServer(new MapNetworkPayloads.MapManualConnectPayload(
                new BlockPos(ax, 0, az), new BlockPos(bx, 0, bz)));
    }

    public static void requestSearch(int requestSeq, Identifier dimensionId, String query) {
        ClientPacketDistributor.sendToServer(new MapNetworkPayloads.MapSearchRequestPayload(requestSeq, dimensionId, query));
    }

    public static void requestRawPathDebug(int requestSeq, Identifier dimensionId) {
        ClientPacketDistributor.sendToServer(new RawPathDebugPayloads.Request(requestSeq, dimensionId));
    }
}
