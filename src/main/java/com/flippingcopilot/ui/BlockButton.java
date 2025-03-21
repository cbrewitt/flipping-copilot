package com.flippingcopilot.ui;

import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import net.runelite.client.util.ImageUtil;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.flippingcopilot.ui.UIUtilities.BUTTON_HOVER_LUMINANCE;

@Slf4j
@Singleton
public class BlockButton extends JButton
{
    private final SuggestionManager suggestionManager;
    private final SuggestionPreferencesManager preferencesManager;

    private static final ImageIcon BLOCK_ICON;
    private static final ImageIcon BLOCK_ICON_HOVER;

    static
    {
        var blockImg = ImageUtil.loadImageResource(BlockButton.class, "/block.png");
        BLOCK_ICON = new ImageIcon(blockImg);
        BLOCK_ICON_HOVER = new ImageIcon(ImageUtil.luminanceScale(blockImg, BUTTON_HOVER_LUMINANCE));
    }

    @Inject
    public BlockButton(SuggestionManager suggestionManager, SuggestionPreferencesManager preferencesManager)
    {
        super(BLOCK_ICON);
        this.suggestionManager = suggestionManager;
        this.preferencesManager = preferencesManager;

        setToolTipText("Block this item");
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);

        // Click action
        addActionListener(e -> confirmAndBlock());

        // Hover effects
        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                setIcon(BLOCK_ICON_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                setIcon(BLOCK_ICON);
            }
        });
    }

    private void confirmAndBlock()
    {
        Suggestion s = suggestionManager.getSuggestion();
        if (s == null)
        {
            log.debug("No current suggestion to block.");
            return;
        }

        // Get item name or fallback text
        String itemName = s.getName() != null ? s.getName() : "this item";

        // Show a confirmation dialog
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Do you want to block " + itemName + "?",
                "Confirm Block",
                JOptionPane.YES_NO_OPTION
        );

        // If user clicks YES, proceed with blocking
        if (choice == JOptionPane.YES_OPTION)
        {
            preferencesManager.blockItem(s.getItemId());
            log.debug("Blocked item with ID {} ({})", s.getItemId(), itemName);
            suggestionManager.setSuggestionNeeded(true);
        }
        else
        {
            log.debug("User canceled blocking for {}", itemName);
        }
    }
}
