package com.flippingcopilot.ui.graph;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.manger.PriceGraphConfigManager;
import com.flippingcopilot.model.ItemPrice;
import com.flippingcopilot.model.OsrsLoginManager;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.graph.model.Constants;
import com.flippingcopilot.ui.graph.model.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

import static com.google.common.base.MoreObjects.firstNonNull;


@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PriceGraphController {

    private final JLabel itemIcon = new JLabel(new ImageIcon(ImageUtil.loadImageResource(getClass(),"/small_open_arrow.png")));

    // dependencies
    private final PriceGraphConfigManager configManager;
    private final FlippingCopilotConfig copilotConfig;
    private final ApiRequestHandler apiRequestHandler;
    private final OsrsLoginManager osrsLoginManager;
    private final ItemManager itemManager;

    // state
    private JDialog currentDialog = null;
    private JPanel mainPanel = null;
    private boolean isShowingSettings = false;
    private boolean currentIsSuggestedItem = false;

    @Getter
    private Data suggestedItemGraphData;

    @Getter
    private Data userItemGraphData;
    private GraphPanel graphPanel;


    // Custom setters to handle data updates
    public void setSuggestedItemGraphData(Data data) {
        this.suggestedItemGraphData = data;
        if (currentDialog != null && currentIsSuggestedItem) {
            updateUIAfterDataChange(data);
        }
    }

    public void setUserItemGraphData(Data data) {
        this.userItemGraphData = data;
        if (currentDialog != null && !currentIsSuggestedItem) {
            updateUIAfterDataChange(data);
        }
    }

    private void updateUIAfterDataChange(Data data) {
        SwingUtilities.invokeLater(() -> {
            if (currentDialog != null && mainPanel != null) {
                if (data != null && data.getLoadingErrorMessage() != null && !data.getLoadingErrorMessage().isEmpty()) {
                    showErrorView(data.getLoadingErrorMessage());
                } else if (data != null) {
                    showGraphView(data.name, data);
                }
            }
        });
    }

    public void loadAndAndShowPriceGraph(Component parent,  int itemId) {
        Consumer<ItemPrice> consumer = (ItemPrice i) -> {
            Data d = firstNonNull(i.getGraphData(), new Data());
            d.loadingErrorMessage = i.getMessage();
            setUserItemGraphData(d);
        };
        ItemComposition item = itemManager.getItemComposition(itemId);
        apiRequestHandler.asyncGetItemPriceWithGraphData(itemId, osrsLoginManager.getPlayerDisplayName(), consumer);
        showPriceGraph(parent, item.getName(), false);
    }

    public void showPriceGraph(Component parent, String itemName, boolean isSuggestedItem) {
        try {
            log.info("Showing price graph panel for item: " + itemName);
            this.currentIsSuggestedItem = isSuggestedItem;

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
            dialog.setTitle(itemName + " analysis");
            dialog.setResizable(true); // Allow resizing

            // Set minimum size to prevent making the graph too small
            dialog.setMinimumSize(new Dimension(500, 250));

            // Create main panel to contain the components
            mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

            // Check if data is available
            Data currentData = isSuggestedItem ? suggestedItemGraphData : userItemGraphData;

            if (currentData == null) {
                // Show loading view
                showLoadingView(itemName);
            } else if (currentData.getLoadingErrorMessage() != null && !currentData.getLoadingErrorMessage().isEmpty()) {
                // Show error view
                showErrorView(currentData.getLoadingErrorMessage());
            } else {
                // Show graph view
                showGraphView(itemName, currentData);
            }

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

    private void showLoadingView(String itemName) {
        mainPanel.removeAll();

        // Create loading panel
        JPanel loadingPanel = new JPanel(new GridBagLayout());
        loadingPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Add spinner
        Spinner spinner = new Spinner();
        spinner.show();
        loadingPanel.add(spinner, gbc);

        // Add loading text
        gbc.gridy = 1;
        JLabel loadingLabel = new JLabel("Loading price data for " + itemName + "...");
        loadingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loadingLabel.setFont(loadingLabel.getFont().deriveFont(Font.BOLD, 14f));
        loadingPanel.add(loadingLabel, gbc);

        mainPanel.add(loadingPanel, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private void showErrorView(String errorMessage) {
        mainPanel.removeAll();

        // Create error panel
        JPanel errorPanel = new JPanel(new GridBagLayout());
        errorPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Add error icon (optional - you could add a red X icon here)
        JLabel errorIcon = new JLabel("⚠");
        errorIcon.setForeground(Color.RED);
        errorIcon.setFont(errorIcon.getFont().deriveFont(Font.BOLD, 24f));
        errorPanel.add(errorIcon, gbc);

        // Add error message
        gbc.gridy = 1;
        JLabel errorLabel = new JLabel("<html><center>" + errorMessage + "</center></html>");
        errorLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        errorLabel.setFont(errorLabel.getFont().deriveFont(14f));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        errorPanel.add(errorLabel, gbc);

        // Add close button
        gbc.gridy = 2;
        gbc.insets = new Insets(20, 10, 10, 10);
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            if (currentDialog != null) {
                currentDialog.dispose();
            }
        });
        errorPanel.add(closeButton, gbc);

        mainPanel.add(errorPanel, BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    // Modified showGraphView method in Manager.java
    private void showGraphView(String itemName, Data data) {
        if (mainPanel == null) {
            log.error("Cannot show graph view, main panel or data is null");
            return;
        }
        if(graphPanel != null && graphPanel.itemName.equals(itemName)) {
            // if it's the same item just update the data and repaint
            DataManager dm = new DataManager(data);
            graphPanel.dataManager = dm;
            graphPanel.zoomHandler.maxViewBounds = dm.calculateBounds((p) -> true);
            graphPanel.zoomHandler.homeViewBounds = dm.calculateBounds((p) -> p.time > graphPanel.zoomHandler.maxViewBounds.xMax - 4 * Constants.DAY_SECONDS);
            graphPanel.zoomHandler.weekViewBounds = dm.calculateBounds((p) -> p.time > graphPanel.zoomHandler.maxViewBounds.xMax - 7 * Constants.DAY_SECONDS);
            graphPanel.zoomHandler.monthViewBounds = dm.calculateBounds((p) -> p.time > graphPanel.zoomHandler.maxViewBounds.xMax - 30 * Constants.DAY_SECONDS);
            graphPanel.repaint();
            return;
        }
        // Clear the main panel
        setItemIcon(data.itemId);
        mainPanel.removeAll();
        DataManager dm = new DataManager(data);
        graphPanel = new GraphPanel(dm, configManager);

        // Create settings button with gear icon
        JPanel statsHeaderPanel = new JPanel(new BorderLayout());
        statsHeaderPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        try {
            BufferedImage gearIcon = ImageUtil.loadImageResource(PriceGraphController.class, "/preferences-icon.png");
            gearIcon = ImageUtil.resizeImage(gearIcon, 20, 20);
            BufferedImage recoloredIcon = ImageUtil.recolorImage(gearIcon, ColorScheme.LIGHT_GRAY_COLOR);
            JLabel settingsButton = ConfigPanel.buildButton(recoloredIcon, "Settings", () -> {
                isShowingSettings = true;
                showSettingsView(itemName, data);
            });
            statsHeaderPanel.add(settingsButton, BorderLayout.EAST); // Move to EAST instead of WEST
            statsHeaderPanel.setBackground(configManager.getConfig().backgroundColor);
        } catch (Exception e) {
            log.error("Error creating settings button", e);
            // Fallback to text button if icon loading fails
            JButton settingsButton = new JButton("Settings");
            settingsButton.addActionListener(e1 -> {
                isShowingSettings = true;
                showSettingsView(itemName, data);
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
        if (currentDialog != null && itemName != null) {
            currentDialog.setTitle(itemName + " analysis");
        }

        // Update UI
        mainPanel.revalidate();
        mainPanel.repaint();

        isShowingSettings = false;
    }

    /**
     * Shows the settings view in the dialog
     */
    private  void showSettingsView(String itemName, Data data) {
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
            BufferedImage gearIcon = ImageUtil.loadImageResource(PriceGraphController.class, "/preferences-icon.png");
            gearIcon = ImageUtil.resizeImage(gearIcon, 20, 20);
            BufferedImage recoloredIcon = ImageUtil.recolorImage(gearIcon, ColorScheme.LIGHT_GRAY_COLOR);
            JLabel backButton = ConfigPanel.buildButton(recoloredIcon, "Back to Graph", () -> {
                isShowingSettings = false;
                showGraphView(itemName, data);
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
            showGraphView(itemName, data);
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


    private void setItemIcon(int itemId) {
        itemIcon.setVisible(false);
        AsyncBufferedImage image = itemManager.getImage(itemId);
        if (image != null) {
            image.addTo(itemIcon);
            itemIcon.setVisible(true);
        }
    }
}