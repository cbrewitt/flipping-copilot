package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.*;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.ui.Paginator;
import com.flippingcopilot.ui.components.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Named;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static com.flippingcopilot.ui.UIUtilities.addHorizontalGap;
import static com.flippingcopilot.util.DateUtil.formatEpochOrNa;
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
    private final CopilotLoginRS copilotLoginRS;
    private final ApiRequestHandler apiRequestHandler;
    private final Consumer<FlipV2> onVisualizeFlip;

    // ui components
    private final AccountDropdown accountDropdown;
    private final JCheckBox showFinishedCheckbox;
    private final JCheckBox showBuyingCheckbox;
    private final JCheckBox showSellingCheckbox;
    private final PaginatedTablePanel<FlipV2> tablePanel;

    // state
    public FlipFilterAndSort sortAndFilter;

    public FlipsPanel(FlipManager flipsManager,
                      ItemController itemController,
                      CopilotLoginRS copilotLoginRS,
                      @Named("copilotExecutor") ExecutorService executorService,
                      FlippingCopilotConfig config,
                      ApiRequestHandler apiRequestHandler,
                      Consumer<FlipV2> onVisualizeFlip) {
        this.flipsManager = flipsManager;
        this.copilotLoginRS = copilotLoginRS;
        this.apiRequestHandler = apiRequestHandler;
        this.onVisualizeFlip = onVisualizeFlip;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Initialize pagination first (before loadFlips is called)
        Paginator paginatorPanel = new Paginator((i) -> sortAndFilter.setPage(i));
        tablePanel = new PaginatedTablePanel<>(COLUMN_NAMES, this::toRow);
        sortAndFilter = new FlipFilterAndSort(flipsManager, tablePanel::setRows, paginatorPanel::setTotalPages,
                tablePanel::setSpinnerVisible, executorService, copilotLoginRS, itemController);

        ItemSearchMultiSelect searchField = ItemSearchMultiSelect.itemsFilter(this, itemController,
                sortAndFilter::getFilteredItems, sortAndFilter::setFilteredItems);

        accountDropdown = DialogUi.accountDropdown(() -> copilotLoginRS.get().displayNameToAccountId, sortAndFilter::setAccountId);

        IntervalDropdown timeIntervalDropdown = DialogUi.intervalDropdown(sortAndFilter::setInterval);

        tablePanel.leftControls().add(searchField);
        addHorizontalGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(timeIntervalDropdown);
        addHorizontalGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(accountDropdown);
        addHorizontalGap(tablePanel.leftControls(), 3);

        JLabel showLabel = new JLabel("Show:");
        showLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
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

        JButton downloadButton = createDownloadButton();
        tablePanel.rightControls().add(downloadButton);

        // Disable default table sorting and set up custom header click handling
        tablePanel.installHeaderSort(sortAndFilter::getSortColumn, sortAndFilter::getSortDirection, (column, direction) -> {
            sortAndFilter.setSortColumn(column);
            sortAndFilter.setSortDirection(direction);
        });
        tablePanel.installPopupHandler(this::showFlipMenu);
        applyRenderers(config);

        tablePanel.installPageFooter(paginatorPanel, sortAndFilter.getPageSize(), sortAndFilter::setPageSize);

        add(tablePanel, BorderLayout.CENTER);
    }

    private JCheckBox createStatusCheckbox(String text) {
        JCheckBox checkbox = new JCheckBox(text, true);
        checkbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        checkbox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        checkbox.setFocusable(false);
        checkbox.addActionListener(e -> applyStatusFilters());
        return checkbox;
    }

    private void applyRenderers(FlippingCopilotConfig config) {
        // Apply renderers
        tablePanel.moneyColumns(GP_FORMAT, true, 7, 8, 9, 11); // Avg. buy price, Avg. sell price, Tax, Profit ea.
        tablePanel.profitColumns(GP_FORMAT, config, 10); // Profit (with color)
        tablePanel.centerColumns(2, 4, 5, 6); // Account, Status, Bought, Sold
    }

    private JButton createDownloadButton() {
        JButton button = new JButton();
        button.setToolTipText("Download as CSV");
        button.setFocusable(false);
        button.setText("Download");
        button.addActionListener(e -> downloadAsCSV());
        return button;
    }

    private Object[] toRow(FlipV2 flip) {
        Map<Integer, String> accountIdToDisplayName = copilotLoginRS.get().accountIdToDisplayName;
        return new Object[]{
                formatEpochOrNa(flip.getOpenedTime()),
                formatEpochOrNa(flip.getClosedTime()),
                accountIdToDisplayName.getOrDefault(flip.getAccountId(), "Display name not loaded"),
                flip.getCachedItemName(),
                flip.getStatus().name(),
                flip.getOpenedQuantity(),
                flip.getClosedQuantity(),
                FlipTableUtil.averageBuy(flip),
                FlipTableUtil.averageSell(flip),
                flip.getTaxPaid(),
                flip.getProfit(),
                FlipTableUtil.profitEach(flip)
        };
    }

    private void showFlipMenu(MouseEvent e, int row) {
        FlipV2 flip = tablePanel.row(row);

        JPopupMenu menu = new JPopupMenu();
        JMenuItem visualizeFlip = new JMenuItem("Visualize flip");
        visualizeFlip.addActionListener(evt -> onVisualizeFlip.accept(flip));
        menu.add(visualizeFlip);

        JMenuItem deleteItem = new JMenuItem("Delete flip");
        deleteItem.addActionListener(evt -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete this flip?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                tablePanel.setSpinnerVisible(true);
                log.info("deleting flip with ID: {}", flip.getId());
                Consumer<List<FlipV2>> onSuccess = (flips) -> {
                    flipsManager.mergeFlips(flips, copilotLoginRS.get().getUserId());
                    tablePanel.setSpinnerVisible(false);
                    sortAndFilter.reloadFlips(true, true);
                };
                apiRequestHandler.asyncDeleteFlip(flip, onSuccess, () -> tablePanel.setSpinnerVisible(false));
            }
        });
        menu.add(deleteItem);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void downloadAsCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("flips.csv"));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (FileWriter writer = new FileWriter(file)) {
                sortAndFilter.writeCsvRecords(writer);
                JOptionPane.showMessageDialog(this, "Flips exported successfully!",
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                log.error("Error exporting flips", ex);
                JOptionPane.showMessageDialog(this, "Error exporting flips: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void onTabShown() {
        sortAndFilter.reloadFlips(true, true);
        accountDropdown.refresh();
    }

    private void applyStatusFilters() {
        EnumSet<FlipStatus> statuses = EnumSet.noneOf(FlipStatus.class);
        if (showFinishedCheckbox.isSelected()) {
            statuses.add(FlipStatus.FINISHED);
        }
        if (showBuyingCheckbox.isSelected()) {
            statuses.add(FlipStatus.BUYING);
        }
        if (showSellingCheckbox.isSelected()) {
            statuses.add(FlipStatus.SELLING);
        }
        sortAndFilter.setIncludedStatuses(statuses);
    }
}
