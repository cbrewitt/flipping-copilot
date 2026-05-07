package com.flippingcopilot.rs;

import com.flippingcopilot.model.GeHistoryRow;
import com.flippingcopilot.model.GeHistoryState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
@Slf4j
public class GeHistoryStateRS extends ReactiveStateImpl<GeHistoryState> {

    private static final int GE_HISTORY_GROUP = 383;
    private static final int GE_HISTORY_LIST_CHILD = 3;

    private static final Pattern MULTI_ITEM_PATTERN = Pattern.compile(">= (.*) each");
    private static final Pattern SINGLE_ITEM_PATTERN = Pattern.compile(">(.*) coin");
    private static final Pattern ORIGINAL_PRICE_PATTERN = Pattern.compile("\\((.*) -");

    private final OsrsLoginRS osrsLoginRS;
    private boolean lastSeenVisible = false;
    private int sessionOpenedAt = 0;

    @Inject
    public GeHistoryStateRS(OsrsLoginRS osrsLoginRS) {
        super(GeHistoryState.empty());
        this.osrsLoginRS = osrsLoginRS;
        osrsLoginRS.registerListener(state -> {
            if (state == null || !state.loggedIn) {
                lastSeenVisible = false;
                set(GeHistoryState.empty());
                return;
            }
            Long loaded = get().getAccountHash();
            if (loaded != null && !Objects.equals(loaded, state.accountHash)) {
                lastSeenVisible = false;
                set(GeHistoryState.empty());
            }
        });
    }

    public void onGameTick(Client client) {
        if (!osrsLoginRS.get().loggedIn) {
            return;
        }
        Widget container = client.getWidget(GE_HISTORY_GROUP, GE_HISTORY_LIST_CHILD);
        boolean visible = container != null;
        if (visible && !lastSeenVisible) {
            sessionOpenedAt = (int) Instant.now().getEpochSecond();
        }
        lastSeenVisible = visible;
        if (!visible) {
            return;
        }
        List<GeHistoryRow> rows = parseRows(container);
        if (rows.isEmpty()) {
            return;
        }
        Long accountHash = osrsLoginRS.get().accountHash;
        GeHistoryState current = get();
        if (current.isLoaded()
                && current.getCapturedAt() == sessionOpenedAt
                && Objects.equals(current.getAccountHash(), accountHash)
                && rows.equals(current.getRows())) {
            return;
        }
        set(new GeHistoryState(true, Collections.unmodifiableList(rows), sessionOpenedAt, accountHash));
    }

    private static List<GeHistoryRow> parseRows(Widget container) {
        Widget[] children = container.getDynamicChildren();
        if (children == null || children.length < 6) {
            return Collections.emptyList();
        }
        List<GeHistoryRow> rows = new ArrayList<>(children.length / 6);
        for (int i = 0; i + 5 < children.length; i += 6) {
            try {
                Widget stateW = children[i + 2];
                Widget itemW = children[i + 4];
                Widget priceW = children[i + 5];
                String stateText = stateW.getText();
                if (stateText == null || stateText.isEmpty()) {
                    continue;
                }
                int itemId = itemW.getItemId();
                int quantity = itemW.getItemQuantity();
                if (itemId <= 0 || quantity <= 0) {
                    continue;
                }
                int price = parsePrice(priceW.getText(), quantity);
                if (price <= 0) {
                    continue;
                }
                boolean isBuy = stateText.startsWith("Bought");
                rows.add(new GeHistoryRow(itemId, quantity, price, isBuy));
            } catch (Exception e) {
                log.debug("failed to parse GE history row at offset {}", i, e);
            }
        }
        return rows;
    }

    private static int parsePrice(String text, int quantity) {
        if (text == null) {
            return 0;
        }
        Matcher m;
        boolean isTotalPrice = false;
        if (text.contains(")</col>")) {
            m = ORIGINAL_PRICE_PATTERN.matcher(text);
            isTotalPrice = true;
        } else if (text.contains("each")) {
            m = MULTI_ITEM_PATTERN.matcher(text);
        } else {
            m = SINGLE_ITEM_PATTERN.matcher(text);
        }
        if (!m.find()) {
            return 0;
        }
        StringBuilder s = new StringBuilder();
        for (char c : m.group(1).toCharArray()) {
            if (Character.isDigit(c)) {
                s.append(c);
            }
        }
        if (s.length() == 0) {
            return 0;
        }
        int price = Integer.parseInt(s.toString());
        if (isTotalPrice && quantity > 0) {
            return price / quantity;
        }
        return price;
    }
}
