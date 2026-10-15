package net.shiroha233.roadweaver.structures.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        ChunkPos origin = generator.getOrigin(randomState);
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
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ());
        BlockPos pos = new BlockPos(anchor.getX(), y, anchor.getZ());
        StructureStart start = cabin.placeAt(level, level.structureManager(), level.getStructureTemplateManager(),
                level.getChunkSource().getGenerator(), pos, Rotation.NONE, level.getRandom());
        if (start == null) {
            LOGGER.warn("Spawn cabin could not be placed at {}", pos);
        } else {
            LOGGER.info("Spawn cabin placed at {} (world spawn {})", pos, worldSpawn);
        }
    }

    private static Structure spawnCabin(ServerLevel level) {
        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        return structureRegistry.getValue(STRUCTURE_ID);
    }
}
