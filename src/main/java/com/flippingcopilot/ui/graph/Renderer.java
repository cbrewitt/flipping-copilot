package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.UIUtilities;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
/**
 * Handles rendering of the price graph elements.
 * Separates rendering logic from component logic.
 */
public class Renderer {
    private SimpleDateFormat dateTimeFormat;
    private SimpleDateFormat timeOnlyFormat;
    private SimpleDateFormat dayFormat;

    /**
     * Creates a new graph renderer.
     */
    public Renderer() {
        this.dateTimeFormat = new SimpleDateFormat("d MMM HH:mm");
        this.timeOnlyFormat = new SimpleDateFormat("HH:mm");
        this.dayFormat = new SimpleDateFormat("d MMM");
    }


    /**
     * Draw a legend explaining the graph elements.
     *
     * @param g2       The graphics context
     * @param width    Component width
     * @param height   Component height
     * @param isZoomed Whether the graph is currently zoomed
     */


}