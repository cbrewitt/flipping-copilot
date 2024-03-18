package com.flippingcopilot.model;

import lombok.Data;

@Data
public class DiscordWebhookBody {
    private String content;
    private Embed embed;

    @Data
    static class Embed {
        final UrlEmbed image;
    }

    @Data
    static class UrlEmbed {
        final String url;
    }
}