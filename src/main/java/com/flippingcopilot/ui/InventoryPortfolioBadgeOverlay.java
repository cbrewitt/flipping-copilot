package com.flippingcopilot.ui;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.controller.PlayerLocationController;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.rs.PortfolioStateRS;
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
import java.util.ArrayList;
import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventoryPortfolioBadgeOverlay extends Overlay {

    private static final int INVENTORY_WIDGET_GROUP = 149;
    private static final int INVENTORY_WIDGET_CHILD = 0;
    private static final int GE_INVENTORY_WIDGET_GROUP = 467;
    private static final int GE_INVENTORY_WIDGET_CHILD = 0;
    private static final int BANK_WIDGET_GROUP = 12;
    private static final int[] BANK_ITEM_CONTAINER_CHILDREN = {12, 13, 89};
    private static final int BANK_INVENTORY_WIDGET_GROUP = 15;
    private static final int BANK_INVENTORY_WIDGET_CHILD = 3;

    private static final Color BADGE_BG = new Color(30, 60, 35, 220);
    private static final Color BADGE_BORDER = new Color(120, 230, 150);
    private static final Color BADGE_TEXT = Color.WHITE;
    private static final int BADGE_SIZE = 12;
    private static final int BADGE_MARGIN = 1;
    private static final BufferedImage BADGE_ICON = loadBadgeIcon();

    private final net.runelite.api.Client client;
    private final ItemController itemController;
    private final PortfolioStateRS portfolioStateRS;
    private final PlayerLocationController playerLocationController;

    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        List<Widget> itemWidgets = getVisibleItemWidgets();
        if (itemWidgets.isEmpty()) {
            return null;
        }

        if (!portfolioStateRS.get().isLoaded()) {
            return null;
        }

        if (!playerLocationController.isNearGE()) {
            return null;
        }

        for (Widget itemWidget : itemWidgets) {
            Widget[] children = itemWidget.getDynamicChildren();
            if (children == null) {
                continue;
            }
            Rectangle containerBounds = itemWidget.getBounds();
            boolean clipToContainerBounds = isScrollableBankItemsWidget(itemWidget) && containerBounds != null;

            for (Widget child : children) {
                if (child == null || child.isHidden()) {
                    continue;
                }
                int itemId = child.getItemId();
                int quantity = child.getItemQuantity();
                if (itemId <= 0 || quantity <= 0) {
                    continue;
                }

                int unnotedItemId = itemController.toUnnotedItemId(itemId);
                PortfolioItemCardData itemData = portfolioStateRS.get().getItemCardDataByItemId().get(unnotedItemId);
                boolean inPortfolio = itemData != null && itemData.isInPortfolio();
                if (!inPortfolio) {
                    continue;
                }

                Rectangle bounds = child.getBounds();
                if (bounds == null) {
                    continue;
                }
                if (clipToContainerBounds && !containerBounds.intersects(bounds)) {
                    continue;
                }
                drawPortfolioBadge(graphics, bounds);
            }
        }

        return null;
    }

    private List<Widget> getVisibleItemWidgets() {
        List<Widget> widgets = new ArrayList<>(3);
        Widget geInventory = client.getWidget(GE_INVENTORY_WIDGET_GROUP, GE_INVENTORY_WIDGET_CHILD);
        if (geInventory != null && !geInventory.isHidden()) {
            widgets.add(geInventory);
        }
        Widget inventory = client.getWidget(INVENTORY_WIDGET_GROUP, INVENTORY_WIDGET_CHILD);
        if (inventory != null && !inventory.isHidden()) {
            widgets.add(inventory);
        }
        for (int childId : BANK_ITEM_CONTAINER_CHILDREN) {
            Widget bankItems = client.getWidget(BANK_WIDGET_GROUP, childId);
            if (bankItems != null && !bankItems.isHidden()) {
                widgets.add(bankItems);
            }
        }
        Widget bankInventory = client.getWidget(BANK_INVENTORY_WIDGET_GROUP, BANK_INVENTORY_WIDGET_CHILD);
        if (bankInventory != null && !bankInventory.isHidden()) {
            widgets.add(bankInventory);
        }
        return widgets;
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

    private boolean isScrollableBankItemsWidget(Widget widget) {
        if (widget == null || (widget.getId() >>> 16) != BANK_WIDGET_GROUP) {
            return false;
        }
        int childId = widget.getId() & 0xFFFF;
        return childId == 12 || childId == 13;
    }

    private static BufferedImage loadBadgeIcon() {
        BufferedImage icon = ImageUtil.loadImageResource(InventoryPortfolioBadgeOverlay.class, "/icon-small.png");
        if (icon == null) {
            return null;
        }
        return ImageUtil.resizeImage(icon, BADGE_SIZE, BADGE_SIZE);
    }
}
