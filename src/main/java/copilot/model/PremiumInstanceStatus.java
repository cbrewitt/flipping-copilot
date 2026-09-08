package copilot.model;

import com.google.protobuf.*;
import com.google.gson.annotations.*;
import lombok.*;

import java.io.*;
import java.util.*;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class PremiumInstanceStatus {
    public String loadingError;
    @SerializedName("premium_instances_count")
    public int premiumInstancesCount;
    @SerializedName("changes_remaining")
    public int changesRemaining;
    @SerializedName("currently_assigned_display_names")
    public List<String> currentlyAssignedDisplayNames;
    @SerializedName("available_display_names")
    public List<String> availableDisplayNames;

    public static PremiumInstanceStatus decodeProto(byte[] bytes) throws IOException {
        var status = new PremiumInstanceStatus();
        status.currentlyAssignedDisplayNames = new ArrayList<>(); status.availableDisplayNames = new ArrayList<>();
        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0;) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: status.premiumInstancesCount = input.readInt32(); break;
                case 2: status.changesRemaining = input.readInt32(); break;
                case 3: status.currentlyAssignedDisplayNames.add(input.readString()); break;
                case 4: status.availableDisplayNames.add(input.readString()); break;
                default:
                    input.skipField(tag);
            }
        }
        return status;
    }

    public static PremiumInstanceStatus ErrorInstance(String error) {
        var pi = new PremiumInstanceStatus();
        pi.setLoadingError(error);
        return pi;
    }
}
