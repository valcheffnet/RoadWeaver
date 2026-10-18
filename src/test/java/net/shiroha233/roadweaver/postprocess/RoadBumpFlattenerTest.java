package net.shiroha233.roadweaver.postprocess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RoadBumpFlattenerTest {
    @Test
    void levelsShortBumpsAndDips() {
        int[] heights = {70, 70, 71, 71, 70, 70, 69, 70, 70, 70};
        RoadBumpFlattener.flatten(heights, new boolean[heights.length], 4);
        assertArrayEquals(new int[]{70, 70, 70, 70, 70, 70, 70, 70, 70, 70}, heights);
    }

    @Test
    void keepsASteadyClimb() {
        int[] heights = {70, 70, 71, 71, 72, 72, 73, 73, 74, 74};
        int[] before = heights.clone();
        RoadBumpFlattener.flatten(heights, new boolean[heights.length], 4);
        assertArrayEquals(before, heights);
    }

    @Test
    void keepsLongHillsAndDeepDips() {
        int[] longHill = {70, 71, 71, 71, 71, 71, 71, 70};
        int[] before = longHill.clone();
        RoadBumpFlattener.flatten(longHill, new boolean[longHill.length], 4);
        assertArrayEquals(before, longHill);

        int[] deep = {70, 70, 67, 67, 70, 70};
        int[] deepBefore = deep.clone();
        RoadBumpFlattener.flatten(deep, new boolean[deep.length], 4);
        assertArrayEquals(deepBefore, deep);
    }

    @Test
    void neverTouchesFixedSegments() {
        int[] heights = {70, 71, 71, 70};
        boolean[] fixed = {false, true, false, false};
        RoadBumpFlattener.flatten(heights, fixed, 4);
        assertArrayEquals(new int[]{70, 71, 71, 70}, heights);
    }
}
