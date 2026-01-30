package com.flippingcopilot.ui.graph;

import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.ui.graph.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GraphPanel extends JPanel {

    private final PriceGraphConfigManager configManager;
    public DataManager dataManager;
    private final RenderV2 renderer;
    public final ZoomHandler zoomHandler;
    private final DatapointTooltip tooltip;
    private PriceLine priceLine;

    public Bounds bounds;

    public Rectangle pricePa;
    public Rectangle volumePa;

    private Point mousePosition = new Point(0,0);
    private Datapoint hoveredPoint = null;

    public GraphPanel(PriceGraphConfigManager configManager) {

        this.configManager = configManager;
        this.renderer = new RenderV2();
        this.zoomHandler = new ZoomHandler();
        this.tooltip = new DatapointTooltip();

        setBackground(configManager.getConfig().backgroundColor);
        setPreferredSize(new Dimension(500, 300));
        setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        setupMouseListeners();
    }

    public void setData(DataManager dm) {
        setData(dm, null);
    }

    public void setData(DataManager dm, PriceLine priceLine) {
        int oldItemID = dataManager == null ? -1 : dataManager.data.itemId;
        dataManager = dm;
        this.priceLine = priceLine;
        zoomHandler.maxViewBounds = dataManager.maxBounds;
        zoomHandler.homeViewBounds = dataManager.calculateHomeBounds();
        zoomHandler.weekViewBounds = dataManager.calculateWeekBounds();
        zoomHandler.monthViewBounds = dataManager.calculateMonthBounds();
        if (oldItemID != dataManager.data.itemId) {
            bounds = zoomHandler.homeViewBounds.copy();
        }
        repaint();
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
                if(dataManager == null){
                    return;
                }
                mousePosition = e.getPoint();
                if (!pricePa.contains(mousePosition)) {
                    return;
                }

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
                hoveredPoint = null;
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if(dataManager == null){
                    return;
                }
                mousePosition = e.getPoint();
                if (zoomHandler.isSelecting()) {
                    zoomHandler.setSelectionEnd(mousePosition);
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if(dataManager == null){
                    return;
                }
                mousePosition = e.getPoint();
                if (zoomHandler.isSelecting()) {
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


        final int leftPadding = 80;
        final int topPadding = 50;
        final int rightPadding = 20;
        final int bottomPadding = 50;
        
        int w = getWidth() - leftPadding - rightPadding;
        int ah = getHeight() - topPadding - bottomPadding;
        int h1 = (int) (ah * 0.75);
        int h2 = ah - h1;
        
        pricePa = new Rectangle(leftPadding, topPadding, w, h1);
        volumePa = new Rectangle(leftPadding, topPadding+h1, w, h2);
        if(dataManager == null) {
            return;
        }
        Data data = dataManager.getData();
        if (data == null) return;
        Config config = configManager.getConfig();
        setBackground(config.backgroundColor);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // First draw the legend above the plot area
        renderer.drawLegend(g2d, config, pricePa, data.predictionTimes != null);

        // Draw the plot area background with dynamic padding
        g2d.setColor(config.plotAreaColor);
        g2d.fillRect(pricePa.x, pricePa.y, pricePa.width, pricePa.height);
        g2d.fillRect(volumePa.x, volumePa.y,  volumePa.width, volumePa.height);
        
        TimeAxis xAxis = AxisCalculator.calculateTimeAxis(bounds, AxisCalculator.getLocalTimeOffsetSeconds());
        YAxis yAxis = AxisCalculator.calculatePriceAxis(bounds);
        YAxis y2Axis = AxisCalculator.calculateVolumeAxis(bounds);

        renderer.drawGrid(g2d, config, pricePa, bounds, bounds::toY, xAxis, yAxis);
        renderer.drawGrid(g2d, config, volumePa, bounds, bounds:: toY2, xAxis, y2Axis);
        renderer.drawAxes(g2d, config, pricePa);
        renderer.drawYAxisLabels(g2d, config, pricePa, bounds::toY, yAxis, false);
        renderer.drawAxes(g2d, config, volumePa);
        renderer.drawYAxisLabels(g2d, config, volumePa, bounds::toY2, y2Axis, true);
        renderer.drawXAxisLabels(g2d, config, volumePa, bounds, xAxis);


        int pointSize = dynamicPointSize(Config.BASE_POINT_SIZE, bounds);
        renderer.drawPoints(g2d, pricePa, bounds, dataManager.lowDatapoints, config.lowColor, pointSize);
        renderer.drawPoints(g2d, pricePa, bounds, dataManager.highDatapoints, config.highColor, pointSize);
        if (config.connectPoints) {
            renderer.drawLines(g2d, pricePa, bounds, dataManager.lowDatapoints, config.lowColor, Config.NORMAL_STROKE);
            renderer.drawLines(g2d, pricePa, bounds, dataManager.highDatapoints, config.highColor, Config.NORMAL_STROKE);
        }
        renderer.drawStartPoints(g2d, pricePa, bounds, dataManager.buyPriceDataPoint(), Color.WHITE, pointSize);
        renderer.drawStartPoints(g2d, pricePa, bounds, dataManager.sellPriceDataPoint(), Color.WHITE, pointSize);
        if (config.showSuggestedPriceLines) {
            drawSuggestedPriceLine(g2d, config);
        }

        renderer.drawLines(g2d, pricePa, bounds, dataManager.predictionLowDatapoints, config.lowColor, Config.DOTTED_STROKE);
        renderer.drawLines(g2d, pricePa, bounds, dataManager.predictionHighDatapoints, config.highColor, Config.DOTTED_STROKE);
        if(data.predictionTimes != null) {
            renderer.drawPredictionIQR(g2d, config, pricePa, bounds, data.predictionTimes, data.predictionLowIQRLower, data.predictionLowIQRUpper, true);
            renderer.drawPredictionIQR(g2d, config, pricePa, bounds, data.predictionTimes, data.predictionHighIQRLower, data.predictionHighIQRUpper, false);
        }
        zoomHandler.drawButtons(g2d, pricePa, mousePosition);
        zoomHandler.drawSelectionRectangle(g2d, pricePa);

        renderer.drawVolumeBars(g2d, config, volumePa, bounds, dataManager.volumes, hoveredPoint);


        if(!this.dataManager.flipEntryDatapoints.isEmpty()) {
            renderer.drawTxsDatapoints(g2d, pricePa, bounds, this.dataManager.flipEntryDatapoints, hoveredPoint, config);
        }

        if(!this.dataManager.flipCloseDatapoints.isEmpty()) {
            renderer.drawTxsDatapoints(g2d, pricePa, bounds, this.dataManager.flipCloseDatapoints, hoveredPoint, config);
        }

        // Draw tooltip for hovered point
        if (hoveredPoint != null) {
            if (hoveredPoint.type == Datapoint.Type.VOLUME_1H) {
                tooltip.drawVolume(g2d, config, volumePa, bounds, hoveredPoint);
            } else {
                tooltip.draw(g2d, config, pricePa, bounds, hoveredPoint);
            }
        }
    }

    private void drawSuggestedPriceLine(Graphics2D g2d, Config config) {
        if (priceLine == null) {
            return;
        }
        int y = bounds.toY(pricePa, priceLine.getPrice());
        g2d.setColor(Color.WHITE);
        Stroke previousStroke = g2d.getStroke();
        g2d.setStroke(Config.NORMAL_STROKE);
        g2d.drawLine(pricePa.x, y, pricePa.x + pricePa.width, y);
        g2d.setStroke(previousStroke);

        String label = priceLine.getMessage();
        if (label == null || label.isBlank()) {
            return;
        }
        FontMetrics metrics = g2d.getFontMetrics();
        int labelWidth = metrics.stringWidth(label);
        int labelX = pricePa.x + pricePa.width - labelWidth - Config.LABEL_PADDING;
        int labelY = priceLine.isTextAbove()
                ? Math.max(pricePa.y + metrics.getAscent(), y - (Config.LABEL_PADDING / 2))
                : Math.min(pricePa.y + pricePa.height - Config.LABEL_PADDING, y + metrics.getAscent() + (Config.LABEL_PADDING / 2));
        g2d.drawString(label, labelX, labelY);
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