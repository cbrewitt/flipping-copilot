//package com.flippingcopilot.controller;
//
//import net.runelite.api.GrandExchangeOffer;
//import net.runelite.api.GrandExchangeOfferState;
//import net.runelite.api.events.GrandExchangeOfferChanged;
//import org.junit.Test;
//
//import java.util.ArrayList;
//
//public class OfferEventFilterTest {
//
//    @Test
//    public void testShouldProcessOnLogin() {
//        OfferEventFilter filter = new OfferEventFilter();
//
//        ArrayList<GrandExchangeOfferChanged> events = new ArrayList<>();
//        ArrayList<Boolean> expectedReturnValues = new ArrayList<>();
//
//        events.add(mockOfferEvent(0, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(1, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(2, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(3, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(4, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(5, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(6, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(7, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(false);
//
//        events.add(mockOfferEvent(0, 6924, 12, 35, 3749999, 0, GrandExchangeOfferState.SELLING));
//        expectedReturnValues.add(true);
//        events.add(mockOfferEvent(1, 10034, 0, 125726, 1312, 0, GrandExchangeOfferState.SELLING));
//        expectedReturnValues.add(true);
//        events.add(mockOfferEvent(2, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(true);
//        events.add(mockOfferEvent(3, 4207, 0, 439, 390000, 0, GrandExchangeOfferState.SELLING));
//        expectedReturnValues.add(true);
//        events.add(mockOfferEvent(4, 6737, 0, 15, 2552692, 0, GrandExchangeOfferState.SELLING));
//        expectedReturnValues.add(true);
//        events.add(mockOfferEvent(5, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(true);
//        events.add(mockOfferEvent(6, 6916, 35, 49, 3320102, 35*3320102, GrandExchangeOfferState.SELLING));
//        expectedReturnValues.add(true);
//        events.add(mockOfferEvent(7, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY));
//        expectedReturnValues.add(true);
//
//        events.add(mockOfferEvent(0, 6924, 12, 35, 3749999, 0, GrandExchangeOfferState.SELLING));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(1, 10034, 0, 125726, 1312, 0, GrandExchangeOfferState.SELLING));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(3, 4207, 0, 439, 390000, 0, GrandExchangeOfferState.SELLING));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(4, 6737, 0, 15, 2552692, 0, GrandExchangeOfferState.SELLING));
//        expectedReturnValues.add(false);
//        events.add(mockOfferEvent(6, 6916, 35, 49, 3320102, 35*3320102, GrandExchangeOfferState.SELLING));
//        expectedReturnValues.add(false);
//
//        for (int i = 0; i < events.size(); i++) {
//            assert filter.shouldProcess(events.get(i)) == expectedReturnValues.get(i);
//        }
//    }
//
//    @Test
//    public void testShouldProcessNewBuyOffer() {
//        OfferEventFilter filter = new OfferEventFilter();
//        GrandExchangeOfferChanged event1 = mockOfferEvent(2, 379, 0, 223, 207, 0, GrandExchangeOfferState.BUYING);
//        assert filter.shouldProcess(event1);
//        GrandExchangeOfferChanged event2 = mockOfferEvent(2, 379, 0, 223, 207, 0, GrandExchangeOfferState.BUYING);
//        assert !filter.shouldProcess(event2);
//    }
//
//    @Test
//    public void testLogoutLogin() {
//        OfferEventFilter filter = new OfferEventFilter();
//        GrandExchangeOfferChanged event = mockOfferEvent(2, 379, 0, 223, 207, 0, GrandExchangeOfferState.BUYING);
//        assert filter.shouldProcess(event);
//
//
//        for (int i = 0; i < 8; i++) {
//            event = mockOfferEvent(i, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY);
//            assert !filter.shouldProcess(event);
//        }
//
//        for (int i = 0; i < 8; i++) {
//            if (i == 2) {
//                event = mockOfferEvent(2, 379, 0, 223, 207, 0, GrandExchangeOfferState.BUYING);
//                assert !filter.shouldProcess(event);
//            } else {
//                event = mockOfferEvent(i, 0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY);
//                assert filter.shouldProcess(event);
//            }
//        }
//
//        event = mockOfferEvent(2, 379, 0, 223, 207, 0, GrandExchangeOfferState.BUYING);
//        assert !filter.shouldProcess(event);
//    }
//
//    static GrandExchangeOfferChanged mockOfferEvent(int slot, int itemId, int quantitySold, int totalQuantity,
//                                                    int price, int spent, GrandExchangeOfferState state) {
//        GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
//        event.setSlot(slot);
//        event.setOffer(new GrandExchangeOffer() {
//            @Override
//            public int getQuantitySold() {
//                return quantitySold;
//            }
//
//            @Override
//            public int getItemId() {
//                return itemId;
//            }
//
//            @Override
//            public int getTotalQuantity() {
//                return totalQuantity;
//            }
//
//            @Override
//            public int getPrice() {
//                return price;
//            }
//
//            @Override
//            public int getSpent() {
//                return spent;
//            }
//
//            @Override
//            public GrandExchangeOfferState getState() {
//                return state;
//            }
//        });
//        return event;
//    }
//
//
//}
