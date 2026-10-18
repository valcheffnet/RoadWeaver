/* Picks the half-step slabs of a natural road from what the road is made of. */
package net.shiroha233.roadweaver.features.path.pathlogic.core;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Half steps should look like part of the road under them: a dirt or mud path gets mud brick slabs, a cobblestone or
 * stone path gets cobblestone or stone brick slabs, a sand path sandstone slabs. A road whose surface does not fall in
 * one of these groups keeps the slabs of its preset, and a preset without slabs keeps none.
 */
public final class RoadSlabs {
    private enum Kind { EARTH, COBBLE, MOSSY, STONE, SAND, RED_SAND }

    private RoadSlabs() {}

    public static List<BlockState> matching(List<BlockState> surface, List<BlockState> presetSlabs) {
        if (presetSlabs == null || presetSlabs.isEmpty() || surface == null || surface.isEmpty()) return presetSlabs;
        int[] counts = new int[Kind.values().length];
        for (BlockState state : surface) {
            Kind kind = kindOf(state.getBlock());
            if (kind != null) counts[kind.ordinal()]++;
        }
        // Mossy and plain cobblestone count together; the mossy slab is used only when moss is the larger part.
        int cobble = counts[Kind.COBBLE.ordinal()] + counts[Kind.MOSSY.ordinal()];
        Kind best = null;
        int bestCount = 0;
        for (Kind kind : Kind.values()) {
            int count = kind == Kind.COBBLE ? cobble : kind == Kind.MOSSY ? 0 : counts[kind.ordinal()];
            if (count > bestCount) {
                best = kind;
                bestCount = count;
            }
        }
        if (best == null) return presetSlabs;
        return switch (best) {
            case EARTH -> List.of(Blocks.MUD_BRICK_SLAB.defaultBlockState());
            case COBBLE -> counts[Kind.MOSSY.ordinal()] > counts[Kind.COBBLE.ordinal()]
                    ? List.of(Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState(), Blocks.COBBLESTONE_SLAB.defaultBlockState())
                    : List.of(Blocks.COBBLESTONE_SLAB.defaultBlockState());
            case STONE -> List.of(Blocks.STONE_BRICK_SLAB.defaultBlockState());
            case SAND -> List.of(Blocks.SANDSTONE_SLAB.defaultBlockState());
            case RED_SAND -> List.of(Blocks.RED_SANDSTONE_SLAB.defaultBlockState());
            default -> presetSlabs;
        };
    }

    /** Whether most of a road's surface blocks are stone or cobblestone (gravel does not count). */
    public static boolean isStoneRoad(List<BlockState> surface) {
        if (surface == null || surface.isEmpty()) return false;
        int stone = 0;
        for (BlockState state : surface) {
            Kind kind = kindOf(state.getBlock());
            if ((kind == Kind.COBBLE || kind == Kind.MOSSY || kind == Kind.STONE) && state.getBlock() != Blocks.GRAVEL) stone++;
        }
        return stone * 2 > surface.size();
    }

    private static Kind kindOf(Block block) {
        if (block == Blocks.DIRT_PATH || block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT
                || block == Blocks.MUD || block == Blocks.PACKED_MUD || block == Blocks.MUD_BRICKS
                || block == Blocks.PODZOL || block == Blocks.MYCELIUM || block == Blocks.GRASS_BLOCK) {
            return Kind.EARTH;
        }
        if (block == Blocks.COBBLESTONE || block == Blocks.GRAVEL) return Kind.COBBLE;
        if (block == Blocks.MOSSY_COBBLESTONE) return Kind.MOSSY;
        if (block == Blocks.STONE || block == Blocks.STONE_BRICKS || block == Blocks.MOSSY_STONE_BRICKS
                || block == Blocks.CRACKED_STONE_BRICKS || block == Blocks.SMOOTH_STONE || block == Blocks.ANDESITE
                || block == Blocks.POLISHED_ANDESITE || block == Blocks.TUFF) {
            return Kind.STONE;
        }
        if (block == Blocks.SAND || block == Blocks.SANDSTONE || block == Blocks.SMOOTH_SANDSTONE) return Kind.SAND;
        if (block == Blocks.RED_SAND || block == Blocks.RED_SANDSTONE || block == Blocks.SMOOTH_RED_SANDSTONE) {
            return Kind.RED_SAND;
        }
        return null;
    }
}
