package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.SessionManager;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutorService;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FlipsDialogController {

    private final ItemController itemController;
    private final FlipManager flipsManager;
    @Named("copilotExecutor")
    private final ExecutorService executorService;
    private final SessionManager sessionManager;
    private final CopilotLoginManager copilotLoginManager;
    private final FlippingCopilotConfig config;
    private final ApiRequestHandler apiRequestHandler;

    private JDialog currentDialog = null;
    private Point lastDialogPosition = null;
    private Dimension lastDialogSize = null;

    public void showFlipsDialog() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::showFlipsDialog);
            return;
        }

        try {
            long st = System.currentTimeMillis();
            log.info("showing flips dialog");

            if (currentDialog != null) {
                lastDialogPosition = currentDialog.getLocation();
                lastDialogSize = currentDialog.getSize();
                currentDialog.dispose();
            }

            JDialog dialog = new JDialog();
            dialog.setTitle("Flipping Copilot - Flips & Transactions");
            dialog.setResizable(true);
            dialog.setMinimumSize(new Dimension(800, 600));

            JTabbedPane tabbedPane = new JTabbedPane();
            tabbedPane.setBackground(ColorScheme.DARK_GRAY_COLOR);

            // Initialize all panels upfront
            log.debug("Creating FlipsPanel...");
            long startTime = System.currentTimeMillis();
            FlipsPanel flipsPanel = new FlipsPanel(flipsManager, itemController, copilotLoginManager,
                    executorService, config, apiRequestHandler);
            log.debug("FlipsPanel created in {}s", (System.currentTimeMillis() - startTime) / 1000.0);

            log.debug("Creating ItemAggregatePanel...");
            startTime = System.currentTimeMillis();
            ItemAggregatePanel itemsPanel = new ItemAggregatePanel(flipsManager, itemController,
                    copilotLoginManager, executorService, config);
            log.debug("ItemAggregatePanel created in {}s", (System.currentTimeMillis() - startTime) / 1000.0);

            log.debug("Creating AccountsAggregatePanel...");
            startTime = System.currentTimeMillis();
            AccountsAggregatePanel accountsPanel = new AccountsAggregatePanel(flipsManager, copilotLoginManager,
                    executorService, config, apiRequestHandler, flipsManager);
            log.debug("AccountsAggregatePanel created in {}s", (System.currentTimeMillis() - startTime) / 1000.0);

            log.debug("Creating ProfitPanel...");
            startTime = System.currentTimeMillis();
            ProfitPanel profitPanel = new ProfitPanel(flipsManager, executorService, sessionManager,
                    copilotLoginManager, config);
            log.debug("ProfitPanel created in {}s", (System.currentTimeMillis() - startTime) / 1000.0);

            // Add all tabs
            tabbedPane.addTab("Flips", flipsPanel);
            tabbedPane.addTab("Items", itemsPanel);
            tabbedPane.addTab("Accounts", accountsPanel);
            tabbedPane.addTab("Profit graph", profitPanel);

            dialog.setContentPane(tabbedPane);

            // Set size and position
            if (lastDialogSize != null && lastDialogPosition != null) {
                dialog.setSize(lastDialogSize);
                dialog.setLocation(lastDialogPosition);
            } else {
                GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
                Rectangle bounds = env.getMaximumWindowBounds(); // Excludes taskbar
                dialog.setSize(bounds.width, bounds.height);
                dialog.setLocation(bounds.x, bounds.y);
            }

            // Add window listener to save position
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    lastDialogPosition = dialog.getLocation();
                    lastDialogSize = dialog.getSize();
                    log.info("saved dialog position: {} and size: {}", lastDialogPosition, lastDialogSize);
                }
            });

            currentDialog = dialog;
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setModalityType(Dialog.ModalityType.MODELESS);
            dialog.setVisible(true);

            log.info("flips dialog shown successfully {}s", (System.currentTimeMillis() - st) / 1000.0);
        } catch (Exception e) {
            log.error("Error showing flips dialog: {}", e.getMessage(), e);
        }
    }
}