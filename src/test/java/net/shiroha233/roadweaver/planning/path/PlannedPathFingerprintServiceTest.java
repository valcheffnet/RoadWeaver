/* 文件职责：验证路径指纹包含实际采样策略和全部关键寻路配置。 */
package net.shiroha233.roadweaver.planning.path;

import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.persistence.WorldgenFingerprint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PlannedPathFingerprintServiceTest {
    private static final WorldgenFingerprint WORLD = new WorldgenFingerprint("world-a", 1, 4000);

    @Test
    void sameInputsProduceStableFingerprint() {
        PathfindingCostConfig config = new PathfindingCostConfig();

        assertEquals(
                PlannedPathFingerprintService.create(WORLD, TerrainSamplingMode.COARSE_CORRIDOR, config),
                PlannedPathFingerprintService.create(WORLD, TerrainSamplingMode.COARSE_CORRIDOR, config.snapshot()));
    }

    @Test
    void modeAndCostChangesInvalidatePath() {
        PathfindingCostConfig original = new PathfindingCostConfig();
        PathfindingCostConfig changed = original.snapshot();
        changed.setElevationWeight(original.elevationWeight() + 1);
        String coarse = PlannedPathFingerprintService.create(WORLD, TerrainSamplingMode.COARSE_CORRIDOR, original);

        assertNotEquals(coarse,
                PlannedPathFingerprintService.create(WORLD, TerrainSamplingMode.FULL_REGION, original));
        assertNotEquals(coarse,
                PlannedPathFingerprintService.create(WORLD, TerrainSamplingMode.COARSE_CORRIDOR, changed));
    }
}
