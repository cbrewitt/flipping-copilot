package copilot.model;

import lombok.*;

@Value
public class GeHistoryRow {
    public int itemId, quantity;
    public long price;
    public boolean buy;
}
