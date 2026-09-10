package com.flippingcopilot.model;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WikiLatestPrice {

    private final Long high;
    private final Long highTime;
    private final Long low;
    private final Long lowTime;

    public static WikiLatestPrice fromJson(Gson gson, String body, int itemId) {
        try {
            JsonObject root = gson.fromJson(body, JsonObject.class);
            JsonElement data = root == null ? null : root.get("data");
            if (data == null || !data.isJsonObject()) {
                return null;
            }
            JsonElement item = data.getAsJsonObject().get(String.valueOf(itemId));
            return item == null || !item.isJsonObject() ? null : gson.fromJson(item, WikiLatestPrice.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
