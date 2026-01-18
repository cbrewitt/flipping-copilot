package com.flippingcopilot.ui.graph;

import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.ui.graph.model.Config;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ConfigPanel extends JPanel {
    private final Map<String, Component> configComponents = new HashMap<>();
    private final Runnable onApplyCallback;
    private final PriceGraphConfigManager configManager;
    private final Config configInstance;

    public ConfigPanel(PriceGraphConfigManager configManager, Runnable callback) {
        this.configManager = configManager;
        this.configInstance = configManager.getConfig();
        this.onApplyCallback = callback;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new GridBagLayout());
        settingsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.NORTH;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(5, 5, 5, 5);

        JLabel titleLabel = new JLabel("Graph Settings");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
        settingsPanel.add(titleLabel, c);
        c.gridy++;

        // boolean
        addBooleanSetting(settingsPanel, c, "connectPoints", configInstance.isConnectPoints());
        addBooleanSetting(settingsPanel, c, "showSuggestedPriceLines", configInstance.isShowSuggestedPriceLines());

        // colours
        addColorSetting(settingsPanel, c, "lowColor", configInstance.getLowColor());
        addColorSetting(settingsPanel, c, "highColor", configInstance.getHighColor());
        addColorSetting(settingsPanel, c, "lowShadeColor", configInstance.getLowShadeColor());
        addColorSetting(settingsPanel, c, "highShadeColor", configInstance.getHighShadeColor());
        addColorSetting(settingsPanel, c, "backgroundColor", configInstance.getBackgroundColor());
        addColorSetting(settingsPanel, c, "plotAreaColor", configInstance.getPlotAreaColor());
        addColorSetting(settingsPanel, c, "textColor", configInstance.getTextColor());
        addColorSetting(settingsPanel, c, "axisColor", configInstance.getAxisColor());
        addColorSetting(settingsPanel, c, "gridColor", configInstance.getGridColor());

        // Add a filler component to push everything to the top
        GridBagConstraints fillerConstraints = new GridBagConstraints();
        fillerConstraints.gridx = 0;
        fillerConstraints.gridy = c.gridy;
        fillerConstraints.gridwidth = GridBagConstraints.REMAINDER;
        fillerConstraints.fill = GridBagConstraints.BOTH;
        fillerConstraints.weightx = 1.0;
        fillerConstraints.weighty = 1.0;

        JPanel fillerPanel = new JPanel();
        fillerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        settingsPanel.add(fillerPanel, fillerConstraints);

        JScrollPane scrollPane = new JScrollPane(settingsPanel);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JButton applyButton = new JButton("Apply");
        applyButton.setFocusPainted(false);
        applyButton.setBackground(ColorScheme.BRAND_ORANGE);
        applyButton.setForeground(Color.WHITE);
        applyButton.addActionListener(e -> {
            applySettings();
            if (onApplyCallback != null) {
                onApplyCallback.run();
            }
        });

        buttonPanel.add(applyButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void addBooleanSetting(JPanel panel, GridBagConstraints c, String name, boolean value) {
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
        if ("showSuggestedPriceLines".equals(name)) {
            return "show suggested/offer price lines";
        }
        StringBuilder result = new StringBuilder();
        for (char ch : name.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                result.append(' ').append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        return result.toString().trim();
    }

    private Color extractColor(String key) {
        JPanel wrapper = (JPanel) configComponents.get(key);
        JPanel colorPanel = (JPanel) wrapper.getComponent(0);
        return colorPanel.getBackground();
    }

    private void applySettings() {
        try {
            // boolean
            JCheckBox connectPointsBox = (JCheckBox) configComponents.get("connectPoints");
            configInstance.setConnectPoints(connectPointsBox.isSelected());
            JCheckBox showSuggestedPriceLinesBox = (JCheckBox) configComponents.get("showSuggestedPriceLines");
            configInstance.setShowSuggestedPriceLines(showSuggestedPriceLinesBox.isSelected());

            // colours
            configInstance.setLowColor(extractColor("lowColor"));
            configInstance.setHighColor(extractColor("highColor"));
            configInstance.setLowShadeColor(extractColor("lowShadeColor"));
            configInstance.setHighShadeColor(extractColor("highShadeColor"));
            configInstance.setBackgroundColor(extractColor("backgroundColor"));
            configInstance.setPlotAreaColor(extractColor("plotAreaColor"));
            configInstance.setTextColor(extractColor("textColor"));
            configInstance.setAxisColor(extractColor("axisColor"));
            configInstance.setGridColor(extractColor("gridColor"));

            configManager.setConfig(configInstance);
            log.debug("Applied and saved graph settings");
        } catch (Exception e) {
            log.error("Error applying settings", e);
            JOptionPane.showMessageDialog(this, "Error applying settings: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
