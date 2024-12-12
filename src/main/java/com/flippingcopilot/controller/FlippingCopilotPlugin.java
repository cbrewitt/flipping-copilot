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
import net.runelite.client.input.KeyManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.flippingcopilot.util.AtomicReferenceUtils.ifBothPresent;
import static com.flippingcopilot.util.AtomicReferenceUtils.ifPresent;

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
	public KeyManager keyManager;
	@Inject
	public FlippingCopilotConfig config;
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
	public final AccountStatus accountStatus = new AccountStatus();
	public SuggestionHandler suggestionHandler;
	public GrandExchange grandExchange;
	public OsrsLoginHandler osrsLoginHandler;

	GameUiChangesHandler gameUiChangesHandler;
	GrandExchangeCollectHandler grandExchangeCollectHandler;
	OfferEventFilter offerEventFilter;

	public final AtomicReference<FlipManager> flipManager = new AtomicReference<>(null);
	public final AtomicReference<TransactionManger> transactionManager = new AtomicReference<>(null);
	public final AtomicReference<SessionManager> sessionManager = new AtomicReference<>(null);

	MainPanel mainPanel;
	public CopilotLoginController copilotLoginController;

	@Inject
	private WebHookController webHookController;

	public HighlightController highlightController;
	GePreviousSearch gePreviousSearch;
	KeybindHandler keybindHandler;
	public OfferHandler offerHandler;

	@Override
	protected void startUp() throws Exception {
		Persistance.setUp(gson);
		apiRequestHandler = new ApiRequestHandler(gson, okHttpClient);
		mainPanel = new MainPanel(config, flipManager, sessionManager, webHookController);
		suggestionHandler = new SuggestionHandler(this);
		grandExchange = new GrandExchange(client);
		grandExchangeCollectHandler = new GrandExchangeCollectHandler(accountStatus, suggestionHandler);
		offerEventFilter = new OfferEventFilter();
		osrsLoginHandler = new OsrsLoginHandler(this);
		gameUiChangesHandler = new GameUiChangesHandler(this);
		copilotLoginController = new CopilotLoginController(() -> mainPanel.renderLoggedInView(), this);
		highlightController = new HighlightController(this);
		gePreviousSearch = new GePreviousSearch(this);
		keybindHandler = new KeybindHandler(this);
		offerHandler = new OfferHandler(this);
		mainPanel.init(this);
		setUpNavButton();
		setUpLogin();
		osrsLoginHandler.init();
		executorService.scheduleAtFixedRate(() -> {
			boolean isFlipping = osrsLoginHandler.isLoggedIn() && accountStatus.currentlyFlipping();
			ifPresent(sessionManager, s -> s.updateSessionStats(isFlipping, accountStatus.currentCashStack()));
		}, 2000, 1000, TimeUnit.MILLISECONDS);
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
		boolean isLoggedIn = loginResponse != null && !loginResponse.error && loginResponse.jwt != null;
		if (isLoggedIn) {
			apiRequestHandler.setLoginResponse(loginResponse);
			mainPanel.renderLoggedInView();
			copilotLoginController.setLoggedIn(true);
			ifPresent(this.flipManager, FlipManager::cancelFlipLoading);
			FlipManager fm = new FlipManager(this.apiRequestHandler, this.executorService, () -> this.mainPanel.copilotPanel.statsPanel.updateStatsAndFlips(true));
			this.flipManager.set(fm);
			ifPresent(this.sessionManager, i -> {
				fm.setIntervalDisplayName(i.getDisplayName());
				fm.setIntervalStartTime(i.getData().startTime);
			});
		}
	}


	@Override
	protected void shutDown() throws Exception {
		highlightController.removeAll();
		clientToolbar.removeNavigation(navButton);
		ifBothPresent(sessionManager, flipManager, (sm, fm) -> {
			accountStatus.onLogout(sm.getDisplayName());
			webHookController.sendMessage(fm.calculateStats(sm.getData().startTime, sm.getDisplayName()), sm.getData(), sm.getDisplayName(), false);
		});
		keybindHandler.unregister();
	}

	@Provides
	public FlippingCopilotConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(FlippingCopilotConfig.class);
	}

	private void processTransaction(Transaction transaction) {
		ifPresent(transactionManager, i -> {
			long profit = i.addTransaction(transaction);
			if (grandExchange.isHomeScreenOpen() && profit != 0) {
				new GpDropOverlay(this, profit, transaction.getBoxId());
			}
		});
	}

	//---------------------------- Event Handlers ----------------------------//
	private final List<GrandExchangeOfferChanged> queuedOfferEvents = new ArrayList<>();

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
		// we need to queue any offer events that arrive too early until the loginHandler is fully initialised
		queuedOfferEvents.add(event);
		if (osrsLoginHandler.isInvalidState()) {
			log.debug("queueing GE offer event until logged in user is initialised");
		} else {
			processQueuedOfferEvents();
		}
	}

	public void processQueuedOfferEvents() {
		while (!queuedOfferEvents.isEmpty()) {
			GrandExchangeOfferChanged event = queuedOfferEvents.remove(0);
			if (event.getSlot() == 0) {
				log.info("received box 0 offer event: {}, {}, {}/{}", event.getOffer().getState(), event.getOffer().getItemId(), event.getOffer().getQuantitySold(), event.getOffer().getTotalQuantity());
			}
			if (offerEventFilter.shouldProcess(event)) {
				if (event.getSlot() == 0) {
					log.info("box 0 is: processing transaction");
				}
				Transaction transaction = accountStatus.updateOffers(event,
						offerHandler.getLastViewedSlotItemId(),
						offerHandler.getLastViewedSlotItemPrice(),
						offerHandler.getLastViewedSlotPriceTime());
				if (transaction != null) {
					processTransaction(transaction);
				}
				suggestionHandler.setSuggestionNeeded(true);
			}
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
		int gameTick = client.getTickCount();
		if (gameTick % 2 == 0 && gameTick > osrsLoginHandler.getValidStateGameTick()+5) {
			accountStatus.selfCorrectOfferStates(client.getGrandExchangeOffers());
		}
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
		log.debug("client shutdown event received");
		ifBothPresent(sessionManager, flipManager, (sm, fm) -> {
			accountStatus.onLogout(sm.getDisplayName());
			webHookController.sendMessage(fm.calculateStats(sm.getData().startTime, sm.getDisplayName()), sm.getData(), sm.getDisplayName(), false);
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (event.getGroup().equals("flippingcopilot")) {
			log.debug("copilot config changed event received");
			if (event.getKey().equals("profitAmountColor") || event.getKey().equals("lossAmountColor")) {
				mainPanel.copilotPanel.statsPanel.updateStatsAndFlips(true);
			}
			if (event.getKey().equals("suggestionHighlights")) {
				clientThread.invokeLater(() -> highlightController.redraw());
			}
		}
	}
}