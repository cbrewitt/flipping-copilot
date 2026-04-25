package com.flippingcopilot.ui;

import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.ImageUtil;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

public class NpcHighlightOverlay extends Overlay {
    private static final int OUTLINE_BORDER_WIDTH = 4;
    private static final int OUTLINE_FEATHER = 4;
    private static final BufferedImage ICON = ImageUtil.loadImageResource(NpcHighlightOverlay.class, "/icon-small.png");

    private final NPC npc;
    private final Supplier<Color> colorSupplier;
    private final ModelOutlineRenderer modelOutlineRenderer;

    public NpcHighlightOverlay(NPC npc, Supplier<Color> colorSupplier, ModelOutlineRenderer modelOutlineRenderer) {
        this.npc = npc;
        this.colorSupplier = colorSupplier;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (npc == null) {
            return null;
        }
        Color color = colorSupplier.get();
        if (color == null) {
            return null;
        }
        modelOutlineRenderer.drawOutline(npc, OUTLINE_BORDER_WIDTH, color, OUTLINE_FEATHER);

        if (ICON != null) {
            Point iconLocation = npc.getCanvasImageLocation(ICON, npc.getLogicalHeight() / 2);
            if (iconLocation != null) {
                graphics.drawImage(ICON, iconLocation.getX(), iconLocation.getY(), null);
            }
        }
        return null;
    }
}
