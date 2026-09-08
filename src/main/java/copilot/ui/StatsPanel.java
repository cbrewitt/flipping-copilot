package copilot.ui;
import static net.runelite.client.util.ImageUtil.*;
import static javax.swing.JOptionPane.*;
import static java.awt.BorderLayout.*;

import java.awt.event.*;
import copilot.ui.components.*;
import copilot.rs.*;
import static copilot.ui.UIUtilities.*;
import static javax.swing.BorderFactory.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.controller.*;
import copilot.model.*;
import copilot.ui.flipsdialog.*;
import lombok.extern.slf4j.*;
import net.runelite.client.callback.*;
import net.runelite.client.ui.*;
import net.runelite.client.util.*;

import javax.inject.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.image.*;

@Slf4j
@Singleton
public class StatsPanel extends JPanel {
    private static final int SUB_INFO_ROW_VERTICAL_PADDING = 3, SUB_INFO_ROW_HEIGHT = 18;

    public final BufferedImage ARROW_ICON = loadImageResource(getClass(),"/small_open_arrow.png");
    public final Icon OPEN_ICON = new ImageIcon(ARROW_ICON);
    public final Icon CLOSE_ICON = new ImageIcon(rotateImage(ARROW_ICON, Math.toRadians(90)));
    public final BufferedImage FLIPS_DIALOG_ICON = recolorImage(resizeImage(loadImageResource(getClass(),"/popout-flips.png"), 20, 20),LIGHT_GRAY_COLOR);
    public final Icon FLIPS_DIALOG = new ImageIcon(FLIPS_DIALOG_ICON);
    public final Icon HIGHLIGHTED_FLIPS_DIALOG = new ImageIcon(luminanceScale(FLIPS_DIALOG_ICON, BUTTON_HOVER_LUMINANCE));

    private static final int SESSION_TIME_ROW = 4, HOURLY_PROFIT_ROW = 5;

    // dependencies
    private final CopilotLogin copilotLogin;
    private final PlayerLogin login;
    private final CopilotConfig config;
    private final FlipManager flipManager;
    private final SessionManager sessionManager;
    private final WebHookController webHookController;
    private final ClientThread clientThread;
    private final FlipsDialogController dialogs;
    private final PortfolioStateRS portfolioStateRS;

    // state
    private IntervalDropdown intervalDropdown;
    private final AccountDropdown accountDropdown;
    private final JButton sessionResetButton = new JButton("  Reset session ");
    private JPanel profitAndSubInfoPanel, subInfoPanel;
    private final JPanel flipsPanel = new JPanel();
    private final JLabel totalProfitVal = new JLabel("0 gp"), roiVal = new JLabel("-0.00%");
    private final JLabel flipsMadeVal = new JLabel("0"), unrealizedProfitVal = new JLabel("0 gp");
    private final JLabel sessionTimeVal = new JLabel("00:00:00"), hourlyProfitVal = new JLabel("0 gp/hr");
    private final JLabel portfolioValueVal = new JLabel("0 gp");
    private final Paginator paginator;
    private final JButton flipsDialogButton = new JButton();

    private volatile boolean lastValidState = false;

    // Modified constructor
    @Inject
    public StatsPanel(CopilotLogin copilotLogin,
                        PlayerLogin login,
                        CopilotConfig config,
                        FlipManager FlipManager,
                        SessionManager sessionManager,
                        WebHookController webHookController,
                        ClientThread clientThread,
                        FlipsDialogController dialogs,
                        PortfolioStateRS portfolioStateRS) {
        this.copilotLogin = copilotLogin; this.login = login; this.sessionManager = sessionManager;
        this.webHookController = webHookController; this.config = config; flipManager = FlipManager;
        this.clientThread = clientThread; this.dialogs = dialogs; this.portfolioStateRS = portfolioStateRS;
        setLayout(new BorderLayout());

        setupTimeIntervalDropdown();
        setupProfitAndSubInfoPanel();
        setupSessionResetButton();
        setupFlipsDialogButton();

        flipsPanel.setLayout(new BoxLayout(flipsPanel, BoxLayout.Y_AXIS)); flipsPanel.setBackground(DARKER_GRAY_COLOR);
        flipsPanel.setBorder(createEmptyBorder(4, 4, 4, 4));

        var scrollPane = new JScrollPane(flipsPanel);
        scrollPane.setBackground(DARKER_GRAY_COLOR);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(2, 0));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Create a main panel with vertical layout
        var mainPanel = verticalPanel(DARKER_GRAY_COLOR);

