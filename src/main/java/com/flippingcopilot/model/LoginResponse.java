package com.flippingcopilot.model;

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
