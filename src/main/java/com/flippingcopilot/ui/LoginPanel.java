package com.flippingcopilot.ui;

import com.flippingcopilot.controller.CopilotLoginController;
import net.runelite.client.input.KeyListener;
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
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


@Singleton
public class LoginPanel extends JPanel {
    private final static int PAGE_WIDTH = 225;

    private final CopilotLoginController copilotLoginController;

    private JPanel loginContainer;
    private JButton loginButton;
    private JTextField emailTextField;
    private JTextField passwordTextField;
    private JLabel errorMessageLabel;

    public final Spinner spinner = new Spinner();

    @Inject
    public LoginPanel(CopilotLoginController copilotLoginController) {
        this.copilotLoginController = copilotLoginController;
        this.setLayout(new BorderLayout());
        this.setBackground(ColorScheme.DARK_GRAY_COLOR);
        this.setSize(PAGE_WIDTH, 250);

        loginContainer = new JPanel();
        loginContainer.setLayout(new BoxLayout(loginContainer, BoxLayout.PAGE_AXIS));

        this.createLogo();
        this.createSpinner();
        this.createErrorMessageLabel();
        this.createEmailInput();
        this.createPasswordInput();
        this.createLoginButton();
        this.createCreateAccountLink();

        this.add(loginContainer, BorderLayout.NORTH);
    }

    public void createLogo() {
        JPanel container = new JPanel();
        ImageIcon icon = new ImageIcon(ImageUtil.loadImageResource(getClass(), "/logo.png"));
        Image resizedLogo = icon.getImage().getScaledInstance(50, 45, Image.SCALE_SMOOTH);
        JLabel logoLabel = new JLabel(new ImageIcon(resizedLogo));
        logoLabel.setSize(50, 45);
        container.add(logoLabel, BorderLayout.CENTER);
        container.setBorder(new EmptyBorder(10, 0, 10, 0));
        loginContainer.add(container, BorderLayout.CENTER);
    }

    public void createSpinner() {
        JPanel container = new JPanel();
        container.add(spinner, BorderLayout.CENTER);
        loginContainer.add(container, BorderLayout.CENTER);
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

    public void createErrorMessageLabel() {
        JPanel container = new JPanel();
        errorMessageLabel = new JLabel();
        errorMessageLabel.setForeground(Color.RED);
        errorMessageLabel.setHorizontalAlignment(SwingConstants.LEFT);
        errorMessageLabel.setSize(PAGE_WIDTH, 40);
        errorMessageLabel.setVisible(false);
        container.add(errorMessageLabel); // Add the error message label under the logo
        loginContainer.add(container, BorderLayout.CENTER);
    }

    public void createEmailInput() {
        JPanel container = new JPanel(new GridLayout(2, 1));
        container.setBorder(new EmptyBorder(0, 0, 10, 0));
        emailTextField = new JTextField();
        emailTextField.setSize(PAGE_WIDTH, 40);
        emailTextField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent event) {
            }

            @Override
            public void keyReleased(KeyEvent event) {
                JTextField textField = (JTextField) event.getSource();
                String text = textField.getText();
                copilotLoginController.onEmailTextChanged(text);
            }

            @Override
            public void keyPressed(KeyEvent event) {
            }
        });
        emailTextField.addActionListener(copilotLoginController::onLoginPressed);
        JLabel emailLabel = new JLabel("Email address");
        container.add(emailLabel, BorderLayout.WEST);
        container.add(emailTextField);
        loginContainer.add(container, BorderLayout.CENTER);
    }

    public void createPasswordInput() {
        JPanel container = new JPanel(new GridLayout(2, 1));
        container.setBorder(new EmptyBorder(0, 0, 10, 0));
        passwordTextField = new JPasswordField();
        passwordTextField.setSize(PAGE_WIDTH, 40);
        passwordTextField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent event) {
            }

            @Override
            public void keyReleased(KeyEvent event) {
                JTextField textField = (JTextField) event.getSource();
                String text = textField.getText();
                copilotLoginController.onPasswordTextChanged(text);
            }

            @Override
            public void keyPressed(KeyEvent event) {
            }
        });
        passwordTextField.addActionListener(copilotLoginController::onLoginPressed);
        JLabel passwordLabel = new JLabel("Password");
        container.add(passwordLabel);
        container.add(passwordTextField);
        loginContainer.add(container, BorderLayout.CENTER);
    }

    public void createCreateAccountLink() {
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
        loginContainer.add(container, BorderLayout.CENTER);
    }

    public void createLoginButton() {
        JPanel container = new JPanel();
        loginButton = new JButton("Login");
        loginButton.addActionListener(copilotLoginController::onLoginPressed);
        container.add(loginButton);
        loginContainer.add(container, BorderLayout.CENTER);
    }

    public void showLoginErrorMessage(String message) {
        errorMessageLabel.setText("<html><p>" + message + "</p></html>");
        errorMessageLabel.setVisible(true);
    }

    public void refresh() {

    }
}
