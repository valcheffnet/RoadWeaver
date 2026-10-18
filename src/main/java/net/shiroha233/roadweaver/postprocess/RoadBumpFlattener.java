/* Levels short humps and dips out of a road's target heights. */
package net.shiroha233.roadweaver.postprocess;

/**
 * On rough ground the averaged road heights still go up a block and down again every few blocks, which reads as a
 * bumpy road. A stretch of at most {@code maxLength} segments that lies one or two blocks above (or below) the road on
 * both sides is set to the level of the nearer side, so the road stays flat and the terrain is shaped to it instead.
 * Steady climbs are left alone, and so are fixed segments (bridges, stairs).
 */
public final class RoadBumpFlattener {
    private static final int MAX_DEPTH = 2;

    private RoadBumpFlattener() {}

    public static void flatten(int[] heights, boolean[] fixed, int maxLength) {
        int n = heights.length;
        boolean changed = true;
        for (int pass = 0; changed && pass < 16; pass++) {
            changed = false;
            int start = 0;
            while (start < n) {
                int end = start;
                while (end + 1 < n && heights[end + 1] == heights[start]) end++;
                if (start > 0 && end < n - 1 && end - start + 1 <= maxLength && !anyFixed(fixed, start, end)) {
                    int level = heights[start];
                    int before = heights[start - 1];
                    int after = heights[end + 1];
                    int target;
                    if (before < level && after < level) {
                        target = Math.max(before, after);
                    } else if (before > level && after > level) {
                        target = Math.min(before, after);
                    } else {
                        target = level;
                    }
                    if (target != level && Math.abs(level - target) <= MAX_DEPTH) {
                        for (int i = start; i <= end; i++) heights[i] = target;
                        changed = true;
                    }
                }
                start = end + 1;
            }
        }
    }

    private static boolean anyFixed(boolean[] fixed, int from, int to) {
        if (fixed == null) return false;
        for (int i = from; i <= to; i++) {
            if (fixed[i]) return true;
        }
        return false;
    }
}
