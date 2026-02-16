package com.flippingcopilot.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class DiscordWebhookBody {
    private String content;
    private Boolean tts;
    private List<Embed> embeds;

    @Data
    public static class Embed {
        private String title;
        private String description;
        private Integer color;
        private Author author;
        private Thumbnail thumbnail;
    }

    @Data
    public static class Thumbnail {
        private String url;
    }

    @Data
    public static class Author {
        private String name;

        @SerializedName("icon_url")
        private String iconUrl;
    }
}
