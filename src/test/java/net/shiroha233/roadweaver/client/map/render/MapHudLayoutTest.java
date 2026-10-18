/* 文件职责：验证地图顶部状态卡与图例的窄屏宽度约束。 */
package net.shiroha233.roadweaver.client.map.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapHudLayoutTest {
    @Test
    void statusCardLeavesRoomForRightChrome() {
        assertEquals(142, MapStatusRenderer.maxCardWidth(320, 320));
        assertEquals(222, MapStatusRenderer.maxCardWidth(480, 480));
        assertEquals(290, MapStatusRenderer.maxCardWidth(1024, 1024));
        assertEquals(40, MapStatusRenderer.maxCardWidth(320, 64));
        assertTrue(MapStatusRenderer.maxTextWidth(320, 320)
                < MapStatusRenderer.maxTextWidth(480, 480));
    }

    @Test
    void legendCardClampsToScreen() {
        assertEquals(44, MapLegendRenderer.clampWidth(180, 124));
        assertEquals(142, MapLegendRenderer.clampWidth(500, 320));
        assertEquals(180, MapLegendRenderer.clampWidth(180, 480));
    }
}
