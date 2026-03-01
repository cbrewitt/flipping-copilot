package com.flippingcopilot.ui.graph.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.runelite.client.ui.ColorScheme;

import java.awt.*;

@Getter
@Setter
@NoArgsConstructor
public class Config {
    public static int LABEL_PADDING = 20;
    public static int TICK_SIZE = 5;
    public static int BASE_POINT_SIZE = 4;

    public static int GRAPH_BUTTON_SIZE = 24;
    public static int GRAPH_BUTTON_MARGIN = 5;

    public static int HOVER_RADIUS = 8; // Distance in pixels to detect hovering

    public static final Stroke THIN_STROKE = new BasicStroke(0.5f);
    public static final Stroke NORMAL_STROKE = new BasicStroke(1f);
    public static final Stroke THICK_STROKE = new BasicStroke(2f);
    public static final Stroke DOTTED_STROKE = new BasicStroke(
            1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0
    );
    /** Thicker stroke for sidebar graph lines (better visibility when small). */
    public static final Stroke SIDEBAR_LINE_STROKE = new BasicStroke(2f);
    /** Thicker dashed stroke for sidebar suggested price line. */
    public static final Stroke SIDEBAR_DOTTED_STROKE = new BasicStroke(
            2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6}, 0
    );
    public static final Stroke GRID_STROKE = new BasicStroke(
            0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0
    );
    public static final Stroke SELECTION_STROKE = new BasicStroke(1.5f);

    public static float FONT_SIZE = 16f;

    public static Color TOOLTIP_BACKGROUND = new Color(20, 20, 20, 220);
    public static Color TOOLTIP_BORDER = new Color(100, 100, 100);
    public static Color SELECTION_COLOR = new Color(100, 100, 240, 80); // Color for zoom selection
    public static Color SELECTION_BORDER_COLOR = new Color(70, 70, 220);
    public static Color GRAPH_BUTTON_COLOR = new Color(150, 150, 150, 200);
    public static Color GRAPH_BUTTON_HOVER_COLOR = new Color(100,100,100);

    // configurable properties
    public boolean connectPoints = false;
    public boolean showSuggestedPriceLines = true;
    public Color lowColor = new Color(0, 153, 255);
    public Color highColor = new Color(255, 102, 0);
    public Color lowShadeColor = new Color(0, 153, 255, 60);
    public Color highShadeColor = new Color(255, 102, 0, 60);

    public Color backgroundColor = ColorScheme.DARKER_GRAY_COLOR.brighter();
    public Color plotAreaColor = new Color(51, 51, 51);
    public Color textColor = new Color(225, 225, 225);
    public Color axisColor = new Color(150, 150, 150);
    public Color gridColor = new Color(85, 85, 85, 90);

    /** Brighter grid/axis for small sidebar graph (easier to read). */
    public static final Color SIDEBAR_GRID_COLOR = new Color(120, 120, 120);
    public static final Color SIDEBAR_AXIS_COLOR = new Color(200, 200, 200);
    /** Slightly lighter plot area for sidebar so grid lines stand out. */
    public static final Color SIDEBAR_PLOT_AREA_COLOR = new Color(55, 55, 55);
    /** High-contrast orange for sidebar high/sell series (easier to see when small). */
    public static final Color SIDEBAR_HIGH_COLOR = new Color(255, 140, 0);
    /** High-contrast blue for sidebar low/buy series. */
    public static final Color SIDEBAR_LOW_COLOR = new Color(70, 180, 255);
    /** Bright suggested price line in sidebar (stands out from grid). */
    public static final Color SIDEBAR_SUGGESTED_PRICE_LINE_COLOR = new Color(180, 255, 100);
    /** Brighter axis label text in sidebar. */
    public static final Color SIDEBAR_TEXT_COLOR = new Color(240, 240, 240);
    /** Thicker grid stroke for sidebar (solid, more visible). */
    public static final Stroke SIDEBAR_GRID_STROKE = new BasicStroke(1f);
}
