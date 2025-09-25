package com.flippingcopilot.ui.components;

import com.flippingcopilot.model.ItemIdName;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class ItemSearchMultiSelect extends JPanel {

    private static final int ITEM_HEIGHT = 20; // Fixed height for each item
    private static final int VISIBLE_ITEMS = 15; // Number of visible items at once


    private final String placeholderText;
    private final JTextField displayField;
    private final JWindow dropdownWindow;
    private final VirtualScrollPanel virtualScrollPanel;
    private final JScrollPane scrollPane;
    private final JPanel actionButtonsPanel;
    private final Supplier<Set<Integer>> allItemIdsSupplier;
    private final BiFunction<String, Set<Integer>, List<ItemIdName>> searchFunc;
    private final Consumer<Set<Integer>> onItemSelectionChanged;
    private final Supplier<Set<Integer>> selectedItemsGetter;
    private List<ItemIdName> currentSearchResults = new ArrayList<>();

    public ItemSearchMultiSelect(
            Supplier<Set<Integer>> selectedItemsGetter,
            Supplier<Set<Integer>> allItemIdsSupplier,
            BiFunction<String, Set<Integer>, List<ItemIdName>> searchFunc,
            Consumer<Set<Integer>> onItemSelectionChanged,
            String placeholderText,
            Window windowAncestor) {
        super();
        this.allItemIdsSupplier = allItemIdsSupplier;
        this.selectedItemsGetter = selectedItemsGetter;
        this.searchFunc = searchFunc;
        this.onItemSelectionChanged = onItemSelectionChanged;
        this.placeholderText = placeholderText;

        setLayout(new BorderLayout());

        // Create main display field with placeholder
        displayField = new JTextField(placeholderText);
        displayField.setPreferredSize(new Dimension(200, displayField.getPreferredSize().height));
        displayField.setForeground(Color.GRAY);
        displayField.setFocusable(true);
        displayField.setEditable(true);


        // Remove focus border
        displayField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        // Setup display field panel without border
        JPanel dropdownPanel = new JPanel(new BorderLayout());
        dropdownPanel.add(displayField, BorderLayout.CENTER);
        dropdownPanel.setBackground(displayField.getBackground());

        // Add the dropdown panel directly without label
        add(dropdownPanel, BorderLayout.CENTER);
        setOpaque(true);
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        dropdownWindow = new JWindow(windowAncestor);
        dropdownWindow.setAlwaysOnTop(RuneLite.getInjector().getInstance(RuneLiteConfig.class).gameAlwaysOnTop());
        dropdownWindow.setFocusableWindowState(true);

        // Create action buttons panel (Select All / Unselect All)
        actionButtonsPanel = createActionButtonsPanel();

        // Use virtual scroll panel instead of regular panel
        virtualScrollPanel = new VirtualScrollPanel();
        scrollPane = new JScrollPane(virtualScrollPanel);
        scrollPane.setPreferredSize(new Dimension(300, ITEM_HEIGHT * VISIBLE_ITEMS));
        scrollPane.getVerticalScrollBar().setUnitIncrement(ITEM_HEIGHT);

        // Create dropdown content panel
        JPanel dropdownContent = new JPanel(new BorderLayout());
        dropdownContent.add(actionButtonsPanel, BorderLayout.NORTH);
        dropdownContent.add(scrollPane, BorderLayout.CENTER);
        dropdownContent.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        dropdownWindow.add(dropdownContent);
        setupListeners();
    }

    private JPanel createActionButtonsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 0, 0)); // Use GridLayout for equal width buttons
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(null);

        JButton selectAllButton = new JButton("Select All");
        selectAllButton.setPreferredSize(new Dimension(0, ITEM_HEIGHT+5)); // Use ITEM_HEIGHT for button height
        selectAllButton.setFont(selectAllButton.getFont().deriveFont(Font.PLAIN, 14f));
        selectAllButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        selectAllButton.setForeground(Color.WHITE);
        selectAllButton.setBorder(BorderFactory.createRaisedBevelBorder());
        selectAllButton.setFocusPainted(false);

        JButton unselectAllButton = new JButton("Unselect All");
        unselectAllButton.setPreferredSize(new Dimension(0, ITEM_HEIGHT+5)); // Use ITEM_HEIGHT for button height
        unselectAllButton.setFont(unselectAllButton.getFont().deriveFont(Font.PLAIN, 14f));
        unselectAllButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        unselectAllButton.setForeground(Color.WHITE);
        unselectAllButton.setBorder(BorderFactory.createRaisedBevelBorder());
        unselectAllButton.setFocusPainted(false);

        selectAllButton.addActionListener(e -> selectAllItems());
        unselectAllButton.addActionListener(e -> unselectAllItems());

        panel.add(selectAllButton);
        panel.add(unselectAllButton);

        return panel;
    }

    private void selectAllItems() {
        Set<Integer> newState = allItemIdsSupplier.get();
        Set<Integer> selectedItems = selectedItemsGetter.get();
        if(!selectedItems.equals(newState)) {
            selectedItems.addAll(allItemIdsSupplier.get());
            onItemSelectionChanged.accept(new HashSet<>(selectedItems));
        }
        virtualScrollPanel.refreshItems();
    }

    private void unselectAllItems() {
        Set<Integer> selectedItems = selectedItemsGetter.get();
        if(!selectedItems.isEmpty()) {
            selectedItems.clear();
            onItemSelectionChanged.accept(new HashSet<>(selectedItems));
        }
        virtualScrollPanel.refreshItems();
    }

    private void updateDropdown(String searchText) {
        currentSearchResults = searchFunc.apply(searchText, selectedItemsGetter.get());

        SwingUtilities.invokeLater(() -> {
            log.debug("there are {} search results", currentSearchResults.size());

            // Update virtual scroll panel with new results
            virtualScrollPanel.setItems(currentSearchResults);

            // Calculate dimensions
            Point location = getLocationOnScreen();
            int actionButtonsHeight = actionButtonsPanel.getPreferredSize().height;
            int scrollBarHeight = scrollPane.getHorizontalScrollBar().getPreferredSize().height;
            int contentHeight = Math.min(currentSearchResults.size() * ITEM_HEIGHT, ITEM_HEIGHT * VISIBLE_ITEMS);

            int totalHeight = contentHeight + actionButtonsHeight + scrollBarHeight + 12; // 12 for border and padding

            // Update window
            dropdownWindow.setLocation(location.x, location.y + getHeight());
            dropdownWindow.setSize(getWidth(), totalHeight);
            dropdownWindow.setVisible(true);
        });
    }

    private void setupListeners() {
        displayField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                log.debug("clicked");
                if (displayField.getText().equals(placeholderText)) {
                    displayField.setText("");
                }
                updateDropdown(displayField.getText());
            }
        });

        displayField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                log.debug("focus gained {}",e.getCause());
                if (displayField.getText().equals(placeholderText)) {
                    displayField.setText("");
                }

