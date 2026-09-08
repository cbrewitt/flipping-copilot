package copilot.ui;

import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.*;
import net.runelite.client.util.*;

import java.awt.*;
import java.awt.image.*;
import java.util.function.*;

public class NpcHighlightOverlay extends Overlay {
    private static final int OUTLINE_BORDER_WIDTH = 4, OUTLINE_FEATHER = 4;
    private static final BufferedImage ICON = ImageUtil.loadImageResource(NpcHighlightOverlay.class, "/icon-small.png");

    private final NPC npc;
    private final Supplier<Color> colorSupplier;
    private final ModelOutlineRenderer modelOutlineRenderer;

    public NpcHighlightOverlay(NPC npc, Supplier<Color> colorSupplier, ModelOutlineRenderer modelOutlineRenderer) {
        this.npc = npc; this.colorSupplier = colorSupplier; this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (npc == null) { return null; }
        Color color = colorSupplier.get();
        if (color == null) { return null; }
        modelOutlineRenderer.drawOutline(npc, OUTLINE_BORDER_WIDTH, color, OUTLINE_FEATHER);

        Point iconLocation = npc.getCanvasImageLocation(ICON, npc.getLogicalHeight() / 2);
        if (iconLocation != null) { graphics.drawImage(ICON, iconLocation.getX(), iconLocation.getY(), null); }
        return null;
    }
}
