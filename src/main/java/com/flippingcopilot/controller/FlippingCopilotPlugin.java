package com.flippingcopilot.controller;
import copilot.controller.*;
import net.runelite.api.gameval.InterfaceID;

import javax.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.Singleton;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.ui.*;
import net.runelite.client.events.*;
import net.runelite.api.gameval.*;
import copilot.config.*;
import copilot.model.*;
import copilot.rs.*;
import copilot.ui.*;
import copilot.ui.flipsdialog.*;
import com.google.gson.*;
import com.google.inject.*;
import com.google.inject.name.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.*;
import net.runelite.client.eventbus.*;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.banktags.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.*;

import javax.inject.*;
import javax.swing.*;
import java.util.concurrent.*;

@Slf4j
@PluginDescriptor(name = "Flipping Copilot", description = "Your AI assistant for trading" )
@PluginDependency(BankTagsPlugin.class)
public class FlippingCopilotPlugin extends Plugin {

	@Inject
	private CopilotConfig config;
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	@Named("copilotExecutor")
	private ScheduledExecutorService executor;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private Gson gson;
	@Inject
	private GrandExchange grandExchange;
	@Inject
	private GrandExchangeCollectHandler grandExchangeCollectHandler;
	@Inject
	private GrandExchangeOfferEventHandler offerEventHandler;
	@Inject
	private AccountStatusManager accounts;
	@Inject
	private SuggestionController suggestionController;
	@Inject
	private Suggestions suggestions;
	@Inject
	private WebHookController webHookController;
	@Inject
	private KeybindHandler keybindHandler;
	@Inject
	private CopilotLoginController copilotLoginController;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private CopilotLogin copilotLogin;
	@Inject
	private HighlightController highlights;
	@Inject
	private GameUiChangesHandler gameUiChangesHandler;
	@Inject
	private PlayerLogin osrsLoginManager;
	@Inject
	private FlipManager flipManager;
	@Inject
	private SessionManager sessionManager;
	@Inject
	private Uncollected uncollected;
	@Inject
	private Transactions transactionManager;
	@Inject
	private Offers offerManager;
	@Inject
	private TooltipController tooltipController;
  	@Inject
	private MenuHandler menuHandler;
	@Inject
	private FlipsDialogController dialogs;
	@Inject
	private SlotProfitColorizer slotProfitColorizer;
	@Inject
	private DumpStream dumpsStreamController;
	@Inject
	private GrandExchangeOpenRS grandExchangeOpenRS;
	@Inject
	private GameLogin gameLogin;
	@Inject
	private ConfigState configRS;
	@Inject
	private InventorySlotTooltipOverlay inventorySlotTooltipOverlay;
	@Inject
	private InventoryPortfolioBadgeOverlay inventoryPortfolioBadgeOverlay;
	@Inject
	private PortfolioBankTabBadgeOverlay portfolioBankTabBadgeOverlay;
	@Inject
	private BankStateRS bankStateRS;
	@Inject
	private GeHistoryStateRS history;
	@Inject
	private PatchNotesController patchNotesController;
	@Inject
	private PortfolioBankTagController portfolioBankTagController;
	@Inject
	private PlayerLocationController playerLocationController;

	// We use our own ThreadPool since the default ScheduledExecutorService only has a single thread and we don't want to block it
	@Provides
	@Singleton
	@Named("copilotExecutor")
	public ScheduledExecutorService provideCustomExecutorService() { return Executors.newScheduledThreadPool(2); }

	@Provides
	@Singleton
	public ExecutorService provideExecutorService(@Named("copilotExecutor") ScheduledExecutorService scheduledExecutor) {
		return scheduledExecutor;
	}

