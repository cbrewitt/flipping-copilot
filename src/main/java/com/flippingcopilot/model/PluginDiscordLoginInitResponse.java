package com.flippingcopilot.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Data
@AllArgsConstructor
public class PluginDiscordLoginInitResponse {
    public String url;

    public static PluginDiscordLoginInitResponse fromRaw(DataInputStream s) throws IOException {
        int length = s.readInt();
        if (length < 0) {
            throw new IOException("invalid oauth url length: " + length);
        }
        byte[] urlBytes = new byte[length];
        s.readFully(urlBytes);
        String url = new String(urlBytes, StandardCharsets.UTF_8);
        return new PluginDiscordLoginInitResponse(url);
    }
}
