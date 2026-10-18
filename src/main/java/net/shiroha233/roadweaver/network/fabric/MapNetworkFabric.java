/* 文件职责：实现 Fabric 平台道路地图的请求、快照与增量状态网络通信。 */
package net.shiroha233.roadweaver.network.fabric;

import net.minecraft.server.permissions.Permissions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.api.RoadNetworkApi;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.network.MapNetworkPayloads;
import net.shiroha233.roadweaver.network.RawPathDebugPayloads;
import net.shiroha233.roadweaver.planning.path.RawPathDebugService;
import net.shiroha233.roadweaver.map.search.MapStructureSearchService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Fabric 平台网络通信实现。 */
public final class MapNetworkFabric {
    private static boolean payloadTypesRegistered;

    private MapNetworkFabric() {}

    public static synchronized void registerPayloadTypes() {
        if (payloadTypesRegistered) return;

        PayloadTypeRegistry.serverboundPlay().register(MapNetworkPayloads.REQ_RECT, MapNetworkPayloads.MapRequestRectPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MapNetworkPayloads.TP_REQ, MapNetworkPayloads.MapTeleportPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MapNetworkPayloads.MAN_REQ, MapNetworkPayloads.MapManualConnectPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MapNetworkPayloads.SEARCH_REQ, MapNetworkPayloads.MapSearchRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RawPathDebugPayloads.REQUEST, RawPathDebugPayloads.Request.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(MapNetworkPayloads.SNAP, MapNetworkPayloads.MapSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MapNetworkPayloads.PATCH, MapNetworkPayloads.MapPatchPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MapNetworkPayloads.AUTO_PLANNING_SAMPLING, MapNetworkPayloads.MapAutomaticPlanningSamplingPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MapNetworkPayloads.TP_ACK, MapNetworkPayloads.MapTeleportAckPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MapNetworkPayloads.ACCESS_SYNC, MapNetworkPayloads.MapAccessSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MapNetworkPayloads.SEARCH_RESP, MapNetworkPayloads.MapSearchResponsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RawPathDebugPayloads.RESPONSE, RawPathDebugPayloads.Response.CODEC);

        payloadTypesRegistered = true;
    }

    public static void registerServerReceivers() {
        registerPayloadTypes();
        registerRawPathRequest();

        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.REQ_RECT, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!MapAccessService.canOpenMap(player)) {
                context.server().execute(() -> syncMapAccess(player));
                return;
            }

            CompletableFuture
                    .supplyAsync(() -> {
                        var level = player.level();
                        MapSnapshot snapshot = buildSnapshot(level, payload.phase(), payload.minX(), payload.minZ(), payload.maxX(), payload.maxZ());
                        return new MapNetworkPayloads.MapSnapshotPayload(
                                payload.requestSeq(),
                                level.dimension().identifier(),
                                payload.phase(),
                                payload.responseIndex(),
                                snapshot,
                                AutomaticPlanningSamplingActivities.snapshot(level)
                        );
                    }, ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.MAP))
                    .thenAccept(reply -> context.server().execute(() -> {
                        if (!player.isRemoved()) ServerPlayNetworking.send(player, reply);
                    }));
        });

        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.TP_REQ, (payload, context) ->
                context.server().execute(() -> handleTeleport(payload, context.player(), context.server()))
        );

        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.MAN_REQ, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                        player.sendOverlayMessage(Component.translatable("gui.roadweaver.map.manual_connect.denied"));
                        return;
                    }
                    if (!Level.OVERWORLD.equals(player.level().dimension())) return;
                    RoadNetworkApi.ensureConnection(player.level(), payload.from(), payload.to());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.SEARCH_REQ, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!MapAccessService.canOpenMap(player)) {
                context.server().execute(() -> ServerPlayNetworking.send(player,
                        new MapNetworkPayloads.MapSearchResponsePayload(payload.requestSeq(), payload.dimension(), false, java.util.List.of())));
                return;
            }
            var level = player.level();
            if (!level.dimension().identifier().equals(payload.dimension())
                    || !MapStructureSearchService.tryBeginRequest(player.getUUID())) {
                context.server().execute(() -> ServerPlayNetworking.send(player,
                        new MapNetworkPayloads.MapSearchResponsePayload(payload.requestSeq(), payload.dimension(), false, java.util.List.of())));
                return;
            }
            try {
                CompletableFuture
                        .supplyAsync(() -> MapStructureSearchService.search(level, payload.query()),
                                ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.MAP))
                        .handle((results, failure) -> {
                            MapStructureSearchService.finishRequest(player.getUUID());
                            return new MapNetworkPayloads.MapSearchResponsePayload(
                                    payload.requestSeq(), payload.dimension(), failure == null,
                                    failure == null ? results : java.util.List.of());
                        })
                        .thenAccept(reply -> context.server().execute(() -> {
                            if (!player.isRemoved()) ServerPlayNetworking.send(player, reply);
                        }));
            } catch (RuntimeException submissionFailure) {
                MapStructureSearchService.finishRequest(player.getUUID());
                context.server().execute(() -> ServerPlayNetworking.send(player,
                        new MapNetworkPayloads.MapSearchResponsePayload(payload.requestSeq(), payload.dimension(), false, java.util.List.of())));
            }
        });
    }

    private static void registerRawPathRequest() {
        ServerPlayNetworking.registerGlobalReceiver(RawPathDebugPayloads.REQUEST, (payload, context) -> {
            ServerPlayer player = context.player();
            var level = player.level();
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                context.server().execute(() -> player.sendOverlayMessage(
                        Component.translatable("debug.roadweaver.raw_paths.denied")));
                return;
            }
            if (!level.dimension().identifier().equals(payload.dimension())) return;
            int x = player.getBlockX(), z = player.getBlockZ();
            CompletableFuture.supplyAsync(() -> new RawPathDebugPayloads.Response(
                            payload.sequence(), level.dimension().identifier(), RawPathDebugService.enabled(),
                            RawPathDebugService.nearby(level, x, z)),
                    ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.MAP))
                    .thenAccept(reply -> context.server().execute(() -> {
                        if (!player.isRemoved() && player.level() == level) ServerPlayNetworking.send(player, reply);
                    }));
        });
    }

    private static MapSnapshot buildSnapshot(net.minecraft.server.level.ServerLevel level,
                                             MapLoadPhase phase,
                                             int minX,
                                             int minZ,
                                             int maxX,
                                             int maxZ) {
        return switch (phase) {
            case STRUCTURES -> MapDataCollector.buildStructuresSnapshot(level, minX, minZ, maxX, maxZ);
            case ROADS -> MapDataCollector.buildRoadsSnapshot(level, minX, minZ, maxX, maxZ);
            case CONNECTIONS -> MapDataCollector.buildConnectionsSnapshot(level, minX, minZ, maxX, maxZ);
        };
    }

    private static void handleTeleport(MapNetworkPayloads.MapTeleportPayload payload,
                                       ServerPlayer player,
                                       net.minecraft.server.MinecraftServer server) {
        if (!player.isCreative() && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            ServerPlayNetworking.send(player, new MapNetworkPayloads.MapTeleportAckPayload(false, 0, 0, 0));
            return;
        }

        var level = player.level();
        int x = payload.x();
        int z = payload.z();
        level.getChunkSource().getChunkFuture(x >> 4, z >> 4, ChunkStatus.FULL, true)
                .thenAccept(ignored -> server.execute(() -> {
                    if (player.isRemoved() || player.hasDisconnected()) return;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    y = y <= level.getMinY() ? level.getSeaLevel() + 1 : y + 1;
                    player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
                    ServerPlayNetworking.send(player, new MapNetworkPayloads.MapTeleportAckPayload(true, x, y, z));
                }));
    }

    public static void broadcastPatch(ServerPlayer player, Identifier dimensionId, MapSnapshotPatch patch) {
        if (player == null || dimensionId == null || patch == null || patch.isEmpty()) return;
        if (!ServerPlayNetworking.canSend(player, MapNetworkPayloads.PATCH)) return;
        ServerPlayNetworking.send(player, new MapNetworkPayloads.MapPatchPayload(dimensionId, patch));
    }

    public static void broadcastAutomaticPlanningSampling(ServerPlayer player,
                                                          Identifier dimensionId,
                                                          List<AutomaticPlanningSamplingBounds> bounds) {
        if (player == null || dimensionId == null) return;
        if (!ServerPlayNetworking.canSend(player, MapNetworkPayloads.AUTO_PLANNING_SAMPLING)) return;
        ServerPlayNetworking.send(player,
                new MapNetworkPayloads.MapAutomaticPlanningSamplingPayload(dimensionId, bounds));
    }

    public static void syncMapAccess(ServerPlayer player) {
        if (player != null && ServerPlayNetworking.canSend(player, MapNetworkPayloads.ACCESS_SYNC)) {
            ServerPlayNetworking.send(player, new MapNetworkPayloads.MapAccessSyncPayload(MapAccessService.canOpenMap(player)));
        }
    }
}
