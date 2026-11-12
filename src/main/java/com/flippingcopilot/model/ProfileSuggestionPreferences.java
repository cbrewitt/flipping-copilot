package com.flippingcopilot.model;

import lombok.Data;

import java.util.*;

@Data
public class ProfileSuggestionPreferences {

    public List<Integer> blockedItemIds = new ArrayList<>();
}
