package com.flippingcopilot.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;


@Getter
@Setter
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class PremiumInstanceStatus {
    private String loadingError;
    @SerializedName("premium_instances_count")
    private int premiumInstancesCount;
    @SerializedName("changes_remaining")
    private int changesRemaining;
    @SerializedName("currently_assigned_display_names")
    private List<String> currentlyAssignedDisplayNames;
    @SerializedName("available_display_names")
    private List<String> availableDisplayNames;

    public static PremiumInstanceStatus ErrorInstance(String error) {
        PremiumInstanceStatus pi = new PremiumInstanceStatus();
        pi.setLoadingError(error);
        return pi;
    }
}
