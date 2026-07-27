package com.flippingcopilot.ui;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.InventorySlotTooltipDataProvider;
import com.flippingcopilot.controller.PlayerLocationController;
import com.flippingcopilot.model.InventorySlotTooltipData;
import com.flippingcopilot.model.TooltipHoverSource;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventorySlotTooltipOverlay extends Overlay {

    private static final int INVENTORY_WIDGET_GROUP = 149;
    private static final int INVENTORY_WIDGET_CHILD = 0;
    private static final int GE_INVENTORY_WIDGET_GROUP = 467;
    private static final int GE_INVENTORY_WIDGET_CHILD = 0;
    private static final int BANK_WIDGET_GROUP = 12;
    private static final int[] BANK_ITEM_CONTAINER_CHILDREN = {12, 13, 89};
    private static final int BANK_INVENTORY_WIDGET_GROUP = 15;
    private static final int BANK_INVENTORY_WIDGET_CHILD = 3;

    private static final String UNREALISED_PROFIT_PREFIX = "Unrealized Profit: ";
    private static final String UNREALISED_ROI_PREFIX = "Unrealized ROI: ";
    private static final String POSITIVE_COLOR = "<col=50dc78>";
    private static final String NEGATIVE_COLOR = "<col=e65a5a>";
    private static final String COLOR_END = "</col>";

    private final Client client;
    private final InventorySlotTooltipDataProvider tooltipDataProvider;
    private final TooltipManager tooltipManager;
    private final PlayerLocationController playerLocationController;
    private final FlippingCopilotConfig config;

    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.portfolioTooltips()) {
            return null;
        }
        if (!playerLocationController.isNearGE()) {
            return null;
        }

        InventorySlotTooltipData tooltipData = findHoveredSlotTooltipData();
        if (tooltipData == null) {
            return null;
        }

        String tooltipText = buildTooltipText(tooltipData.getLines());
        if (tooltipText != null && !tooltipText.isEmpty()) {
            tooltipManager.add(new Tooltip(tooltipText));
        }
        return null;
    }

    private InventorySlotTooltipData findHoveredSlotTooltipData() {
        Point mousePos = client.getMouseCanvasPosition();
        if (mousePos == null) {
            return null;
        }

        for (SourcedWidget sourcedWidget : getVisibleItemWidgets()) {
            Widget[] children = sourcedWidget.widget.getDynamicChildren();
            if (children == null) {
                continue;
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

                return tooltipDataProvider.getTooltipData(itemId, quantity, sourcedWidget.source);
            }
        }

        return null;
    }

    private List<SourcedWidget> getVisibleItemWidgets() {
        List<SourcedWidget> widgets = new ArrayList<>(3);
        Widget geInventory = client.getWidget(GE_INVENTORY_WIDGET_GROUP, GE_INVENTORY_WIDGET_CHILD);
        if (geInventory != null && !geInventory.isHidden()) {
            widgets.add(new SourcedWidget(geInventory, TooltipHoverSource.INVENTORY));
        }

        Widget inventory = client.getWidget(INVENTORY_WIDGET_GROUP, INVENTORY_WIDGET_CHILD);
        if (inventory != null && !inventory.isHidden()) {
            widgets.add(new SourcedWidget(inventory, TooltipHoverSource.INVENTORY));
        }

        for (int childId : BANK_ITEM_CONTAINER_CHILDREN) {
            Widget bankItems = client.getWidget(BANK_WIDGET_GROUP, childId);
            if (bankItems != null && !bankItems.isHidden()) {
                widgets.add(new SourcedWidget(bankItems, TooltipHoverSource.BANK));
            }
        }

        Widget bankInventory = client.getWidget(BANK_INVENTORY_WIDGET_GROUP, BANK_INVENTORY_WIDGET_CHILD);
        if (bankInventory != null && !bankInventory.isHidden()) {
            widgets.add(new SourcedWidget(bankInventory, TooltipHoverSource.INVENTORY));
        }

        return widgets;
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
        for (String prefix : new String[]{UNREALISED_PROFIT_PREFIX, UNREALISED_ROI_PREFIX}) {
            String formatted = formatSignedValueLine(line, prefix);
            if (formatted != null) {
                return formatted;
            }
        }

        return line == null ? "" : line;
    }

    private String formatSignedValueLine(String line, String prefix) {
        if (line == null || !line.startsWith(prefix)) {
            return null;
        }

        String value = line.substring(prefix.length());
        if ("Unknown".equals(value)) {
            return line;
        }

        String color = value.startsWith("-") ? NEGATIVE_COLOR : POSITIVE_COLOR;
        return prefix + color + value + COLOR_END;
    }

    @RequiredArgsConstructor
    private static class SourcedWidget {
        private final Widget widget;
        private final TooltipHoverSource source;
    }
}
