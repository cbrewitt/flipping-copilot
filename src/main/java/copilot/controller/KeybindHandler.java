package copilot.controller;
import net.runelite.client.input.KeyListener;

import net.runelite.client.input.*;
import net.runelite.api.*;
import copilot.config.*;
import copilot.model.*;
import copilot.ui.flipsdialog.*;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.*;

import javax.inject.*;
import java.awt.event.*;

@Singleton
public class KeybindHandler {

    private final KeyManager keyManager;
    private final CopilotConfig config;
    private final ClientThread clientThread;
    private final Suggestions suggestions;
    private final Client client;
    private final GrandExchange grandExchange;
    private final OfferHandler offerHandler;
    private final SuggestionController suggestionController;
    private final FlipsDialogController dialogs;
    private final KeyListener keyListener;

    @Inject
    public KeybindHandler(KeyManager keyManager, CopilotConfig config, ClientThread clientThread, Suggestions suggestions, Client client, GrandExchange grandExchange, OfferHandler offerHandler, SuggestionController suggestionController, FlipsDialogController dialogs) {
        this.keyManager = keyManager; this.config = config; this.clientThread = clientThread;
        this.suggestions = suggestions; this.client = client; this.grandExchange = grandExchange;
        this.offerHandler = offerHandler; this.suggestionController = suggestionController; this.dialogs = dialogs;
        keyListener = createKeyListener();
    }

    public void register() { keyManager.registerKeyListener(keyListener); }

    public void unregister() { keyManager.unregisterKeyListener(keyListener); }

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
                if (!quickSetPressed && !skipSuggestionPressed && !openGraphPressed) { return; }

                clientThread.invokeLater(() -> handleKeybind(quickSetPressed, skipSuggestionPressed, openGraphPressed));
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }

            private void handleKeybind(boolean quickSetPressed, boolean skipSuggestionPressed, boolean openGraphPressed) {
                var suggestion = suggestions.getSuggestion();

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

                if (openGraphPressed && !isPriceOrQuantityBoxOpen) { dialogs.openSuggestionPriceGraph(); }
            }
        };
    }

    private boolean keybindMatches(Keybind keybind, KeyEvent e) { return keybind != null && keybind.matches(e); }
}
