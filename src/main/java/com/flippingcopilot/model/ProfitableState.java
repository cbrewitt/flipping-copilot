package com.flippingcopilot.model;

/**
 * Represents whether a GE offer is listed at a profitable price.
 */
public enum ProfitableState {
    /**
     * Offer is at a profitable price (green border).
     * For buys: offer price <= copilot buy price
     * For sells: offer price >= copilot sell price
     */
    PROFITABLE,
    
    /**
     * Offer is not at a profitable price (red border).
     * For buys: offer price > copilot buy price
     * For sells: offer price < copilot sell price
     */
    NOT_PROFITABLE,
    
    /**
     * Unable to determine profitability (blue/default border).
     * No copilot price data available or slot is empty.
     */
    UNKNOWN
}
