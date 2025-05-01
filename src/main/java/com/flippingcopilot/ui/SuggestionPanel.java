package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.controller.GrandExchange;
import com.flippingcopilot.controller.HighlightController;
import com.flippingcopilot.manger.PriceGraphConfigManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.graph.Manager;
import com.flippingcopilot.ui.graph.model.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import static com.flippingcopilot.ui.UIUtilities.*;
import static com.flippingcopilot.util.Constants.MIN_GP_NEEDED_TO_FLIP;


@Singleton
@Slf4j
public class SuggestionPanel extends JPanel {

    // dependencies
    private final FlippingCopilotConfig config;
    private final SuggestionManager suggestionManager;
    private final AccountStatusManager accountStatusManager;
    public final PauseButton pauseButton;
    private final BlockButton blockButton;
    private final OsrsLoginManager osrsLoginManager;
    private final Client client;
    private final PausedManager pausedManager;
    private final GrandExchangeUncollectedManager uncollectedManager;
    private final ClientThread clientThread;
    private final HighlightController highlightController;
    private final ItemManager itemManager;
    private final GrandExchange grandExchange;
    private final PriceGraphConfigManager priceGraphConfigManager;

    private final JLabel suggestionText = new JLabel();
    private final JLabel suggestionIcon = new JLabel(new ImageIcon(ImageUtil.loadImageResource(getClass(),"/small_open_arrow.png")));
    private final JPanel suggestionTextContainer = new JPanel();
    public final Spinner spinner = new Spinner();
    private JLabel skipButton;
    private final JPanel buttonContainer = new JPanel();
    private JLabel graphButton;
    private final JPanel suggestedActionPanel;
    private final PreferencesPanel preferencesPanel;
    private final JLayeredPane layeredPane = new JLayeredPane();
    private boolean isPreferencesPanelVisible = false;
    private final JLabel gearButton;
    private String innerSuggestionMessage;
    private String highlightedColor = "yellow";

    @Setter
    private String serverMessage = "";


