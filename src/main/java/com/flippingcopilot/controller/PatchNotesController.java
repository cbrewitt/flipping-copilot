package com.flippingcopilot.controller;

import com.flippingcopilot.ui.PatchNotesPopup;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
@Slf4j
public class PatchNotesController {

    // Bump this and update PatchNotesPopup when shipping a new patch-note popup.
    public static final int PATCH_NOTES_VERSION = 1;
    static final String PATCH_NOTES_VERSION_FILE = "patch-notes-version.txt";

    public void maybeShowOnStartup(Component parent, boolean hadExistingInstallation) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> maybeShowOnStartup(parent, hadExistingInstallation));
            return;
        }

        Integer lastSeenVersion = loadLastSeenVersion();
        boolean shouldShow = shouldShowPatchNotes(PATCH_NOTES_VERSION, lastSeenVersion, hadExistingInstallation);
        persistSeenVersion(lastSeenVersion == null ? PATCH_NOTES_VERSION : Math.max(lastSeenVersion, PATCH_NOTES_VERSION));

        if (!shouldShow || GraphicsEnvironment.isHeadless()) {
            return;
        }

        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        PatchNotesPopup.show(owner != null ? owner : parent, PATCH_NOTES_VERSION);
    }

    static boolean shouldShowPatchNotes(int currentVersion, Integer lastSeenVersion, boolean hadExistingInstallation) {
        if (lastSeenVersion != null) {
            return currentVersion > lastSeenVersion;
        }
        return hadExistingInstallation;
    }

    private Integer loadLastSeenVersion() {
        Path patchNotesVersionPath = patchNotesVersionPath();
        if (!Files.exists(patchNotesVersionPath)) {
            return null;
        }

        try {
            String rawVersion = Files.readString(patchNotesVersionPath, StandardCharsets.UTF_8).trim();
            return rawVersion.isEmpty() ? null : Integer.parseInt(rawVersion);
        } catch (IOException | NumberFormatException e) {
            log.warn("error loading patch notes version from {}", patchNotesVersionPath, e);
            return null;
        }
    }

    private void persistSeenVersion(int version) {
        Path patchNotesVersionPath = patchNotesVersionPath();

        try {
            Files.writeString(patchNotesVersionPath, Integer.toString(version), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("error saving patch notes version to {}", patchNotesVersionPath, e);
        }
    }

    private Path patchNotesVersionPath() {
        return Persistance.COPILOT_DIR.toPath().resolve(PATCH_NOTES_VERSION_FILE);
    }
}
