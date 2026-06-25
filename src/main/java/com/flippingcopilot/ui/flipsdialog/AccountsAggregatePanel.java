package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.model.AccountAggregate;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.ui.components.IntervalDropdown;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Named;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

@Slf4j
public class AccountsAggregatePanel extends JPanel {

    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final String[] COLUMN_NAMES = {
            "Account", "Number of flips", "Biggest loss", "Biggest win", "Total profit"
    };

    // dependencies
    private final CopilotLoginRS copilotLoginRS;
    private final ApiRequestHandler apiRequestHandler;
    private final FlipManager flipManager;
    private final ExecutorService executorService;

    // ui components
    private final PaginatedTablePanel<AccountAggregate> tablePanel;

    // state
    private final AccountsAggregateFilterSort sortAndFilter;

    public AccountsAggregatePanel(FlipManager flipsManager,
                                  CopilotLoginRS copilotLoginRS,
                                  @Named("copilotExecutor") ExecutorService executorService,
                                  FlippingCopilotConfig config,
                                  ApiRequestHandler apiRequestHandler,
                                  FlipManager flipManager) {
        this.copilotLoginRS = copilotLoginRS;
        this.apiRequestHandler = apiRequestHandler;
        this.flipManager = flipManager;
        this.executorService = executorService;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Initialize sort and filter
        tablePanel = new PaginatedTablePanel<>(COLUMN_NAMES, this::toRow);
        sortAndFilter = new AccountsAggregateFilterSort(flipsManager, copilotLoginRS,
                tablePanel::setRows, tablePanel::setSpinnerVisible, executorService);

        // Create top panel with all controls
        IntervalDropdown timeIntervalDropdown = new IntervalDropdown(sortAndFilter::setInterval, IntervalDropdown.ALL_TIME, false);
        timeIntervalDropdown.setPreferredSize(new Dimension(150, timeIntervalDropdown.getPreferredSize().height));
        timeIntervalDropdown.setToolTipText("Select time interval");
        tablePanel.leftControls().add(timeIntervalDropdown);

        // Enable built-in table sorting
        tablePanel.enableBuiltInSorting();

        // Apply renderers
        // Center align for count column
        tablePanel.centerColumns(1); // Number of flips

        // Custom renderer for money columns
        tablePanel.moneyColumns(GP_FORMAT, 2, 3); // Biggest loss, Biggest win

        // Custom renderer for profit columns (with color)
        tablePanel.profitColumns(GP_FORMAT, config, 4); // Total profit (with color)
        tablePanel.installPopupHandler(this::showAccountMenu);

        add(tablePanel, BorderLayout.CENTER);
    }

    private Object[] toRow(AccountAggregate aggregate) {
        return new Object[]{
                aggregate.getAccountName(),
                aggregate.getNumberOfFlips(),
                aggregate.getBiggestLoss(),
                aggregate.getBiggestWin(),
                aggregate.getTotalProfit(),
        };
    }

    private void showAccountMenu(MouseEvent e, int row) {
        AccountAggregate account = tablePanel.row(row);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Delete account");
        deleteItem.addActionListener(evt -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete " + account.getAccountName() + "?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                tablePanel.setSpinnerVisible(true);
                log.info("Deleting account: {}", account.getAccountId());
                Runnable onSuccess = () -> {
                    copilotLoginRS.removeAccount(account.getAccountId());
                    executorService.submit(() -> flipManager.deleteAccount(account.getAccountId()));
                    tablePanel.setSpinnerVisible(false);
                    sortAndFilter.reloadAggregates(true);
                };
                apiRequestHandler.asyncDeleteAccount(account.getAccountId(), onSuccess, () -> tablePanel.setSpinnerVisible(false));
            }
        });
        menu.add(deleteItem);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    public void onTabShown() {
        sortAndFilter.reloadAggregates(true);
    }
}
