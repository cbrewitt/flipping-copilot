package com.flippingcopilot.ui.graph;

import net.runelite.client.ui.ColorScheme;

import java.awt.*;

public class Config {
    // Layout constants
    public static int PADDING = 50;
    public static int LABEL_PADDING = 20;
    public static int TICK_SIZE = 5;
    public static int POINT_SIZE = 4;

    public static boolean CONNECT_POINTS = false;

    // Colors and styling
    public static Color LOW_COLOR = new Color(220, 50, 50);
    public static Color HIGH_COLOR = new Color(50, 50, 220);
    public static Color LOW_SHADE_COLOR = new Color(220, 50, 50, 40);
    public static Color HIGH_SHADE_COLOR = new Color(50, 50, 220, 40);
    public static Color BACKGROUND_COLOR = ColorScheme.DARKER_GRAY_COLOR.brighter(); // Lighter background color
    public static Color PLOT_AREA_COLOR = ColorScheme.LIGHT_GRAY_COLOR.brighter();// New constant for plot area color
    public static Color TEXT_COLOR = new Color(225, 225, 225);
    public static Color AXIS_COLOR = new Color(150, 150, 150);
    public static Color GRID_COLOR = new Color(85, 85, 85, 90); // Slightly lighter grid for better visibility
    public static Color TOOLTIP_BACKGROUND = new Color(20, 20, 20, 220);
    public static Color TOOLTIP_BORDER = new Color(100, 100, 100);
    public static Color SELECTION_COLOR = new Color(100, 100, 240, 80); // Color for zoom selection
    public static Color SELECTION_BORDER_COLOR = new Color(70, 70, 220);
    public static Color HOME_BUTTON_COLOR = new Color(150, 150, 150, 200);
    public static Color HOME_BUTTON_HOVER_COLOR = new Color(100,100,100);

    public static final Stroke SELECTION_STROKE = new BasicStroke(1.5f);

    // Font settings
    public static float FONT_SIZE = 16f;

    // Home button properties
    public static int HOME_BUTTON_SIZE = 24;
    public static int HOME_BUTTON_MARGIN = 5;

    // Hover detection
    public static int HOVER_RADIUS = 8; // Distance in pixels to detect hovering


    // Strokes
    public static final Stroke NORMAL_STROKE = new BasicStroke(1f);
    public static final Stroke DOTTED_STROKE = new BasicStroke(
            1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0
    );
    public static final Stroke GRID_STROKE = new BasicStroke(
            0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0
    );
}