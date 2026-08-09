package com.flippingcopilot.model;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.IOException;

@Data
@AllArgsConstructor
public class PluginDiscordLoginInitResponse {
    public String url;

    public static PluginDiscordLoginInitResponse decodeProto(byte[] bytes) throws IOException {
        PluginDiscordLoginInitResponse response = new PluginDiscordLoginInitResponse("");
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    response.url = input.readString();
                    break;
                default:
                    input.skipField(tag);
            }
        }
        return response;
    }
}
