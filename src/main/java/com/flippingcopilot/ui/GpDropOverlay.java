package com.flippingcopilot.ui;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TextComponent;

import java.awt.*;

public class GpDropOverlay extends Overlay {
    private final OverlayManager overlayManager;
    private final long startTime;
    private final Point startPosition = new Point(); // Starting position of the text
    final TextComponent textComponent = new TextComponent();

    public GpDropOverlay(OverlayManager overlayManager, Client client, long profit, int slot) {
        this.overlayManager = overlayManager;
        this.startTime = System.currentTimeMillis();

        Widget slotWidget = client.getWidget(465, slot + 7);
        if (slotWidget == null) {
            return;
        }
        startPosition.x = slotWidget.getCanvasLocation().getX() + 35;
        startPosition.y = slotWidget.getCanvasLocation().getY() + 75;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);

        String absProfitText = UIUtilities.quantityToRSDecimalStack(Math.abs(profit), false);
        String profitText = (profit >= 0 ? "+ " : "- ") + absProfitText + " gp";
        textComponent.setText(profitText);
        textComponent.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 16f));
        if (profit < 0) {
            textComponent.setColor(Color.RED);
        } else {
            textComponent.setColor(Color.GREEN);
        }
        overlayManager.add(this);
    }


    @Override
    public Dimension render(Graphics2D graphics) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > 3000) { // Display for 5 seconds
            overlayManager.remove(this);
            return null;
        }

        // Calculate the upward movement. Adjust the divisor to control the speed.
        int yOffset = (int) (elapsed / 50); // Moves up 1 pixel every 50ms

        // Ensure the text moves upwards by subtracting yOffset from the starting Y position
        Point currentPosition = new Point(startPosition.x, startPosition.y - yOffset);
        textComponent.setPosition(currentPosition);
        textComponent.render(graphics);

        return null;
    }
}