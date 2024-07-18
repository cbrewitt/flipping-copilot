package com.flippingcopilot.controller;

import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.widgets.Widget;

public class GrandExchange {
    private final Client client;
    private final static int CURRENTLY_OPEN_GE_SLOT_VARBIT_ID = 4439;

    GrandExchange(Client client) {
        this.client = client;
    }

    boolean isHomeScreenOpen() {
        return isOpen() && !isSlotOpen();
    }

    boolean isSlotOpen() {
        return getOpenSlot() != -1;
    }

    boolean isOpen() {
        return client.getWidget(465, 7) != null;
    }

    int getOpenSlot() {
        return client.getVarbitValue(CURRENTLY_OPEN_GE_SLOT_VARBIT_ID) - 1;
    }

    Widget getSlotWidget(int slot) {
        return client.getWidget(465, 7 + slot);
    }

    Widget getBuyButton(int slot) {
        Widget slotWidget = getSlotWidget(slot);
        if (slotWidget == null) {
            return null;
        }
        return slotWidget.getChild(0);
    }

    Widget getCollectButton() {
        Widget topBar = client.getWidget(465, 6);
        if (topBar == null) {
            return null;
        }
        return topBar.getChild(1);
    }

    Widget getOfferContainerWidget() {
        return client.getWidget(465, 25);
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
        return client.getVarbitValue(4396);
    }

    int getOfferPrice() {
        return client.getVarbitValue(4398);
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
}
