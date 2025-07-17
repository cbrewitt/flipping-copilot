package com.flippingcopilot.ui.components;
import com.google.common.base.MoreObjects;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class AccountDropdown extends JComboBox<String> {

    public static final String ALL_ACCOUNTS_DROPDOWN_OPTION = "All accounts";

    // dependencies
    private final Supplier<Map<String, Integer>> accountsGetter;

    // state
    private Map<String, Integer> cachedAccounts;
    private volatile boolean refreshInProgress = false;

    public AccountDropdown(Supplier<Map<String, Integer>> accountsGetter, Consumer<Integer> onDisplayNameChanged, String initialValue) {
        super();
        this.accountsGetter = accountsGetter;

        setBorder(BorderFactory.createEmptyBorder());
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        setSelectedItem(initialValue);
        addItemListener(e -> {
            if (!refreshInProgress && e.getStateChange() == ItemEvent.SELECTED) {
                onDisplayNameChanged.accept(getSelectedAccountId());
            }
        });
    }

    public Integer getSelectedAccountId() {
        String value = (String) getSelectedItem();
        if (value == null || ALL_ACCOUNTS_DROPDOWN_OPTION.equals(value)) {
            return null;
        }
        return cachedAccounts.getOrDefault(value, -1);
    }

    public void refresh() {
        Map<String, Integer> displayNameOptions = accountsGetter.get();
        if (!Objects.equals(displayNameOptions, cachedAccounts)) {
            cachedAccounts = displayNameOptions;
            String previousSelectedItem = (String) getSelectedItem();
            refreshInProgress = true;
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) getModel();
            model.removeAllElements();
            cachedAccounts.forEach((k, v) -> {
                model.addElement(k);
            });
            model.addElement(ALL_ACCOUNTS_DROPDOWN_OPTION);
            setVisible(model.getSize() > 1);
            refreshInProgress = false;
            setSelectedItem(MoreObjects.firstNonNull(previousSelectedItem, ALL_ACCOUNTS_DROPDOWN_OPTION));
        }
    }

    public void setSelectedAccountId(Integer accountId) {
        if (accountId == null) {
            setSelectedItem(ALL_ACCOUNTS_DROPDOWN_OPTION);
        } else {
            if(cachedAccounts == null) {
                cachedAccounts = accountsGetter.get();
            }
            for (Map.Entry<String, Integer> a : cachedAccounts.entrySet()) {
                if(a.getValue().equals(accountId)) {
                    setSelectedItem(a.getKey());
                }
            }
        }
    }
}