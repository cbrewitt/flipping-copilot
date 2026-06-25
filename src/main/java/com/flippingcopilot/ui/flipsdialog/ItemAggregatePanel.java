package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.ItemAggregate;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.ui.Paginator;
import com.flippingcopilot.ui.components.AccountDropdown;
import com.flippingcopilot.ui.components.IntervalDropdown;
import com.flippingcopilot.ui.components.ItemSearchMultiSelect;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Named;
import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

@Slf4j
public class ItemAggregatePanel extends JPanel {

    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100, 200, 500, 1000, 2000};
    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final String[] COLUMN_NAMES = {
            "Item", "Number of flips", "Total quantity flipped", "Biggest loss", "Biggest win",
            "Total profit", "Avg profit", "Avg profit ea."
    };

    // ui components
    private final AccountDropdown accountDropdown;
    private final PaginatedTablePanel<ItemAggregate> tablePanel;

    // state
    private ItemAggregateFilterSort sortAndFilter;

    public ItemAggregatePanel(FlipManager flipsManager,
                              ItemController itemController,
                              CopilotLoginRS copilotLoginRS,
                              @Named("copilotExecutor") ExecutorService executorService,
                              FlippingCopilotConfig config) {
        setFocusable(true);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Initialize pagination first (before loadAggregates is called)
        Paginator paginatorPanel = new Paginator((i) -> sortAndFilter.setPage(i));
        tablePanel = new PaginatedTablePanel<>(COLUMN_NAMES, this::toRow);
        sortAndFilter = new ItemAggregateFilterSort(flipsManager, itemController, tablePanel::setRows,
                paginatorPanel::setTotalPages, tablePanel::setSpinnerVisible, executorService);

        ItemSearchMultiSelect searchField = new ItemSearchMultiSelect(
                sortAndFilter::getFilteredItems,
                itemController::allItemIds,
                itemController::search,
                sortAndFilter::setFilteredItems,
                "Items filter...",
                SwingUtilities.getWindowAncestor(this));
        searchField.setMinimumSize(new Dimension(300, 0));
        searchField.setToolTipText("Search by item name");

        IntervalDropdown timeIntervalDropdown = new IntervalDropdown(sortAndFilter::setInterval, IntervalDropdown.ALL_TIME, false);
        timeIntervalDropdown.setPreferredSize(new Dimension(150, timeIntervalDropdown.getPreferredSize().height));
        timeIntervalDropdown.setToolTipText("Select time interval");

        accountDropdown = new AccountDropdown(
                () -> copilotLoginRS.get().displayNameToAccountId,
                sortAndFilter::setAccountId,
                AccountDropdown.ALL_ACCOUNTS_DROPDOWN_OPTION
        );
        accountDropdown.setPreferredSize(new Dimension(120, accountDropdown.getPreferredSize().height));
        accountDropdown.setToolTipText("Select account");
        accountDropdown.refresh();

        tablePanel.leftControls().add(searchField);
        addGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(timeIntervalDropdown);
        addGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(accountDropdown);

        tablePanel.installHeaderSort(sortAndFilter::getSortColumn, sortAndFilter::getSortDirection, (column, direction) -> {
            sortAndFilter.setSortColumn(column);
            sortAndFilter.setSortDirection(direction);
        });

        // Apply renderers
        tablePanel.centerColumns(1);
        tablePanel.moneyColumns(GP_FORMAT, 2, 3);
        tablePanel.profitColumns(GP_FORMAT, config, 4, 5, 6, 7);

        JComboBox<Integer> pageSizeComboBox = new JComboBox<>(PAGE_SIZE_OPTIONS);
        pageSizeComboBox.setSelectedItem(sortAndFilter.getPageSize());
        pageSizeComboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizeComboBox.setFocusable(false);
        pageSizeComboBox.setToolTipText("Page size");
        pageSizeComboBox.addActionListener(e -> sortAndFilter.setPageSize((Integer) pageSizeComboBox.getSelectedItem()));
        tablePanel.installPageFooter(paginatorPanel, pageSizeComboBox);

        add(tablePanel, BorderLayout.CENTER);
    }

    private Object[] toRow(ItemAggregate aggregate) {
        return new Object[]{
                aggregate.getItemName(),
                aggregate.getNumberOfFlips(),
                aggregate.getTotalQuantityFlipped(),
                aggregate.getBiggestLoss(),
                aggregate.getBiggestWin(),
                aggregate.getTotalProfit(),
                aggregate.getAvgProfit(),
                aggregate.getAvgProfitEa()
        };
    }

    private static void addGap(JPanel panel, int width) {
        panel.add(Box.createRigidArea(new Dimension(width, 0)));
    }

    public void onTabShown() {
        sortAndFilter.reloadAggregates(true);
        accountDropdown.refresh();
    }
}
