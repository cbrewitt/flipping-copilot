package com.flippingcopilot.ui;

import com.flippingcopilot.config.FlippingCopilotConfig;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

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
public class PortfolioBankTabBadgeOverlay extends Overlay {

    private static final String PORTFOLIO_BANK_TAG = "portfolio";
    private static final int BANK_TAG_TAB_CHILD_OFFSET = 4;
    private static final Color BADGE_BG = new Color(30, 60, 35, 220);
    private static final Color BADGE_BORDER = new Color(120, 230, 150);
    private static final Color BADGE_TEXT = Color.WHITE;
    private static final int BADGE_SIZE = 16;
    private static final int BADGE_MARGIN = 1;
    private static final BufferedImage BADGE_ICON = loadBadgeIcon();

    private final Client client;
    private final FlippingCopilotConfig config;
    private final PluginManager pluginManager;
    private final BankTagsPlugin bankTagsPlugin;

    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.MANUAL);
        setPriority(PRIORITY_LOW);
        // Keep the badge in the bank's draw order so later modal interfaces cover it.
        drawAfterLayer(InterfaceID.Bankmain.ITEMS_CONTAINER);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.portfolioBankTag() || !pluginManager.isPluginActive(bankTagsPlugin)) {
            return null;
        }

        Widget tabIcon = getPortfolioBankTabIcon();
        if (tabIcon == null) {
            return null;
        }

        Rectangle bounds = tabIcon.getBounds();
        if (bounds == null) {
            return null;
        }

        drawBadge(graphics, bounds);
        return null;
    }

    private Widget getPortfolioBankTabIcon() {
        Widget parent = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
        if (parent == null || parent.isHidden() || parent.getChildren() == null) {
            return null;
        }

        Widget[] children = parent.getChildren();
        for (int i = BANK_TAG_TAB_CHILD_OFFSET + 1; i < children.length; i += 2) {
            Widget icon = children[i];
            if (icon == null || icon.isHidden()) {
                continue;
            }

            String widgetName = icon.getName();
            if (widgetName != null && PORTFOLIO_BANK_TAG.equals(Text.removeTags(widgetName))) {
                return icon;
            }
        }
        return null;
    }

    private void drawBadge(Graphics2D graphics, Rectangle slotBounds) {
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
        BufferedImage icon = ImageUtil.loadImageResource(PortfolioBankTabBadgeOverlay.class, "/icon-small.png");
        if (icon == null) {
            return null;
        }
        return ImageUtil.resizeImage(icon, BADGE_SIZE, BADGE_SIZE);
    }
}