	private MainPanel mainPanel;
	private StatsPanel statsPanel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception {
		boolean hadExistingInstallation = Persistance.hasExistingInstallation();
		keybindHandler.register();
		overlayManager.add(inventorySlotTooltipOverlay);
		overlayManager.add(inventoryPortfolioBadgeOverlay);
		overlayManager.add(portfolioBankTabBadgeOverlay);
		portfolioBankTagController.startUp();
		highlights.activate();
		Persistance.setUp(gson);
		// seems we need to delay instantiating the UI till here as otherwise the panels look different
		mainPanel = injector.getInstance(MainPanel.class);
		final var icon = ImageUtil.loadImageResource(getClass(), "/icon-small.png");
		navButton = NavigationButton.builder()
				.tooltip("Flipping Copilot")
				.icon(icon)
				.priority(3)
				.panel(mainPanel)
				.build();
		clientToolbar.addNavigation(navButton);
		copilotLoginController.setLoginPanel(mainPanel.loginPanel);
		copilotLoginController.setMainPanel(mainPanel);
		suggestionController.setCopilotPanel(mainPanel.copilotPanel);
		suggestionController.setMainPanel(mainPanel);
		suggestionController.setLoginPanel(mainPanel.loginPanel);
		suggestionController.setSuggestionPanel(mainPanel.copilotPanel.suggestionPanel);
		grandExchangeCollectHandler.setSuggestionPanel(mainPanel.copilotPanel.suggestionPanel);
		statsPanel = mainPanel.copilotPanel.statsPanel;

		mainPanel.refresh();
		SwingUtilities.invokeLater(() -> patchNotesController.maybeShowOnStartup(mainPanel, hadExistingInstallation));

		if(osrsLoginManager.getInvalidStateDisplayMessage() == null) {
			flipManager.setIntervalAccount(null);
			flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
		}
		dialogs.initDialog(SwingUtilities.getWindowAncestor(mainPanel));
		executor.scheduleAtFixedRate(() ->
			clientThread.invoke(() -> {
				boolean loginValid = osrsLoginManager.isValidLoginState();
				if (loginValid) {
					var accStatus = accounts.getAccountStatus();
					boolean isFlipping = accStatus != null && accStatus.currentlyFlipping();
					long cashStack = accStatus == null ? 0 : accStatus.currentCashStack();
					if(sessionManager.updateSessionStats(isFlipping, cashStack)) {
						mainPanel.copilotPanel.statsPanel.refresh(false, copilotLogin.get().isLoggedIn() && osrsLoginManager.isValidLoginState());
					}
				}
			})
		, 2000, 1000, TimeUnit.MILLISECONDS);
	}

	@Override
	protected void shutDown() throws Exception {
		overlayManager.remove(inventorySlotTooltipOverlay);
		overlayManager.remove(inventoryPortfolioBadgeOverlay);
		overlayManager.remove(portfolioBankTabBadgeOverlay);
		portfolioBankTagController.shutDown();
		offerManager.saveAll();
		highlights.deactivateAndRemoveAll();
		clientThread.invokeLater(() -> slotProfitColorizer.resetAllSlots());
		clientToolbar.removeNavigation(navButton);
		sendSessionStats();
		keybindHandler.unregister();
	}

	private void sendSessionStats() {
		if(copilotLogin.get().isLoggedIn()) {
			String displayName = osrsLoginManager.getLastDisplayName();
			Integer accountId = copilotLogin.get().getAccountId(displayName);
			if (accountId != null && accountId != -1) {
				webHookController.sendMessage(flipManager.calculateStats(sessionManager.getCachedSessionData().startTime, accountId), sessionManager.getCachedSessionData(), displayName, false);
			}
		}
	}

