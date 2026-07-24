package com.flippingcopilot.model;

import com.flippingcopilot.util.ProtoUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountClientTransactionsRequest {
    private final Integer end;
    private final int limit;
    // v1 sent the display name as a query parameter; v2 carries it in the body.
    private final String displayName;

    public byte[] encodeProto() {
        return ProtoUtils.encodeMessage(out -> {
            if (end != null) {
                out.writeInt32(1, end);
            }
            out.writeInt32(2, limit);
            out.writeString(3, displayName);
        });
    }
}
