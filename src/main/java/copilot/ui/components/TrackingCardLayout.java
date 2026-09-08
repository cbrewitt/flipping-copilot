package copilot.ui.components;

import java.awt.*;

import java.util.*;

public class TrackingCardLayout extends CardLayout {

    private final LinkedList<String> cardHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 20;

    @Override
    public void show(Container parent, String name) {
        cardHistory.addFirst(name);
        if (cardHistory.size() > MAX_HISTORY_SIZE) { cardHistory.removeLast(); }
        super.show(parent, name);
    }

    public void showPrevious(Container parent) {
        if (cardHistory.size() > 1) {
            cardHistory.removeFirst(); // Remove current card
            String previousCard = cardHistory.getFirst();
            super.show(parent, previousCard);
        }
    }

    public String getCurrentCard() { return cardHistory.isEmpty() ? null : cardHistory.getFirst(); }
}
