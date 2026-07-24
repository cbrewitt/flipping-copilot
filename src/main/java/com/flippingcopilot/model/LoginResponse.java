package com.flippingcopilot.model;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Data
@AllArgsConstructor
public class LoginResponse {
    public String jwt;

    @SerializedName("user_id")
    public int userId;

    public String error;

    public static LoginResponse decodeProto(byte[] bytes) throws IOException {
        LoginResponse response = new LoginResponse("", 0, null);
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 2:
                    response.jwt = input.readString();
                    break;
                case 3:
                    response.userId = input.readInt32();
                    break;
                default:
                    input.skipField(tag);
            }
        }
        return response;
    }

    public static LoginResponse fromRaw(DataInputStream s) {
        try {
            int length = s.readInt();
            if (length < 0) {
                throw new IOException("invalid login token length: " + length);
            }
            byte[] tokenBytes = new byte[length];
            s.readFully(tokenBytes);
            String token = new String(tokenBytes, StandardCharsets.UTF_8);
            int userId = s.readInt();
            int errorLength = s.readInt();
            if (errorLength < 0) {
                throw new IOException("invalid error length: " + errorLength);
            }
            byte[] errorBytes = new byte[errorLength];
            s.readFully(errorBytes);
            String error = new String(errorBytes, StandardCharsets.UTF_8);
            return new LoginResponse(token, userId, error);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode login response", e);
        }
    }
}
