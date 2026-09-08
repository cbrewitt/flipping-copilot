package copilot.ui.flipsdialog;

import copilot.model.*;
import copilot.config.*;
import copilot.controller.*;
import copilot.rs.*;
import lombok.extern.slf4j.*;
import net.runelite.client.ui.*;

import javax.inject.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class AccountsAggregatePanel extends JPanel {

    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final String[] COLUMN_NAMES = {
            "Account", "Number of flips", "Biggest loss", "Biggest win", "Total profit"
    };

    // dependencies
    private final CopilotLogin copilotLogin;
    private final ApiClient api;
    private final FlipManager flipManager;
    private final ExecutorService executor;

    // ui components
    private final PaginatedTablePanel<AccountAggregate> tablePanel;

    // state
    private final AccountsAggregateFilterSort sortAndFilter;

    public AccountsAggregatePanel(CopilotLogin copilotLogin,
                                  @Named("copilotExecutor") ExecutorService executor,
                                  CopilotConfig config,
                                  ApiClient api,
                                  FlipManager flipManager) {
        this.copilotLogin = copilotLogin; this.api = api; this.flipManager = flipManager; this.executor = executor;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Initialize sort and filter
        tablePanel = new PaginatedTablePanel<>(COLUMN_NAMES, this::toRow);
        sortAndFilter = new AccountsAggregateFilterSort(flipManager, copilotLogin,
                tablePanel::setRows, tablePanel::setSpinnerVisible, executor);

        // Create top panel with all controls
        var timeIntervalDropdown = DialogUi.intervalDropdown(sortAndFilter::setInterval);
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
                aggregate.accountName,
                aggregate.numberOfFlips,
                aggregate.biggestLoss,
                aggregate.biggestWin,
                aggregate.totalProfit,
        };
    }

    private void showAccountMenu(MouseEvent e, int row) {
        var account = tablePanel.row(row);
        var menu = new JPopupMenu();
        DialogUi.addMenuItem(menu, "Delete account", () -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete " + account.accountName + "?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                tablePanel.setSpinnerVisible(true);
                log.info("Deleting account: {}", account.accountId);
                Runnable onSuccess = () -> {
                    copilotLogin.removeAccount(account.accountId);
                    executor.submit(() -> flipManager.deleteAccount(account.accountId));
                    tablePanel.setSpinnerVisible(false);
                    sortAndFilter.reloadAggregates(true);
                };
                api.asyncDeleteAccount(account.accountId, onSuccess, () -> tablePanel.setSpinnerVisible(false));
            }
        });
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    public void onTabShown() { sortAndFilter.reloadAggregates(true); }
}
