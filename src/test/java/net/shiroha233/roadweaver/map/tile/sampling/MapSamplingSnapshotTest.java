/* 文件职责：验证主动地图采样快照的状态与百分比约束。 */
package net.shiroha233.roadweaver.map.tile.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSamplingSnapshotTest {
    private static final MapSamplingBounds BOUNDS = new MapSamplingBounds(0, 0, 64, 64);

    @Test
    void clampsPublishedPercent() {
        assertEquals(0, new MapSamplingSnapshot(
                MapSamplingSnapshot.Stage.SAMPLING_TERRAIN, BOUNDS, -5).percent());
        assertEquals(100, new MapSamplingSnapshot(
                MapSamplingSnapshot.Stage.WRITING_PNG, BOUNDS, 105).percent());
    }

    @Test
    void onlyWorkStagesAreActive() {
        assertTrue(new MapSamplingSnapshot(
                MapSamplingSnapshot.Stage.SAMPLING_TERRAIN, BOUNDS, 20).active());
        assertTrue(new MapSamplingSnapshot(
                MapSamplingSnapshot.Stage.WRITING_PNG, BOUNDS, 80).active());
        assertTrue(new MapSamplingSnapshot(
                MapSamplingSnapshot.Stage.QUERYING_STRUCTURES, BOUNDS, 90).active());
        assertFalse(new MapSamplingSnapshot(
                MapSamplingSnapshot.Stage.COMPLETED, BOUNDS, 100).active());
        assertFalse(MapSamplingSnapshot.idle().active());
    }

    @Test
    void rejectsMissingBoundsOutsideIdleState() {
        assertThrows(IllegalArgumentException.class, () -> new MapSamplingSnapshot(
                MapSamplingSnapshot.Stage.FAILED, null, 0));
    }
}
