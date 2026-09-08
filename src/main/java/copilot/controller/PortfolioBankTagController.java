package copilot.controller;
import static java.util.Collections.*;

import copilot.rs.*;
import copilot.config.*;
import copilot.model.*;
import com.google.inject.*;
import lombok.extern.slf4j.*;
import net.runelite.api.gameval.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.*;
import net.runelite.client.game.*;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.banktags.*;
import net.runelite.client.util.*;

import java.util.*;
import java.util.concurrent.atomic.*;

@Singleton
@Slf4j
@lombok.RequiredArgsConstructor(onConstructor_ = @Inject)
public class PortfolioBankTagController {
    private static final String COPILOT_CONFIG_GROUP = "flippingcopilot";
    private static final String CREATED_TAB_CONFIG_KEY = "portfolioBankTagTabCreated";
    private static final String BANK_TAGS_CONFIG_GROUP = "banktags", BANK_TAGS_TAB_CONFIG = "tagtabs";
    private static final String BANK_TAGS_ICON_PREFIX = "icon_", TAG_NAME = "portfolio";
    private static final int TAB_ICON_ITEM_ID = ItemID.FRISD_TAXBAG_BULGING;
    private static final String LEGACY_TAB_ICON_ITEM_ID = String.valueOf(Items.PLATINUM_TOKENS_ITEM_ID);

    private final CopilotConfig config;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private final BankTagsPlugin bankTagsPlugin;
    private final BankTagsService bankTagsService;
    private final TagManager bankTagManager;
    private final ItemManager itemManager;
    private final PortfolioStateRS portfolioStateRS;
    private final BankStateRS bank;

    private final AtomicBoolean registered = new AtomicBoolean(false), active = new AtomicBoolean(false);
    private final AtomicBoolean syncQueued = new AtomicBoolean(false);
    private volatile Set<Integer> bankedPortfolioItemIds = emptySet();
    private Runnable removePortfolioListener, removeBankListener;

    public void startUp() {
        active.set(true);
        removePortfolioListener = portfolioStateRS.registerListener(state -> requestSync());
        removeBankListener = bank.registerListener(state -> requestSync());
        requestSync();
    }

    public void shutDown() {
        active.set(false);
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

    public void onConfigChanged() { requestSync(); }

    private void requestSync() {
        if (!active.get() || !syncQueued.compareAndSet(false, true)) { return; }

        // Plugin start-up runs on the AWT event thread when toggled from the plugin
        // panel. ItemManager.canonicalize(), used by sync(), requires the client thread.
        clientThread.invokeLater(() -> {
            syncQueued.set(false);
            if (active.get()) { sync(); }
        });
    }

    private void sync() {
        if (!config.portfolioBankTag() || !pluginManager.isPluginActive(bankTagsPlugin)) {
            bankedPortfolioItemIds = emptySet();
            if (!config.portfolioBankTag()) { removeAutoCreatedTab(); }
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
        bankedPortfolioItemIds = emptySet();
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
        var portfolioState = portfolioStateRS.get();
        var bankState = bank.get();
        if (portfolioState == null || !portfolioState.loaded || bankState == null || !bankState.loaded) {
            setBankedPortfolioItemIds(emptySet());
            return;
        }

        Set<Integer> bankItems = new HashSet<>();
        bankState.items.forEach((itemId, quantity) -> {
            if (itemId != null && quantity != null && quantity > 0) { bankItems.add(canonicalize(itemId)); }
        });

        Set<Integer> itemIds = new HashSet<>();
        for (PortfolioItem item : portfolioState.itemCardDataByItemId.values()) {
            if (item != null && item.hasPortfolioQuantityInBank()) {
                int itemId = canonicalize(item.itemId);
                if (bankItems.contains(itemId)) { itemIds.add(itemId); }
            }
        }
        setBankedPortfolioItemIds(unmodifiableSet(itemIds));
    }

    private void setBankedPortfolioItemIds(Set<Integer> itemIds) {
        var previous = bankedPortfolioItemIds;
        if (previous.equals(itemIds)) { return; }

        bankedPortfolioItemIds = itemIds;
        refreshActivePortfolioTag();
    }

    private void refreshActivePortfolioTag() {
        if (TAG_NAME.equals(bankTagsService.getActiveTag())) {
            bankTagsService.openBankTag(TAG_NAME, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
        }
    }

    private int canonicalize(int itemId) { return itemManager.canonicalize(Math.abs(itemId)); }
}
