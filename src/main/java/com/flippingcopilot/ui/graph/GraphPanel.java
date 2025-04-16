package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.ui.graph.model.Datapoint;
import com.flippingcopilot.ui.graph.model.PriceAxis;
import com.flippingcopilot.ui.graph.model.TimeAxis;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.stream.Collectors;
import java.util.List;

public class GraphPanel extends JPanel {
    public final String itemName;

    // Component references
    private final DataManager dataManager;
    private final RenderV2 renderer;
    private final ZoomHandler zoomHandler;
    private final DatapointTooltip tooltip;
    private final PlotArea pa;

    // For point hovering
    private Point mousePosition = new Point(0,0);
    private Datapoint hoveredPoint = null;

    public GraphPanel(Data data) {
        this.itemName = data.name;

        // Initialize components
        this.dataManager = new DataManager(data);
        this.renderer = new RenderV2();
        this.zoomHandler = new ZoomHandler();
        this.tooltip = new DatapointTooltip();

        // Set up panel
        setBackground(Config.BACKGROUND_COLOR);
        setPreferredSize(new Dimension(500, 300));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        this.pa = new PlotArea();
        zoomHandler.maxViewBounds = dataManager.calculateMaxBounds();
        int homeDays = 4;
        List<Datapoint> homeDatapoints = dataManager.datapoints.stream().filter((p) -> p.time > zoomHandler.maxViewBounds.xMax - homeDays * AxisCalculator.DAY_SECONDS).collect(Collectors.toList());
        zoomHandler.homeViewBounds = dataManager.calculateBounds(homeDatapoints);
        List<Datapoint> weekDatapoints = dataManager.datapoints.stream().filter((p) -> p.time > zoomHandler.maxViewBounds.xMax - 7 * AxisCalculator.DAY_SECONDS).collect(Collectors.toList());
        zoomHandler.weekViewBounds = dataManager.calculateBounds(weekDatapoints);
        List<Datapoint> monthDatapoints = dataManager.datapoints.stream().filter((p) -> p.time > zoomHandler.maxViewBounds.xMax - 30 * AxisCalculator.DAY_SECONDS).collect(Collectors.toList());
        zoomHandler.monthViewBounds = dataManager.calculateBounds(monthDatapoints);
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

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Graphics2D plotAreaG2 = (Graphics2D) g2.create(pa.leftPadding, pa.topPadding, pa.w, pa.h);

        // First draw the legend above the plot area
        renderer.drawLegend(g2, pa.leftPadding + pa.w / 2);

        // Draw the plot area background with dynamic padding
        plotAreaG2.setColor(Config.PLOT_AREA_COLOR);
        plotAreaG2.fillRect(0,0, pa.w, pa.h);

        TimeAxis xAxis = AxisCalculator.calculateTimeAxis(pa, AxisCalculator.getLocalTimeOffsetSeconds());
        PriceAxis yAxis = AxisCalculator.calculatePriceAxis(pa);
        renderer.drawGrid(plotAreaG2, pa, xAxis, yAxis);
        renderer.drawAxes(g2, pa, xAxis, yAxis);


        int pointSize = dynamicPointSize(Config.POINT_SIZE, pa);
        renderer.drawPoints(plotAreaG2, pa, data.lowTimes, data.lowPrices, Config.LOW_COLOR, pointSize);
        renderer.drawPoints(plotAreaG2, pa, data.highTimes, data.highPrices, Config.HIGH_COLOR, pointSize);
        if (Config.CONNECT_POINTS) {
            renderer.drawLines(plotAreaG2, pa, data.lowTimes, data.lowPrices, Config.LOW_COLOR, Config.NORMAL_STROKE);
            renderer.drawLines(plotAreaG2, pa, data.highTimes, data.highPrices, Config.HIGH_COLOR, Config.NORMAL_STROKE);
        }


        renderer.drawLines(plotAreaG2, pa, data.predictionTimes, data.predictionLowMeans, Config.LOW_COLOR, Config.DOTTED_STROKE);
        renderer.drawLines(plotAreaG2, pa, data.predictionTimes, data.predictionHighMeans, Config.HIGH_COLOR, Config.DOTTED_STROKE);
        renderer.drawPredictionIQR(plotAreaG2,pa, data.predictionTimes, data.predictionLowIQRLower, data.predictionLowIQRUpper, true);
        renderer.drawPredictionIQR(plotAreaG2,pa, data.predictionTimes, data.predictionHighIQRLower, data.predictionHighIQRUpper, false);
        zoomHandler.drawButtons(plotAreaG2, pa, pa.relativePoint(mousePosition));
        zoomHandler.drawSelectionRectangle(plotAreaG2);

        // Draw tooltip for hovered point
        if (hoveredPoint != null) {
            tooltip.draw(plotAreaG2, hoveredPoint, getWidth(), getHeight());
        }
    }

    private int dynamicPointSize(int baseSize, PlotArea pa) {
        int td = pa.bounds.xMax - pa.bounds.xMin;
        if (td < AxisCalculator.DAY_SECONDS) {
            return (int) ((float) baseSize * 1.25);
        } else if (td > AxisCalculator.DAY_SECONDS * 20) {
            return (int) ((float) baseSize * 0.5);
        } else if (td > AxisCalculator.DAY_SECONDS * 6) {
            return (int) ((float) baseSize * 0.75);
        }
        return baseSize;
    }
}