package copilot.ui.components;

import static javax.swing.BorderFactory.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.controller.*;
import copilot.model.*;
import lombok.extern.slf4j.*;
import net.runelite.client.*;
import net.runelite.client.config.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.*;

@Slf4j
public class ItemSearchMultiSelect extends JPanel {

    private static final int ITEM_HEIGHT = 20; // Fixed height for each item
    private static final int VISIBLE_ITEMS = 15; // Number of visible items at once

    private final String placeholderText;
    private final JTextField displayField;
    private final JWindow dropdownWindow;
    private final ItemCheckList itemsList;
    private final JScrollPane scrollPane;
    private final JPanel actionButtonsPanel;
    private final Supplier<Set<Integer>> allItemIdsSupplier;
    private final BiFunction<String, Set<Integer>, List<ItemIdName>> searchFunc;
    private final Consumer<Set<Integer>> onItemSelectionChanged;
    private final Supplier<Set<Integer>> selectedItemsGetter;
    private List<ItemIdName> currentSearchResults = new ArrayList<>();

    /**
     * Builds the standard "Items filter..." search field used by the flips dialog tabs.
     */
    public static ItemSearchMultiSelect itemsFilter(Component parent,
                                                    Items itemController,
                                                    Supplier<Set<Integer>> selectedItemsGetter,
                                                    Consumer<Set<Integer>> onItemSelectionChanged) {
        var field = new ItemSearchMultiSelect(
                selectedItemsGetter,
                itemController::allItemIds,
                itemController::search,
                onItemSelectionChanged,
                "Items filter...",
                SwingUtilities.getWindowAncestor(parent));
        field.setMinimumSize(new Dimension(300, 0)); field.setToolTipText("Search by item name");
        return field;
    }

    public ItemSearchMultiSelect(
            Supplier<Set<Integer>> selectedItemsGetter,
            Supplier<Set<Integer>> allItemIdsSupplier,
            BiFunction<String, Set<Integer>, List<ItemIdName>> searchFunc,
            Consumer<Set<Integer>> onItemSelectionChanged,
            String placeholderText,
            Window windowAncestor) {
        super();
        this.allItemIdsSupplier = allItemIdsSupplier; this.selectedItemsGetter = selectedItemsGetter;
        this.searchFunc = searchFunc; this.onItemSelectionChanged = onItemSelectionChanged;
        this.placeholderText = placeholderText;

        setLayout(new BorderLayout());

        // Create main display field with placeholder
        displayField = new JTextField(placeholderText);
        displayField.setPreferredSize(new Dimension(200, displayField.getPreferredSize().height));
        displayField.setForeground(Color.GRAY); displayField.setFocusable(true); displayField.setEditable(true);

        // Remove focus border
        displayField.setBorder(createCompoundBorder(createLineBorder(DARK_GRAY_COLOR), createEmptyBorder(5, 5, 5, 5)));

        // Setup display field panel without border
        var dropdownPanel = new JPanel(new BorderLayout());
        dropdownPanel.add(displayField, BorderLayout.CENTER);
        dropdownPanel.setBackground(displayField.getBackground());

        // Add the dropdown panel directly without label
        add(dropdownPanel, BorderLayout.CENTER);
        setOpaque(true);
        setBackground(DARKER_GRAY_COLOR);

        dropdownWindow = new JWindow(windowAncestor);
        dropdownWindow.setAlwaysOnTop(RuneLite.getInjector().getInstance(RuneLiteConfig.class).gameAlwaysOnTop());
        dropdownWindow.setFocusableWindowState(true);

        // Create action buttons panel (Select All / Unselect All)
        actionButtonsPanel = createActionButtonsPanel();

        // Use virtual scroll panel instead of regular panel
        itemsList = new ItemCheckList(selectedItemsGetter, onItemSelectionChanged);
        scrollPane = new JScrollPane(itemsList);
        scrollPane.setPreferredSize(new Dimension(300, ITEM_HEIGHT * VISIBLE_ITEMS));
        scrollPane.getVerticalScrollBar().setUnitIncrement(ITEM_HEIGHT);

        // Create dropdown content panel
        var dropdownContent = new JPanel(new BorderLayout());
        dropdownContent.add(actionButtonsPanel, BorderLayout.NORTH);
        dropdownContent.add(scrollPane, BorderLayout.CENTER);
        dropdownContent.setBorder(createLineBorder(Color.DARK_GRAY));

        dropdownWindow.add(dropdownContent);
        setupListeners();
    }

    private JPanel createActionButtonsPanel() {
        var panel = new JPanel(new GridLayout(1, 2, 0, 0)); // Use GridLayout for equal width buttons
        panel.setBackground(DARKER_GRAY_COLOR); panel.setBorder(null);

        panel.add(selectionButton("Select All", this::selectAllItems));
        panel.add(selectionButton("Unselect All", this::unselectAllItems));
        return panel;
    }

