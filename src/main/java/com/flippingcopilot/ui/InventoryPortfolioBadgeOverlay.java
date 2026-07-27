package com.flippingcopilot.ui;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.controller.PlayerLocationController;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.rs.PortfolioStateRS;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.*;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.awt.image.BufferedImage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventoryPortfolioBadgeOverlay extends WidgetItemOverlay {

    private static final int INVENTORY_WIDGET_GROUP = 149;
    private static final int GE_INVENTORY_WIDGET_GROUP = 467;
    private static final int BANK_INVENTORY_WIDGET_GROUP = 15;
    private static final int BADGE_SIZE = 12;
    private static final int BADGE_MARGIN = 1;
    private static final BufferedImage BADGE_ICON = ImageUtil.resizeImage(
            ImageUtil.loadImageResource(InventoryPortfolioBadgeOverlay.class, "/icon-small.png"), BADGE_SIZE, BADGE_SIZE);

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
        if (isBankItemWidget(widget) && !itemData.hasPortfolioQuantityInBank()) {
            return;
        }

        Rectangle bounds = widget == null ? widgetItem.getCanvasBounds() : widget.getBounds();
        if (bounds == null) {
            return;
        }

        graphics.drawImage(BADGE_ICON,
                bounds.x + bounds.width - BADGE_SIZE - BADGE_MARGIN,
                bounds.y + bounds.height - BADGE_SIZE - BADGE_MARGIN,
                null);
    }

    private boolean isBankItemWidget(Widget widget) {
        if (widget == null) {
            return false;
        }

        Widget parent = widget.getParent();
        int parentId = parent == null ? widget.getParentId() : parent.getId();
        return WidgetUtil.componentToInterface(parentId) == InterfaceID.BANKMAIN;
    }
}
