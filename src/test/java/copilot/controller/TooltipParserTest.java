package copilot.controller;

import org.junit.Test;
import static org.junit.Assert.*;

public class TooltipParserTest {
    @Test public void matchesThePriorTwoPassParser() {
        ReferenceTooltipParser old = new ReferenceTooltipParser();
        String[] actions = {"Selling", "Buying", "selling", "Other"};
        String[] names = {"Rune platebody", "A 1 / 2", "", "a<br>b", "a\nb", "<col=ff00ff>Item</col>", "Rune's sword"};
        String[] quantities = {"1 / 2", "1,000 / 2,000", "0 / 0", "01 / 2", "1,23 / 2", "1 / -2", "1 / 2,000,000"};
        String[] endings = {"", " ", " Profit: 10 gp", " Profit: -1,000 gp", " Profit: 10 g", " Profit: 10", "\n"};
        for (String action : actions) {
            for (String name : names) {
                for (String quantity : quantities) {
                    for (String end : endings) {
                        String text = action + ": " + name + " " + quantity + end;
                        String expected = old.isItemSelling(text) ? old.getItemNameFromTooltipText(text) : null;
                        assertEquals(text, expected, TooltipController.sellingItem(text));
                    }
                }
            }
        }
    }
}
