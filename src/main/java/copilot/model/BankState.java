package copilot.model;

import java.util.*;
import lombok.*;

@Value
public class BankState {
    public boolean loaded;
    public Map<Integer, Integer> items;
    public Long loadedAccountHash;

    public static BankState empty() { return new BankState(false, Collections.emptyMap(), null); }
}
