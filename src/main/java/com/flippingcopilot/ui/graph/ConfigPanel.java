package com.flippingcopilot.ui.graph;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Panel for configuring graph settings
 */
@Slf4j
public class ConfigPanel extends JPanel {
    private final Map<String, Component> configComponents = new HashMap<>();
    private Runnable onApplyCallback;

    public ConfigPanel() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new GridBagLayout());
        settingsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(5, 5, 5, 5);

        // Add a title
        JLabel titleLabel = new JLabel("Graph Settings");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
        settingsPanel.add(titleLabel, c);
        c.gridy++;

        // Get all fields from Config class
        Field[] fields = Config.class.getDeclaredFields();

        for (Field field : fields) {
            // Skip complex types like Stroke and constants we don't want to modify
            if (field.getType() == Stroke.class ||
                    field.getName().equals("DOTTED_STROKE") ||
                    field.getName().equals("GRID_STROKE") ||
                    field.getName().equals("NORMAL_STROKE") ||
                    field.getName().equals("SELECTION_STROKE")) {
                // Removed CONNECT_POINTS from the skip list
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(null);

                if (value instanceof Integer) {
                    addIntegerSetting(settingsPanel, c, field.getName(), (Integer) value);
                } else if (value instanceof Color) {
                    addColorSetting(settingsPanel, c, field.getName(), (Color) value);
                } else if (value instanceof Boolean) {
                    addBooleanSetting(settingsPanel, c, field.getName(), (Boolean) value);
                } else if (value instanceof Float) {
                    addFloatSetting(settingsPanel, c, field.getName(), (Float) value);
                }
            } catch (Exception e) {
                log.error("Error adding setting for " + field.getName(), e);
            }
        }

        // Add a scrollpane to make the settings scrollable
        JScrollPane scrollPane = new JScrollPane(settingsPanel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Add apply button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JButton applyButton = new JButton("Apply");
        applyButton.setFocusPainted(false);
        applyButton.setBackground(ColorScheme.BRAND_ORANGE);
        applyButton.setForeground(Color.WHITE);
        applyButton.addActionListener(e -> {
            applySettings();
            // Return to graph view after applying settings
            if (onApplyCallback != null) {
                onApplyCallback.run();
            }
        });

        buttonPanel.add(applyButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Sets the callback to be executed after applying settings
     * @param callback runnable to execute after applying settings
     */
    public void setOnApplyCallback(Runnable callback) {
        this.onApplyCallback = callback;
    }

    private void addIntegerSetting(JPanel panel, GridBagConstraints c, String name, Integer value) {
        JLabel label = new JLabel(formatFieldName(name));
        label.setForeground(Color.WHITE);
        panel.add(label, c);
        c.gridx = 1;

        JSpinner spinner = new JSpinner(new SpinnerNumberModel((int) value, 0, 1000, 1));
        spinner.setToolTipText(name);
        configComponents.put(name, spinner);
        panel.add(spinner, c);

        c.gridx = 0;
        c.gridy++;
    }

    private void addFloatSetting(JPanel panel, GridBagConstraints c, String name, Float value) {
        JLabel label = new JLabel(formatFieldName(name));
        label.setForeground(Color.WHITE);
        panel.add(label, c);
        c.gridx = 1;

        JSpinner spinner = new JSpinner(new SpinnerNumberModel((float) value, 0.0f, 100.0f, 0.5f));
        spinner.setToolTipText(name);
        configComponents.put(name, spinner);
        panel.add(spinner, c);

        c.gridx = 0;
        c.gridy++;
    }

    private void addBooleanSetting(JPanel panel, GridBagConstraints c, String name, Boolean value) {
        JLabel label = new JLabel(formatFieldName(name));
        label.setForeground(Color.WHITE);
        panel.add(label, c);
        c.gridx = 1;

        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(value);
        checkBox.setToolTipText(name);
        checkBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        configComponents.put(name, checkBox);
        panel.add(checkBox, c);

        c.gridx = 0;
        c.gridy++;
    }

    private void addColorSetting(JPanel panel, GridBagConstraints c, String name, Color value) {
        JLabel label = new JLabel(formatFieldName(name));
        label.setForeground(Color.WHITE);
        panel.add(label, c);
        c.gridx = 1;

        JPanel colorPanel = new JPanel();
        colorPanel.setBackground(value);
        colorPanel.setPreferredSize(new Dimension(30, 20));
        colorPanel.setBorder(BorderFactory.createLineBorder(Color.WHITE));

        JButton colorButton = new JButton("Choose...");
        colorButton.setToolTipText(name);
        colorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, "Choose " + formatFieldName(name), colorPanel.getBackground());
            if (newColor != null) {
                colorPanel.setBackground(newColor);
            }
        });

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        wrapper.add(colorPanel);
        wrapper.add(colorButton);

        configComponents.put(name, wrapper);
        panel.add(wrapper, c);

        c.gridx = 0;
        c.gridy++;
    }

    private String formatFieldName(String name) {
        StringBuilder result = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                result.append(' ').append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString().trim();
    }

    /**
     * Apply the settings
     */
    private void applySettings() {
        try {
            for (Map.Entry<String, Component> entry : configComponents.entrySet()) {
                String fieldName = entry.getKey();
                Component component = entry.getValue();

                Field field = Config.class.getDeclaredField(fieldName);
                field.setAccessible(true);

                if (component instanceof JSpinner) {
                    JSpinner spinner = (JSpinner) component;
                    if (field.getType() == Integer.TYPE || field.getType() == Integer.class) {
                        field.set(null, ((Number) spinner.getValue()).intValue());
                    } else if (field.getType() == Float.TYPE || field.getType() == Float.class) {
                        field.set(null, ((Number) spinner.getValue()).floatValue());
                    }
                } else if (component instanceof JCheckBox) {
                    JCheckBox checkBox = (JCheckBox) component;
                    field.set(null, checkBox.isSelected());
                } else if (component instanceof JPanel) {
                    // For color settings, we stored a panel
                    JPanel wrapper = (JPanel) component;
                    JPanel colorPanel = (JPanel) wrapper.getComponent(0);
                    field.set(null, colorPanel.getBackground());
                }
            }
            log.info("Applied graph settings");
        } catch (Exception e) {
            log.error("Error applying settings", e);
            JOptionPane.showMessageDialog(this, "Error applying settings: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Creates a button with an icon for use in UI
     * Place the gear on the top right by adjusting the layout constraints when adding it
     *
     * @param icon Icon to display
     * @param tooltip Tooltip text
     * @param onClick Action to perform when clicked
     * @return JLabel that acts as a button
     */
    public static JLabel buildButton(Image icon, String tooltip, Runnable onClick) {
        JLabel button = new JLabel(new ImageIcon(icon));
        button.setToolTipText(tooltip);
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                onClick.run();
            }
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        });
        return button;
    }
}