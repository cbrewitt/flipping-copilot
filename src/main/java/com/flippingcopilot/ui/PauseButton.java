package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotPlugin;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.flippingcopilot.ui.UIUtilities.BUTTON_HOVER_LUMINANCE;

public class PauseButton extends JButton
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
            plugin.suggestionHandler.togglePause();
            update();
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setIcon(plugin.suggestionHandler.isPaused() ? PLAY_ICON_HOVER : PAUSE_ICON_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setIcon(plugin.suggestionHandler.isPaused() ? PLAY_ICON : PAUSE_ICON);
            }
        });

        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);

    }

    public void setPausedState(boolean paused) {
        if(paused) {
            plugin.suggestionHandler.pause();
            update();
        } else {
            plugin.suggestionHandler.unpause();
            update();
        }
    }

    private void update()
    {
        setIcon(plugin.suggestionHandler.isPaused() ? PLAY_ICON : PAUSE_ICON);
        setToolTipText(plugin.suggestionHandler.isPaused() ? "Unpause suggestions" :  "Pause suggestions");
    }
}