        var timeIntervalDropdownWrapper = new JPanel(new BorderLayout(0, 0));
        timeIntervalDropdownWrapper.setBorder(createEmptyBorder()); // No border
        timeIntervalDropdownWrapper.add(intervalDropdown, CENTER);
        timeIntervalDropdownWrapper.add(sessionResetButton, EAST);
        timeIntervalDropdownWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, timeIntervalDropdownWrapper.getPreferredSize().height));

        var intervalRsAccountDropdownWrapper = new JPanel(new BorderLayout(0, 0));
        accountDropdown = new AccountDropdown(
                () -> copilotLogin.get().displayNameToAccountId,
                flipManager::setIntervalAccount,
                AccountDropdown.ALL_ACCOUNTS_DROPDOWN_OPTION
        );
        intervalRsAccountDropdownWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, intervalRsAccountDropdownWrapper.getPreferredSize().height));
        intervalRsAccountDropdownWrapper.add(timeIntervalDropdownWrapper, NORTH);
        intervalRsAccountDropdownWrapper.add(accountDropdown, SOUTH);

        mainPanel.add(intervalRsAccountDropdownWrapper);
        mainPanel.add(profitAndSubInfoPanel);
        mainPanel.add(scrollPane);

        add(mainPanel, CENTER);

        paginator = new Paginator((i) -> refresh(true, lastValidState));

        var bottomPanel = darkPanel(new BorderLayout(), DARKER_GRAY_COLOR);
        bottomPanel.add(paginator, CENTER);
        bottomPanel.add(flipsDialogButton, EAST);

        add(bottomPanel, SOUTH);

        flipManager.setFlipsChangedCallback(() -> refresh(true, copilotLogin.get().isLoggedIn() && login.isValidLoginState()));
    }

    private void setupFlipsDialogButton() {
        flipsDialogButton.setIcon(FLIPS_DIALOG); flipsDialogButton.setOpaque(true); flipsDialogButton.setEnabled(true);
        flipsDialogButton.setFocusable(true); flipsDialogButton.setBorder(createEmptyBorder(0,0,0,5));
        flipsDialogButton.setBackground(DARKER_GRAY_COLOR); flipsDialogButton.setToolTipText("Open flips dialog");

        addMouseActions(dialogs::showPortfolioTab, null, flipsDialogButton);
        addHoverIcons(flipsDialogButton, () -> FLIPS_DIALOG, () -> HIGHLIGHTED_FLIPS_DIALOG);
    }

    private void setupSessionResetButton() {
        sessionResetButton.setBorder(createEmptyBorder());
        sessionResetButton.addActionListener((l) -> {
            final int result = showOptionDialog(SwingUtilities.getWindowAncestor(this), "<html>Are you sure you want to reset the session?</html>",
                    "Are you sure?", YES_NO_OPTION, WARNING_MESSAGE,
                    null, new String[]{"Yes", "No"}, "No");
            if (result == YES_OPTION) {
                // send discord message before resetting session stats
                clientThread.invoke(() -> {
                    if (login.isValidLoginState()) {
                        String displayName = login.getPlayerDisplayName();
                        Integer accountId = copilotLogin.get().getAccountId(displayName);
                        if(accountId != null && accountId != -1) {
                            webHookController.sendMessage(flipManager.calculateStats(sessionManager.getCachedSessionData().startTime, accountId), sessionManager.getCachedSessionData(), displayName, true);
                            sessionManager.resetSession();
                            if (IntervalTimeUnit.SESSION.equals(intervalDropdown.getSelectedIntervalTimeUnit())) {
                                flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
                            }
                        }
                        refresh(true, copilotLogin.get().isLoggedIn() && login.isValidLoginState());
                    }
                });
            }
        });
    }

    private void setupTimeIntervalDropdown() {
        intervalDropdown = new IntervalDropdown((intervalTimeUnit, intervalValue) -> {
            long startTime = IntervalDropdown.calculateStartTime(intervalTimeUnit, intervalValue, sessionManager.getCachedSessionData().startTime);
            flipManager.setIntervalStartTime((int) startTime);
        }, IntervalDropdown.ALL_TIME, true);
    }

    public void resetIntervalDropdownToSession() { intervalDropdown.resetToSession(); }

    private JPanel buildSubInfoPanelItem(String key, JLabel value, Color valueColor) {
        return buildSubInfoPanelItem(key, value, valueColor, null);
    }

    private JPanel buildSubInfoPanelItem(String key, JLabel value, Color valueColor, Runnable onClick) {
        var item = new JPanel(new BorderLayout());
        item.setBorder(new EmptyBorder(SUB_INFO_ROW_VERTICAL_PADDING, 2, SUB_INFO_ROW_VERTICAL_PADDING, 2));
        item.setBackground(DARKER_GRAY_COLOR);
        var keyLabel = new JLabel(key);
        keyLabel.setFont(FontManager.getRunescapeSmallFont());
        item.add(keyLabel, WEST);
        value.setFont(FontManager.getRunescapeSmallFont()); value.setForeground(valueColor);
        item.add(value, EAST);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, SUB_INFO_ROW_HEIGHT));

        if (onClick != null) {
            item.setToolTipText("Open portfolio");
            keyLabel.setToolTipText("Open portfolio");
            value.setToolTipText("Open portfolio");

            addMouseActions(onClick, hovered -> {
                item.setBackground(hovered ? DARKER_GRAY_COLOR.brighter() : DARKER_GRAY_COLOR);
                item.setCursor(hovered ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }, item, keyLabel, value);
        }

        return item;
    }

    private JPanel buildSubInfoPanel() {
        var subInfoPanel = verticalPanel(DARKER_GRAY_COLOR);
        subInfoPanel.add(buildSubInfoPanelItem("Unrealized profit:", unrealizedProfitVal, LIGHT_GRAY_COLOR, dialogs::showPortfolioTab));
        subInfoPanel.add(buildSubInfoPanelItem("Flips made:", flipsMadeVal, LIGHT_GRAY_COLOR));
        subInfoPanel.add(buildSubInfoPanelItem("ROI:", roiVal, TOMATO));
        subInfoPanel.add(buildSubInfoPanelItem("Session time:", sessionTimeVal, GRAND_EXCHANGE_ALCH));
        subInfoPanel.add(buildSubInfoPanelItem("Hourly profit:", hourlyProfitVal, Color.WHITE));
        subInfoPanel.add(buildSubInfoPanelItem("Portfolio value:", portfolioValueVal, LIGHT_GRAY_COLOR, dialogs::showPortfolioTab));
        subInfoPanel.setBorder(createCompoundBorder(createMatteBorder(0,0,1,0, DARK_GRAY_COLOR),
                new EmptyBorder(2, 5, 5, 5)));
        return subInfoPanel;
    }

    private void setupProfitAndSubInfoPanel() {
        profitAndSubInfoPanel = verticalPanel(DARK_GRAY_COLOR);

        // Create the header panel that can be clicked to expand/collapse sub info
        var headerPanel = darkPanel(new BorderLayout(), DARKER_GRAY_COLOR);
        headerPanel.setBorder(createCompoundBorder(
                createMatteBorder(1,0,1,0, DARK_GRAY_COLOR),
                new EmptyBorder(4, 0, 4, 0)));

        final var profitTitle = new JLabel("Profit: ");
        profitTitle.setFont(FontManager.getRunescapeBoldFont());

        totalProfitVal.setForeground(GRAND_EXCHANGE_PRICE);
        totalProfitVal.setFont(FontManager.getRunescapeBoldFont().deriveFont(24f));
        totalProfitVal.setHorizontalAlignment(SwingConstants.CENTER);

        // Use a panel to stack the profitTitle and totalProfitVal vertically
        var profitTextPanel = new JPanel();
        profitTextPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        profitTextPanel.setBackground(DARKER_GRAY_COLOR);
        profitTextPanel.add(profitTitle);
        addHorizontalGap(profitTextPanel, 5); // Spacing between title and value
        profitTextPanel.add(totalProfitVal);
        profitTextPanel.setBorder(createEmptyBorder(1,4,1,4));

        // Arrow label
        var arrowLabel = new JLabel(OPEN_ICON);
        arrowLabel.setHorizontalAlignment(SwingConstants.CENTER);
        arrowLabel.setVerticalAlignment(SwingConstants.CENTER);
        arrowLabel.setPreferredSize(new Dimension(16, 16)); // Adjust size as needed

        // Add components to headerPanel
        headerPanel.add(profitTextPanel, CENTER);
        headerPanel.add(arrowLabel, EAST);

        // Create the sub-info panel
        subInfoPanel = buildSubInfoPanel();

        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        profitAndSubInfoPanel.add(headerPanel);
        profitAndSubInfoPanel.add(subInfoPanel);

        // Mouse listener to handle expand/collapse and hover effects
        addMouseActions(() -> {
            boolean isExpanded = subInfoPanel.isVisible();
            subInfoPanel.setVisible(!isExpanded);
            arrowLabel.setIcon(isExpanded ? OPEN_ICON : CLOSE_ICON);
        }, hovered -> {
            headerPanel.setBackground(hovered ? DARKER_GRAY_COLOR.brighter() : DARKER_GRAY_COLOR);
            profitTextPanel.setBackground(hovered ? DARKER_GRAY_COLOR.brighter() : DARKER_GRAY_COLOR);
            headerPanel.setCursor(hovered ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }, headerPanel, totalProfitVal, profitTitle);

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
        if (!ensureEdt(() -> refresh(flipsMaybeChanged, validLoginState))) return;
        lastValidState = validLoginState;
        if (!validLoginState) {
            totalProfitVal.setText("0 gp");
            roiVal.setText("-0.00%");
            flipsMadeVal.setText("0");
            unrealizedProfitVal.setText("0 gp");
            sessionTimeVal.setText("00:00:00");
            hourlyProfitVal.setText("0 gp/hr");
            portfolioValueVal.setText("0 gp");
            flipsPanel.removeAll();
            paginator.setTotalPages(1);
            setSessionStatsVisible(IntervalTimeUnit.SESSION.equals(intervalDropdown.getSelectedIntervalTimeUnit()));
            accountDropdown.setVisible(false);
            return;
        }

        accountDropdown.setSelectedAccountId(flipManager.getIntervalAccount()); accountDropdown.setVisible(true);
        accountDropdown.refresh();

        var sd = sessionManager.getCachedSessionData();
        Stats stats = flipManager.getIntervalStats();
        paginator.setTotalPages(1 + stats.flipsMade / 50);
        long s = System.nanoTime();
        if (flipsMaybeChanged) {
            flipsPanel.removeAll();
            flipManager.getPageFlips(paginator.getPageNumber(), 50)
                    .forEach(f -> flipsPanel.add(new FlipPanel(f, config, () -> dialogs.showVisualizeFlip(f))));
            // labels displayed to the user
            roiVal.setText(String.format("%.3f%%", stats.calculateRoi() * 100));
            roiVal.setForeground(getProfitColor(stats.profit, config));
            flipsMadeVal.setText(String.format("%d", stats.flipsMade));
            totalProfitVal.setText(formatProfit(stats.profit));
            totalProfitVal.setForeground(getProfitColor(stats.profit, config));
            log.debug("populating flips took {}ms", (System.nanoTime() - s) / 1000_000);
        }

        var summaryData = portfolioStateRS.get().summaryData;
        long portfolioValue = summaryData.portfolioMarketValue;
        portfolioValueVal.setText(quantityToRSDecimalStack(Math.abs(portfolioValue), true) + " gp");
        long unrealizedProfit = summaryData.unrealizedProfit;
        unrealizedProfitVal.setText(formatProfit(unrealizedProfit));
        unrealizedProfitVal.setForeground(getProfitColor(unrealizedProfit, config));

        // 'Session time' and 'Hourly profit' should only be set if 'Session' is select in the dropdown
        if (IntervalTimeUnit.SESSION.equals(intervalDropdown.getSelectedIntervalTimeUnit())) {
            setSessionStatsVisible(true);
            long seconds = sd.durationMillis / 1000;
            float hoursFloat = (((float) seconds) / 3600.0f);
            long hourlyProfit = hoursFloat == 0 ? 0 : (long) (stats.profit / hoursFloat);
            String sessionTime = String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
            sessionTimeVal.setText(sessionTime);
            hourlyProfitVal.setText(formatProfitWithoutGp(hourlyProfit) + " gp/hr");
            hourlyProfitVal.setForeground(getProfitColor(hourlyProfit, config));
        } else {
            setSessionStatsVisible(false);
        }
    }

    private void setSessionStatsVisible(boolean visible) {
        subInfoPanel.getComponent(SESSION_TIME_ROW).setVisible(visible);
        subInfoPanel.getComponent(HOURLY_PROFIT_ROW).setVisible(visible);
    }
}
