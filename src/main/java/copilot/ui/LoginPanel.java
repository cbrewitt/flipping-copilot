package copilot.ui;

import net.runelite.client.util.*;
import copilot.controller.*;
import net.runelite.client.ui.*;
import okhttp3.*;

import javax.inject.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

@Singleton
public class LoginPanel extends JPanel {
    private final static int PAGE_WIDTH = 225;

    private final CopilotLoginController copilotLoginController;
    private final ApiClient api;

    private final JButton signUpButton = new JButton("Sign up"), loginButton =  new JButton("Login");
    private final JButton discordLoginButton = new JButton("Login with Discord");
    private final JButton cancelDiscordLoginButton = new JButton("Cancel Discord Login");

    private final JTextField emailTextField, passwordTextField;
    private final JLabel errorMessageLabel;

    public final Spinner spinner = new Spinner();
    private volatile Call discordLoginCall;

    @Inject
    public LoginPanel(CopilotLoginController copilotLoginController, ApiClient api) {
        this.copilotLoginController = copilotLoginController; this.api = api;
        this.setLayout(new BorderLayout()); this.setBackground(ColorScheme.DARK_GRAY_COLOR);
        this.setSize(PAGE_WIDTH, 350);

        var loginContainer = new JPanel();
        loginContainer.setLayout(new BoxLayout(loginContainer, BoxLayout.PAGE_AXIS));

        var logoContainer = this.buildLogo();
        loginContainer.add(logoContainer);

        var spinnerContainer = this.buildSpinner();
        loginContainer.add(spinnerContainer);

        var errorContainer = this.buildErrorMessageLabel();
        errorMessageLabel = ((JLabel) errorContainer.getComponent(0));
        loginContainer.add(errorContainer);

        emailTextField = new JTextField();
        emailTextField.setSize(PAGE_WIDTH, 40);
        loginContainer.add(this.buildInput("Email address", emailTextField));

        passwordTextField = new JPasswordField();
        passwordTextField.setSize(PAGE_WIDTH, 40);
        loginContainer.add(this.buildInput("Password", passwordTextField));

        var loginButtonContainer = this.buildLoginButtons(signUpButton, loginButton);
        loginContainer.add(loginButtonContainer, BorderLayout.CENTER);

        var loginOrSpacer = this.buildLoginOrSpacer();
        loginContainer.add(loginOrSpacer);

        this.setupDiscordLoginButton(); this.setupCancelDiscordLoginButton();
        var container = fullWidthContainer();
        container.add(discordLoginButton);
        loginContainer.add(container);

        var c2 = fullWidthContainer();
        c2.add(cancelDiscordLoginButton);
        c2.setVisible(false);
        loginContainer.add(c2);

        this.add(loginContainer, BorderLayout.NORTH);
    }

    public JPanel buildLogo() {
        var container = new JPanel();
        var icon = new ImageIcon(ImageUtil.loadImageResource(getClass(), "/logo.png"));
        Image resizedLogo = icon.getImage().getScaledInstance(50, 45, Image.SCALE_SMOOTH);
        var logoLabel = new JLabel(new ImageIcon(resizedLogo));
        logoLabel.setSize(50, 45);
        container.add(logoLabel, BorderLayout.CENTER);
        container.setBorder(new EmptyBorder(10, 0, 10, 0)); container.setAlignmentX(Component.LEFT_ALIGNMENT);
        return container;
    }

    public JPanel buildSpinner() {
        var container = new JPanel();
        container.add(spinner, BorderLayout.CENTER);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        return container;
    }

    public void startLoading() {
        spinner.show();
        loginButton.setEnabled(false);
        signUpButton.setEnabled(false);
        discordLoginButton.getParent().setVisible(false);
        cancelDiscordLoginButton.getParent().setVisible(true);
        errorMessageLabel.setText(""); errorMessageLabel.setVisible(false);
    }

    public void endLoading() {
        spinner.hide();
        loginButton.setEnabled(true);
        discordLoginButton.getParent().setVisible(true);
        cancelDiscordLoginButton.getParent().setVisible(false);
        signUpButton.setEnabled(true);
        discordLoginCall = null;
    }

    public JPanel buildErrorMessageLabel() {
        var container = new JPanel();
        var errorLabel = UIUtilities.coloredLabel("", Color.RED); errorLabel.setHorizontalAlignment(SwingConstants.LEFT);
        errorLabel.setSize(PAGE_WIDTH, 40); errorLabel.setVisible(false);
        container.add(errorLabel);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        return container;
    }

    private JPanel buildInput(String label, JTextField textField) {
        var container = new JPanel(new GridLayout(2, 1));
        container.setBorder(new EmptyBorder(0, 0, 10, 0));
        container.add(new JLabel(label), BorderLayout.WEST);
        container.add(textField);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        return container;
    }

    public JPanel buildLoginButtons(JButton signUpButton, JButton loginButton) {
        var container = new JPanel(new GridLayout(1, 2, 8, 0));
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        container.setPreferredSize(new Dimension(PAGE_WIDTH, 36));
        signUpButton.setPreferredSize(new Dimension((PAGE_WIDTH - 8) / 2, 36));
        signUpButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        signUpButton.addActionListener((ActionEvent a) -> {
            LinkBrowser.browse("https://flippingcopilot.com/signup");
        });
        loginButton.setPreferredSize(new Dimension((PAGE_WIDTH - 8) / 2, 36));
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        loginButton.addActionListener((ActionEvent a) -> {
            copilotLoginController.onLoginPressed(emailTextField.getText(), passwordTextField.getText());
        });
        container.add(signUpButton);
        container.add(loginButton);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        return container;
    }

    public void setupDiscordLoginButton() {
        var discordBlue = new Color(88, 101, 242);
        discordLoginButton.setBackground(discordBlue); discordLoginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        discordLoginButton.addActionListener((ActionEvent a) -> {
            startLoading();
            discordLoginCall = api.discordLoginAsync(
                    LinkBrowser::browse,
                (loginResponse) -> {
                    copilotLoginController.onLoginResponse(loginResponse);
                    endLoading();
                },
                (error) -> {
                    String msg = discordLoginCall == null ? "Discord login cancelled" : error.responseMessage;
                    copilotLoginController.onLoginFailure(msg);
                    endLoading();
                }
            );
        });
    }

    public void setupCancelDiscordLoginButton() {
        cancelDiscordLoginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        cancelDiscordLoginButton.addActionListener((ActionEvent a) -> {
            if (discordLoginCall != null) { discordLoginCall.cancel(); }
            endLoading();
        });
    }

    public JPanel buildLoginOrSpacer() {
        var container = new JPanel();
        var orLabel = UIUtilities.coloredLabel("OR", ColorScheme.LIGHT_GRAY_COLOR);
        container.setBorder(new EmptyBorder(8, 0, 8, 0));
        container.setMaximumSize(new Dimension(PAGE_WIDTH, Integer.MAX_VALUE));
        container.setPreferredSize(new Dimension(PAGE_WIDTH, 42)); container.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(orLabel);
        return container;
    }

    public void showLoginErrorMessage(String message) {
        errorMessageLabel.setText("<html><p>" + message + "</p></html>"); errorMessageLabel.setVisible(true);
    }

    public JPanel fullWidthContainer() {
        var container = new JPanel(new BorderLayout());
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        container.setPreferredSize(new Dimension(PAGE_WIDTH, 36)); container.setAlignmentX(LEFT_ALIGNMENT);
        return container;
    }
}
