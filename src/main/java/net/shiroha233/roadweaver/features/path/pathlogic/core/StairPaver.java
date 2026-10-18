/* Builds the steps, supports, side walls and landing lanterns of a stair span. */
package net.shiroha233.roadweaver.features.path.pathlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.features.path.decoration.system.AboveColumnClearer;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.RoadHeightInterpolator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every column of a stair span is paved in one material: a solid block under each tread, and a stair block facing
 * uphill wherever the next column up is higher, so landings and the gaps between steps are not left as dirt, gravel or
 * slabs. Each column is paved once, by the first segment that reaches it. Air under the surface is filled so no step
 * floats,
 * a low wall runs along the edges where the ground beside the steps drops away, and each landing gets a lantern.
 * Paved roads use the stairs and walls of their own material (stone brick, mud brick, wood for plank roads); dirt and
 * gravel paths get cobblestone steps and walls.
 */
public final class StairPaver {
    private static final int MAX_SUPPORT = 6;
    private static final int MAX_WALL_DROP = 4;

    private StairPaver() {}

    /** Stair, wall and fill blocks for one road. */
    public record Style(BlockState stairs, BlockState wall, BlockState fill) {}

    /** A paved stair column: its tread height (the block above the surface), the climb direction and the style. */
    public record Column(int y, Direction uphill, Style style) {}

    public static void paveStairSegment(WorldGenLevel world,
                                        RoadSegmentPlacement seg,
                                        int index,
                                        List<BlockPos> centers,
                                        int[] targetY,
                                        Direction uphill,
                                        Set<Long> roadColumns,
                                        Map<Long, Column> paved,
                                        boolean landing,
                                        Style style,
                                        ModConfig cfg) {
        List<BlockPos> positions = seg.positions();
        if (positions.isEmpty()) return;
        int[] heights = RoadHeightInterpolator.batchInterpolate(positions, index, centers, targetY);
        Direction left = uphill.getCounterClockWise();
        Direction right = uphill.getClockWise();
        BlockPos lanternWall = null;

        for (int i = 0; i < positions.size(); i++) {
            BlockPos column = positions.get(i);
            int h = heights[i];
            BlockPos step = new BlockPos(column.getX(), h, column.getZ());
            if (StructureAvoidanceService.shouldAvoid(world, step)) continue;
            if (paved.putIfAbsent(columnKey(column.getX(), column.getZ()), new Column(h, uphill, style)) != null) continue;

            world.setBlock(step.below(), style.fill(), 3);
            AboveColumnClearer.clearAboveColumn(world, step, cfg);
            fillBelow(world, step.below(2), style.fill(), MAX_SUPPORT);

            for (Direction side : new Direction[]{left, right}) {
                BlockPos outside = step.relative(side);
                if (roadColumns.contains(columnKey(outside.getX(), outside.getZ()))) continue;
                BlockPos wall = placeWall(world, outside, h, style);
                if (wall != null && landing && lanternWall == null) lanternWall = wall;
            }
        }

        if (lanternWall != null && world.getBlockState(lanternWall.above()).isAir()) {
            world.setBlock(lanternWall.above(), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 3);
        }
    }

