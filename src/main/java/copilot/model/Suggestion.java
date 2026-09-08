package copilot.model;

import copilot.ui.graph.model.Data;
import com.google.protobuf.*;
import copilot.ui.graph.model.*;
import copilot.util.*;
import com.google.gson.annotations.*;
import lombok.*;
import lombok.extern.slf4j.*;

import java.text.*;
import java.time.*;
import java.io.*;
import java.util.*;

@Setter
@Getter
@AllArgsConstructor
@ToString
@NoArgsConstructor
@Slf4j
public class Suggestion {
    public SuggestionType type;
    public int boxId, itemId;
    public long price;
    public int quantity;
    public String name;
    public int id;
    public String message = "";
    public Double expectedProfit, expectedDuration;
    @SerializedName("is_hold")
    private boolean isHold;
    public Map<Integer, Integer> bankItems;
    public List<PortfolioItem> portfolioItems;
    private Data graphData;
    public Instant timeIssued;

    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PortfolioItem {
        public int itemId;

        // Per-portfolio breakdowns. Only portfolio_id 0 (COFLIP_PORTFOLIO) and 1 (PERSONAL_PORTFOLIO)
        // count as "in portfolio" (getAmount / getSellValue / getBuySpend / getHeldMinutes).
        // Ghost (portfolio_id -1) is tracked separately and excluded from those totals; it only
        // surfaces as a fallback when computing per-unit market prices for items the user holds
        // client-side but has no visible portfolio/personal entry for.
        public int portfolioAmount;
        public long portfolioSellValue, portfolioBuySpend;
        public int portfolioHeldMinutes, personalAmount;
        public long personalSellValue, personalBuySpend;
        public int personalHeldMinutes, ghostAmount;
        public long ghostSellValue, ghostBuySpend;
        public int ghostHeldMinutes;

        public int getAmount() { return portfolioAmount + personalAmount; }

        public long getSellValue() { return portfolioSellValue + personalSellValue; }

        public long getBuySpend() { return portfolioBuySpend + personalBuySpend; }

        public int getHeldMinutes() { return Math.max(portfolioHeldMinutes, personalHeldMinutes); }

        public long getPostTaxSellUnitPrice() {
            int amt = getAmount();
            if (amt > 0) { return getSellValue() / amt; }
            if (ghostAmount > 0) { return ghostSellValue / ghostAmount; }
            return 0L;
        }

        public long getUnitBuyPrice() {
            int amt = getAmount();
            if (amt > 0) { return getBuySpend() / amt; }
            if (ghostAmount > 0) { return ghostBuySpend / ghostAmount; }
            return 0L;
        }

        public static PortfolioItem decodeProto(CodedInputStream input) throws IOException {
            var item = new PortfolioItem();
            for (int tag; (tag = input.readTag()) != 0;) {
                int field = WireFormat.getTagFieldNumber(tag);
                switch (field) {
                    case 1: item.itemId = input.readInt32(); break;
                    case 7: item.portfolioAmount = input.readInt32(); break;
                    case 8: item.portfolioSellValue = input.readInt64(); break;
                    case 9: item.portfolioBuySpend = input.readInt64(); break;
                    case 10: item.portfolioHeldMinutes = input.readInt32(); break;
                    case 11: item.personalAmount = input.readInt32(); break;
                    case 12: item.personalSellValue = input.readInt64(); break;
                    case 13: item.personalBuySpend = input.readInt64(); break;
                    case 14: item.personalHeldMinutes = input.readInt32(); break;
                    case 15: item.ghostAmount = input.readInt32(); break;
                    case 16: item.ghostSellValue = input.readInt64(); break;
                    case 17: item.ghostBuySpend = input.readInt64(); break;
                    case 18: item.ghostHeldMinutes = input.readInt32(); break;
                    default:
                        // Skips deprecated legacy aggregates (2-6).
                        input.skipField(tag);
                }
            }
            return item;
        }
    }

    public volatile Instant dumpAlertReceived = Instant.now();
    public volatile boolean isDumpAlert;
    public volatile int actionedTick = -1;

    public boolean equals(Suggestion other) {
        return type == other.type
                && itemId == other.itemId
                && name.equals(other.name);
    }

    public boolean isWaitSuggestion() { return type == SuggestionType.WAIT; }

    public boolean isAbortSuggestion() { return type == SuggestionType.ABORT; }

    public boolean isBuySuggestion() { return type == SuggestionType.BUY || type == SuggestionType.MODIFY_BUY; }

    public boolean isSellSuggestion() { return type == SuggestionType.SELL || type == SuggestionType.MODIFY_SELL; }

