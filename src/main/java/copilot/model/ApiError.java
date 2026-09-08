package copilot.model;

import com.google.protobuf.*;
import lombok.*;

import java.io.*;

/** The protobuf error body every v2 endpoint replies with on failure; its code mirrors the HTTP status. */
@Getter
@AllArgsConstructor
public class ApiError {

    private final int code;
    public final String displayErr;

    public static ApiError decodeProto(byte[] bytes) throws IOException {
        int code = 0;
        String displayErr = "";
        if (bytes == null || bytes.length == 0) { return new ApiError(code, displayErr); }
        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0;) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: code = input.readInt32(); break;
                case 2: displayErr = input.readString(); break;
                default:
                    input.skipField(tag);
            }
        }
        return new ApiError(code, displayErr);
    }
}
