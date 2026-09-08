package copilot.controller;

import java.util.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;

import javax.inject.*;

@Slf4j
@Singleton
@lombok.RequiredArgsConstructor(onConstructor_ = @Inject)
public class PortfolioController {
    // dependencies
    private final Client client;
    private final Items items;

    public Set<Integer> getActiveGrandExchangeItemIdsForSync() {
        Set<Integer> itemIds = new LinkedHashSet<>();
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null) { return itemIds; }
        for (GrandExchangeOffer offer : offers) {
            if (offer == null) { continue; }
            var state = offer.getState();
            if (state != GrandExchangeOfferState.EMPTY) {
                int itemId = offer.getItemId();
                if (itemId > 0) { itemIds.add(items.toUnnotedItemId(itemId)); }
            }
        }
        return itemIds;
    }

}
