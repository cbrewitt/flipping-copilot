package copilot.model;

import lombok.*;

import java.util.*;
import java.util.List;

@Value
public class GeHistoryState {
    public boolean loaded;
    public List<GeHistoryRow> rows;
    public int capturedAt;
    public Long accountHash;

    public static GeHistoryState empty() { return new GeHistoryState(false, Collections.emptyList(), 0, null); }
}
