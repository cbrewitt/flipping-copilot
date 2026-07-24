package com.flippingcopilot.model;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.IOException;
import java.util.ArrayList;
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

    public static PremiumInstanceStatus decodeProto(byte[] bytes) throws IOException {
        PremiumInstanceStatus status = new PremiumInstanceStatus();
        status.currentlyAssignedDisplayNames = new ArrayList<>();
        status.availableDisplayNames = new ArrayList<>();
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    status.premiumInstancesCount = input.readInt32();
                    break;
                case 2:
                    status.changesRemaining = input.readInt32();
                    break;
                case 3:
                    status.currentlyAssignedDisplayNames.add(input.readString());
                    break;
                case 4:
                    status.availableDisplayNames.add(input.readString());
                    break;
                default:
                    input.skipField(tag);
            }
        }
        return status;
    }

    public static PremiumInstanceStatus ErrorInstance(String error) {
        PremiumInstanceStatus pi = new PremiumInstanceStatus();
        pi.setLoadingError(error);
        return pi;
    }
}
