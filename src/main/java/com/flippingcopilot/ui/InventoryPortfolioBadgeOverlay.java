package com.flippingcopilot.ui;

import com.flippingcopilot.controller.InventoryPortfolioService;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.util.FlipUtils;
import lombok.RequiredArgsConstructor;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

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
    private static final int BADGE_SIZE = 12;
    private static final int BADGE_MARGIN = 1;
    private static final BufferedImage BADGE_ICON = loadBadgeIcon();

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
            FlipV2 openFlip = portfolioService.getOpenFlip(unnotedItemId, accountId);
            List<FlipV2> openFlips = openFlip == null ? Collections.emptyList() : Collections.singletonList(openFlip);
            boolean inPortfolio = FlipUtils.inPortfolio(openFlips);
            if (!inPortfolio) {
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
        int x = slotBounds.x + slotBounds.width - BADGE_SIZE - BADGE_MARGIN;
        int y = slotBounds.y + slotBounds.height - BADGE_SIZE - BADGE_MARGIN;

        if (BADGE_ICON != null) {
            graphics.drawImage(BADGE_ICON, x, y, null);
            return;
        }

        graphics.setColor(BADGE_BG);
        graphics.fillOval(x, y, BADGE_SIZE, BADGE_SIZE);
        graphics.setColor(BADGE_BORDER);
        graphics.drawOval(x, y, BADGE_SIZE, BADGE_SIZE);

        Font originalFont = graphics.getFont();
        graphics.setFont(originalFont.deriveFont(Font.BOLD, 8f));
        graphics.setColor(BADGE_TEXT);
        graphics.drawString("P", x + 2, y + 8);
        graphics.setFont(originalFont);
    }

    private static BufferedImage loadBadgeIcon() {
        BufferedImage icon = ImageUtil.loadImageResource(InventoryPortfolioBadgeOverlay.class, "/icon-small.png");
        if (icon == null) {
            return null;
        }
        return ImageUtil.resizeImage(icon, BADGE_SIZE, BADGE_SIZE);
    }
}
