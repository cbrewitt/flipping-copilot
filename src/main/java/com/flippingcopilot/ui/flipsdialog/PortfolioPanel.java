package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.PortfolioController;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.model.PortfolioSummaryData;
import com.flippingcopilot.rs.OsrsLoginRS;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PortfolioPanel extends JPanel {
    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final String CONTENT_CARD = "content";
    private static final String LOGIN_PROMPT_CARD = "login";

    private final PortfolioController portfolioController;
    private final ItemController itemController;
    private final FlippingCopilotConfig config;
    private final OsrsLoginRS osrsLoginRS;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private final JPanel summaryTablePanel;
    private final JPanel cardsListPanel;
    private final ClientThread clientThread;

    public PortfolioPanel(PortfolioController portfolioController,
                          ItemController itemController,
                          FlippingCopilotConfig config,
                          OsrsLoginRS osrsLoginRs,
                          ClientThread clientThread) {
        this.portfolioController = portfolioController;
        this.itemController = itemController;
        this.config = config;
        this.osrsLoginRS = osrsLoginRs;
        this.clientThread = clientThread;

        setLayout(new BorderLayout(0, 12));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 12));
        contentPanel.setOpaque(false);

        JPanel summarySection = new JPanel(new BorderLayout(20, 0));
        summarySection.setOpaque(false);
        summarySection.setBorder(new EmptyBorder(0, 0, 10, 0));

        summaryTablePanel = new JPanel(new GridLayout(4, 2, 24, 12));
        summaryTablePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        summaryTablePanel.setBorder(new EmptyBorder(16, 16, 16, 16));
        summarySection.add(summaryTablePanel, BorderLayout.WEST);

        JPanel emptyRightPanel = new JPanel();
        emptyRightPanel.setOpaque(false);
        summarySection.add(emptyRightPanel, BorderLayout.CENTER);

        contentPanel.add(summarySection, BorderLayout.NORTH);

        cardsListPanel = new JPanel();
        cardsListPanel.setOpaque(false);
        cardsListPanel.setLayout(new BoxLayout(cardsListPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(cardsListPanel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        cardPanel.add(contentPanel, CONTENT_CARD);
        cardPanel.add(buildLoginPromptPanel(), LOGIN_PROMPT_CARD);
        add(cardPanel, BorderLayout.CENTER);

        refresh();
    }

    public void onTabShown() {
        refresh();
    }

    private void refresh() {
        if (!osrsLoginRS.get().loggedIn) {
            cardLayout.show(cardPanel, LOGIN_PROMPT_CARD);
            revalidate();
            repaint();
            return;
        }
        cardLayout.show(cardPanel, CONTENT_CARD);
        clientThread.invokeLater(() -> {
            List<PortfolioItemCardData> items = portfolioController.getPortfolioItems();
            List<PortfolioItemCardData> inPortfolioItems = filterInPortfolioItems(items);
            PortfolioSummaryData sm = portfolioController.buildPortfolioSummaryData(inPortfolioItems);
            SwingUtilities.invokeLater(() -> {
                renderSummary(sm);
                renderCards(inPortfolioItems);
                revalidate();
                repaint();
            });
        });
    }

    private List<PortfolioItemCardData> filterInPortfolioItems(List<PortfolioItemCardData> items) {
        List<PortfolioItemCardData> filteredItems = new ArrayList<>();
        for (PortfolioItemCardData item : items) {
            if (item.isInPortfolio()) {
                filteredItems.add(item);
            }
        }
        return filteredItems;
    }

    private void renderSummary(PortfolioSummaryData data) {
        summaryTablePanel.removeAll();
        if (data == null) {
            return;
        }
        addSummaryRow("Portfolio Market Value", formatGp(data.getPortfolioMarketValue(), false), Color.WHITE);
        addSummaryRow("Unrealised PNL", formatGp(data.getUnrealizedPnl(), true), getPnlColor(data.getUnrealizedPnl()));
        addSummaryRow("Cash Value", formatGp(data.getCashValue(), false), Color.WHITE);
        addSummaryRow("Assets Value", formatGp(data.getAssetsValue(), false), Color.WHITE);
    }

    private void addSummaryRow(String label, String value, Color valueColor) {
        JLabel keyLabel = new JLabel(label);
        keyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        keyLabel.setFont(keyLabel.getFont().deriveFont(19f));

        JLabel valueLabel = new JLabel(value, SwingConstants.RIGHT);
        valueLabel.setForeground(valueColor);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 19f));

        summaryTablePanel.add(keyLabel);
        summaryTablePanel.add(valueLabel);
    }

    private void renderCards(List<PortfolioItemCardData> items) {
        cardsListPanel.removeAll();

        if (items.isEmpty()) {
            JLabel emptyLabel = new JLabel("No portfolio items to show.");
            emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(15f));
            emptyLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            cardsListPanel.add(emptyLabel);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            PortfolioItemCard card = new PortfolioItemCard(items.get(i), itemController, config);
            cardsListPanel.add(card);
            if (i < items.size() - 1) {
                cardsListPanel.add(Box.createVerticalStrut(8));
            }
        }
    }

    private JPanel buildLoginPromptPanel() {
        JPanel loginPromptPanel = new JPanel(new GridBagLayout());
        loginPromptPanel.setOpaque(false);
        JLabel messageLabel = new JLabel("Log into the game to see account portfolio");
        messageLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        messageLabel.setFont(messageLabel.getFont().deriveFont(18f));
        loginPromptPanel.add(messageLabel);
        return loginPromptPanel;
    }

    private Color getPnlColor(long pnl) {
        if (pnl > 0) {
            return config.profitAmountColor();
        }
        if (pnl < 0) {
            return config.lossAmountColor();
        }
        return Color.WHITE;
    }

    private String formatGp(long amount, boolean signed) {
        String prefix = signed && amount > 0 ? "+" : "";
        return prefix + GP_FORMAT.format(amount) + " gp";
    }
}
