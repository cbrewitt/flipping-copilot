package com.flippingcopilot.model;

import com.flippingcopilot.util.ProtoUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountClientTransactionsRequest {
    private final Integer end;
    private final int limit;

    public byte[] encodeProto() {
        return ProtoUtils.encodeMessage(out -> {
            if (end != null) {
                out.writeInt32(1, end);
            }
            out.writeInt32(2, limit);
        });
    }
}
