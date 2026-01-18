package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.SuggestionManager;
import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.callback.ClientThread;
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


    @Inject
    public KeybindHandler(KeyManager keyManager, FlippingCopilotConfig config, ClientThread clientThread, SuggestionManager suggestionManager, Client client, GrandExchange grandExchange, OfferHandler offerHandler) {
        this.keyManager = keyManager;
        this.config = config;
        this.clientThread = clientThread;
        this.suggestionManager = suggestionManager;
        this.client = client;
        this.grandExchange = grandExchange;
        this.offerHandler = offerHandler;
        keyManager.registerKeyListener(offerEditorKeyListener());
    }

    public void unregister() {
        keyManager.unregisterKeyListener(offerEditorKeyListener());
    }


    private KeyListener offerEditorKeyListener() {
        return new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                // Prevent enter as a keybind as that will also submit the value
                if (e.getKeyCode() == KeyEvent.VK_ENTER) return;
                if (e.getKeyCode() !=config.quickSetKeybind().getKeyCode()) return;

               clientThread.invokeLater(this::handleKeybind);
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }

            private void handleKeybind() {
                var suggestion = suggestionManager.getSuggestion();

                var inputType = client.getVarcIntValue(VarClientInt.INPUT_TYPE);

                var isPriceOrQuantityBoxOpen =client.getWidget(ComponentID.CHATBOX_TITLE) != null
                        && inputType == 7
                        &&client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER) != null
                        &&grandExchange.isSlotOpen();

                if (isPriceOrQuantityBoxOpen) {
                   offerHandler.setSuggestedAction(suggestion);
                }
            }
        };
    }
}
