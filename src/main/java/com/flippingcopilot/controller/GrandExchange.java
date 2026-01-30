package com.flippingcopilot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GrandExchange {

    private final static int CURRENTLY_OPEN_GE_SLOT_VARBIT_ID = 4439;
    final static int SHOW_LAST_SEARCHED_VARBIT_ID = 10295;

    private final Client client;

    boolean isHomeScreenOpen() {
        return isOpen() && !isSlotOpen();
    }

    boolean isSlotOpen() {
        return getOpenSlot() != -1;
    }

    boolean isCollectButtonVisible() {
        Widget w = client.getWidget(InterfaceID.GRAND_EXCHANGE, 6);
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
        return topBar.getChild(2);
    }

    Widget getOfferContainerWidget() {
        return client.getWidget(465, 26);
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

    public boolean isOpen() {
        return client.getWidget(InterfaceID.GRAND_EXCHANGE, 7) != null;
    }

    public boolean isPreviousSearchSet() {
        return client.getVarpValue(2674) != -1;
    }

    public boolean showLastSearchEnabled() {
        return client.getVarbitValue(SHOW_LAST_SEARCHED_VARBIT_ID) == 0;
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

    Widget getBackButton() {
        return client.getWidget(InterfaceID.GRAND_EXCHANGE, 4);
    }
}
