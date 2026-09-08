package copilot.ui.flipsdialog;

import lombok.*;

import java.time.*;

@RequiredArgsConstructor
public class Datapoint {
    public final LocalDate t;
    public final long cumulativeProfit, dailyProfit;
    public boolean isDailyProfitHovered, isCumulativeProfitHovered;

    public long timestamp() { return t.toEpochSecond(LocalTime.MIN, ZoneOffset.UTC); }
}
