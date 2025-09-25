package com.flippingcopilot.ui;

import com.flippingcopilot.controller.CopilotLoginController;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


@Singleton
public class LoginPanel extends JPanel {
    private final static int PAGE_WIDTH = 225;

    private final CopilotLoginController copilotLoginController;

    private final JPanel loginContainer;
    private final JButton loginButton;
    private final JTextField emailTextField;
    private final JTextField passwordTextField;
    private final JLabel errorMessageLabel;

    public final Spinner spinner = new Spinner();

    @Inject
    public LoginPanel(CopilotLoginController copilotLoginController) {
        this.copilotLoginController = copilotLoginController;
        this.setLayout(new BorderLayout());
        this.setBackground(ColorScheme.DARK_GRAY_COLOR);
        this.setSize(PAGE_WIDTH, 250);

        loginContainer = new JPanel();
        loginContainer.setLayout(new BoxLayout(loginContainer, BoxLayout.PAGE_AXIS));

        JPanel logoContainer = this.buildLogo();
        loginContainer.add(logoContainer, BorderLayout.CENTER);

        JPanel spinnerContainer = this.buildSpinner();
        loginContainer.add(spinnerContainer, BorderLayout.CENTER);

        JPanel errorContainer = this.buildErrorMessageLabel();
        errorMessageLabel = ((JLabel) errorContainer.getComponent(0));
        loginContainer.add(errorContainer, BorderLayout.CENTER);

        emailTextField = new JTextField();
        emailTextField.setSize(PAGE_WIDTH, 40);
        loginContainer.add(this.buildEmailInput(emailTextField), BorderLayout.CENTER);

        passwordTextField = new JPasswordField();
        passwordTextField.setSize(PAGE_WIDTH, 40);
        loginContainer.add(this.buildPasswordInput(passwordTextField), BorderLayout.CENTER);

        JPanel loginButtonContainer = this.buildLoginButton();
        loginButton = ((JButton) loginButtonContainer.getComponent(0));
        loginContainer.add(loginButtonContainer, BorderLayout.CENTER);

        JPanel createAccountLink = this.buildCreateAccountLink();
        loginContainer.add(createAccountLink, BorderLayout.CENTER);

        this.add(loginContainer, BorderLayout.NORTH);
    }

    public JPanel buildLogo() {
        JPanel container = new JPanel();
        ImageIcon icon = new ImageIcon(ImageUtil.loadImageResource(getClass(), "/logo.png"));
        Image resizedLogo = icon.getImage().getScaledInstance(50, 45, Image.SCALE_SMOOTH);
        JLabel logoLabel = new JLabel(new ImageIcon(resizedLogo));
        logoLabel.setSize(50, 45);
        container.add(logoLabel, BorderLayout.CENTER);
        container.setBorder(new EmptyBorder(10, 0, 10, 0));
        return container;
    }

    public JPanel buildSpinner() {
        JPanel container = new JPanel();
        container.add(spinner, BorderLayout.CENTER);
        return container;
    }

    public void startLoading() {
        spinner.show();
        loginButton.setEnabled(false);
        errorMessageLabel.setText("");
        errorMessageLabel.setVisible(false);
    }

    public void endLoading() {
        spinner.hide();
        loginButton.setEnabled(true);
    }

    public JPanel buildErrorMessageLabel() {
        JPanel container = new JPanel();
        JLabel errorLabel = new JLabel();
        errorLabel.setForeground(Color.RED);
        errorLabel.setHorizontalAlignment(SwingConstants.LEFT);
        errorLabel.setSize(PAGE_WIDTH, 40);
        errorLabel.setVisible(false);
        container.add(errorLabel);
        return container;
    }

    public JPanel buildEmailInput(JTextField textField) {
        JPanel container = new JPanel(new GridLayout(2, 1));
        container.setBorder(new EmptyBorder(0, 0, 10, 0));
        JLabel emailLabel = new JLabel("Email address");
        container.add(emailLabel, BorderLayout.WEST);
        container.add(textField);
        return container;
    }

    public JPanel buildPasswordInput(JTextField textField) {
        JPanel container = new JPanel(new GridLayout(2, 1));
        container.setBorder(new EmptyBorder(0, 0, 10, 0));
        JLabel passwordLabel = new JLabel("Password");
        container.add(passwordLabel);
        container.add(textField);
        return container;
    }

    public JPanel buildCreateAccountLink() {
        JPanel container = new JPanel();
        JLabel createAccountLabel = new JLabel("Don't have an account? Sign up.");
        createAccountLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        createAccountLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                LinkBrowser.browse("https://flippingcopilot.com/signup");
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                createAccountLabel.setForeground(ColorScheme.BRAND_ORANGE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                createAccountLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            }
        });
        container.add(createAccountLabel);
        return container;
    }

    public JPanel buildLoginButton() {
        JPanel container = new JPanel();
        JButton button = new JButton("Login");
        button.addActionListener((ActionEvent a) -> {
            copilotLoginController.onLoginPressed(emailTextField.getText(), passwordTextField.getText());
        });
        container.add(button);
        return container;
    }

    public void showLoginErrorMessage(String message) {
        errorMessageLabel.setText("<html><p>" + message + "</p></html>");
        errorMessageLabel.setVisible(true);
    }

    public void refresh() {

    }
}