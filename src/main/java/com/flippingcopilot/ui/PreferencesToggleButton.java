package com.flippingcopilot.ui;


import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.*;

import net.runelite.client.util.*;

class PreferencesToggleButton extends JToggleButton
{
    private static final ImageIcon ON_SWITCHER;
    private static final ImageIcon OFF_SWITCHER;

    public PreferencesToggleButton(String selectedToolTipText, String unSelectedToolTipText) {
        super(OFF_SWITCHER);
        setSelectedIcon(ON_SWITCHER);
        SwingUtil.removeButtonDecorations(this);
        setPreferredSize(new Dimension(25, 25));
        SwingUtil.addModalTooltip(this, selectedToolTipText, unSelectedToolTipText);
    }

    static
    {
        BufferedImage onSwitcher = ImageUtil.loadImageResource(CopilotPanel.class, "/switcher_on.png");
        ON_SWITCHER = new ImageIcon(onSwitcher);
        OFF_SWITCHER = new ImageIcon(ImageUtil.flipImage(
                ImageUtil.luminanceScale(
                        ImageUtil.grayscaleImage(onSwitcher),
                        0.61f
                ),
                true,
                false
        ));
    }
}
