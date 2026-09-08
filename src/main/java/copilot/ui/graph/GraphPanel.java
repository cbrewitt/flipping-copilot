package copilot.ui.graph;

import copilot.manager.*;
import copilot.ui.graph.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GraphPanel extends JPanel {

    private final GraphSettings configManager;
    public DataManager dataManager;
    public final ZoomHandler zoomHandler;
    private PriceLine priceLine;

    public Bounds bounds;

    public Rectangle pricePa, volumePa;

    private Point mousePosition = new Point(0,0);
    private Datapoint hoveredPoint = null;

    public GraphPanel(GraphSettings configManager) {

        this.configManager = configManager; zoomHandler = new ZoomHandler();

        setBackground(configManager.getConfig().backgroundColor);
        setPreferredSize(new Dimension(500, 300));
        setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        setupMouseListeners();
    }

    public void setData(DataManager dm) { setData(dm, null); }

    public void setData(DataManager dm, PriceLine priceLine) {
        int oldItemID = dataManager == null ? -1 : dataManager.data.itemId;
        dataManager = dm;
        this.priceLine = priceLine;
        zoomHandler.maxViewBounds = dataManager.maxBounds;
        zoomHandler.homeViewBounds = dataManager.calculateHomeBounds();
        for (ZoomHandler.ZoomPreset preset : zoomHandler.presets) {
            preset.bounds = dataManager.calculateSpanBounds(preset.spanSeconds, preset.horizonSeconds);
        }
        if (oldItemID != dataManager.data.itemId) { bounds = zoomHandler.homeViewBounds.copy(); }
        repaint();
    }

    private void setupMouseListeners() {
        var mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (dataManager == null) { return; }
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
                if (dataManager == null) { return; }
                mousePosition = e.getPoint();
                if (!pricePa.contains(mousePosition)) { return; }

                if (zoomHandler.applyButtonView(mousePosition, bounds)) {
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
                if (dataManager == null) { return; }
                mousePosition = e.getPoint();
                if (zoomHandler.isSelecting()) {
                    zoomHandler.setSelectionEnd(mousePosition);
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dataManager == null) { return; }
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
                if (dataManager == null) { return; }
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

        final int leftPadding = 80, topPadding = 50, rightPadding = 20, bottomPadding = 50;
        
        int w = getWidth() - leftPadding - rightPadding, ah = getHeight() - topPadding - bottomPadding;
        int h1 = (int) (ah * 0.75), h2 = ah - h1;
        
        pricePa = new Rectangle(leftPadding, topPadding, w, h1);
        volumePa = new Rectangle(leftPadding, topPadding+h1, w, h2);
        if (dataManager == null) { return; }
        Data data = dataManager.data;
        if (data == null) return;
        Config config = configManager.getConfig();
        setBackground(config.backgroundColor);
        Graphics2D graphics = (Graphics2D) g;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        var price = new GraphRenderer(graphics, config, pricePa, bounds);
        var volume = new GraphRenderer(graphics, config, volumePa, bounds);

        // First draw the legend above the plot area
        price.drawLegend(data.predictionTimes != null);

        // Draw the plot area background with dynamic padding
        graphics.setColor(config.plotAreaColor);
        graphics.fillRect(pricePa.x, pricePa.y, pricePa.width, pricePa.height);
        graphics.fillRect(volumePa.x, volumePa.y,  volumePa.width, volumePa.height);
        
        var xAxis = AxisCalculator.calculateTimeAxis(bounds, AxisCalculator.getLocalTimeOffsetSeconds());
        // trailing args are (max labelled ticks, max grid lines) for each axis
        YAxis yAxis = AxisCalculator.calculateNumericAxis(bounds.yMin, bounds.yMax, bounds.yDelta(), 18, 28);
        YAxis y2Axis = AxisCalculator.calculateNumericAxis(bounds.y2Min, bounds.y2Max, bounds.y2Delta(), 8, 16);

        price.drawGrid(bounds::toY, xAxis, yAxis);
        volume.drawGrid(bounds:: toY2, xAxis, y2Axis);
        price.drawAxes();
        price.drawYAxisLabels(bounds::toY, yAxis, false);
        volume.drawAxes();
        volume.drawYAxisLabels(bounds::toY2, y2Axis, true);
        volume.drawXAxisLabels(xAxis);

        int pointSize = dynamicPointSize(Config.BASE_POINT_SIZE, bounds);
        price.drawPoints(dataManager.lowDatapoints, config.lowColor, pointSize);
        price.drawPoints(dataManager.highDatapoints, config.highColor, pointSize);
        if (config.connectPoints) {
            price.drawLines(dataManager.lowDatapoints, config.lowColor, Config.NORMAL_STROKE);
            price.drawLines(dataManager.highDatapoints, config.highColor, Config.NORMAL_STROKE);
        }
        price.drawStartPoints(dataManager.buyPriceDataPoint(), Color.WHITE, pointSize);
        price.drawStartPoints(dataManager.sellPriceDataPoint(), Color.WHITE, pointSize);
        if (config.showSuggestedPriceLines) { drawSuggestedPriceLine(graphics, config); }

        price.drawLines(dataManager.predictionLowDatapoints, config.lowColor, Config.DOTTED_STROKE);
        price.drawLines(dataManager.predictionHighDatapoints, config.highColor, Config.DOTTED_STROKE);
        if(data.predictionTimes != null) {
            price.drawPredictionIQR(data.predictionTimes, data.predictionLowIQRLower, data.predictionLowIQRUpper, true);
            price.drawPredictionIQR(data.predictionTimes, data.predictionHighIQRLower, data.predictionHighIQRUpper, false);
        }
        zoomHandler.drawButtons(graphics, pricePa, mousePosition);
        zoomHandler.drawSelectionRectangle(graphics, pricePa);

        volume.drawVolumeBars(dataManager.volumes, hoveredPoint);

        if(!dataManager.flipEntryDatapoints.isEmpty()) {
            price.drawTxsDatapoints(dataManager.flipEntryDatapoints, hoveredPoint);
        }

        if(!dataManager.flipCloseDatapoints.isEmpty()) {
            price.drawTxsDatapoints(dataManager.flipCloseDatapoints, hoveredPoint);
        }

        // Draw tooltip for hovered point
        if (hoveredPoint != null) {
            if (hoveredPoint.type == Datapoint.Type.VOLUME_1H) {
                DatapointTooltip.drawVolume(graphics, config, volumePa, bounds, hoveredPoint);
            } else {
                DatapointTooltip.draw(graphics, config, pricePa, bounds, hoveredPoint);
            }
        }
    }

    private void drawSuggestedPriceLine(Graphics2D graphics, Config config) {
        if (priceLine == null) { return; }
        int y = bounds.toY(pricePa, priceLine.getPrice());
        graphics.setColor(Color.WHITE);
        Stroke previousStroke = graphics.getStroke();
        graphics.setStroke(Config.DOTTED_STROKE);
        graphics.drawLine(pricePa.x, y, pricePa.x + pricePa.width, y);
        graphics.setStroke(previousStroke);

        String label = priceLine.getMessage();
        if (label == null || label.isBlank()) { return; }
        var metrics = graphics.getFontMetrics();
        int labelWidth = metrics.stringWidth(label);
        int labelX = pricePa.x + pricePa.width - labelWidth - Config.LABEL_PADDING;
        int labelY = priceLine.isTextAbove()
                ? Math.max(pricePa.y + metrics.getAscent(), y - (Config.LABEL_PADDING / 2))
                : Math.min(pricePa.y + pricePa.height - Config.LABEL_PADDING, y + metrics.getAscent() + (Config.LABEL_PADDING / 2));
        graphics.drawString(label, labelX, labelY);
    }

    private int dynamicPointSize(int baseSize, Bounds bounds) {
        int td = bounds.xMax - bounds.xMin;
        if (td < Constants.DAY_SECONDS) { return (int) ((float) baseSize * 1.25); } else if (td > Constants.DAY_SECONDS * 20) {
            return (int) ((float) baseSize * 0.5);
        } else if (td > Constants.DAY_SECONDS * 6) {
            return (int) ((float) baseSize * 0.75);
        }
        return baseSize;
    }
}
