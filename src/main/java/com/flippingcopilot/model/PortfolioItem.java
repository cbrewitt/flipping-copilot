package com.flippingcopilot.model;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PortfolioItem {
    public int itemId;
    public int amount;
    public long sellValue;
    public long buySpend;
    public boolean inPortfolio;
    public int heldMinutes;

    public long getPostTaxSellUnitPrice() {
        if (amount <= 0) {
            return 0L;
        }
        return sellValue / amount;
    }

    public long getUnitBuyPrice() {
        if (amount <= 0) {
            return 0L;
        }
        return buySpend / amount;
    }

    public static PortfolioItem decodeProto(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        PortfolioItem item = new PortfolioItem();
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
