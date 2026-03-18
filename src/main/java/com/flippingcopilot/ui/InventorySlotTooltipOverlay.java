package com.flippingcopilot.ui;

import com.flippingcopilot.controller.InventorySlotTooltipDataProvider;
import com.flippingcopilot.model.InventorySlotTooltipData;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventorySlotTooltipOverlay extends Overlay {

    private static final int INVENTORY_WIDGET_GROUP = 149;
    private static final int INVENTORY_WIDGET_CHILD = 0;
    private static final int GE_INVENTORY_WIDGET_GROUP = 467;
    private static final int GE_INVENTORY_WIDGET_CHILD = 0;

    private static final String UNREALISED_PNL_PREFIX = "Unrealized PNL: ";
    private static final String POSITIVE_COLOR = "<col=50dc78>";
    private static final String NEGATIVE_COLOR = "<col=e65a5a>";
    private static final String COLOR_END = "</col>";

    private final Client client;
    private final InventorySlotTooltipDataProvider tooltipDataProvider;
    private final TooltipManager tooltipManager;

    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        HoveredInventorySlot slot = findHoveredInventorySlot();
        if (slot == null) {
            return null;
        }

        InventorySlotTooltipData tooltipData = tooltipDataProvider.getTooltipData(slot.itemId, slot.quantity);
        if (tooltipData == null) {
            return null;
        }

        String tooltipText = buildTooltipText(tooltipData.getLines());
        if (tooltipText != null && !tooltipText.isEmpty()) {
            tooltipManager.add(new Tooltip(tooltipText));
        }
        return null;
    }

    private HoveredInventorySlot findHoveredInventorySlot() {
        Point mousePos = client.getMouseCanvasPosition();
        if (mousePos == null) {
            return null;
        }

        Widget inventoryWidget = getInventoryWidget();
        if (inventoryWidget == null) {
            return null;
        }

        Widget[] children = inventoryWidget.getDynamicChildren();
        if (children == null) {
            return null;
        }

        for (Widget child : children) {
            if (child == null || child.isHidden()) {
                continue;
            }

            int itemId = child.getItemId();
            int quantity = child.getItemQuantity();
            if (itemId <= 0 || quantity <= 0) {
                continue;
            }

            Rectangle bounds = child.getBounds();
            if (bounds == null || !bounds.contains(mousePos.getX(), mousePos.getY())) {
                continue;
            }

            return new HoveredInventorySlot(itemId, quantity);
        }

        return null;
    }

    private Widget getInventoryWidget() {
        Widget geInventory = client.getWidget(GE_INVENTORY_WIDGET_GROUP, GE_INVENTORY_WIDGET_CHILD);
        if (geInventory != null && !geInventory.isHidden()) {
            return geInventory;
        }

        Widget inventory = client.getWidget(INVENTORY_WIDGET_GROUP, INVENTORY_WIDGET_CHILD);
        if (inventory != null && !inventory.isHidden()) {
            return inventory;
        }

        return null;
    }

    private String buildTooltipText(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("<br>");
            }
            sb.append(formatTooltipLine(lines.get(i)));
        }
        return sb.toString();
    }

    private String formatTooltipLine(String line) {
        if (line != null && line.startsWith(UNREALISED_PNL_PREFIX)) {
            String value = line.substring(UNREALISED_PNL_PREFIX.length());
            String color = value.contains("-") ? NEGATIVE_COLOR : POSITIVE_COLOR;
            return UNREALISED_PNL_PREFIX + color + value + COLOR_END;
        }
        return line == null ? "" : line;
    }

    private static class HoveredInventorySlot {
        private final int itemId;
        private final int quantity;

        private HoveredInventorySlot(int itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }
}