	@Provides
	public CopilotConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(CopilotConfig.class);
	}

	//---------------------------- Event Handlers ----------------------------//
	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
		offerEventHandler.onGrandExchangeOfferChanged(event);
		clientThread.invokeLater(() -> highlights.redraw());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		boolean inventoryChanged = event.getContainerId() == InventoryID.INV;
		boolean bankChanged = event.getContainerId() == InventoryID.BANK;

		if (bankChanged || (inventoryChanged && isBankOpen())) {
			bankStateRS.onGameTick();
			clientThread.invokeLater(() -> highlights.redraw());
		}
		if (bankChanged && playerLocationController.isNearGE()) {
			suggestions.setSuggestionNeeded(true);
		}

		if (event.getContainerId() == InventoryID.INV && grandExchange.isOpen()) {
			suggestions.setSuggestionNeeded(true);
			clientThread.invokeLater(() -> highlights.redraw());
		}
	}

	private boolean isBankOpen() {
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		bankStateRS.onGameTick();
		history.onGameTick(client);
		grandExchangeOpenRS.set(grandExchange.isOpen());

		suggestionController.onGameTick();
		offerEventHandler.onGameTick();
		gameLogin.set(gameLogin.get().nextState(client));
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event) {
		gameUiChangesHandler.onBeforeRender(event);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		int slot = grandExchange.getOpenSlot();
		grandExchangeCollectHandler.handleCollect(event, slot);
		gameUiChangesHandler.handleMenuOptionClicked(event);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired e) {
		tooltipController.tooltip(e);
		gameUiChangesHandler.onScriptPostFired(e);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		gameUiChangesHandler.onMenuEntryAdded(event);
		menuHandler.injectInventoryPortfolioMenuEntry(event);
		menuHandler.injectCopilotPriceGraphMenuEntry(event);
		menuHandler.injectConfirmMenuEntry(event);
		menuHandler.injectSlotActionSwapMenuEntry(event);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		gameUiChangesHandler.onWidgetLoaded(event);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		gameUiChangesHandler.onWidgetClosed(event);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		gameUiChangesHandler.onVarbitChanged(event);
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged event) {
		gameUiChangesHandler.onVarClientStrChanged(event);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		switch (event.getGameState()) {
			case LOGIN_SCREEN:
				sessionManager.reset();
				suggestions.reset();
				osrsLoginManager.reset();
				accounts.reset();
				uncollected.reset();
				statsPanel.refresh(true, copilotLogin.get().isLoggedIn() && osrsLoginManager.isValidLoginState());
				gameLogin.set(gameLogin.get().nextState(client));
				mainPanel.refresh();
				break;
			case LOGGING_IN:
			case HOPPING:
			case CONNECTION_LOST:
				osrsLoginManager.setLastLoginTick(client.getTickCount());
				gameLogin.set(gameLogin.get().nextState(client));
				break;
			case LOGGED_IN:
				// we want to update the flips panel on login but unfortunately the display name
				// is not available immediately so schedule what we need to do here for in the future
				// todo: move to just using the accountHash which is available immediately to simply things
				clientThread.invokeLater(() -> {
					if (client.getGameState() != GameState.LOGGED_IN) { return true; }
					final String name = osrsLoginManager.getPlayerDisplayName();
					if (name == null) { return false; }
					statsPanel.resetIntervalDropdownToSession();
					Integer accountId = copilotLogin.get().getAccountId(name);
					if (accountId != null && accountId != -1) {
						flipManager.setIntervalAccount(accountId);
					} else {
						flipManager.setIntervalAccount(null);
					}
					flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
					statsPanel.refresh(true, copilotLogin.get().isLoggedIn()  && osrsLoginManager.isValidLoginState());
					mainPanel.refresh();
					if(copilotLogin.get().isLoggedIn()) {
						transactionManager.scheduleSyncIn(0, name);
					}
					return true;
				});
		}
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event) {
		gameUiChangesHandler.onVarClientIntChanged(event);
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown clientShutdownEvent) {
		log.debug("client shutdown event received");
		offerManager.saveAll();
		sendSessionStats();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (event.getGroup().equals("flippingcopilot")) {
			log.debug("copilot config changed event received");
			configRS.forceSet(config);
			if (event.getKey().equals("profitAmountColor") || event.getKey().equals("lossAmountColor")) {
				mainPanel.copilotPanel.statsPanel.refresh(true, copilotLogin.get().isLoggedIn() && osrsLoginManager.isValidLoginState());
			}
			if (event.getKey().equals("suggestionHighlights")) {
				clientThread.invokeLater(() -> highlights.redraw());
			}
			if (event.getKey().equals("slotPriceColorEnabled")) {
				handleSlotPriceColorConfigChange();
			}
			if (event.getKey().equals("slotPriceProfitableColor") || event.getKey().equals("slotPriceUnprofitableColor")) {
				clientThread.invokeLater(() -> {
					slotProfitColorizer.updateAllSlots();
					highlights.redraw();
				});
			}
			if (event.getKey().equals("portfolioBankTag")) {
				portfolioBankTagController.onConfigChanged();
			}
		}
	}

	private void handleSlotPriceColorConfigChange() {
		if (config.slotPriceColorEnabled()) {
			clientThread.invokeLater(() -> slotProfitColorizer.updateAllSlots());
		} else {
			clientThread.invokeLater(() -> slotProfitColorizer.resetAllSlots());
		}
	}
}
