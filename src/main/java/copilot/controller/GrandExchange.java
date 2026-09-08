package copilot.controller;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.*;
import copilot.model.*;
import lombok.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.api.gameval.*;

import javax.inject.*;
import java.util.*;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;
import static net.runelite.api.Varbits.GE_OFFER_CREATION_TYPE;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GrandExchange {
    private final Client client;

    boolean isHomeScreenOpen() { return isOpen() && !isSlotOpen(); }

    boolean isSlotOpen() { return getOpenSlot() != -1; }

    String getOfferType() { return client.getVarbitValue(GE_OFFER_CREATION_TYPE) == 1 ? "sell" : "buy"; }

    boolean isCollectButtonVisible() {
        Widget w = client.getWidget(InterfaceID.GE_OFFERS, 6);
        if (w == null) { return false; }
        Widget[] children = w.getChildren();
        if (children == null) { return false; }
        return Arrays.stream(children).anyMatch(c -> !c.isHidden() && "Collect".equals(c.getText()));
    }

    public int getOpenSlot() { return client.getVarbitValue(VarbitID.GE_SELECTEDSLOT) - 1; }

    Widget getSlotWidget(int slot) { return client.getWidget(InterfaceID.GE_OFFERS, 7 + slot); }

    Widget getBuyButton(int slot) { return child(getSlotWidget(slot), 0); }

    Widget getCollectButton() { return child(client.getWidget(InterfaceID.GE_OFFERS, 6), 2); }

    Widget getOfferContainerWidget() { return client.getWidget(InterfaceID.GE_OFFERS, 26); }

    Widget getConfirmButton() { return child(getOfferContainerWidget(), 58); }

    int getOfferQuantity() { return client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY); }

    int getOfferPrice() { return client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE); }

    boolean isOfferTypeSell() { return client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) == 1; }

    public boolean isOpen() { return client.getWidget(InterfaceID.GE_OFFERS, 7) != null; }

    public boolean isPreviousSearchSet() { return client.getVarpValue(VarPlayerID.GE_LAST_SEARCHED) != -1; }

    public boolean showLastSearchEnabled() { return client.getVarbitValue(VarbitID.DISABLE_LAST_SEARCHED) == 0; }

    public Widget getSetQuantityButton() { return child(getOfferContainerWidget(), 51); }

    public Widget getSetPriceButton() { return child(getOfferContainerWidget(), 54); }

    public Widget getSetQuantityAllButton() { return child(getOfferContainerWidget(), 50); }

    private static Widget child(Widget parent, int index) { return parent == null ? null : parent.getChild(index); }

    public OfferSetup getOfferScreenSetupOfferState() {
        if (!isSlotOpen() || !isSetupOfferOpen()) { return null; }
        return new OfferSetup(
                getOfferType(),
                client.getVarpValue(CURRENT_GE_ITEM),
                getOfferPrice(),
                getOfferQuantity(),
                isSearchOpen());
    }

    Widget getBackButton() { return client.getWidget(InterfaceID.GE_OFFERS, 4); }

    public boolean isSetupOfferOpen() {
        Widget confirmButton = getConfirmButton();
        return confirmButton != null && !confirmButton.isHidden();
    }

    private boolean isSearchOpen() {
        Widget searchResults = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        return searchResults != null && !searchResults.isHidden();
    }
}
