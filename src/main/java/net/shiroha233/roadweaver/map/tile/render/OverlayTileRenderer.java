package net.shiroha233.roadweaver.map.tile.render;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.map.tile.query.OverlayTileScene;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * overlay 瓦片渲染器。
 */
public final class OverlayTileRenderer {
    private OverlayTileRenderer() {}

    private static final Color ROAD_COLOR = new Color(0x1E, 0x1E, 0x1E, 210);
    private static final Color ROAD_OUTLINE = new Color(0xFF, 0xFF, 0xFF, 110);
    private static final Color STRUCTURE_COLOR = new Color(0x5E, 0x3D, 0x1E, 230);
    private static final int STRUCTURE_SIZE = 4;

    public static BufferedImage render(MapTileCoord coord, OverlayTileScene scene) {
        BufferedImage image = new BufferedImage(
                MapTileScheme.TILE_SIZE_PX,
                MapTileScheme.TILE_SIZE_PX,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawRoads(g, coord, scene.roadPolylines());
            drawStructures(g, coord, scene.structures());
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawRoads(Graphics2D g, MapTileCoord coord, List<List<BlockPos>> roads) {
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(ROAD_OUTLINE);
        for (List<BlockPos> road : roads) {
            drawPolyline(g, coord, road);
        }
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(ROAD_COLOR);
        for (List<BlockPos> road : roads) {
            drawPolyline(g, coord, road);
        }
    }

    private static void drawPolyline(Graphics2D g, MapTileCoord coord, List<BlockPos> road) {
        if (road == null || road.size() < 2) return;
        BlockPos prev = road.get(0);
        for (int i = 1; i < road.size(); i++) {
            BlockPos next = road.get(i);
            int x1 = toTilePixelX(coord, prev.getX());
            int y1 = toTilePixelY(coord, prev.getZ());
            int x2 = toTilePixelX(coord, next.getX());
            int y2 = toTilePixelY(coord, next.getZ());
            g.drawLine(x1, y1, x2, y2);
            prev = next;
        }
    }

    private static void drawStructures(Graphics2D g, MapTileCoord coord, List<BlockPos> structures) {
        g.setColor(STRUCTURE_COLOR);
        int half = STRUCTURE_SIZE / 2;
        for (BlockPos pos : structures) {
            int x = toTilePixelX(coord, pos.getX()) - half;
            int y = toTilePixelY(coord, pos.getZ()) - half;
            g.fillRect(x, y, STRUCTURE_SIZE, STRUCTURE_SIZE);
        }
    }

    private static int toTilePixelX(MapTileCoord coord, int worldX) {
        double pixel = MapTileScheme.worldToPixel(worldX, coord.zoom()) - MapTileScheme.tileOriginPixelX(coord);
        return (int) Math.round(pixel);
    }

    private static int toTilePixelY(MapTileCoord coord, int worldZ) {
        double pixel = MapTileScheme.worldToPixel(worldZ, coord.zoom()) - MapTileScheme.tileOriginPixelZ(coord);
        return (int) Math.round(pixel);
    }
}