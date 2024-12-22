package com.flippingcopilot.ui;

import com.flippingcopilot.controller.*;
import com.flippingcopilot.model.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;


import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class StatsPanelV2 extends JPanel {


    public final BufferedImage TRASH_ICON = ImageUtil.loadImageResource(getClass(), "/trash.png");
    public final BufferedImage ARROW_ICON = ImageUtil.loadImageResource(getClass(),"/small_open_arrow.png");
    public final Icon OPEN_ICON = new ImageIcon(ARROW_ICON);
    public final Icon CLOSE_ICON = new ImageIcon(ImageUtil.rotateImage(ARROW_ICON, Math.toRadians(90)));

    private static final String ALL_ACCOUNTS_DROPDOWN_OPTION = "All accounts";
    private static final java.util.List<Integer> SESSION_STATS_INDS = Arrays.asList(3,4,5);
    private static final String[] TIME_INTERVAL_STRINGS = {
            "-1h (Past Hour)",
            "-4h (Past 4 Hours)",
            "-12h (Past 12 Hours)",
            "-1d (Past Day)",
            "-1w (Past Week)",
            "-1m (Past Month)",
            "Session",
            "All"};

    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^-?(\\d+)([hdwmy])[()\\w\\s]*");

    // dependencies
    private final LoginResponseManager loginResponseManager;
    private final OsrsLoginManager osrsLoginManager;
    private final FlippingCopilotConfig config;
    private final FlipManager flipManager;
    private final SessionManager sessionManager;
    private final WebHookController webHookController;
    private final ClientThread clientThread;

    // state
    private JComboBox<String> timeIntervalDropdown;
    private final DefaultComboBoxModel<String> rsAccountDropdownModel = new DefaultComboBoxModel<>();
    private final JComboBox<String> rsAccountDropdown = new JComboBox<>(rsAccountDropdownModel);
    private final JButton sessionResetButton = new JButton("  reset  ");
    private JPanel profitAndSubInfoPanel;
    private JPanel subInfoPanel;
    private final JPanel flipsPanel = new JPanel();
    private final JLabel totalProfitVal = new JLabel("0 gp");
    private final JLabel roiVal = new JLabel("-0.00%");
    private final JLabel flipsMadeVal = new JLabel("0");
    private final JLabel taxPaidVal = new JLabel("0 gp");
    private final JLabel sessionTimeVal = new JLabel("00:00:00");
    private final JLabel hourlyProfitVal = new JLabel("0 gp/hr");
    private final JLabel avgCashVal = new JLabel("0 gp");
    private final Paginator paginator;

    private IntervalTimeUnit selectedIntervalTimeUnit = IntervalTimeUnit.SESSION;
    private int selectedIntervalValue = -1;
    private volatile boolean lastValidState = false;

    @Inject
    public StatsPanelV2(LoginResponseManager loginResponseManager,
                        OsrsLoginManager osrsLoginManager,
                        FlippingCopilotConfig config,
                        FlipManager FlipManager,
                        SessionManager sessionManager,
                        WebHookController webHookController,
                        ClientThread clientThread) {
        this.loginResponseManager = loginResponseManager;
        this.osrsLoginManager = osrsLoginManager;
        this.sessionManager = sessionManager;
        this.webHookController = webHookController;
        this.config = config;
        this.flipManager = FlipManager;
        this.clientThread = clientThread;
        setLayout(new BorderLayout());

        setupTimeIntervalDropdown();
        setupProfitAndSubInfoPanel();
        setupSessionResetButton();

        flipsPanel.setLayout(new BoxLayout(flipsPanel, BoxLayout.Y_AXIS));
        flipsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        flipsPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JScrollPane scrollPane = new JScrollPane(flipsPanel);
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(2, 0));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Create a main panel with vertical layout
        JPanel mainPanel = UIUtilities.newVerticalBoxLayoutJPanel();
        mainPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel timeIntervalDropdownWrapper = new JPanel(new BorderLayout(0, 0));
        timeIntervalDropdownWrapper.setBorder(BorderFactory.createEmptyBorder()); // No border
        timeIntervalDropdownWrapper.add(timeIntervalDropdown, BorderLayout.CENTER);
        timeIntervalDropdownWrapper.add(sessionResetButton, BorderLayout.EAST);
        timeIntervalDropdownWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, timeIntervalDropdownWrapper.getPreferredSize().height));

        JPanel intervalRsAccountDropdownWrapper = new JPanel(new BorderLayout(0, 0));
        rsAccountDropdown.setBorder(BorderFactory.createEmptyBorder());
        rsAccountDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, rsAccountDropdown.getPreferredSize().height));
        intervalRsAccountDropdownWrapper.add(timeIntervalDropdownWrapper, BorderLayout.NORTH);
        intervalRsAccountDropdownWrapper.add(rsAccountDropdown, BorderLayout.SOUTH);
        rsAccountDropdown.addActionListener(e -> {
            String value = (String) rsAccountDropdown.getSelectedItem();
            log.info("selected rs account is: {}", value);
            if(value != null) {
                if (ALL_ACCOUNTS_DROPDOWN_OPTION.equals(value)) {
                    flipManager.setIntervalDisplayName(null);
                } else {
                    flipManager.setIntervalDisplayName(value);
                }
            }
        });
        intervalRsAccountDropdownWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, intervalRsAccountDropdownWrapper.getPreferredSize().height));

        mainPanel.add(intervalRsAccountDropdownWrapper);
        mainPanel.add(profitAndSubInfoPanel);
        mainPanel.add(scrollPane);

        add(mainPanel, BorderLayout.CENTER);

        paginator = new Paginator(() -> refresh(true, lastValidState));
        add(paginator, BorderLayout.SOUTH);

        flipManager.setFlipsChangedCallback(() -> refresh(true, loginResponseManager.isLoggedIn() && osrsLoginManager.isValidLoginState()));
    }

    private void setupSessionResetButton() {
        sessionResetButton.setBorder(BorderFactory.createEmptyBorder());
        sessionResetButton.addActionListener((l) -> {
            JLabel resetIcon = new JLabel(new ImageIcon(TRASH_ICON));
            final int result = JOptionPane.showOptionDialog(resetIcon, "<html>Are you sure you want to reset the session?</html>",
                    "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, new String[]{"Yes", "No"}, "No");
            if (result == JOptionPane.YES_OPTION) {
                // send discord message before resetting session stats
                clientThread.invoke(() -> {
                    if (osrsLoginManager.isValidLoginState()) {
                        String displayName = osrsLoginManager.getPlayerDisplayName();
                        webHookController.sendMessage(flipManager.calculateStats(sessionManager.getCachedSessionData().startTime, displayName), sessionManager.getCachedSessionData(), displayName, true);
                        sessionManager.resetSession();
                        if (IntervalTimeUnit.SESSION.equals(selectedIntervalTimeUnit)) {
                            flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
                        }
                        refresh(true, loginResponseManager.isLoggedIn() && osrsLoginManager.isValidLoginState());
                    }
                });
            }
        });
    }

    public void resetIntervalDropdownToSession() {
        timeIntervalDropdown.setSelectedItem("Session");
        selectedIntervalTimeUnit = IntervalTimeUnit.SESSION;
        selectedIntervalValue = -1;
    }

    private boolean extractAndUpdateTimeInterval(String value) {
        if (value != null) {
            if ("Session".equals(value)) {
                selectedIntervalTimeUnit = IntervalTimeUnit.SESSION;
                selectedIntervalValue = -1;
                return true;
            } else if ("All".equals(value)) {
                selectedIntervalTimeUnit = IntervalTimeUnit.ALL;
                selectedIntervalValue = -1;
                return true;
            } else {
                Matcher matcher = INTERVAL_PATTERN.matcher(value);
                if (matcher.matches()) {
                    selectedIntervalValue = Integer.parseInt(matcher.group(1));
                    selectedIntervalTimeUnit = IntervalTimeUnit.fromString(matcher.group(2));
                    return true;
                }
            }
        }
        return false;
    }


    private void setupTimeIntervalDropdown() {
        timeIntervalDropdown = new JComboBox<>(TIME_INTERVAL_STRINGS);
        timeIntervalDropdown.setEditable(true);
        timeIntervalDropdown.setSelectedItem("Session");
        timeIntervalDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, timeIntervalDropdown.getPreferredSize().height));
        timeIntervalDropdown.setBorder(BorderFactory.createEmptyBorder());

        // Create a custom editor to handle both selection and manual input
        ComboBoxEditor editor = new BasicComboBoxEditor() {
            @Override
            public void setItem(Object item) {
                super.setItem(item);
                if (item != null) {
                    String value = item.toString();
                    if (extractAndUpdateTimeInterval(value)) {
                        updateFlipManagerAndStats();
                    }
                }
            }
        };
        timeIntervalDropdown.setEditor(editor);

        // Add action listener for selection changes and manual edits
        timeIntervalDropdown.addActionListener(e -> {
            String value = (String) timeIntervalDropdown.getSelectedItem();
            if (extractAndUpdateTimeInterval(value)) {
                updateFlipManagerAndStats();
            }
        });
    }

    private void updateFlipManagerAndStats() {
        log.debug("selection interval value updated to {} {}", selectedIntervalValue, selectedIntervalTimeUnit);
        switch (selectedIntervalTimeUnit) {
            case ALL:
                flipManager.setIntervalStartTime(1);
                break;
            case SESSION:
                flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
                break;
            default:
                int startTime = (int) Instant.now().getEpochSecond() - selectedIntervalValue * selectedIntervalTimeUnit.getSeconds();
                flipManager.setIntervalStartTime(startTime);
        }
    }

    private JPanel buildSubInfoPanelItem(String key, JLabel value, Color valueColor) {
        JPanel item = new JPanel(new BorderLayout());
        item.setBorder(new EmptyBorder(4, 2, 4, 2));
        item.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        JLabel keyLabel = new JLabel(key);
        keyLabel.setFont(FontManager.getRunescapeSmallFont());
        item.add(keyLabel, BorderLayout.WEST);
        value.setFont(FontManager.getRunescapeSmallFont());
        value.setForeground(valueColor);
        item.add(value, BorderLayout.EAST);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        return item;
    }

    private JPanel buildSubInfoPanel() {
        JPanel subInfoPanel = UIUtilities.newVerticalBoxLayoutJPanel();
        subInfoPanel.add(buildSubInfoPanelItem("ROI:", roiVal, UIUtilities.TOMATO));
        subInfoPanel.add(buildSubInfoPanelItem("Flips made:", flipsMadeVal, ColorScheme.LIGHT_GRAY_COLOR));
        subInfoPanel.add(buildSubInfoPanelItem("Tax paid:", taxPaidVal, ColorScheme.LIGHT_GRAY_COLOR));
        subInfoPanel.add(buildSubInfoPanelItem("Session time:", sessionTimeVal, ColorScheme.GRAND_EXCHANGE_ALCH));
        subInfoPanel.add(buildSubInfoPanelItem("Hourly profit:", hourlyProfitVal, Color.WHITE));
        subInfoPanel.add(buildSubInfoPanelItem("Avg wealth:", avgCashVal, ColorScheme.LIGHT_GRAY_COLOR));
        subInfoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        subInfoPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(2, 5, 5, 5)));
       return subInfoPanel;
    }

    private void setupProfitAndSubInfoPanel() {
        profitAndSubInfoPanel = UIUtilities.newVerticalBoxLayoutJPanel();
        profitAndSubInfoPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Create the header panel that can be clicked to expand/collapse sub info
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1,0,1,0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(5, 0, 5, 0)));

        final JLabel profitTitle = new JLabel("Profit: ");
        profitTitle.setFont(FontManager.getRunescapeBoldFont());

        totalProfitVal.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
        totalProfitVal.setFont(FontManager.getRunescapeBoldFont().deriveFont(24f));
        totalProfitVal.setHorizontalAlignment(SwingConstants.CENTER);

        // Use a panel to stack the profitTitle and totalProfitVal vertically
        JPanel profitTextPanel = new JPanel();
        profitTextPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        profitTextPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        profitTextPanel.add(profitTitle);
        profitTextPanel.add(Box.createRigidArea(new Dimension(5, 0))); // Spacing between title and value
        profitTextPanel.add(totalProfitVal);
        profitTextPanel.setBorder(BorderFactory.createEmptyBorder(2,4,2,4));

        // Arrow label
        JLabel arrowLabel = new JLabel(OPEN_ICON);
        arrowLabel.setHorizontalAlignment(SwingConstants.CENTER);
        arrowLabel.setVerticalAlignment(SwingConstants.CENTER);
        arrowLabel.setPreferredSize(new Dimension(16, 16)); // Adjust size as needed

        // Add components to headerPanel
        headerPanel.add(profitTextPanel, BorderLayout.CENTER);
        headerPanel.add(arrowLabel, BorderLayout.EAST);

        // Create the sub-info panel
        subInfoPanel = buildSubInfoPanel();

        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        profitAndSubInfoPanel.add(headerPanel);
        profitAndSubInfoPanel.add(subInfoPanel);

        // Mouse listener to handle expand/collapse and hover effects
        MouseAdapter headerMouseListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean isExpanded = subInfoPanel.isVisible();
                if(!isExpanded && rsAccountDropdownModel.getSize() > 2) {
                    rsAccountDropdown.setVisible(true);
                } else if (isExpanded && rsAccountDropdownModel.getSize() > 2) {
                    rsAccountDropdown.setVisible(false);
                }
                subInfoPanel.setVisible(!isExpanded);
                arrowLabel.setIcon(isExpanded ? OPEN_ICON : CLOSE_ICON);
                log.debug("profit and sub info panel clicked");
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
                profitTextPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
                headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                profitTextPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                headerPanel.setCursor(Cursor.getDefaultCursor());
            }
        };

        // Add mouse listener to header components
        headerPanel.addMouseListener(headerMouseListener);
        totalProfitVal.addMouseListener(headerMouseListener);
        profitTitle.addMouseListener(headerMouseListener);

    }

    // called when:
    //
    // - time interval drop down changed (Swing EDT thread)
    // - session reset button pressed (Swing EDT thread)
    // - transaction processing downstream (ScheduledExecutorService)
    // - FlipTrackerV2 initialisation (ScheduledExecutorService)
    // - session stats updated (ScheduledExecutorService)
    // - plugin config changed (Client thread)
    // - page changed (Swing EDT thread)
    //

    public void refresh(boolean flipsMaybeChanged, boolean validLoginState) {
        if(!SwingUtilities.isEventDispatchThread()) {
            // we always execute this in the Swing EDT thread
            SwingUtilities.invokeLater(() -> refresh(flipsMaybeChanged, validLoginState));
            return;
        }
        lastValidState = validLoginState;
        if (!validLoginState) {
            totalProfitVal.setText("0 gp");
            roiVal.setText("-0.00%");
            flipsMadeVal.setText("0");
            taxPaidVal.setText("0 gp");
            sessionTimeVal.setText("00:00:00");
            hourlyProfitVal.setText("0 gp/hr");
            avgCashVal.setText("0 gp");
            flipsPanel.removeAll();
            paginator.setTotalPages(1);
            boolean v = IntervalTimeUnit.SESSION.equals(selectedIntervalTimeUnit);
            SESSION_STATS_INDS.forEach(i -> subInfoPanel.getComponent(i).setVisible(v));
            rsAccountDropdownModel.removeAllElements();
            rsAccountDropdown.setVisible(false);
            return;
        }

        java.util.List<String> displayNameOptions = flipManager.getDisplayNameOptions();
        String selectedDisplayName = flipManager.getIntervalDisplayName();
        if (displayNameOptionsOutOfDate(displayNameOptions) || selectedDisplayNameOutOfDate(selectedDisplayName)) {
            rsAccountDropdownModel.removeAllElements();
            rsAccountDropdownModel.addAll(displayNameOptions);
            rsAccountDropdownModel.addElement(ALL_ACCOUNTS_DROPDOWN_OPTION);
            if (selectedDisplayName == null) {
                rsAccountDropdown.setSelectedItem(rsAccountDropdownModel.getElementAt(rsAccountDropdownModel.getSize()-1));
            } else {
                rsAccountDropdown.setSelectedItem(selectedDisplayName);
            }
            if(rsAccountDropdownModel.getSize() > 2) {
                rsAccountDropdown.setVisible(true);
            }
        }

        SessionData sd = sessionManager.getCachedSessionData();
        Stats stats = flipManager.getIntervalStats();
        paginator.setTotalPages(1 + stats.flipsMade / 50);
        long s = System.nanoTime();
        if (flipsMaybeChanged) {
            flipsPanel.removeAll();
            flipManager.getPageFlips(paginator.getPageNumber(), 50).forEach(f -> flipsPanel.add(new FlipPanel(f, config)));
            // labels displayed to the user
            roiVal.setText(String.format("%.3f%%", stats.calculateRoi() * 100));
            roiVal.setForeground(UIUtilities.getProfitColor(stats.profit, config));
            flipsMadeVal.setText(String.format("%d", stats.flipsMade));
            taxPaidVal.setText(UIUtilities.formatProfit(stats.taxPaid));
            totalProfitVal.setText(UIUtilities.formatProfit(stats.profit));
            totalProfitVal.setForeground(UIUtilities.getProfitColor(stats.profit, config));
            log.debug("populating flips took {}ms", (System.nanoTime() - s) / 1000_000);
        }

        // 'Session time', 'Hourly profit' and 'Avg wealth' should only be set if 'Session' is select in the dropdown
        if (IntervalTimeUnit.SESSION.equals(selectedIntervalTimeUnit)) {
            SESSION_STATS_INDS.forEach(i -> subInfoPanel.getComponent(i).setVisible(true));
            long seconds = sd.durationMillis / 1000;
            float hoursFloat = (((float) seconds) / 3600.0f);
            long hourlyProfit = hoursFloat == 0 ? 0 : (long) (stats.profit / hoursFloat);
            String sessionTime = String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
            sessionTimeVal.setText(sessionTime);
            hourlyProfitVal.setText(UIUtilities.formatProfitWithoutGp(hourlyProfit) + " gp/hr");
            hourlyProfitVal.setForeground(UIUtilities.getProfitColor(hourlyProfit, config));
            avgCashVal.setText(UIUtilities.quantityToRSDecimalStack(Math.abs(sd.averageCash), false) + " gp");
        } else {
            SESSION_STATS_INDS.forEach(i -> subInfoPanel.getComponent(i).setVisible(false));
        }
    }

    private boolean selectedDisplayNameOutOfDate(String selectedDisplayName) {
        String oldSelectedDisplayName = (String) rsAccountDropdown.getSelectedItem();
        if (ALL_ACCOUNTS_DROPDOWN_OPTION.equals(oldSelectedDisplayName) && selectedDisplayName == null) {
            return false;
        }
        return !Objects.equals(oldSelectedDisplayName, selectedDisplayName);
    }

    private boolean displayNameOptionsOutOfDate(List<String> displayNameOptions) {
        if (displayNameOptions.size() + 1 != rsAccountDropdownModel.getSize()) {
            return true;
        }
        for(int i = 0; i< displayNameOptions.size(); i++) {
            if (!displayNameOptions.get(i).equals(rsAccountDropdownModel.getElementAt(i))) {
                return true;
            }
        }
        return false;
    }
}