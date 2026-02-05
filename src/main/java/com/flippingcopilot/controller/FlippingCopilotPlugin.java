package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.rs.FlippingCopilotConfigRS;
import com.flippingcopilot.rs.GrandExchangeOpenRS;
import com.flippingcopilot.rs.OsrsLoginRS;
import com.flippingcopilot.ui.*;
import com.flippingcopilot.ui.flipsdialog.FlipsDialogController;
import com.google.gson.Gson;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
		name = "Flipping Copilot",
		description = "Your AI assistant for trading"
)
public class FlippingCopilotPlugin extends Plugin {

	@Inject
	private FlippingCopilotConfig config;
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	@Named("copilotExecutor")
	private ScheduledExecutorService executorService;
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
	private ApiRequestHandler apiRequestHandler;
	@Inject
	private AccountStatusManager accountStatusManager;
	@Inject
	private SuggestionController suggestionController;
	@Inject
	private SuggestionManager suggestionManager;
	@Inject
	private WebHookController webHookController;
	@Inject
	private KeybindHandler keybindHandler;
	@Inject
	private CopilotLoginController copilotLoginController;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private CopilotLoginManager copilotLoginManager;
	@Inject
	private HighlightController highlightController;
	@Inject
	private GameUiChangesHandler gameUiChangesHandler;
	@Inject
	private OsrsLoginManager osrsLoginManager;
	@Inject
	private FlipManager flipManager;
	@Inject
	private SessionManager sessionManager;
	@Inject
	private GrandExchangeUncollectedManager grandExchangeUncollectedManager;
	@Inject
	private TransactionManager transactionManager;
	@Inject
	private OfferManager offerManager;
	@Inject
	private TooltipController tooltipController;
  	@Inject
	private MenuHandler menuHandler;
    @Inject
	private GeHistoryTabController geHistoryTabController;
	@Inject
	private FlipsDialogController flipsDialogController;
	@Inject
	private SuggestionPreferencesManager preferencesManager;
	@Inject
	private DumpsStreamController dumpsStreamController;
	@Inject
	private GrandExchangeOpenRS grandExchangeOpenRS;
	@Inject
	private OsrsLoginRS osrsLoginRS;
	@Inject
	private FlippingCopilotConfigRS configRS;

	// We use our own ThreadPool since the default ScheduledExecutorService only has a single thread and we don't want to block it
	@Provides
	@Singleton
	@Named("copilotExecutor")
	public ScheduledExecutorService provideCustomExecutorService() {
		return Executors.newScheduledThreadPool(2);
	}

	@Provides
	@Singleton
	public ExecutorService provideExecutorService(@Named("copilotExecutor") ScheduledExecutorService scheduledExecutor) {
		return scheduledExecutor;
	}

	private MainPanel mainPanel;
	private StatsPanelV2 statsPanel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception {
		Persistance.setUp(gson);
		// seems we need to delay instantiating the UI till here as otherwise the panels look different
		mainPanel = injector.getInstance(MainPanel.class);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon-small.png");
		navButton = NavigationButton.builder()
				.tooltip("Flipping Copilot")
				.icon(icon)
				.priority(3)
				.panel(mainPanel)
				.build();
		clientToolbar.addNavigation(navButton);
		apiRequestHandler.setCopilotLoginController(copilotLoginController);
		copilotLoginController.setLoginPanel(mainPanel.loginPanel);
		copilotLoginController.setMainPanel(mainPanel);
		suggestionController.setCopilotPanel(mainPanel.copilotPanel);
		suggestionController.setMainPanel(mainPanel);
		suggestionController.setLoginPanel(mainPanel.loginPanel);
		suggestionController.setSuggestionPanel(mainPanel.copilotPanel.suggestionPanel);
		grandExchangeCollectHandler.setSuggestionPanel(mainPanel.copilotPanel.suggestionPanel);
		statsPanel = mainPanel.copilotPanel.statsPanel;

		mainPanel.refresh();

		if(osrsLoginManager.getInvalidStateDisplayMessage() == null) {
			flipManager.setIntervalAccount(null);
			flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
		}
		flipsDialogController.initDialog(SwingUtilities.getWindowAncestor(mainPanel));
		executorService.scheduleAtFixedRate(() ->
			clientThread.invoke(() -> {
				boolean loginValid = osrsLoginManager.isValidLoginState();
				if (loginValid) {
					AccountStatus accStatus = accountStatusManager.getAccountStatus();
					boolean isFlipping = accStatus != null && accStatus.currentlyFlipping();
					long cashStack = accStatus == null ? 0 : accStatus.currentCashStack();
					if(sessionManager.updateSessionStats(isFlipping, cashStack)) {
						mainPanel.copilotPanel.statsPanel.refresh(false, copilotLoginManager.isLoggedIn() && osrsLoginManager.isValidLoginState());
					}
				}
			})
		, 2000, 1000, TimeUnit.MILLISECONDS);
	}

