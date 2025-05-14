package com.flippingcopilot.ui.graph;

import com.flippingcopilot.manger.PriceGraphConfigManager;
import com.flippingcopilot.ui.graph.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GraphPanel extends JPanel {
    public final String itemName;

    // Component references
    private final PriceGraphConfigManager configManager;
    public DataManager dataManager;
    private final RenderV2 renderer;
    public final ZoomHandler zoomHandler;
    private final DatapointTooltip tooltip;
    public final PlotArea pa;

    // For point hovering
    private Point mousePosition = new Point(0,0);
    private Datapoint hoveredPoint = null;

    public GraphPanel(DataManager dm, PriceGraphConfigManager configManager) {
        this.itemName = dm.data.name;

        // Initialize components
        this.dataManager = dm;
        this.configManager = configManager;
        this.renderer = new RenderV2();
        this.zoomHandler = new ZoomHandler();
        this.tooltip = new DatapointTooltip();

        // Set up panel
        setBackground(configManager.getConfig().backgroundColor);
        setPreferredSize(new Dimension(500, 300));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        this.pa = new PlotArea();
        zoomHandler.maxViewBounds = dataManager.calculateBounds((p) -> true);
        zoomHandler.homeViewBounds = dataManager.calculateBounds((p) -> p.time > zoomHandler.maxViewBounds.xMax - 4 * Constants.DAY_SECONDS);
        zoomHandler.weekViewBounds = dataManager.calculateBounds((p) -> p.time > zoomHandler.maxViewBounds.xMax - 7 * Constants.DAY_SECONDS);
        zoomHandler.monthViewBounds = dataManager.calculateBounds((p) -> p.time > zoomHandler.maxViewBounds.xMax - 30 * Constants.DAY_SECONDS);
        pa.bounds = zoomHandler.homeViewBounds.copy();
        setupMouseListeners();
    }


    private void setupMouseListeners() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mousePosition = e.getPoint();
                hoveredPoint = dataManager.findClosestPoint(pa.relativePoint(e.getPoint()), Config.HOVER_RADIUS, pa);
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                mousePosition = e.getPoint();
                Point plotPoint = pa.relativePoint(mousePosition);
                if (!pa.pointInPlotArea(plotPoint)) {
                    return;
                }

                if (zoomHandler.isOverHomeButton(plotPoint)) {
                    zoomHandler.applyHomeView(pa);
                    repaint();
                    return;
                }
                if (zoomHandler.isOverMaxButton(plotPoint)) {
                    zoomHandler.applyMaxView(pa);
                    repaint();
                    return;
                }
                if (zoomHandler.isOverZoomInButton(plotPoint)) {
                    zoomHandler.applyZoomIn(pa);
                    repaint();
                    return;
                }
                if (zoomHandler.isOverZoomOutButton(plotPoint)) {
                    zoomHandler.applyZoomOut(pa);
                    repaint();
                    return;
                }
                if (zoomHandler.isOverWeekButton(plotPoint)) {
                    zoomHandler.applyWeekView(pa);
                    repaint();
                    return;
                }
                if (zoomHandler.isOverMonthButton(plotPoint)) {
                    zoomHandler.applyMonthView(pa);
                    repaint();
                    return;
                }

                zoomHandler.startSelection(plotPoint);
                hoveredPoint = null;
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mousePosition = e.getPoint();
                if (zoomHandler.isSelecting()) {
                    zoomHandler.setSelectionEnd(pa.relativePoint(mousePosition));
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mousePosition = e.getPoint();
                if (zoomHandler.isSelecting()) {
                    setCursor(Cursor.getDefaultCursor());
                    zoomHandler.setSelectionEnd(pa.relativePoint(mousePosition));
                    zoomHandler.applySelection(pa);
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
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
        pa.w = getWidth() - pa.leftPadding - pa.rightPadding;
        pa.h = getHeight() - pa.topPadding - pa.bottomPadding;


        Data data = dataManager.getData();
        if (data == null) return;
        Config config = configManager.getConfig();
        setBackground(config.backgroundColor);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Graphics2D plotAreaG2 = (Graphics2D) g2.create(pa.leftPadding, pa.topPadding, pa.w, pa.h);

        // First draw the legend above the plot area
        renderer.drawLegend(g2, config, pa);

        // Draw the plot area background with dynamic padding
        plotAreaG2.setColor(config.plotAreaColor);
        plotAreaG2.fillRect(0,0, pa.w, pa.h);

        TimeAxis xAxis = AxisCalculator.calculateTimeAxis(pa, AxisCalculator.getLocalTimeOffsetSeconds());
        PriceAxis yAxis = AxisCalculator.calculatePriceAxis(pa);
        renderer.drawGrid(plotAreaG2, config, pa, xAxis, yAxis);
        renderer.drawAxes(g2, config, pa, xAxis, yAxis);


        int pointSize = dynamicPointSize(Config.BASE_POINT_SIZE, pa);
        renderer.drawPoints(plotAreaG2, pa, dataManager.lowDatapoints, config.lowColor, pointSize);
        renderer.drawPoints(plotAreaG2, pa, dataManager.highDatapoints, config.highColor, pointSize);
        if (config.connectPoints) {
            renderer.drawLines(plotAreaG2, pa, dataManager.lowDatapoints, config.lowColor, Config.NORMAL_STROKE);
            renderer.drawLines(plotAreaG2, pa, dataManager.highDatapoints, config.highColor, Config.NORMAL_STROKE);
        }
        renderer.drawStartPoints(plotAreaG2, pa, dataManager.buyPriceDataPoint(), Color.WHITE, pointSize);
        renderer.drawStartPoints(plotAreaG2, pa, dataManager.sellPriceDataPoint(), Color.WHITE, pointSize);

        renderer.drawLines(plotAreaG2, pa, dataManager.predictionLowDatapoints, config.lowColor, Config.DOTTED_STROKE);
        renderer.drawLines(plotAreaG2, pa, dataManager.predictionHighDatapoints, config.highColor, Config.DOTTED_STROKE);
        renderer.drawPredictionIQR(plotAreaG2, config, pa, data.predictionTimes, data.predictionLowIQRLower, data.predictionLowIQRUpper, true);
        renderer.drawPredictionIQR(plotAreaG2, config, pa, data.predictionTimes, data.predictionHighIQRLower, data.predictionHighIQRUpper, false);
        zoomHandler.drawButtons(plotAreaG2, pa, pa.relativePoint(mousePosition));
        zoomHandler.drawSelectionRectangle(plotAreaG2);

        // Draw tooltip for hovered point
        if (hoveredPoint != null) {
            tooltip.draw(plotAreaG2, config, pa, hoveredPoint);
        }
    }

    private int dynamicPointSize(int baseSize, PlotArea pa) {
        int td = pa.bounds.xMax - pa.bounds.xMin;
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