package copilot.model;

import java.io.*;
import lombok.*;

import java.nio.charset.*;

@Data
@AllArgsConstructor
public class PluginDiscordLoginInitResponse {
    public String url;

    public static PluginDiscordLoginInitResponse fromRaw(DataInputStream s) throws IOException {
        int length = s.readInt();
        if (length < 0) { throw new IOException("invalid oauth url length: " + length); }
        byte[] urlBytes = new byte[length];
        s.readFully(urlBytes);
        var url = new String(urlBytes, StandardCharsets.UTF_8);
        return new PluginDiscordLoginInitResponse(url);
    }
}
