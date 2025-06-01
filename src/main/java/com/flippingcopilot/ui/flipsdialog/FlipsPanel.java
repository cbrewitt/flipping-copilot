package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.Paginator;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.components.DisplayNameDropdown;
import com.flippingcopilot.ui.components.IntervalDropdown;
import com.flippingcopilot.ui.components.ItemSearchMultiSelect;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Named;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static com.flippingcopilot.util.DateUtil.formatEpoch;

@Slf4j
public class FlipsPanel extends JPanel {


    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100, 200, 500, 1000, 2000};
    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    // dependencies
    private final FlipManager flipsManager;

    // ui components
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Paginator paginatorPanel;
    private final Spinner spinner;
    private final JScrollPane scrollPane;
    private ItemSearchMultiSelect searchField;
    private JCheckBox groupByItemCheckbox;
    private JCheckBox showOpeningFlipsCheckbox;
    private IntervalDropdown timeIntervalDropdown;
    private DisplayNameDropdown displayNameDropdown;
    private JComboBox<Integer> pageSizeComboBox;

    // state
    private List<FlipV2> currentFlips = new ArrayList<>();
    private FlipFilterAndSort pager;

    private final String[] columnNames = {
            "First buy time", "Last sell time", "Account", "Item", "Status", "Bought", "Sold",
            "Avg. buy price", "Avg. sell price", "Tax", "Profit", "Profit ea."
    };
    private JPanel spinnerOverlay;

    public FlipsPanel(FlipManager flipsManager,
                      ItemController itemController,
                      TransactionManager transactionManager,
                      @Named("copilotExecutor") ExecutorService executorService) {

        // Initialize pagination first (before loadFlips is called)
        paginatorPanel = new Paginator((i) -> pager.setPage(i));
        pager = new FlipFilterAndSort(flipsManager, this::showFlips, paginatorPanel::setTotalPages, this::setSpinnerVisible, executorService);
        this.flipsManager = flipsManager;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Create top panel with all controls
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Create left panel with all inputs stacked from left
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchField = new ItemSearchMultiSelect(
                new HashSet<>(),
                itemController::allItemIds,
                itemController::search,
                pager::setFilteredItems,
                "Items filter...");
        searchField.setMinimumSize(new Dimension(300, 0));
        searchField.setToolTipText("Search by item name");

        // Create group by checkbox
        groupByItemCheckbox = new JCheckBox("Group by item");
        groupByItemCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        groupByItemCheckbox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        groupByItemCheckbox.setFocusable(false);

        // Create group by checkbox
        showOpeningFlipsCheckbox= new JCheckBox("Show opening flips");
        showOpeningFlipsCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        showOpeningFlipsCheckbox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        showOpeningFlipsCheckbox.setFocusable(false);
        showOpeningFlipsCheckbox.addActionListener(e -> pager.setIncludeOpeningFlips(showOpeningFlipsCheckbox.isSelected()));

        // Create page size dropdown
        pageSizeComboBox = new JComboBox<>(PAGE_SIZE_OPTIONS);
        pageSizeComboBox.setSelectedItem(pager.getPageSize());
        pageSizeComboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizeComboBox.setFocusable(false);
        pageSizeComboBox.setToolTipText("Page size");
        pageSizeComboBox.addActionListener(e -> pager.setPageSize((Integer) pageSizeComboBox.getSelectedItem()));

        // Create download button panel (stays on right)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JButton downloadButton = createDownloadButton();
        buttonPanel.add(downloadButton);

        topPanel.add(leftPanel, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Create table model
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Create table
        table = new JTable(tableModel);
        table.setBackground(ColorScheme.DARK_GRAY_COLOR);
        table.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        table.setSelectionBackground(ColorScheme.BRAND_ORANGE);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(ColorScheme.MEDIUM_GRAY_COLOR);
        table.setRowHeight(25);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Disable default table sorting and set up custom header click handling
        table.setRowSorter(null);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFocusable(false);

        // Add custom header click listener for sorting
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int columnIndex = table.getTableHeader().columnAtPoint(e.getPoint());
                if (columnIndex >= 0 && columnIndex < columnNames.length) {
                    String clickedColumn = columnNames[columnIndex];

                    // Toggle sort direction if clicking the same column, otherwise default to DESC
                    SortDirection newDirection = SortDirection.DESC;
                    if (clickedColumn.equals(pager.getSortColumn())) {
                        newDirection = pager.getSortDirection() == SortDirection.DESC ?
                                SortDirection.ASC : SortDirection.DESC;
                    }

                    pager.setSortColumn(clickedColumn);
                    pager.setSortDirection(newDirection);
                }
            }
        });

        // Add mouse listener for right-click menu
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            private void showPopup(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < table.getRowCount()) {
                    table.setRowSelectionInterval(row, row);
                    showFlipMenu(e, row);
                }
            }
        });

        // Custom renderer for money columns
        DefaultTableCellRenderer moneyRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    setText(formatGp((Long) value));
                    setHorizontalAlignment(RIGHT);
                } else if (value instanceof String) {
                    setHorizontalAlignment(CENTER);
                }
                return c;
            }
        };

        // Custom renderer for profit column (column 10)
        DefaultTableCellRenderer profitRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    long amount = (Long) value;
                    setText(formatGp(amount));
                    setHorizontalAlignment(RIGHT);

                    // Color profit/loss only if not selected
                    if (!isSelected) {
                        if (amount > 0) {
                            setForeground(new Color(0, 200, 0));
                        } else if (amount < 0) {
                            setForeground(new Color(200, 0, 0));
                        } else {
                            setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                        }
                    }
                }
                return c;
            }
        };

        // Apply renderers
        table.getColumnModel().getColumn(7).setCellRenderer(moneyRenderer); // Avg. buy price
        table.getColumnModel().getColumn(8).setCellRenderer(moneyRenderer); // Avg. sell price
        table.getColumnModel().getColumn(9).setCellRenderer(moneyRenderer); // Tax
        table.getColumnModel().getColumn(10).setCellRenderer(profitRenderer); // Profit (with color)
        table.getColumnModel().getColumn(11).setCellRenderer(moneyRenderer); // Profit ea.

        // Center align quantity, status, and account columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer); // Account
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer); // Status
        table.getColumnModel().getColumn(5).setCellRenderer(centerRenderer); // Bought
        table.getColumnModel().getColumn(6).setCellRenderer(centerRenderer); // Sold

        // Setup dropdowns using the new components
        setupDropdowns();

        leftPanel.add(searchField);
        leftPanel.add(Box.createRigidArea(new Dimension(3,0)));
        leftPanel.add(timeIntervalDropdown);
        leftPanel.add(Box.createRigidArea(new Dimension(3,0)));
        leftPanel.add(displayNameDropdown);
        leftPanel.add(Box.createRigidArea(new Dimension(3,0)));
        leftPanel.add(groupByItemCheckbox);
        leftPanel.add(Box.createRigidArea(new Dimension(3,0)));
        leftPanel.add(showOpeningFlipsCheckbox);

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layeredPane.setOpaque(true);

        // Create spinner with semi-transparent background
        spinner = new Spinner();
        spinner.show();
        spinnerOverlay = new JPanel(new GridBagLayout());
        spinnerOverlay.setBackground(ColorScheme.DARK_GRAY_COLOR);
        spinnerOverlay.setOpaque(true);
        spinnerOverlay.add(spinner);
        spinnerOverlay.setVisible(false); // Initially hidden

        scrollPane = new JScrollPane(table);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        layeredPane.setLayout(new OverlayLayout(layeredPane));
        layeredPane.add(spinnerOverlay, JLayeredPane.MODAL_LAYER);
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);

        add(layeredPane, BorderLayout.CENTER);

        // Create bottom panel with pagination and page size controls
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Create left panel for page size controls
        JPanel pageSizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pageSizePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizePanel.setBorder(BorderFactory.createEmptyBorder(4,0, 0,0));
        JLabel pageSizeLabel = new JLabel("Page size:");
        pageSizePanel.add(pageSizeLabel);
        pageSizePanel.add(pageSizeComboBox);
        paginatorPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0,0, 0,pageSizePanel.getPreferredSize().width ), paginatorPanel.getBorder()));
        bottomPanel.add(pageSizePanel, BorderLayout.WEST);
        bottomPanel.add(paginatorPanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);
        pager.setPageSize(FlipFilterAndSort.DEFAULT_PAGE_SIZE);
    }

    private void setSpinnerVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            if (visible) {
                spinnerOverlay.setVisible(true);
                table.setEnabled(false);
            } else {
                spinnerOverlay.setVisible(false);
                table.setEnabled(true);
            }
        });
    }

    private JButton createDownloadButton() {
        JButton button = new JButton();
        button.setToolTipText("Download as CSV");
        button.setFocusable(false);
        button.setText("Download");
        button.addActionListener(e -> downloadAsCSV());
        return button;
    }

    private void setupDropdowns() {
        // Setup display name dropdown using the component
        displayNameDropdown = new DisplayNameDropdown(
                flipsManager::getDisplayNameOptions, // Supplier for getting display names
                pager::setDisplayName// Consumer for handling changes
        );
        displayNameDropdown.setPreferredSize(new Dimension(120, displayNameDropdown.getPreferredSize().height));
        displayNameDropdown.setToolTipText("Select account");
        displayNameDropdown.refresh();

        // Setup time interval dropdown using the component
        timeIntervalDropdown = new IntervalDropdown(pager::setInterval);
        timeIntervalDropdown.setPreferredSize(new Dimension(150, timeIntervalDropdown.getPreferredSize().height));
        timeIntervalDropdown.setToolTipText("Select time interval");
    }


    private void showFlipMenu(MouseEvent e, int row) {
        FlipV2 flip = currentFlips.get(row);
        String status = (String) tableModel.getValueAt(row, 4); // Status is now column 4

        JPopupMenu menu = new JPopupMenu();

        JMenuItem deleteItem = new JMenuItem("Delete flip");
        deleteItem.addActionListener(evt -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete this flip?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                log.info("Deleting flip with ID: {}", flip.getId());
                // TODO: Implement flip deletion
            }
        });

        menu.add(deleteItem);

        // Only show "Close flip" if status is not "CLOSED"
        if (!"CLOSED".equals(status)) {
            JMenuItem closeItem = new JMenuItem("Close flip");
            closeItem.addActionListener(evt -> {
                log.info("Closing flip with ID: {}", flip.getId());
                // TODO: Implement flip closing - update status to CLOSED
            });
            menu.add(closeItem);
        }

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void downloadAsCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("flips.csv"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (FileWriter writer = new FileWriter(file)) {
                // Write headers
                writer.write("First buy time,Last sell time,Account,Item,Status,Bought,Sold,Avg. buy price,Avg. sell price,Tax,Profit,Profit ea.\n");
                JOptionPane.showMessageDialog(this, "Flips exported successfully!",
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                log.error("Error exporting flips", ex);
                JOptionPane.showMessageDialog(this, "Error exporting flips: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showFlips(List<FlipV2> flips) {
        SwingUtilities.invokeLater(() -> {
            // Clear existing data
            currentFlips = flips;
            tableModel.setRowCount(0);
            Map<Integer, String> accountIdToDisplayName = flipsManager.getDisplayNameToAccountId().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
            // Add new data
            for (FlipV2 flip : flips) {
                long profitPerItem = flip.getClosedQuantity() > 0 ? flip.getProfit() / flip.getClosedQuantity() : 0L;

                Object[] row = {
                        formatTimestamp(flip.getOpenedTime()),
                        formatTimestamp(flip.getClosedTime()),
                        accountIdToDisplayName.getOrDefault(flip.getAccountId(), "Display name not loaded"),
                        flip.getItemName() != null ? flip.getItemName() : "Unknown",
                        flip.getStatus(),
                        flip.getOpenedQuantity(),
                        flip.getClosedQuantity(),
                        flip.getSpent(),
                        flip.getReceivedPostTax(),
                        flip.getTaxPaid(),
                        flip.getProfit(),
                        profitPerItem
                };
                tableModel.addRow(row);
            }

            for (int i = 0; i < table.getColumnCount(); i++) {
                resizeColumnWidth(table, i);
            }
        });
    }

    private void resizeColumnWidth(JTable table, int column) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        int preferredWidth = tableColumn.getMinWidth();
        int maxWidth = tableColumn.getMaxWidth();

        // Check header width
        Component comp = table.getTableHeader().getDefaultRenderer()
                .getTableCellRendererComponent(table, tableColumn.getHeaderValue(), false, false, 0, column);
        preferredWidth = Math.max(comp.getPreferredSize().width + 10, preferredWidth);

        // Check cell widths
        for (int row = 0; row < table.getRowCount(); row++) {
            comp = table.getCellRenderer(row, column)
                    .getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
            preferredWidth = Math.max(comp.getPreferredSize().width + 10, preferredWidth);
        }

        preferredWidth = Math.min(preferredWidth, maxWidth);
        tableColumn.setPreferredWidth(preferredWidth);
    }

    private String formatGp(long amount) {
        return GP_FORMAT.format(amount) + " gp";
    }

    private String formatTimestamp(int epochSeconds) {
        if (epochSeconds == 0) {
            return "N/A";
        }
        return formatEpoch(epochSeconds);
    }
}