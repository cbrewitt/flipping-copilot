package com.flippingcopilot.ui;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

@Singleton
public class GeHistoryTransactionButton extends JButton {

    @Inject
    public GeHistoryTransactionButton() {
        super("Add GE history transactions");
        setupButton();
    }
    
    private void setupButton() {
        setFont(FontManager.getRunescapeBoldFont());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 20, 8, 20));
        setOpaque(true);
        setFocusable(false);
        setVisible(false);
        
        // Add hover effect
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
            }
            
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        });
    }
}