	@Override
	protected void shutDown() throws Exception {
		offerManager.saveAll();
		highlightController.removeAll();
		clientToolbar.removeNavigation(navButton);
		if(copilotLoginManager.isLoggedIn()) {
			String displayName = osrsLoginManager.getLastDisplayName();
			Integer accountId = copilotLoginManager.getAccountId(displayName);
			if (accountId != null && accountId != -1) {
				webHookController.sendMessage(flipManager.calculateStats(sessionManager.getCachedSessionData().startTime, accountId), sessionManager.getCachedSessionData(), displayName, false);
			}
		}
		keybindHandler.unregister();
	}

	@Provides
	public FlippingCopilotConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(FlippingCopilotConfig.class);
	}

	//---------------------------- Event Handlers ----------------------------//
	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
		offerEventHandler.onGrandExchangeOfferChanged(event);
		clientThread.invokeLater(() -> highlightController.redraw());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		if (event.getContainerId() == InventoryID.INV && grandExchange.isOpen()) {
			suggestionManager.setSuggestionNeeded(true);
//			log.debug("inventory change item {} qty {}", lastItems, event.getItemContainer().getItems());
			clientThread.invokeLater(() -> highlightController.redraw());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		suggestionController.onGameTick();
		offerEventHandler.onGameTick();
		grandExchangeOpenRS.set(grandExchange.isOpen());
		osrsLoginRS.set(osrsLoginRS.get().nexState(client));
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
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		menuHandler.injectCopilotPriceGraphMenuEntry(event);
		menuHandler.injectConfirmMenuEntry(event);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() == 383) {
			clientThread.invokeLater(() -> {
				geHistoryTabController.onGeHistoryTabOpened();
			});
		}
		gameUiChangesHandler.onWidgetLoaded(event);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		if (event.getGroupId() == 383) {
			geHistoryTabController.onGeHistoryTabClosed();
		}
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
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
				sessionManager.reset();
				suggestionManager.reset();
				osrsLoginManager.reset();
				geHistoryTabController.onGeHistoryTabClosed();
				accountStatusManager.reset();
				grandExchangeUncollectedManager.reset();
				statsPanel.refresh(true, copilotLoginManager.isLoggedIn() && osrsLoginManager.isValidLoginState());
				osrsLoginRS.set(osrsLoginRS.get().nexState(client));
				mainPanel.refresh();
				break;
			case LOGGING_IN:
			case HOPPING:
			case CONNECTION_LOST:
				osrsLoginManager.setLastLoginTick(client.getTickCount());
				osrsLoginRS.set(osrsLoginRS.get().nexState(client));
				break;
			case LOGGED_IN:
				// we want to update the flips panel on login but unfortunately the display name
				// is not available immediately so schedule what we need to do here for in the future
				// todo: move to just using the accountHash which is available immediately to simply things
				clientThread.invokeLater(() -> {
					if (client.getGameState() != GameState.LOGGED_IN) {
						return true;
					}
					final String name = osrsLoginManager.getPlayerDisplayName();
					if(name == null) {
						return false;
					}
					statsPanel.resetIntervalDropdownToSession();
					Integer accountId = copilotLoginManager.getAccountId(name);
					if (accountId != null && accountId != -1) {
						flipManager.setIntervalAccount(accountId);
					} else {
						flipManager.setIntervalAccount(null);
					}
					flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
					statsPanel.refresh(true, copilotLoginManager.isLoggedIn()  && osrsLoginManager.isValidLoginState());
					mainPanel.refresh();
					if(copilotLoginManager.isLoggedIn()) {
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
		if(copilotLoginManager.isLoggedIn()) {
			String displayName = osrsLoginManager.getLastDisplayName();
			Integer accountId = copilotLoginManager.getAccountId(displayName);
			if (accountId != null && accountId != -1) {
				webHookController.sendMessage(flipManager.calculateStats(sessionManager.getCachedSessionData().startTime, accountId), sessionManager.getCachedSessionData(), displayName, false);
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (event.getGroup().equals("flippingcopilot")) {
			log.debug("copilot config changed event received");
			configRS.forceSet(config);
			if (event.getKey().equals("profitAmountColor") || event.getKey().equals("lossAmountColor")) {
				mainPanel.copilotPanel.statsPanel.refresh(true, copilotLoginManager.isLoggedIn() && osrsLoginManager.isValidLoginState());
			}
			if (event.getKey().equals("suggestionHighlights")) {
				clientThread.invokeLater(() -> highlightController.redraw());
			}
		}
	}
}
