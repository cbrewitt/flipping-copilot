package com.flippingcopilot.controller;

import com.flippingcopilot.model.AccountStatus;
import com.flippingcopilot.model.LoginResponse;
import com.flippingcopilot.model.Transaction;
import com.flippingcopilot.ui.GePreviousSearch;
import com.flippingcopilot.ui.GpDropOverlay;
import com.flippingcopilot.ui.MainPanel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.Notifier;
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
import okhttp3.*;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@PluginDescriptor(
		name = "Flipping Copilot",
		description = "Your AI assistant for trading"
)
public class FlippingCopilotPlugin extends Plugin {
	@Inject
	@Getter
	public Client client;
	@Inject
	@Getter
	ClientThread clientThread;
	@Inject
	FlippingCopilotConfig config;
	@Inject
	ScheduledExecutorService executorService;
	@Inject
	ClientToolbar clientToolbar;
	@Inject
	Notifier notifier;
	@Inject
	public OverlayManager overlayManager;
	@Inject
	public Gson gson;
	@Inject
	public OkHttpClient okHttpClient;
	public ApiRequestHandler apiRequestHandler;
	NavigationButton navButton;
	public AccountStatus accountStatus;
	public SuggestionHandler suggestionHandler;
	public GrandExchange grandExchange;
	public OsrsLoginHandler osrsLoginHandler;

	GameUiChangesHandler gameUiChangesHandler;
	GrandExchangeCollectHandler grandExchangeCollectHandler;
	OfferEventFilter offerEventFilter;
	FlipTracker flipTracker;
	MainPanel mainPanel;
	public CopilotLoginController copilotLoginController;
	WebHookController webHookController;
	public HighlightController highlightController;
	GePreviousSearch gePreviousSearch;

	@Override
	protected void startUp() throws Exception {
		Persistance.setUp(gson);
		flipTracker = new FlipTracker();
		apiRequestHandler = new ApiRequestHandler(gson, okHttpClient);
		mainPanel = new MainPanel(config);
		suggestionHandler = new SuggestionHandler(this);
		accountStatus = new AccountStatus();
		grandExchange = new GrandExchange(client);
		grandExchangeCollectHandler = new GrandExchangeCollectHandler(accountStatus, suggestionHandler);
		offerEventFilter = new OfferEventFilter();
		osrsLoginHandler = new OsrsLoginHandler(this);
		gameUiChangesHandler = new GameUiChangesHandler(this);
		copilotLoginController = new CopilotLoginController(() -> mainPanel.renderLoggedInView(), this);
		webHookController = new WebHookController(this);
		highlightController = new HighlightController(this);
		gePreviousSearch = new GePreviousSearch(this);
		mainPanel.init(this);
		setUpNavButton();
		setUpLogin();
		osrsLoginHandler.init();
	}

	private void setUpNavButton() {
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon-small.png");
		navButton = NavigationButton.builder()
				.tooltip("Flipping Copilot")
				.icon(icon)
				.priority(3)
				.panel(mainPanel)
				.build();
		clientToolbar.addNavigation(navButton);
	}

	private void setUpLogin() throws IOException {
		LoginResponse loginResponse = Persistance.loadLoginResponse();
		boolean isLoggedIn = loginResponse != null && !loginResponse.error;
		if (isLoggedIn) {
			apiRequestHandler.setLoginResponse(loginResponse);
			mainPanel.renderLoggedInView();
			copilotLoginController.setLoggedIn(true);
		}
	}

	@Override
	protected void shutDown() throws Exception {
		highlightController.removeAll();
		clientToolbar.removeNavigation(navButton);
		Persistance.saveLoginResponse(apiRequestHandler.getLoginResponse());
		webHookController.sendMessage();
	}

	@Provides
	FlippingCopilotConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(FlippingCopilotConfig.class);
	}

	private void processTransaction(Transaction transaction) {
		long transactionProfit = flipTracker.addTransaction(transaction);
		if (transactionProfit != 0) {
			mainPanel.copilotPanel.statsPanel.updateFlips(flipTracker, client);
			if (grandExchange.isHomeScreenOpen()) {
				new GpDropOverlay(this, transactionProfit, transaction.getBoxId());
			}
		}
	}

	//---------------------------- Event Handlers ----------------------------//

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
		if (offerEventFilter.shouldProcess(event)) {
			Transaction transaction = accountStatus.updateOffers(event);
			if(transaction != null) {
				processTransaction(transaction);
			}
			suggestionHandler.setSuggestionNeeded(true);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
			accountStatus.handleInventoryChanged(event, client);
			suggestionHandler.setSuggestionNeeded(true);
		}
	}
	@Subscribe
	public void onGameTick(GameTick event) {
		suggestionHandler.onGameTick();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		int slot = grandExchange.getOpenSlot();
		grandExchangeCollectHandler.handleCollect(event, slot);
		gameUiChangesHandler.handleMenuOptionClicked(event);
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
		osrsLoginHandler.handleGameStateChanged(event);
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event) {
		gameUiChangesHandler.onVarClientIntChanged(event);
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown clientShutdownEvent) {
		webHookController.sendMessage();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (event.getGroup().equals("flippingcopilot")) {
			if (event.getKey().equals("profitAmountColor") || event.getKey().equals("lossAmountColor")) {
				mainPanel.copilotPanel.statsPanel.updateFlips(flipTracker, client);
			}
			if (event.getKey().equals("suggestionHighlights")) {
				clientThread.invokeLater(() -> highlightController.redraw());
			}
		}
	}
}