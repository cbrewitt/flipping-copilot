package com.flippingcopilot.model;

import com.google.gson.Gson;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class PriceMigrationTest {
    @Test
    public void offerPricesAboveMaxCashSurviveAdaptersPersistenceAndEncoding() throws IOException {
        assertOfferRoundTrip(3_000_000_000L, 6_000_000_000L);
    }

    @Test
    public void cumulativeSpentAboveMaxCashSurvivesWithAnIntSizedUnitPrice() throws IOException {
        assertOfferRoundTrip(1_500_000_000L, 3_000_000_000L);
    }

    @Test
    public void setupStateComparesFullPriceAboveMaxCash() {
        long price = 5_000_000_000L;
        Suggestion suggestion = new Suggestion();
        suggestion.setType(SuggestionType.BUY);
        suggestion.setItemId(4151);
        suggestion.setPrice(price);
        suggestion.setQuantity(2);

        GEOfferScreenSetupOfferState state = new GEOfferScreenSetupOfferState("buy", 4151, price, 2, false);
        assertTrue(state.offerDetailsCorrect(suggestion));
        suggestion.setPrice((int) price);
        assertFalse(state.offerDetailsCorrect(suggestion));
    }

    private void assertOfferRoundTrip(long price, long spent) throws IOException {
        GrandExchangeOffer source = (GrandExchangeOffer) Proxy.newProxyInstance(
                GrandExchangeOffer.class.getClassLoader(), new Class<?>[]{GrandExchangeOffer.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getState": return GrandExchangeOfferState.BUYING;
                        case "getItemId": return 4151;
                        case "getPrice": return price;
                        case "getSpent": return spent;
                        case "getTotalQuantity": return 3;
                        case "getQuantitySold": return 2;
                        default: throw new AssertionError(method.getName());
                    }
                });

        Offer offer = Offer.fromRunelite(source, 0);
        assertEquals(price, offer.getPrice());
        assertEquals(spent, offer.getAmountSpent());
        assertEquals(price * 3, offer.cashStackGpValue());

        SavedOffer saved = SavedOffer.fromGrandExchangeOffer(source);
        Gson gson = new Gson();
        SavedOffer restored = gson.fromJson(gson.toJson(saved), SavedOffer.class);
        assertEquals(price, restored.getPrice());
        assertEquals(spent, restored.getSpent());

        CodedInputStream input = CodedInputStream.newInstance(offer.encodeProto());
        Long encodedPrice = null;
        Long encodedSpent = null;
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 3: encodedPrice = input.readInt64(); break;
                case 8: encodedSpent = input.readInt64(); break;
                default: input.skipField(tag);
            }
        }
        assertEquals(Long.valueOf(price), encodedPrice);
        assertEquals(Long.valueOf(spent), encodedSpent);
    }
}
