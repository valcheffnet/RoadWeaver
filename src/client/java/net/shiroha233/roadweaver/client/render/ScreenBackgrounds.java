package net.shiroha233.roadweaver.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared non-blur screen background used by custom UI screens.
 */
public final class ScreenBackgrounds {
    private static final int TOP_COLOR = 0xC0101010;
    private static final int BOTTOM_COLOR = 0xD0101010;

    private ScreenBackgrounds() {}

    public static void render(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, height, TOP_COLOR, BOTTOM_COLOR);
    }
}
