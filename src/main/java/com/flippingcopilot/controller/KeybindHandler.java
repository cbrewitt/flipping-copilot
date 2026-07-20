package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.ui.flipsdialog.FlipsDialogController;
import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;


@Singleton
public class KeybindHandler {

    private final KeyManager keyManager;
    private final FlippingCopilotConfig config;
    private final ClientThread clientThread;
    private final SuggestionManager suggestionManager;
    private final Client client;
    private final GrandExchange grandExchange;
    private final OfferHandler offerHandler;
    private final SuggestionController suggestionController;
    private final FlipsDialogController flipsDialogController;
    private final KeyListener keyListener;


    @Inject
    public KeybindHandler(KeyManager keyManager, FlippingCopilotConfig config, ClientThread clientThread, SuggestionManager suggestionManager, Client client, GrandExchange grandExchange, OfferHandler offerHandler, SuggestionController suggestionController, FlipsDialogController flipsDialogController) {
        this.keyManager = keyManager;
        this.config = config;
        this.clientThread = clientThread;
        this.suggestionManager = suggestionManager;
        this.client = client;
        this.grandExchange = grandExchange;
        this.offerHandler = offerHandler;
        this.suggestionController = suggestionController;
        this.flipsDialogController = flipsDialogController;
        this.keyListener = createKeyListener();
    }

    public void register() {
        keyManager.registerKeyListener(keyListener);
    }

    public void unregister() {
        keyManager.unregisterKeyListener(keyListener);
    }


    private KeyListener createKeyListener() {
        return new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                // Prevent enter as a keybind as that will also submit the value
                if (e.getKeyCode() == KeyEvent.VK_ENTER) return;

                boolean quickSetPressed = keybindMatches(config.quickSetKeybind(), e);
                boolean skipSuggestionPressed = keybindMatches(config.skipSuggestionKeybind(), e);
                boolean openGraphPressed = keybindMatches(config.openGraphKeybind(), e);
                if (!quickSetPressed && !skipSuggestionPressed && !openGraphPressed) {
                    return;
                }

                clientThread.invokeLater(() -> handleKeybind(quickSetPressed, skipSuggestionPressed, openGraphPressed));
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }

            private void handleKeybind(boolean quickSetPressed, boolean skipSuggestionPressed, boolean openGraphPressed) {
                var suggestion = suggestionManager.getSuggestion();

                var inputType = client.getVarcIntValue(VarClientInt.INPUT_TYPE);

                var isPriceOrQuantityBoxOpen = client.getWidget(ComponentID.CHATBOX_TITLE) != null
                        && inputType == 7
                        && client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER) != null
                        && grandExchange.isSlotOpen();

                if (quickSetPressed && isPriceOrQuantityBoxOpen) {
                    offerHandler.setSuggestedAction(suggestion);
                    return;
                }

                if (skipSuggestionPressed && !isPriceOrQuantityBoxOpen) {
                    suggestionController.skipSuggestion();
                    return;
                }

                if (openGraphPressed && !isPriceOrQuantityBoxOpen) {
                    flipsDialogController.openSuggestionPriceGraph();
                }
            }
        };
    }

    private boolean keybindMatches(Keybind keybind, KeyEvent e) {
        return keybind != null && keybind.matches(e);
    }
}
