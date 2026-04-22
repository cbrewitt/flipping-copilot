package com.flippingcopilot.ui;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class PatchNotesPopup {



    private static final int LOGO_WIDTH = 50;
    private static final int LOGO_HEIGHT = 45;

    private PatchNotesPopup() {
    }

    // When shipping new patch notes: bump LATEST_VERSION and update writeNotes below.
    public static final int LATEST_VERSION = 1;

    public static void show(Component parent) {
        JLabel heading = new JLabel("Flipping Copilot has been updated to v1.8.0");
        heading.setForeground(Color.WHITE);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));

        JTextPane notes = new JTextPane();
        notes.setEditable(false);
        notes.setFocusable(false);
        notes.setForeground(Color.WHITE);
        notes.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        notes.setFont(UIManager.getFont("TextArea.font"));
        notes.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        writeNotes(notes);

        JScrollPane scrollPane = new JScrollPane(notes);
        scrollPane.setPreferredSize(new Dimension(430, 220));
        scrollPane.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
        scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(
                parent,
                panel,
                "Flipping Copilot Update",
                JOptionPane.PLAIN_MESSAGE,
                buildLogoIcon()
        );
    }

    private static void writeNotes(JTextPane pane) {
        StyledDocument doc = pane.getStyledDocument();
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        try {
            doc.insertString(doc.getLength(),
                    "Two new features in this update:\n\n", null);
            doc.insertString(doc.getLength(), "Portfolio Tracking\n", bold);
            doc.insertString(doc.getLength(),
                    "- See your unrealized profit for held items\n"
                            + "- Right click items to add/remove\n"
                            + "- Track your total portfolio value, along with per-item breakdown\n"
                            + "\n",
                    null);
            doc.insertString(doc.getLength(), "Holds\n", bold);
            doc.insertString(doc.getLength(),
                    "- Hold items for bigger margins instead of selling immediately\n"
                            + "- Earn passive GP on holds while your GE slots stay free for active flips\n"
                            + "- Get sell suggestions when a held item is ready to flip",
                    null);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Icon buildLogoIcon() {
        BufferedImage logo = ImageUtil.loadImageResource(PatchNotesPopup.class, "/logo.png");
        Image resizedLogo = new ImageIcon(logo).getImage().getScaledInstance(LOGO_WIDTH, LOGO_HEIGHT, Image.SCALE_SMOOTH);
        return new ImageIcon(resizedLogo);
    }
}
