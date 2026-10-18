/* 文件职责：实现普通道路的世界生成入口。 */
package net.shiroha233.roadweaver.features.path;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.features.path.bridge.BridgeSegment;
import net.shiroha233.roadweaver.features.path.decoration.base.Decoration;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationExecutor;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationPlanner;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeRangeCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeSegmentPlanner;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeSegmentPlannerNew;
import net.shiroha233.roadweaver.features.path.pathlogic.core.SegmentPaver;
import net.shiroha233.roadweaver.features.path.pathlogic.core.StairPaver;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureAvoidanceService;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.BridgeTransitionAdjuster;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.HeightProfileService;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BuoyBuilder;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BuoyMarkerPlanner;
import net.shiroha233.roadweaver.features.path.decoration.system.SkippedBridgeBankSignPlanner;
import net.shiroha233.roadweaver.worldgen.road.RoadChunkPlan;
import net.shiroha233.roadweaver.worldgen.road.RoadChunkSlice;
import net.shiroha233.roadweaver.worldgen.road.RoadWorldgenPlanCache;

import java.util.*;

/**
 * 道路世界生成 Feature
 */
public class PathFeature extends Feature<PathFeatureConfig> {
    public PathFeature(Codec<PathFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<PathFeatureConfig> ctx) {
        WorldGenLevel world = ctx.level();
        RandomSource random = ctx.random();
        BlockPos origin = ctx.origin();
        Level lvl = world.getLevel();
        if (!(lvl instanceof ServerLevel server) || !Level.OVERWORLD.equals(server.dimension()))
            return false;

        ModConfig cfg = ConfigService.get();
        if (!cfg.roadAppearance().roadsEnabled())
            return false;

        ChunkPos currentChunk = ChunkPos.containing(origin);
        RoadChunkPlan plan = RoadWorldgenPlanCache.get(server, currentChunk, cfg);
        if (plan.slices().isEmpty())
            return false;

        int averagingRadius = Math.max(0, cfg.averagingRadius());

        Set<BlockPos> processedMiddle = new HashSet<>();
        Set<Decoration> decorations = new HashSet<>();

        // Stairs are paved first and once per column; other roads that cross or share them leave those columns alone,
        // so their dirt, gravel and slabs do not break up the steps.
        List<RoadChunkSlice> slices = new ArrayList<>(plan.slices());
        StairColumns stairColumns = new StairColumns();
        for (RoadChunkSlice slice : slices) stairColumns.collect(slice);
        slices.sort(Comparator.comparing(slice -> !stairColumns.hasStairs(slice.road())));

        for (RoadChunkSlice slice : slices) {
            processRoadSlice(world, server, currentChunk, slice, processedMiddle, decorations, random, cfg,
                    averagingRadius, stairColumns);
        }
        StairPaver.placeSteps(world, stairColumns.paved(), stairColumns.stairs(), stairColumns.roadColumns());

        dropDecorationsOnRoads(server, currentChunk, decorations, stairColumns, cfg);
        DecorationExecutor.tryPlaceDecorations(decorations);
        return true;
    }

