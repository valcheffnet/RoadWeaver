package net.shiroha233.roadweaver.pathfinding;

import net.shiroha233.roadweaver.pathfinding.StairPlanner.Settings;
import net.shiroha233.roadweaver.pathfinding.StairPlanner.Stair;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** A stair over {@code start..end}; fitToRoad reads only the span, not the planned heights. */
    private static Stair span(int start, int end) {
        return new Stair(start, end, new int[end - start + 1]);
    }

    private static void assertSmooth(int[] heights, int from, int to) {
        for (int i = from + 1; i <= to; i++) {
            assertTrue(Math.abs(heights[i] - heights[i - 1]) <= 1,
                    "step of " + (heights[i] - heights[i - 1]) + " at " + i + " in " + Arrays.toString(heights));
        }
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
        StairPlanner.fitToRoad(up, List.of(span(3, 8)), 4);
        assertArrayEquals(new int[]{105, 105, 105, 105, 105, 106, 107, 108, 108, 108, 108, 108}, up);

        int[] down = {109, 109, 110, 109, 108, 107, 106, 105, 104, 104};
        StairPlanner.fitToRoad(down, List.of(span(2, 7)), 4);
        assertArrayEquals(new int[]{109, 109, 109, 109, 108, 107, 106, 105, 104, 104}, down);
    }

    @Test
    void stairThatMeetsTheRoadIsKept() {
        int[] heights = {100, 100, 101, 102, 103, 104, 104, 105};
        int[] before = heights.clone();
        StairPlanner.fitToRoad(heights, List.of(span(1, 5)), 4);
        assertArrayEquals(before, heights);
    }

    @Test
    void stairsThatMeetEndToEndAreJoined() {
        // Down 104 -> 99 over 2..7, then up 102 -> 107 over 8..13: three blocks apart at the bottom of the valley.
        int[] valley = {104, 104, 104, 103, 102, 101, 100, 99, 102, 103, 104, 105, 106, 107, 107, 107};
        int[] ridge = new int[valley.length];
        for (int i = 0; i < valley.length; i++) ridge[i] = 200 - valley[i];
        for (int[] heights : new int[][]{valley, ridge}) {
            int[] before = heights.clone();
            StairPlanner.fitToRoad(heights, List.of(span(2, 7), span(8, 13)), 4);
            assertSmooth(heights, 0, heights.length - 1);
            assertEquals(before[2], heights[2], "top of the first stair still meets the road");
            assertEquals(before[13], heights[13], "top of the second stair still meets the road");
        }
    }

    @Test
    void stairsOnRealGroundNeverOverlapOrJump() {
        // Ground under two roads of a test world where neighbouring stairs ended up three blocks apart: a valley and
        // a ridge between steep slopes, and a stretch across a ravine where two spans overlapped.
        int[][] grounds = {
                {96, 96, 94, 91, 93, 93, 94, 95, 94, 95, 94, 94, 94, 105, 105, 94, 94, 93, 93, 93, 93, 92, 92, 92, 90,
                        88, 88, 89, 89, 93, 93, 94, 95, 92, 93, 93, 91, 91, 90, 90, 88, 88, 88, 88, 87, 89, 89, 90, 89,
                        89, 90, 90, 91, 90, 90, 90, 89, 91, 91, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 93, 94, 95,
                        95, 96, 95, 96, 96, 96, 96, 96, 96, 99, 100, 101, 101, 100, 98, 97, 96, 96, 96, 100, 101, 101,
                        101, 101, 101, 101, 101, 101, 100, 100, 99, 98},
                {113, 113, 113, 113, 113, 114, 115, 115, 115, 115, 115, 115, 116, 115, 115, 115, 115, 115, 115, 114,
                        114, 114, 113, 113, 98, 98, 98, 99, 113, 101, 113, 113, 114, 114, 114, 114, 114, 115, 114, 115,
                        114, 114, 114, 114, 114},
        };
        for (int[] ground : grounds) {
            List<Stair> stairs = StairPlanner.plan(ground, new boolean[ground.length], Settings.DEFAULT);
            assertFalse(stairs.isEmpty());
            int[] heights = ground.clone();
            for (Stair stair : stairs) {
                for (int i = stair.start(); i <= stair.end(); i++) heights[i] = stair.height(i);
            }
            StairPlanner.fitToRoad(heights, stairs, 4);
            for (int k = 0; k < stairs.size(); k++) {
                Stair stair = stairs.get(k);
                boolean touchesPrevious = k > 0 && stair.start() == stairs.get(k - 1).end() + 1;
                if (k > 0) assertTrue(stair.start() > stairs.get(k - 1).end(), "overlapping stairs " + stairs);
                assertSmooth(heights, touchesPrevious ? stair.start() - 1 : stair.start(), stair.end());
            }
        }
    }

    @Test
    void cliffTooSteepForStepsIsLeftAlone() {
        // 20 blocks in 4 segments, with no flat ground around to spread it over.
        int[] ground = {64, 64, 69, 74, 79, 84, 84, 84};
        assertTrue(StairPlanner.plan(ground, new boolean[ground.length], Settings.DEFAULT).isEmpty());
    }
}
