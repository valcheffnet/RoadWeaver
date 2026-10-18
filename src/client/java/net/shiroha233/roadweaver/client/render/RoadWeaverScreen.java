package net.shiroha233.roadweaver.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared screen base that keeps RoadWeaver UI backgrounds deterministic and non-blurred on 1.21.
 */
public abstract class RoadWeaverScreen extends Screen {
    protected RoadWeaverScreen(Component title) {
        super(title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
    }
}
