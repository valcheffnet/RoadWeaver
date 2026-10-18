/* Joins roads that stop before a village to the nearest street of that village, around its buildings. */
package net.shiroha233.roadweaver.structures.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.postprocess.JunctionSlopeLimiter;
import net.shiroha233.roadweaver.postprocess.RoadBumpFlattener;
import net.shiroha233.roadweaver.structures.village.VillageConnectorPathfinder.Column;
import net.shiroha233.roadweaver.structures.village.VillageConnectorPathfinder.Rect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Roads are trimmed {@code villageRoadOffset} blocks before a village, and the planner cannot see the village layout.
 * Once a village's structure start exists, every road that ends at it gets a connector: a short road from its last paved
 * segment to the nearest street piece, found on a block grid that keeps clear of every non-street piece.
 * Connectors are stored as roads with {@link RoadData#villageConnector()} set, so worldgen paves them like any road.
 */
public final class VillageStreetConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    /** A road owner is the village's locate position (start chunk corner); allow some slack for older data. */
    private static final int OWNER_TOLERANCE = 24;
    /** Free blocks kept between a connector's centre line and a building, on top of half the road width. */
    private static final int BUILDING_MARGIN = 1;
    private static final int SEARCH_PAD = 24;
    private static final int MAX_NODES = 80_000;
    private static final int MAX_WIDTH = 3;
    /** First and last segment PathFeature paves on an ordinary road (it leaves the ends of every road unpaved). */
    private static final int FIRST_PAVED = 9;
    private static final int LAST_PAVED_FROM_END = 7;
    private static final int ROADS_PER_TICK = 2;

    private static final ConcurrentLinkedQueue<RoadData> ADDED_ROADS = new ConcurrentLinkedQueue<>();

    private VillageStreetConnector() {}

    /**
     * @param streets       street piece boxes; they also hold the space where houses attach, so they only guide the search
     * @param streetColumns the columns of the street surface itself (empty when the templates have no known surface block)
     */
    record Layout(List<Rect> streets, Set<Long> streetColumns, List<Rect> buildings) {
        boolean isStreet(int x, int z) {
            return streetColumns.isEmpty()
                    ? VillageConnectorPathfinder.isInside(streets, x, z)
                    : streetColumns.contains(VillageConnectorPathfinder.key(x, z));
        }
    }

    /** Surface blocks of vanilla village streets: dirt path, and smooth sandstone in deserts. */
    private static final List<Block> STREET_SURFACE = List.of(Blocks.DIRT_PATH, Blocks.SMOOTH_SANDSTONE);

    /** Worldgen thread: the structure starts of {@code chunk} were just created. */
    public static void onStructureStartsAvailable(ServerLevel level, ChunkAccess chunk) {
        if (!enabled(level)) return;
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            StructureStart start = entry.getValue();
            if (start == null || !start.isValid() || !isVillage(level, entry.getKey())) continue;
            try {
                connect(level, chunk.getPos(), start, null);
            } catch (RuntimeException failure) {
                LOGGER.warn("Village street connector failed at chunk {}", chunk.getPos(), failure);
            }
        }
    }

    /** A road planned after the village start already existed misses the hook above; it is checked on the next ticks. */
    public static void onRoadAdded(RoadData road) {
        if (road != null && !road.villageConnector() && road.hasOwnerPair()) ADDED_ROADS.add(road);
    }

    /** Server thread. */
    public static void tick(ServerLevel level) {
        if (ADDED_ROADS.isEmpty()) return;
        if (!enabled(level)) {
            ADDED_ROADS.clear();
            return;
        }
        for (int i = 0; i < ROADS_PER_TICK; i++) {
            RoadData road = ADDED_ROADS.poll();
            if (road == null) return;
            for (long owner : new long[]{road.ownerA2dKey(), road.ownerB2dKey()}) {
                BlockPos ownerPos = PlanningUtils.posFrom2dKey(owner);
                ChunkPos chunkPos = ChunkPos.containing(ownerPos);
                ChunkAccess chunk = level.getChunkSource().getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.STRUCTURE_STARTS, false);
                if (chunk == null) continue;
                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (start == null || !start.isValid() || !isVillage(level, entry.getKey())) continue;
                    try {
                        connect(level, chunkPos, start, road);
                    } catch (RuntimeException failure) {
                        LOGGER.warn("Village street connector failed at chunk {}", chunkPos, failure);
                    }
                }
            }
        }
    }

    public static void clear() {
        ADDED_ROADS.clear();
    }

    private static boolean enabled(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension()) && ConfigService.get().connectVillageStreets();
    }

    private static boolean isVillage(ServerLevel level, Structure structure) {
        return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(structure).is(StructureTags.VILLAGE);
    }

    /** @param onlyRoad when set, only this road is connected (it was added after the start existed) */
    private static void connect(ServerLevel level, ChunkPos startChunk, StructureStart start, RoadData onlyRoad) {
        BlockPos villagePos = new BlockPos(startChunk.getMinBlockX(), 0, startChunk.getMinBlockZ());
        Layout layout = layout(level, start);
        if (layout.streets().isEmpty()) return;

        int reach = ConfigService.get().villageRoadOffset() + 96;
        List<RoadData> nearby = RoadShardStorage.queryRect(level,
                villagePos.getX() - reach, villagePos.getZ() - reach, villagePos.getX() + reach, villagePos.getZ() + reach);
        List<RoadData> connectors = new ArrayList<>();
        for (RoadData road : nearby) {
            if (road.villageConnector()) connectors.add(road);
        }
        List<RoadData> candidates = onlyRoad != null ? List.of(onlyRoad) : nearby;

        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            AccurateHeightSampler heights = cache.getAccurateSampler(level);
            for (RoadData road : candidates) {
                if (road.villageConnector() || !endsAt(road, villagePos)) continue;
                RoadData connector = build(road, villagePos, layout, heights);
                if (connector == null || alreadyConnected(connectors, connector)) continue;
                RoadShardStorage.addRoad(level, connector);
                connectors.add(connector);
                LOGGER.debug("Connected road to village at {} with {} segments", villagePos, connector.roadSegmentList().size());
            }
        } finally {
            cache.clear();
        }
    }

    static Layout layout(ServerLevel level, StructureStart start) {
        StructureTemplateManager templates = level.getStructureManager();
        List<Rect> streets = new ArrayList<>();
        Set<Long> streetColumns = new HashSet<>();
        List<Rect> buildings = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            Rect rect = new Rect(box.minX(), box.minZ(), box.maxX(), box.maxZ());
            if (piece instanceof PoolElementStructurePiece poolPiece
                    && poolPiece.getElement() instanceof SinglePoolElement element
                    && element.getTemplateLocation().getPath().contains("/streets/")) {
                streets.add(rect);
                addSurface(templates, element, poolPiece, streetColumns);
            } else {
                buildings.add(rect);
            }
        }
        return new Layout(streets, streetColumns, buildings);
    }

    private static void addSurface(StructureTemplateManager templates, SinglePoolElement element,
                                   PoolElementStructurePiece piece, Set<Long> out) {
        StructureTemplate template = templates.get(element.getTemplateLocation()).orElse(null);
        if (template == null) return;
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
        for (Block surface : STREET_SURFACE) {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(piece.getPosition(), settings, surface)) {
                out.add(VillageConnectorPathfinder.key(info.pos().getX(), info.pos().getZ()));
            }
        }
    }

    private static boolean endsAt(RoadData road, BlockPos villagePos) {
        if (!road.hasOwnerPair()) return false;
        return near(road.ownerA2dKey(), villagePos) || near(road.ownerB2dKey(), villagePos);
    }

    private static boolean near(long owner, BlockPos villagePos) {
        BlockPos pos = PlanningUtils.posFrom2dKey(owner);
        long dx = pos.getX() - villagePos.getX();
        long dz = pos.getZ() - villagePos.getZ();
        return dx * dx + dz * dz <= (long) OWNER_TOLERANCE * OWNER_TOLERANCE;
    }

    static RoadData build(RoadData road, BlockPos villagePos, Layout layout, AccurateHeightSampler heights) {
        List<RoadSegmentPlacement> segments = road.roadSegmentList();
        int n = segments.size();
        if (n < 2) return null;
        // The end of the segment list that lies closer to the village is the end that stops before it.
        boolean tail = distSq(segments.get(n - 1).middlePos(), villagePos) <= distSq(segments.get(0).middlePos(), villagePos);
        int join = tail ? Math.max(0, n - LAST_PAVED_FROM_END) : Math.min(n - 1, FIRST_PAVED);
        int step = tail ? -1 : 1;
        // Start from the last paved segment that is outside every village piece.
        while (join >= 0 && join < n && VillageConnectorPathfinder.isInside(layout.buildings(), segments.get(join).middlePos().getX(),
                segments.get(join).middlePos().getZ())) {
            join += step;
        }
        if (join < 0 || join >= n) return null;
        BlockPos joinPos = segments.get(join).middlePos();
        if (layout.isStreet(joinPos.getX(), joinPos.getZ())) return null;

        int width = Math.max(1, Math.min(MAX_WIDTH, road.width()));
        int margin = width / 2 + BUILDING_MARGIN;
        List<Rect> blocked = new ArrayList<>(layout.buildings().size());
        for (Rect building : layout.buildings()) blocked.add(building.grow(margin));

        List<Column> path = VillageConnectorPathfinder.find(new Column(joinPos.getX(), joinPos.getZ()),
                key -> layout.isStreet((int) (key >> 32), (int) key), layout.streets(), blocked,
                heights::surfaceHeight, SEARCH_PAD, MAX_NODES);
        if (path.size() < 2) return null;

        int joinY = road.targetY() != null && join < road.targetY().size() && road.targetY().get(join) != null
                ? road.targetY().get(join) : joinPos.getY();
        List<Integer> targetY = smoothHeights(path, heights, joinY);
        List<RoadSegmentPlacement> placements = new ArrayList<>(path.size());
        double radius = width / 2.0;
        int reachBlocks = (int) Math.ceil(radius);
        for (int i = 0; i < path.size(); i++) {
            Column column = path.get(i);
            List<BlockPos> positions = new ArrayList<>();
            for (int dx = -reachBlocks; dx <= reachBlocks; dx++) {
                for (int dz = -reachBlocks; dz <= reachBlocks; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 0.3) continue;
                    int x = column.x() + dx;
                    int z = column.z() + dz;
                    // The road body never enters a building or covers the village street itself.
                    if (VillageConnectorPathfinder.isInside(layout.buildings(), x, z)) continue;
                    if (layout.isStreet(x, z)) continue;
                    positions.add(new BlockPos(x, 0, z));
                }
            }
            if (positions.isEmpty()) positions.add(new BlockPos(column.x(), 0, column.z()));
            placements.add(new RoadSegmentPlacement(new BlockPos(column.x(), targetY.get(i), column.z()), positions));
        }
        return new RoadData(width, road.roadType(), road.materials(), road.slabMaterials(), placements, List.of(), targetY,
                PlanningUtils.pos2dKey(villagePos), RoadData.NO_OWNER_2D, true);
    }

    /**
     * Ground heights averaged over five columns, then held to the road slope limit between the road's own height at the
     * start and the ground at the street.
     */
    private static List<Integer> smoothHeights(List<Column> path, AccurateHeightSampler heights, int joinY) {
        int n = path.size();
        int[] ground = new int[n];
        for (int i = 0; i < n; i++) ground[i] = heights.surfaceHeight(path.get(i).x(), path.get(i).z());
        int[] smoothed = new int[n];
        for (int i = 0; i < n; i++) {
            int sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - 2); j <= Math.min(n - 1, i + 2); j++) {
                sum += ground[j];
                count++;
            }
            smoothed[i] = Math.round(sum / (float) count);
        }
        smoothed[0] = joinY;
        smoothed[n - 1] = ground[n - 1];
        boolean[] fixed = new boolean[n];
        fixed[0] = true;
        fixed[n - 1] = true;
        RoadBumpFlattener.flatten(smoothed, fixed, 4);
        var cfg = ConfigService.get();
        JunctionSlopeLimiter.limit(smoothed, fixed, cfg.slopeLimitEnabled() ? cfg.maxSlopeStepPerTwoSegments() : 2);
        List<Integer> out = new ArrayList<>(n);
        for (int y : smoothed) out.add(y);
        return out;
    }

    private static boolean alreadyConnected(List<RoadData> connectors, RoadData candidate) {
        BlockPos first = candidate.roadSegmentList().getFirst().middlePos();
        for (RoadData existing : connectors) {
            BlockPos other = existing.roadSegmentList().getFirst().middlePos();
            if (Math.abs(other.getX() - first.getX()) <= 2 && Math.abs(other.getZ() - first.getZ()) <= 2) return true;
        }
        return false;
    }

    private static long distSq(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
