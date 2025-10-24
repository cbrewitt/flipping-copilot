package com.flippingcopilot.ui.components;

import lombok.Getter;

import java.awt.CardLayout;
import java.awt.Container;

@Getter
public class TrackingCardLayout extends CardLayout {

    private String currentCard;

    @Override
    public void show(Container parent, String name) {
        currentCard = name;
        super.show(parent, name);
    }

}
