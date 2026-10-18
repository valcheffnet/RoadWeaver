package net.shiroha233.roadweaver.pathfinding;

import net.shiroha233.roadweaver.pathfinding.StairPlanner.Settings;
import net.shiroha233.roadweaver.pathfinding.StairPlanner.Stair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StairPlannerTest {
    private static int[] terrain(int flatBefore, int climb, int flatAfter, int base) {
        int[] g = new int[flatBefore + climb + flatAfter];
        for (int i = 0; i < g.length; i++) {
            int c = Math.max(0, Math.min(climb, i - flatBefore));
            g[i] = base + c;
        }
        return g;
    }

    @Test
    void steepHillsideBecomesStairsWithLandings() {
        int[] ground = terrain(10, 20, 10, 64);
        List<Stair> stairs = StairPlanner.plan(ground, new boolean[ground.length], Settings.DEFAULT);
        assertEquals(1, stairs.size());
        Stair stair = stairs.getFirst();
        int maxRun = 0;
        int run = 0;
        boolean sawLanding = false;
        for (int i = stair.start() + 1; i <= stair.end(); i++) {
            int step = stair.height(i) - stair.height(i - 1);
            assertTrue(step == 0 || step == 1, "step " + step + " at " + i);
            if (step == 1) {
                maxRun = Math.max(maxRun, ++run);
            } else {
                if (run > 0) sawLanding = true;
                run = 0;
            }
        }
        assertTrue(maxRun <= 8, "flight longer than 8 steps: " + maxRun);
        assertTrue(sawLanding);
        assertEquals(stair.height(stair.end()) - stair.height(stair.start()), 20 - 1, "climb kept (smoothed ends)");
    }

    @Test
    void gentleSlopeHasNoStairs() {
        int[] ground = new int[40];
        for (int i = 0; i < ground.length; i++) ground[i] = 70 + i / 2;
        assertTrue(StairPlanner.plan(ground, new boolean[ground.length], Settings.DEFAULT).isEmpty());
    }

    @Test
    void goesDownToo() {
        int[] ground = terrain(8, 12, 8, 80);
        for (int i = 0; i < ground.length / 2; i++) {
            int t = ground[i];
            ground[i] = ground[ground.length - 1 - i];
            ground[ground.length - 1 - i] = t;
        }
        List<Stair> stairs = StairPlanner.plan(ground, new boolean[ground.length], Settings.DEFAULT);
        assertEquals(1, stairs.size());
        Stair stair = stairs.getFirst();
        assertTrue(stair.height(stair.end()) < stair.height(stair.start()));
    }

    @Test
    void neverCrossesABridge() {
        int[] ground = terrain(5, 20, 5, 64);
        boolean[] blocked = new boolean[ground.length];
        for (int i = 12; i < 16; i++) blocked[i] = true;
        for (Stair stair : StairPlanner.plan(ground, blocked, Settings.DEFAULT)) {
            for (int i = stair.start(); i <= stair.end(); i++) assertTrue(!blocked[i], "stair over bridge at " + i);
        }
    }

    @Test
    void stairEndsMeetTheRoad() {
        // Steps 3..8 climb 104 -> 109, the road before lies at 105 and after at 108.
        int[] up = {105, 105, 105, 104, 105, 106, 107, 108, 109, 108, 108, 108};
        StairPlanner.fitToRoad(up, 3, 8, 4);
        assertArrayEquals(new int[]{105, 105, 105, 105, 105, 106, 107, 108, 108, 108, 108, 108}, up);

        int[] down = {109, 109, 110, 109, 108, 107, 106, 105, 104, 104};
        StairPlanner.fitToRoad(down, 2, 7, 4);
        assertArrayEquals(new int[]{109, 109, 109, 109, 108, 107, 106, 105, 104, 104}, down);
    }

    @Test
    void stairThatMeetsTheRoadIsKept() {
        int[] heights = {100, 100, 101, 102, 103, 104, 104, 105};
        int[] before = heights.clone();
        StairPlanner.fitToRoad(heights, 1, 5, 4);
        assertArrayEquals(before, heights);
    }

    @Test
    void cliffTooSteepForStepsIsLeftAlone() {
        // 20 blocks in 4 segments, with no flat ground around to spread it over.
        int[] ground = {64, 64, 69, 74, 79, 84, 84, 84};
        assertTrue(StairPlanner.plan(ground, new boolean[ground.length], Settings.DEFAULT).isEmpty());
    }
}
