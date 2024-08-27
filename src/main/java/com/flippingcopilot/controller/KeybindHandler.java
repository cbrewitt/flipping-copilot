package com.flippingcopilot.controller;

import net.runelite.api.VarClientInt;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.input.KeyListener;

import java.awt.event.KeyEvent;

public class KeybindHandler {

    FlippingCopilotPlugin plugin;

    public KeybindHandler(FlippingCopilotPlugin plugin) {
        this.plugin = plugin;
        plugin.keyManager.registerKeyListener(offerEditorKeyListener());

    }

    public void unregister() {
        plugin.keyManager.unregisterKeyListener(offerEditorKeyListener());
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
                if (e.getKeyCode() != plugin.config.quickSetKeybind().getKeyCode()) return;

                plugin.getClientThread().invokeLater(this::handleKeybind);
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }

            private void handleKeybind() {
                var suggestion = plugin.suggestionHandler.getCurrentSuggestion();
                if (suggestion == null) return;

                var inputType = plugin.client.getVarcIntValue(VarClientInt.INPUT_TYPE);

                var isPriceOrQuantityBoxOpen = plugin.client.getWidget(ComponentID.CHATBOX_TITLE) != null
                        && inputType == 7
                        && plugin.client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER) != null
                        && plugin.grandExchange.isSlotOpen();

                if (isPriceOrQuantityBoxOpen) {
                    plugin.offerHandler.setSuggestedAction(suggestion);
                }
            }
        };
    }
}
