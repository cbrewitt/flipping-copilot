package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.BankState;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.model.PortfolioState;
import com.flippingcopilot.rs.BankStateRS;
import com.flippingcopilot.rs.PortfolioStateRS;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
@Slf4j
public class PortfolioBankTagController {
    private static final String COPILOT_CONFIG_GROUP = "flippingcopilot";
    private static final String CREATED_TAB_CONFIG_KEY = "portfolioBankTagTabCreated";
    private static final String BANK_TAGS_CONFIG_GROUP = "banktags";
    private static final String BANK_TAGS_TAB_CONFIG = "tagtabs";
    private static final String BANK_TAGS_ICON_PREFIX = "icon_";
    private static final String TAG_NAME = "portfolio";
    private static final int TAB_ICON_ITEM_ID = ItemID.FRISD_TAXBAG_BULGING;
    private static final String LEGACY_TAB_ICON_ITEM_ID = String.valueOf(ItemController.PLATINUM_TOKENS_ITEM_ID);

    private final FlippingCopilotConfig config;
    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private final BankTagsPlugin bankTagsPlugin;
    private final BankTagsService bankTagsService;
    private final TagManager bankTagManager;
    private final ItemManager itemManager;
    private final PortfolioStateRS portfolioStateRS;
    private final BankStateRS bankStateRS;

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private volatile Set<Integer> bankedPortfolioItemIds = Collections.emptySet();
    private Runnable removePortfolioListener;
    private Runnable removeBankListener;

    @Inject
    public PortfolioBankTagController(FlippingCopilotConfig config,
                                      ConfigManager configManager,
                                      PluginManager pluginManager,
                                      BankTagsPlugin bankTagsPlugin,
                                      BankTagsService bankTagsService,
                                      TagManager bankTagManager,
                                      ItemManager itemManager,
                                      PortfolioStateRS portfolioStateRS,
                                      BankStateRS bankStateRS) {
        this.config = config;
        this.configManager = configManager;
        this.pluginManager = pluginManager;
        this.bankTagsPlugin = bankTagsPlugin;
        this.bankTagsService = bankTagsService;
        this.bankTagManager = bankTagManager;
        this.itemManager = itemManager;
        this.portfolioStateRS = portfolioStateRS;
        this.bankStateRS = bankStateRS;
    }

    public void startUp() {
        removePortfolioListener = portfolioStateRS.registerListener(state -> sync());
        removeBankListener = bankStateRS.registerListener(state -> sync());
        sync();
    }

    public void shutDown() {
        if (removePortfolioListener != null) {
            removePortfolioListener.run();
            removePortfolioListener = null;
        }
        if (removeBankListener != null) {
            removeBankListener.run();
            removeBankListener = null;
        }
        unregisterTag();
    }

    public void onConfigChanged() {
        sync();
    }

    private void sync() {
        if (!config.portfolioBankTag() || !pluginManager.isPluginActive(bankTagsPlugin)) {
            bankedPortfolioItemIds = Collections.emptySet();
            if (!config.portfolioBankTag()) {
                removeAutoCreatedTab();
            }
            unregisterTag();
            return;
        }

        registerTag();
        ensureBankTagTab();
        updateBankedPortfolioItems();
    }

    private void registerTag() {
        if (registered.compareAndSet(false, true)) {
            bankTagManager.registerTag(TAG_NAME, itemId -> bankedPortfolioItemIds.contains(canonicalize(itemId)));
            log.debug("registered dynamic Bank Tags tag '{}'", TAG_NAME);
        }
    }

    private void unregisterTag() {
        bankedPortfolioItemIds = Collections.emptySet();
        if (registered.compareAndSet(true, false)) {
            bankTagManager.unregisterTag(TAG_NAME);
            log.debug("unregistered dynamic Bank Tags tag '{}'", TAG_NAME);
        }
    }

    private void ensureBankTagTab() {
        List<String> tabs = new ArrayList<>(Text.fromCSV(configValue(BANK_TAGS_TAB_CONFIG)));
        if (!tabs.contains(TAG_NAME)) {
            tabs.add(TAG_NAME);
            configManager.setConfiguration(BANK_TAGS_CONFIG_GROUP, BANK_TAGS_TAB_CONFIG, Text.toCSV(tabs));
            configManager.setConfiguration(COPILOT_CONFIG_GROUP, CREATED_TAB_CONFIG_KEY, true);
        }

        String iconKey = BANK_TAGS_ICON_PREFIX + TAG_NAME;
        String iconItemId = configManager.getConfiguration(BANK_TAGS_CONFIG_GROUP, iconKey);
        if (iconItemId == null || LEGACY_TAB_ICON_ITEM_ID.equals(iconItemId)) {
            configManager.setConfiguration(BANK_TAGS_CONFIG_GROUP, iconKey, TAB_ICON_ITEM_ID);
        }
    }

    private void removeAutoCreatedTab() {
        if (!Boolean.TRUE.equals(configManager.getConfiguration(COPILOT_CONFIG_GROUP, CREATED_TAB_CONFIG_KEY, Boolean.class))) {
            return;
        }

        List<String> tabs = new ArrayList<>(Text.fromCSV(configValue(BANK_TAGS_TAB_CONFIG)));
        if (tabs.remove(TAG_NAME)) {
            configManager.setConfiguration(BANK_TAGS_CONFIG_GROUP, BANK_TAGS_TAB_CONFIG, Text.toCSV(tabs));
        }
        configManager.unsetConfiguration(BANK_TAGS_CONFIG_GROUP, BANK_TAGS_ICON_PREFIX + TAG_NAME);
        configManager.unsetConfiguration(COPILOT_CONFIG_GROUP, CREATED_TAB_CONFIG_KEY);
    }

    private String configValue(String key) {
        String value = configManager.getConfiguration(BANK_TAGS_CONFIG_GROUP, key);
        return value == null ? "" : value;
    }

    private void updateBankedPortfolioItems() {
        PortfolioState portfolioState = portfolioStateRS.get();
        BankState bankState = bankStateRS.get();
        if (portfolioState == null || !portfolioState.isLoaded() || bankState == null || !bankState.isLoaded()) {
            setBankedPortfolioItemIds(Collections.emptySet());
            return;
        }

        Set<Integer> bankItems = new HashSet<>();
        bankState.getItems().forEach((itemId, quantity) -> {
            if (itemId != null && quantity != null && quantity > 0) {
                bankItems.add(canonicalize(itemId));
            }
        });

        Set<Integer> itemIds = new HashSet<>();
        for (PortfolioItemCardData item : portfolioState.getItemCardDataByItemId().values()) {
            if (item != null && item.hasPortfolioQuantityInBank()) {
                int itemId = canonicalize(item.getItemId());
                if (bankItems.contains(itemId)) {
                    itemIds.add(itemId);
                }
            }
        }
        setBankedPortfolioItemIds(Collections.unmodifiableSet(itemIds));
    }

    private void setBankedPortfolioItemIds(Set<Integer> itemIds) {
        Set<Integer> previous = bankedPortfolioItemIds;
        if (previous.equals(itemIds)) {
            return;
        }

        bankedPortfolioItemIds = itemIds;
        refreshActivePortfolioTag();
    }

    private void refreshActivePortfolioTag() {
        if (TAG_NAME.equals(bankTagsService.getActiveTag())) {
            bankTagsService.openBankTag(TAG_NAME, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
        }
    }

    private int canonicalize(int itemId) {
        return itemManager.canonicalize(Math.abs(itemId));
    }
}
