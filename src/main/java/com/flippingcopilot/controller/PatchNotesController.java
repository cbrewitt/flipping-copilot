package com.flippingcopilot.controller;

import com.flippingcopilot.ui.PatchNotesPopup;
import com.flippingcopilot.ui.UIUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;

@Singleton
@Slf4j
public class PatchNotesController {

    @javax.inject.Inject
    private Persistance persistance;

    static final String PATCH_NOTES_VERSION_FILE = "patch-notes-version.txt";

    public void maybeShowOnStartup(Component parent, boolean hadExistingInstallation) {
        if (!UIUtilities.ensureEdt(() -> maybeShowOnStartup(parent, hadExistingInstallation))) return;

        int latestVersion = PatchNotesPopup.LATEST_VERSION;
        Integer lastSeenVersion = loadLastSeenVersion();
        boolean shouldShow = shouldShowPatchNotes(latestVersion, lastSeenVersion, hadExistingInstallation);
        persistSeenVersion(lastSeenVersion == null ? latestVersion : Math.max(lastSeenVersion, latestVersion));

        if (!shouldShow || GraphicsEnvironment.isHeadless()) {
            return;
        }

        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        PatchNotesPopup.show(owner != null ? owner : parent);
    }

    static boolean shouldShowPatchNotes(int currentVersion, Integer lastSeenVersion, boolean hadExistingInstallation) {
        if (lastSeenVersion != null) {
            return currentVersion > lastSeenVersion;
        }
        return hadExistingInstallation;
    }

    private Integer loadLastSeenVersion() {
        Filepath patchNotesVersionPath = patchNotesVersionPath();
        if (!patchNotesVersionPath.exists()) {
            return null;
        }

        try {
            String rawVersion = Persistance.readString(patchNotesVersionPath).trim();
            return rawVersion.isEmpty() ? null : Integer.parseInt(rawVersion);
        } catch (IOException | NumberFormatException e) {
            log.warn("error loading patch notes version from {}", patchNotesVersionPath, e);
            return null;
        }
    }

    private void persistSeenVersion(int version) {
        Filepath patchNotesVersionPath = patchNotesVersionPath();

        try {
            patchNotesVersionPath.write(Integer.toString(version));
        } catch (IOException e) {
            log.warn("error saving patch notes version to {}", patchNotesVersionPath, e);
        }
    }

    private Filepath patchNotesVersionPath() {
        return persistance.getDirectory().joinSegment(PATCH_NOTES_VERSION_FILE);
    }
}