    private static void processRoadSlice(WorldGenLevel world,
            ServerLevel server,
            ChunkPos currentChunk,
            RoadChunkSlice slice,
            Set<BlockPos> processedMiddle,
            Set<Decoration> decorations,
            RandomSource random,
            ModConfig cfg,
            int averagingRadius,
            StairColumns stairColumns) {
        if (!cfg.roadAppearance().roadsEnabled()) {
            return;
        }

        RoadData data = slice.road();
        boolean bridgeEnabled = cfg.bridgeEnabled();
        int roadType = data.roadType();
        if (roadType != 0 && roadType != 1) {
            return;
        }
        
        int roadWidth = Math.max(1, data.width());
        List<BlockState> materials = data.materials();
        List<BlockState> slabMaterials = data.slabMaterials();
        List<RoadSegmentPlacement> segments = data.roadSegmentList();
        // A village connector is short and is paved end to end; ordinary roads keep their unpaved ends.
        boolean connector = data.villageConnector();
        if (segments == null || segments.size() < (connector ? 1 : 5))
            return;

        List<BlockPos> middlePositions = segments.stream().map(RoadSegmentPlacement::middlePos).toList();
        BridgeRangeCalculator.RangeResult res = BridgeRangeCalculator.compute(middlePositions, data.spans(), cfg);
        boolean[] isBridge = res.isBridge();
        List<int[]> bridgeRanges = res.mergedRanges();
        boolean[] skipSegments = res.skipSegments();

        BridgeSegment bridgeSegment = new BridgeSegment(isBridge, segments);

        boolean useBuoysInstead = bridgeEnabled && cfg.bridgeUseBuoysInstead();
        boolean useBuoysWhenSkipped = bridgeEnabled && cfg.bridgeUseBuoysWhenSkipped();

        int intervalBlocks = Math.max(4, cfg.buoyIntervalBlocks());
        boolean[] buoyMarkersForBridge = (useBuoysInstead
                ? BuoyMarkerPlanner.markersForBridgeRanges(middlePositions, bridgeRanges, intervalBlocks)
                : null);
        boolean[] buoyMarkersForSkipped = (useBuoysWhenSkipped
                ? BuoyMarkerPlanner.markersForMask(middlePositions, skipSegments, intervalBlocks)
                : null);

        var targetY = data.targetY();
        boolean slopeLimitEnabled = cfg.slopeLimitEnabled();
        HeightProfileService.HeightProfile hp = HeightProfileService.build(
                world,
                middlePositions,
                currentChunk,
                averagingRadius,
                slopeLimitEnabled,
                cfg.maxSlopeStepPerTwoSegments(),
                targetY);
        boolean usePersisted = hp.usePersisted();
        int[] smoothedYArr = hp.smoothedY();
        int[] baseYArr;
        if (usePersisted && targetY != null && targetY.size() == middlePositions.size()) {
            baseYArr = targetY.stream().mapToInt(Integer::intValue).toArray();
        } else {
            baseYArr = smoothedYArr;
        }

        if (baseYArr != null && bridgeEnabled && !useBuoysInstead && !bridgeRanges.isEmpty()) {
            baseYArr = BridgeTransitionAdjuster.adjust(baseYArr, bridgeRanges, cfg);
        }

        // Stair spans: segment -> +1 / -1 climb direction along the road (0 = not a stair).
        int[] stairSign = stairSigns(data, middlePositions, baseYArr);
        StairPaver.Style stairStyle = null;

        int deckY = server.getSeaLevel() + cfg.bridgeDeckClearance();
        BridgeSegmentPlanner.Context bridgeCtx = BridgeSegmentPlanner.newContext();
        
        for (int localIndex = 0; localIndex < slice.segmentCount(); localIndex++) {
            int i = slice.segmentIndexAt(localIndex);
            if (!pavedSegment(connector, i, segments.size()))
                continue;
            BlockPos middle = middlePositions.get(i);
            int segmentIndex = i - 1;
            if (!contains(currentChunk, middle))
                continue;
            boolean stairHere = stairSign != null && stairSign[i] != 0 && baseYArr != null;
            // A stretch shared with another road's stairs is left to that road.
            if (!stairHere && stairColumns.isStairMiddle(middle))
                continue;
            if (!processedMiddle.add(middle))
                continue;

            BlockPos prev = middlePositions.get(Math.max(0, i - 2));
            BlockPos next = middlePositions.get(Math.min(middlePositions.size() - 1, i + 2));

            int sea = server.getSeaLevel();
            int motion = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.getX(), middle.getZ());
            int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, middle.getX(), middle.getZ());
            int topYCenter = (motion > sea + 2) ? motion : surface;
            BlockPos averaged = new BlockPos(middle.getX(), topYCenter, middle.getZ());
            int baseYForThis = (baseYArr != null ? baseYArr[i] : topYCenter);

            RoadSegmentPlacement seg = segments.get(i);
            if (StructureAvoidanceService.shouldAvoid(world, middle)) {
                continue;
            }

            if (skipSegments != null && i >= 0 && i < skipSegments.length && skipSegments[i]) {
                if (useBuoysWhenSkipped && buoyMarkersForSkipped != null && i < buoyMarkersForSkipped.length
                        && buoyMarkersForSkipped[i]) {
                    BuoyBuilder.placeBuoy(world, middle, server.getSeaLevel(), random, cfg);
                }
                continue;
            }

            if (useBuoysInstead && isBridge != null && i >= 0 && i < isBridge.length && isBridge[i]) {
                if (buoyMarkersForBridge != null && i < buoyMarkersForBridge.length && buoyMarkersForBridge[i]) {
                    BuoyBuilder.placeBuoy(world, middle, server.getSeaLevel(), random, cfg);
                }
                continue;
            }

