package com.flippingcopilot.controller;

import com.flippingcopilot.ui.UIUtilities;
import com.flippingcopilot.util.ProfitCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;

import java.awt.*;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TooltipController {
    private static final int SCRIPT_TOOLTIP_GE = 526;
    private static final int TOOLTIP_HEIGHT_WITH_PROFIT = 45;

    private static final FontMetrics FONT_METRICS = Toolkit.getDefaultToolkit().getFontMetrics(FontManager.getRunescapeFont());
    private static final int WIDTH_PADDING = 4;

    private final Client client;
    private final ProfitCalculator profitCalculator;

    public void tooltip(ScriptPostFired e) {
        if(e.getScriptId() != SCRIPT_TOOLTIP_GE) {
            return;
        }

        Widget tooltip = client.getWidget(InterfaceID.GeOffers.TOOLTIP);

        if(tooltip == null || tooltip.isHidden()) {
            return;
        }

        Widget background = tooltip.getChild(0);
        Widget border = tooltip.getChild(1);
        Widget text = tooltip.getChild(2);

        if (text != null && background != null && border != null) {
            if (text.getText().contains("Profit:")) {
                // If the tooltip already contains profit information, we don't need to process it again
                return;
            }

            if(!isItemSelling(text.getText())) {
                return;
            }

            String name = getItemNameFromTooltipText(text.getText());

            if(name != null) {
                long profit = profitCalculator.getProfitByItemName(name);
                text.setText(text.getText()  + "<br>Profit: " + UIUtilities.quantityToRSDecimalStack(profit, false) + " gp");

                tooltip.setOriginalHeight(TOOLTIP_HEIGHT_WITH_PROFIT);
                text.setOriginalHeight(TOOLTIP_HEIGHT_WITH_PROFIT);

                int width = calculateTooltipWidth(text.getText());
                tooltip.setOriginalWidth(width);
                text.setOriginalWidth(width);

                tooltip.revalidate();
                border.revalidate();
                background.revalidate();
            }
        }
    }

    public boolean isItemSelling(String text) {
        text = text.replaceAll("<br>", " ").trim();
        Pattern pattern = Pattern.compile("^(Buying|Selling): (.+) (\\d{1,3}(?:,\\d{3})*|\\d+) / (\\d{1,3}(?:,\\d{3})*|\\d+)( Profit: -?[\\d,]+ gp?)?$");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String action = matcher.group(1);
            return action.equals("Selling");
        } else {
            return false; // or handle as you wish
        }
    }

    public String getItemNameFromTooltipText(String text) {
        text = text.replaceAll("<br>", " ").trim();
        Pattern pattern = Pattern.compile("^(Buying|Selling): (.+) (\\d{1,3}(?:,\\d{3})*|\\d+) / (\\d{1,3}(?:,\\d{3})*|\\d+)( Profit: -?[\\d,]+ gp?)?$");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(2);
        } else {
            return null; // or handle as you wish
        }
    }

    private int calculateTooltipWidth(String text)
    {
        final String[] lines = text.split("<br>");
        int maxWidth = 0;
        for (String line : lines) {
            String left = "";
            left = line;

            int width = FONT_METRICS.stringWidth(left);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        return maxWidth + WIDTH_PADDING;
    }
}
