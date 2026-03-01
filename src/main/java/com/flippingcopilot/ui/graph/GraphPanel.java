package com.flippingcopilot.ui.graph;

import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.ui.graph.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GraphPanel extends JPanel {

    private final PriceGraphConfigManager configManager;
    private final boolean sidebarMode;
    public DataManager dataManager;
    private final RenderV2 renderer;
    public final ZoomHandler zoomHandler;
    private final DatapointTooltip tooltip;
    private PriceLine priceLine;

    /** Used in sidebar mode for time range (minutes before/after now). */
    private int sidebarMinutesBefore = 60;
    private int sidebarMinutesAfter = 60;

    /** Cached sidebar bounds to avoid recalculating every paint; invalidated when data or time range changes. */
    private Bounds cachedSidebarBounds;
    private long cachedSidebarBoundsMinute = -1;
    private int cachedSidebarMinutesBefore = -1;
    private int cachedSidebarMinutesAfter = -1;

    public Bounds bounds;

    public Rectangle pricePa;
    public Rectangle volumePa;

    private Point mousePosition = new Point(0,0);
    private Datapoint hoveredPoint = null;

    public GraphPanel(PriceGraphConfigManager configManager) {
        this(configManager, false);
    }

    public GraphPanel(PriceGraphConfigManager configManager, boolean sidebarMode) {
        this.sidebarMode = sidebarMode;
        this.configManager = configManager;
        this.renderer = new RenderV2();
        this.zoomHandler = new ZoomHandler();
        this.tooltip = new DatapointTooltip();

        setBackground(configManager.getConfig().backgroundColor);
        setPreferredSize(new Dimension(500, 300));
        setBorder(BorderFactory.createEmptyBorder(0, sidebarMode ? 4 : 10, sidebarMode ? 4 : 10, sidebarMode ? 4 : 10));
        setupMouseListeners();
    }

    public void setData(DataManager dm) {
        setData(dm, null);
    }

    public void setData(DataManager dm, PriceLine priceLine) {
        int oldItemID = dataManager == null ? -1 : dataManager.data.itemId;
        dataManager = dm;
        this.priceLine = priceLine;
        cachedSidebarBounds = null;
        zoomHandler.maxViewBounds = dataManager.maxBounds;
        if (sidebarMode) {
            zoomHandler.homeViewBounds = dataManager.calculateSidebarBounds(sidebarMinutesBefore, sidebarMinutesAfter);
        } else {
            zoomHandler.homeViewBounds = dataManager.calculateHomeBounds();
        }
        zoomHandler.weekViewBounds = dataManager.calculateWeekBounds();
        zoomHandler.monthViewBounds = dataManager.calculateMonthBounds();
        if (sidebarMode || oldItemID != dataManager.data.itemId) {
            bounds = zoomHandler.homeViewBounds.copy();
        }
        repaint();
    }

    /**
     * Sets the sidebar graph time range (minutes before and after now). Only used when in sidebar mode.
     */
    public void setSidebarTimeRange(int minutesBefore, int minutesAfter) {
        this.sidebarMinutesBefore = minutesBefore;
        this.sidebarMinutesAfter = minutesAfter;
        cachedSidebarBounds = null;
    }


    private void setupMouseListeners() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if(dataManager == null){
                    return;
                }
                mousePosition = e.getPoint();
                if(pricePa.contains(mousePosition)) {
                    hoveredPoint = dataManager.findClosestPoint(mousePosition, Config.HOVER_RADIUS, pricePa, bounds);
                }else if(volumePa.contains(mousePosition)) {
                    hoveredPoint = dataManager.closedVolumeBar(mousePosition, volumePa, bounds);
                } else {
                    hoveredPoint = null;
                }
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (dataManager == null) {
                    return;
                }
                mousePosition = e.getPoint();
                if (!pricePa.contains(mousePosition)) {
                    return;
                }

                if (!sidebarMode) {
                    if (zoomHandler.isOverHomeButton(mousePosition)) {
                        zoomHandler.applyHomeView(bounds);
                        repaint();
                        return;
                    }
                    if (zoomHandler.isOverMaxButton(mousePosition)) {
                        zoomHandler.applyMaxView(bounds);
                        repaint();
                        return;
                    }
                    if (zoomHandler.isOverZoomInButton(mousePosition)) {
                        zoomHandler.applyZoomIn(bounds);
                        repaint();
                        return;
                    }
                    if (zoomHandler.isOverZoomOutButton(mousePosition)) {
                        zoomHandler.applyZoomOut(bounds);
                        repaint();
                        return;
                    }
                    if (zoomHandler.isOverWeekButton(mousePosition)) {
                        zoomHandler.applyWeekView(bounds);
                        repaint();
                        return;
                    }
                    if (zoomHandler.isOverMonthButton(mousePosition)) {
                        zoomHandler.applyMonthView(bounds);
                        repaint();
                        return;
                    }
                    zoomHandler.startSelection(mousePosition);
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                }
                hoveredPoint = null;
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dataManager == null) {
                    return;
                }
                mousePosition = e.getPoint();
                if (!sidebarMode && zoomHandler.isSelecting()) {
                    zoomHandler.setSelectionEnd(mousePosition);
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dataManager == null) {
                    return;
                }
                mousePosition = e.getPoint();
                if (!sidebarMode && zoomHandler.isSelecting()) {
                    setCursor(Cursor.getDefaultCursor());
                    zoomHandler.setSelectionEnd(mousePosition);
                    zoomHandler.applySelection(pricePa, bounds);
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if(dataManager == null){
                    return;
                }
                mousePosition = e.getPoint();
                hoveredPoint = null;
                repaint();
            }
        };

        addMouseMotionListener(mouseAdapter);
        addMouseListener(mouseAdapter);
    }


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        final int leftPadding = sidebarMode ? 36 : 80;
        final int topPadding = sidebarMode ? 8 : 50;
        final int rightPadding = sidebarMode ? 6 : 20;
        final int bottomPadding = sidebarMode ? 20 : 50;

        int w = getWidth() - leftPadding - rightPadding;
        int ah = getHeight() - topPadding - bottomPadding;
        int h1 = sidebarMode ? ah : (int) (ah * 0.75);
        int h2 = ah - h1;

        pricePa = new Rectangle(leftPadding, topPadding, w, h1);
        volumePa = new Rectangle(leftPadding, topPadding + h1, w, h2);
        if (dataManager == null) {
            return;
        }
        Data data = dataManager.getData();
        if (data == null) return;

        if (sidebarMode) {
            long nowMinute = System.currentTimeMillis() / 60000;
            if (cachedSidebarBounds == null || cachedSidebarBoundsMinute != nowMinute
                    || cachedSidebarMinutesBefore != sidebarMinutesBefore || cachedSidebarMinutesAfter != sidebarMinutesAfter) {
                bounds = dataManager.calculateSidebarBounds(sidebarMinutesBefore, sidebarMinutesAfter).copy();
                cachedSidebarBounds = bounds.copy();
                cachedSidebarBoundsMinute = nowMinute;
                cachedSidebarMinutesBefore = sidebarMinutesBefore;
                cachedSidebarMinutesAfter = sidebarMinutesAfter;
            } else {
                bounds = cachedSidebarBounds.copy();
            }
        }

        Config config = configManager.getConfig();
        setBackground(config.backgroundColor);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        if (!sidebarMode) {
            renderer.drawLegend(g2d, config, pricePa, data.predictionTimes != null);
        }

        // Draw the plot area background with dynamic padding
        g2d.setColor(sidebarMode ? Config.SIDEBAR_PLOT_AREA_COLOR : config.plotAreaColor);
        g2d.fillRect(pricePa.x, pricePa.y, pricePa.width, pricePa.height);
        if (!sidebarMode) {
            g2d.fillRect(volumePa.x, volumePa.y, volumePa.width, volumePa.height);
        }

        TimeAxis xAxis = AxisCalculator.calculateTimeAxis(bounds, AxisCalculator.getLocalTimeOffsetSeconds());
        YAxis yAxis = AxisCalculator.calculatePriceAxis(bounds, sidebarMode ? 5 : 18);
        YAxis y2Axis = AxisCalculator.calculateVolumeAxis(bounds);

        renderer.drawGrid(g2d, config, pricePa, bounds, bounds::toY, xAxis, yAxis,
                sidebarMode ? Config.SIDEBAR_GRID_COLOR : null,
                sidebarMode ? Config.SIDEBAR_GRID_STROKE : null);
        if (!sidebarMode) {
            renderer.drawGrid(g2d, config, volumePa, bounds, bounds::toY2, xAxis, y2Axis);
        }
        renderer.drawAxes(g2d, config, pricePa, sidebarMode ? Config.SIDEBAR_AXIS_COLOR : null);
        float axisFontSize = sidebarMode ? 12f : Config.FONT_SIZE;
        Color axisLabelColor = sidebarMode ? Config.SIDEBAR_TEXT_COLOR : null;
        renderer.drawYAxisLabels(g2d, config, pricePa, bounds::toY, yAxis, false, axisFontSize, axisLabelColor);
        if (!sidebarMode) {
            renderer.drawAxes(g2d, config, volumePa);
            renderer.drawYAxisLabels(g2d, config, volumePa, bounds::toY2, y2Axis, true);
        }
        renderer.drawXAxisLabels(g2d, config, sidebarMode ? pricePa : volumePa, bounds, xAxis, axisFontSize, axisLabelColor);

        int pointSize = dynamicPointSize(Config.BASE_POINT_SIZE, bounds);
        if (sidebarMode) {
            pointSize = Math.max(pointSize, 6);
        }
        Color highColor = sidebarMode ? Config.SIDEBAR_HIGH_COLOR : config.highColor;
        Color lowColor = sidebarMode ? Config.SIDEBAR_LOW_COLOR : config.lowColor;
        renderer.drawPoints(g2d, pricePa, bounds, dataManager.lowDatapoints, lowColor, pointSize);
        renderer.drawPoints(g2d, pricePa, bounds, dataManager.highDatapoints, highColor, pointSize);
        Stroke dataLineStroke = sidebarMode ? Config.SIDEBAR_LINE_STROKE : Config.NORMAL_STROKE;
        if (config.connectPoints || sidebarMode) {
            renderer.drawLines(g2d, pricePa, bounds, dataManager.lowDatapoints, lowColor, dataLineStroke);
            renderer.drawLines(g2d, pricePa, bounds, dataManager.highDatapoints, highColor, dataLineStroke);
        }
        renderer.drawStartPoints(g2d, pricePa, bounds, dataManager.buyPriceDataPoint(), Color.WHITE, pointSize);
        renderer.drawStartPoints(g2d, pricePa, bounds, dataManager.sellPriceDataPoint(), Color.WHITE, pointSize);
        if (config.showSuggestedPriceLines) {
            drawSuggestedPriceLine(g2d, config, axisFontSize);
        }

        renderer.drawLines(g2d, pricePa, bounds, dataManager.predictionLowDatapoints,
                sidebarMode ? Config.SIDEBAR_LOW_COLOR : config.lowColor,
                sidebarMode ? Config.SIDEBAR_LINE_STROKE : Config.DOTTED_STROKE);
        renderer.drawLines(g2d, pricePa, bounds, dataManager.predictionHighDatapoints,
                sidebarMode ? Config.SIDEBAR_HIGH_COLOR : config.highColor,
                sidebarMode ? Config.SIDEBAR_LINE_STROKE : Config.DOTTED_STROKE);
        if (data.predictionTimes != null && !sidebarMode) {
            renderer.drawPredictionIQR(g2d, config, pricePa, bounds, data.predictionTimes, data.predictionLowIQRLower, data.predictionLowIQRUpper, true);
            renderer.drawPredictionIQR(g2d, config, pricePa, bounds, data.predictionTimes, data.predictionHighIQRLower, data.predictionHighIQRUpper, false);
        }
        if (!sidebarMode) {
            zoomHandler.drawButtons(g2d, pricePa, mousePosition);
            zoomHandler.drawSelectionRectangle(g2d, pricePa);
            renderer.drawVolumeBars(g2d, config, volumePa, bounds, dataManager.volumes, hoveredPoint);
        }

        if (!this.dataManager.flipEntryDatapoints.isEmpty()) {
            renderer.drawTxsDatapoints(g2d, pricePa, bounds, this.dataManager.flipEntryDatapoints, hoveredPoint, config);
        }

        if (!this.dataManager.flipCloseDatapoints.isEmpty()) {
            renderer.drawTxsDatapoints(g2d, pricePa, bounds, this.dataManager.flipCloseDatapoints, hoveredPoint, config);
        }

        // Draw tooltip for hovered point
        if (hoveredPoint != null) {
            if (!sidebarMode && hoveredPoint.type == Datapoint.Type.VOLUME_1H) {
                tooltip.drawVolume(g2d, config, volumePa, bounds, hoveredPoint);
            } else if (hoveredPoint.type != Datapoint.Type.VOLUME_1H) {
                tooltip.draw(g2d, config, pricePa, bounds, hoveredPoint);
            }
        }
    }

    private void drawSuggestedPriceLine(Graphics2D g2d, Config config, float labelFontSize) {
        if (priceLine == null) {
            return;
        }
        int y = bounds.toY(pricePa, priceLine.getPrice());
        g2d.setColor(sidebarMode ? Config.SIDEBAR_SUGGESTED_PRICE_LINE_COLOR : Color.WHITE);
        Stroke previousStroke = g2d.getStroke();
        g2d.setStroke(sidebarMode ? Config.SIDEBAR_DOTTED_STROKE : Config.DOTTED_STROKE);
        g2d.drawLine(pricePa.x, y, pricePa.x + pricePa.width, y);
        g2d.setStroke(previousStroke);

        if (sidebarMode) {
            return;
        }
        String label = priceLine.getMessage();
        if (label == null || label.isBlank()) {
            return;
        }
        Font prevFont = g2d.getFont();
        g2d.setFont(prevFont.deriveFont(labelFontSize));
        FontMetrics metrics = g2d.getFontMetrics();
        int labelWidth = metrics.stringWidth(label);
        int padding = Config.LABEL_PADDING;
        int labelX = pricePa.x + pricePa.width - labelWidth - padding;
        int labelY = priceLine.isTextAbove()
                ? Math.max(pricePa.y + metrics.getAscent(), y - (padding / 2))
                : Math.min(pricePa.y + pricePa.height - padding, y + metrics.getAscent() + (padding / 2));
        g2d.drawString(label, labelX, labelY);
        g2d.setFont(prevFont);
    }

    private int dynamicPointSize(int baseSize, Bounds bounds) {
        int td = bounds.xMax - bounds.xMin;
        if (td < Constants.DAY_SECONDS) {
            return (int) ((float) baseSize * 1.25);
        } else if (td > Constants.DAY_SECONDS * 20) {
            return (int) ((float) baseSize * 0.5);
        } else if (td > Constants.DAY_SECONDS * 6) {
            return (int) ((float) baseSize * 0.75);
        }
        return baseSize;
    }
}
