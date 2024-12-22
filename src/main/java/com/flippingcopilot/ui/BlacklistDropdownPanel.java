package com.flippingcopilot.ui;

import com.flippingcopilot.model.SuggestionPreferencesManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

@Singleton
public class BlacklistDropdownPanel extends JPanel {
    private final SuggestionPreferencesManager suggestionPreferencesManager;
    private final JTextField displayField;
    private final JWindow dropdownWindow;
    private final JPanel resultsPanel;
    private final JScrollPane scrollPane;
    private final JTextField searchField;
    private final ClientThread clientThread;
    private Window parentWindow;

    @Inject
    public BlacklistDropdownPanel(SuggestionPreferencesManager suggestionPreferencesManager, ClientThread clientThread) {
        this.suggestionPreferencesManager = suggestionPreferencesManager;
        this.clientThread = clientThread;

        setLayout(new BorderLayout());

        // Create main display field with placeholder
        displayField = new JTextField("Search an item...");
        displayField.setEditable(true);
        displayField.setPreferredSize(new Dimension(12, displayField.getPreferredSize().height));
        displayField.setForeground(Color.GRAY);

        // Create label
        JLabel label = new JLabel("Blacklist:");
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        label.setOpaque(true);
        label.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Setup display field panel
        JPanel dropdownPanel = new JPanel(new BorderLayout());
        dropdownPanel.add(displayField, BorderLayout.CENTER);
        dropdownPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        // Create container panel
        JPanel containerPanel = new JPanel(new BorderLayout(5, 0));
        containerPanel.add(label, BorderLayout.WEST);
        containerPanel.add(dropdownPanel, BorderLayout.CENTER);
        containerPanel.setOpaque(true);
        containerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        add(containerPanel, BorderLayout.CENTER);

        // Setup dropdown components
        dropdownWindow = new JWindow();
        searchField = new JTextField();
        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));

        scrollPane = new JScrollPane(resultsPanel);
        scrollPane.setPreferredSize(new Dimension(300, 400));

        // Create dropdown content panel
        JPanel dropdownContent = new JPanel(new BorderLayout());
        dropdownContent.add(searchField, BorderLayout.NORTH);
        dropdownContent.add(scrollPane, BorderLayout.CENTER);
        dropdownContent.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        dropdownWindow.add(dropdownContent);

        setupListeners();
    }

    private void updateDropdown(String searchText) {
        if (parentWindow == null) {
            parentWindow = SwingUtilities.getWindowAncestor(this);
        }

        clientThread.invoke(() -> {
            // Get fresh data
            List<Pair<Integer, String>> searchResults = suggestionPreferencesManager.search(searchText);
            Set<Integer> blockedItems = new HashSet<>(suggestionPreferencesManager.blockedItems());

            // Update results panel
            resultsPanel.removeAll();
            for (Pair<Integer, String> item : searchResults) {
                resultsPanel.add(createItemPanel(item, blockedItems));
            }

            SwingUtilities.invokeLater(() -> {
                // Calculate dimensions
                Point location = getLocationOnScreen();
                int searchHeight = searchField.getPreferredSize().height;
                int scrollBarHeight = scrollPane.getHorizontalScrollBar().getPreferredSize().height;
                int contentHeight = Arrays.stream(resultsPanel.getComponents())
                        .mapToInt(comp -> comp.getPreferredSize().height)
                        .sum();

                int totalHeight = Math.min(
                        contentHeight + searchHeight + scrollBarHeight + 12, // 12 for border and padding
                        400 // Maximum height
                );

                // Update window
                dropdownWindow.setLocation(location.x, location.y + getHeight());
                dropdownWindow.setSize(getWidth(), totalHeight);
                dropdownWindow.setVisible(true);

                // Update UI
                resultsPanel.revalidate();
                resultsPanel.repaint();
                searchField.setText(searchText);
                searchField.requestFocus();
            });
        });
    }

    private void setupListeners() {
        // Display field mouse listener for click detection
        displayField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!dropdownWindow.isVisible() && displayField.getText().isEmpty()) {
                    updateDropdown("");
                }
            }
        });

        // Display field focus listener
        displayField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (displayField.getText().equals("Search an item...")) {
                    displayField.setText("");
                    displayField.setForeground(Color.WHITE);
                }
                if (!dropdownWindow.isVisible() && displayField.getText().isEmpty()) {
                    updateDropdown("");
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (displayField.getText().isEmpty()) {
                    displayField.setText("Search an item...");
                    displayField.setForeground(Color.GRAY);
                    dropdownWindow.setVisible(false);
                }
            }
        });

        // Display field key listener
        displayField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    updateDropdown(displayField.getText());
                    e.consume();
                }
            }
        });

        // Global click listener to close dropdown and handle field unfocus
        Toolkit.getDefaultToolkit().addAWTEventListener(
                event -> {
                    if (event instanceof MouseEvent) {
                        MouseEvent mouseEvent = (MouseEvent) event;
                        if (mouseEvent.getID() == MouseEvent.MOUSE_PRESSED) {
                            Component source = mouseEvent.getComponent();
                            if (!isChildOf(source, dropdownWindow) && source != displayField) {
                                dropdownWindow.setVisible(false);
                                displayField.transferFocus();  // Remove focus from the field
                                displayField.setText("Search an item...");
                                displayField.setForeground(Color.GRAY);
                            }
                        }
                    }
                },
                AWTEvent.MOUSE_EVENT_MASK
        );
    }

    private JPanel createItemPanel(Pair<Integer, String> item, Set<Integer> blockedItemIds) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));

        JLabel nameLabel = new JLabel(item.getValue());
        panel.add(nameLabel, BorderLayout.CENTER);

        boolean isBlocked = blockedItemIds.contains(item.getKey());
        JButton toggleButton = new JButton(isBlocked ? BlacklistIcons.createXIcon() : BlacklistIcons.createTickIcon());
        toggleButton.setBorderPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setPreferredSize(new Dimension(16, 16));

        toggleButton.addActionListener(e -> {
            if (isBlocked) {
                suggestionPreferencesManager.unblockItem(item.getKey());
                toggleButton.setIcon(BlacklistIcons.createTickIcon());
            } else {
                suggestionPreferencesManager.blockItem(item.getKey());
                toggleButton.setIcon(BlacklistIcons.createXIcon());
            }
            panel.revalidate();
            panel.repaint();
        });

        panel.add(toggleButton, BorderLayout.EAST);
        return panel;
    }

    private boolean isChildOf(Component component, Container container) {
        if (component == null) return false;
        if (component == container) return true;
        return isChildOf(component.getParent(), container);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> {
            parentWindow = SwingUtilities.getWindowAncestor(this);
        });
    }
}