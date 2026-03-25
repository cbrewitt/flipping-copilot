package com.flippingcopilot.model;

import com.flippingcopilot.util.ProtoUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SyncPortfolioItem {
    private final int itemId;
    private final int quantityHeld;

    public byte[] encodeProto() {
        return ProtoUtils.encodeMessage(out -> {
            out.writeInt32(1, itemId);
            out.writeInt32(2, quantityHeld);
        });
    }
}
