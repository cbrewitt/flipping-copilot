package com.flippingcopilot.ui;

import com.flippingcopilot.model.SuggestionPreferencesManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

@Slf4j
@Singleton
public class BlacklistDropdownPanel extends JPanel {
    private final SuggestionPreferencesManager preferencesManager;
    private final JTextField displayField;
    private final JWindow dropdownWindow;
    private final JPanel resultsPanel;
    private final JScrollPane scrollPane;
    private final JTextField searchField;
    private final ClientThread clientThread;

    @Inject
    public BlacklistDropdownPanel(SuggestionPreferencesManager preferencesManager, ClientThread clientThread) {
        super();
        this.preferencesManager = preferencesManager;
        this.clientThread = clientThread;

        setLayout(new BorderLayout());

        // Create main display field with placeholder
        displayField = new JTextField("Search an item...");
        displayField.setEditable(true);
        displayField.setPreferredSize(new Dimension(12, displayField.getPreferredSize().height));
        displayField.setForeground(Color.GRAY);

        // Create label
        JLabel label = new JLabel("Blocklist:");
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
        clientThread.invoke(() -> {
            // Get fresh data
            List<Pair<Integer, String>> searchResults = preferencesManager.search(searchText);
            Set<Integer> blockedItems = new HashSet<>(preferencesManager.blockedItems());

            SwingUtilities.invokeLater(() -> {

                // Update results panel
                resultsPanel.removeAll();
                for (Pair<Integer, String> item : searchResults) {
                    resultsPanel.add(createItemPanel(item, blockedItems));
                }
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
            });
        });
    }

    private void setupListeners() {

        displayField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                log.debug("mouse clicked");
                updateDropdown(displayField.getText());
            }
        });

        displayField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (displayField.getText().equals("Search an item...")) {
                    displayField.setText("");
                    updateDropdown(displayField.getText());
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                log.debug("focus lost");
                displayField.setText("Search an item...");
                displayField.setForeground(Color.GRAY);
                dropdownWindow.setVisible(false);
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
    }

    private JPanel createItemPanel(Pair<Integer, String> item, Set<Integer> blockedItemIds) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));

        JLabel nameLabel = new JLabel(item.getValue());
        panel.add(nameLabel, BorderLayout.CENTER);

        JButton toggleButton = new JButton(blockedItemIds.contains(item.getKey()) ? BlacklistIcons.createXIcon() : BlacklistIcons.createTickIcon());
        toggleButton.setBorderPainted(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setPreferredSize(new Dimension(16, 16));

         Runnable onClick = () -> {
            if (blockedItemIds.contains(item.getKey())) {
                preferencesManager.unblockItem(item.getKey());
                blockedItemIds.remove(item.getKey());
                toggleButton.setIcon(BlacklistIcons.createTickIcon());
            } else {
                preferencesManager.blockItem(item.getKey());
                blockedItemIds.add(item.getKey());
                toggleButton.setIcon(BlacklistIcons.createXIcon());
            }
            panel.revalidate();
            panel.repaint();
        };

        toggleButton.addActionListener(e -> onClick.run());

        panel.add(toggleButton, BorderLayout.EAST);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent ee) {
                onClick.run();
            }
        });
        return panel;
    }
}