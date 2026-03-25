package com.flippingcopilot.model;

import com.flippingcopilot.util.ProtoUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SyncPortfolioRequest {
    private final int accountId;
    private final List<SyncPortfolioItem> syncItems;

    public byte[] encodeProto() {
        return ProtoUtils.encodeMessage(out -> {
            out.writeInt32(1, accountId);
            if (syncItems == null) {
                return;
            }
            for (SyncPortfolioItem item : syncItems) {
                if (item != null) {
                    ProtoUtils.writeDelimitedMessageField(out, 2, item.encodeProto());
                }
            }
        });
    }
}
