package copilot.ui;

import net.runelite.client.util.*;
import net.runelite.client.ui.*;
import static copilot.ui.UIUtilities.*;
import copilot.rs.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

@Singleton
@Slf4j
public class MainPanel extends PluginPanel {

    public static final int CONTENT_WIDTH = 242 - 12;

    // dependencies
    public final LoginPanel loginPanel;
    public final CopilotPanel copilotPanel;
    private final CopilotLogin copilotLogin;

    // UI components
    private final CardLayout cardLayout = new CardLayout();

    @Inject
    public MainPanel(CopilotPanel copilotPanel, LoginPanel loginPanel, CopilotLogin copilotLogin) {
        super(false);
        this.copilotLogin = copilotLogin; this.copilotPanel = copilotPanel; this.loginPanel = loginPanel;

        setLayout(cardLayout);
        setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        add(buildView(true, copilotPanel), "logged-in");
        add(buildView(false, loginPanel), "logged-out");
        cardLayout.show(this, copilotLogin.get().isLoggedIn() ? "logged-in" : "logged-out");

    }

    private JPanel buildView(boolean isLoggedIn, JComponent content) {
        var wrapper = new JPanel();
        wrapper.setLayout(new BorderLayout());
        wrapper.add(constructTopBar(isLoggedIn), BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    public void refresh() {
        if (!ensureEdt(this::refresh)) return;
        if (copilotLogin.get().isLoggedIn()) {
            showLoggedInView();
            copilotPanel.refresh();
        } else {
            showLoggedOutView();
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
        var container = darkPanel(new FlowLayout(), ColorScheme.DARK_GRAY_COLOR);
        container.setLayout(new BorderLayout());

        var topBar = darkPanel(new FlowLayout(), ColorScheme.DARK_GRAY_COLOR);
        int columns = isLoggedIn ? 4 : 3;
        topBar.setLayout(new GridLayout(1, columns));

        var reddit = buildTopBarUriButton(redditIcon,
                "Flipping Copilot reddit",
                "https://www.reddit.com/r/FlippingCopilot/");
        topBar.add(reddit);

        var discord = buildTopBarUriButton(discordIcon, "Flipping Copilot Discord", "https://discord.gg/UyQxA4QJAq");
        topBar.add(discord);

        var website = buildTopBarUriButton(internetIcon, "Flipping Copilot website", "https://flippingcopilot.com");
        topBar.add(website);

        if (isLoggedIn) {
            var icon = ImageUtil.loadImageResource(getClass(), logoutIcon);
            var logout = buildButton(icon, "Log out", () -> {
                copilotLogin.clear();
                showLoggedOutView();
            });
            topBar.add(logout);
        }

        container.add(topBar);
        container.setBorder(new EmptyBorder(3, 0, 6, 0));
        return container;
    }

    private JLabel buildTopBarUriButton(String iconPath, String tooltip, String uriString) {
        var icon = ImageUtil.loadImageResource(getClass(), iconPath);
        return buildButton(icon, tooltip, () -> {
            LinkBrowser.browse(uriString);
        });
    }
}
