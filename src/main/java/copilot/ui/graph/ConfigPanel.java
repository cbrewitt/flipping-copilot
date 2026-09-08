package copilot.ui.graph;

import java.util.function.*;
import static copilot.ui.UIUtilities.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.manager.*;
import copilot.ui.graph.model.Config;
import lombok.extern.slf4j.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

@Slf4j
public class ConfigPanel extends JPanel {
    private final Runnable onChangeCallback;
    private final GraphSettings configManager;
    private final Config configInstance;

    public ConfigPanel(GraphSettings configManager, Runnable callback, Runnable onBackCallback) {
        this.configManager = configManager; configInstance = configManager.getConfig();
        onChangeCallback = callback;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(DARKER_GRAY_COLOR);

        var settingsPanel = new JPanel();
        settingsPanel.setLayout(new GridBagLayout()); settingsPanel.setBackground(DARKER_GRAY_COLOR);

        var c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0; c.anchor = GridBagConstraints.NORTH; c.gridx = 0;
        c.gridy = 0; c.insets = new Insets(5, 5, 5, 5);

        var titleLabel = coloredLabel("Graph Settings", Color.WHITE); titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
        settingsPanel.add(titleLabel, c);
        c.gridy++;

        // boolean
        addBooleanSetting(settingsPanel, c, "connectPoints", configInstance.isConnectPoints(), configInstance::setConnectPoints);
        addBooleanSetting(settingsPanel, c, "showSuggestedPriceLines", configInstance.isShowSuggestedPriceLines(), configInstance::setShowSuggestedPriceLines);

        // colours
        addColorSetting(settingsPanel, c, "lowColor", configInstance.getLowColor(), configInstance::setLowColor);
        addColorSetting(settingsPanel, c, "highColor", configInstance.getHighColor(), configInstance::setHighColor);
        addColorSetting(settingsPanel, c, "lowShadeColor", configInstance.getLowShadeColor(), configInstance::setLowShadeColor);
        addColorSetting(settingsPanel, c, "highShadeColor", configInstance.getHighShadeColor(), configInstance::setHighShadeColor);
        addColorSetting(settingsPanel, c, "backgroundColor", configInstance.getBackgroundColor(), configInstance::setBackgroundColor);
        addColorSetting(settingsPanel, c, "plotAreaColor", configInstance.getPlotAreaColor(), configInstance::setPlotAreaColor);
        addColorSetting(settingsPanel, c, "textColor", configInstance.getTextColor(), configInstance::setTextColor);
        addColorSetting(settingsPanel, c, "axisColor", configInstance.getAxisColor(), configInstance::setAxisColor);
        addColorSetting(settingsPanel, c, "gridColor", configInstance.getGridColor(), configInstance::setGridColor);

        // Add a filler component to push everything to the top
        var fillerConstraints = new GridBagConstraints();
        fillerConstraints.gridx = 0; fillerConstraints.gridy = c.gridy;
        fillerConstraints.gridwidth = GridBagConstraints.REMAINDER; fillerConstraints.fill = GridBagConstraints.BOTH;
        fillerConstraints.weightx = 1.0; fillerConstraints.weighty = 1.0;

        var fillerPanel = darkPanel(new FlowLayout(), DARKER_GRAY_COLOR);
        settingsPanel.add(fillerPanel, fillerConstraints);

        var scrollPane = new JScrollPane(settingsPanel);
        scrollPane.setBorder(null); scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        var buttonPanel = darkPanel(new FlowLayout(FlowLayout.RIGHT), DARKER_GRAY_COLOR);
        var backButton = new JButton("Back");
        backButton.setFocusPainted(false); backButton.setBackground(BRAND_ORANGE);
        backButton.setForeground(Color.WHITE);
        backButton.addActionListener(e -> onBackCallback.run());
        buttonPanel.add(backButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void addBooleanSetting(JPanel panel, GridBagConstraints c, String name, boolean value, Consumer<Boolean> setter) {
        var checkBox = new JCheckBox();
        checkBox.setSelected(value); checkBox.setToolTipText(name); checkBox.setBackground(DARKER_GRAY_COLOR);
        checkBox.addItemListener(e -> applySetting(() -> setter.accept(checkBox.isSelected())));
        addSetting(panel, c, name, checkBox);
    }

    private void addColorSetting(JPanel panel, GridBagConstraints c, String name, Color value, Consumer<Color> setter) {
        var colorPanel = darkPanel(new FlowLayout(), value);
        colorPanel.setPreferredSize(new Dimension(30, 20));
        colorPanel.setBorder(BorderFactory.createLineBorder(Color.WHITE));

        var colorButton = new JButton("Choose...");
        colorButton.setToolTipText(name);
        colorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, "Choose " + formatFieldName(name), colorPanel.getBackground());
            if (newColor != null) { colorPanel.setBackground(newColor); }
        });

        var wrapper = darkPanel(new FlowLayout(FlowLayout.LEFT), DARKER_GRAY_COLOR);
        wrapper.add(colorPanel);
        wrapper.add(colorButton);

        colorPanel.addPropertyChangeListener("background",
                e -> applySetting(() -> setter.accept((Color) e.getNewValue())));
        addSetting(panel, c, name, wrapper);
    }

    private String formatFieldName(String name) {
        if ("showSuggestedPriceLines".equals(name)) { return "show suggested/offer price lines"; }
        var result = new StringBuilder();
        for (char ch : name.toCharArray()) {
            if (Character.isUpperCase(ch)) { result.append(' ').append(Character.toLowerCase(ch)); } else {
                result.append(ch);
            }
        }
        return result.toString().trim();
    }

    private void addSetting(JPanel panel, GridBagConstraints c, String name, JComponent control) {
        var label = coloredLabel(formatFieldName(name), Color.WHITE);
        panel.add(label, c);
        c.gridx = 1;

        panel.add(control, c);
        c.gridx = 0;
        c.gridy++;
    }

    private void applySetting(Runnable update) {
        try {
            update.run();

            configManager.setConfig(configInstance);
            if (onChangeCallback != null) { onChangeCallback.run(); }
        } catch (Exception e) {
            log.error("Error applying settings", e);
            JOptionPane.showMessageDialog(this, "Error applying settings: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