    public boolean isModifySuggestion() {
        return type == SuggestionType.MODIFY_BUY || type == SuggestionType.MODIFY_SELL;
    }

    public String offerType() {
        if (isBuySuggestion()) { return "buy"; }
        if (isSellSuggestion()) { return "sell"; }
        return null;
    }

    public boolean isRecentUnActionedDumpAlert() {
        return isDumpAlert && actionedTick == -1 && dumpAlertReceived.isAfter(Instant.now().minusSeconds(10));
    }

    public boolean isBuyDumpSuggestion() { return isDumpAlert && type == SuggestionType.BUY; }

    public String toMessage() {
        String prefix = isDumpAlert ? "DUMP ALERT!! " : "Flipping Copilot: ";
        if (type == null) return prefix + "Unknown suggestion type";
        switch (type) {
            case BUY:
            case SELL:
            case MODIFY_BUY:
            case MODIFY_SELL:
                String action;
                if (isModifySuggestion()) { action = "Modify " + (isBuySuggestion() ? "buy" : "sell") + " offer for"; } else {
                    action = type == SuggestionType.SELL ? "Sell" : isHold ? "Buy and hold" : "Buy";
                }
                var formatter = NumberFormat.getNumberInstance();
                return prefix + String.format("%s %s %s %s %s gp", action, formatter.format(quantity), name,
                        isModifySuggestion() ? "to" : "for", formatter.format(price));
            case ABORT: return prefix + "Abort " + name;
            case WAIT: return prefix + "Wait";
            default: return prefix + "Unknown suggestion type";
        }
    }

    public static Suggestion decodeProto(byte[] bytes) {
        if (bytes == null || bytes.length == 0) { return null; }

        var suggestion = new Suggestion();
        try {
            var input = CodedInputStream.newInstance(bytes);
            for (int tag; (tag = input.readTag()) != 0;) {

                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                switch (fieldNumber) {
                    case 1: suggestion.timeIssued = ProtoUtils.decodeTimestamp(input); break;
                    case 2: suggestion.boxId = input.readInt32(); break;
                    case 3: suggestion.type = SuggestionType.fromProtoInt(input.readInt32()); break;
                    case 4: suggestion.itemId = input.readInt32(); break;
                    case 5: suggestion.quantity = clampToInt(input.readInt64()); break;
                    case 6: suggestion.price = input.readInt64(); break;
                    case 7: suggestion.id = input.readInt32(); break;
                    case 11: suggestion.message = input.readString(); break;
                    case 12: suggestion.expectedProfit = input.readDouble(); break;
                    case 14: suggestion.isDumpAlert = input.readBool(); break;
                    case 15: suggestion.name = input.readString(); break;
                    case 16: suggestion.expectedDuration = input.readDouble(); break;
                    case 17:
                        if (suggestion.bankItems == null) { suggestion.bankItems = new HashMap<>(); }
                        int mapLength = input.readRawVarint32(), mapLimit = input.pushLimit(mapLength);
                        int key = 0, value = 0;
                        for (int mapTag; (mapTag = input.readTag()) != 0;) {
                            int mapFieldNumber = WireFormat.getTagFieldNumber(mapTag);
                            switch (mapFieldNumber) {
                                case 1: key = input.readInt32(); break;
                                case 2: value = input.readInt32(); break;
                                default:
                                    input.skipField(mapTag);
                            }
                        }
                        suggestion.bankItems.put(key, value);
                        input.popLimit(mapLimit);
                        break;
                    case 18:
                        if (suggestion.portfolioItems == null) { suggestion.portfolioItems = new ArrayList<>(); }
                        int itemLength = input.readRawVarint32(), itemLimit = input.pushLimit(itemLength);
                        suggestion.portfolioItems.add(PortfolioItem.decodeProto(input));
                        input.popLimit(itemLimit);
                        break;
                    case 19: suggestion.isHold = input.readBool(); break;
                    default:
                        input.skipField(tag);
                }
            }
        } catch (IOException e) {
            log.warn("failed decoding suggestion proto", e);
            return null;
        }

        if (!suggestion.isDumpAlert && suggestion.message != null && suggestion.message.contains("Dump alert")) {
            suggestion.isDumpAlert = true;
        }
        if (suggestion.type == SuggestionType.ABORT) { suggestion.isDumpAlert = false; }
        return suggestion;
    }

    private static int clampToInt(long value) {
        if (value > Integer.MAX_VALUE) { return Integer.MAX_VALUE; }
        if (value < Integer.MIN_VALUE) { return Integer.MIN_VALUE; }
        return (int) value;
    }
}
