package copilot.model;

import lombok.*;
import copilot.util.*;

@Getter
@AllArgsConstructor
public class PortfolioRequest {

    // Sentinel for the portfolioId field meaning "remove from portfolio".
    public static final int REMOVE = -1;

    public final int accountId, itemId;
    private final int portfolioId, bagQuantity;
    private final int bankQuantity, quantity;

    public byte[] encodeProto() {
        return ProtoUtils.encodeMessage(out -> {
            out.writeInt32(1, accountId);
            out.writeInt32(2, itemId);
            out.writeSInt32(3, portfolioId);
            out.writeInt32(4, bagQuantity);
            out.writeInt32(5, bankQuantity);
            out.writeInt32(6, quantity);
        });
    }
}
