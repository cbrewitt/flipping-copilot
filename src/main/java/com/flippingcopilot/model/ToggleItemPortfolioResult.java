package com.flippingcopilot.model;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class ToggleItemPortfolioResult {
    private List<Suggestion.PortfolioItem> portfolioItems = new ArrayList<>();

    public static ToggleItemPortfolioResult decodeProto(byte[] bytes) throws IOException {
        ToggleItemPortfolioResult result = new ToggleItemPortfolioResult();
        if (bytes == null || bytes.length == 0) {
            return result;
        }

        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            if (fieldNumber == 1) {
                int length = input.readRawVarint32();
                int limit = input.pushLimit(length);
                Suggestion.PortfolioItem item = decodePortfolioItem(input);
                input.popLimit(limit);
                if (item != null) {
                    result.portfolioItems.add(item);
                }
            } else {
                input.skipField(tag);
            }
        }
        return result;
    }

    private static Suggestion.PortfolioItem decodePortfolioItem(CodedInputStream input) throws IOException {
        Suggestion.PortfolioItem item = new Suggestion.PortfolioItem();
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            switch (fieldNumber) {
                case 1:
                    item.itemId = input.readInt32();
                    break;
                case 2:
                    item.amount = input.readInt32();
                    break;
                case 3:
                    item.sellValue = input.readInt64();
                    break;
                case 4:
                    item.buySpend = input.readInt64();
                    break;
                case 5:
                    item.inPortfolio = input.readBool();
                    break;
                case 6:
                    item.heldMinutes = input.readInt32();
                    break;
                default:
                    input.skipField(tag);
            }
        }
        return item;
    }
}
