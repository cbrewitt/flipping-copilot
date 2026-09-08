package copilot.ui.flipsdialog;

import java.awt.event.*;
import static copilot.ui.UIUtilities.*;
import static javax.swing.BorderFactory.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.model.*;
import copilot.ui.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.function.*;

public class PaginatedTablePanel<T> extends JPanel {

    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100, 200, 500, 1000, 2000};

    private final String[] columnNames;
    private final Function<T, Object[]> rowMapper;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JPanel topPanel, leftControls;
    private final JPanel rightControls;
    private final DialogUi.BusyPane layeredPane;

    private List<T> rows = new ArrayList<>();

    public PaginatedTablePanel(String[] columnNames, Function<T, Object[]> rowMapper) {
        this(columnNames, rowMapper, 25);
    }

    public PaginatedTablePanel(String[] columnNames, Function<T, Object[]> rowMapper, int rowHeight) {
        this.columnNames = columnNames; this.rowMapper = rowMapper;

        setLayout(new BorderLayout());
        setBackground(DARK_GRAY_COLOR);

        topPanel = darkPanel(new BorderLayout(), DARK_GRAY_COLOR);
        topPanel.setBorder(createEmptyBorder(5, 5, 5, 5));

        leftControls = darkPanel(new FlowLayout(FlowLayout.LEFT, 0, 0), DARK_GRAY_COLOR);
        rightControls = darkPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0), DARK_GRAY_COLOR);

        topPanel.add(leftControls, BorderLayout.WEST);
        topPanel.add(rightControls, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Create table model
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        // Create table
        table = new JTable(tableModel);
        table.setBackground(DARK_GRAY_COLOR); table.setForeground(LIGHT_GRAY_COLOR);
        table.setSelectionBackground(BRAND_ORANGE); table.setSelectionForeground(Color.WHITE);
        table.setGridColor(MEDIUM_GRAY_COLOR); table.setRowHeight(rowHeight);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setRowSorter(null); // Disable sorting
        table.getTableHeader().setReorderingAllowed(false);
        table.setFocusable(false);

        var scrollPane = new JScrollPane(table);
        scrollPane.setBackground(DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(DARK_GRAY_COLOR);

        layeredPane = new DialogUi.BusyPane(scrollPane);

        add(layeredPane, BorderLayout.CENTER);
    }

    public JPanel leftControls() { return leftControls; }

    public JPanel rightControls() { return rightControls; }

    public JTable table() { return table; }

    public void setRenderer(TableCellRenderer renderer, int... columns) {
        for (int column : columns) { table.getColumnModel().getColumn(column).setCellRenderer(renderer); }
    }

    public void centerColumns(int... columns) { setRenderer(alignedRenderer(JLabel.CENTER), columns); }

    public void rightColumns(int... columns) { setRenderer(alignedRenderer(JLabel.RIGHT), columns); }

    public void moneyColumns(NumberFormat format, int... columns) { moneyColumns(format, false, columns); }

    public void moneyColumns(NumberFormat format, boolean centerStrings, int... columns) {
        setRenderer(moneyRenderer(format, centerStrings), columns);
    }

    public void profitColumns(NumberFormat format, CopilotConfig config, int... columns) {
        setRenderer(profitRenderer(format, config), columns);
    }

    public void setTopControlsVisible(boolean visible) { topPanel.setVisible(visible); }

    public void enableBuiltInSorting() { table.setRowSorter(new TableRowSorter<>(tableModel)); }

    public void installHeaderSort(Supplier<String> currentColumn,
                                  Supplier<SortDirection> currentDirection,
                                  BiConsumer<String, SortDirection> onSortChanged) {
        // Add custom header click listener for sorting
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int columnIndex = table.getTableHeader().columnAtPoint(e.getPoint());
                if (columnIndex < 0 || columnIndex >= columnNames.length) { return; }
                String clickedColumn = columnNames[columnIndex];
                var newDirection = SortDirection.DESC;
                if (clickedColumn.equals(currentColumn.get())) {
                    // Toggle sort direction if clicking the same column, otherwise default to DESC
                    newDirection = currentDirection.get() == SortDirection.DESC ? SortDirection.ASC : SortDirection.DESC;
                }
                onSortChanged.accept(clickedColumn, newDirection);
            }
        });
    }

    public void installPopupHandler(BiConsumer<MouseEvent, Integer> onPopup) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) { showPopup(e); }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) { showPopup(e); }
            }

            private void showPopup(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow < 0 || viewRow >= table.getRowCount()) { return; }
                table.setRowSelectionInterval(viewRow, viewRow);
                onPopup.accept(e, table.convertRowIndexToModel(viewRow));
            }
        });
    }

    public void installPageFooter(Paginator paginatorPanel, int initialPageSize, IntConsumer onPageSizeChanged) {
        JComboBox<Integer> pageSizeComboBox = new JComboBox<>(PAGE_SIZE_OPTIONS);
        pageSizeComboBox.setSelectedItem(initialPageSize); pageSizeComboBox.setBackground(DARKER_GRAY_COLOR);
        pageSizeComboBox.setFocusable(false); pageSizeComboBox.setToolTipText("Page size");
        pageSizeComboBox.addActionListener(e -> onPageSizeChanged.accept((Integer) pageSizeComboBox.getSelectedItem()));

        // Create bottom panel with pagination
        var bottomPanel = darkPanel(new BorderLayout(), DARKER_GRAY_COLOR);

        var pageSizePanel = darkPanel(new FlowLayout(FlowLayout.LEFT, 5, 0), DARKER_GRAY_COLOR);
        pageSizePanel.setBorder(createEmptyBorder(4, 0, 0, 0));
        var pageSizeLabel = coloredLabel("Page size:", LIGHT_GRAY_COLOR);
        pageSizePanel.add(pageSizeLabel);
        pageSizePanel.add(pageSizeComboBox);

        // Adjust paginator border to account for page size panel width
        paginatorPanel.setBorder(createCompoundBorder(
                createEmptyBorder(0, 0, 0, pageSizePanel.getPreferredSize().width),
                paginatorPanel.getBorder()));
        bottomPanel.add(pageSizePanel, BorderLayout.WEST);
        bottomPanel.add(paginatorPanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    public JLabel setSpinnerText(String text) { return layeredPane.setMessage(text); }

    public void addOverlay(Component component, Integer layer) { layeredPane.add(component, layer); }

    public void setRows(List<T> newRows) {
        Runnable updateRows = () -> {
            rows = new ArrayList<>(newRows);
            tableModel.setRowCount(0);
            for (T row : rows) { tableModel.addRow(rowMapper.apply(row)); }
            resizeAllColumns();
        };
        if (ensureEdt(updateRows)) updateRows.run();
    }

    public T row(int modelRow) { return rows.get(modelRow); }

    public void setSpinnerVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            layeredPane.overlay.setVisible(visible);
            table.setEnabled(!visible);
        });
    }

    private void resizeAllColumns() {
        for (int i = 0; i < table.getColumnCount(); i++) {
            resizeColumnWidth(i);
        }
    }

    private void resizeColumnWidth(int column) {
        var tableColumn = table.getColumnModel().getColumn(column);
        int preferredWidth = tableColumn.getMinWidth(), maxWidth = tableColumn.getMaxWidth();

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

        tableColumn.setPreferredWidth(Math.min(preferredWidth, maxWidth));
    }

    private static DefaultTableCellRenderer alignedRenderer(int alignment) {
        var renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(alignment);
        return renderer;
    }

    private static DefaultTableCellRenderer moneyRenderer(NumberFormat format, boolean centerStrings) {
        return new StyledRenderer() {
            @Override
            protected void style(JTable table, Object value, boolean isSelected, int row) {
                if (value instanceof Long) {
                    setText(format.format(value));
                    setHorizontalAlignment(RIGHT);
                } else if (centerStrings && value instanceof String) {
                    setHorizontalAlignment(CENTER);
                }
            }
        };
    }

    private static DefaultTableCellRenderer profitRenderer(NumberFormat format, CopilotConfig config) {
        return new StyledRenderer() {
            @Override
            protected void style(JTable table, Object value, boolean isSelected, int row) {
                if (value instanceof Long) {
                    long amount = (Long) value;
                    setText(format.format(amount));
                    setHorizontalAlignment(RIGHT);
                    // Color profit/loss only if not selected
                    if (!isSelected) {
                        if (amount > 0) { setForeground(config.profitAmountColor()); } else if (amount < 0) {
                            setForeground(config.lossAmountColor());
                        } else {
                            setForeground(LIGHT_GRAY_COLOR);
                        }
                    }
                }
            }
        };
    }
}
