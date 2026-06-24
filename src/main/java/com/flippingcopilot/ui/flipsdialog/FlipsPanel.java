package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.FlipStatus;
import com.flippingcopilot.model.FlipV2;
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
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static com.flippingcopilot.util.DateUtil.formatEpoch;

@Slf4j
public class FlipsPanel extends JPanel {

    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100, 200, 500, 1000, 2000};
    public static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    public static final String[] COLUMN_NAMES = {
            "First buy time", "Last sell time", "Account", "Item", "Status", "Bought", "Sold",
            "Avg. buy price", "Avg. sell price", "Tax", "Profit", "Profit ea."
    };

    private final FlipManager flipsManager;
    private final CopilotLoginRS copilotLoginRS;
    private final ApiRequestHandler apiRequestHandler;
    private final Consumer<FlipV2> onVisualizeFlip;
    private final AccountDropdown accountDropdown;
    private final JCheckBox showFinishedCheckbox;
    private final JCheckBox showBuyingCheckbox;
    private final JCheckBox showSellingCheckbox;
    private final PaginatedTablePanel<FlipV2> tablePanel;

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

        Paginator paginatorPanel = new Paginator((i) -> sortAndFilter.setPage(i));
        tablePanel = new PaginatedTablePanel<>(COLUMN_NAMES, this::toRow);
        sortAndFilter = new FlipFilterAndSort(flipsManager, tablePanel::setRows, paginatorPanel::setTotalPages,
                tablePanel::setSpinnerVisible, executorService, copilotLoginRS, itemController);

        ItemSearchMultiSelect searchField = new ItemSearchMultiSelect(
                sortAndFilter::getFilteredItems,
                itemController::allItemIds,
                itemController::search,
                sortAndFilter::setFilteredItems,
                "Items filter...",
                SwingUtilities.getWindowAncestor(this));
        searchField.setMinimumSize(new Dimension(300, 0));
        searchField.setToolTipText("Search by item name");

        accountDropdown = new AccountDropdown(
                () -> copilotLoginRS.get().displayNameToAccountId,
                sortAndFilter::setAccountId,
                AccountDropdown.ALL_ACCOUNTS_DROPDOWN_OPTION
        );
        accountDropdown.setPreferredSize(new Dimension(120, accountDropdown.getPreferredSize().height));
        accountDropdown.setToolTipText("Select account");

        IntervalDropdown timeIntervalDropdown = new IntervalDropdown(sortAndFilter::setInterval, IntervalDropdown.ALL_TIME, false);
        timeIntervalDropdown.setPreferredSize(new Dimension(150, timeIntervalDropdown.getPreferredSize().height));
        timeIntervalDropdown.setToolTipText("Select time interval");

        tablePanel.leftControls().add(searchField);
        addGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(timeIntervalDropdown);
        addGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(accountDropdown);
        addGap(tablePanel.leftControls(), 3);

        JLabel showLabel = new JLabel("Show:");
        showLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        tablePanel.leftControls().add(showLabel);
        addGap(tablePanel.leftControls(), 3);

        showFinishedCheckbox = createStatusCheckbox("FINISHED");
        showBuyingCheckbox = createStatusCheckbox("BUYING");
        showSellingCheckbox = createStatusCheckbox("SELLING");
        tablePanel.leftControls().add(showFinishedCheckbox);
        addGap(tablePanel.leftControls(), 2);
        tablePanel.leftControls().add(showBuyingCheckbox);
        addGap(tablePanel.leftControls(), 2);
        tablePanel.leftControls().add(showSellingCheckbox);
        applyStatusFilters();

        JButton downloadButton = createDownloadButton();
        tablePanel.rightControls().add(downloadButton);

        tablePanel.installHeaderSort(sortAndFilter::getSortColumn, sortAndFilter::getSortDirection, (column, direction) -> {
            sortAndFilter.setSortColumn(column);
            sortAndFilter.setSortDirection(direction);
        });
        tablePanel.installPopupHandler(this::showFlipMenu);
        applyRenderers(config);

        JComboBox<Integer> pageSizeComboBox = new JComboBox<>(PAGE_SIZE_OPTIONS);
        pageSizeComboBox.setSelectedItem(sortAndFilter.getPageSize());
        pageSizeComboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizeComboBox.setFocusable(false);
        pageSizeComboBox.setToolTipText("Page size");
        pageSizeComboBox.addActionListener(e -> sortAndFilter.setPageSize((Integer) pageSizeComboBox.getSelectedItem()));
        tablePanel.installPageFooter(paginatorPanel, pageSizeComboBox);

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
        tablePanel.moneyColumns(GP_FORMAT, true, 7, 8, 9, 11);
        tablePanel.profitColumns(GP_FORMAT, config, 10);
        tablePanel.centerColumns(2, 4, 5, 6);
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
        long profitPerItem = flip.getClosedQuantity() > 0 ? flip.getProfit() / flip.getClosedQuantity() : 0L;
        return new Object[]{
                formatTimestamp(flip.getOpenedTime()),
                formatTimestamp(flip.getClosedTime()),
                accountIdToDisplayName.getOrDefault(flip.getAccountId(), "Display name not loaded"),
                flip.getCachedItemName(),
                flip.getStatus().name(),
                flip.getOpenedQuantity(),
                flip.getClosedQuantity(),
                flip.getSpent() / flip.getOpenedQuantity(),
                flip.getClosedQuantity() == 0 ? 0 : (flip.getReceivedPostTax() + flip.getTaxPaid()) / flip.getClosedQuantity(),
                flip.getTaxPaid(),
                flip.getProfit(),
                profitPerItem
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
                Consumer<FlipV2> onSuccess = (f) -> {
                    flipsManager.mergeFlips(Collections.singletonList(f), copilotLoginRS.get().getUserId());
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

    private String formatTimestamp(int epochSeconds) {
        if (epochSeconds == 0) {
            return "N/A";
        }
        return formatEpoch(epochSeconds);
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

    private static void addGap(JPanel panel, int width) {
        panel.add(Box.createRigidArea(new Dimension(width, 0)));
    }
}
