/* 文件职责：将预编译的区块道路密度 stamp 接入 Fabric 原版 Beardifier。 */
package net.shiroha233.roadweaver.mixin.fabric;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.shiroha233.roadweaver.beardifier.RoadBeardifierAccess;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.worldgen.road.RoadChunkPlan;
import net.shiroha233.roadweaver.worldgen.road.RoadDensityStamp;
import net.shiroha233.roadweaver.worldgen.road.RoadWorldgenPlanCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 向 Beardifier 注入道路路基密度贡献。
 * 26.x samples density in volumes (sampleVolume) and single points (sampleValue) instead of compute(ctx).
 */
@Mixin(Beardifier.class)
public class BeardifierMixin implements RoadBeardifierAccess {
    @Unique
    private static final Logger roadweaver$LOGGER = LoggerFactory.getLogger("RoadWeaver/BeardifierMixin");

    @Unique
    private RoadChunkPlan roadweaver$roadChunkPlan;

    @Override
    public void roadweaver$setRoadChunkPlan(RoadChunkPlan plan) {
        this.roadweaver$roadChunkPlan = plan;
    }

    @Override
    public RoadChunkPlan roadweaver$getRoadChunkPlan() {
        return this.roadweaver$roadChunkPlan;
    }

    @Inject(method = "forStructuresInChunk", at = @At("RETURN"), cancellable = true)
    private static void roadweaver$injectRoadData(StructureManager mgr, ChunkPos pos,
                                                   CallbackInfoReturnable<Beardifier> cir) {
        try {
            ModConfig cfg = ConfigService.get();
            if (cfg == null) return;
            LevelAccessor levelAccessor = ((StructureManagerAccessor) mgr).roadweaver$getLevel();
            ServerLevel serverLevel = roadweaver$resolveServerLevel(levelAccessor);
            if (serverLevel == null || !Level.OVERWORLD.equals(serverLevel.dimension())) return;
            RoadChunkPlan plan = RoadWorldgenPlanCache.get(serverLevel, pos, cfg);
            if (plan == null || plan.densityStamp().isEmpty()) return;
            Beardifier beardifier = cir.getReturnValue();
            if (beardifier == Beardifier.EMPTY) {
                // Chunks without structures get the shared EMPTY instance; never attach a plan to it.
                // A null affected box makes the vanilla part contribute nothing, as EMPTY does.
                beardifier = new Beardifier(List.of(), List.of(), null);
                cir.setReturnValue(beardifier);
            }
            ((RoadBeardifierAccess) beardifier).roadweaver$setRoadChunkPlan(plan);
        } catch (Exception e) {
            roadweaver$LOGGER.debug("Failed to inject road data into Beardifier", e);
        }
    }

    @Inject(method = "sampleVolume", at = @At("RETURN"))
    private void roadweaver$addRoadDensityVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume,
                                                 CallbackInfo ci) {
        RoadChunkPlan plan = roadweaver$roadChunkPlan;
        if (plan == null) return;
        RoadDensityStamp stamp = plan.densityStamp();
        if (stamp.isEmpty()) return;
        ChunkPos chunk = stamp.chunkPos();
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        for (int ix = 0; ix < volume.sizeX(); ix++) {
            int x = volume.blockX(ix);
            if (x < minX || x >= minX + 16) continue;
            for (int iz = 0; iz < volume.sizeZ(); iz++) {
                int z = volume.blockZ(iz);
                if (z < minZ || z >= minZ + 16) continue;
                for (int iy = 0; iy < volume.sizeY(); iy++) {
                    double road = stamp.density(x, volume.blockY(iy), z);
                    if (road != 0.0D) {
                        buffer.addTo(volume.indexUnchecked(ix, iy, iz), (float) road);
                    }
                }
            }
        }
    }

    @Inject(method = "sampleValue", at = @At("RETURN"), cancellable = true)
    private void roadweaver$addRoadDensityValue(SamplerContext context, int x, int y, int z,
                                                CallbackInfoReturnable<Float> cir) {
        RoadChunkPlan plan = roadweaver$roadChunkPlan;
        if (plan == null || plan.densityStamp().isEmpty()) return;
        double road = plan.densityStamp().density(x, y, z);
        if (road != 0.0D) {
            cir.setReturnValue(cir.getReturnValue() + (float) road);
        }
    }

    @Unique
    private static ServerLevel roadweaver$resolveServerLevel(LevelAccessor accessor) {
        if (accessor instanceof ServerLevel sl) return sl;
        if (accessor instanceof WorldGenRegion wgr) return wgr.getLevel();
        return null;
    }
}
