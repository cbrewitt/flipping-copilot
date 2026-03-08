package com.flippingcopilot.ui;

import com.flippingcopilot.controller.InventoryPortfolioService;
import lombok.RequiredArgsConstructor;
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
import java.awt.Graphics2D;
import java.awt.Rectangle;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventoryPortfolioBadgeOverlay extends Overlay {

    private static final int INVENTORY_WIDGET_GROUP = 149;
    private static final int INVENTORY_WIDGET_CHILD = 0;
    private static final int GE_INVENTORY_WIDGET_GROUP = 467;
    private static final int GE_INVENTORY_WIDGET_CHILD = 0;

    private static final Color BADGE_BG = new Color(30, 60, 35, 220);
    private static final Color BADGE_BORDER = new Color(120, 230, 150);
    private static final Color BADGE_TEXT = Color.WHITE;

    private final net.runelite.api.Client client;
    private final InventoryPortfolioService portfolioService;

    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Widget inventoryWidget = getInventoryWidget();
        if (inventoryWidget == null) {
            return null;
        }

        Integer accountId = portfolioService.getActiveAccountId();
        if (accountId == null) {
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

            int unnotedItemId = portfolioService.toUnnotedItemId(itemId);
            if (!portfolioService.isInPortfolio(unnotedItemId, accountId)) {
                continue;
            }

            Rectangle bounds = child.getBounds();
            if (bounds == null) {
                continue;
            }
            drawPortfolioBadge(graphics, bounds);
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

    private void drawPortfolioBadge(Graphics2D graphics, Rectangle slotBounds) {
        final int size = 10;
        final int margin = 1;
        int x = slotBounds.x + slotBounds.width - size - margin;
        int y = slotBounds.y + slotBounds.height - size - margin;

        graphics.setColor(BADGE_BG);
        graphics.fillOval(x, y, size, size);
        graphics.setColor(BADGE_BORDER);
        graphics.drawOval(x, y, size, size);

        Font originalFont = graphics.getFont();
        graphics.setFont(originalFont.deriveFont(Font.BOLD, 8f));
        graphics.setColor(BADGE_TEXT);
        graphics.drawString("P", x + 2, y + 8);
        graphics.setFont(originalFont);
    }
}
