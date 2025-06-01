package com.flippingcopilot.ui.components;

import com.google.common.base.MoreObjects;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class DisplayNameDropdown extends JComboBox<String> {

    private static final String ALL_ACCOUNTS_DROPDOWN_OPTION = "All accounts";

    // dependencies
    private final Supplier<List<String>> displayNamesGetter;
    private final Consumer<String> onDisplayNameChanged;

    // state
    private volatile boolean refreshInProgress = false;

    public DisplayNameDropdown(Supplier<List<String>> displayNamesGetter, Consumer<String> onDisplayNameChanged) {
        super();
        this.displayNamesGetter = displayNamesGetter;
        this.onDisplayNameChanged = onDisplayNameChanged;
        setupDropdown();
    }

    private void setupDropdown() {
        setBorder(BorderFactory.createEmptyBorder());
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));


        addItemListener(e -> {
            if (!refreshInProgress && e.getStateChange() == ItemEvent.SELECTED) {
                onDisplayNameChanged.accept(getSelectedDisplayName());
            }
        });
    }

    public String getSelectedDisplayName() {
        String value = (String) getSelectedItem();
        if (value == null || ALL_ACCOUNTS_DROPDOWN_OPTION.equals(value)) {
            return null;
        }
        return value;
    }

    public void refresh() {
        List<String> displayNameOptions = displayNamesGetter.get();
        if (displayNameOptionsOutOfDate(displayNameOptions)) {
            String previousSelectedItem = (String) getSelectedItem();
            refreshInProgress = true;
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) getModel();
            model.removeAllElements();
            displayNameOptions.forEach(model::addElement);
            model.addElement(ALL_ACCOUNTS_DROPDOWN_OPTION);
            setVisible(model.getSize() > 1);
            refreshInProgress = false;
            setSelectedItem(MoreObjects.firstNonNull(previousSelectedItem, ALL_ACCOUNTS_DROPDOWN_OPTION));
        }
    }

    public void setSelectedDisplayName(String displayName) {
        setSelectedItem(MoreObjects.firstNonNull(displayName, ALL_ACCOUNTS_DROPDOWN_OPTION));
    }

    private boolean displayNameOptionsOutOfDate(List<String> displayNameOptions) {
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) getModel();
        if (displayNameOptions.size() + 1 != model.getSize()) {
            return true;
        }
        for (int i = 0; i < displayNameOptions.size(); i++) {
            if (!displayNameOptions.get(i).equals(model.getElementAt(i))) {
                return true;
            }
        }
        return false;
    }
}