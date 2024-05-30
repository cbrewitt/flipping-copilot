package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.controller.FlippingCopilotPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;

import static com.flippingcopilot.ui.UIUtilities.buildButton;

public class MainPanel extends PluginPanel {
    public LoginPanel loginPanel;
    public CopilotPanel copilotPanel;
    public Runnable onCopilotLogout;

    public MainPanel(FlippingCopilotConfig config) {
        setLayout(new BorderLayout());
        copilotPanel = new CopilotPanel(config);
    }

    public void init(FlippingCopilotPlugin plugin) {
        copilotPanel.init(plugin);
        this.loginPanel = plugin.copilotLoginController.getPanel();
        this.onCopilotLogout = plugin.copilotLoginController::onLogout;
        renderLoggedOutView();
    }

    public void renderLoggedOutView() {
        removeAll();
        add(constructTopBar(false), BorderLayout.NORTH);
        loginPanel.showLoginErrorMessage("");
        add(loginPanel);
        revalidate();
    }

    public void renderLoggedInView() {
        removeAll();
        add(constructTopBar(true), BorderLayout.NORTH);
        add(copilotPanel);
        revalidate();
    }

    private JPanel constructTopBar(boolean isLoggedIn) {
        JPanel container = new JPanel();
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);
        container.setLayout(new BorderLayout());
        container.setBorder(new EmptyBorder(0, 0, 5, 0));
        JPanel topBar = new JPanel();
        topBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        int columns = isLoggedIn ? 4 : 3;
        topBar.setLayout(new GridLayout(1, columns));

        JLabel github = buildTopBarUriButton(UIUtilities.githubIcon,
                "Flipping Copilot Github",
                "https://github.com/cbrewitt/flipping-copilot");
        topBar.add(github);

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
                onCopilotLogout.run();
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
            try {
                Desktop desktop = Desktop.getDesktop();
                URI uri = new URI(uriString);
                desktop.browse(uri);
            } catch (Exception error) {}
        });
    }

}
