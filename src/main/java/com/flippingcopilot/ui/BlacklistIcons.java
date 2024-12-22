package com.flippingcopilot.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class BlacklistIcons {

    public static ImageIcon createTickIcon() {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(new Color(40, 167, 69));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(3, 8, 7, 12);
        g2d.drawLine(7, 12, 13, 4);
        g2d.dispose();

        return new ImageIcon(image);
    }

    public static ImageIcon createXIcon() {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(new Color(220, 53, 69));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(4, 4, 12, 12);
        g2d.drawLine(4, 12, 12, 4);
        g2d.dispose();

        return new ImageIcon(image);
    }
}