            if (stairHere) {
                if (stairStyle == null) stairStyle = StairPaver.styleFor(world, middle, roadType, materials);
                Direction travel = travelDirection(middlePositions, i);
                Direction uphill = stairSign[i] > 0 ? travel : travel.getOpposite();
                boolean landing = i > 0 && i + 1 < baseYArr.length && stairSign[i + 1] != 0
                        && baseYArr[i] == baseYArr[i + 1] && baseYArr[i - 1] != baseYArr[i];
                Set<Long> keepClear = StairPaver.roadColumns(segments, i, 6);
                keepClear.addAll(stairColumns.roadColumns());
                StairPaver.paveStairSegment(world, seg, i, middlePositions, baseYArr, uphill, keepClear,
                        stairColumns.paved(), landing, stairStyle, cfg);
                continue;
            }

            if (bridgeEnabled && isBridge[i]) {
                var line = bridgeSegment.getLine(i);
                if (line == null || line.getTotalLength() <= 15) {
                    BridgeSegmentPlanner.processSegment(world, seg, middle, prev, next, roadWidth, baseYForThis, deckY,
                            segmentIndex, random, cfg, bridgeRanges, baseYArr, i, bridgeCtx);
                } else {
                    boolean success = BridgeSegmentPlannerNew.processSegment(world, line, seg, middle, prev);
                    if (!success) {
                        BridgeSegmentPlanner.processSegment(world, seg, middle, prev, next, roadWidth, baseYForThis,
                                deckY,
                                segmentIndex, random, cfg, bridgeRanges, baseYArr, i, bridgeCtx);
                    }
                }
            } else {
                SegmentPaver.paveSegment(world, seg, i, middlePositions, baseYArr, roadType, materials, slabMaterials,
                        random, cfg, stairColumns.stairs());

                if (!connector && cfg.roadAppearance().roadSignsEnabled()) {
                    SkippedBridgeBankSignPlanner.addIfSkippedBridgeBank(
                            world,
                            decorations,
                            averaged,
                            next,
                            prev,
                            roadWidth,
                            skipSegments,
                            i);
                }
            }

