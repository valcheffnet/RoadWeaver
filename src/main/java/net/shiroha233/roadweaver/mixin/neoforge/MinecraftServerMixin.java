/* 文件职责：在 Fabric 服务端关卡准备阶段触发 RoadWeaver 初始化。 */
package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.structures.placement.SpawnCabinPlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MinecraftServer Mixin
 * New worlds: vanilla fully generates the spawn chunks inside setInitialSpawn (spawn search), which runs before
 * prepareLevels. The initial road network is therefore planned at the start of setInitialSpawn, around the predicted
 * spawn, and the spawn cabin is built once setInitialSpawn has finished. Existing worlds keep the prepareLevels path
 * and get no cabin: the area around their spawn has long been explored and may be built on.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Unique
    private static volatile boolean roadweaver$initialRunDone;

    @Inject(method = "setInitialSpawn", at = @At("HEAD"))
    private static void roadweaver$planBeforeSpawnSearch(ServerLevel level, ServerLevelData levelData, boolean bonusChest,
                                                       boolean debug, LevelLoadListener listener, CallbackInfo ci) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        RoadShardStorage.preload(level);
        if (!InitialGenManager.shouldRunInitialGeneration(level)) return;
        // Plan around the point where vanilla starts its spawn search; setInitialSpawn overwrites this right after.
        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), SpawnCabinPlacer.predictSpawn(level), 0.0F, 0.0F));
        InitialGenManager.begin(level);
        InitialGenManager.blockUntilDone(level);
        roadweaver$initialRunDone = true;
    }

    @Inject(method = "setInitialSpawn", at = @At("RETURN"))
    private static void roadweaver$placeCabinAfterSpawnSearch(ServerLevel level, ServerLevelData levelData, boolean bonusChest,
                                                            boolean debug, LevelLoadListener listener, CallbackInfo ci) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        SpawnCabinPlacer.placePlannedCabin(level, levelData.getRespawnData().pos());
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void roadweaver$preloadBeforePrepareLevels(CallbackInfo ci) {
        if (roadweaver$initialRunDone) {
            roadweaver$initialRunDone = false;
            return;
        }
        MinecraftServer server = (MinecraftServer)(Object)this;
        ServerLevel level = server.overworld();
        if (level == null) return;
        RoadShardStorage.preload(level);
        if (InitialGenManager.shouldRunInitialGeneration(level)) {
            InitialGenManager.begin(level);
            InitialGenManager.blockUntilDone(level);
        } else {
            RoadGenerationService.onServerStarted();
        }
    }
}
