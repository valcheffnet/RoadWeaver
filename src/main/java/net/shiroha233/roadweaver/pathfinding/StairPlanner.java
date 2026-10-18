/* Finds steep stretches of a road and turns them into flights of steps with landings. */
package net.shiroha233.roadweaver.pathfinding;

import java.util.ArrayList;
import java.util.List;

/**
 * A road keeps its slope limit (one block per two segments by default) and shapes the terrain to fit, which on a steep
 * hillside means deep cuts or raised walls of earth. Where the ground itself climbs faster than that, a stair span
 * follows it instead: one block per segment in flights of up to {@code flightSteps}, with flat landings between them.
 */
public final class StairPlanner {
    /** A stair span over segments {@code start..end} (inclusive) and the height of every segment in it. */
    public record Stair(int start, int end, int[] heights) {
        public int height(int segment) {
            return heights[segment - start];
        }
    }

    public record Settings(int minRise, int maxRise, int flightSteps, int landingLength, int maxExtension) {
        public static final Settings DEFAULT = new Settings(4, 48, 8, 2, 12);
    }

    private StairPlanner() {}

    /**
     * @param ground  ground height under each segment centre
     * @param blocked segments that cannot be stairs (bridges, water)
     */
    public static List<Stair> plan(int[] ground, boolean[] blocked, Settings settings) {
        int n = ground.length;
        List<Stair> stairs = new ArrayList<>();
        if (n < 6) return stairs;
        int[] g = smooth(ground);

        int i = 0;
        while (i + 2 < n) {
            int sign = Integer.signum(g[i + 2] - g[i]);
            if (sign == 0 || Math.abs(g[i + 2] - g[i]) < 2 || blocked(blocked, i, i + 2)) {
                i++;
                continue;
            }
            // Grow while the ground keeps going the same way faster than the slope limit (two short pauses allowed).
            int start = i;
            int end = i + 2;
            int pause = 0;
            while (end + 1 < n && !blocked[end + 1]) {
                int next = end + 1;
                int lookAhead = Math.min(n - 1, next + 1);
                boolean steep = Integer.signum(g[lookAhead] - g[end]) == sign && Math.abs(g[lookAhead] - g[end]) >= 1;
                if (steep) {
                    pause = 0;
                } else if (++pause > 2) {
                    break;
                }
                end = next;
            }
            end -= pause;
            Stair stair = fit(g, blocked, start, end, sign, settings);
            if (stair != null) {
                stairs.add(stair);
                i = stair.end() + 1;
            } else {
                i = end + 1;
            }
        }
        return stairs;
    }

    /** Builds the step profile, widening the span onto flatter ground when the climb needs more segments. */
    private static Stair fit(int[] g, boolean[] blocked, int start, int end, int sign, Settings settings) {
        int n = g.length;
        for (int widen = 0; widen <= settings.maxExtension(); widen++) {
            int rise = Math.abs(g[end] - g[start]);
            if (rise < settings.minRise()) return null;
            if (rise > settings.maxRise()) return null;
            int flights = (rise + settings.flightSteps() - 1) / settings.flightSteps();
            int needed = rise + (flights - 1) * settings.landingLength();
            int length = end - start;
            if (length >= needed) return profile(g[start], g[end], start, end, sign, flights, settings);
            // Too short for one block per segment: take one more segment on the side that adds less climb.
            boolean canBefore = start > 0 && !blocked[start - 1];
            boolean canAfter = end + 1 < n && !blocked[end + 1];
            if (!canBefore && !canAfter) return null;
            int addBefore = canBefore ? Math.abs(g[end] - g[start - 1]) : Integer.MAX_VALUE;
            int addAfter = canAfter ? Math.abs(g[end + 1] - g[start]) : Integer.MAX_VALUE;
            if (addBefore <= addAfter) start--;
            else end++;
        }
        return null;
    }

    private static Stair profile(int from, int to, int start, int end, int sign, int flights, Settings settings) {
        int length = end - start;
        int rise = Math.abs(to - from);
        int landings = flights - 1;
        int flat = length - rise - landings * settings.landingLength();
        int leadIn = flat / 2;
        int[] heights = new int[end - start + 1];
        int h = from;
        int k = 0;
        heights[k++] = h;
        for (int f = 0; f < leadIn; f++) heights[k++] = h;
        int climbed = 0;
        for (int flight = 0; flight < flights; flight++) {
            // Spread the steps evenly over the flights.
            int steps = (rise * (flight + 1)) / flights - climbed;
            for (int s = 0; s < steps; s++) {
                h += sign;
                heights[k++] = h;
            }
            climbed += steps;
            if (flight < landings) {
                for (int l = 0; l < settings.landingLength(); l++) heights[k++] = h;
            }
        }
        while (k < heights.length) heights[k++] = h;
        return new Stair(start, end, heights);
    }

    /**
     * The steps follow the ground, so the top step can sit a block above the road that leads on from it (or the bottom
     * step a block below), which reads as a hump or a dip right at the end of the stairs. Clips the stair to the
     * highest and lowest level the road reaches within {@code window} segments on either side, turning the extra step
     * into a landing.
     */
    public static void fitToRoad(int[] heights, int start, int end, int window) {
        int n = heights.length;
        if (start <= 0 || end >= n - 1) return;
        boolean up = heights[end] >= heights[start];
        int lowSide = up ? -1 : 1;
        // Road level next to the low end: the highest it gets nearby; next to the high end: the lowest.
        int floor = extreme(heights, up ? start : end, lowSide, window, true);
        int ceiling = extreme(heights, up ? end : start, -lowSide, window, false);
        if (floor > ceiling) return;
        for (int i = start; i <= end; i++) heights[i] = Math.max(floor, Math.min(ceiling, heights[i]));
    }

    private static int extreme(int[] heights, int from, int dir, int window, boolean max) {
        int best = max ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int k = 1; k <= window; k++) {
            int i = from + dir * k;
            if (i < 0 || i >= heights.length) break;
            best = max ? Math.max(best, heights[i]) : Math.min(best, heights[i]);
        }
        return best;
    }

    private static int[] smooth(int[] ground) {
        int n = ground.length;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            int sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - 1); j <= Math.min(n - 1, i + 1); j++) {
                sum += ground[j];
                count++;
            }
            out[i] = Math.round(sum / (float) count);
        }
        return out;
    }

    private static boolean blocked(boolean[] blocked, int from, int to) {
        for (int i = from; i <= to; i++) {
            if (blocked[i]) return true;
        }
        return false;
    }
}
