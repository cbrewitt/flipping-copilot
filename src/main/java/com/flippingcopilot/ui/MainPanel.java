package com.flippingcopilot.ui;

import com.flippingcopilot.controller.CopilotLoginController;
import com.flippingcopilot.model.LoginResponseManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;;

import static com.flippingcopilot.ui.UIUtilities.buildButton;

@Singleton
public class MainPanel extends PluginPanel {

    // dependencies
    public final LoginPanel loginPanel;
    public final CopilotPanel copilotPanel;
    private final LoginResponseManager loginResponseManager;
    private final CopilotLoginController copilotLoginController;

    private Boolean isLoggedInView;

    @Inject
    public MainPanel(CopilotPanel copilotPanel,
                     LoginPanel loginPanel,
                     LoginResponseManager loginResponseManager,
                     CopilotLoginController copilotLoginController) {
        super(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        this.loginResponseManager = loginResponseManager;
        this.copilotPanel = copilotPanel;
        this.loginPanel = loginPanel;
        this.copilotLoginController = copilotLoginController;
    }

    public void refresh() {
        if(!SwingUtilities.isEventDispatchThread()) {
            // we always execute this in the Swing EDT thread
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        boolean shouldBeLoggedInView = loginResponseManager.isLoggedIn();
        if(shouldBeLoggedInView) {
            if (isLoggedInView == null || !isLoggedInView) {
                renderLoggedInView();
            }
            copilotPanel.refresh();
        } else {
            if (isLoggedInView == null || isLoggedInView) {
                renderLoggedOutView();
            }
            loginPanel.refresh();
            copilotPanel.suggestionPanel.refresh();
        }
    }

    public void renderLoggedOutView() {
        removeAll();
        add(constructTopBar(false), BorderLayout.NORTH);
        loginPanel.showLoginErrorMessage("");
        add(loginPanel, BorderLayout.CENTER);
        revalidate();
        isLoggedInView = false;
    }

    public void renderLoggedInView() {
        removeAll();
        add(constructTopBar(true), BorderLayout.NORTH);
        add(copilotPanel, BorderLayout.CENTER);
        revalidate();
        isLoggedInView = true;
    }

    private JPanel constructTopBar(boolean isLoggedIn) {
        JPanel container = new JPanel();
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);
        container.setLayout(new BorderLayout());
        JPanel topBar = new JPanel();
        topBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        int columns = isLoggedIn ? 4 : 3;
        topBar.setLayout(new GridLayout(1, columns));

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
                renderLoggedOutView();
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
