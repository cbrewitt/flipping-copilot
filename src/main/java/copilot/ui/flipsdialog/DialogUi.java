package copilot.ui.flipsdialog;

import copilot.ui.components.*;
import copilot.ui.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.model.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.function.*;

final class DialogUi {
    private DialogUi() {
    }

    /** Content with a reusable, initially hidden loading overlay. */
    static class BusyPane extends JLayeredPane {
        final Spinner spinner = new Spinner();
        final JPanel overlay = UIUtilities.darkPanel(new GridBagLayout(), DARK_GRAY_COLOR);

        BusyPane(Component content) {
            setBackground(DARK_GRAY_COLOR); setOpaque(true);
            spinner.show();
            overlay.setOpaque(true);
            overlay.add(spinner);
            overlay.setVisible(false);
            setLayout(new OverlayLayout(this));
            add(overlay, MODAL_LAYER);
            add(content, DEFAULT_LAYER);
        }

        JLabel setMessage(String text) {
            var label = UIUtilities.coloredLabel(text, LIGHT_GRAY_COLOR);
            label.setFont(label.getFont().deriveFont(14f));
            var row = UIUtilities.darkPanel(new FlowLayout(FlowLayout.CENTER, 10, 0), DARK_GRAY_COLOR);
            row.setOpaque(false);
            row.add(label);
            row.add(spinner);
            overlay.removeAll();
            overlay.add(row);
            overlay.revalidate();
            overlay.repaint();
            return label;
        }
    }

    static void addMenuItem(JPopupMenu menu, String label, Runnable action) {
        var item = new JMenuItem(label);
        item.addActionListener(event -> action.run());
        menu.add(item);
    }

    static JPanel centeredMessage(String message, Color background, boolean opaque, float fontSize) {
        var panel = new JPanel(new GridBagLayout());
        panel.setOpaque(opaque);
        if (background != null) { panel.setBackground(background); }
        var label = UIUtilities.coloredLabel(message, LIGHT_GRAY_COLOR); label.setFont(label.getFont().deriveFont(fontSize));
        label.setHorizontalAlignment(SwingConstants.CENTER); label.setMinimumSize(label.getPreferredSize());
        var gbc = new GridBagConstraints();
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(label, gbc);
        return panel;
    }

    static JPanel loadingCard(String message, Color background) {
        var loadingLabel = new JLabel(message);
        var spinner = new Spinner();
        var loadingPanel = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();

        loadingLabel.setForeground(LIGHT_GRAY_COLOR); loadingLabel.setFont(loadingLabel.getFont().deriveFont(14f));
        loadingPanel.setBackground(background);
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(10, 10, 10, 10);
        spinner.show();
        loadingPanel.add(spinner, gbc);
        gbc.gridy = 1;
        loadingPanel.add(loadingLabel, gbc);
        return loadingPanel;
    }

    static IntervalDropdown intervalDropdown(BiConsumer<IntervalTimeUnit, Integer> onIntervalChanged) {
        var dropdown = new IntervalDropdown(onIntervalChanged, IntervalDropdown.ALL_TIME, false);
        dropdown.setPreferredSize(new Dimension(150, dropdown.getPreferredSize().height));
        dropdown.setToolTipText("Select time interval");
        return dropdown;
    }

    static AccountDropdown accountDropdown(Supplier<Map<String, Integer>> accountsGetter, Consumer<Integer> onAccountChanged) {
        var dropdown = new AccountDropdown(accountsGetter, onAccountChanged, AccountDropdown.ALL_ACCOUNTS_DROPDOWN_OPTION);
        dropdown.setPreferredSize(new Dimension(120, dropdown.getPreferredSize().height));
        dropdown.setToolTipText("Select account");
        return dropdown;
    }

    static JPanel errorCard(JLabel errorLabel, Runnable onRetry) {
        errorLabel.setForeground(Color.RED); errorLabel.setFont(errorLabel.getFont().deriveFont(14f));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        var errorPanel = UIUtilities.darkPanel(new GridBagLayout(), DARK_GRAY_COLOR);
        var gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(10, 10, 10, 10);
        errorPanel.add(errorLabel, gbc);
        gbc.gridy = 1; gbc.insets = new Insets(20, 10, 10, 10);
        var retryButton = new JButton("Retry");
        retryButton.setBackground(DARKER_GRAY_COLOR); retryButton.setFocusable(false);
        retryButton.addActionListener(e -> onRetry.run());
        errorPanel.add(retryButton, gbc);
        return errorPanel;
    }

    static JSplitPane splitGraphCard(Component graph, Component stats) {
        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        splitPane.setLeftComponent(graph); splitPane.setRightComponent(stats); splitPane.setResizeWeight(0.95);
        splitPane.setDividerLocation(0.95); splitPane.setBackground(DARK_GRAY_COLOR);
        return splitPane;
    }
}
