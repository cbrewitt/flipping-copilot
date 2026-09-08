package copilot.model;

import lombok.*;

import java.util.*;
import java.util.List;

@Data
public class ProfileSuggestionPreferences {

    public List<Integer> blockedItemIds = new ArrayList<>();
}
