package com.flippingcopilot.controller;

import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.UIUtilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontTypeFace;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.flippingcopilot.model.OfferStatus.SELL;
import static com.flippingcopilot.util.GeTax.getPostTaxPrice;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TooltipController {
    private static final int SCRIPT_TOOLTIP_GE = 526;

    private static final int WIDTH_PADDING = 4;

    private final Client client;
    private final OfferManager offerManager;
    private final FlipManager flipManager;
    private final OsrsLoginManager osrsLoginManager;
    private final CopilotLoginManager copilotLoginManager;

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
                long profit = getProfitFromItemName(name);

                text.setText(text.getText() + "<br>Profit: " + UIUtilities.quantityToRSDecimalStack(profit, false) + " gp");
                recomputeTooltipSize(tooltip, text);

                tooltip.revalidate();
                background.revalidate();
                border.revalidate();
                text.revalidate();
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

    public long getProfitFromItemName(String itemName) {
        String displayName = osrsLoginManager.getPlayerDisplayName();

        for(int i = 0; i < 8; i++) {
            long accountHash = client.getAccountHash();
            SavedOffer offer = offerManager.loadOffer(accountHash, i);

            if(offer.getOfferStatus().equals(SELL)) {
                Integer accountId = copilotLoginManager.getAccountId(displayName);
                if(accountId != null && accountId != -1) {
                    FlipV2 flip = flipManager.getLastFlipByItemId(accountId, offer.getItemId());

                    if (flip == null || FlipStatus.FINISHED.equals(flip.getStatus())) {
                        continue;
                    }

                    if(flip.getCachedItemName().equals(itemName)) {
                        return ((long) getPostTaxPrice(offer.getItemId(), offer.getPrice()) * offer.getTotalQuantity()) - (flip.getAvgBuyPrice() * offer.getTotalQuantity());
                    }
                }
            }
        }

        return 0;
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

    private void recomputeTooltipSize(Widget tooltip, Widget textWidget) {
        final String[] lines = textWidget.getText().split("<br>");

        tooltip.setOriginalWidth(calculateTooltipWidth(textWidget.getFont(), lines));
        tooltip.setOriginalHeight(calculateTooltipHeight(lines));
    }

    private int calculateTooltipWidth(FontTypeFace fontTypeFace, String[] lines) {
        double maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, fontTypeFace.getTextWidth(line));
        }

        return (int) Math.ceil(maxWidth) + WIDTH_PADDING;
    }

    private int calculateTooltipHeight(String[] lines) {
        // 12 = FontID.PLAIN_12 = FontID 495 = what's used by the tooltip text
        // 6 = 30 (default tooltip height consisting of 2 lines) - 24 (FontID.PLAIN_12 * 2 lines)
        return (12 * lines.length) + 6;
    }
}
