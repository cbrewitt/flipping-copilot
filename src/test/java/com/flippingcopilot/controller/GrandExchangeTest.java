package com.flippingcopilot.controller;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class GrandExchangeTest {
    @Test
    public void readsLongSetupPriceWithoutFallingBack() {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
                new Class<?>[]{Client.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getVarpLongValue")) {
                        assertEquals(1043, args[0]);
                        return 3_000_000_123L;
                    }
                    throw new AssertionError(method.getName());
                });

        assertEquals(3_000_000_123L, new GrandExchange(client).getOfferPrice());
    }

    @Test
    public void fallsBackToPriceVarbitWhenVarpIsStillInt() {
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
                new Class<?>[]{Client.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getVarpLongValue":
                            assertEquals(1043, args[0]);
                            throw new IllegalArgumentException("varp 1043 is an int");
                        case "getVarbitValue":
                            assertEquals(VarbitID.GE_NEWOFFER_PRICE, args[0]);
                            return Integer.MAX_VALUE;
                        default:
                            throw new AssertionError(method.getName());
                    }
                });

        assertEquals((long) Integer.MAX_VALUE, new GrandExchange(client).getOfferPrice());
    }
}