            if (!connector && (!isBridge[i] || cfg.bridgeKeepLamps())) {
                DecorationPlanner.addDecoration(
                        world,
                        decorations,
                        averaged,
                        segmentIndex,
                        next,
                        prev,
                        middlePositions,
                        roadWidth,
                        random,
                        cfg,
                        (roadType == 0 ? DecorationPlanner.Mode.ARTIFICIAL : DecorationPlanner.Mode.NATURAL));
            }
        }
    }

    /** Ordinary roads leave their first and last few segments unpaved; a village connector is paved end to end. */
    private static boolean pavedSegment(boolean connector, int i, int count) {
        return connector || (i >= 9 && i <= count - 7);
    }

    /**
     * Paving a road column clears the blocks above it. A lamp or sign post standing on a column that a road (or a
     * stair) from a neighbouring chunk paves later loses its middle and leaves the lantern or hanging sign in the air,
     * so posts keep off every paved road column, including those of the chunk next door.
     */
    private static void dropDecorationsOnRoads(ServerLevel server, ChunkPos currentChunk, Set<Decoration> decorations,
            StairColumns here, ModConfig cfg) {
        Map<Long, Set<Long>> surfaceByChunk = new HashMap<>();
        surfaceByChunk.put(currentChunk.pack(), here.surface());
        decorations.removeIf(decoration -> {
            BlockPos pos = decoration.getPos();
            ChunkPos chunk = ChunkPos.containing(pos);
            Set<Long> surface = surfaceByChunk.computeIfAbsent(chunk.pack(), key -> {
                StairColumns columns = new StairColumns();
                RoadChunkPlan plan = RoadWorldgenPlanCache.get(server, chunk, cfg);
                for (RoadChunkSlice slice : plan.slices()) columns.collect(slice);
                return columns.surface();
            });
            return surface.contains(StairPaver.columnKey(pos.getX(), pos.getZ()));
        });
    }

    private static int[] stairSigns(RoadData data, List<BlockPos> middles, int[] heights) {
        if (data.spans() == null || heights == null) return null;
        int[] signs = null;
        Map<Long, Integer> indexByMiddle = null;
        for (var span : data.spans()) {
            if (span.type() != SpanType.STAIRS) continue;
            if (indexByMiddle == null) {
                indexByMiddle = new HashMap<>();
                for (int i = 0; i < middles.size(); i++) indexByMiddle.putIfAbsent(middles.get(i).asLong(), i);
                signs = new int[middles.size()];
            }
            Integer a = indexByMiddle.get(span.start().asLong());
            Integer b = indexByMiddle.get(span.end().asLong());
            if (a == null || b == null) continue;
            int from = Math.min(a, b);
            int to = Math.max(a, b);
            int sign = Integer.signum(heights[to] - heights[from]);
            if (sign == 0) continue;
            for (int i = from; i <= to; i++) signs[i] = sign;
        }
        return signs;
    }

    /** Block columns of the stair spans and of all roads in one chunk. */
    private static final class StairColumns {
        private final Set<Long> stairs = new HashSet<>();
        private final Set<Long> roads = new HashSet<>();
        private final Set<Long> surface = new HashSet<>();
        private final Map<Long, StairPaver.Column> paved = new HashMap<>();
        private final Set<BlockPos> stairMiddles = new HashSet<>();
        private final Set<RoadData> withStairs = Collections.newSetFromMap(new IdentityHashMap<>());

        void collect(RoadChunkSlice slice) {
            RoadData data = slice.road();
            List<RoadSegmentPlacement> segments = data.roadSegmentList();
            if (segments == null) return;
            for (int k = 0; k < slice.segmentCount(); k++) {
                int i = slice.segmentIndexAt(k);
                if (i < 0 || i >= segments.size()) continue;
                boolean surfaced = pavedSegment(data.villageConnector(), i, segments.size());
                for (BlockPos p : segments.get(i).positions()) {
                    long key = StairPaver.columnKey(p.getX(), p.getZ());
                    roads.add(key);
                    if (surfaced) surface.add(key);
                }
            }
            if (data.spans() == null || data.spans().stream().noneMatch(s -> s.type() == SpanType.STAIRS)) return;
            Map<Long, Integer> indexByMiddle = new HashMap<>();
            for (int i = 0; i < segments.size(); i++) indexByMiddle.putIfAbsent(segments.get(i).middlePos().asLong(), i);
            for (var span : data.spans()) {
                if (span.type() != SpanType.STAIRS) continue;
                Integer a = indexByMiddle.get(span.start().asLong());
                Integer b = indexByMiddle.get(span.end().asLong());
                if (a == null || b == null) continue;
                // Paved as stairs only when the stored heights still climb (see stairSigns).
                List<Integer> heights = data.targetY();
                if (heights == null || heights.size() != segments.size()
                        || heights.get(Math.min(a, b)).equals(heights.get(Math.max(a, b)))) continue;
                withStairs.add(data);
                for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
                    stairMiddles.add(segments.get(i).middlePos());
                    for (BlockPos p : segments.get(i).positions()) stairs.add(StairPaver.columnKey(p.getX(), p.getZ()));
                }
            }
        }

        boolean isStairMiddle(BlockPos middle) {
            return stairMiddles.contains(middle);
        }

        boolean hasStairs(RoadData data) {
            return withStairs.contains(data);
        }

        Set<Long> stairs() {
            return stairs;
        }

        Set<Long> roadColumns() {
            return roads;
        }

        /** Road columns that get paved, without the unpaved ends of ordinary roads. */
        Set<Long> surface() {
            return surface;
        }

        Map<Long, StairPaver.Column> paved() {
            return paved;
        }
    }

    /** Main axis of the road at segment {@code i}, pointing towards higher segment indices. */
    private static Direction travelDirection(List<BlockPos> middles, int i) {
        BlockPos a = middles.get(Math.max(0, i - 2));
        BlockPos b = middles.get(Math.min(middles.size() - 1, i + 2));
        int dx = b.getX() - a.getX();
        int dz = b.getZ() - a.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean contains(ChunkPos chunkPos, BlockPos pos) {
        return pos.getX() >= chunkPos.getMinBlockX() && pos.getX() <= chunkPos.getMaxBlockX()
                && pos.getZ() >= chunkPos.getMinBlockZ() && pos.getZ() <= chunkPos.getMaxBlockZ();
    }
}
