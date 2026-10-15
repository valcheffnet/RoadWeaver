/* 文件职责：验证 terrain 图层目录隔离与旧版 terrain_v2 迁移行为。 */
package net.shiroha233.roadweaver.map.tile.storage;

import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMapTileStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void pathAndExistenceStayLayerIsolated() throws IOException {
        Path dimensionRoot = tempDir.resolve("overworld");
        MapTileCoord coord = new MapTileCoord(3, 4, 5);

        Path coarsePath = ServerMapTileStorage.path(dimensionRoot, MapTileLayer.TERRAIN_COARSE, coord);
        Path accuratePath = ServerMapTileStorage.path(dimensionRoot, MapTileLayer.TERRAIN_ACCURATE, coord);

        assertEquals(dimensionRoot.resolve("terrain_coarse").resolve("z3").resolve("4").resolve("5.png"), coarsePath);
        assertEquals(dimensionRoot.resolve("terrain_accurate").resolve("z3").resolve("4").resolve("5.png"), accuratePath);
        assertNotEquals(coarsePath, accuratePath);

        writeText(accuratePath, "accurate");

        assertTrue(ServerMapTileStorage.exists(dimensionRoot, MapTileLayer.TERRAIN_ACCURATE, coord));
        assertFalse(ServerMapTileStorage.exists(dimensionRoot, MapTileLayer.TERRAIN_COARSE, coord));
    }

    @Test
    void migrationCopiesLegacyTilesIntoCoarseWithoutOverwritingNewTiles() throws IOException {
        Path dimensionRoot = tempDir.resolve("overworld");
        MapTileCoord preservedCoord = new MapTileCoord(2, 7, 9);
        MapTileCoord copiedCoord = new MapTileCoord(1, 3, 4);

        Path coarsePreservedPath = ServerMapTileStorage.path(dimensionRoot, MapTileLayer.TERRAIN_COARSE, preservedCoord);
        Path coarseCopiedPath = ServerMapTileStorage.path(dimensionRoot, MapTileLayer.TERRAIN_COARSE, copiedCoord);
        Path legacyPreservedPath = ServerMapTilePathResolver.legacyTerrainTilePath(dimensionRoot, preservedCoord);
        Path legacyCopiedPath = ServerMapTilePathResolver.legacyTerrainTilePath(dimensionRoot, copiedCoord);

        writeText(coarsePreservedPath, "new-coarse");
        writeText(legacyPreservedPath, "legacy-preserved");
        writeText(legacyCopiedPath, "legacy-copied");

        ServerMapTileStorage.TerrainLayerMigrationResult result =
                ServerMapTileStorage.migrateLegacyTerrainTiles(dimensionRoot);

        assertTrue(result.completed());
        assertEquals(1, result.copiedTiles());
        assertEquals(1, result.skippedExistingTiles());
        assertEquals("new-coarse", Files.readString(coarsePreservedPath));
        assertEquals("legacy-copied", Files.readString(coarseCopiedPath));
        assertFalse(ServerMapTileStorage.exists(dimensionRoot, MapTileLayer.TERRAIN_ACCURATE, copiedCoord));
        assertFalse(Files.exists(ServerMapTilePathResolver.legacyTerrainRoot(dimensionRoot)));
        assertTrue(Files.exists(ServerMapTilePathResolver.terrainLayerMigrationMarker(dimensionRoot)));
    }

    @Test
    void coverageCheckRejectsPartiallyTransparentSharedTile() throws IOException {
        Path dimensionRoot = tempDir.resolve("overworld");
        MapTileCoord coord = new MapTileCoord(0, 0, 0);
        Path path = ServerMapTileStorage.path(dimensionRoot, MapTileLayer.TERRAIN_ACCURATE, coord);
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < 10; z++) {
            for (int x = 0; x < 9; x++) image.setRGB(x, z, 0xFF336699);
        }
        ImageIO.write(image, "PNG", path.toFile());
        int minX = MapTileScheme.sampleBlockX(coord, 0);
        int minZ = MapTileScheme.sampleBlockZ(coord, 0);
        int maxX = MapTileScheme.sampleBlockX(coord, 9);
        int maxZ = MapTileScheme.sampleBlockZ(coord, 9);

        assertFalse(ServerMapTileStorage.hasCoverage(
                dimensionRoot, MapTileLayer.TERRAIN_ACCURATE, coord,
                minX, minZ, maxX, maxZ));

        for (int z = 0; z < 10; z++) image.setRGB(9, z, 0xFF336699);
        ImageIO.write(image, "PNG", path.toFile());
        assertTrue(ServerMapTileStorage.hasCoverage(
                dimensionRoot, MapTileLayer.TERRAIN_ACCURATE, coord,
                minX, minZ, maxX, maxZ));
    }

    private static void writeText(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