    private JButton selectionButton(String text, Runnable action) {
        var button = new JButton(text);
        button.setPreferredSize(new Dimension(0, ITEM_HEIGHT + 5));
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 14f));
        button.setBackground(DARK_GRAY_COLOR); button.setForeground(Color.WHITE);
        button.setBorder(createRaisedBevelBorder()); button.setFocusPainted(false);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void selectAllItems() {
        Set<Integer> newState = allItemIdsSupplier.get(), selectedItems = selectedItemsGetter.get();
        if(!selectedItems.equals(newState)) {
            selectedItems.addAll(allItemIdsSupplier.get());
            onItemSelectionChanged.accept(new HashSet<>(selectedItems));
        }
        itemsList.repaint();
    }

    private void unselectAllItems() {
        var selectedItems = selectedItemsGetter.get();
        if(!selectedItems.isEmpty()) {
            selectedItems.clear();
            onItemSelectionChanged.accept(new HashSet<>(selectedItems));
        }
        itemsList.repaint();
    }

    private void updateDropdown(String searchText) {
        currentSearchResults = searchFunc.apply(searchText, selectedItemsGetter.get());

        SwingUtilities.invokeLater(() -> {
            log.debug("there are {} search results", currentSearchResults.size());

            // Update virtual scroll panel with new results
            itemsList.setItems(currentSearchResults);

            // Calculate dimensions
            Point location = getLocationOnScreen();
            int actionButtonsHeight = actionButtonsPanel.getPreferredSize().height;
            int scrollBarHeight = scrollPane.getHorizontalScrollBar().getPreferredSize().height;
            int contentHeight = Math.min(currentSearchResults.size() * ITEM_HEIGHT, ITEM_HEIGHT * VISIBLE_ITEMS);

            int totalHeight = contentHeight + actionButtonsHeight + scrollBarHeight + 12; // 12 for border and padding

            // Update window
            dropdownWindow.setLocation(location.x, location.y + getHeight());
            dropdownWindow.setSize(getWidth(), totalHeight); dropdownWindow.setVisible(true);
        });
    }

    private void setupListeners() {
        displayField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (displayField.getText().equals(placeholderText)) { displayField.setText(""); }
                updateDropdown(displayField.getText());
            }
        });

        displayField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                log.debug("focus gained {}",e.getCause());
                if (displayField.getText().equals(placeholderText)) { displayField.setText(""); }
            }

            @Override
            public void focusLost(FocusEvent e) {
                log.debug("focus lost to {} setting text to {}", e.getOppositeComponent(), placeholderText);
                displayField.setText(placeholderText); displayField.setForeground(Color.GRAY);
                dropdownWindow.setVisible(false);
            }
        });

        displayField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    updateDropdown(displayField.getText());
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // Allow escape key to close dropdown and remove focus
                    dropdownWindow.setVisible(false);
                    displayField.transferFocus();
                    e.consume();
                }
            }
        });

        dropdownWindow.addHierarchyListener(e -> {
            log.debug("hierarchy changed {} {} {}", dropdownWindow.isVisible(), dropdownWindow.isShowing(), e);
            if (!displayField.hasFocus()) { dropdownWindow.setVisible(false); }
        });
    }

    /** Swing renders only visible rows; selection remains owned by the surrounding filter. */
    static class ItemCheckList extends JList<ItemIdName> {
        ItemCheckList(Supplier<Set<Integer>> selectedItems, Consumer<Set<Integer>> onChange) {
            setBackground(DARK_GRAY_COLOR);
            setFixedCellHeight(ITEM_HEIGHT);
            setFixedCellWidth(0); // Rows stretch to the viewport; no need to measure every item.
            setFocusable(false);

            var row = new JPanel(new BorderLayout());
            row.setBorder(createEmptyBorder(1, 2, 1, 2));
            row.setBackground(DARK_GRAY_COLOR);
            var name = new JLabel();
            var check = new JCheckBox();
            check.setBackground(DARK_GRAY_COLOR);
            check.setPreferredSize(new Dimension(20, 16));
            check.setFont(check.getFont().deriveFont(10f));
            check.setMargin(new Insets(0, 0, 0, 0));
            row.add(name, BorderLayout.CENTER);
            row.add(check, BorderLayout.EAST);
            setCellRenderer((list, item, index, selected, focused) -> {
                name.setText(item.name);
                check.setSelected(selectedItems.get().contains(item.itemId));
                return row;
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    int index = locationToIndex(event.getPoint());
                    if (index < 0 || !getCellBounds(index, index).contains(event.getPoint())) return;
                    int id = getModel().getElementAt(index).itemId;
                    var selected = selectedItems.get();
                    if (!selected.remove(id)) { selected.add(id); }
                    onChange.accept(new HashSet<>(selected));
                    repaint();
                }
            });
        }

        void setItems(List<ItemIdName> items) { setListData(items.toArray(new ItemIdName[0])); }

        @Override public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(getWidth(), ITEM_HEIGHT * VISIBLE_ITEMS);
        }

        @Override public int getScrollableUnitIncrement(Rectangle area, int orientation, int direction) {
            return ITEM_HEIGHT;
        }

        @Override public int getScrollableBlockIncrement(Rectangle area, int orientation, int direction) {
            return ITEM_HEIGHT * VISIBLE_ITEMS;
        }

        @Override public boolean getScrollableTracksViewportWidth() { return true; }

        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
