package com.flippingcopilot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GrandExchange {
    private final Client client;

    boolean isHomeScreenOpen() {
        return isOpen() && !isSlotOpen();
    }

    boolean isSlotOpen() {
        return getOpenSlot() != -1;
    }

    boolean isCollectButtonVisible() {
        Widget w = client.getWidget(InterfaceID.GE_OFFERS, 6);
        if (w == null) {
            return false;
        }
        Widget[] children = w.getChildren();
        if(children == null) {
            return false;
        }
        return Arrays.stream(children).anyMatch(c -> !c.isHidden() && "Collect".equals(c.getText()));
    }

    int getOpenSlot() {
        return client.getVarbitValue(VarbitID.GE_SELECTEDSLOT) - 1;
    }

    Widget getSlotWidget(int slot) {
        return client.getWidget(InterfaceID.GE_OFFERS, 7 + slot);
    }

    Widget getBuyButton(int slot) {
        Widget slotWidget = getSlotWidget(slot);
        if (slotWidget == null) {
            return null;
        }
        return slotWidget.getChild(0);
    }

    Widget getCollectButton() {
        Widget topBar = client.getWidget(InterfaceID.GE_OFFERS, 6);
        if (topBar == null) {
            return null;
        }
        return topBar.getChild(2);
    }

    Widget getOfferContainerWidget() {
        return client.getWidget(InterfaceID.GE_OFFERS, 26);
    }

    Widget getOfferTypeWidget() {
        Widget offerContainer = getOfferContainerWidget();
        if (offerContainer == null) {
            return null;
        }
        return offerContainer.getChild(20);
    }

    Widget getConfirmButton() {
        Widget offerContainer = getOfferContainerWidget();
        if (offerContainer == null) {
            return null;
        }
        return offerContainer.getChild(58);
    }

    int getOfferQuantity() {
        return client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
    }

    int getOfferPrice() {
        return client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE);
    }

    boolean isOfferTypeSell() {
        return client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) == 1;
    }

    public boolean isOpen() {
        return client.getWidget(InterfaceID.GE_OFFERS, 7) != null;
    }

    public boolean isPreviousSearchSet() {
        return client.getVarpValue(VarPlayerID.GE_LAST_SEARCHED) != -1;
    }

    public boolean showLastSearchEnabled() {
        return client.getVarbitValue(VarbitID.DISABLE_LAST_SEARCHED) == 0;
    }

    public Widget getSetQuantityButton() {
        Widget offerContainer = getOfferContainerWidget();
        if (offerContainer == null) {
            return null;
        }
        return offerContainer.getChild(51);
    }

    public Widget getSetPriceButton() {
        Widget offerContainer = getOfferContainerWidget();
        if (offerContainer == null) {
            return null;
        }
        return offerContainer.getChild(54);
    }

    public Widget getSetQuantityAllButton() {
        Widget offerContainer = getOfferContainerWidget();
        if (offerContainer == null) {
            return null;
        }
        return offerContainer.getChild(50);
    }
}
