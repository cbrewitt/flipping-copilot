package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.PremiumInstanceStatus;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.ui.PremiumInstancePanel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.util.function.Consumer;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PremiumInstanceController {

    private final ApiRequestHandler apiRequestHandler;
    private final FlippingCopilotConfig copilotConfig;
    private final SuggestionManager suggestionManager;
    private JDialog dialog;

    public void loadAndOpenPremiumInstanceDialog() {
        // Create the dialog
        if(dialog != null) {
            dialog.dispose();
        }
        dialog = new JDialog();
        dialog.setTitle("Premium accounts management");
        dialog.setModal(false);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(null);

        // Create the panel
        PremiumInstancePanel panel = new PremiumInstancePanel(copilotConfig, apiRequestHandler, suggestionManager);
        dialog.setContentPane(panel);

        // Show loading state
        panel.showLoading();
        Consumer<PremiumInstanceStatus> c = (status) -> {
            SwingUtilities.invokeLater(() -> {  // Make sure UI updates happen on EDT
                if (status.getLoadingError() != null && !status.getLoadingError().isEmpty()) {
                    panel.showError(status.getLoadingError());
                } else {
                    panel.showManagementView(status);
                }
            });
        };
        apiRequestHandler.asyncGetPremiumInstanceStatus(c);
        dialog.setVisible(true);
    }
}