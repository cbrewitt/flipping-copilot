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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
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

    private static final Color TOOLTIP_BG = new Color(30, 30, 30, 220);
    private static final Color TOOLTIP_BORDER = new Color(80, 80, 80);
    private static final Color TEXT_PRIMARY = Color.WHITE;
    private static final Color PROFIT_POSITIVE = new Color(80, 220, 120);
    private static final Color PROFIT_NEGATIVE = new Color(230, 90, 90);

    private final Client client;
    private final InventorySlotTooltipDataProvider tooltipDataProvider;

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
        drawTooltip(graphics, slot.mouseX, slot.mouseY - 30, tooltipData.getLines());
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

            return new HoveredInventorySlot(itemId, quantity, mousePos.getX(), mousePos.getY());
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

    private void drawTooltip(Graphics2D graphics, int anchorX, int y, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }

        Font originalFont = graphics.getFont();
        graphics.setFont(new Font("Arial", Font.PLAIN, 11));
        FontMetrics fm = graphics.getFontMetrics();

        int padding = 5;
        int lineHeight = fm.getHeight();
        int maxWidth = lines.stream().mapToInt(fm::stringWidth).max().orElse(0);
        int tooltipWidth = maxWidth + padding * 2;
        int tooltipHeight = lineHeight * lines.size() + padding * 2;
        int x = anchorX - tooltipWidth - 15;

        graphics.setColor(TOOLTIP_BG);
        graphics.fillRoundRect(x, y, tooltipWidth, tooltipHeight, 4, 4);
        graphics.setColor(TOOLTIP_BORDER);
        graphics.drawRoundRect(x, y, tooltipWidth, tooltipHeight, 4, 4);

        int textY = y + padding + fm.getAscent();
        for (String line : lines) {
            drawTooltipLine(graphics, fm, line, x + padding, textY);
            textY += lineHeight;
        }

        graphics.setFont(originalFont);
    }

    private void drawTooltipLine(Graphics2D graphics, FontMetrics fm, String line, int x, int y) {
        if (line != null && line.startsWith("Profit est: ")) {
            String prefix = "Profit est: ";
            String value = line.substring(prefix.length());
            graphics.setColor(TEXT_PRIMARY);
            graphics.drawString(prefix, x, y);
            graphics.setColor(value.trim().startsWith("-") ? PROFIT_NEGATIVE : PROFIT_POSITIVE);
            graphics.drawString(value, x + fm.stringWidth(prefix), y);
            return;
        }
        graphics.setColor(TEXT_PRIMARY);
        graphics.drawString(line, x, y);
    }

    private static class HoveredInventorySlot {
        private final int itemId;
        private final int quantity;
        private final int mouseX;
        private final int mouseY;

        private HoveredInventorySlot(int itemId, int quantity, int mouseX, int mouseY) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }
    }
}
