package com.flippingcopilot.ui;

import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

@Getter
public class Spinner extends JPanel {
    private final JLabel spinnerLabel = new JLabel();

    public Spinner() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = GridBagConstraints.REMAINDER;
        gbc.gridy = GridBagConstraints.REMAINDER;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.CENTER;

        spinnerLabel.setPreferredSize(new Dimension(30, 30));
        add(spinnerLabel, gbc);
        setUpIcon();
        hide();
    }

    public void show() {
        spinnerLabel.setVisible(true);
    }

    public void hide() {
        spinnerLabel.setVisible(false);
    }

    private void setUpIcon() {
        IconTextField.Icon icon = IconTextField.Icon.LOADING;
        final ImageIcon imageIcon = new ImageIcon(Objects.requireNonNull(IconTextField.class.getResource(icon.getFile())));
        spinnerLabel.setIcon(imageIcon);
    }
}