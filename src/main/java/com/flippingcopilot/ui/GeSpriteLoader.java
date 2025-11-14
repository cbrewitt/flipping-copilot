package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Loads custom GE slot border sprites and manages sprite ID mappings.
 */
@Slf4j
public class GeSpriteLoader {

    // Widget child indices for GE slot components
    public static final int TOP_CHILD_IDX = 5;
    public static final int BOTTOM_CHILD_IDX = 6;
    public static final int LEFT_CHILD_IDX = 7;
    public static final int RIGHT_CHILD_IDX = 8;
    public static final int TOP_LEFT_CHILD_IDX = 9;
    public static final int TOP_RIGHT_CHILD_IDX = 10;
    public static final int BOTTOM_LEFT_CHILD_IDX = 11;
    public static final int BOTTOM_RIGHT_CHILD_IDX = 12;
    public static final int HORIZONTAL_CHILD_IDX = 13;
    public static final int LEFT_INTERSECTION_CHILD_IDX = 14;
    public static final int RIGHT_INTERSECTION_CHILD_IDX = 15;
    public static final int ITEM_BOX_CHILD_IDX = 17;

    // List of dynamic children that can be modified
    public static final List<Integer> DYNAMIC_CHILDREN_IDXS = Arrays.asList(
        BOTTOM_CHILD_IDX,
        BOTTOM_LEFT_CHILD_IDX,
        BOTTOM_RIGHT_CHILD_IDX,
        TOP_LEFT_CHILD_IDX,
        TOP_RIGHT_CHILD_IDX,
        HORIZONTAL_CHILD_IDX,
        LEFT_INTERSECTION_CHILD_IDX,
        RIGHT_INTERSECTION_CHILD_IDX,
        LEFT_CHILD_IDX,
        RIGHT_CHILD_IDX,
        TOP_CHILD_IDX,
        ITEM_BOX_CHILD_IDX
    );

    // File names for sprite resources (in order)
    public static final List<String> FILE_NAMES = Arrays.asList(
        "border_offer_bottom.png",
        "border_offer_corner_bottom_left.png",
        "border_offer_corner_bottom_right.png",
        "border_offer_corner_top_left.png",
        "border_offer_corner_top_right.png",
        "border_offer_horizontal.png",
        "border_offer_intersection_left.png",
        "border_offer_intersection_right.png",
        "border_offer_left.png",
        "border_offer_right.png",
        "border_offer_top.png",
        "selected_item_box.png"
    );

    // Sprite ID mappings
    public static final Map<Integer, Integer> CHILDREN_IDX_TO_DEFAULT_SPRITE_ID = new HashMap<>();
    public static final Map<Integer, Integer> CHILDREN_IDX_TO_RED_SPRITE_ID = new HashMap<>();
    public static final Map<Integer, Integer> CHILDREN_IDX_TO_BLUE_SPRITE_ID = new HashMap<>();
    public static final Map<Integer, Integer> CHILDREN_IDX_TO_YELLOW_SPRITE_ID = new HashMap<>();

    static {
        // Initialize mappings
        for (int i = 0; i < DYNAMIC_CHILDREN_IDXS.size(); i++) {
            int childIdx = DYNAMIC_CHILDREN_IDXS.get(i);
            CHILDREN_IDX_TO_DEFAULT_SPRITE_ID.put(childIdx, CustomSpriteIds.DEFAULT_SLOT_SPRITES.get(i));
            CHILDREN_IDX_TO_RED_SPRITE_ID.put(childIdx, CustomSpriteIds.RED_SLOT_SPRITES.get(i));
            CHILDREN_IDX_TO_BLUE_SPRITE_ID.put(childIdx, CustomSpriteIds.BLUE_SLOT_SPRITES.get(i));
            CHILDREN_IDX_TO_YELLOW_SPRITE_ID.put(childIdx, CustomSpriteIds.YELLOW_SLOT_SPRITES.get(i));
        }
    }

    /**
     * Registers all custom sprite overrides with the client.
     * Should be called during plugin startup.
     */
    public static void setClientSpriteOverrides(Client client) {
        log.debug("Loading custom GE slot sprites");
        setClientSpriteOverrides(client, "red", CustomSpriteIds.RED_SLOT_SPRITES);
        setClientSpriteOverrides(client, "blue", CustomSpriteIds.BLUE_SLOT_SPRITES);
        setClientSpriteOverrides(client, "yellow", CustomSpriteIds.YELLOW_SLOT_SPRITES);
        log.debug("Custom GE slot sprites loaded successfully");
    }

    /**
     * Registers sprite overrides for a specific color.
     */
    private static void setClientSpriteOverrides(Client client, String color, List<Integer> spriteIds) {
        for (int i = 0; i < spriteIds.size(); i++) {
            int spriteId = spriteIds.get(i);
            String filename = FILE_NAMES.get(i);
            try {
                BufferedImage image = ImageUtil.loadImageResource(FlippingCopilotPlugin.class, 
                    "/ge-sprites/" + color + "/" + filename);
                if (image != null) {
                    client.getSpriteOverrides().put(spriteId, ImageUtil.getImageSpritePixels(image, client));
                } else {
                    log.warn("Failed to load sprite: /ge-sprites/{}/{}", color, filename);
                }
            } catch (Exception e) {
                log.error("Error loading sprite /ge-sprites/{}/{}", color, filename, e);
            }
        }
    }
}
