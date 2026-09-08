package copilot.ui.flipsdialog;
import static javax.swing.JOptionPane.*;

import copilot.controller.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.model.*;
import copilot.rs.*;
import copilot.ui.*;
import copilot.ui.components.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import static copilot.ui.UIUtilities.addHorizontalGap;
import static copilot.util.DateUtil.formatEpochOrNa;
import java.util.List;

@Slf4j
public class FlipsPanel extends JPanel {

    public static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    public static final String[] COLUMN_NAMES = {
            "First buy time", "Last sell time", "Account", "Item", "Status", "Bought", "Sold",
            "Avg. buy price", "Avg. sell price", "Tax", "Profit", "Profit ea."
    };

    // dependencies
    private final FlipManager flipsManager;
    private final CopilotLogin copilotLogin;
    private final ApiClient api;
    private final Consumer<Flip> onVisualizeFlip;

    // ui components
    private final AccountDropdown accountDropdown;
    private final JCheckBox showFinishedCheckbox, showBuyingCheckbox;
    private final JCheckBox showSellingCheckbox;
    private final PaginatedTablePanel<Flip> tablePanel;

    // state
    public FlipFilterAndSort sortAndFilter;

    public FlipsPanel(FlipManager flipsManager,
                      Items itemController,
                      CopilotLogin copilotLogin,
                      @Named("copilotExecutor") ExecutorService executorService,
                      CopilotConfig config,
                      ApiClient api,
                      Consumer<Flip> onVisualizeFlip) {
        this.flipsManager = flipsManager; this.copilotLogin = copilotLogin; this.api = api;
        this.onVisualizeFlip = onVisualizeFlip;

        setLayout(new BorderLayout());
        setBackground(DARK_GRAY_COLOR);

        // Initialize pagination first (before loadFlips is called)
        var paginatorPanel = new Paginator((i) -> sortAndFilter.setPage(i));
        tablePanel = new PaginatedTablePanel<>(COLUMN_NAMES, this::toRow);
        sortAndFilter = new FlipFilterAndSort(flipsManager, tablePanel::setRows, paginatorPanel::setTotalPages,
                tablePanel::setSpinnerVisible, executorService, copilotLogin, itemController);

        var searchField = ItemSearchMultiSelect.itemsFilter(this, itemController,
                sortAndFilter::getFilteredItems, sortAndFilter::setFilteredItems);

        accountDropdown = DialogUi.accountDropdown(() -> copilotLogin.get().displayNameToAccountId, sortAndFilter::setAccountId);

        var timeIntervalDropdown = DialogUi.intervalDropdown(sortAndFilter::setInterval);

        tablePanel.leftControls().add(searchField);
        addHorizontalGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(timeIntervalDropdown);
        addHorizontalGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(accountDropdown);
        addHorizontalGap(tablePanel.leftControls(), 3);

        var showLabel = UIUtilities.coloredLabel("Show:", LIGHT_GRAY_COLOR);
        tablePanel.leftControls().add(showLabel);
        addHorizontalGap(tablePanel.leftControls(), 3);

        showFinishedCheckbox = createStatusCheckbox("FINISHED");
        showBuyingCheckbox = createStatusCheckbox("BUYING");
        showSellingCheckbox = createStatusCheckbox("SELLING");
        tablePanel.leftControls().add(showFinishedCheckbox);
        addHorizontalGap(tablePanel.leftControls(), 2);
        tablePanel.leftControls().add(showBuyingCheckbox);
        addHorizontalGap(tablePanel.leftControls(), 2);
        tablePanel.leftControls().add(showSellingCheckbox);
        applyStatusFilters();

        var downloadButton = createDownloadButton();
        tablePanel.rightControls().add(downloadButton);

        // Disable default table sorting and set up custom header click handling
        tablePanel.installHeaderSort(sortAndFilter::getSortColumn, sortAndFilter::getSortDirection, (column, direction) -> {
            sortAndFilter.setSortColumn(column); sortAndFilter.setSortDirection(direction);
        });
        tablePanel.installPopupHandler(this::showFlipMenu);
        applyRenderers(config);

        tablePanel.installPageFooter(paginatorPanel, sortAndFilter.getPageSize(), sortAndFilter::setPageSize);

        add(tablePanel, BorderLayout.CENTER);
    }

