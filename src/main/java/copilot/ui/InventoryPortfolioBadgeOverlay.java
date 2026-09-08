package copilot.ui;

import copilot.controller.*;
import copilot.config.*;
import copilot.rs.*;
import lombok.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.*;

import javax.inject.*;
import java.awt.*;
import java.awt.image.*;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventoryPortfolioBadgeOverlay extends WidgetItemOverlay {

    private static final int INVENTORY_WIDGET_GROUP = 149, GE_INVENTORY_WIDGET_GROUP = 467;
    private static final int BANK_INVENTORY_WIDGET_GROUP = 15, BADGE_SIZE = 12, BADGE_MARGIN = 1;
    private static final BufferedImage BADGE_ICON = ImageUtil.resizeImage(
            ImageUtil.loadImageResource(InventoryPortfolioBadgeOverlay.class, "/icon-small.png"), BADGE_SIZE, BADGE_SIZE);

    private final CopilotConfig config;
    private final Items items;
    private final PortfolioStateRS portfolio;
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
        if (!config.portfolioIcons() || !portfolio.get().loaded || !playerLocationController.isNearGE()) {
            return null;
        }

        return super.render(graphics);
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
        if (itemId <= 0 || widgetItem.getQuantity() <= 0) { return; }

        int unnotedItemId = items.toUnnotedItemId(itemId);
        var itemData = portfolio.get().itemCardDataByItemId.get(unnotedItemId);
        if (itemData == null || !itemData.isInPortfolio()) { return; }

        Widget widget = widgetItem.getWidget();
        if (isBankItemWidget(widget) && !itemData.hasPortfolioQuantityInBank()) { return; }

        Rectangle bounds = widget == null ? widgetItem.getCanvasBounds() : widget.getBounds();
        if (bounds == null) { return; }

        graphics.drawImage(BADGE_ICON,
                bounds.x + bounds.width - BADGE_SIZE - BADGE_MARGIN,
                bounds.y + bounds.height - BADGE_SIZE - BADGE_MARGIN,
                null);
    }

    private boolean isBankItemWidget(Widget widget) {
        if (widget == null) { return false; }

        Widget parent = widget.getParent();
        int parentId = parent == null ? widget.getParentId() : parent.getId();
        return WidgetUtil.componentToInterface(parentId) == InterfaceID.BANKMAIN;
    }
}
