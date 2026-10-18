/* 文件职责：实现 NeoForge 平台道路地图的请求、快照与增量状态网络通信。 */
package net.shiroha233.roadweaver.network.neoforge;

import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
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

/** NeoForge 平台网络通信实现。 */
public final class MapNetworkNeoForge {
    private MapNetworkNeoForge() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(MapNetworkNeoForge::onRegisterPayloads);
    }

    /**
     * All channels are optional, so players without the mod can join; the server only sends map data to players
     * whose client has the channel. Client handlers are added by the client side.
     */
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(MapNetworkPayloads.SNAP, MapNetworkPayloads.MapSnapshotPayload.CODEC);
        registrar.playToClient(MapNetworkPayloads.PATCH, MapNetworkPayloads.MapPatchPayload.CODEC);
        registrar.playToClient(MapNetworkPayloads.AUTO_PLANNING_SAMPLING, MapNetworkPayloads.MapAutomaticPlanningSamplingPayload.CODEC);
        registrar.playToClient(MapNetworkPayloads.TP_ACK, MapNetworkPayloads.MapTeleportAckPayload.CODEC);
        registrar.playToClient(MapNetworkPayloads.ACCESS_SYNC, MapNetworkPayloads.MapAccessSyncPayload.CODEC);
        registrar.playToClient(MapNetworkPayloads.SEARCH_RESP, MapNetworkPayloads.MapSearchResponsePayload.CODEC);
        registrar.playToClient(RawPathDebugPayloads.RESPONSE, RawPathDebugPayloads.Response.CODEC);
        registerServerReceivers(registrar);
    }

    private static ServerPlayer player(IPayloadContext context) {
        return (ServerPlayer) context.player();
    }

    private static MinecraftServer server(IPayloadContext context) {
        return player(context).level().getServer();
    }

    private static void registerServerReceivers(PayloadRegistrar registrar) {
        registerRawPathRequest(registrar);

        registrar.playToServer(MapNetworkPayloads.REQ_RECT, MapNetworkPayloads.MapRequestRectPayload.CODEC, (payload, context) -> {
            ServerPlayer player = player(context);
            if (!MapAccessService.canOpenMap(player)) {
                server(context).execute(() -> syncMapAccess(player));
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
                    .thenAccept(reply -> server(context).execute(() -> {
                        if (!player.isRemoved()) PacketDistributor.sendToPlayer(player, reply);
                    }));
        });

        registrar.playToServer(MapNetworkPayloads.TP_REQ, MapNetworkPayloads.MapTeleportPayload.CODEC, (payload, context) ->
                server(context).execute(() -> handleTeleport(payload, player(context), server(context)))
        );

        registrar.playToServer(MapNetworkPayloads.MAN_REQ, MapNetworkPayloads.MapManualConnectPayload.CODEC, (payload, context) ->
                server(context).execute(() -> {
                    ServerPlayer player = player(context);
                    if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                        player.sendOverlayMessage(Component.translatable("gui.roadweaver.map.manual_connect.denied"));
                        return;
                    }
                    if (!Level.OVERWORLD.equals(player.level().dimension())) return;
                    RoadNetworkApi.ensureConnection(player.level(), payload.from(), payload.to());
                })
        );

        registrar.playToServer(MapNetworkPayloads.SEARCH_REQ, MapNetworkPayloads.MapSearchRequestPayload.CODEC, (payload, context) -> {
            ServerPlayer player = player(context);
            if (!MapAccessService.canOpenMap(player)) {
                server(context).execute(() -> PacketDistributor.sendToPlayer(player,
                        new MapNetworkPayloads.MapSearchResponsePayload(payload.requestSeq(), payload.dimension(), false, java.util.List.of())));
                return;
            }
            var level = player.level();
            if (!level.dimension().identifier().equals(payload.dimension())
                    || !MapStructureSearchService.tryBeginRequest(player.getUUID())) {
                server(context).execute(() -> PacketDistributor.sendToPlayer(player,
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
                        .thenAccept(reply -> server(context).execute(() -> {
                            if (!player.isRemoved()) PacketDistributor.sendToPlayer(player, reply);
                        }));
            } catch (RuntimeException submissionFailure) {
                MapStructureSearchService.finishRequest(player.getUUID());
                server(context).execute(() -> PacketDistributor.sendToPlayer(player,
                        new MapNetworkPayloads.MapSearchResponsePayload(payload.requestSeq(), payload.dimension(), false, java.util.List.of())));
            }
        });
    }

    private static void registerRawPathRequest(PayloadRegistrar registrar) {
        registrar.playToServer(RawPathDebugPayloads.REQUEST, RawPathDebugPayloads.Request.CODEC, (payload, context) -> {
            ServerPlayer player = player(context);
            var level = player.level();
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                server(context).execute(() -> player.sendOverlayMessage(
                        Component.translatable("debug.roadweaver.raw_paths.denied")));
                return;
            }
            if (!level.dimension().identifier().equals(payload.dimension())) return;
            int x = player.getBlockX(), z = player.getBlockZ();
            CompletableFuture.supplyAsync(() -> new RawPathDebugPayloads.Response(
                            payload.sequence(), level.dimension().identifier(), RawPathDebugService.enabled(),
                            RawPathDebugService.nearby(level, x, z)),
                    ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.MAP))
                    .thenAccept(reply -> server(context).execute(() -> {
                        if (!player.isRemoved() && player.level() == level) PacketDistributor.sendToPlayer(player, reply);
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
            PacketDistributor.sendToPlayer(player, new MapNetworkPayloads.MapTeleportAckPayload(false, 0, 0, 0));
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
                    PacketDistributor.sendToPlayer(player, new MapNetworkPayloads.MapTeleportAckPayload(true, x, y, z));
                }));
    }

    public static void broadcastPatch(ServerPlayer player, Identifier dimensionId, MapSnapshotPatch patch) {
        if (player == null || dimensionId == null || patch == null || patch.isEmpty()) return;
        if (!player.connection.hasChannel(MapNetworkPayloads.PATCH)) return;
        PacketDistributor.sendToPlayer(player, new MapNetworkPayloads.MapPatchPayload(dimensionId, patch));
    }

    public static void broadcastAutomaticPlanningSampling(ServerPlayer player,
                                                          Identifier dimensionId,
                                                          List<AutomaticPlanningSamplingBounds> bounds) {
        if (player == null || dimensionId == null) return;
        if (!player.connection.hasChannel(MapNetworkPayloads.AUTO_PLANNING_SAMPLING)) return;
        PacketDistributor.sendToPlayer(player,
                new MapNetworkPayloads.MapAutomaticPlanningSamplingPayload(dimensionId, bounds));
    }

    public static void syncMapAccess(ServerPlayer player) {
        if (player != null && player.connection.hasChannel(MapNetworkPayloads.ACCESS_SYNC)) {
            PacketDistributor.sendToPlayer(player, new MapNetworkPayloads.MapAccessSyncPayload(MapAccessService.canOpenMap(player)));
        }
    }
}
