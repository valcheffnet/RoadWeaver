package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * 缓存预热器
 */
public final class TerrainCachePrewarmer {
    private TerrainCachePrewarmer() {}

    private static final int COARSE_STEP = 64;

    public static void prewarmAlongRoute(BlockPos startGround, BlockPos endGround,
                                         ServerLevel level, int maxSteps,
                                         TerrainSamplingCache cache) {
        if (cache == null || startGround == null || endGround == null || maxSteps <= 0) return;
        calculateCoarseSkeleton(startGround, endGround, level, maxSteps, cache);
    }

    private static void calculateCoarseSkeleton(BlockPos startGround, BlockPos endGround,
                                                ServerLevel level, int maxSteps,
                                                TerrainSamplingCache cache) {
        int d = COARSE_STEP;
        BlockPos start = new BlockPos(snapToGrid(startGround.getX(), d), 0, snapToGrid(startGround.getZ(), d));
        BlockPos end = new BlockPos(snapToGrid(endGround.getX(), d), 0, snapToGrid(endGround.getZ(), d));
        BlockPos startG = new BlockPos(start.getX(), cache.height(level, start.getX(), start.getZ()), start.getZ());
        BlockPos endG = new BlockPos(end.getX(), cache.height(level, end.getX(), end.getZ()), end.getZ());

        PriorityQueue<CoarseNode> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Long, CoarseNode> best = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        CoarseNode startNode = new CoarseNode(startG, null, 0.0, heuristicEuclid(startG, endG));
        open.add(startNode);
        best.put(posKey2d(startG), startNode);

        int[][] offsets = {
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
        };

        int stepsBudget = Math.max(1, maxSteps);
        while (!open.isEmpty() && stepsBudget-- > 0) {
            if (Thread.currentThread().isInterrupted()) return;
            CoarseNode cur = open.poll();
            if (cur == null) break;
            long ck = posKey2d(cur.pos);
            if (!closed.add(ck)) continue;

            if (manhattan2d(cur.pos, endG) < d * 2) return;

            for (int[] off : offsets) {
                int nx = cur.pos.getX() + off[0];
                int nz = cur.pos.getZ() + off[1];
                int ny = cache.height(level, nx, nz);
                cache.isColumnWater(level, nx, nz);
                cache.getBiome(level, nx, nz);

                BlockPos np = new BlockPos(nx, ny, nz);
                long nk = posKey2d(np);
                if (closed.contains(nk)) continue;

                double stepCost = (Math.abs(off[0]) + Math.abs(off[1]) == 2 * d) ? 1.41421356237 : 1.0;
                double elevation = Math.abs(ny - cur.pos.getY());
                double g = cur.g + stepCost + elevation * 0.02;
                double f = g + heuristicEuclid(np, endG);

                CoarseNode prevBest = best.get(nk);
                if (prevBest == null || g < prevBest.g) {
                    CoarseNode nxt = new CoarseNode(np, cur, g, f);
                    best.put(nk, nxt);
                    open.add(nxt);
                }
            }
        }
    }

    private static long posKey2d(BlockPos p) {
        return (((long) p.getX()) << 32) ^ (p.getZ() & 0xffffffffL);
    }

    private static int manhattan2d(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private static double heuristicEuclid(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz) / (double) COARSE_STEP;
    }

    static int snapToGrid(int v, int gridSize) {
        return Math.floorDiv(v, gridSize) * gridSize;
    }

    private static final class CoarseNode {
        final BlockPos pos;
        final double g;
        final double f;
        CoarseNode(BlockPos pos, CoarseNode parent, double g, double f) {
            this.pos = pos;
            this.g = g;
            this.f = f;
        }
    }
}
