package copilot.model;

import java.io.*;
import com.google.protobuf.*;
import lombok.*;
import com.google.gson.annotations.*;

import java.nio.charset.*;

@Data
@AllArgsConstructor
public class LoginResponse {
    public String jwt;

    @SerializedName("user_id")
    public int userId;

    public String error;

    public static LoginResponse decodeProto(byte[] bytes) throws IOException {
        var response = new LoginResponse("", 0, null);
        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0;) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 2: response.jwt = input.readString(); break;
                case 3: response.userId = input.readInt32(); break;
                default:
                    input.skipField(tag);
            }
        }
        return response;
    }

    public static LoginResponse fromRaw(DataInputStream s) {
        try {
            int length = s.readInt();
            if (length < 0) { throw new IOException("invalid login token length: " + length); }
            byte[] tokenBytes = new byte[length];
            s.readFully(tokenBytes);
            var token = new String(tokenBytes, StandardCharsets.UTF_8);
            int userId = s.readInt(), errorLength = s.readInt();
            if (errorLength < 0) { throw new IOException("invalid error length: " + errorLength); }
            byte[] errorBytes = new byte[errorLength];
            s.readFully(errorBytes);
            var error = new String(errorBytes, StandardCharsets.UTF_8);
            return new LoginResponse(token, userId, error);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode login response", e);
        }
    }
}
