package copilot.controller;

import java.util.regex.*;
import net.runelite.api.*;
import copilot.ui.*;
import copilot.util.*;
import lombok.*;
import lombok.extern.slf4j.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.*;

import javax.inject.*;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TooltipController {
    private static final int SCRIPT_TOOLTIP_GE = 526, TOOLTIP_HEIGHT_WITH_PROFIT = 45;

    private static final int WIDTH_PADDING = 4;
    private static final Pattern TOOLTIP = Pattern.compile("^(Buying|Selling): (.+) (\\d{1,3}(?:,\\d{3})*|\\d+) / (\\d{1,3}(?:,\\d{3})*|\\d+)( Profit: -?[\\d,]+ gp?)?$");

    private final Client client;
    private final ProfitCalculator profitCalculator;

    public void tooltip(ScriptPostFired e) {
        if (e.getScriptId() != SCRIPT_TOOLTIP_GE) { return; }

        Widget tooltip = client.getWidget(InterfaceID.GeOffers.TOOLTIP);

        if (tooltip == null || tooltip.isHidden()) { return; }

        Widget background = tooltip.getChild(0), border = tooltip.getChild(1), text = tooltip.getChild(2);

        if (text != null && background != null && border != null) {

            if (text.getText().contains("Profit:")) {
                // If the tooltip already contains profit information, we don't need to process it again
                return;
            }

            String name = sellingItem(text.getText());

            if(name != null) {
                long profit = profitCalculator.getProfitByItemName(name);
                text.setText(text.getText()  + "<br>Profit: " + UIUtilities.quantityToRSDecimalStack(profit, false) + " gp");
                tooltip.setOriginalHeight(TOOLTIP_HEIGHT_WITH_PROFIT);

                int width = calculateTooltipWidth(text.getFont(), text.getText());
                tooltip.setOriginalWidth(width);

                tooltip.revalidate();
                border.revalidate();
                background.revalidate();
                text.revalidate();
            }
        }
    }

    static String sellingItem(String text) {
        Matcher match = TOOLTIP.matcher(text.replace("<br>", " ").trim());
        return match.find() && match.group(1).equals("Selling") ? match.group(2) : null;
    }

    private int calculateTooltipWidth(FontTypeFace f, String text) {
        final String[] lines = text.split("<br>");
        int maxWidth = 0;
        for (String line : lines) {
            int width = f.getTextWidth(line);
            if (width > maxWidth) { maxWidth = width; }
        }
        return maxWidth + WIDTH_PADDING;
    }
}
