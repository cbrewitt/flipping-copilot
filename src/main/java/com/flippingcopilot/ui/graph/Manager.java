package com.flippingcopilot.ui.graph;

import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.manger.PriceGraphConfigManager;
import com.flippingcopilot.ui.graph.model.Config;
import com.flippingcopilot.ui.graph.model.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Utility class to manage the display of price graphs in RuneLite.
 */
@Slf4j
public class Manager {

    private static JDialog currentDialog = null;
    private static JPanel mainPanel = null;
    private static Data currentData = null;
    private static String currentItemName = null;
    private static boolean isShowingSettings = false;

    /**
     * Shows a price graph panel in a popup dialog that can be moved and resized.
     *
     * @param parent The parent component (usually a button that was clicked)
     * @param data   The price data to display
     */
    public static void showPriceGraph(Component parent, Data data, PriceGraphConfigManager configManager, FlippingCopilotConfig copilotConfig) {
        try {
            log.info("Showing price graph panel for item: " + data.name);

            // Store the data and item name for later use
            currentData = data;
            currentItemName = data.name;

            // If there's already a dialog showing, dispose it
            if (currentDialog != null) {
                log.info("Disposing existing dialog");
                currentDialog.dispose();
            }

            // Find the parent frame
            Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(parent);
            if (parentFrame == null) {
                log.error("Could not find parent frame");
                return;
            }

            // Create new dialog with decorations for moving and resizing
            JDialog dialog = new JDialog(parentFrame);
            dialog.setUndecorated(false); // Keep window decorations to allow resizing
            dialog.setTitle(data.name + " analysis");
            dialog.setResizable(true); // Allow resizing

            // Set minimum size to prevent making the graph too small
            dialog.setMinimumSize(new Dimension(500, 250));

            // Create main panel to contain the components
            mainPanel = new JPanel(new BorderLayout());

            // Create and show the graph view
            showGraphView(configManager, copilotConfig);

            // Set the content pane
            dialog.setContentPane(mainPanel);

            // Set the initial size
            dialog.setSize(700, 400);

            // Position the dialog within the RuneLite window
            try {
                Dimension parentSize = parentFrame.getSize();
                log.info("parent size is {}", parentSize);
                dialog.setSize(parentSize.width, parentSize.height);
                Point parentLocation = parentFrame.getLocationOnScreen();
                Dimension dialogSize = dialog.getSize();

                // Calculate initial position (centered or near the button)
                int x = parentLocation.x + (parentSize.width - dialogSize.width) / 2;
                int y = parentLocation.y + (parentSize.height - dialogSize.height) / 2;

                dialog.setLocation(x, y);
                log.info("Positioned dialog at (" + x + ", " + y + ")");
            } catch (IllegalComponentStateException e) {
                log.error("error calculating dialog position", e);
                dialog.setLocationRelativeTo(parentFrame);
            }

            // Make the dialog modal so it stays on top but doesn't block
            dialog.setModalityType(Dialog.ModalityType.MODELESS);

            // Add resize listener to update the graph when window size changes
            dialog.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (!isShowingSettings && mainPanel.getComponentCount() > 0) {
                        Component comp = mainPanel.getComponent(0);
                        if (comp instanceof JSplitPane) {
                            JSplitPane splitPane = (JSplitPane) comp;
                            Component leftComp = splitPane.getLeftComponent();
                            if (leftComp instanceof GraphPanel) {
                                ((GraphPanel) leftComp).repaint();
                            }
                        }
                    }
                }
            });

            // Store the current dialog reference
            currentDialog = dialog;

            // Set default close operation
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            // Make dialog visible
            dialog.setVisible(true);
            log.info("Price graph panel shown successfully");
        } catch (Exception e) {
            log.error("Error showing price graph panel: " + e.getMessage(), e);
        }
    }

    // Modified showGraphView method in Manager.java
    private static void showGraphView(PriceGraphConfigManager configManager, FlippingCopilotConfig copilotConfig) {
        if (mainPanel == null || currentData == null) {
            log.error("Cannot show graph view, main panel or data is null");
            return;
        }

        // Clear the main panel
        mainPanel.removeAll();

        // Create the stats panel
        DataManager dm = new DataManager(currentData);
        // Create the graph panel
        GraphPanel graphPanel = new GraphPanel(dm, configManager);

        // Create settings button with gear icon
        JPanel statsHeaderPanel = new JPanel(new BorderLayout());
        statsHeaderPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        try {
            BufferedImage gearIcon = ImageUtil.loadImageResource(Manager.class, "/preferences-icon.png");
            gearIcon = ImageUtil.resizeImage(gearIcon, 20, 20);
            BufferedImage recoloredIcon = ImageUtil.recolorImage(gearIcon, ColorScheme.LIGHT_GRAY_COLOR);
            JLabel settingsButton = ConfigPanel.buildButton(recoloredIcon, "Settings", () -> {
                isShowingSettings = true;
                showSettingsView(configManager, copilotConfig);
            });
            statsHeaderPanel.add(settingsButton, BorderLayout.EAST); // Move to EAST instead of WEST
            statsHeaderPanel.setBackground(configManager.getConfig().backgroundColor);
        } catch (Exception e) {
            log.error("Error creating settings button", e);
            // Fallback to text button if icon loading fails
            JButton settingsButton = new JButton("Settings");
            settingsButton.addActionListener(e1 -> {
                isShowingSettings = true;
                showSettingsView(configManager, copilotConfig);
            });
            statsHeaderPanel.add(settingsButton, BorderLayout.EAST); // Move to EAST instead of WEST
        }


        StatsPanel statsPanel = new StatsPanel(dm, configManager, copilotConfig);
        statsPanel.setBackground(configManager.getConfig().backgroundColor);

        // Create stats container that includes the header and stats panel
        JPanel statsContainer = new JPanel(new BorderLayout());
        statsContainer.add(statsHeaderPanel, BorderLayout.NORTH);
        statsContainer.add(statsPanel, BorderLayout.CENTER);

        // Create split pane with graph on left and stats container on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        splitPane.setLeftComponent(graphPanel);
        splitPane.setRightComponent(statsContainer);
        splitPane.setResizeWeight(1.0); // Graph gets all extra space
        splitPane.setDividerLocation(0.75); // Initial position of divider at 75%

        // Add padding
        graphPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        statsContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        statsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Add components to main panel - no more top panel for the entire window
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Update the dialog title if needed
        if (currentDialog != null && currentItemName != null) {
            currentDialog.setTitle(currentItemName + " analysis");
        }

        // Update UI
        mainPanel.revalidate();
        mainPanel.repaint();

        isShowingSettings = false;
    }

    /**
     * Shows the settings view in the dialog
     */
    private static void showSettingsView(PriceGraphConfigManager configManager, FlippingCopilotConfig copilotConfig) {
        if (mainPanel == null) {
            log.error("Cannot show settings view, main panel is null");
            return;
        }

        // Clear the main panel
        mainPanel.removeAll();

        // Create the top panel for back button
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Create back button with gear icon (reusing the same icon for simplicity)
        try {
            BufferedImage gearIcon = ImageUtil.loadImageResource(Manager.class, "/preferences-icon.png");
            gearIcon = ImageUtil.resizeImage(gearIcon, 20, 20);
            BufferedImage recoloredIcon = ImageUtil.recolorImage(gearIcon, ColorScheme.LIGHT_GRAY_COLOR);
            JLabel backButton = ConfigPanel.buildButton(recoloredIcon, "Back to Graph", () -> {
                isShowingSettings = false;
                showGraphView(configManager, copilotConfig);
            });
            topPanel.setBackground(configManager.getConfig().backgroundColor);
            topPanel.add(backButton, BorderLayout.EAST);
        } catch (Exception e) {
            log.error("Error creating back button", e);
        }

        // Create settings panel
        ConfigPanel configPanel = new ConfigPanel(configManager);

        // Set the callback to return to graph view when "Apply" is clicked
        configPanel.setOnApplyCallback(() -> {
            isShowingSettings = false;
            showGraphView(configManager, copilotConfig);
        });

        // Add components to main panel
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(configPanel, BorderLayout.CENTER);

        // Update the dialog title
        if (currentDialog != null) {
            currentDialog.setTitle("Graph Settings");
        }

        // Update UI
        mainPanel.revalidate();
        mainPanel.repaint();
    }
}