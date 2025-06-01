package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.SessionManager;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import com.flippingcopilot.model.TransactionManager;
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
    private final TransactionManager transactionManager;
    private final FlipManager flipsManager;
    private final SuggestionPreferencesManager preferencesManager;
    @Named("copilotExecutor")
    private final ExecutorService executorService;
    private final SessionManager sessionManager;

    private JDialog currentDialog = null;
    private Point lastDialogPosition = null;
    private Dimension lastDialogSize = null;

    public void showFlipsDialog() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::showFlipsDialog);
            return;
        }

        try {
            log.info("Showing flips dialog");

            // If there's already a dialog showing, dispose it
            if (currentDialog != null) {
                lastDialogPosition = currentDialog.getLocation();
                lastDialogSize = currentDialog.getSize();
                currentDialog.dispose();
            }

            // Create new dialog
            JDialog dialog = new JDialog();
            dialog.setTitle("Flipping Copilot - Flips & Transactions");
            dialog.setResizable(true);
            dialog.setMinimumSize(new Dimension(800, 600));

            // Create tabbed pane
            JTabbedPane tabbedPane = new JTabbedPane();
            tabbedPane.setBackground(ColorScheme.DARK_GRAY_COLOR);

            // Create panels for each tab
            FlipsPanel flipsPanel = new FlipsPanel(flipsManager,itemController, transactionManager, executorService);
            TransactionsPanel transactionsPanel = new TransactionsPanel(transactionManager);
            ProfitPanel profitPanel = new ProfitPanel(flipsManager, executorService, sessionManager);

            // Add tabs
            tabbedPane.addTab("Flips", flipsPanel);
            tabbedPane.addTab("Profit graph", profitPanel);
            tabbedPane.addTab("Transactions", transactionsPanel);

            // Set content
            dialog.setContentPane(tabbedPane);

            // Set size and position
            if (lastDialogSize != null && lastDialogPosition != null) {
                dialog.setSize(lastDialogSize);
                dialog.setLocation(lastDialogPosition);
            } else {
                dialog.setSize(1000, 700);
                dialog.setLocationRelativeTo(null);
            }

            // Add window listener to save position
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    lastDialogPosition = dialog.getLocation();
                    lastDialogSize = dialog.getSize();
                    log.info("Saved dialog position: {} and size: {}", lastDialogPosition, lastDialogSize);
                }
            });

            // Store reference and show
            currentDialog = dialog;
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setModalityType(Dialog.ModalityType.MODELESS);
            dialog.setVisible(true);

            log.info("Flips dialog shown successfully");
        } catch (Exception e) {
            log.error("Error showing flips dialog: {}", e.getMessage(), e);
        }
    }
}