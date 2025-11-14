package com.flippingcopilot.ui;

import net.runelite.api.gameval.SpriteID;

import java.util.Arrays;
import java.util.List;

/**
 * Custom sprite IDs for GE slot borders.
 * These IDs are used to override the default GE slot border sprites.
 */
public class CustomSpriteIds {
    
    // Red slot sprites (not profitable)
    public static final int RED_GE_SLOT_BOTTOM = 189546140;
    public static final int RED_GE_SLOT_BOTTOM_LEFT = 189546141;
    public static final int RED_GE_SLOT_BOTTOM_RIGHT = 189546142;
    public static final int RED_GE_SLOT_TOP_LEFT = 189546143;
    public static final int RED_GE_SLOT_TOP_RIGHT = 189546144;
    public static final int RED_GE_SLOT_HORIZONTAL = 189546145;
    public static final int RED_GE_SLOT_INTERSECTION_LEFT = 189546146;
    public static final int RED_GE_SLOT_INTERSECTION_RIGHT = 189546147;
    public static final int RED_GE_SLOT_LEFT = 189546148;
    public static final int RED_GE_SLOT_RIGHT = 189546149;
    public static final int RED_GE_SLOT_TOP = 189546150;
    public static final int RED_GE_SLOT_ITEM_BOX = 189546151;

    // Blue slot sprites (buy offers or profitable sell offers)
    public static final int BLUE_GE_SLOT_BOTTOM = 189546152;
    public static final int BLUE_GE_SLOT_BOTTOM_LEFT = 189546153;
    public static final int BLUE_GE_SLOT_BOTTOM_RIGHT = 189546154;
    public static final int BLUE_GE_SLOT_TOP_LEFT = 189546155;
    public static final int BLUE_GE_SLOT_TOP_RIGHT = 189546156;
    public static final int BLUE_GE_SLOT_HORIZONTAL = 189546157;
    public static final int BLUE_GE_SLOT_INTERSECTION_LEFT = 189546158;
    public static final int BLUE_GE_SLOT_INTERSECTION_RIGHT = 189546159;
    public static final int BLUE_GE_SLOT_LEFT = 189546160;
    public static final int BLUE_GE_SLOT_RIGHT = 189546161;
    public static final int BLUE_GE_SLOT_TOP = 189546162;
    public static final int BLUE_GE_SLOT_ITEM_BOX = 189546163;

    // Yellow slot sprites (no tracking data / unknown)
    public static final int YELLOW_GE_SLOT_BOTTOM = 189546176;
    public static final int YELLOW_GE_SLOT_BOTTOM_LEFT = 189546177;
    public static final int YELLOW_GE_SLOT_BOTTOM_RIGHT = 189546178;
    public static final int YELLOW_GE_SLOT_TOP_LEFT = 189546179;
    public static final int YELLOW_GE_SLOT_TOP_RIGHT = 189546180;
    public static final int YELLOW_GE_SLOT_HORIZONTAL = 189546181;
    public static final int YELLOW_GE_SLOT_INTERSECTION_LEFT = 189546182;
    public static final int YELLOW_GE_SLOT_INTERSECTION_RIGHT = 189546183;
    public static final int YELLOW_GE_SLOT_LEFT = 189546184;
    public static final int YELLOW_GE_SLOT_RIGHT = 189546185;
    public static final int YELLOW_GE_SLOT_TOP = 189546186;
    public static final int YELLOW_GE_SLOT_ITEM_BOX = 189546187;

    // Lists of sprite IDs in order matching the file names
    public static final List<Integer> RED_SLOT_SPRITES = Arrays.asList(
        RED_GE_SLOT_BOTTOM,
        RED_GE_SLOT_BOTTOM_LEFT,
        RED_GE_SLOT_BOTTOM_RIGHT,
        RED_GE_SLOT_TOP_LEFT,
        RED_GE_SLOT_TOP_RIGHT,
        RED_GE_SLOT_HORIZONTAL,
        RED_GE_SLOT_INTERSECTION_LEFT,
        RED_GE_SLOT_INTERSECTION_RIGHT,
        RED_GE_SLOT_LEFT,
        RED_GE_SLOT_RIGHT,
        RED_GE_SLOT_TOP,
        RED_GE_SLOT_ITEM_BOX
    );

    public static final List<Integer> BLUE_SLOT_SPRITES = Arrays.asList(
        BLUE_GE_SLOT_BOTTOM,
        BLUE_GE_SLOT_BOTTOM_LEFT,
        BLUE_GE_SLOT_BOTTOM_RIGHT,
        BLUE_GE_SLOT_TOP_LEFT,
        BLUE_GE_SLOT_TOP_RIGHT,
        BLUE_GE_SLOT_HORIZONTAL,
        BLUE_GE_SLOT_INTERSECTION_LEFT,
        BLUE_GE_SLOT_INTERSECTION_RIGHT,
        BLUE_GE_SLOT_LEFT,
        BLUE_GE_SLOT_RIGHT,
        BLUE_GE_SLOT_TOP,
        BLUE_GE_SLOT_ITEM_BOX
    );

    public static final List<Integer> YELLOW_SLOT_SPRITES = Arrays.asList(
        YELLOW_GE_SLOT_BOTTOM,
        YELLOW_GE_SLOT_BOTTOM_LEFT,
        YELLOW_GE_SLOT_BOTTOM_RIGHT,
        YELLOW_GE_SLOT_TOP_LEFT,
        YELLOW_GE_SLOT_TOP_RIGHT,
        YELLOW_GE_SLOT_HORIZONTAL,
        YELLOW_GE_SLOT_INTERSECTION_LEFT,
        YELLOW_GE_SLOT_INTERSECTION_RIGHT,
        YELLOW_GE_SLOT_LEFT,
        YELLOW_GE_SLOT_RIGHT,
        YELLOW_GE_SLOT_TOP,
        YELLOW_GE_SLOT_ITEM_BOX
    );

    public static final List<Integer> DEFAULT_SLOT_SPRITES = Arrays.asList(
        SpriteID.V2BordersSlim.HORIZONTAL_B,
        SpriteID.V2BordersSlim.BOTTOM_LEFT,
        SpriteID.V2BordersSlim.BOTTOM_RIGHT,
        SpriteID.V2BordersSlim.TOP_LEFT,
        SpriteID.V2BordersSlim.TOP_RIGHT,
        SpriteID.V2BordersSlim.HORIZONTAL_C,
        SpriteID.V2BordersSlim.INTERSECTION_LEFT,
        SpriteID.V2BordersSlim.INTERSECTION_RIGHT,
        SpriteID.V2BordersSlim.VERTICAL_A,
        SpriteID.V2BordersSlim.VERTICAL_B,
        SpriteID.V2BordersSlim.HORIZONTAL_A,
        SpriteID.GeItembackdrop.BOX
    );
}
