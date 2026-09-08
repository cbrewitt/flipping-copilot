package copilot.controller;

import copilot.model.*;
import copilot.config.*;
import copilot.ui.*;
import lombok.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import javax.swing.*;
import java.util.function.*;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PremiumInstanceController {

    private final ApiClient api;
    private final CopilotConfig copilotConfig;
    private final Suggestions suggestions;
    private JDialog dialog;

    public void loadAndOpenPremiumInstanceDialog() {
        // Create the dialog
        if(dialog != null) { dialog.dispose(); }
        dialog = new JDialog();
        dialog.setTitle("Premium accounts management");
        dialog.setModal(false);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(null);

        // Create the panel
        var panel = new PremiumInstancePanel(copilotConfig, api, suggestions);
        dialog.setContentPane(panel);

        // Show loading state
        panel.showLoading();
        Consumer<PremiumInstanceStatus> c = (status) -> {
            SwingUtilities.invokeLater(() -> {  // Make sure UI updates happen on EDT
                if (status.loadingError != null && !status.loadingError.isEmpty()) {
                    panel.showError(status.loadingError);
                } else {
                    panel.showManagementView(status);
                }
            });
        };
        api.asyncGetPremiumInstanceStatus(c);
        dialog.setVisible(true);
    }
}