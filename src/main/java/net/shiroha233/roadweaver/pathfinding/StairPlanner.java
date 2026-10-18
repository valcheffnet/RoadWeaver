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
        // Blocked segments and those already on a stair: a span never widens into another one.
        boolean[] taken = blocked.clone();

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
            Stair stair = null;
            // A climb that goes on in the same direction right after a stair continues that stair, with a landing
            // between the flights, instead of starting a second one at its own height.
            Stair last = stairs.isEmpty() ? null : stairs.getLast();
            if (last != null && direction(last) == sign && start - last.end() - 1 <= settings.landingLength()
                    && !blocked(blocked, last.end() + 1, start - 1)) {
                stair = fit(g, taken, last.start(), end, sign, settings);
                if (stair != null) stairs.removeLast();
            }
            if (stair == null) stair = fit(g, taken, start, end, sign, settings);
            if (stair != null) {
                stairs.add(stair);
                for (int k = stair.start(); k <= stair.end(); k++) taken[k] = true;
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

    private static int direction(Stair stair) {
        int[] heights = stair.heights();
        return Integer.signum(heights[heights.length - 1] - heights[0]);
    }

    /**
     * The steps follow the ground, so the top step can sit a block above the road that leads on from it (or the bottom
     * step a block below), which reads as a hump or a dip right at the end of the stairs. Clips each stair to the
     * highest and lowest level the road reaches within {@code window} segments on either side, turning the extra step
     * into a landing. The road ends where another stair begins; stairs that meet end to end are joined instead.
     *
     * @param stairs the stairs in {@code heights}, in road order and not overlapping
     */
    public static void fitToRoad(int[] heights, List<Stair> stairs, int window) {
        boolean[] onStair = new boolean[heights.length];
        for (Stair stair : stairs) {
            for (int i = stair.start(); i <= stair.end(); i++) onStair[i] = true;
        }
        for (Stair stair : stairs) fitToRoad(heights, onStair, stair.start(), stair.end(), window);
        join(heights, stairs);
    }

    private static void fitToRoad(int[] heights, boolean[] onStair, int start, int end, int window) {
        int n = heights.length;
        if (start <= 0 || end >= n - 1) return;
        boolean up = heights[end] >= heights[start];
        int lowSide = up ? -1 : 1;
        // Road level next to the low end: the highest it gets nearby; next to the high end: the lowest.
        int floor = extreme(heights, onStair, up ? start : end, lowSide, window, true);
        int ceiling = extreme(heights, onStair, up ? end : start, -lowSide, window, false);
        if (floor > ceiling) return;
        for (int i = start; i <= end; i++) heights[i] = Math.max(floor, Math.min(ceiling, heights[i]));
    }

    private static int extreme(int[] heights, boolean[] onStair, int from, int dir, int window, boolean max) {
        int best = max ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int k = 1; k <= window; k++) {
            int i = from + dir * k;
            if (i < 0 || i >= heights.length || onStair[i]) break;
            best = max ? Math.max(best, heights[i]) : Math.min(best, heights[i]);
        }
        return best;
    }

    /**
     * Two stairs that meet end to end (in a valley or on a ridge) each follow the ground, so the last step of one and
     * the first of the next can be several blocks apart. At each such joint, either brings the higher side down or the
     * lower side up, one block per segment away from the joint, across the whole run of touching stairs. Prefers the
     * side whose far end, where the run meets the road, stays where it was, then the smaller change.
     */
    private static void join(int[] heights, List<Stair> stairs) {
        int first = 0;
        while (first < stairs.size()) {
            int last = first;
            while (last + 1 < stairs.size() && stairs.get(last + 1).start() == stairs.get(last).end() + 1) last++;
            int runStart = stairs.get(first).start();
            int runEnd = stairs.get(last).end();
            for (int k = first + 1; k <= last; k++) {
                int joint = stairs.get(k).start();
                int a = heights[joint - 1];
                int b = heights[joint];
                if (Math.abs(b - a) <= 1) continue;
                // Either the side before the joint ends a block from b, or the side after it starts a block from a.
                int[] before = heights.clone();
                int[] after = heights.clone();
                slope(before, runStart, joint - 1, joint - 1, b > a ? b - 1 : b + 1, b < a);
                slope(after, joint, runEnd, joint, b > a ? a + 1 : a - 1, b > a);
                int[] chosen = cost(heights, before, runStart) <= cost(heights, after, runEnd) ? before : after;
                System.arraycopy(chosen, 0, heights, 0, heights.length);
            }
            first = last + 1;
        }
    }

    /**
     * Lowers {@code from..to} to at most {@code level} at {@code origin} plus a block per segment away from it, or
     * raises it to at least {@code level} minus a block per segment.
     */
    private static void slope(int[] heights, int from, int to, int origin, int level, boolean down) {
        for (int i = from; i <= to; i++) {
            int distance = Math.abs(i - origin);
            heights[i] = down ? Math.min(heights[i], level + distance) : Math.max(heights[i], level - distance);
        }
    }

    private static long cost(int[] original, int[] changed, int farEnd) {
        long cost = original[farEnd] != changed[farEnd] ? Integer.MAX_VALUE : 0;
        for (int i = 0; i < original.length; i++) cost += Math.abs(changed[i] - original[i]);
        return cost;
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
