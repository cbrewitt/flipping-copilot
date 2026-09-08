package copilot.ui.flipsdialog;

import copilot.model.*;
import copilot.config.*;
import copilot.controller.*;
import copilot.rs.*;
import copilot.ui.*;
import copilot.ui.components.*;
import lombok.extern.slf4j.*;
import net.runelite.client.ui.*;

import static copilot.ui.UIUtilities.addHorizontalGap;

import javax.inject.*;
import javax.swing.*;
import java.awt.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class ItemAggregatePanel extends JPanel {

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
                              Items itemController,
                              CopilotLogin copilotLoginRS,
                              @Named("copilotExecutor") ExecutorService executorService,
                              CopilotConfig config) {
        setFocusable(true);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Initialize pagination first (before loadAggregates is called)
        var paginatorPanel = new Paginator((i) -> sortAndFilter.setPage(i));
        tablePanel = new PaginatedTablePanel<>(COLUMN_NAMES, this::toRow);
        sortAndFilter = new ItemAggregateFilterSort(flipsManager, itemController, tablePanel::setRows,
                paginatorPanel::setTotalPages, tablePanel::setSpinnerVisible, executorService);

        var searchField = ItemSearchMultiSelect.itemsFilter(this, itemController,
                sortAndFilter::getFilteredItems, sortAndFilter::setFilteredItems);

        var timeIntervalDropdown = DialogUi.intervalDropdown(sortAndFilter::setInterval);

        accountDropdown = DialogUi.accountDropdown(() -> copilotLoginRS.get().displayNameToAccountId, sortAndFilter::setAccountId);
        accountDropdown.refresh();

        tablePanel.leftControls().add(searchField);
        addHorizontalGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(timeIntervalDropdown);
        addHorizontalGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(accountDropdown);

        tablePanel.installHeaderSort(sortAndFilter::getSortColumn, sortAndFilter::getSortDirection, (column, direction) -> {
            sortAndFilter.setSortColumn(column); sortAndFilter.setSortDirection(direction);
        });

        // Apply renderers
        tablePanel.centerColumns(1);
        tablePanel.moneyColumns(GP_FORMAT, 2, 3);
        tablePanel.profitColumns(GP_FORMAT, config, 4, 5, 6, 7);

        tablePanel.installPageFooter(paginatorPanel, sortAndFilter.getPageSize(), sortAndFilter::setPageSize);

        add(tablePanel, BorderLayout.CENTER);
    }

    private Object[] toRow(ItemAggregate aggregate) {
        return new Object[]{
                aggregate.itemName,
                aggregate.numberOfFlips,
                aggregate.totalQuantityFlipped,
                aggregate.biggestLoss,
                aggregate.biggestWin,
                aggregate.totalProfit,
                aggregate.avgProfit,
                aggregate.avgProfitEa
        };
    }

    public void onTabShown() {
        sortAndFilter.reloadAggregates(true);
        accountDropdown.refresh();
    }
}
