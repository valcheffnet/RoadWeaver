package net.shiroha233.roadweaver.structures.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 初始小屋放置器。
 * Server-side 26.3 fork: vanilla generates the spawn chunks while it searches a spawn point, before structures can be
 * injected into them, so the cabin is no longer queued as a pending structure. ensurePlaced() only registers the
 * anchor as a road endpoint (without loading chunks); placePlannedCabin() builds the cabin into the world once the
 * spawn chunks exist.
 */
public final class SpawnCabinPlacer {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private SpawnCabinPlacer() {}

    private static final Identifier STRUCTURE_ID = Identifier.fromNamespaceAndPath("roadweaver", "spawn_cabin");

    /** Anchor registered by ensurePlaced() and not built yet. */
    private static final AtomicReference<BlockPos> PLANNED_ANCHOR = new AtomicReference<>();

    /**
     * Where vanilla starts its spawn search (MinecraftServer.setInitialSpawn): the middle of the generator origin chunk.
     * The height comes from the noise only, so no chunk is generated.
     */
    public static BlockPos predictSpawn(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        ChunkPos origin = ChunkPos.containing(randomState.sampler().findSpawnPosition());
        int x = origin.getMinBlockX() + 8;
        int z = origin.getMinBlockZ() + 8;
        int y = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, randomState);
        return new BlockPos(x, y, z);
    }

    public static boolean ensurePlaced(ServerLevel level) {
        if (level == null) return false;

        BlockPos spawn = level.getRespawnData().pos();

        var provider = WorldDataProvider.getInstance();
        var locs = provider.getStructureLocations(level);
        if (locs != null && locs.structureLocations() != null && !locs.structureLocations().isEmpty()) {
            return false;
        }

        if (!(spawnCabin(level) instanceof SpawnCabinStructure)) {
            return false;
        }

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int y = generator.getBaseHeight(spawn.getX(), spawn.getZ(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                level, level.getChunkSource().randomState());
        BlockPos anchor = new BlockPos(spawn.getX(), y, spawn.getZ());
        PLANNED_ANCHOR.set(anchor);

        provider.addStructureLocation(level, anchor);
        StructureFileStorage.addStructures(
                level,
                java.util.List.of(new StructureInfo(new BlockPos(anchor.getX(), 0, anchor.getZ()), STRUCTURE_ID.toString())),
                StructureFileStorage.SOURCE_MANUAL
        );

        return true;
    }

    /**
     * Builds the cabin registered by ensurePlaced() directly into the world. Call it after the initial roads are
     * planned and the spawn chunks exist; the chunks under the cabin are loaded if needed.
     */
    public static void placePlannedCabin(ServerLevel level, BlockPos worldSpawn) {
        BlockPos anchor = PLANNED_ANCHOR.getAndSet(null);
        if (level == null || anchor == null) return;
        if (!(spawnCabin(level) instanceof SpawnCabinStructure cabin)) return;
        Vec3i size = level.getStructureManager().get(cabin.templateId())
                .map(t -> t.getSize()).orElse(cabin.sizeHint());
        BlockPos pos = findFreeSpot(level, anchor, size);
        if (pos == null) {
            LOGGER.warn("No free spot for the spawn cabin within {} blocks of {}, skipping it", SEARCH_RADIUS, anchor);
            return;
        }
        StructureStart start = cabin.placeAt(level, level.structureManager(), level.getStructureManager(),
                level.getChunkSource().getGenerator(), pos, Rotation.NONE, level.getRandom());
        if (start == null) {
            LOGGER.warn("Spawn cabin could not be placed at {}", pos);
        } else {
            LOGGER.info("Spawn cabin placed at {} (world spawn {})", pos, worldSpawn);
        }
    }

    /** How far from the planned anchor the cabin may move to stay clear of other structures and the road. */
    private static final int SEARCH_RADIUS = 32;
    private static final int SEARCH_STEP = 4;
    /** Gap kept between the cabin and other structure pieces. */
    private static final int CLEARANCE = 2;

    /**
     * Nearest spot to {@code anchor} (spiral in {@link #SEARCH_STEP} block steps) where the cabin footprint touches no
     * structure piece and no road. The returned position has the cabin's foundation layer in the top ground block.
     */
    private static BlockPos findFreeSpot(ServerLevel level, BlockPos anchor, Vec3i size) {
        for (int ring = 0; ring <= SEARCH_RADIUS / SEARCH_STEP; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = anchor.getX() + dx * SEARCH_STEP;
                    int z = anchor.getZ() + dz * SEARCH_STEP;
                    int y = groundTop(level, x + size.getX() / 2, z + size.getZ() / 2);
                    BoundingBox box = new BoundingBox(x, y, z, x + size.getX() - 1, y + size.getY() - 1, z + size.getZ() - 1);
                    if (!touchesStructure(level, box.inflatedBy(CLEARANCE)) && !touchesRoad(level, box)) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Top ground block at x/z. Level.getHeight answers getMinY() for chunks that are not loaded, so the chunk is
     * loaded (generated if needed) first; this mirrors Level.getHeight without the "+ 1".
     */
    private static int groundTop(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    private static boolean touchesStructure(ServerLevel level, BoundingBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.STRUCTURE_REFERENCES);
                for (Map.Entry<Structure, LongSet> ref : chunk.getAllReferences().entrySet()) {
                    for (long startChunk : ref.getValue()) {
                        ChunkAccess startAccess = level.getChunk(ChunkPos.getX(startChunk), ChunkPos.getZ(startChunk),
                                ChunkStatus.STRUCTURE_STARTS);
                        StructureStart start = startAccess.getStartForStructure(ref.getKey());
                        if (start == null || !start.isValid()) continue;
                        for (StructurePiece piece : start.getPieces()) {
                            if (piece.getBoundingBox().intersects(box)) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean touchesRoad(ServerLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (RoadPositionQuery.isOnRoad(level, pos.set(x, box.minY(), z))) return true;
            }
        }
        return false;
    }

    private static Structure spawnCabin(ServerLevel level) {
        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        return structureRegistry.getValue(STRUCTURE_ID);
    }
}