    @Inject
    public SuggestionPanel(FlippingCopilotConfig config,
                           SuggestionManager suggestionManager,
                           AccountStatusManager accountStatusManager,
                           PauseButton pauseButton,
                           BlockButton blockButton,
                           PreferencesPanel preferencesPanel,
                           OsrsLoginManager osrsLoginManager,
                           Client client, PausedManager pausedManager,
                           GrandExchangeUncollectedManager uncollectedManager,
                           ClientThread clientThread,
                           HighlightController highlightController,
                           ItemManager itemManager,
                           GrandExchange grandExchange, PriceGraphConfigManager priceGraphConfigManager) {
        this.preferencesPanel = preferencesPanel;
        this.config = config;
        this.suggestionManager = suggestionManager;
        this.accountStatusManager = accountStatusManager;
        this.pauseButton = pauseButton;
        this.blockButton = blockButton;
        this.osrsLoginManager = osrsLoginManager;
        this.client = client;
        this.pausedManager = pausedManager;
        this.uncollectedManager = uncollectedManager;
        this.clientThread = clientThread;
        this.highlightController = highlightController;
        this.itemManager = itemManager;
        this.grandExchange = grandExchange;
        this.priceGraphConfigManager = priceGraphConfigManager;

        // Create the layered pane first
        layeredPane.setLayout(null);  // LayeredPane needs null layout

        // Create a main panel that will hold all the regular components
        suggestedActionPanel = new JPanel(new BorderLayout());
        suggestedActionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestedActionPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        suggestedActionPanel.setBounds(0, 0, 300, 150);  // Set appropriate size
        JLabel title = new JLabel("<html><center> <FONT COLOR=white><b>Suggested Action:" +
                "</b></FONT></center></html>");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        suggestedActionPanel.add(title, BorderLayout.NORTH);

        JPanel suggestionContainer = new JPanel();
        suggestionContainer.setLayout(new CardLayout());
        suggestionContainer.setOpaque(true);
        suggestionContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionContainer.setPreferredSize(new Dimension(0, 85));
        suggestedActionPanel.add(suggestionContainer, BorderLayout.CENTER);

        suggestionTextContainer.setLayout(new BoxLayout(suggestionTextContainer, BoxLayout.X_AXIS));
        suggestionTextContainer.add(Box.createHorizontalGlue());
        suggestionTextContainer.add(suggestionIcon);
        suggestionTextContainer.add(suggestionText);
        suggestionTextContainer.add(Box.createHorizontalGlue());
        suggestionTextContainer.setOpaque(true);
        suggestionTextContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionIcon.setVisible(false);
        suggestionIcon.setOpaque(true);
        suggestionIcon.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        suggestionText.setHorizontalAlignment(SwingConstants.CENTER);
        suggestionText.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        suggestionContainer.add(suggestionTextContainer);

        suggestionContainer.add(spinner);
        setupButtonContainer();
        suggestedActionPanel.add(buttonContainer, BorderLayout.SOUTH);




        layeredPane.add(suggestedActionPanel, JLayeredPane.DEFAULT_LAYER);

        // Build the suggestion preferences panel:
        this.preferencesPanel.setVisible(false);
        layeredPane.add(this.preferencesPanel, JLayeredPane.DEFAULT_LAYER);

        // Create and add the gear button
        BufferedImage gearIcon = ImageUtil.loadImageResource(getClass(), "/preferences-icon.png");
        gearIcon = ImageUtil.resizeImage(gearIcon, 20, 20);
        BufferedImage recoloredIcon = ImageUtil.recolorImage(gearIcon, ColorScheme.LIGHT_GRAY_COLOR);
        gearButton = buildButton(recoloredIcon, "Settings", () -> {});
        gearButton.setEnabled(true);
        gearButton.setFocusable(true);
        gearButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        gearButton.setOpaque(true);
        ImageIcon iconOff = new ImageIcon(recoloredIcon);
        ImageIcon iconOn = new ImageIcon(ImageUtil.luminanceScale(recoloredIcon, BUTTON_HOVER_LUMINANCE));
        // Replace the existing gear button MouseAdapter with this implementation
        gearButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isEventDispatchThread()) {
                    SwingUtilities.invokeLater(() -> handleGearClick());
                    return;
                }
                handleGearClick();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                gearButton.setIcon(iconOn);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                gearButton.setIcon(iconOff);
            }
        });
        gearButton.setOpaque(true);
        gearButton.setBounds(5, 5, 20, 20);
        layeredPane.add(gearButton, JLayeredPane.PALETTE_LAYER);

        // Set up the main panel
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setPreferredSize(new Dimension(0, 150));

        add(layeredPane);

        // Add a component listener to handle resizing
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                preferencesPanel.setBounds(0, 0, getWidth(), getHeight());
                suggestedActionPanel.setBounds(0, 0, getWidth(), getHeight());
                layeredPane.setPreferredSize(new Dimension(getWidth(), getHeight()));
            }
        });
    }

    // Add this as a private method in the class
    private void handleGearClick() {
//        Data data = getPriceData();
//
//        Manager.showPriceGraph(graphButton, data);


        isPreferencesPanelVisible = !isPreferencesPanelVisible;
        preferencesPanel.setVisible(isPreferencesPanelVisible);
        suggestedActionPanel.setVisible(!isPreferencesPanelVisible);
        refresh();
        layeredPane.revalidate();
        layeredPane.repaint();
    }

    private void setupButtonContainer() {
        buttonContainer.setLayout(new BorderLayout());
        buttonContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    
        JPanel centerPanel = new JPanel(new GridLayout(1, 5, 15, 0));
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    
        BufferedImage graphIcon = ImageUtil.loadImageResource(getClass(), "/graph.png");
        graphButton = buildButton(graphIcon, "Price graph", () -> {
            if(config.priceGraphWebsite().equals(FlippingCopilotConfig.PriceGraphWebsite.COPILOT)) {
                Data priceData = suggestionManager.getSuggestion().getGraphData();
                Manager.showPriceGraph(graphButton, priceData, priceGraphConfigManager, config);
            } else {
                Suggestion suggestion = suggestionManager.getSuggestion();
                String url = config.priceGraphWebsite().getUrl(suggestion.getName(), suggestion.getItemId());
                LinkBrowser.browse(url);
            }
        });
        centerPanel.add(graphButton);
    
        JPanel emptyPanel = new JPanel();
        emptyPanel.setOpaque(false);
        centerPanel.add(emptyPanel);
        centerPanel.add(pauseButton);
        centerPanel.add(blockButton);
    
        BufferedImage skipIcon = ImageUtil.loadImageResource(getClass(), "/skip.png");
        skipButton = buildButton(skipIcon, "Skip suggestion", () -> {
            showLoading();
            Suggestion s = suggestionManager.getSuggestion();
            accountStatusManager.setSkipSuggestion(s != null ? s.getId() : -1);
            suggestionManager.setSuggestionNeeded(true);
        });
        centerPanel.add(skipButton);
        
        buttonContainer.add(centerPanel, BorderLayout.CENTER);
    }


    private void setItemIcon(int itemId) {
        AsyncBufferedImage image = itemManager.getImage(itemId);
        if (image != null) {
            image.addTo(suggestionIcon);
            suggestionIcon.setVisible(true);
        }
    }


    public void updateSuggestion(Suggestion suggestion) {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        String suggestionString = "<html><center>";
        suggestionTextContainer.setVisible(false);

        switch (suggestion.getType()) {
            case "wait":
                suggestionString += "Wait <br>";
                break;
            case "abort":
                suggestionString += "Abort offer for<br><FONT COLOR=white>" + suggestion.getName() + "<br></FONT>";
                setItemIcon(suggestion.getItemId());
                break;
            case "buy":
            case "sell":
                String capitalisedAction = suggestion.getType().equals("buy") ? "Buy" : "Sell";
                suggestionString += capitalisedAction +
                        " <FONT COLOR=" + highlightedColor + ">" + formatter.format(suggestion.getQuantity()) + "</FONT><br>" +
                        "<FONT COLOR=white>" + suggestion.getName() + "</FONT><br>" +
                        "for <FONT COLOR=" + highlightedColor + ">" + formatter.format(suggestion.getPrice()) + "</FONT> gp<br>";
                setItemIcon(suggestion.getItemId());
                break;
            default:
                suggestionString += "Error processing suggestion<br>";
        }
        suggestionString += suggestion.getMessage();
        suggestionString += "</center><html>";
        innerSuggestionMessage = "";
        if(!suggestion.getType().equals("wait")) {
            setButtonsVisible(true);
        }
        suggestionText.setText(suggestionString);
        suggestionText.setMaximumSize(new Dimension(suggestionText.getPreferredSize().width, Integer.MAX_VALUE));
        suggestionTextContainer.setVisible(true);
        suggestionTextContainer.revalidate();
        suggestionTextContainer.repaint();
    }

    public void suggestCollect() {
        setMessage("Collect items");
        setButtonsVisible(false);
    }

    public void suggestAddGp() {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        setMessage("Add " +
                "at least <FONT COLOR=" + highlightedColor + ">" + formatter.format(MIN_GP_NEEDED_TO_FLIP)
                               + "</FONT> gp<br>to your inventory<br>"
                               + "to get a flip suggestion");
        setButtonsVisible(false);
    }

    public void suggestOpenGe() {
        setMessage("Open the Grand Exchange<br>"
                + "to get a flip suggestion");
        setButtonsVisible(false);
    }

    public void setIsPausedMessage() {
        setMessage("Suggestions are paused");
        setButtonsVisible(false);
    }

    public void setMessage(String message) {
        innerSuggestionMessage = message;
        setButtonsVisible(false);
        suggestionText.setText("<html><center>" + innerSuggestionMessage +  "<br>" + serverMessage + "</center><html>");
        suggestionText.setMaximumSize(new Dimension(suggestionText.getPreferredSize().width, Integer.MAX_VALUE));
        suggestionTextContainer.revalidate();
        suggestionTextContainer.repaint();
    }

    public boolean isCollectItemsSuggested() {
        return suggestionText.isVisible() && "Collect items".equals(innerSuggestionMessage);
    }

    public void showLoading() {
        suggestionTextContainer.setVisible(false);
        setServerMessage("");
        spinner.show();
        setButtonsVisible(false);
        suggestionIcon.setVisible(false);
        suggestionText.setText("");
    }

    public void hideLoading() {
        spinner.hide();
        suggestionTextContainer.setVisible(true);
    }

    private void setButtonsVisible(boolean visible) {
        skipButton.setVisible(visible);
        blockButton.setVisible(visible);
        graphButton.setVisible(visible);
        suggestionIcon.setVisible(visible);
    }

    public void displaySuggestion() {
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null) {
            return;
        }
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        setServerMessage(suggestion.getMessage());
        boolean collectNeeded = accountStatus.isCollectNeeded(suggestion);
        if(collectNeeded && !uncollectedManager.HasUncollected(osrsLoginManager.getAccountHash())) {
            log.warn("tick {} collect is suggested but there is nothing to collect! suggestion: {} {} {}", client.getTickCount(), suggestion.getType(), suggestion.getQuantity(), suggestion.getItemId());
        }
        if (collectNeeded) {
            suggestCollect();
        } else if (suggestion.getType().equals("wait") && accountStatus.moreGpNeeded()) {
            suggestAddGp();
        } else if(suggestion.getType().equals("wait") && !grandExchange.isOpen() && accountStatus.emptySlotExists()) {
            suggestOpenGe();
        } else {
            updateSuggestion(suggestion);
        }
        highlightController.redraw();
    }

    public void refresh() {
        log.debug("refreshing suggestion panel {}", client.getGameState());
        if(!SwingUtilities.isEventDispatchThread()) {
            // we always execute this in the Swing EDT thread
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        if(isPreferencesPanelVisible) {
            preferencesPanel.refresh();
        }
        if (pausedManager.isPaused()) {
            setIsPausedMessage();
            hideLoading();
            return;
        }

        String errorMessage = osrsLoginManager.getInvalidStateDisplayMessage();
        if (errorMessage != null) {
            setMessage(errorMessage);
            hideLoading();
        }

        if(suggestionManager.isSuggestionRequestInProgress()) {
            showLoading();
            return;
        }
        hideLoading();

        final HttpResponseException suggestionError = suggestionManager.getSuggestionError();
        if(suggestionError != null) {
            highlightController.redraw();
            setMessage("Error: " + suggestionError.getMessage());
            return;
        }

        if(!client.isClientThread()) {
            clientThread.invoke(this::displaySuggestion);
        } else {
            displaySuggestion();
        }
    }

    private Data getPriceData() {
        String lowsFile = "dragon_bones_lows.csv";
        String highsFile = "dragon_bones_highs.csv";

        Data data = new Data();
        data.name = "Dragon Bones";

        try {
            // Load low prices
            int[][] lowData = loadPriceDataFromCSV(lowsFile);
            data.lowLatestTimes = lowData[0];
            data.lowLatestPrices = lowData[1];

            // Load high prices
            int[][] highData = loadPriceDataFromCSV(highsFile);
            data.highLatestTimes = highData[0];
            data.highLatestPrices = highData[1];

            // Generate prediction data based on historical data
            int[] predictionTimes = new int[7];
            int[] predictionLowMeans = new int[7];
            int[] predictionLowIQRLower = new int[7];
            int[] predictionLowIQRUpper = new int[7];
            int[] predictionHighMeans = new int[7];
            int[] predictionHighIQRLower = new int[7];
            int[] predictionHighIQRUpper = new int[7];

            int lastLowPrice = data.lowLatestPrices[data.lowLatestPrices.length-1];
            int lastHighPrice = data.highLatestPrices[data.highLatestPrices.length-1];
            int now  = Math.max(data.highLatestTimes[data.highLatestTimes.length-1], data.lowLatestTimes[data.lowLatestTimes.length-1]);
            for (int i = 0; i < 7; i++) {
                predictionTimes[i] = now + i * 4 * 3600;
                predictionLowMeans[i] = lastLowPrice + (int) (Math.random() * 100) - 50;
                int iqrSize = 20 + i * 10; // Uncertainty grows over time
                predictionLowIQRLower[i] = predictionLowMeans[i] - iqrSize;
                predictionLowIQRUpper[i] = predictionLowMeans[i] + iqrSize;
                predictionHighMeans[i] = lastHighPrice + (int) (Math.random() * 100) - 50;
                predictionHighIQRLower[i] = predictionHighMeans[i] - iqrSize;
                predictionHighIQRUpper[i] = predictionHighMeans[i] + iqrSize;
            }

            data.predictionTimes = predictionTimes;
            data.predictionLowMeans = predictionLowMeans;
            data.predictionLowIQRLower = predictionLowIQRLower;
            data.predictionLowIQRUpper = predictionLowIQRUpper;
            data.predictionHighMeans = predictionHighMeans;
            data.predictionHighIQRLower = predictionHighIQRLower;
            data.predictionHighIQRUpper = predictionHighIQRUpper;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load price data from CSV files", e);
        }

        return data;
    }

    /**
     * Loads time and price data from a CSV file in the resources folder.
     * @param filename The name of the CSV file to load
     * @return A 2D array where the first inner array contains times and the second contains prices
     * @throws IOException If the file cannot be read
     */
    private int[][] loadPriceDataFromCSV(String filename) throws IOException {
        List<Integer> times = new ArrayList<Integer>();
        List<Integer> prices = new ArrayList<Integer>();

        // Get file from resources folder
        InputStream is = getClass().getClassLoader().getResourceAsStream(filename);
        if (is == null) {
            throw new FileNotFoundException("Resource not found: " + filename);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    try {
                        int time = Integer.parseInt(parts[0].trim());
                        int price = Integer.parseInt(parts[1].trim());
                        times.add(time);
                        prices.add(price);
                    } catch (NumberFormatException e) {
                        // Skip malformed rows
                        continue;
                    }
                }
            }
        }

        // Convert Lists to arrays
        int[][] result = new int[2][];
        result[0] = times.stream().mapToInt(Integer::intValue).toArray();
        result[1] = prices.stream().mapToInt(Integer::intValue).toArray();

        return result;
    }
}