    private JCheckBox createStatusCheckbox(String text) {
        var checkbox = new JCheckBox(text, true);
        checkbox.setBackground(DARK_GRAY_COLOR); checkbox.setForeground(LIGHT_GRAY_COLOR); checkbox.setFocusable(false);
        checkbox.addActionListener(e -> applyStatusFilters());
        return checkbox;
    }

    private void applyRenderers(CopilotConfig config) {
        // Apply renderers
        tablePanel.moneyColumns(GP_FORMAT, true, 7, 8, 9, 11); // Avg. buy price, Avg. sell price, Tax, Profit ea.
        tablePanel.profitColumns(GP_FORMAT, config, 10); // Profit (with color)
        tablePanel.centerColumns(2, 4, 5, 6); // Account, Status, Bought, Sold
    }

    private JButton createDownloadButton() {
        var button = new JButton();
        button.setToolTipText("Download as CSV"); button.setFocusable(false); button.setText("Download");
        button.addActionListener(e -> downloadAsCSV());
        return button;
    }

    private Object[] toRow(Flip flip) {
        var accountIdToDisplayName = copilotLogin.get().accountIdToDisplayName;
        return new Object[]{
                formatEpochOrNa(flip.openedTime),
                formatEpochOrNa(flip.closedTime),
                accountIdToDisplayName.getOrDefault(flip.accountId, "Display name not loaded"),
                flip.cachedItemName,
                flip.status.name(),
                flip.openedQuantity,
                flip.closedQuantity,
                FlipTableUtil.averageBuy(flip),
                FlipTableUtil.averageSell(flip),
                flip.taxPaid,
                flip.profit,
                FlipTableUtil.profitEach(flip)
        };
    }

    private void showFlipMenu(MouseEvent e, int row) {
        Flip flip = tablePanel.row(row);

        var menu = new JPopupMenu();
        DialogUi.addMenuItem(menu, "Visualize flip", () -> onVisualizeFlip.accept(flip));

        DialogUi.addMenuItem(menu, "Delete flip", () -> {
            int result = showConfirmDialog(this,
                    "Are you sure you want to delete this flip?",
                    "Confirm Delete",
                    YES_NO_OPTION);
            if (result == YES_OPTION) {
                tablePanel.setSpinnerVisible(true);
                log.info("deleting flip with ID: {}", flip.id);
                Consumer<List<Flip>> onSuccess = (flips) -> {
                    flipsManager.mergeFlips(flips, copilotLogin.get().getUserId());
                    tablePanel.setSpinnerVisible(false);
                    sortAndFilter.reloadFlips(true, true);
                };
                api.asyncDeleteFlip(flip, onSuccess, () -> tablePanel.setSpinnerVisible(false));
            }
        });
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void downloadAsCSV() {
        var fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("flips.csv"));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (FileWriter writer = new FileWriter(file)) {
                sortAndFilter.writeCsvRecords(writer);
                showMessageDialog(this, "Flips exported successfully!", "Export Complete", INFORMATION_MESSAGE);
            } catch (IOException ex) {
                log.error("Error exporting flips", ex);
                showMessageDialog(this, "Error exporting flips: " + ex.getMessage(), "Export Error", ERROR_MESSAGE);
            }
        }
    }

    public void onTabShown() {
        sortAndFilter.reloadFlips(true, true);
        accountDropdown.refresh();
    }

    private void applyStatusFilters() {
        EnumSet<FlipStatus> statuses = EnumSet.noneOf(FlipStatus.class);
        if (showFinishedCheckbox.isSelected()) { statuses.add(FlipStatus.FINISHED); }
        if (showBuyingCheckbox.isSelected()) { statuses.add(FlipStatus.BUYING); }
        if (showSellingCheckbox.isSelected()) { statuses.add(FlipStatus.SELLING); }
        sortAndFilter.setIncludedStatuses(statuses);
    }
}
