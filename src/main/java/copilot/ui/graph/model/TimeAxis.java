package copilot.ui.graph.model;

import lombok.*;

@AllArgsConstructor
public class TimeAxis {
    public int[] dateOnlyTickTimes, timeOnlyTickTimes;
    public int[] gridOnlyTickTimes;
}
