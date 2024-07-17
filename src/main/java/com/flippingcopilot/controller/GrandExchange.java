package com.flippingcopilot.controller;

import net.runelite.api.Client;
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

}
