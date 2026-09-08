package copilot.model;

import lombok.*;

import java.util.List;

@Value
public class ItemTooltipData {
    int itemId, quantity;
    String itemName;
    public List<String> lines;
}
