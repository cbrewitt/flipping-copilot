package com.flippingcopilot.ui.graph.model;

import java.text.SimpleDateFormat;

public class Constants {
    public static final int DAY_SECONDS = 86400;
    public static final int FIVE_MIN_SECONDS = 60*5;
    public static final int TEN_MIN_SECONDS = 60*10;
    public static final int THIRTY_MIN_SECONDS = 60*30;
    public static final int HOUR_SECONDS = 60*60;
    public static final SimpleDateFormat SECOND_DATE_FORMAT = new SimpleDateFormat("d MMM HH:mm:ss");
    public static final SimpleDateFormat MINUTE_DATE_FORMAT = new SimpleDateFormat("d MMM HH:mm");
    public static final SimpleDateFormat MINUTE_TIME_FORMAT = new SimpleDateFormat("HH:mm");
}
