package com.flippingcopilot.controller;

import com.flippingcopilot.model.OfferStatus;
import com.flippingcopilot.model.OsrsLoginManager;
import com.flippingcopilot.model.Transaction;
import com.flippingcopilot.model.TransactionManager;
import com.flippingcopilot.ui.GeAddTransactionsDialogPanel;
import com.flippingcopilot.ui.GeHistoryTransactionButton;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts data from the widgets in the trade history tab so that the TradeHistoryTabPanel can display them. I wanted
 * the TradeHistoryTabPanel, and all UI components, to pretty much only draw stuff and not do much else, hence the separation.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GeHistoryTabController {

    private static Pattern MULTI_ITEM_PATTERN = Pattern.compile(">= (.*) each");
    private static Pattern SINGLE_ITEM_PATTERN = Pattern.compile(">(.*) coin");
    private static Pattern ORIGINAL_PRICE_PATTERN = Pattern.compile("\\((.*) -");

    // dependencies
    private final GeHistoryTransactionButton geHistoryTransactionButton;
    private final Client client;
    private final ApiRequestHandler apiRequestHandler;
    private final ItemController itemController;
    private final OsrsLoginManager osrsLoginManager;
    private final TransactionManager transactionManager;

    // dialog
    private JDialog dialog;

    public void onGeHistoryTabClosed() {
        SwingUtilities.invokeLater(() -> {
            geHistoryTransactionButton.setVisible(false);
            if (dialog != null) {
                dialog.dispose();
            }
        });
    }

    public void onGeHistoryTabOpened() {
        geHistoryTransactionButton.addActionListener(e -> {
            Widget w = client.getWidget(383, 3);
            if( w != null) {
                Widget[] widgets = w.getDynamicChildren();
                List<List<Widget>> groupsOfWidgets = Lists.partition(Arrays.asList(widgets), 6);
                List<Transaction> historyTransactions = groupsOfWidgets.stream().map(this::createTransaction).collect(Collectors.toList());
                historyTransactions.forEach(tx -> log.debug("ge history transaction: {}", tx));
                openAddTransactionsDialog(historyTransactions);
            }
        });
        geHistoryTransactionButton.setVisible(true);
    }

    private void openAddTransactionsDialog(List<Transaction> geTransactions) {
        SwingUtilities.invokeLater(() -> {
            // Get the parent frame
            if(dialog != null) {
                dialog.dispose();
            }
            dialog = new JDialog();
            dialog.setTitle("Add GE History Transactions");
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setModal(false);

            // Create the panel
            GeAddTransactionsDialogPanel panel = new GeAddTransactionsDialogPanel(
                    apiRequestHandler,
                    itemController,
                    osrsLoginManager.getPlayerDisplayName(),
                    transactionManager,
                    dialog::dispose,
                    geTransactions
            );

            dialog.setContentPane(panel);
            dialog.pack();
            dialog.setVisible(true);
        });
    }

    public Transaction createTransaction(List<Widget> widgets) {
        GrandExchangeOfferState offerState = getState(widgets.get(2));
        int quantity = widgets.get(4).getItemQuantity();
        int itemId = widgets.get(4).getItemId();
        int price = getPrice(widgets.get(5), quantity);
        boolean isBuy = offerState == GrandExchangeOfferState.BOUGHT;

        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setType(isBuy ? OfferStatus.BUY : OfferStatus.SELL);
        t.setItemId(itemId);
        t.setPrice(price);
        t.setQuantity(quantity);
        t.setBoxId(0);
        t.setAmountSpent(price * quantity);
        t.setTimestamp(Instant.now());
        t.setCopilotPriceUsed(true);
        t.setWasCopilotSuggestion(true);
        t.setOfferTotalQuantity(quantity);
        return t;
    }


    /**
     * Gets original price of each item (price before ge tax) from the ge history. We want
     * to get the price before ge tax bc the the price after ge tax is always calculated by the
     * OfferEvent class wherever we need to display it. That means that if we get the price after tax
     * here and then construct an OfferEvent with that price, and then try to display the OfferEvent's price
     * using OfferEvent.getPrice(), we will effectively be applying the ge tax twice.
     * @return the original price of each item in the offer
     */
    private static int getPrice(Widget w, int quantity) {
        String text = w.getText();
        String numString = text;
        Matcher m;
        boolean isTotalPrice = false;
        // This will trigger anytime there is the ge tax text on an offer in the history
        // For example when you have that text with the format "({original price} - {ge_tax})
        // Whenever that text is present, we want to get the original price (NOT THE PRICE
        // AFTER THE TAX). However, since the original price displayed is not the price of each item
        //we have to divide it by the quantity to get the original price of each item.
        if (text.contains(")</col>")) {
            m = ORIGINAL_PRICE_PATTERN.matcher(text);
            isTotalPrice = true;
        }
        //if the case above doesn't trigger that means there is no tax on the item, so we check if "each"
        //is present which allows us to use the regex for getting the price of each item when multiple
        //have been traded.
        else if (text.contains("each")) {
            m = MULTI_ITEM_PATTERN.matcher(text);
        }
        //if this case triggers than it is an offer for a single item that is untaxed
        else {
            m = SINGLE_ITEM_PATTERN.matcher(text);
        }
        m.find();
        numString = m.group(1);
        StringBuilder s = new StringBuilder();
        for (char c : numString.toCharArray()) {
            if (Character.isDigit(c)) {
                s.append(c);
            }
        }

        int price = Integer.parseInt(s.toString());
        if (isTotalPrice) {
            return price/quantity;
        }
        return price;
    }

    private static GrandExchangeOfferState getState(Widget w) {
        String text = w.getText();
        if (text.startsWith("Bought")) {
            return GrandExchangeOfferState.BOUGHT;
        } else {
            return GrandExchangeOfferState.SOLD;
        }
    }
}