//                FocusEvent.Cause c = e.getCause();
//                if(FocusEvent.Cause.MOUSE_EVENT.equals(c)) {
//                    if (displayField.getText().equals(placeholderText)) {
//                        displayField.setText("");
//                        updateDropdown(displayField.getText());
//                    }
//                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                log.debug("focus lost to {} setting text to {}", e.getOppositeComponent(), placeholderText);
                displayField.setText(placeholderText);
                displayField.setForeground(Color.GRAY);
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

        dropdownWindow.addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                log.debug("hierarchy changed {} {} {}", dropdownWindow.isVisible(), dropdownWindow.isShowing(), e);
                if(!displayField.hasFocus()) {
                    dropdownWindow.setVisible(false);
                }
            }
        });
    }

    /**
     * Virtual scrolling panel that only renders visible items
     */
    private class VirtualScrollPanel extends JPanel implements Scrollable {
        private List<ItemIdName> items = new ArrayList<>();
        private final Map<Integer, JPanel> panelCache = new HashMap<>();
        private int firstVisibleIndex = 0;
        private int lastVisibleIndex = 0;

        public VirtualScrollPanel() {
            setLayout(null); // Use absolute positioning
            setBackground(ColorScheme.DARK_GRAY_COLOR);

            // Add viewport change listener to handle scrolling
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    updateVisibleItems();
                }
            });
        }

        public void setItems(List<ItemIdName> items) {
            this.items = items;
            panelCache.clear(); // Clear cache when items change

            // Set preferred size based on total items
            setPreferredSize(new Dimension(getWidth(), items.size() * ITEM_HEIGHT));

            updateVisibleItems();
            revalidate();
            repaint();
        }

        public void refreshItems() {
            panelCache.clear(); // Clear cache to force recreation of panels with updated icons
            updateVisibleItems();
        }

        private void updateVisibleItems() {
            if (items.isEmpty()) {
                removeAll();
                return;
            }

            Rectangle viewRect = scrollPane.getViewport().getViewRect();
            firstVisibleIndex = Math.max(0, viewRect.y / ITEM_HEIGHT);
            lastVisibleIndex = Math.min(items.size() - 1,
                    (viewRect.y + viewRect.height) / ITEM_HEIGHT + 1);

            // Remove all components and add only visible ones
            removeAll();

            Set<Integer> selectedItems = selectedItemsGetter.get();
            for (int i = firstVisibleIndex; i <= lastVisibleIndex; i++) {
                JPanel itemPanel = getOrCreateItemPanel(i, selectedItems);
                itemPanel.setBounds(0, i * ITEM_HEIGHT, getWidth(), ITEM_HEIGHT);
                add(itemPanel);
            }

            revalidate();
            repaint();
        }

        private JPanel getOrCreateItemPanel(int index, Set<Integer> selectedItems) {
            if (index < 0 || index >= items.size()) {
                return new JPanel();
            }
            if (panelCache.containsKey(index)) {
                return panelCache.get(index);
            }
            ItemIdName item = items.get(index);
            JPanel panel = createItemPanel(item, selectedItems);
            panelCache.put(index, panel);
            return panel;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Update visible items when painting
            updateVisibleItems();
        }

        // Scrollable interface methods
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(getWidth(), ITEM_HEIGHT * VISIBLE_ITEMS);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ITEM_HEIGHT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ITEM_HEIGHT * VISIBLE_ITEMS;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private JPanel createItemPanel(ItemIdName item, Set<Integer> selectedItems) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, ITEM_HEIGHT));
        panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, ITEM_HEIGHT));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setOpaque(true);

        JLabel nameLabel = new JLabel(item.name);
        panel.add(nameLabel, BorderLayout.CENTER);

        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(selectedItems.contains(item.itemId));
        checkBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        checkBox.setPreferredSize(new Dimension(20, 16));

        // Scale down the checkbox icon
        checkBox.setFont(checkBox.getFont().deriveFont(10f));
        checkBox.setMargin(new Insets(0, 0, 0, 0));

        Runnable onClick = () -> {
            if (selectedItems.contains(item.itemId)) {
                selectedItems.remove(item.itemId);
                checkBox.setSelected(false);
            } else {
                selectedItems.add(item.itemId);
                checkBox.setSelected(true);
            }
            onItemSelectionChanged.accept(new HashSet<>(selectedItems));
            panel.revalidate();
            panel.repaint();
        };

        checkBox.addActionListener(e -> onClick.run());

        panel.add(checkBox, BorderLayout.EAST);

        // Add hover effect - clicking anywhere on panel toggles checkbox
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent ee) {
                onClick.run();
            }
        });

        return panel;
    }
}