package com.flippingcopilot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.util.List;


@Getter
@Setter
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class PremiumInstanceStatus {
    private String loadingError;
    @SerializedName("premium_instances_count")
    @JsonProperty("pic")
    private int premiumInstancesCount;
    @SerializedName("changes_remaining")
    @JsonProperty("cr")
    private int changesRemaining;
    @SerializedName("currently_assigned_display_names")
    @JsonProperty("cadn")
    private List<String> currentlyAssignedDisplayNames;
    @SerializedName("available_display_names")
    @JsonProperty("adn")
    private List<String> availableDisplayNames;

    public static PremiumInstanceStatus ErrorInstance(String error) {
        PremiumInstanceStatus pi = new PremiumInstanceStatus();
        pi.setLoadingError(error);
        return pi;
    }
}
