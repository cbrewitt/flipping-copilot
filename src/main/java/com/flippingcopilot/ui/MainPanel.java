package com.flippingcopilot.ui;

import com.flippingcopilot.controller.CopilotLoginController;
import com.flippingcopilot.manager.CopilotLoginManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

import static com.flippingcopilot.ui.UIUtilities.buildButton;

@Singleton
@Slf4j
public class MainPanel extends PluginPanel {

    public static final int CONTENT_WIDTH = 242 - 12;

    // dependencies
    public final LoginPanel loginPanel;
    public final CopilotPanel copilotPanel;
    private final CopilotLoginManager copilotLoginManager;
    private final CopilotLoginController copilotLoginController;

    // UI components
    private final CardLayout cardLayout = new CardLayout();

    @Inject
    public MainPanel(CopilotPanel copilotPanel,
                     LoginPanel loginPanel,
                     CopilotLoginManager copilotLoginManager,
                     CopilotLoginController copilotLoginController) {
        super(false);
        this.copilotLoginManager = copilotLoginManager;
        this.copilotPanel = copilotPanel;
        this.loginPanel = loginPanel;
        this.copilotLoginController = copilotLoginController;

        setLayout(cardLayout);
        setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        add(buildLoggedInView(), "logged-in");
        add(buildLoggedOutView(), "logged-out");
        cardLayout.show(this, copilotLoginManager.isLoggedIn() ? "logged-in" : "logged-out");

    }

    private JPanel buildLoggedOutView() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BorderLayout());
        JPanel topBar = constructTopBar(false);
        wrapper.add(topBar, BorderLayout.NORTH);
        wrapper.add(loginPanel, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildLoggedInView() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BorderLayout());
        JPanel topBar = constructTopBar(true);
        wrapper.add(topBar, BorderLayout.NORTH);
        wrapper.add(copilotPanel, BorderLayout.CENTER);
        return wrapper;
    }



    public void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            // Always execute this in the Swing EDT thread
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        if (copilotLoginManager.isLoggedIn()) {
            showLoggedInView();
            copilotPanel.refresh();
        } else {
            showLoggedOutView();
            loginPanel.refresh();
            copilotPanel.suggestionPanel.refresh();
        }
    }

    private void showLoggedOutView() {
        loginPanel.showLoginErrorMessage("");
        cardLayout.show(this, "logged-out");
        revalidate();
        repaint();
    }

    private void showLoggedInView() {
        cardLayout.show(this, "logged-in");
        revalidate();
        repaint();
    }

    private JPanel constructTopBar(boolean isLoggedIn) {
        JPanel container = new JPanel();
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);
        container.setLayout(new BorderLayout());

        JPanel topBar = new JPanel();
        topBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        int columns = isLoggedIn ? 4 : 3;
        topBar.setLayout(new GridLayout(1, columns));

        JLabel reddit = buildTopBarUriButton(UIUtilities.redditIcon,
                "Flipping Copilot reddit",
                "https://www.reddit.com/r/FlippingCopilot/");
        topBar.add(reddit);

        JLabel discord = buildTopBarUriButton(UIUtilities.discordIcon,
                "Flipping Copilot Discord",
                "https://discord.gg/UyQxA4QJAq");
        topBar.add(discord);

        JLabel website = buildTopBarUriButton(UIUtilities.internetIcon,
                "Flipping Copilot website",
                "https://flippingcopilot.com");
        topBar.add(website);


        if (isLoggedIn) {
            BufferedImage icon = ImageUtil.loadImageResource(getClass(), UIUtilities.logoutIcon);
            JLabel logout = buildButton(icon, "Log out", () -> {
                copilotLoginController.onLogout();
                showLoggedOutView();
            });
            topBar.add(logout);
        }

        container.add(topBar);
        container.setBorder(new EmptyBorder(3, 0, 10, 0));
        return container;
    }

    private JLabel buildTopBarUriButton(String iconPath, String tooltip, String uriString) {
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), iconPath);
        return buildButton(icon, tooltip, () -> {
            LinkBrowser.browse(uriString);
        });
    }
}