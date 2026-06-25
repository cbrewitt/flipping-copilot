package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.SortDirection;
import com.flippingcopilot.ui.Paginator;
import com.flippingcopilot.ui.Spinner;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class PaginatedTablePanel<T> extends JPanel {

    private final String[] columnNames;
    private final Function<T, Object[]> rowMapper;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JPanel topPanel;
    private final JPanel leftControls;
    private final JPanel rightControls;
    private final JPanel spinnerOverlay;
    private final Spinner spinner;
    private final JLayeredPane layeredPane;

    private List<T> rows = new ArrayList<>();

    public PaginatedTablePanel(String[] columnNames, Function<T, Object[]> rowMapper) {
        this(columnNames, rowMapper, 25);
    }

    public PaginatedTablePanel(String[] columnNames, Function<T, Object[]> rowMapper, int rowHeight) {
        this.columnNames = columnNames;
        this.rowMapper = rowMapper;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftControls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightControls.setBackground(ColorScheme.DARK_GRAY_COLOR);

        topPanel.add(leftControls, BorderLayout.WEST);
        topPanel.add(rightControls, BorderLayout.EAST);
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
        table.setRowHeight(rowHeight);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setRowSorter(null); // Disable sorting
        table.getTableHeader().setReorderingAllowed(false);
        table.setFocusable(false);

        // Create layered pane for spinner overlay
        layeredPane = new JLayeredPane();
        layeredPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layeredPane.setOpaque(true);

        // Create spinner overlay with loading text
        // Create spinner with semi-transparent background
        spinner = new Spinner();
        spinner.show();
        spinnerOverlay = new JPanel(new GridBagLayout());
        spinnerOverlay.setBackground(ColorScheme.DARK_GRAY_COLOR);
        spinnerOverlay.setOpaque(true);
        spinnerOverlay.add(spinner);
        spinnerOverlay.setVisible(false); // Initially hidden

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        layeredPane.setLayout(new OverlayLayout(layeredPane));
        layeredPane.add(spinnerOverlay, JLayeredPane.MODAL_LAYER);
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);

        add(layeredPane, BorderLayout.CENTER);
    }

    public JPanel leftControls() {
        return leftControls;
    }

    public JPanel rightControls() {
        return rightControls;
    }

    public JTable table() {
        return table;
    }

    public void setRenderer(TableCellRenderer renderer, int... columns) {
        for (int column : columns) {
            table.getColumnModel().getColumn(column).setCellRenderer(renderer);
        }
    }

    public void centerColumns(int... columns) {
        setRenderer(centerRenderer(), columns);
    }

    public void rightColumns(int... columns) {
        setRenderer(rightTextRenderer(value -> value == null ? "" : value.toString()), columns);
    }

    public void moneyColumns(NumberFormat format, int... columns) {
        moneyColumns(format, false, columns);
    }

    public void moneyColumns(NumberFormat format, boolean centerStrings, int... columns) {
        setRenderer(moneyRenderer(format, centerStrings), columns);
    }

    public void profitColumns(NumberFormat format, FlippingCopilotConfig config, int... columns) {
        setRenderer(profitRenderer(format, config), columns);
    }

    public void setTopControlsVisible(boolean visible) {
        topPanel.setVisible(visible);
    }

    public void enableBuiltInSorting() {
        table.setRowSorter(new TableRowSorter<>(tableModel));
    }

    public void installHeaderSort(Supplier<String> currentColumn,
                                  Supplier<SortDirection> currentDirection,
                                  BiConsumer<String, SortDirection> onSortChanged) {
        // Add custom header click listener for sorting
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int columnIndex = table.getTableHeader().columnAtPoint(e.getPoint());
                if (columnIndex < 0 || columnIndex >= columnNames.length) {
                    return;
                }
                String clickedColumn = columnNames[columnIndex];
                SortDirection newDirection = SortDirection.DESC;
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
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow < 0 || viewRow >= table.getRowCount()) {
                    return;
                }
                table.setRowSelectionInterval(viewRow, viewRow);
                onPopup.accept(e, table.convertRowIndexToModel(viewRow));
            }
        });
    }

    public void installPageFooter(Paginator paginatorPanel, JComboBox<Integer> pageSizeComboBox) {
        // Create bottom panel with pagination
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel pageSizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pageSizePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizePanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JLabel pageSizeLabel = new JLabel("Page size:");
        pageSizeLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        pageSizePanel.add(pageSizeLabel);
        pageSizePanel.add(pageSizeComboBox);

        // Adjust paginator border to account for page size panel width
        paginatorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 0, pageSizePanel.getPreferredSize().width),
                paginatorPanel.getBorder()));
        bottomPanel.add(pageSizePanel, BorderLayout.WEST);
        bottomPanel.add(paginatorPanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    public JLabel setSpinnerText(String text) {
        // Create loading text label
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(label.getFont().deriveFont(14f));

        // Create a panel to hold the loading text and spinner horizontally
        JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        loadingPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        loadingPanel.setOpaque(false);

        // Add text and spinner to the loading panel
        loadingPanel.add(label);
        loadingPanel.add(spinner);

        // Add the loading panel to the spinner overlay
        spinnerOverlay.removeAll();
        spinnerOverlay.add(loadingPanel);
        spinnerOverlay.revalidate();
        spinnerOverlay.repaint();
        return label;
    }

    public void addOverlay(Component component, Integer layer) {
        layeredPane.add(component, layer);
    }

    public void setRows(List<T> newRows) {
        Runnable updateRows = () -> {
            rows = new ArrayList<>(newRows);
            tableModel.setRowCount(0);
            for (T row : rows) {
                tableModel.addRow(rowMapper.apply(row));
            }
            resizeAllColumns();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            updateRows.run();
        } else {
            SwingUtilities.invokeLater(updateRows);
        }
    }

    public T row(int modelRow) {
        return rows.get(modelRow);
    }

    public void setSpinnerVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            spinnerOverlay.setVisible(visible);
            table.setEnabled(!visible);
        });
    }

    public void resizeAllColumns() {
        for (int i = 0; i < table.getColumnCount(); i++) {
            resizeColumnWidth(i);
        }
    }

    private void resizeColumnWidth(int column) {
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

        tableColumn.setPreferredWidth(Math.min(preferredWidth, maxWidth));
    }

    public static DefaultTableCellRenderer centerRenderer() {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(JLabel.CENTER);
        return renderer;
    }

    public static DefaultTableCellRenderer moneyRenderer(NumberFormat format) {
        return moneyRenderer(format, false);
    }

    public static DefaultTableCellRenderer moneyRenderer(NumberFormat format, boolean centerStrings) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    setText(format.format(value));
                    setHorizontalAlignment(RIGHT);
                } else if (centerStrings && value instanceof String) {
                    setHorizontalAlignment(CENTER);
                }
                return c;
            }
        };
    }

    public static DefaultTableCellRenderer rightTextRenderer(Function<Object, String> formatter) {
        return textRenderer(formatter, JLabel.RIGHT);
    }

    public static DefaultTableCellRenderer textRenderer(Function<Object, String> formatter, int alignment) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setText(formatter.apply(value));
                setHorizontalAlignment(alignment);
                return c;
            }
        };
    }

    public static DefaultTableCellRenderer profitRenderer(NumberFormat format, FlippingCopilotConfig config) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    long amount = (Long) value;
                    setText(format.format(amount));
                    setHorizontalAlignment(RIGHT);
                    // Color profit/loss only if not selected
                    if (!isSelected) {
                        if (amount > 0) {
                            setForeground(config.profitAmountColor());
                        } else if (amount < 0) {
                            setForeground(config.lossAmountColor());
                        } else {
                            setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                        }
                    }
                }
                return c;
            }
        };
    }
}
