/* 文件职责：注册 NeoForge 服务端生命周期与 Tick 钩子。 */
package net.shiroha233.roadweaver.planning.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.generation.IdleRoadGenerationService;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.runtime.CacheManager;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.structures.village.VillageStreetConnector;

/**
 * NeoForge 服务端规划钩子
 */
public final class ServerPlanningHooks {
    private ServerPlanningHooks() {}

    private static int tick;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onServerStopping);
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ServerPlanningHooks::onLevelUnload);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        tick = 0;
        CacheManager.onServerStarted();
        ThreadPoolManager.onServerStarted(server);
        // Keep queued sign text: the spawn chunks are generated before this event and their signs are still waiting.
        IdleRoadGenerationService.onServerStarted();

        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) return;

        StructureDiscoveryService.discoverFromLevel(level);

        if (server.isDedicatedServer()) {
            RoadGenerationService.onServerStarted();
            if (InitialGenManager.shouldRunInitialGeneration(level)) {
                RoadPlanningService.initialPlanAsync(level);
            }
            return;
        }

        RoadGenerationService.onServerStarted();
        if (InitialGenManager.shouldRunInitialGeneration(level)) {
            InitialGenManager.begin(level);
            InitialGenManager.blockUntilDone(level);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if ((tick++ % 20) == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (Level.OVERWORLD.equals(p.level().dimension())) {
                    SignTextService.onChunkReady(p.level(), p.chunkPosition());
                    RoadPlanningService.planAroundPlayer(p);
                    IdleRoadGenerationService.tickPlayer(p);
                }
            }
        }

        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level != null) {
            IdleRoadGenerationService.tick(level);
            RoadGenerationService.tick(level);
            SignTextService.tick(level);
            VillageStreetConnector.tick(level);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        // 先递增 epoch 并停池，阻止旧任务的完成回调回写世界状态。
        ThreadPoolManager.onServerStopping();
        RoadGenerationService.onServerStopping();
        RoadPlanningService.resetAll();
        CacheManager.onServerStopping(event.getServer().getAllLevels());
        SignTextService.clearPending();
        VillageStreetConnector.clear();
        tick = 0;
    }

    /** Sign text is written once the chunk is a full LevelChunk; generation alone marks the chunk too early. */
    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getChunk().getLevel() instanceof ServerLevel level) {
            SignTextService.onChunkReady(level, event.getChunk().getPos());
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CacheManager.onDimensionUnload(level);
        }
    }
}