    /**
     * Puts a stair block on every road column that is exactly one block below a neighbouring road column, once all
     * stair columns of the chunk are paved, so every rise along the stairs can be walked up without jumping. The
     * actual blocks decide, not the planned heights: columns are paved by whichever segment reaches them first, and
     * the road may continue in a chunk paved earlier.
     */
    public static void placeSteps(WorldGenLevel world, Map<Long, Column> paved, Set<Long> stairColumns,
                                  Set<Long> roadColumns) {
        List<BlockPos> placed = new ArrayList<>();
        for (Map.Entry<Long, Column> entry : paved.entrySet()) {
            Column col = entry.getValue();
            BlockPos tread = posOf(entry.getKey(), col.y());
            if (!world.getBlockState(tread).isAir()) continue;
            Direction facing = higherNeighbour(world, tread, col.uphill(), paved, roadColumns);
            if (facing != null) {
                placeStep(world, tread, col.style(), facing);
                placed.add(tread);
            }
            // A stair column paved in another chunk can be the lower one.
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos lower = tread.relative(dir).below();
                long key = columnKey(lower.getX(), lower.getZ());
                if (paved.containsKey(key) || !stairColumns.contains(key)) continue;
                if (!world.getBlockState(lower).isAir() || !isTread(world, lower)) continue;
                placeStep(world, lower, col.style(), dir.getOpposite());
                placed.add(lower);
            }
        }
        // Let neighbouring steps form inner and outer corners, as they would when placed by hand.
        for (BlockPos pos : placed) reshapeStairs(world, pos);
    }

    /** The side on which the next road column is one block higher, preferring the climb direction. */
    private static Direction higherNeighbour(WorldGenLevel world, BlockPos tread, Direction uphill,
                                             Map<Long, Column> paved, Set<Long> roadColumns) {
        Direction[] order = {uphill, uphill.getCounterClockWise(), uphill.getClockWise(), uphill.getOpposite()};
        for (Direction dir : order) {
            BlockPos next = tread.relative(dir);
            long key = columnKey(next.getX(), next.getZ());
            Column other = paved.get(key);
            if (other != null) {
                if (other.y() == tread.getY() + 1) return dir;
                continue;
            }
            if (roadColumns.contains(key) && isTread(world, next.above())) return dir;
        }
        return null;
    }

    /** True when {@code pos} is open with a solid top under it, i.e. a walking surface at that height. */
    private static boolean isTread(WorldGenLevel world, BlockPos pos) {
        BlockState below = world.getBlockState(pos.below());
        BlockState at = world.getBlockState(pos);
        return (at.isAir() || at.canBeReplaced()) && at.getFluidState().isEmpty()
                && below.isFaceSturdy(world, pos.below(), Direction.UP);
    }

    private static void placeStep(WorldGenLevel world, BlockPos pos, Style style, Direction facing) {
        world.setBlock(pos, style.stairs().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM), 3);
    }

    private static BlockPos posOf(long key, int y) {
        return new BlockPos((int) (key >> 32), y, (int) key);
    }

    /** A wall at road level beside the step, only where the ground there lies below the road. */
    private static BlockPos placeWall(WorldGenLevel world, BlockPos outside, int roadY, Style style) {
        int groundTop = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, outside.getX(), outside.getZ());
        if (groundTop >= roadY) return null;
        if (roadY - groundTop > MAX_WALL_DROP) return null;
        BlockPos wallPos = new BlockPos(outside.getX(), roadY, outside.getZ());
        if (!world.getBlockState(wallPos).canBeReplaced()) return null;
        for (int y = groundTop; y < roadY; y++) {
            BlockPos support = new BlockPos(outside.getX(), y, outside.getZ());
            if (world.getBlockState(support).canBeReplaced()) world.setBlock(support, style.fill(), 3);
        }
        setConnected(world, wallPos, style.wall());
        return wallPos;
    }

    private static void fillBelow(WorldGenLevel world, BlockPos top, BlockState fill, int maxDepth) {
        for (int d = 0; d < maxDepth; d++) {
            BlockPos pos = top.below(d);
            if (!world.getBlockState(pos).canBeReplaced()) return;
            world.setBlock(pos, fill, 3);
        }
    }

    /** Walls and fences only join their neighbours when their shape is updated, which worldgen does not do. */
    private static void setConnected(WorldGenLevel world, BlockPos pos, BlockState state) {
        world.setBlock(pos, Block.updateFromNeighbourShapes(state, world, pos), 3);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(dir);
            BlockState other = world.getBlockState(neighbour);
            if (other.getBlock() instanceof WallBlock || other.getBlock() instanceof FenceBlock) {
                world.setBlock(neighbour, Block.updateFromNeighbourShapes(other, world, neighbour), 3);
            }
        }
    }

    private static void reshapeStairs(WorldGenLevel world, BlockPos pos) {
        for (BlockPos p : new BlockPos[]{pos, pos.north(), pos.south(), pos.east(), pos.west()}) {
            BlockState state = world.getBlockState(p);
            if (state.getBlock() instanceof StairBlock) {
                world.setBlock(p, Block.updateFromNeighbourShapes(state, world, p), 3);
            }
        }
    }

    public static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** All block columns of the segments around {@code index}, so a wall is never put on the road itself. */
    public static Set<Long> roadColumns(List<RoadSegmentPlacement> segments, int index, int reach) {
        Set<Long> out = new HashSet<>();
        for (int i = Math.max(0, index - reach); i <= Math.min(segments.size() - 1, index + reach); i++) {
            for (BlockPos p : segments.get(i).positions()) out.add(columnKey(p.getX(), p.getZ()));
        }
        return out;
    }

    /** Stairs and wall in the road's own material; dirt and gravel paths use cobblestone. */
    public static Style styleFor(WorldGenLevel world, BlockPos pos, int roadType, List<BlockState> materials) {
        if (roadType == 0 && materials != null) {
            for (BlockState material : materials) {
                String path = BuiltInRegistries.BLOCK.getKey(material.getBlock()).getPath();
                Block stairs = variant(path, "stairs");
                if (stairs == null) continue;
                Block wall = variant(path, "wall");
                if (wall == null) wall = Blocks.COBBLESTONE_WALL;
                return new Style(stairs.defaultBlockState(), wall.defaultBlockState(), material);
            }
        }
        // Dirt and gravel paths, and materials without a stair variant: rough cobblestone steps.
        return new Style(Blocks.COBBLESTONE_STAIRS.defaultBlockState(), Blocks.COBBLESTONE_WALL.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState());
    }

    /** {@code stone_bricks} -> {@code stone_brick_stairs}, {@code oak_planks} -> {@code oak_stairs}, and so on. */
    static Block variant(String path, String kind) {
        String stem;
        if (path.endsWith("_planks")) {
            stem = path.substring(0, path.length() - "_planks".length());
            if (kind.equals("wall")) kind = "fence";
        } else if (path.endsWith("_bricks") || path.endsWith("_tiles")) {
            stem = path.substring(0, path.length() - 1);
        } else if (path.equals("packed_mud")) {
            stem = "mud_brick";
        } else if (path.startsWith("polished_") && kind.equals("wall") && !path.equals("polished_blackstone")
                && !path.equals("polished_deepslate") && !path.equals("polished_tuff")) {
            stem = path.substring("polished_".length());
        } else {
            stem = path;
        }
        Identifier id = Identifier.withDefaultNamespace(stem + "_" + kind);
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : null;
    }
}
