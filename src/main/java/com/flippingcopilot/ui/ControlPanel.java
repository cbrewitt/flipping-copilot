package com.flippingcopilot.ui;

import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;

@Singleton
public class ControlPanel extends JPanel {

    private final SuggestionPreferencesManager preferencesManager;
    private final JPanel timeframePanel;
    private final JToggleButton btn5m;
    private final JToggleButton btn30m;
    private final JToggleButton btn2h;
    private final JToggleButton btn8h;

    @Inject
    public ControlPanel(
            SuggestionManager suggestionManager,
            SuggestionPreferencesManager preferencesManager) {
        this.preferencesManager = preferencesManager;
        
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setBounds(0, 0, 300, 150);

        // Add timeframe buttons
        timeframePanel = new JPanel();
        timeframePanel.setLayout(new BoxLayout(timeframePanel, BoxLayout.Y_AXIS));
        timeframePanel.setOpaque(false);
        JLabel timeframeLabel = new JLabel("How often do you check offers?");
        timeframeLabel.setHorizontalAlignment(SwingConstants.LEFT);
        timeframeLabel.setMaximumSize(timeframeLabel.getPreferredSize());
        JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        labelPanel.setOpaque(false);
        labelPanel.add(timeframeLabel);
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 4, 0, 0));
        buttonPanel.setOpaque(false);
        ButtonGroup timeframeButtonGroup = new ButtonGroup();
        btn5m = createTimeframeButton("5m", 5, suggestionManager);
        btn30m = createTimeframeButton("30m", 30, suggestionManager);
        btn2h = createTimeframeButton("2h", 120, suggestionManager);
        btn8h = createTimeframeButton("8h", 480, suggestionManager);
        timeframeButtonGroup.add(btn5m);
        timeframeButtonGroup.add(btn30m);
        timeframeButtonGroup.add(btn2h);
        timeframeButtonGroup.add(btn8h);
        buttonPanel.add(btn5m);
        buttonPanel.add(btn30m);
        buttonPanel.add(btn2h);
        buttonPanel.add(btn8h);
        timeframePanel.add(labelPanel);
        timeframePanel.add(Box.createRigidArea(new Dimension(0, 3)));
        timeframePanel.add(buttonPanel);
        add(timeframePanel);
    }

    private JToggleButton createTimeframeButton(String label, int value, SuggestionManager suggestionManager) {
        JToggleButton button = new JToggleButton();
        button.addActionListener(e -> {
            preferencesManager.setTimeframe(value);
            suggestionManager.setSuggestionNeeded(true);
        });
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setForeground(ColorScheme.TEXT_COLOR);

        // Set initial text as white
        button.setText("<html><font color='rgb(198, 198, 198)'>" + label + "</font></html>");

        // Change color and text when selected/unselected
        button.addChangeListener(e -> {
            if (button.isSelected()) {
                button.setBackground(ColorScheme.BRAND_ORANGE);
                button.setText("<html><font color='black'>" + label + "</font></html>");
            } else {
                button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                button.setText("<html><font color='rgb(198, 198, 198)'>" + label + "</font></html>");
            }
        });

        return button;
    }

    public void refresh() {
        if(!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        
        int tf = preferencesManager.getTimeframe();
        btn5m.setSelected(tf == 5);
        btn30m.setSelected(tf == 30);
        btn2h.setSelected(tf == 120);
        btn8h.setSelected(tf == 480);
    }
} 