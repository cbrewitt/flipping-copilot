package com.flippingcopilot.model;

import com.flippingcopilot.util.GeTax;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import java.util.Comparator;
import java.util.UUID;

@Data
public class FlipV2 {

    public static UUID MAX_UUID = new UUID(-1L, -1L);
    public static UUID MIN_UUID = new UUID(0L, 0L);

    @SerializedName("id")
    private UUID id;

    @SerializedName("account_id")
    private int accountId;

    @SerializedName("item_id")
    private int itemId;

    @SerializedName("item_name")
    private String itemName;

    @SerializedName("opened_time")
    private int openedTime;

    @SerializedName("opened_quantity")
    private int openedQuantity;

    @SerializedName("spent")
    private long spent;

    @SerializedName("closed_time")
    private int closedTime;

    @SerializedName("closed_quantity")
    private int closedQuantity;

    @SerializedName("received_post_tax")
    private long receivedPostTax;

    @SerializedName("profit")
    private long profit;

    @SerializedName("tax_paid")
    private long taxPaid;

    @SerializedName("is_closed")
    private boolean isClosed;

    private String accountDisplayName;

    public long calculateProfit(Transaction transaction) {
        long amountToClose = Math.min(openedQuantity - closedQuantity, transaction.getQuantity());
        if(amountToClose <= 0 ){
            return 0;
        }
        long gpOut = (spent * amountToClose) / openedQuantity;
        int sellPrice  = transaction.getAmountSpent() / transaction.getQuantity();
        int sellPricePostTax = GeTax.getPostTaxPrice(transaction.getItemId(), sellPrice);
        long gpIn = amountToClose * sellPricePostTax;
        return gpIn - gpOut;
    }

    public long getAvgBuyPrice() {
        if (spent == 0) {
            return 0;
        }
        return spent / openedQuantity ;
    }

    public long getAvgSellPrice() {
        if (receivedPostTax == 0) {
            return 0;
        }
        return (receivedPostTax  + taxPaid) / closedQuantity;
    }
}
