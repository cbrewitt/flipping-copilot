package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotPlugin;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.flippingcopilot.ui.UIUtilities.BUTTON_HOVER_LUMINANCE;

class PauseButton extends JButton
{
    private final FlippingCopilotPlugin plugin;

    private static final ImageIcon PLAY_ICON;
    private static final ImageIcon PAUSE_ICON;
    private static final ImageIcon PLAY_ICON_HOVER;
    private static final ImageIcon PAUSE_ICON_HOVER;

    static {
        var play = ImageUtil.loadImageResource(PauseButton.class, "/play.png");
        var pause = ImageUtil.loadImageResource(PauseButton.class, "/pause.png");
        PLAY_ICON = new ImageIcon(play);
        PAUSE_ICON = new ImageIcon(pause);
        PLAY_ICON_HOVER =  new ImageIcon(ImageUtil.luminanceScale(play, BUTTON_HOVER_LUMINANCE));
        PAUSE_ICON_HOVER = new ImageIcon(ImageUtil.luminanceScale(pause, BUTTON_HOVER_LUMINANCE));
    }

    public PauseButton(FlippingCopilotPlugin plugin) {
        super(PAUSE_ICON);
        this.plugin = plugin;
        setToolTipText("Pause suggestions");
        addActionListener(e -> {
            plugin.isPaused = !plugin.isPaused;
            if (plugin.isPaused) {
                plugin.suggestionHandler.getSuggestionPanel().setIsPausedMessage();
                plugin.suggestionHandler.setCurrentSuggestion(null);
                plugin.highlightController.removeAll();
            }
            update();
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setIcon(plugin.isPaused ? PLAY_ICON_HOVER : PAUSE_ICON_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setIcon(plugin.isPaused ? PLAY_ICON : PAUSE_ICON);
            }
        });

        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
    }

    private void update()
    {
        setIcon(plugin.isPaused ? PLAY_ICON : PAUSE_ICON);
        setToolTipText(plugin.isPaused ? "Unpause suggestions" :  "Pause suggestions");
    }
}
