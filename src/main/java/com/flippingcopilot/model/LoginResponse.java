package com.flippingcopilot.model;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.IOException;

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

    public static LoginResponse decodeDiscordLoginResult(byte[] bytes) throws IOException {
        LoginResponse response = new LoginResponse("", 0, null);
        if (bytes.length == 0) {
            response.error = "Login failed (no response from server)";
            return response;
        }
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    response.jwt = input.readString();
                    break;
                case 2:
                    response.userId = input.readInt32();
                    break;
                case 3:
                    response.error = input.readString();
                    break;
                default:
                    input.skipField(tag);
            }
        }
        return response;
    }
}
