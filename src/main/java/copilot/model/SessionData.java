package copilot.model;

import com.google.gson.annotations.*;
import lombok.*;

@AllArgsConstructor
public class SessionData {

    @SerializedName("start_time")
    public int startTime;

    @SerializedName("duration_millis")
    public long durationMillis;

    @SerializedName("average_cash")
    public long averageCash;
}
