/* Grid search for the short road that joins a trimmed road end to the nearest street of a village. */
package net.shiroha233.roadweaver.structures.village;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.IntBinaryOperator;
import java.util.function.LongPredicate;

/**
 * A* over block columns from a start column to the closest column inside a street rectangle.
 * Columns inside a blocked rectangle (buildings, grown by a margin) cannot be entered; the start column may be blocked
 * only when the road already ended there, in which case the search just leaves it.
 */
public final class VillageConnectorPathfinder {
    /** Inclusive rectangle in block columns. */
    public record Rect(int minX, int minZ, int maxX, int maxZ) {
        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public Rect grow(int by) {
            return new Rect(minX - by, minZ - by, maxX + by, maxZ + by);
        }

        double distance(int x, int z) {
            int dx = Math.max(0, Math.max(minX - x, x - maxX));
            int dz = Math.max(0, Math.max(minZ - z, z - maxZ));
            return Math.sqrt((double) dx * dx + (double) dz * dz);
        }
    }

    public record Column(int x, int z) {}

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final double DIAGONAL = Math.sqrt(2.0);
    private static final double TURN_COST = 0.35;
    private static final double CLIMB_COST = 1.5;
    private static final int MAX_STEP_UP = 2;

    private VillageConnectorPathfinder() {}

    /**
     * @param heights   surface height of a column, used to keep the connector off cliffs; may be null for flat ground
     * @param searchPad how far the search may leave the box spanned by the start and the streets
     * @return the columns from start to the first street column (the street column itself is not included),
     *         or an empty list when no street can be reached
     */
    public static List<Column> find(Column start, List<Rect> streets, List<Rect> blocked,
                                    IntBinaryOperator heights, int searchPad, int maxNodes) {
        if (streets == null) return List.of();
        return find(start, key -> isStreet(streets, x(key), z(key)), streets, blocked, heights, searchPad, maxNodes);
    }

    /**
     * @param isTarget  street column test on {@link #key(int, int)}
     * @param streets   rectangles that contain every target column; they bound the search and guide it
     */
    public static List<Column> find(Column start, LongPredicate isTarget, List<Rect> streets, List<Rect> blocked,
                                    IntBinaryOperator heights, int searchPad, int maxNodes) {
        if (start == null || streets == null || streets.isEmpty()) return List.of();
        if (isTarget.test(key(start.x(), start.z()))) return List.of(start);

        int minX = start.x(), minZ = start.z(), maxX = start.x(), maxZ = start.z();
        for (Rect street : streets) {
            minX = Math.min(minX, street.minX());
            minZ = Math.min(minZ, street.minZ());
            maxX = Math.max(maxX, street.maxX());
            maxZ = Math.max(maxZ, street.maxZ());
        }
        minX -= searchPad;
        minZ -= searchPad;
        maxX += searchPad;
        maxZ += searchPad;
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        int cells = width * depth;

        double[] cost = new double[cells];
        Arrays.fill(cost, Double.POSITIVE_INFINITY);
        int[] parent = new int[cells];
        Arrays.fill(parent, -1);
        int[] arrivalDir = new int[cells];
        boolean[] closed = new boolean[cells];
        int[] heightCache = new int[cells];
        Arrays.fill(heightCache, Integer.MIN_VALUE);

        int startIndex = (start.x() - minX) * depth + (start.z() - minZ);
        cost[startIndex] = 0;
        arrivalDir[startIndex] = -1;
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        open.add(new double[]{heuristic(start.x(), start.z(), streets), startIndex});

        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            int index = (int) open.poll()[1];
            if (closed[index]) continue;
            closed[index] = true;
            expanded++;
            int x = minX + index / depth;
            int z = minZ + index % depth;
            int y = height(heights, heightCache, index, x, z);

            for (int dir = 0; dir < STEPS.length; dir++) {
                int nx = x + STEPS[dir][0];
                int nz = z + STEPS[dir][1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                int next = (nx - minX) * depth + (nz - minZ);
                if (closed[next]) continue;
                // Streets run right along the houses, so a street column ends the search even inside the margin.
                if (dir < 4 && isTarget.test(key(nx, nz))) {
                    return trace(parent, index, minX, minZ, depth);
                }
                if (isInside(blocked, nx, nz)) continue;
                // Diagonal steps may not cut the corner of a building.
                if (dir >= 4 && (isInside(blocked, x + STEPS[dir][0], z) || isInside(blocked, x, z + STEPS[dir][1]))) continue;
                if (isTarget.test(key(nx, nz))) {
                    return trace(parent, index, minX, minZ, depth);
                }

                int ny = height(heights, heightCache, next, nx, nz);
                int climb = Math.abs(ny - y);
                if (climb > MAX_STEP_UP) continue;
                double step = dir >= 4 ? DIAGONAL : 1.0;
                double turn = arrivalDir[index] >= 0 && arrivalDir[index] != dir ? TURN_COST : 0.0;
                double candidate = cost[index] + step + turn + climb * CLIMB_COST;
                if (candidate < cost[next]) {
                    cost[next] = candidate;
                    parent[next] = index;
                    arrivalDir[next] = dir;
                    open.add(new double[]{candidate + heuristic(nx, nz, streets), next});
                }
            }
        }
        return List.of();
    }

    private static List<Column> trace(int[] parent, int last, int minX, int minZ, int depth) {
        List<Column> path = new ArrayList<>();
        for (int i = last; i >= 0; i = parent[i]) {
            path.add(new Column(minX + i / depth, minZ + i % depth));
        }
        Collections.reverse(path);
        return path;
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int z(long key) {
        return (int) key;
    }

    private static int height(IntBinaryOperator heights, int[] cache, int index, int x, int z) {
        if (heights == null) return 0;
        if (cache[index] == Integer.MIN_VALUE) cache[index] = heights.applyAsInt(x, z);
        return cache[index];
    }

    private static double heuristic(int x, int z, List<Rect> streets) {
        double best = Double.POSITIVE_INFINITY;
        for (Rect street : streets) best = Math.min(best, street.distance(x, z));
        return best;
    }

    private static boolean isStreet(List<Rect> streets, int x, int z) {
        for (Rect street : streets) {
            if (street.contains(x, z)) return true;
        }
        return false;
    }

    static boolean isInside(List<Rect> rects, int x, int z) {
        for (Rect rect : rects) {
            if (rect.contains(x, z)) return true;
        }
        return false;
    }
}
