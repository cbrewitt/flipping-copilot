package copilot.ui.graph.model;

import java.text.*;

public class Constants {
    public static final int DAY_SECONDS = 86400, FIVE_MIN_SECONDS = 60*5, TEN_MIN_SECONDS = 60*10;
    public static final int THIRTY_MIN_SECONDS = 60*30, HOUR_SECONDS = 60*60;
    public static final SimpleDateFormat SECOND_DATE_FORMAT = new SimpleDateFormat("d MMM HH:mm:ss");
    public static final SimpleDateFormat MINUTE_DATE_FORMAT = new SimpleDateFormat("d MMM HH:mm");
    public static final SimpleDateFormat MINUTE_TIME_FORMAT = new SimpleDateFormat("HH:mm");
}
