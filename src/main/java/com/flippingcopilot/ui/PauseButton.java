package com.flippingcopilot.ui;

import com.flippingcopilot.controller.SuggestionController;
import com.flippingcopilot.model.PausedManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.flippingcopilot.ui.UIUtilities.BUTTON_HOVER_LUMINANCE;

@Singleton
public class PauseButton extends JButton {

    private final PausedManager pausedManager;

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

    @Inject
    public PauseButton(PausedManager pausedManager,
                       SuggestionController suggestionController) {
        super(PAUSE_ICON);
        this.pausedManager = pausedManager;
        setToolTipText("Pause suggestions");
        addActionListener(e -> {
            suggestionController.togglePause();
            update();
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setIcon(pausedManager.isPaused() ? PLAY_ICON_HOVER : PAUSE_ICON_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setIcon(pausedManager.isPaused() ? PLAY_ICON : PAUSE_ICON);
            }
        });

        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
    }

    private void update() {
        boolean isPaused = pausedManager.isPaused();
        setIcon(isPaused ? PLAY_ICON : PAUSE_ICON);
        setToolTipText(isPaused ? "Unpause suggestions" :  "Pause suggestions");
    }
}
