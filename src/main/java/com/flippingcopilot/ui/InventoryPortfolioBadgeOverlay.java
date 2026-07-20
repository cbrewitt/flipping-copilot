package com.flippingcopilot.ui;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.controller.PlayerLocationController;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.rs.PortfolioStateRS;
import lombok.RequiredArgsConstructor;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventoryPortfolioBadgeOverlay extends WidgetItemOverlay {

    private static final int INVENTORY_WIDGET_GROUP = 149;
    private static final int GE_INVENTORY_WIDGET_GROUP = 467;
    private static final int BANK_INVENTORY_WIDGET_GROUP = 15;
    private static final Color BADGE_BG = new Color(30, 60, 35, 220);
    private static final Color BADGE_BORDER = new Color(120, 230, 150);
    private static final Color BADGE_TEXT = Color.WHITE;
    private static final int BADGE_SIZE = 12;
    private static final int BADGE_MARGIN = 1;
    private static final BufferedImage BADGE_ICON = loadBadgeIcon();

    private final FlippingCopilotConfig config;
    private final ItemController itemController;
    private final PortfolioStateRS portfolioStateRS;
    private final PlayerLocationController playerLocationController;

    {
        // WidgetItemOverlay renders on manual widget hooks, so interfaces drawn later
        // (such as the world map) naturally cover these badges.
        showOnInterfaces(INVENTORY_WIDGET_GROUP, GE_INVENTORY_WIDGET_GROUP, BANK_INVENTORY_WIDGET_GROUP);
        showOnBank();
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.portfolioIcons()
                || !portfolioStateRS.get().isLoaded()
                || !playerLocationController.isNearGE()) {
            return null;
        }

        return super.render(graphics);
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
        if (itemId <= 0 || widgetItem.getQuantity() <= 0) {
            return;
        }

        int unnotedItemId = itemController.toUnnotedItemId(itemId);
        PortfolioItemCardData itemData = portfolioStateRS.get().getItemCardDataByItemId().get(unnotedItemId);
        if (itemData == null || !itemData.isInPortfolio()) {
            return;
        }

        Widget widget = widgetItem.getWidget();
        Rectangle bounds = widget == null ? widgetItem.getCanvasBounds() : widget.getBounds();
        if (bounds == null) {
            return;
        }

        drawPortfolioBadge(graphics, bounds);
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
        graphics.setFont(originalFont.deriveFont(Font.BOLD, Math.max(8f, BADGE_SIZE - 4f)));
        graphics.setColor(BADGE_TEXT);
        graphics.drawString("P", x + 2, y + BADGE_SIZE - 4);
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
