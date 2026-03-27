package com.flippingcopilot.model;

import com.flippingcopilot.util.ProtoUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ToggleItemPortfolioRequest {

    private final int accountId;
    private final int itemId;
    private final int portfolioId;
    private final int bankQuantity;

    public byte[] encodeProto() {
        return ProtoUtils.encodeMessage(out -> {
            out.writeInt32(1, accountId);
            out.writeInt32(2, itemId);
            out.writeSInt32(3, portfolioId);
            out.writeInt32(4, bankQuantity);
        });
    }
}
